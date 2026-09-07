import XCTest
import Foundation
import QuillCore
@testable import Quill

private actor ScriptedProvider: HTTPTransport {
    var results: [HTTPResult]
    let delay: UInt64
    init(_ bodies: [(Int, String)], delay: UInt64 = 0) { results = bodies.map { HTTPResult(data: Data($0.1.utf8), status: $0.0) }; self.delay = delay }
    func send(_ request: URLRequest) async throws -> HTTPResult {
        if delay > 0 { try await Task.sleep(nanoseconds: delay) }
        guard !results.isEmpty else { throw QuillError.network }
        return results.removeFirst()
    }
}
@MainActor private final class PipelineHistory: HistoryRepository {
    var data: [HistoryEntry] = []
    var failSave = false
    func save(_ entry: HistoryEntry) throws { if failSave { throw QuillError.storage }; data.append(entry) }
    func entries(retentionMinutes: Int) throws -> [HistoryEntry] { HistoryPolicy.prune(data, retentionMinutes: retentionMinutes) }
    func delete(_ id: UUID) throws { data.removeAll { $0.id == id } }
    func clear() throws { data = [] }
}

@MainActor private final class FaultingRecordingStore: RecordingRecoveryStore {
    private let files = FileRecordingRecoveryStore()
    var failTerminalWrite = false
    var failAudioRemoval = false
    var failManifestRemoval = false
    func load() -> PendingRecording? { files.load() }
    func save(_ recording: PendingRecording) throws {
        if recording.disposition != .pending && failTerminalWrite { throw QuillError.storage }
        try files.save(recording)
    }
    func removeAudio(at url: URL) throws {
        if failAudioRemoval { throw QuillError.storage }; try files.removeAudio(at: url)
    }
    func removeManifest() throws {
        if failManifestRemoval { throw QuillError.storage }; try files.removeManifest()
    }
    func audioFiles() throws -> [URL] { try files.audioFiles() }
    func recoverStorage() { failTerminalWrite = false; failAudioRemoval = false; failManifestRemoval = false }
}

@MainActor final class PipelineTests: XCTestCase {
    private var keyIDs: [SavedKey] = []
    override func tearDown() async throws {
        await MainActor.run {
            let vault = KeyVault()
            for key in keyIDs { try? vault.delete(key) }
            keyIDs = []
        }
    }
    private func makeModel(_ transport: ScriptedProvider, store: PipelineHistory? = nil,
                           recordingStore: (any RecordingRecoveryStore)? = nil) throws -> AppModel {
        let model = AppModel(providerAPI: ProviderAPI(transport: transport), historyStore: store ?? PipelineHistory(), recordingStore: recordingStore)
        model.cancel(); model.settings = QuillSettings(); model.settings.cleanupEnabled = false
        try model.vault.save("synthetic-test-key-" + UUID().uuidString, provider: .groq, endpoint: Provider.groq.baseURL, label: "Pipeline test")
        keyIDs.append(try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL)))
        return model
    }
    private func wav() throws -> URL {
        try AppFiles.prepare()
        let url = AppFiles.audio.appendingPathComponent(UUID().uuidString + ".wav")
        try (Data("RIFF".utf8) + Data(repeating: 0, count: 100)).write(to: url, options: [.atomic, .completeFileProtection])
        return url
    }
    private func wait(_ model: AppModel, phase: DictationPhase) async throws {
        for _ in 0..<200 { if model.phase == phase { return }; try await Task.sleep(nanoseconds: 10_000_000) }
        XCTFail("Expected \(phase), got \(model.phase)")
    }
    func testSuccessfulPipelineSavesExactFinalTextAndDeletesAudio() async throws {
        let store = PipelineHistory(), model = try makeModel(ScriptedProvider([(200, #"{"text":"tihor hello"}"#)]), store: store)
        let file = try wav()
        try model.processCapturedRecording(file, configuration: model.settings, words: [CustomWord(heard: "tihor", replacement: "Tihor")])
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "Tihor hello"); XCTAssertEqual(store.data.first?.text, model.result)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path)); XCTAssertFalse(model.canRetry)
    }
    func testCompletedTakeWithFailedDeletionsNeverRestoresAsRetry() async throws {
        let files = FaultingRecordingStore(), history = PipelineHistory()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"completed once"}"#)]), store: history, recordingStore: files)
        files.failAudioRemoval = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "completed once"); XCTAssertEqual(history.data.count, 1)
        XCTAssertEqual(files.load()?.disposition, .completed)
        XCTAssertTrue(model.cleanupRequired); XCTAssertFalse(model.canRetry)
        XCTAssertEqual(model.errorKey, QuillError.storage.copyKey)
        let relaunched = AppModel(historyStore: history, recordingStore: files)
        XCTAssertFalse(relaunched.canRetry); XCTAssertTrue(relaunched.cleanupRequired)
        relaunched.retry(); XCTAssertEqual(history.data.count, 1)
        let anotherFile = try wav()
        XCTAssertThrowsError(try relaunched.processCapturedRecording(anotherFile, configuration: relaunched.settings))
        XCTAssertEqual(files.load()?.filename, file.lastPathComponent)
        try? FileManager.default.removeItem(at: anotherFile)
        files.recoverStorage(); model.retryRecordingCleanup()
        XCTAssertFalse(model.cleanupRequired); XCTAssertEqual(history.data.count, 1)
    }
    func testPrivateCompletedMarkerContainsNoTranscriptAndNeverRetries() async throws {
        let files = FaultingRecordingStore(), history = PipelineHistory()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"private completion never persisted"}"#)]), store: history, recordingStore: files)
        model.settings.privateMode = true; files.failAudioRemoval = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        try model.processCapturedRecording(wav(), configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertTrue(history.data.isEmpty)
        let terminal = try XCTUnwrap(files.load())
        XCTAssertEqual(terminal.disposition, .completed)
        XCTAssertFalse(String(decoding: try JSONEncoder().encode(terminal), as: UTF8.self).contains(model.result))
        let relaunched = AppModel(historyStore: history, recordingStore: files)
        XCTAssertFalse(relaunched.canRetry); XCTAssertTrue(relaunched.cleanupRequired)
        XCTAssertTrue(relaunched.result.isEmpty); XCTAssertTrue(history.data.isEmpty)
    }
    func testCanceledTakeWithFailedDeletionsNeverRestoresAsRetry() async throws {
        let files = FaultingRecordingStore(), history = PipelineHistory()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"canceled take"}"#)], delay: 500_000_000), store: history, recordingStore: files)
        files.failAudioRemoval = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        try model.processCapturedRecording(wav(), configuration: model.settings)
        model.cancel(); try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(files.load()?.disposition, .discarded)
        XCTAssertEqual(model.phase, .failed); XCTAssertFalse(model.canRetry); XCTAssertTrue(model.cleanupRequired)
        XCTAssertTrue(model.result.isEmpty); XCTAssertTrue(history.data.isEmpty)
        let relaunched = AppModel(historyStore: history, recordingStore: files)
        XCTAssertFalse(relaunched.canRetry); XCTAssertTrue(relaunched.cleanupRequired)
        files.recoverStorage(); relaunched.retryRecordingCleanup()
        XCTAssertFalse(relaunched.cleanupRequired); XCTAssertEqual(relaunched.phase, .idle)
    }
    func testFailedTerminalMarkerWithSuccessfulDeletionStillCompletes() async throws {
        let files = FaultingRecordingStore()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"storage recovered"}"#)]), recordingStore: files)
        files.failTerminalWrite = true
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "storage recovered"); XCTAssertFalse(model.cleanupRequired)
        XCTAssertNil(model.errorKey); XCTAssertNil(files.load())
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
    func testTotalStorageFailureDefersPublicationAndRetriesOnlyCleanup() async throws {
        let files = FaultingRecordingStore(), history = PipelineHistory()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"keep exact completion"}"#)]), store: history, recordingStore: files)
        files.failTerminalWrite = true; files.failAudioRemoval = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .failed)
        XCTAssertTrue(model.result.isEmpty); XCTAssertTrue(history.data.isEmpty)
        XCTAssertTrue(model.cleanupRequired); XCTAssertFalse(model.canRetry)
        XCTAssertEqual(files.load()?.disposition, .pending) // No completion has been committed or exposed.
        model.retry(); XCTAssertEqual(model.phase, .failed)
        model.retryRecordingCleanup(); XCTAssertTrue(model.result.isEmpty)
        XCTAssertEqual(files.load()?.filename, file.lastPathComponent)
        files.recoverStorage(); model.retryRecordingCleanup()
        XCTAssertEqual(model.phase, .ready); XCTAssertEqual(model.result, "keep exact completion")
        XCTAssertEqual(history.data.count, 1); XCTAssertFalse(model.cleanupRequired)
        model.retryRecordingCleanup(); XCTAssertEqual(history.data.count, 1)
    }
    func testUnresolvedCleanupBlocksResetAndAnotherCaptureWithoutErasingSettingsOrKeys() async throws {
        let files = FaultingRecordingStore(), history = PipelineHistory()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"complete before reset"}"#)]), store: history, recordingStore: files)
        model.settings.privateMode = true; model.settings.onboardingComplete = true
        let keyIDsBefore = model.keys.map(\.id)
        files.failAudioRemoval = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .ready)
        model.reset()
        XCTAssertTrue(model.cleanupRequired); XCTAssertTrue(model.settings.privateMode)
        XCTAssertTrue(model.settings.onboardingComplete); XCTAssertEqual(model.keys.map(\.id), keyIDsBefore)
        XCTAssertTrue(model.hasActiveKey); XCTAssertEqual(model.errorKey, QuillError.storage.copyKey)
        await model.start()
        await model.startMicTest()
        XCTAssertEqual(files.load()?.filename, file.lastPathComponent)
        XCTAssertFalse(model.recorder.isRecording)
        XCTAssertNil(model.recorder.fileURL)
    }
    func testRemovedAudioWithUndeletableManifestCannotBecomeRetryAfterRestart() async throws {
        let files = FaultingRecordingStore()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"audio removed"}"#)]), recordingStore: files)
        files.failTerminalWrite = true; files.failManifestRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertTrue(model.cleanupRequired); XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
        let relaunched = AppModel(recordingStore: files)
        XCTAssertFalse(relaunched.canRetry); XCTAssertTrue(relaunched.cleanupRequired)
        XCTAssertEqual(model.errorKey, QuillError.storage.copyKey)
        relaunched.settings.onboardingComplete = true
        relaunched.reset()
        XCTAssertTrue(relaunched.settings.onboardingComplete); XCTAssertTrue(relaunched.hasActiveKey)
        XCTAssertTrue(relaunched.cleanupRequired)
        files.recoverStorage(); relaunched.retryRecordingCleanup()
        XCTAssertFalse(relaunched.cleanupRequired); XCTAssertNil(files.load())
    }
    func testOrphanAfterFailedMarkerAndAudioRemovalRemainsCleanupOnlyWithoutOverwritingAnotherTake() async throws {
        let files = FaultingRecordingStore()
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"manifest removed"}"#)]), recordingStore: files)
        files.failTerminalWrite = true; files.failAudioRemoval = true
        defer { files.recoverStorage(); model.cancel() }
        let orphan = try wav(); try model.processCapturedRecording(orphan, configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertNil(files.load()); XCTAssertTrue(FileManager.default.fileExists(atPath: orphan.path))
        let relaunched = AppModel(recordingStore: files)
        XCTAssertFalse(relaunched.canRetry); XCTAssertTrue(relaunched.cleanupRequired)
        XCTAssertEqual(relaunched.errorKey, QuillError.storage.copyKey)
        relaunched.settings.onboardingComplete = true; relaunched.reset()
        XCTAssertTrue(relaunched.settings.onboardingComplete); XCTAssertTrue(relaunched.hasActiveKey)
        // An orphan scan must never overwrite the manifest or remove audio of a genuinely
        // unfinished take that also exists. Only the orphan is eligible for cleanup here.
        let unfinished = try wav()
        try files.save(PendingRecording(filename: unfinished.lastPathComponent, settings: model.settings, words: [], voiceSample: false))
        let withPendingTake = AppModel(recordingStore: files)
        XCTAssertTrue(withPendingTake.canRetry); XCTAssertTrue(withPendingTake.cleanupRequired)
        XCTAssertEqual(files.load()?.filename, unfinished.lastPathComponent)
        files.recoverStorage(); withPendingTake.retryRecordingCleanup()
        XCTAssertFalse(withPendingTake.cleanupRequired); XCTAssertTrue(withPendingTake.canRetry)
        XCTAssertEqual(withPendingTake.phase, .failed)
        XCTAssertFalse(FileManager.default.fileExists(atPath: orphan.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: unfinished.path))
        XCTAssertEqual(files.load()?.filename, unfinished.lastPathComponent)
        withPendingTake.cancel()
    }
    func testCleanupFailureStillProducesRawAndWords() async throws {
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"tihor hello"}"#), (500, "")]))
        model.settings.cleanupEnabled = true
        try model.processCapturedRecording(wav(), configuration: model.settings, words: [CustomWord(heard: "tihor", replacement: "Tihor")])
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "Tihor hello")
    }
    func testDivergentCleanupCannotOverrideTranscript() async throws {
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"type my email"}"#), (200, #"{"choices":[{"message":{"content":"I'm ready to type your email"},"finish_reason":"stop"}]}"#)]))
        model.settings.cleanupEnabled = true
        try model.processCapturedRecording(wav(), configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "type my email")
    }
    func testPrivateFailureKeepsRetryAndSuccessSkipsHistory() async throws {
        let store = PipelineHistory(), model = try makeModel(ScriptedProvider([(503, ""), (200, #"{"text":"private words"}"#)]), store: store)
        model.settings.privateMode = true
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        try await wait(model, phase: .failed)
        XCTAssertTrue(model.canRetry); XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
        model.retry(); try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "private words"); XCTAssertTrue(store.data.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
    func testCancelPreventsLateHistoryAndDeletesAudio() async throws {
        let store = PipelineHistory(), model = try makeModel(ScriptedProvider([(200, #"{"text":"must not survive"}"#)], delay: 500_000_000), store: store)
        let file = try wav(); try model.processCapturedRecording(file, configuration: model.settings)
        model.cancel(); try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(model.phase, .idle); XCTAssertEqual(model.result, ""); XCTAssertTrue(store.data.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
    func testHistoryFailureKeepsReadyTranscript() async throws {
        let store = PipelineHistory(); store.failSave = true
        let model = try makeModel(ScriptedProvider([(200, #"{"text":"keep these words"}"#)]), store: store)
        try model.processCapturedRecording(wav(), configuration: model.settings)
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.result, "keep these words"); XCTAssertEqual(model.errorKey, QuillError.storage.copyKey)
    }
    func testVoiceWordCaptureIsRawAndNeverHistory() async throws {
        let store = PipelineHistory(), model = try makeModel(ScriptedProvider([(200, #"{"text":"clod code"}"#)]), store: store)
        model.settings.cleanupEnabled = true
        try model.processCapturedRecording(wav(), configuration: model.settings, voiceSample: true)
        try await wait(model, phase: .ready)
        XCTAssertEqual(model.voiceWordResult, "clod code"); XCTAssertTrue(store.data.isEmpty); XCTAssertEqual(model.result, "")
    }
    func testSavingAdditionalProviderKeyDoesNotRedirectSpeechSettings() async throws {
        let model = AppModel(providerAPI: ProviderAPI(transport: ScriptedProvider([(200, "{}")])) , historyStore: PipelineHistory())
        model.cancel(); model.settings = QuillSettings(); model.settings.selectProvider(.gemini)
        let saved = await model.verifyAndSaveKey(provider: .groq, secret: "synthetic-key", label: "Additional key")
        XCTAssertTrue(saved); XCTAssertEqual(model.settings.provider, .gemini)
        keyIDs.append(try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL)))
    }
    func testOnboardingSelectsPreferredKnownFreeModelFromCatalog() async throws {
        let body = #"{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]}]}"#
        let model = AppModel(providerAPI: ProviderAPI(transport: ScriptedProvider([(200, "{}"), (200, body)])), historyStore: PipelineHistory())
        model.cancel(); model.settings = QuillSettings()
        let saved = await model.verifyAndSaveKey(provider: .gemini, secret: "synthetic-key", label: "Setup", activateSpeechProvider: true)
        XCTAssertTrue(saved); XCTAssertEqual(model.settings.provider, .gemini)
        XCTAssertEqual(model.settings.model, "gemini-3.8-flash")
        keyIDs.append(try XCTUnwrap(model.vault.active(.gemini, endpoint: Provider.gemini.baseURL)))
    }
    func testCatalogRefreshNeverOverridesSavedModelSelection() async throws {
        let model = try makeModel(ScriptedProvider([(200, #"{"data":[{"id":"whisper-large-v3-turbo"}]}"#)]))
        model.settings.model = "saved-private-whisper"
        await model.refreshModels(cleanup: false)
        XCTAssertEqual(model.settings.model, "saved-private-whisper")
        XCTAssertEqual(model.modelChoices.map(\.id), ["whisper-large-v3-turbo"])
    }
    func testLateCatalogResponseCannotPopulateAnotherProvider() async throws {
        let model = try makeModel(ScriptedProvider([(200, #"{"data":[{"id":"whisper-large-v3-turbo"}]}"#)], delay: 100_000_000))
        let refresh = Task { await model.refreshModels(cleanup: false) }
        try await Task.sleep(nanoseconds: 20_000_000)
        model.selectProvider(.gemini)
        await refresh.value
        XCTAssertEqual(model.settings.provider, .gemini)
        XCTAssertTrue(model.modelChoices.isEmpty)
        XCTAssertFalse(model.loadingModels)
    }
}
