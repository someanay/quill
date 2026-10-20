import AppKit
import Combine
import Contacts
import AVFoundation
import ServiceManagement
import QuillCore

enum DictationState: Equatable { case idle, recording, transcribing, paste, error }
@MainActor final class Dictation: ObservableObject {
    @Published private(set) var state: DictationState = .idle
    @Published private(set) var level = 0.0
    @Published private(set) var elapsed = 0.0
    @Published private(set) var limitWarning = false
    @Published var message = ""
    @Published private(set) var transcript = ""
    @Published var shortcutAvailable = true
    @Published var microphoneGranted = AudioCapture.permitted
    @Published var accessibilityGranted = TextInjector.permitted
    @Published var microphoneTest = false
    @Published var testHeard = false
    @Published private(set) var testCaptureSucceeded = false
    @Published var recoveryWarning = false
    let settings: SettingsStore
    let keys: KeychainStore
    let history: HistoryStore
    let client: ProviderClient
    private let audio: AudioRecording
    private let microphonePermission: () -> Bool
    private let pendingStore: any PendingTakeStoring
    private var captureID: UUID?
    private let injector: TextInjector
    private var pipeline: Task<Void,Never>?
    private var recordingSettings: QuillSettings?
    private var target: FocusSnapshot?
    private var pendingAudio: Data?
    private var clipboardTarget = false
    private var wordCaptureMode = false
    private var recoveryUnavailable = false
    private var pasteSent = false
    private var generation = UUID()
    private var permissionTimer: Timer?
    private var lastHistoryRefresh = Date.distantPast
    private var audioRouteObserver: NSObjectProtocol?
    var onWordCaptured: ((String) -> Void)?
    init(settings: SettingsStore, keys: KeychainStore, history: HistoryStore, audio: AudioRecording? = nil, client: ProviderClient? = nil, pendingStore: (any PendingTakeStoring)? = nil, injector: TextInjector? = nil, microphonePermission: @escaping () -> Bool = { AudioCapture.permitted }) {
        self.settings = settings; self.keys = keys; self.history = history
        self.audio = audio ?? AudioCapture(); self.client = client ?? ProviderClient(); self.microphonePermission = microphonePermission
        self.pendingStore = pendingStore ?? PendingAudioStore()
        self.injector = injector ?? TextInjector()
        self.audio.onLevel = { [weak self] id, amplitude, duration, warn in Task { @MainActor in
            guard let self, self.captureID == id, self.state == .recording || self.microphoneTest else { return }
            self.level = self.level * 0.35 + amplitude * 0.65; self.elapsed = duration; self.limitWarning = warn
            if self.microphoneTest && amplitude > 0.06 { self.testHeard = true }
        } }
        self.audio.onAutoStop = { [weak self] id in Task { @MainActor in guard let self, self.captureID == id else { return }; if self.microphoneTest { self.stopMicTest() } else { self.stop() } } }
        permissionTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in Task { @MainActor in self?.refreshPermissions() } }
        audioRouteObserver = NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: nil, queue: .main) { [weak self] _ in MainActor.assumeIsolated {
            guard let self else { return }
            if self.state == .recording { self.stop() }
            else if self.microphoneTest { self.stopMicTest() }
        } }
        restorePending()
    }
    func refreshPermissions() {
        microphoneGranted = microphonePermission(); accessibilityGranted = TextInjector.permitted
        if accessibilityGranted && !settings.value.accessibilityEnabledOnce { settings.value.accessibilityEnabledOnce = true }
        if state == .recording && !microphoneGranted { suspend(); message = "mac.error.microphone" }
        if Date().timeIntervalSince(lastHistoryRefresh) >= 60 { history.refresh(settings: settings.value); lastHistoryRefresh = Date() }
    }
    func toggle() { if state == .recording { stop() } else if state == .paste { paste() } else if state == .error && pendingAudio != nil { retry() } else if state == .idle || state == .error { start() } }
    func start(wordCapture: Bool = false, toClipboard: Bool = false) {
        guard state != .recording && state != .transcribing else { return }
        guard pendingAudio == nil && state != .paste && !(state == .error && !transcript.isEmpty) && !recoveryUnavailable else { message = "mac.status.pending_retry"; return }
        guard cancel() else { return }; refreshPermissions()
        do {
            guard microphoneGranted else { throw QuillError.noMicrophone }
            let snapshot = settings.value
            if !wordCapture && !snapshot.accessibilityConsent { throw QuillError.noAccessibility }
            guard keys.active(snapshot.provider, settings: snapshot) != nil else { throw QuillError.badKey }
            guard !snapshot.speechModel.isEmpty else { throw QuillError.invalidModel }
            if snapshot.provider == .custom { _ = try Endpoint.validated(snapshot.customBaseURL) }
            let focused = wordCapture ? nil : try injector.snapshot()
            if !wordCapture && !toClipboard && focused == nil { throw QuillError.noFocusedField }
            target = toClipboard ? nil : focused
            clipboardTarget = toClipboard
            wordCaptureMode = wordCapture
            recordingSettings = snapshot; elapsed = 0; level = 0; limitWarning = false; transcript = ""; message = ""
            captureID = try audio.start(cap: wordCapture ? 8 : snapshot.sessionCapSeconds); state = .recording
        } catch { report(error) }
    }
    func stop() {
        guard state == .recording else { return }
        captureID = nil; pendingAudio = audio.stop(); level = 0
        guard (pendingAudio?.count ?? 0) > 44 else { pendingAudio = nil; state = .idle; return }
        persistPending()
        transcribe()
    }
    func retry() { guard pendingAudio != nil, state == .error, !recoveryUnavailable else { return }; transcribe() }
    private func transcribe() {
        guard let audio = pendingAudio, let snapshot = recordingSettings else { return }
        var credentialSettings = settings.value
        credentialSettings.customBaseURL = snapshot.customBaseURL
        guard let key = keys.active(snapshot.provider, settings: credentialSettings) else { report(QuillError.badKey); return }
        state = .transcribing; message = ""; let token = generation
        pipeline = Task { [weak self] in
            guard let self else { return }
            do {
                let options = SpeechRequest(provider: snapshot.provider, model: snapshot.speechModel, baseURL: snapshot.customBaseURL, language: snapshot.language)
                let raw = try await client.transcribe(audio: audio, options: options, key: key)
                try Task.checkCancellation(); guard token == generation else { return }
                if wordCaptureMode, let captured = onWordCaptured {
                    transcript = raw; pendingAudio = nil
                    guard persistPending() else { target = nil; state = .paste; message = "mac.error.pending_storage"; return }
                    onWordCaptured = nil; clearSavedTake(); transcript = ""; recordingSettings = nil; state = .idle
                    captured(raw.trimmingCharacters(in: .whitespacesAndNewlines)); return
                }
                if wordCaptureMode { transcript = raw; pendingAudio = nil; persistPending(); state = .paste; message = "mac.status.ready_to_copy"; return }
                var final = raw
                if snapshot.shouldClean, let cleanupKey = keys.active(snapshot.effectiveCleanupProvider, settings: credentialSettings) {
                    final = try await client.clean(raw, provider: snapshot.effectiveCleanupProvider, model: snapshot.effectiveCleanupModel, baseURL: snapshot.customBaseURL, key: cleanupKey, style: snapshot.style(for: target?.bundleID))
                }
                try Task.checkCancellation(); guard token == generation else { return }
                final = WordSubstitutions.apply(final, words: snapshot.words)
                transcript = final; pendingAudio = nil
                // Retire retryable audio durably before any automatic delivery. If
                // storage cannot replace it, a restart must not offer delivered audio
                // for transcription again. Keep the completed text for manual recovery.
                guard persistPending() else { target = nil; state = .paste; message = "mac.error.pending_storage"; return }
                var historySettings = settings.value
                historySettings.privateMode = historySettings.privateMode || snapshot.privateMode
                historySettings.historyEnabled = historySettings.historyEnabled && snapshot.historyEnabled
                history.insert(HistoryEntry(text: final, provider: snapshot.provider, appName: target?.appName ?? "", duration: elapsed), settings: historySettings)
                if clipboardTarget {
                    guard ClipboardLease.write(final) else { state = .paste; message = "mac.error.clipboard"; return }
                    clearSavedTake(); message = "mac.status.copied"; state = .idle; transcript = settings.value.privateMode || snapshot.privateMode ? "" : final; target = nil; recordingSettings = nil; return
                }
                do {
                    let insertion = try await injector.insert(final, target: target)
                    guard token == generation, !Task.isCancelled else { return }
                    switch insertion {
                    case .confirmed: state = .idle
                    case .unconfirmed: state = .paste; message = "mac.status.unconfirmed"
                    case .unavailable: state = .paste; message = "mac.status.ready_to_copy"
                    }
                }
                catch { guard token == generation, !Task.isCancelled else { return }; message = (error as? QuillError)?.copyKey ?? "mac.error.focus_changed"; state = .paste }
                if state == .idle { clearSavedTake(); message = "mac.status.inserted"; target = nil; recordingSettings = nil; if settings.value.privateMode || snapshot.privateMode { transcript = "" } }
            } catch is CancellationError { }
            catch { if token == generation { persistPending(); report(error) } }
        }
    }
    func paste() {
        guard !transcript.isEmpty else { return }
        guard target != nil else { message = "mac.status.ready_to_copy"; return }
        guard !pasteSent else { message = "mac.status.pasted"; return }
        do { if try injector.paste(transcript, target: target) { state = .paste; message = "mac.status.pasted"; pasteSent = true } }
        catch { report(error); state = .paste }
    }
    func copyTranscript() {
        guard !transcript.isEmpty else { return }
        generation = UUID(); pipeline?.cancel(); pipeline = nil
        target = nil; state = .paste
        guard persistPending() else { message = "mac.error.pending_storage"; return }
        guard ClipboardLease.write(transcript) else { message = "mac.error.clipboard"; return }
        clearSavedTake(); message = "mac.status.copied"; state = .idle; transcript = ""; recordingSettings = nil
    }
    @discardableResult func cancel() -> Bool {
        generation = UUID(); pipeline?.cancel(); pipeline = nil; captureID = nil; audio.cancel()
        microphoneTest = false; onWordCaptured = nil; level = 0
        guard clearSavedTake() else {
            // Do not make a failed Discard look successful or let a new recording
            // overwrite recovery that could not be removed. Explicit Discard can retry.
            recoveryUnavailable = true; state = .error; message = "mac.error.pending_storage"; return false
        }
        if let count = pendingAudio?.count { pendingAudio?.resetBytes(in: 0..<count) }; pendingAudio = nil
        state = .idle; level = 0; elapsed = 0; transcript = ""; target = nil; recordingSettings = nil; microphoneTest = false; onWordCaptured = nil
        clipboardTarget = false
        wordCaptureMode = false
        recoveryUnavailable = false
        pasteSent = false
        return true
    }
    func suspend() {
        if microphoneTest { stopMicTest(); return }
        guard state == .recording || state == .transcribing else { return }
        generation = UUID(); pipeline?.cancel(); pipeline = nil
        if state == .recording { captureID = nil; pendingAudio = audio.stop(); level = 0 }
        persistPending()
        if pendingAudio == nil && !transcript.isEmpty {
            // The OS may still complete an accepted insertion after cancellation.
            // Retain manual Copy recovery and prevent a new capture from erasing it.
            target = nil; state = .paste; message = "mac.status.unconfirmed"
        } else { state = .error; message = "mac.status.sleep_retry" }
    }
    func prepareForTermination() {
        if state == .recording || state == .transcribing { suspend() }
        if microphoneTest { stopMicTest() }
        pipeline?.cancel(); captureID = nil; audio.cancel()
    }
    @discardableResult private func persistPending() -> Bool {
        guard var snapshot = recordingSettings, pendingAudio != nil || !transcript.isEmpty else { return true }
        snapshot.privateMode = snapshot.privateMode || settings.value.privateMode
        snapshot.historyEnabled = snapshot.historyEnabled && settings.value.historyEnabled
        recordingSettings = snapshot
        do { try pendingStore.save(PendingTake(audio: pendingAudio, transcript: transcript.isEmpty ? nil : transcript, settings: snapshot, duration: elapsed, clipboardOnly: clipboardTarget, wordCapture: wordCaptureMode)); recoveryWarning = false; return true }
        catch { recoveryWarning = true; message = "mac.error.pending_storage"; return false }
    }
    @discardableResult private func clearSavedTake() -> Bool { do { try pendingStore.clear(); recoveryWarning = false; return true } catch { recoveryWarning = true; message = "mac.error.storage"; return false } }
    private func restorePending() {
        do {
            guard let saved = try pendingStore.load() else { return }
            recordingSettings = saved.settings; elapsed = saved.duration; clipboardTarget = saved.clipboardOnly; wordCaptureMode = saved.wordCapture
            pendingAudio = saved.audio; transcript = saved.transcript ?? ""
            state = transcript.isEmpty ? .error : .paste; message = "mac.status.recovered"
        } catch { recoveryUnavailable = true; recoveryWarning = true; state = .error; message = "mac.error.pending_storage" }
    }
    func startMicTest() {
        guard state == .idle, pendingAudio == nil, !recoveryUnavailable else { message = "mac.status.pending_retry"; return }
        guard cancel() else { return }; testHeard = false; testCaptureSucceeded = false
        do { microphoneTest = true; captureID = try audio.start(cap: 30); testCaptureSucceeded = true } catch { microphoneTest = false; report(error) }
    }
    func stopMicTest() { captureID = nil; audio.cancel(); microphoneTest = false; level = 0 }
    func report(_ error: Error) { message = (error as? QuillError)?.copyKey ?? "mac.error.provider"; state = .error }
    func configureLogin() {
        guard !AppPaths.testMode else { return }
        do { if settings.value.bootRestore && settings.value.onboardingComplete { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() } }
        catch { message = "mac.error.login" }
    }
    func importContacts() async -> Bool {
        message = ""
        do {
            let store = CNContactStore()
            guard try await store.requestAccess(for: .contacts) else { message = "mac.error.contacts"; return false }
            let request = CNContactFetchRequest(keysToFetch: [CNContactGivenNameKey as CNKeyDescriptor, CNContactFamilyNameKey as CNKeyDescriptor])
            var names: Set<String> = []
            try store.enumerateContacts(with: request) { contact, _ in
                let name = [contact.givenName,contact.familyName].filter { !$0.isEmpty }.joined(separator: " ")
                if !name.isEmpty { names.insert(name) }
            }
            settings.value.contactNames = ContactVocabulary.extract(names.sorted())
            return true
        } catch { message = "mac.error.contacts"; return false }
    }
}
extension Notification.Name { static let quillExplicitVisibility = Notification.Name("QuillExplicitVisibility") }
