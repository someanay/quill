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
    @Published var microphoneGranted = AudioCapture.permitted
    @Published var accessibilityGranted = TextInjector.permitted
    @Published var microphoneTest = false
    @Published var testHeard = false
    let settings: SettingsStore
    let keys: KeychainStore
    let history: HistoryStore
    let client = ProviderClient()
    private let audio = AudioCapture()
    private let injector = TextInjector()
    private var pipeline: Task<Void,Never>?
    private var recordingSettings: QuillSettings?
    private var target: FocusSnapshot?
    private var pendingAudio: Data?
    private var clipboardTarget = false
    private var pasteSent = false
    private var generation = UUID()
    private var permissionTimer: Timer?
    private var lastHistoryRefresh = Date.distantPast
    private var audioRouteObserver: NSObjectProtocol?
    var onWordCaptured: ((String) -> Void)?
    init(settings: SettingsStore, keys: KeychainStore, history: HistoryStore) {
        self.settings = settings; self.keys = keys; self.history = history
        audio.onLevel = { [weak self] amplitude, duration, warn in Task { @MainActor in
            guard let self, self.state == .recording || self.microphoneTest else { return }
            self.level = self.level * 0.35 + amplitude * 0.65; self.elapsed = duration; self.limitWarning = warn
            if self.microphoneTest && amplitude > 0.06 { self.testHeard = true }
        } }
        audio.onAutoStop = { [weak self] in Task { @MainActor in guard let self else { return }; if self.microphoneTest { self.stopMicTest() } else { self.stop() } } }
        permissionTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in Task { @MainActor in self?.refreshPermissions() } }
        audioRouteObserver = NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: nil, queue: .main) { [weak self] _ in Task { @MainActor in
            guard let self else { return }
            if self.state == .recording { self.stop() }
            else if self.microphoneTest { self.stopMicTest() }
        } }
    }
    func refreshPermissions() {
        microphoneGranted = AudioCapture.permitted; accessibilityGranted = TextInjector.permitted
        if accessibilityGranted && !settings.value.accessibilityEnabledOnce { settings.value.accessibilityEnabledOnce = true }
        if state == .recording && !microphoneGranted { cancel(); message = "mac.error.microphone" }
        if Date().timeIntervalSince(lastHistoryRefresh) >= 60 { history.refresh(settings: settings.value); lastHistoryRefresh = Date() }
    }
    func toggle() { if state == .recording { stop() } else if state == .paste { paste() } else if state == .idle || state == .error { start() } }
    func start(wordCapture: Bool = false, toClipboard: Bool = false) {
        guard state != .recording && state != .transcribing else { return }
        cancel(); refreshPermissions()
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
            recordingSettings = snapshot; elapsed = 0; level = 0; limitWarning = false; transcript = ""; message = ""
            try audio.start(cap: wordCapture ? 30 : snapshot.sessionCapSeconds); state = .recording
        } catch { report(error) }
    }
    func stop() {
        guard state == .recording else { return }
        pendingAudio = audio.stop(); level = 0
        guard (pendingAudio?.count ?? 0) > 44 else { pendingAudio = nil; state = .idle; return }
        transcribe()
    }
    func retry() { guard pendingAudio != nil, state == .error else { return }; transcribe() }
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
                var final = raw
                if snapshot.shouldClean, let cleanupKey = keys.active(snapshot.effectiveCleanupProvider, settings: credentialSettings) {
                    final = try await client.clean(raw, provider: snapshot.effectiveCleanupProvider, model: snapshot.effectiveCleanupModel, baseURL: snapshot.customBaseURL, key: cleanupKey, style: snapshot.style(for: target?.bundleID))
                }
                try Task.checkCancellation(); guard token == generation else { return }
                final = WordSubstitutions.apply(final, words: snapshot.words)
                transcript = final; pendingAudio = nil
                if let captured = onWordCaptured { onWordCaptured = nil; captured(final); state = .idle; return }
                history.insert(HistoryEntry(text: final, provider: snapshot.provider, appName: target?.appName ?? "", duration: elapsed), settings: settings.value)
                if clipboardTarget { ClipboardLease.write(final); message = "mac.status.copied"; state = .idle; transcript = settings.value.privateMode ? "" : final; target = nil; recordingSettings = nil; return }
                do {
                    switch try injector.insert(final, target: target) {
                    case .confirmed: state = .idle
                    case .unconfirmed: state = .paste; message = "mac.status.unconfirmed"
                    case .unavailable: state = .paste; message = "mac.status.ready_to_copy"
                    }
                }
                catch { message = (error as? QuillError)?.copyKey ?? "mac.error.focus_changed"; state = .paste }
                if state == .idle { message = "mac.status.inserted"; target = nil; recordingSettings = nil; if settings.value.privateMode { transcript = "" } }
            } catch is CancellationError { }
            catch { if token == generation { report(error) } }
        }
    }
    func paste() {
        guard !transcript.isEmpty else { return }
        guard !pasteSent else { message = "mac.status.pasted"; return }
        do { if try injector.paste(transcript, target: target) { state = .paste; message = "mac.status.pasted"; pasteSent = true } }
        catch { report(error); state = .paste }
    }
    func copyTranscript() { guard !transcript.isEmpty else { return }; ClipboardLease.write(transcript); message = "mac.status.copied"; state = .idle; transcript = ""; target = nil; recordingSettings = nil }
    func cancel() {
        generation = UUID(); pipeline?.cancel(); pipeline = nil; audio.cancel(); if let count = pendingAudio?.count { pendingAudio?.resetBytes(in: 0..<count) }; pendingAudio = nil
        state = .idle; level = 0; elapsed = 0; transcript = ""; target = nil; recordingSettings = nil; microphoneTest = false; onWordCaptured = nil
        clipboardTarget = false
        pasteSent = false
    }
    func suspend() {
        if microphoneTest { stopMicTest(); return }
        guard state == .recording || state == .transcribing else { return }
        generation = UUID(); pipeline?.cancel(); pipeline = nil
        if state == .recording { pendingAudio = audio.stop(); level = 0 }
        state = .error; message = "mac.status.sleep_retry"
    }
    func startMicTest() {
        cancel(); testHeard = false
        do { microphoneTest = true; try audio.start(cap: 30) } catch { microphoneTest = false; report(error) }
    }
    func stopMicTest() { audio.cancel(); microphoneTest = false; level = 0 }
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
