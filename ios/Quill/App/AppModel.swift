import SwiftUI
import Contacts
import QuillCore

enum DictationPhase: String { case idle, recording, transcribing, cleaning, ready, failed }

@MainActor final class AppModel: ObservableObject {
    @Published var settings: QuillSettings { didSet { persistSettings() } }
    @Published private(set) var phase: DictationPhase = .idle
    @Published var result = ""
    @Published var notice: String?
    @Published var errorKey: String?
    @Published var history: [HistoryEntry] = []
    @Published var words: [CustomWord] = []
    @Published var contactNames: [String] = []
    @Published var keys: [SavedKey] = []
    @Published var tab = 0
    @Published var modelChoices: [ModelOption] = []
    @Published var loadingModels = false
    @Published var keyValidationRunning = false
    @Published var voiceWordResult = ""
    @Published var microphoneGranted = AudioRecorder.permission == .granted
    let recorder = AudioRecorder()
    let vault = KeyVault()
    let copy: CopyCatalog
    private let providerAPI = ProviderAPI()
    private var historyStore: HistoryStore?
    private var operation: Task<Void, Never>?
    private var pruneTimer: Timer?
    private var retainedAudio: URL?
    private var retainedUntil: Date?
    private var snapshot: QuillSettings?
    private var snapshotWords: [CustomWord] = []
    private var generation = UUID()
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    private var testingMicrophone = false
    private var voiceSampleMode = false

    init() {
        let bundle = Bundle.main
        let url = bundle.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? bundle.url(forResource: "strings.default", withExtension: "json")
        copy = (url.flatMap { try? Data(contentsOf: $0) }.flatMap { try? CopyCatalog(data: $0) }) ?? CopyCatalog(strings: [:])
        settings = AppFiles.read(QuillSettings.self, name: "settings.json") ?? QuillSettings()
        if AppFiles.isUITesting { settings = QuillSettings() }
        settings.sanitize()
        do { try AppFiles.prepare(); historyStore = try HistoryStore() }
        catch { errorKey = QuillError.storage.copyKey }
        words = AppFiles.read([CustomWord].self, name: "words.json") ?? []
        contactNames = AppFiles.read([String].self, name: "contacts.json") ?? []
        keys = vault.keys
        AppFiles.purgeAudio(); KeyboardTransfer.prune()
        recorder.onAutomaticStop = { [weak self] in
            guard let self else { return }
            if self.testingMicrophone { self.stopMicTest() } else { self.stopAndTranscribe() }
        }
        recorder.onInterruption = { [weak self] in self?.interrupted() }
        pruneTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in Task { @MainActor in self?.prune() } }
        refreshHistory()
    }
    deinit { pruneTimer?.invalidate(); operation?.cancel() }
    func c(_ key: String, _ args: String...) -> String { copy.format(key, args: args) }
    var busy: Bool { [.recording, .transcribing, .cleaning].contains(phase) }
    var canRetry: Bool { retainedAudio != nil && (retainedUntil ?? .distantPast) > Date() && phase == .failed }
    var hasActiveKey: Bool { (try? endpoint(settings.provider)).flatMap { vault.active(settings.provider, endpoint: $0) } != nil }
    func endpoint(_ provider: Provider) throws -> String { try Endpoint.normalize(provider == .custom ? settings.customBaseURL : provider.baseURL) }
    func activeKey(_ provider: Provider) -> UUID? { (try? endpoint(provider)).flatMap { vault.active(provider, endpoint: $0)?.id } }
    private func persistSettings() {
        do { try AppFiles.write(settings, name: "settings.json") }
        catch { errorKey = QuillError.storage.copyKey }
    }
    func report(_ error: Error) { errorKey = (error as? QuillError ?? .storage).copyKey }
    func selectProvider(_ provider: Provider) { settings.selectProvider(provider); modelChoices = [] }
    func selectCleanupProvider(_ provider: Provider) {
        settings.cleanupAutomatic = false; settings.cleanupProvider = provider; settings.cleanupModel = provider.defaultCleanupModel
        modelChoices = []
    }
    func verifyAndSaveKey(provider: Provider, secret: String, label: String) async -> Bool {
        guard !keyValidationRunning else { return false }
        keyValidationRunning = true; defer { keyValidationRunning = false }
        do {
            let config = try ProviderConfiguration(provider: provider, customBaseURL: settings.customBaseURL, model: provider == .custom ? settings.customModel : provider.defaultModel)
            let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
            try await providerAPI.validate(config, key: trimmed)
            try Task.checkCancellation()
            // Do not save a custom key against an endpoint changed while validation was in flight.
            guard config.endpoint == (try endpoint(provider)) else { throw QuillError.invalidEndpoint }
            try vault.save(trimmed, provider: provider, endpoint: config.endpoint, label: label)
            keys = vault.keys; notice = c("onboarding.key.valid")
            if !hasActiveKey { selectProvider(provider) }
            return true
        } catch is CancellationError { return false }
        catch { report(error); return false }
    }
    func activateKey(_ key: SavedKey) { do { try vault.activate(key); keys = vault.keys } catch { report(error) } }
    func deleteKey(_ key: SavedKey) { do { try vault.delete(key); keys = vault.keys } catch { report(error) } }
    func testKey(_ key: SavedKey) async {
        do {
            let configuration = try ProviderConfiguration(provider: key.provider, customBaseURL: key.endpoint, model: key.provider == .custom ? settings.customModel : key.provider.defaultModel)
            try await providerAPI.validate(configuration, key: vault.read(key))
            notice = c("onboarding.key.valid")
        } catch { report(error) }
    }
    func refreshModels(cleanup: Bool) async {
        guard !loadingModels else { return }
        loadingModels = true; defer { loadingModels = false }
        do {
            let provider = cleanup ? settings.effectiveCleanupProvider : settings.provider
            let model = cleanup ? settings.effectiveCleanupModel : settings.model
            let config = try ProviderConfiguration(provider: provider, customBaseURL: settings.customBaseURL, model: model)
            let key = try vault.secret(provider, endpoint: config.endpoint)
            modelChoices = try await providerAPI.models(config, key: key, cleanup: cleanup)
            if modelChoices.isEmpty { notice = c("ios.models.empty") }
        } catch { report(error) }
    }
    func requestMicrophone() async {
        microphoneGranted = await AudioRecorder.requestPermission()
        if !microphoneGranted { errorKey = "ios.mic.blocked" }
    }
    func resume() {
        microphoneGranted = AudioRecorder.permission == .granted
        if !microphoneGranted && recorder.isRecording { interrupted() }
        prune()
    }
    func startMicTest() async {
        if !microphoneGranted { await requestMicrophone() }
        guard microphoneGranted, !busy else { return }
        do { testingMicrophone = true; try recorder.start(capSeconds: 30, microphoneTest: true) }
        catch { testingMicrophone = false; report(error) }
    }
    func stopMicTest() { recorder.cancel(); testingMicrophone = false }
    func start(voiceSample: Bool = false) async {
        guard !busy else { return }
        stopMicTest()
        if !microphoneGranted { await requestMicrophone() }
        guard microphoneGranted else { return }
        do {
            guard hasActiveKey else { throw QuillError.noKey }
            _ = try ProviderConfiguration(provider: settings.provider, customBaseURL: settings.customBaseURL, model: settings.model, language: settings.language)
            discardRetry(); result = ""; errorKey = nil; notice = nil
            snapshot = settings; snapshotWords = words; generation = UUID()
            voiceSampleMode = voiceSample
            try recorder.start(capSeconds: settings.sessionCapSeconds)
            phase = .recording
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } catch { phase = .failed; report(error) }
    }
    func stopAndTranscribe() {
        guard phase == .recording, let file = recorder.stop() else { return }
        retainedAudio = file; retainedUntil = Date().addingTimeInterval(600)
        beginTranscription()
    }
    func retry() { guard canRetry else { return }; errorKey = nil; beginTranscription() }
    private func beginTranscription() {
        guard let file = retainedAudio, let capture = snapshot else { return }
        operation?.cancel(); let token = generation
        phase = .transcribing
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Finish dictation") { [weak self] in
            Task { @MainActor in self?.processingExpired() }
        }
        operation = Task { [weak self] in
            guard let self else { return }
            defer { self.endBackgroundTask() }
            do {
                let config = try ProviderConfiguration(provider: capture.provider, customBaseURL: capture.customBaseURL, model: capture.model, language: capture.language)
                let key = try self.vault.secret(capture.provider, endpoint: config.endpoint)
                let wav = try Data(contentsOf: file)
                let raw = try await self.providerAPI.transcribe(config, key: key, wav: wav)
                try Task.checkCancellation()
                var cleaned: String?
                if !self.voiceSampleMode, capture.effectiveCleanupEnabled,
                   let cleanup = try? ProviderConfiguration(provider: capture.effectiveCleanupProvider, customBaseURL: capture.customBaseURL, model: capture.effectiveCleanupModel),
                   let cleanupKey = try? self.vault.secret(cleanup.provider, endpoint: cleanup.endpoint) {
                    self.phase = .cleaning
                    cleaned = try await self.providerAPI.clean(cleanup, key: cleanupKey, raw: raw, style: capture.writingStyle)
                }
                try Task.checkCancellation()
                guard self.generation == token else { return }
                let final = self.voiceSampleMode ? raw : TextPipeline.finish(raw: raw, cleaned: cleaned, words: self.snapshotWords)
                if self.voiceSampleMode { self.voiceWordResult = final } else { self.result = final }
                self.phase = .ready
                if !self.voiceSampleMode && capture.shouldSaveHistory && self.settings.shouldSaveHistory {
                    do { try self.historyStore?.save(HistoryEntry(text: final, provider: capture.provider)); self.refreshHistory() }
                    catch { self.report(error) }
                }
                self.discardRetry()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } catch is CancellationError { }
            catch {
                guard self.generation == token else { return }
                self.phase = .failed; self.report(error)
                if capture.privateMode || self.settings.privateMode { self.discardRetry() }
            }
        }
    }
    private func interrupted() {
        if testingMicrophone { stopMicTest(); errorKey = QuillError.interrupted.copyKey; return }
        guard phase == .recording else { return }
        retainedAudio = recorder.stop(); retainedUntil = Date().addingTimeInterval(600)
        phase = .failed; errorKey = QuillError.interrupted.copyKey
        if settings.privateMode || snapshot?.privateMode == true { discardRetry() }
    }
    private func processingExpired() {
        operation?.cancel(); generation = UUID(); phase = .failed; errorKey = QuillError.interrupted.copyKey
        if settings.privateMode || snapshot?.privateMode == true { discardRetry() }
        endBackgroundTask()
    }
    private func endBackgroundTask() {
        if backgroundTask != .invalid { UIApplication.shared.endBackgroundTask(backgroundTask); backgroundTask = .invalid }
    }
    func cancel() {
        generation = UUID(); operation?.cancel(); operation = nil
        recorder.cancel(); testingMicrophone = false; discardRetry(); endBackgroundTask()
        result = ""; phase = .idle; errorKey = nil
    }
    private func discardRetry() {
        if let retainedAudio { try? FileManager.default.removeItem(at: retainedAudio) }
        retainedAudio = nil; retainedUntil = nil
    }
    func copyText(_ text: String) {
        UIPasteboard.general.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
        notice = c("ios.result.copied")
    }
    func sendToKeyboard(_ text: String) {
        do { try KeyboardTransfer.publish(text); notice = c("ios.result.sent") }
        catch { report(error) }
    }
    func clearTransfer() { do { try KeyboardTransfer.clear(); notice = c("ios.transfer.cleared") } catch { report(error) } }
    func refreshHistory() { do { history = try historyStore?.entries(retentionMinutes: settings.retentionMinutes) ?? [] } catch { report(error) } }
    func deleteHistory(_ entry: HistoryEntry) { do { try historyStore?.delete(entry.id); refreshHistory() } catch { report(error) } }
    func clearHistory() { do { try historyStore?.clear(); refreshHistory() } catch { report(error) } }
    func setPrivateMode(_ value: Bool) {
        settings.privateMode = value
        if value {
            try? KeyboardTransfer.clear()
            if phase == .failed { discardRetry() }
        }
    }
    func saveWords() { do { try AppFiles.write(words, name: "words.json") } catch { report(error) } }
    func addWord(heard: String, replacement: String) {
        let heard = heard.trimmingCharacters(in: .whitespacesAndNewlines), replacement = replacement.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !heard.isEmpty, !replacement.isEmpty else { return }
        words.append(CustomWord(heard: String(heard.prefix(200)), replacement: String(replacement.prefix(200))))
        saveWords()
    }
    func importContacts() async {
        do {
            let store = CNContactStore()
            guard try await store.requestAccess(for: .contacts) else { throw QuillError.storage }
            let names = try await Task.detached(priority: .userInitiated) {
                let keys: [CNKeyDescriptor] = [CNContactGivenNameKey as CNKeyDescriptor, CNContactFamilyNameKey as CNKeyDescriptor, CNContactMiddleNameKey as CNKeyDescriptor]
                var names: [String] = []
                try CNContactStore().enumerateContacts(with: CNContactFetchRequest(keysToFetch: keys)) { contact, stop in
                    names.append([contact.givenName, contact.middleName, contact.familyName].filter { !$0.isEmpty }.joined(separator: " "))
                    if names.count >= 20_000 { stop.pointee = true }
                }
                return ContactVocabulary.extract(names)
            }.value
            contactNames = names; try AppFiles.write(names, name: "contacts.json")
        } catch { errorKey = "ios.contacts.denied" }
    }
    func clearContacts() { contactNames = []; do { try AppFiles.write(contactNames, name: "contacts.json") } catch { report(error) } }
    private func prune() {
        KeyboardTransfer.prune(); refreshHistory()
        if let until = retainedUntil, until <= Date(), phase == .failed { discardRetry(); errorKey = QuillError.expired.copyKey }
    }
    func reset() {
        cancel()
        do {
            try vault.clear(); try KeyboardTransfer.clear(); try historyStore?.clear()
            words = []; contactNames = []; keys = []; history = []
            try AppFiles.write(words, name: "words.json"); try AppFiles.write(contactNames, name: "contacts.json")
            settings = QuillSettings(); tab = 0
        } catch { report(error) }
    }
    var diagnostics: String {
        // Only a fixed whitelist: no provider response, host field content, keys, model IDs,
        // endpoint, contact data, transcript or file paths ever enter this report.
        "Quill iOS\nVersion: \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")\niOS: \(UIDevice.current.systemVersion)\nMicrophone allowed: \(microphoneGranted)\nPhase: \(phase.rawValue)\nPrivate mode: \(settings.privateMode)"
    }
}
