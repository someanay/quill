import AppKit
import ApplicationServices
import AVFoundation
import QuillCore

private struct LiveFailure: Error { let message: String; init(_ message: String) { self.message = message } }

@main @MainActor struct LiveProviderProbe {
    static func main() async {
        guard ProcessInfo.processInfo.environment["GITHUB_ACTIONS"] == "true", CommandLine.arguments.count == 3 else { exit(64) }
        NSApplication.shared.setActivationPolicy(.prohibited)
        let speech = URL(fileURLWithPath: CommandLine.arguments[1])
        let output = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
        try? FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        LiveHTTPAudit.outputURL = output.appendingPathComponent("http-audit.json")
        var results: [[String: Any]] = [], editor: NSRunningApplication?
        let dataRoot = FileManager.default.temporaryDirectory.appendingPathComponent("QuillLive-" + UUID().uuidString)
        do {
            guard AXIsProcessTrusted() else { throw LiveFailure("Actual Accessibility trust unavailable.") }
            guard NSRunningApplication.runningApplications(withBundleIdentifier: "com.apple.TextEdit").isEmpty else { throw LiveFailure("TextEdit was already open; refusing to touch existing documents.") }
            var microphoneAllowed = AudioCapture.permitted
            if !microphoneAllowed { microphoneAllowed = await AudioCapture.requestPermission() }
            guard microphoneAllowed else { throw LiveFailure("Actual microphone permission unavailable.") }
            try FileManager.default.createDirectory(at: dataRoot, withIntermediateDirectories: true)
            guard let editorURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.apple.TextEdit") else { throw LiveFailure("TextEdit unavailable.") }
            let launch = NSWorkspace.OpenConfiguration(); launch.activates = true; launch.addsToRecentItems = false; launch.arguments = ["-ApplePersistenceIgnoreState", "YES"]
            editor = try await withCheckedThrowingContinuation { continuation in NSWorkspace.shared.openApplication(at: editorURL, configuration: launch) { app, error in
                if let app { continuation.resume(returning: app) } else { continuation.resume(throwing: error ?? LiveFailure("TextEdit launch failed.")) }
            } }
            for (speechProvider, cleanupProvider) in [(Provider.gemini,Provider.gemini),(.openrouter,.openrouter),(.gemini,.openrouter)] {
                let directory = dataRoot.appendingPathComponent(UUID().uuidString); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let settings = SettingsStore(directory: directory), keys = KeychainStore(directory: directory, namespace: "com.tihor.quill.live." + UUID().uuidString)
                defer { try? keys.clear() }
                for provider in [Provider.gemini,.openrouter] {
                    guard let key = ProcessInfo.processInfo.environment[provider == .gemini ? "QUILL_LIVE_GEMINI_KEY" : "QUILL_LIVE_OPENROUTER_KEY"], !key.isEmpty else { throw LiveFailure("Required live key unavailable.") }
                    let slot = try keys.save(provider: provider, label: "Temporary live QA", secret: key, status: "valid", customURL: "")
                    settings.value.activeKeys[provider.rawValue] = slot.id
                }
                settings.value.provider = speechProvider; settings.value.speechModel = speechProvider.defaultSpeech
                settings.value.cleanupAutomatic = false; settings.value.cleanupEnabled = true; settings.value.cleanupProvider = cleanupProvider; settings.value.cleanupModel = cleanupProvider.defaultCleanup
                settings.value.onboardingComplete = true; settings.value.accessibilityConsent = true; settings.value.privateMode = true; settings.value.historyEnabled = false
                let session = URLSession(configuration: LiveHTTPAudit.configuration())
                let dictation = Dictation(settings: settings, keys: keys, history: HistoryStore(directory: directory), client: ProviderClient(session: session), pendingStore: PendingAudioStore(directory: directory, namespace: "com.tihor.quill.live.pending." + UUID().uuidString))
                defer { _ = dictation.cancel(); session.invalidateAndCancel() }
                for take in 1...2 {
                    let document = directory.appendingPathComponent("synthetic-\(take).txt"), prefix = "Quill live fixture: "
                    try prefix.write(to: document, atomically: true, encoding: .utf8)
                    _ = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<NSRunningApplication, Error>) in NSWorkspace.shared.open([document], withApplicationAt: editorURL, configuration: launch) { app, error in
                        if let app { continuation.resume(returning: app) } else { continuation.resume(throwing: error ?? LiveFailure("Owned document open failed.")) }
                    } }
                    _ = editor?.activate(options: [.activateAllWindows,.activateIgnoringOtherApps])
                    let injector = TextInjector(); var target: FocusSnapshot?
                    for _ in 0..<120 {
                        if let value = try? injector.snapshot(), value.bundleID == "com.apple.TextEdit", value.originalText?.trimmingCharacters(in: .newlines) == prefix { target = value; break }
                        try await Task.sleep(nanoseconds: 100_000_000)
                    }
                    guard let target else { throw LiveFailure("Owned synthetic TextEdit document did not become focused.") }
                    var range = CFRange(location: prefix.utf16.count, length: 0)
                    guard let value = AXValueCreate(.cfRange, &range), AXUIElementSetAttributeValue(target.element, kAXSelectedTextRangeAttribute as CFString, value) == .success else { throw LiveFailure("Cannot prepare owned caret.") }
                    let before = LiveHTTPAudit.snapshot().count
                    dictation.start(); guard dictation.state == .recording else { throw LiveFailure("Production recorder did not start: " + dictation.message) }
                    let playback = Process(); playback.executableURL = URL(fileURLWithPath: "/usr/bin/afplay"); playback.arguments = [speech.path]; try playback.run()
                    var peak = 0.0
                    while playback.isRunning { peak = max(peak,dictation.level); try await Task.sleep(nanoseconds: 80_000_000) }
                    guard playback.terminationStatus == 0, peak > 0.005 else { throw LiveFailure("Virtual-input recorder did not observe the real speech signal.") }
                    try await Task.sleep(nanoseconds: 300_000_000)
                    dictation.stop()
                    for _ in 0..<1200 { if dictation.state != .transcribing { break }; try await Task.sleep(nanoseconds: 100_000_000) }
                    let events = Array(LiveHTTPAudit.snapshot().dropFirst(before))
                    guard dictation.state == .idle, events.count == 2, events.allSatisfy({ $0["status"] as? Int == 200 }),
                          let raw = events.first?["output"] as? String, let clean = events.last?["output"] as? String, events.last?["finish"] as? String == "stop",
                          CleanupGuard.preservesMeaning(raw: raw, cleaned: clean) else { throw LiveFailure("Real transcription/cleanup or confirmed insertion failed: " + dictation.message) }
                    var readback: CFTypeRef?
                    guard AXUIElementCopyAttributeValue(target.element, kAXValueAttribute as CFString, &readback) == .success, let actual = readback as? String,
                          actual.trimmingCharacters(in: .newlines) == prefix + clean.trimmingCharacters(in: .whitespacesAndNewlines) else { throw LiveFailure("Actual TextEdit readback differs from the live cleaned result.") }
                    for anchor in ["project","notes","tomorrow","budget"] { guard raw.lowercased().contains(anchor) else { throw LiveFailure("Captured speech lost a required anchor.") } }
                    results.append(["speech":speechProvider.rawValue,"cleanup":cleanupProvider.rawValue,"take":take,"passed":true,"input":"actual AudioCapture via BlackHole 2ch synthetic speech","peak":peak,"raw":raw,"clean":clean,"editor":actual,"requests":events])
                }
            }
            write(["passed":true,"accessibilityTrusted":true,"realRecorder":true,"results":results], to: output)
            _ = editor?.forceTerminate(); try? FileManager.default.removeItem(at: dataRoot); exit(0)
        } catch {
            write(["passed":false,"reason":(error as? LiveFailure)?.message ?? String(describing:type(of:error)),"results":results,"requests":LiveHTTPAudit.snapshot()], to: output)
            _ = editor?.forceTerminate(); try? FileManager.default.removeItem(at: dataRoot); exit(1)
        }
    }
    static func write(_ report: [String: Any], to folder: URL) { if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted,.sortedKeys]) { try? data.write(to: folder.appendingPathComponent("result.json")) } }
}
