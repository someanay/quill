import XCTest
import Combine
import AVFoundation
import QuillCore
@testable import QuillMac

private final class TestRecording: AudioRecording, @unchecked Sendable {
    var onLevel: (@Sendable (UUID, Double, Double, Bool) -> Void)?
    var onAutoStop: (@Sendable (UUID) -> Void)?
    var id = UUID()
    var starts = 0
    var stops = 0
    var cap = 0
    let wav = WAV.encode(pcm: Data(repeating: 12, count: 32000))
    func start(cap: Int) throws -> UUID { id = UUID(); self.cap = cap; starts += 1; return id }
    func stop() -> Data { stops += 1; return wav }
    func cancel() { stops += 1 }
}

private final class NativeTranscriptProtocol: URLProtocol, @unchecked Sendable {
    static var requests = 0
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.requests += 1
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{\"text\":\"um quell\"}".utf8)); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@MainActor private final class DictationFixture {
    let directory: URL
    let settings: SettingsStore
    let keys: KeychainStore
    let history: HistoryStore
    let pending: PendingAudioStore
    let audio = TestRecording()
    var microphoneAllowed = true
    let client: ProviderClient
    lazy var dictation = makeDictation()
    init() throws {
        let configuration = URLSessionConfiguration.ephemeral; configuration.protocolClasses = [NativeTranscriptProtocol.self]
        client = ProviderClient(session: URLSession(configuration: configuration))
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("QuillDictationTests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        settings = SettingsStore(directory: directory); history = HistoryStore(directory: directory)
        let namespace = "com.tihor.quill.tests." + UUID().uuidString
        keys = KeychainStore(directory: directory, namespace: namespace + ".keys"); pending = PendingAudioStore(directory: directory, namespace: namespace + ".pending")
        let slot = try keys.save(provider: .groq, label: "fixture", secret: "gsk_testfixture1234567890123456", status: "valid", customURL: "")
        settings.value.activeKeys["groq"] = slot.id; settings.value.onboardingComplete = true; settings.value.accessibilityConsent = true
    }
    func makeDictation() -> Dictation { Dictation(settings: settings, keys: keys, history: history, audio: audio, client: client, pendingStore: pending, microphonePermission: { [weak self] in self?.microphoneAllowed == true }) }
    func dispose() { dictation.cancel(); try? keys.clear(); try? pending.clear(); try? FileManager.default.removeItem(at: directory) }
}

final class DictationTests: XCTestCase {
    @MainActor func testRouteChangeIsHandledBeforeANewMicrophoneTestCanStart() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let dictation = fixture.dictation
        dictation.startMicTest(); XCTAssertTrue(dictation.microphoneTest)
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: nil)
        XCTAssertFalse(dictation.microphoneTest)
        dictation.startMicTest(); let stopped = fixture.audio.stops
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertTrue(dictation.microphoneTest); XCTAssertEqual(fixture.audio.stops, stopped)
    }
    @MainActor func testStaleMicrophoneCallbacksCannotStopOrAnimateNewCapture() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let dictation = fixture.dictation
        dictation.startMicTest(); let old = fixture.audio.id; dictation.stopMicTest(); dictation.startMicTest()
        let current = fixture.audio.id, stopped = fixture.audio.stops
        fixture.audio.onAutoStop?(old); fixture.audio.onLevel?(old, 1, 999, true)
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertTrue(dictation.microphoneTest); XCTAssertEqual(fixture.audio.stops, stopped); XCTAssertFalse(dictation.testHeard)
        fixture.audio.onLevel?(current, 1, 1, false); try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertTrue(dictation.testHeard)
    }
    @MainActor func testMicRevocationKeepsEncryptedAudioAndPrivateStateForRelaunch() throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        fixture.settings.value.privateMode = true; fixture.dictation.start(wordCapture: true)
        XCTAssertEqual(fixture.audio.cap, 8); XCTAssertEqual(fixture.dictation.state, .recording)
        fixture.microphoneAllowed = false; fixture.dictation.refreshPermissions()
        XCTAssertEqual(fixture.dictation.state, .error)
        let saved = try XCTUnwrap(fixture.pending.load()); XCTAssertEqual(saved.audio, fixture.audio.wav); XCTAssertTrue(saved.settings.privateMode)
        let ciphertext = try Data(contentsOf: fixture.directory.appendingPathComponent("pending-take.encrypted"))
        XCTAssertNil(ciphertext.range(of: Data("RIFF".utf8))); XCTAssertNil(ciphertext.range(of: Data("privateMode".utf8)))
        fixture.dictation.prepareForTermination(); fixture.settings.value.privateMode = false
        let recovered = fixture.makeDictation(); XCTAssertEqual(recovered.state, .error)
        let starts = fixture.audio.starts; recovered.start(wordCapture: true); XCTAssertEqual(fixture.audio.starts, starts); XCTAssertNotNil(try fixture.pending.load())
        recovered.cancel(); XCTAssertNil(try fixture.pending.load())
    }
    @MainActor func testVoiceWordCaptureReturnsRawBeforeCleanupAndSubstitutions() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        fixture.settings.value.words = [CustomWord(heard: "quell", replacement: "Quill")]
        fixture.settings.value.cleanupEnabled = true; NativeTranscriptProtocol.requests = 0
        let finished = expectation(description: "Raw spoken word captured")
        fixture.dictation.start(wordCapture: true)
        fixture.dictation.onWordCaptured = { text in XCTAssertEqual(text, "um quell"); finished.fulfill() }
        fixture.dictation.stop()
        await fulfillment(of: [finished], timeout: 5)
        XCTAssertEqual(NativeTranscriptProtocol.requests, 1); XCTAssertTrue(fixture.history.entries.isEmpty); XCTAssertNil(try fixture.pending.load())
    }
    @MainActor func testPrivateRecoveredTakeCannotBecomeHistoryWhenPrivateModeIsOff() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        var snapshot = fixture.settings.value; snapshot.privateMode = true; snapshot.cleanupEnabled = false
        try fixture.pending.save(PendingTake(audio: fixture.audio.wav, transcript: nil, settings: snapshot, duration: 1, clipboardOnly: false, wordCapture: false))
        fixture.settings.value.privateMode = false
        let dictation = fixture.dictation
        XCTAssertEqual(dictation.state, .error)
        let finished = expectation(description: "Recovered take becomes manual transcript")
        let subscription = dictation.$state.filter { $0 == .paste }.first().sink { _ in finished.fulfill() }
        defer { subscription.cancel() }
        dictation.retry(); await fulfillment(of: [finished], timeout: 5)
        XCTAssertTrue(fixture.history.entries.isEmpty)
        let saved = try XCTUnwrap(fixture.pending.load()); XCTAssertNil(saved.audio); XCTAssertEqual(saved.transcript, "um quell")
        let reopened = fixture.makeDictation(); XCTAssertEqual(reopened.state, .paste); XCTAssertEqual(reopened.transcript, "um quell")
    }
    func testEncryptedRecoveryRejectsTamperingAndPersistsTranscriptWithoutAudio() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("QuillPendingTests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let store = PendingAudioStore(directory: directory, namespace: "com.tihor.quill.tests.pending." + UUID().uuidString)
        defer { try? store.clear(); try? FileManager.default.removeItem(at: directory) }
        var settings = QuillSettings(); settings.privateMode = true
        let take = PendingTake(audio: nil, transcript: "Private recovered words", settings: settings, duration: 4, clipboardOnly: false, wordCapture: false)
        try store.save(take); XCTAssertEqual(try store.load(), take)
        let path = directory.appendingPathComponent("pending-take.encrypted")
        var bytes = try Data(contentsOf: path); bytes[bytes.count - 1] ^= 1; try bytes.write(to: path)
        XCTAssertThrowsError(try store.load())
    }
}
