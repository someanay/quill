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
    let diagnosticsCollector = DiagnosticsCollector()
    let copy: CopyCatalog
    private let providerAPI: ProviderAPI
    private var historyStore: (any HistoryRepository)?
    private var operation: Task<Void, Never>?
    private var pruneTimer: Timer?
    private var retainedAudio: URL?
    private var snapshot: QuillSettings?
    private var snapshotWords: [CustomWord] = []
    private var generation = UUID()
    private var modelCatalogGeneration = UUID()
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    private var testingMicrophone = false
    private var voiceSampleMode = false

    init(providerAPI: ProviderAPI = ProviderAPI(), historyStore: (any HistoryRepository)? = nil) {
        self.providerAPI = providerAPI
        let bundle = Bundle.main
        let url = bundle.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? bundle.url(forResource: "strings.default", withExtension: "json")
        copy = (url.flatMap { try? Data(contentsOf: $0) }.flatMap { try? CopyCatalog(data: $0) }) ?? CopyCatalog(strings: [:])
        settings = AppFiles.read(QuillSettings.self, name: "settings.json") ?? QuillSettings()
        if AppFiles.isUITesting { settings = QuillSettings() }
        settings.sanitize()
        do { try AppFiles.prepare(); self.historyStore = try historyStore ?? HistoryStore() }
        catch { errorKey = QuillError.storage.copyKey }
        words = AppFiles.read([CustomWord].self, name: "words.json") ?? []
        contactNames = AppFiles.read([String].self, name: "contacts.json") ?? []
        keys = vault.keys
        diagnosticsCollector.onUpdate = { [weak self] in self?.objectWillChange.send() }
        diagnosticsCollector.setEnabled(settings.crashReportingEnabled)
        if let pending = AppFiles.read(PendingRecording.self, name: "pending-recording.json"), let url = pending.url,
           FileManager.default.fileExists(atPath: url.path) {
            retainedAudio = url; snapshot = pending.settings; snapshotWords = pending.words; voiceSampleMode = pending.voiceSample
            phase = .failed; notice = copy.text("ios.recording.retry_retention")
        }
        AppFiles.purgeAudio(keeping: retainedAudio); KeyboardTransfer.prune()
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
    var canRetry: Bool { retainedAudio != nil && phase == .failed }
    var hasActiveKey: Bool { (try? endpoint(settings.provider)).flatMap { vault.active(settings.provider, endpoint: $0) } != nil }
    func endpoint(_ provider: Provider) throws -> String { try Endpoint.normalize(provider == .custom ? settings.customBaseURL : provider.baseURL) }
    func activeKey(_ provider: Provider) -> UUID? { (try? endpoint(provider)).flatMap { vault.active(provider, endpoint: $0)?.id } }
    private func persistSettings() {
        diagnosticsCollector.setEnabled(settings.crashReportingEnabled)
        do { try AppFiles.write(settings, name: "settings.json") }
        catch { errorKey = QuillError.storage.copyKey }
    }
    func report(_ error: Error) { errorKey = (error as? QuillError ?? .storage).copyKey }
    func invalidateModelChoices() { modelCatalogGeneration = UUID(); modelChoices = []; loadingModels = false }
    func selectProvider(_ provider: Provider) { settings.selectProvider(provider); invalidateModelChoices() }
    func selectCleanupProvider(_ provider: Provider) {
        settings.selectCleanupProvider(provider)
        invalidateModelChoices()
    }
    func verifyAndSaveKey(provider: Provider, secret: String, label: String, activateSpeechProvider: Bool = false) async -> Bool {
        guard !keyValidationRunning else { return false }
        keyValidationRunning = true; defer { keyValidationRunning = false }
        do {
            let config = try ProviderConfiguration(provider: provider, customBaseURL: settings.customBaseURL, model: provider == .custom ? settings.customModel : provider.defaultModel)
            let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
            try await providerAPI.validate(config, key: trimmed)
            let choices = activateSpeechProvider ? ((try? await providerAPI.models(config, key: trimmed, cleanup: false)) ?? []) : []
            try Task.checkCancellation()
            // Do not save a custom key against an endpoint changed while validation was in flight.
            guard config.endpoint == (try endpoint(provider)) else { throw QuillError.invalidEndpoint }
            try vault.save(trimmed, provider: provider, endpoint: config.endpoint, label: label)
            keys = vault.keys; notice = c("onboarding.key.valid")
            if activateSpeechProvider {
                selectProvider(provider)
                if let preferred = choices.first(where: \.isFree) { settings.model = preferred.id }
            }
            return true
        } catch is CancellationError { return false }
        catch { report(error); return false }
    }
    func validateKeyCandidate(provider: Provider, secret: String) async throws {
        let config = try ProviderConfiguration(provider: provider, customBaseURL: settings.customBaseURL, model: provider.defaultModel)
        try await providerAPI.validate(config, key: secret)
        try Task.checkCancellation()
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
        let token = modelCatalogGeneration
        loadingModels = true; defer { if token == modelCatalogGeneration { loadingModels = false } }
        do {
            let provider = cleanup ? settings.effectiveCleanupProvider : settings.provider
            let model = cleanup ? settings.effectiveCleanupModel : settings.model
            let config = try ProviderConfiguration(provider: provider, customBaseURL: settings.customBaseURL, model: model)
            let key: String
            if provider == .openrouter { key = (try? vault.secret(provider, endpoint: config.endpoint)) ?? "" }
            else { key = try vault.secret(provider, endpoint: config.endpoint) }
            let choices = try await providerAPI.models(config, key: key, cleanup: cleanup)
            try Task.checkCancellation()
            guard token == modelCatalogGeneration,
                  provider == (cleanup ? settings.effectiveCleanupProvider : settings.provider),
                  config.endpoint == (try endpoint(provider)) else { return }
            modelChoices = choices
            if modelChoices.isEmpty { notice = c("ios.models.empty") }
        } catch is CancellationError { }
        catch { if token == modelCatalogGeneration { report(error) } }
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
        guard !busy, !canRetry else { return }
        stopMicTest()
        if !microphoneGranted { await requestMicrophone() }
        guard microphoneGranted else { return }
        do {
            guard hasActiveKey else { throw QuillError.noKey }
            _ = try ProviderConfiguration(provider: settings.provider, customBaseURL: settings.customBaseURL, model: settings.model, language: settings.language)
            discardRetry(); result = ""; errorKey = nil; notice = nil
            snapshot = settings; snapshotWords = words; generation = UUID()
            voiceSampleMode = voiceSample
            try recorder.start(capSeconds: voiceSample ? 8 : settings.sessionCapSeconds)
            retainedAudio = recorder.fileURL
            try persistPendingRecording()
            phase = .recording
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } catch { if recorder.isRecording { retainedAudio = recorder.stop() }; phase = .failed; report(error) }
    }
    func stopAndTranscribe() {
        guard phase == .recording, let file = recorder.stop() else { return }
        guard let snapshot else { return }
        do { try processCapturedRecording(file, configuration: snapshot, words: snapshotWords, voiceSample: voiceSampleMode) }
        catch { phase = .failed; report(error) }
    }
    /// The capture adapter hands a finalized app-owned file to the pipeline. Tests inject only
    /// the transport/storage seams and exercise this same entry point with a synthetic WAV.
    func processCapturedRecording(_ file: URL, configuration: QuillSettings, words: [CustomWord] = [], voiceSample: Bool = false) throws {
        guard ![.transcribing, .cleaning].contains(phase),
              file.standardizedFileURL.deletingLastPathComponent() == AppFiles.audio.standardizedFileURL,
              FileManager.default.fileExists(atPath: file.path) else { throw QuillError.audio }
        snapshot = configuration; snapshotWords = words; voiceSampleMode = voiceSample
        retainedAudio = file; generation = UUID()
        do { try persistPendingRecording() } catch { report(error) }
        beginTranscription()
    }
    func retry() { guard canRetry else { return }; errorKey = nil; beginTranscription() }
    private func beginTranscription() {
        guard let file = retainedAudio, let capture = snapshot else { return }
        operation?.cancel(); let token = generation
        phase = .transcribing
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Finish dictation") { [weak self] in
            Task { @MainActor in self?.processingExpired(generation: token) }
        }
        let backgroundIdentifier = backgroundTask
        operation = Task { [weak self] in
            guard let self else { return }
            defer { self.endBackgroundTask(backgroundIdentifier) }
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
            }
        }
    }
    private func interrupted() {
        if testingMicrophone { stopMicTest(); errorKey = QuillError.interrupted.copyKey; return }
        guard phase == .recording else { return }
        retainedAudio = recorder.stop()
        do { try persistPendingRecording() } catch { report(error) }
        phase = .failed; errorKey = QuillError.interrupted.copyKey
    }
    private func processingExpired(generation expected: UUID) {
        guard generation == expected, [.transcribing, .cleaning].contains(phase) else { return }
        operation?.cancel(); generation = UUID(); phase = .failed; errorKey = QuillError.interrupted.copyKey
        endBackgroundTask()
    }
    private func endBackgroundTask(_ expected: UIBackgroundTaskIdentifier? = nil) {
        if let expected, expected != backgroundTask { return }
        if backgroundTask != .invalid { UIApplication.shared.endBackgroundTask(backgroundTask); backgroundTask = .invalid }
    }
    func cancel() {
        generation = UUID(); operation?.cancel(); operation = nil
        recorder.cancel(); testingMicrophone = false; discardRetry(); endBackgroundTask()
        result = ""; phase = .idle; errorKey = nil
    }
    private func discardRetry() {
        if let retainedAudio { try? FileManager.default.removeItem(at: retainedAudio) }
        retainedAudio = nil
        try? FileManager.default.removeItem(at: AppFiles.root.appendingPathComponent("pending-recording.json"))
    }
    private func persistPendingRecording() throws {
        guard let url = retainedAudio, let snapshot else { return }
        try AppFiles.write(PendingRecording(filename: url.lastPathComponent, settings: snapshot, words: snapshotWords, voiceSample: voiceSampleMode), name: "pending-recording.json")
    }
    func copyText(_ text: String) {
        UIPasteboard.general.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true])
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
        }
    }
    func saveWords() { do { try AppFiles.write(words, name: "words.json") } catch { report(error) } }
    func addWord(heard: String, replacement: String) {
        let heard = heard.trimmingCharacters(in: .whitespacesAndNewlines), replacement = replacement.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !heard.isEmpty, !replacement.isEmpty else { return }
        words = WordSubstitutions.upsert(words, heard: heard, replacement: replacement)
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
    }
    func reset() {
        cancel()
        do {
            try vault.clear(); try KeyboardTransfer.clear(); try historyStore?.clear()
            try KeyDraftVault().clearAll()
            try? FileManager.default.removeItem(at: AppFiles.root.appendingPathComponent("key-setup.json"))
            diagnosticsCollector.clear()
            words = []; contactNames = []; keys = []; history = []
            try AppFiles.write(words, name: "words.json"); try AppFiles.write(contactNames, name: "contacts.json")
            settings = QuillSettings(); tab = 0
        } catch { report(error) }
    }
    var diagnostics: String {
        // Only a fixed whitelist: no provider response, host field content, keys, model IDs,
        // endpoint, contact data, transcript or file paths ever enter this report.
        var report = "Quill iOS\nVersion: \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")\niOS: \(UIDevice.current.systemVersion)\nMicrophone allowed: \(microphoneGranted)\nPhase: \(phase.rawValue)\nPrivate mode: \(settings.privateMode)"
        if let summary = diagnosticsCollector.latest {
            report += "\nCrash count: \(summary.crashes)\nHang count: \(summary.hangs)\nCPU exception count: \(summary.cpuExceptions)\nDisk write exception count: \(summary.diskWriteExceptions)"
        }
        return report
    }
}
