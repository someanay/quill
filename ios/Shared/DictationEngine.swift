import Foundation

// Shared dictation pipeline for BOTH the main app and the keyboard extension: record → transcribe →
// guarded cleanup → custom-word substitution → final text (also saved to history). It does NOT perform
// OS insertion — the caller inserts the returned text (the app copies/shares it; the keyboard uses
// UITextDocumentProxy.insertText). Mirrors the Android/Windows/mac pipeline order and guards.
@MainActor
final class DictationEngine: ObservableObject {
    enum Phase: Equatable { case idle, listening, transcribing, cleaning, done, error }
    @Published private(set) var phase: Phase = .idle
    @Published private(set) var statusText = "Ready"
    @Published private(set) var lastText = ""

    private let settingsStore: SettingsStore
    private let secrets = SecretStore()
    private let history: HistoryStore
    private let audio: AudioCaptureService
    private let session: URLSession
    private let cleanup: CleanupClient
    private var busy = false

    init(paths: AppPaths = AppPaths()) {
        settingsStore = SettingsStore(paths: paths)
        history = HistoryStore(paths: paths)
        audio = AudioCaptureService(paths: paths)
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 45
        c.timeoutIntervalForResource = 120
        session = URLSession(configuration: c)
        cleanup = CleanupClient(session: session)
        audio.onAutoStop = { [weak self] in Task { @MainActor in _ = await self?.finish(application: "Quill") } }
    }

    var isListening: Bool { phase == .listening }

    /// Toggle: start listening, or stop → transcribe and return the final text (nil while starting/on error).
    @discardableResult
    func toggle(application: String) async -> String? {
        if phase == .listening { return await finish(application: application) }
        await start()
        return nil
    }

    func cancel() {
        if audio.isRecording { _ = audio.stop() }
        phase = .idle; statusText = "Ready"
    }

    private func start() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        let settings = await settingsStore.load()
        guard let key = secrets.get(settings.provider), !key.trimmed.isEmpty else {
            phase = .error; statusText = "Add a \(settings.provider.displayName) key in Settings."; return
        }
        _ = key
        guard await AudioCaptureService.requestPermission() else {
            phase = .error; statusText = "Microphone access is off."; return
        }
        do {
            try audio.start(settings)
            phase = .listening; statusText = "Listening… tap to finish."
        } catch {
            phase = .error; statusText = "Microphone unavailable."
        }
    }

    @discardableResult
    private func finish(application: String) async -> String? {
        guard phase == .listening, !busy else { return nil }
        busy = true; defer { busy = false }
        phase = .transcribing; statusText = "Transcribing…"
        let recording = audio.stop()
        defer { try? FileManager.default.removeItem(at: recording.url) }
        guard recording.hasSpeech else { phase = .idle; statusText = "Nothing heard."; return nil }

        let settings = await settingsStore.load()
        guard let key = secrets.get(settings.provider), !key.trimmed.isEmpty else {
            phase = .error; statusText = "The API key is missing."; return nil
        }
        let descriptor = ProviderCatalog.all[settings.provider]!
        let model = settings.model.trimmed.isEmpty ? descriptor.defaultModel : settings.model
        do {
            let client = try TranscriptionClientFactory.create(
                provider: settings.provider, key: key, customBaseURL: settings.customBaseURL, session: session)
            let transcript = try await client.transcribe(
                audioPath: recording.url,
                options: TranscriptionOptions(model: model, language: settings.language, prompt: transcriptionPrompt(settings)))
            var text = transcript.text
            var cleaned = false
            if settings.cleanupEnabled {
                phase = .cleaning; statusText = "Tidying…"
                let candidate = await cleanup.clean(text, provider: settings.provider, key: key,
                                                    model: settings.cleanupModel, style: settings.defaultWritingStyle)
                cleaned = (candidate != text)
                text = candidate
            }
            text = Self.applyCustomWords(text, settings.customWords)
            await history.add(HistoryEntry(text: text, application: application, provider: settings.provider, wasCleaned: cleaned),
                              settings: settings)
            lastText = text; phase = .done; statusText = "Done."
            return text
        } catch let e as TranscriptionException {
            phase = .error; statusText = e.message; return nil
        } catch {
            phase = .error; statusText = error.localizedDescription; return nil
        }
    }

    private func transcriptionPrompt(_ s: AppSettings) -> String? {
        var parts: [String] = []
        if let romanize = RomanizePrompt.forLanguage(s.language) { parts.append(romanize) }
        if !s.customWords.isEmpty {
            parts.append("Preferred spellings: " + s.customWords.map { $0.replacement }.joined(separator: ", "))
        }
        return parts.isEmpty ? nil : parts.joined(separator: "\n")
    }

    // Whole-word, case-insensitive, LITERAL replacement (W-3 parity: '$' is not a capture-group ref).
    nonisolated static func applyCustomWords(_ text: String, _ words: [WordReplacement]) -> String {
        var out = text
        for w in words where !w.heard.trimmed.isEmpty {
            let pattern = "\\b\(NSRegularExpression.escapedPattern(for: w.heard))\\b"
            guard let rx = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { continue }
            out = rx.stringByReplacingMatches(in: out, range: NSRange(out.startIndex..., in: out),
                                              withTemplate: NSRegularExpression.escapedTemplate(for: w.replacement))
        }
        return out
    }
}
