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
    @Published private(set) var cleanupRequired = false
    let recorder = AudioRecorder()
    let keyboardSession = KeyboardSessionController()
    let vault = KeyVault()
    let diagnosticsCollector = DiagnosticsCollector()
    let copy: CopyCatalog
    private let providerAPI: ProviderAPI
    private let recordingStore: any RecordingRecoveryStore
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
    private var terminalRecording: PendingRecording?
    private var orphanedAudio: [URL] = []
    private var orphanScanFailed = false
    private struct Completion {
        let text: String
        let settings: QuillSettings
        let voiceSample: Bool
    }
    private var pendingCompletion: Completion?

    init(providerAPI: ProviderAPI = ProviderAPI(), historyStore: (any HistoryRepository)? = nil,
         recordingStore: (any RecordingRecoveryStore)? = nil) {
        self.providerAPI = providerAPI
        self.recordingStore = recordingStore ?? FileRecordingRecoveryStore()
        let bundle = Bundle.main
        let url = bundle.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? bundle.url(forResource: "strings.default", withExtension: "json")
        copy = (url.flatMap { try? Data(contentsOf: $0) }.flatMap { try? CopyCatalog(data: $0) }) ?? CopyCatalog(strings: [:])
        settings = AppFiles.read(QuillSettings.self, name: "settings.json") ?? QuillSettings()
        if AppFiles.resetsUISettingsOnLaunch { settings = QuillSettings() }
        settings.sanitize()
        do { try AppFiles.prepare(); self.historyStore = try historyStore ?? HistoryStore() }
        catch { errorKey = QuillError.storage.copyKey }
        words = AppFiles.read([CustomWord].self, name: "words.json") ?? []
        contactNames = AppFiles.read([String].self, name: "contacts.json") ?? []
        keys = vault.keys
        diagnosticsCollector.onUpdate = { [weak self] in self?.objectWillChange.send() }
        diagnosticsCollector.setEnabled(settings.crashReportingEnabled)
        if let pending = self.recordingStore.load(), let url = pending.url {
            if pending.disposition != .pending {
                terminalRecording = pending
                _ = cleanTerminalRecording()
                if cleanupRequired { phase = .failed }
            } else if FileManager.default.fileExists(atPath: url.path) {
                retainedAudio = url; snapshot = pending.settings; snapshotWords = pending.words; voiceSampleMode = pending.voiceSample
                phase = .failed; notice = copy.text("ios.recording.retry_retention")
            } else {
                // A previous deletion may have removed the audio while its manifest remained.
                // Its captured settings/words still need explicit cleanup; it cannot be retried.
                var terminal = pending; terminal.disposition = .discarded; terminalRecording = terminal
                _ = cleanTerminalRecording()
            }
        }
        cleanOrphanedAudio()
        if cleanupRequired { phase = .failed }
        KeyboardTransfer.prune()
        recorder.onAutomaticStop = { [weak self] in
            guard let self else { return }
            if self.testingMicrophone { self.stopMicTest() } else { self.stopAndTranscribe() }
        }
        recorder.onInterruption = { [weak self] in self?.interrupted() }
        pruneTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in Task { @MainActor in self?.prune() } }
        refreshHistory()
        keyboardSession.attach(self)
        #if DEBUG
        if AppFiles.isUITesting {
            let args = ProcessInfo.processInfo.arguments
            if args.contains("--ui-dark") { settings.theme = "Dark" }
            if let index = args.firstIndex(of: "--ui-onboarding-stage"), args.indices.contains(index + 1), let stage = Int(args[index + 1]), (1...6).contains(stage) {
                settings.onboardingStep = stage
            }
            if args.contains("--ui-history-fixtures") {
                try? self.historyStore?.clear()
                for text in ["A short thought, captured before it disappears.", "A longer note for the reading layout.\n\nKeep the first idea. Give the next one its own paragraph. A transcript should remain comfortable to read when the window changes shape.\n\nEvery word belongs to you. 👋🏽"] {
                    try? self.historyStore?.save(HistoryEntry(text: text, provider: .groq))
                }
                refreshHistory()
            }
        }
        #endif
    }
    deinit { pruneTimer?.invalidate(); operation?.cancel() }
    func c(_ key: String, _ args: String...) -> String { copy.format(key, args: args) }
    var busy: Bool { [.recording, .transcribing, .cleaning].contains(phase) }
    var canRetry: Bool { retainedAudio != nil && terminalRecording == nil && phase == .failed }
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
        guard !busy, !canRetry, !cleanupRequired else { return }
        keyboardSession.end()
        if !microphoneGranted { await requestMicrophone() }
        guard microphoneGranted, !busy, !canRetry, !cleanupRequired else { return }
        do { testingMicrophone = true; try recorder.start(capSeconds: 30, microphoneTest: true) }
        catch { testingMicrophone = false; report(error) }
    }
    func stopMicTest() { guard testingMicrophone else { return }; recorder.cancel(); testingMicrophone = false }
    func start(voiceSample: Bool = false, capSeconds: Int? = nil) async {
        guard !busy, !canRetry, !cleanupRequired else { return }
        if capSeconds == nil { keyboardSession.end() }
        stopMicTest()
        if !microphoneGranted { await requestMicrophone() }
        guard microphoneGranted, !busy, !canRetry, !cleanupRequired else { return }
        do {
            guard hasActiveKey else { throw QuillError.noKey }
            _ = try ProviderConfiguration(provider: settings.provider, customBaseURL: settings.customBaseURL, model: settings.model, language: settings.language)
            result = ""; errorKey = nil; notice = nil
            snapshot = settings; snapshotWords = words; generation = UUID()
            voiceSampleMode = voiceSample
            try recorder.start(capSeconds: voiceSample ? 8 : min(settings.sessionCapSeconds, capSeconds ?? settings.sessionCapSeconds))
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
        guard ![.transcribing, .cleaning].contains(phase), !cleanupRequired,
              retainedAudio == nil || retainedAudio == file,
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
                self.pendingCompletion = Completion(text: final, settings: capture, voiceSample: self.voiceSampleMode)
                // Commit a terminal marker (or remove the pending take) before exposing success.
                // A disk that rejects every operation must not turn an uncommitted result into a
                // delivered result that could later be offered again as a retry.
                if self.finishRecording(as: .completed) { self.publishCompletion() }
                else { self.phase = .failed }
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
    func interruptKeyboardRecording() { interrupted() }
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
        pendingCompletion = nil; result = ""; voiceWordResult = ""; errorKey = nil
        recorder.cancel(); testingMicrophone = false
        _ = finishRecording(as: .discarded); endBackgroundTask()
        phase = cleanupRequired ? .failed : .idle
    }
    func retryRecordingCleanup() {
        guard cleanupRequired else { return }
        errorKey = nil
        let terminalCommitted = terminalRecording == nil || cleanTerminalRecording()
        cleanOrphanedAudio()
        if terminalCommitted, pendingCompletion != nil { publishCompletion() }
        else if !cleanupRequired { phase = retainedAudio != nil ? .failed : result.isEmpty && voiceWordResult.isEmpty ? .idle : .ready }
    }
    private func finishRecording(as disposition: PendingRecording.Disposition) -> Bool {
        if let pending = terminalRecording {
            var terminal = pending; terminal.disposition = disposition; terminalRecording = terminal
        } else if let url = retainedAudio, let snapshot {
            terminalRecording = PendingRecording(filename: url.lastPathComponent, settings: snapshot,
                words: snapshotWords, voiceSample: voiceSampleMode, disposition: disposition)
        } else { return true }
        return cleanTerminalRecording()
    }
    @discardableResult private func cleanTerminalRecording() -> Bool {
        guard let terminal = terminalRecording, let url = terminal.url else { return true }
        let outcome = RecordingCleanup.finish(markTerminal: { try recordingStore.save(terminal) },
            removeAudio: { try recordingStore.removeAudio(at: url) }, removeManifest: { try recordingStore.removeManifest() })
        // Keep either owned file available for explicit cleanup retry. Even a manifest without
        // audio contains capture settings and words, so Clear all cannot report success yet.
        retainedAudio = nil
        if !outcome.cleanupFailed { terminalRecording = nil }
        cleanupRequired = terminalRecording != nil || !orphanedAudio.isEmpty || orphanScanFailed
        if outcome.cleanupFailed { report(QuillError.storage) }
        return outcome.terminalCommitted
    }
    private func cleanOrphanedAudio() {
        do {
            let protected = [retainedAudio, terminalRecording?.url, recorder.fileURL].compactMap { $0?.standardizedFileURL }
            var failed: [URL] = []
            for url in try recordingStore.audioFiles() where !protected.contains(url.standardizedFileURL) {
                do { try recordingStore.removeAudio(at: url) } catch { failed.append(url) }
            }
            orphanedAudio = failed; orphanScanFailed = false
        } catch { orphanScanFailed = true }
        cleanupRequired = terminalRecording != nil || !orphanedAudio.isEmpty || orphanScanFailed
        if cleanupRequired { report(QuillError.storage) }
    }
    private func publishCompletion() {
        guard let completion = pendingCompletion else { return }
        pendingCompletion = nil
        if completion.voiceSample { voiceWordResult = completion.text } else { result = completion.text }
        phase = .ready
        if !completion.voiceSample && completion.settings.shouldSaveHistory && settings.shouldSaveHistory {
            do { try historyStore?.save(HistoryEntry(text: completion.text, provider: completion.settings.provider)); refreshHistory() }
            catch { report(error) }
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
    private func persistPendingRecording() throws {
        guard let url = retainedAudio, let snapshot else { return }
        try recordingStore.save(PendingRecording(filename: url.lastPathComponent, settings: snapshot, words: snapshotWords, voiceSample: voiceSampleMode))
    }
    func copyText(_ text: String) {
        UIPasteboard.general.setItems([["public.utf8-plain-text": text]], options: [.localOnly: true])
        notice = c("ios.result.copied")
    }
    @discardableResult func sendToKeyboard(_ text: String) -> UUID? {
        do { let id = try KeyboardTransfer.publish(text); notice = c("ios.result.sent"); return id }
        catch { report(error); return nil }
    }
    func clearTransfer() {
        keyboardSession.end(cancelCapture: true)
        do { try KeyboardTransfer.clear(); notice = c("ios.transfer.cleared") } catch { report(error) }
    }
    func refreshHistory() { do { history = try historyStore?.entries(retentionMinutes: settings.retentionMinutes) ?? [] } catch { report(error) } }
    func deleteHistory(_ entry: HistoryEntry) { do { try historyStore?.delete(entry.id); refreshHistory() } catch { report(error) } }
    func clearHistory() { do { try historyStore?.clear(); refreshHistory() } catch { report(error) } }
    func setPrivateMode(_ value: Bool) {
        settings.privateMode = value
        if value {
            keyboardSession.end(cancelCapture: true)
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
        keyboardSession.end(cancelCapture: true)
        cancel()
        guard !cleanupRequired else { return }
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
