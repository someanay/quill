import Foundation

// End-to-end dictation flow — faithful port of windows/.../Core/DictationOrchestrator.cs, including the
// overnight fixes: pending target/clipboard-mode reset on a failed start (W-7), the cleanup guard
// enforced inside CleanupClient (W-1), and literal custom-word replacement (W-3).
@MainActor
final class DictationOrchestrator: ObservableObject {
    struct Status { var state: DictationState; var title: String; var detail: String; var toast: String }
    @Published private(set) var status = Status(state: .idle, title: "Ready", detail: "", toast: "")

    private let settingsStore: SettingsStore
    private let secrets = SecretStore()
    private let history: HistoryStore
    private let audio: AudioCaptureService
    private let injector: FocusedTextInjector
    private let cleanup: CleanupClient
    private let session: URLSession

    private var target: FocusTarget?
    private var clipboardOnly = false
    private var busy = false // reentrancy gate == the Windows SemaphoreSlim.WaitAsync(0)

    init(settingsStore: SettingsStore, history: HistoryStore, audio: AudioCaptureService,
         injector: FocusedTextInjector, session: URLSession) {
        self.settingsStore = settingsStore; self.history = history; self.audio = audio
        self.injector = injector; self.session = session
        self.cleanup = CleanupClient(session: session)
        audio.onAutoStop = { [weak self] in Task { @MainActor in await self?.autoStop() } }
    }

    var state: DictationState { status.state }

    func toggle() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        if status.state == .idle || status.state == .error { await start() }
        else if status.state == .listening { await stopAndProcess() }
    }

    /// Records without a focused target and places the transcript on the clipboard.
    func toggleClipboard() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        if status.state == .idle || status.state == .error { clipboardOnly = true; await start() }
        else if status.state == .listening { await stopAndProcess() }
    }

    private func autoStop() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        if status.state == .listening { await stopAndProcess() }
    }

    private func start() async {
        let settings = await settingsStore.load()
        guard let key = secrets.get(settings.provider), !key.trimmed.isEmpty else {
            setStatus(.error, "API key required", "Add a \(settings.provider.displayName) key in Settings.")
            resetPending(); return
        }
        target = clipboardOnly ? nil : injector.captureTarget()
        if target?.isSensitive == true {
            setStatus(.error, "Protected field", "Quill never records for or types into password fields.")
            resetPending(); return
        }
        let diverted = (target?.hasEditableField == false)
        if diverted { clipboardOnly = true }
        do {
            try audio.start(settings)
            setStatus(.listening, "Listening", "Press \(settings.hotkey) again to finish.",
                      toast: diverted ? "No text field selected — this dictation will be copied to your clipboard." : "")
        } catch {
            setStatus(.error, "Microphone unavailable", error.localizedDescription)
            resetPending()
        }
    }

    private func stopAndProcess() async {
        setStatus(.transcribing, "Transcribing", "Your audio goes directly to your chosen provider.")
        let recording = audio.stop()
        defer {
            try? FileManager.default.removeItem(at: recording.url)
            target = nil; clipboardOnly = false
        }
        guard recording.hasSpeech else {
            setStatus(.idle, "Nothing heard", "No speech was detected, so nothing was sent to your provider.")
            return
        }
        let settings = await settingsStore.load()
        guard let key = secrets.get(settings.provider), !key.trimmed.isEmpty else {
            setStatus(.error, "Dictation stopped", "The API key is missing."); return
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
                setStatus(.cleaning, "Tidying", "Meaning and names are guarded before insertion.")
                let style = resolveStyle(settings, target?.application)
                let candidate = await cleanup.clean(text, provider: settings.provider, key: key,
                                                     model: settings.cleanupModel, style: style)
                cleaned = (candidate != text)
                text = candidate
            }
            text = Self.applyCustomWords(text, settings.customWords)

            setStatus(.inserting, clipboardOnly ? "Copying" : "Typing",
                      clipboardOnly ? "Clipboard" : (target?.application ?? "Focused application"))
            let result = clipboardOnly
                ? injector.copyToClipboard(text)
                : injector.insert(target ?? injector.captureTarget(), text)
            guard result.inserted || result.copiedToClipboard else {
                setStatus(.error, "Dictation stopped", result.error ?? "The system did not accept the text."); return
            }
            await history.add(HistoryEntry(
                text: text, application: clipboardOnly ? "Clipboard" : (target?.application ?? "Unknown app"),
                provider: settings.provider, wasCleaned: cleaned), settings: settings)
            setStatus(.idle, result.inserted ? "Ready" : "Copied to clipboard",
                      result.inserted ? "Press \(settings.hotkey) to dictate." : (result.error ?? "Paste it where you need it."),
                      toast: result.inserted ? "" : "Transcript copied — paste it with ⌘V.")
        } catch let e as TranscriptionException {
            setStatus(.error, "Dictation stopped", e.message)
        } catch {
            setStatus(.error, "Dictation stopped", error.localizedDescription)
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

    nonisolated static func applyCustomWords(_ text: String, _ words: [WordReplacement]) -> String {
        var out = text
        for w in words where !w.heard.trimmed.isEmpty {
            let pattern = "\\b\(NSRegularExpression.escapedPattern(for: w.heard))\\b"
            guard let rx = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { continue }
            // escapedTemplate makes the replacement literal — a '$' in it is NOT a capture-group ref (W-3).
            out = rx.stringByReplacingMatches(in: out, range: NSRange(out.startIndex..., in: out),
                                              withTemplate: NSRegularExpression.escapedTemplate(for: w.replacement))
        }
        return out
    }

    private func resolveStyle(_ s: AppSettings, _ application: String?) -> WritingStyle {
        if let application, let style = s.perAppStyles[application] { return style }
        return s.defaultWritingStyle
    }

    private func setStatus(_ state: DictationState, _ title: String, _ detail: String, toast: String = "") {
        status = Status(state: state, title: title, detail: detail, toast: toast)
    }

    private func resetPending() { clipboardOnly = false; target = nil }
}
