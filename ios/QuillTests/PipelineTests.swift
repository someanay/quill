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

@MainActor final class PipelineTests: XCTestCase {
    private var keyIDs: [SavedKey] = []
    override func tearDown() async throws {
        await MainActor.run {
            let vault = KeyVault()
            for key in keyIDs { try? vault.delete(key) }
            keyIDs = []
        }
    }
    private func makeModel(_ transport: ScriptedProvider, store: PipelineHistory? = nil) throws -> AppModel {
        let model = AppModel(providerAPI: ProviderAPI(transport: transport), historyStore: store ?? PipelineHistory())
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
}
