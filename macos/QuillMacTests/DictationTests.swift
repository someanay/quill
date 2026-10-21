import XCTest
import Combine
import AVFoundation
import AppKit
import QuillCore
@testable import QuillMac

private final class TestRecording: AudioRecording, @unchecked Sendable {
    var onLevel: (@Sendable (UUID, Double, Double, Bool) -> Void)?
    var onAutoStop: (@Sendable (UUID) -> Void)?
    var id = UUID()
    var starts = 0
    var stops = 0
    var cap = 0
    var wav = WAV.encode(pcm: Data((0..<16000).flatMap { i -> [UInt8] in
        let s = UInt16(bitPattern: Int16(sin(Double(i)*Double.pi*440/8000)*1600))
        return [UInt8(s & 255), UInt8(s >> 8)]
    }))
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

private final class FailingPendingStore: PendingTakeStoring {
    let underlying: PendingAudioStore
    var failTranscriptSave = true
    var failClear = false
    var failedClears = 0
    init(_ underlying: PendingAudioStore) { self.underlying = underlying }
    func load() throws -> PendingTake? { try underlying.load() }
    func save(_ take: PendingTake) throws {
        if failTranscriptSave && take.transcript != nil { throw QuillError.storage }
        try underlying.save(take)
    }
    func clear() throws {
        if failClear { failedClears += 1; throw QuillError.storage }
        try underlying.clear()
    }
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
    var injector: TextInjector?
    var pendingOverride: (any PendingTakeStoring)?
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
    func makeDictation() -> Dictation { Dictation(settings: settings, keys: keys, history: history, audio: audio, client: client, pendingStore: pendingOverride ?? pending, injector: injector, microphonePermission: { [weak self] in self?.microphoneAllowed == true }) }
    func dispose() { dictation.cancel(); try? keys.clear(); try? pending.clear(); try? FileManager.default.removeItem(at: directory) }
}

final class DictationTests: XCTestCase {
    @MainActor func testAudioTicksInvalidateOnlyMeterLeaves() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let dictation = fixture.dictation
        dictation.start(wordCapture: true)
        var pages = 0, meters = 0
        let pageSubscription = dictation.objectWillChange.sink { pages += 1 }
        let meterSubscription = dictation.meter.objectWillChange.sink { meters += 1 }
        for i in 0..<100 { fixture.audio.onLevel?(fixture.audio.id,0.3,Double(i)*0.08,false) }
        for _ in 0..<100 where meters < 100 { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(meters,100); XCTAssertEqual(pages,0)
        withExtendedLifetime((pageSubscription,meterSubscription)) {}
    }
    @MainActor func testQuietTakeNeverUploadsAndCannotRestoreForRetry() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        fixture.audio.wav = WAV.encode(pcm: Data(repeating: 0, count: 32000))
        let dictation = fixture.dictation, requests = NativeTranscriptProtocol.requests
        dictation.start(wordCapture: true); dictation.stop()
        let deadline = Date().addingTimeInterval(3)
        while dictation.state == .transcribing && Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(dictation.state,.error); XCTAssertEqual(dictation.message,"mac.error.no_speech")
        XCTAssertEqual(NativeTranscriptProtocol.requests,requests); XCTAssertEqual(dictation.transcript,"")
        XCTAssertNil(try fixture.pending.load())
        dictation.retry(); XCTAssertEqual(NativeTranscriptProtocol.requests,requests)
        XCTAssertEqual(fixture.makeDictation().state,.idle)
    }
    @MainActor func testFailedDiscardKeepsRecoveryBlockedUntilDeletionSucceeds() throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        try fixture.pending.save(PendingTake(audio: fixture.audio.wav, transcript: nil, settings: fixture.settings.value, duration: 1, clipboardOnly: false, wordCapture: false))
        let failing = FailingPendingStore(fixture.pending); failing.failClear = true
        fixture.pendingOverride = failing
        let dictation = fixture.dictation
        XCTAssertFalse(dictation.cancel())
        XCTAssertEqual(dictation.state, .error); XCTAssertTrue(dictation.recoveryWarning)
        XCTAssertEqual(dictation.message, "mac.error.pending_storage")
        let requests = NativeTranscriptProtocol.requests
        dictation.retry(); XCTAssertEqual(NativeTranscriptProtocol.requests, requests)
        dictation.start(wordCapture: true); dictation.startMicTest()
        XCTAssertEqual(fixture.audio.starts, 0); XCTAssertNotNil(try fixture.pending.load()?.audio)
        let reopened = fixture.makeDictation()
        reopened.start(wordCapture: true); reopened.startMicTest()
        XCTAssertEqual(reopened.state, .error); XCTAssertEqual(fixture.audio.starts, 0)
        XCTAssertNotNil(try fixture.pending.load()?.audio)
        failing.failClear = false
        XCTAssertTrue(dictation.cancel()); XCTAssertEqual(dictation.state, .idle)
        XCTAssertNil(try fixture.pending.load())
        dictation.start(wordCapture: true); XCTAssertEqual(fixture.audio.starts, 1)
    }
    @MainActor func testCopyDuringAcceptedInsertionCannotResurrectPasteState() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        fixture.injector = TextInjector(access: editor)
        let dictation = fixture.dictation
        dictation.start(); dictation.stop()
        let deadline = Date().addingTimeInterval(3)
        while editor.writes == 0 && Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(editor.writes, 1); XCTAssertEqual(dictation.transcript, "um quell")
        dictation.copyTranscript()
        XCTAssertEqual(dictation.state, .idle); XCTAssertTrue(dictation.transcript.isEmpty)
        XCTAssertEqual(NSPasteboard.general.string(forType: .string), "um quell")
        try await Task.sleep(nanoseconds: 850_000_000)
        XCTAssertEqual(dictation.state, .idle); XCTAssertTrue(dictation.transcript.isEmpty)
        XCTAssertNil(try fixture.pending.load()); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testRecoveryWriteAndDeleteFailurePreventsDeliveryUntilAudioIsRetired() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let editor = TestEditorAccess(), failing = FailingPendingStore(fixture.pending)
        fixture.injector = TextInjector(access: editor); fixture.pendingOverride = failing
        let dictation = fixture.dictation
        dictation.start(); failing.failClear = true; dictation.stop()
        let deadline = Date().addingTimeInterval(3)
        while dictation.state == .transcribing && Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(dictation.state, .paste); XCTAssertEqual(dictation.transcript, "um quell")
        XCTAssertEqual(dictation.message, "mac.error.pending_storage"); XCTAssertTrue(dictation.recoveryWarning)
        XCTAssertEqual(editor.writes, 0); XCTAssertTrue(fixture.history.entries.isEmpty)
        let unretired = try XCTUnwrap(fixture.pending.load())
        XCTAssertNotNil(unretired.audio); XCTAssertNil(unretired.transcript)
        let clipboardVersion = NSPasteboard.general.changeCount
        dictation.copyTranscript()
        XCTAssertEqual(NSPasteboard.general.changeCount, clipboardVersion)
        XCTAssertEqual(dictation.state, .paste); XCTAssertEqual(dictation.transcript, "um quell")
        XCTAssertEqual(editor.writes, 0)
        // Recovery can resume once a transcript-only record is durable, even if
        // cleanup still fails: relaunch can never offer already-delivered audio.
        failing.failTranscriptSave = false
        dictation.copyTranscript()
        XCTAssertEqual(dictation.state, .idle); XCTAssertTrue(dictation.transcript.isEmpty)
        XCTAssertEqual(NSPasteboard.general.string(forType: .string), "um quell")
        XCTAssertEqual(failing.failedClears, 1); XCTAssertTrue(dictation.recoveryWarning)
        let retired = try XCTUnwrap(fixture.pending.load())
        XCTAssertNil(retired.audio); XCTAssertEqual(retired.transcript, "um quell")
        let reopened = fixture.makeDictation()
        XCTAssertEqual(reopened.state, .paste); XCTAssertEqual(reopened.transcript, "um quell")
        let requests = NativeTranscriptProtocol.requests
        reopened.retry(); XCTAssertEqual(NativeTranscriptProtocol.requests, requests)
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testCancelDuringAcceptedInsertionCannotResurrectTranscriptOrPasteState() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        fixture.injector = TextInjector(access: editor)
        let dictation = fixture.dictation
        dictation.start(); XCTAssertEqual(dictation.state, .recording); dictation.stop()
        let deadline = Date().addingTimeInterval(3)
        while editor.writes == 0 && Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(editor.writes, 1)
        dictation.cancel()
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(dictation.state, .idle); XCTAssertTrue(dictation.transcript.isEmpty)
        XCTAssertNil(try fixture.pending.load()); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testSuspendDuringConfirmationKeepsRecoveryWithoutStaleUITransition() async throws {
        let fixture = try DictationFixture(); defer { fixture.dispose() }
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        fixture.injector = TextInjector(access: editor)
        let dictation = fixture.dictation
        dictation.start(); dictation.stop()
        let deadline = Date().addingTimeInterval(3)
        while editor.writes == 0 && Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
        XCTAssertEqual(editor.writes, 1)
        dictation.suspend()
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(dictation.state, .paste); XCTAssertEqual(dictation.message, "mac.status.unconfirmed")
        XCTAssertEqual(try fixture.pending.load()?.transcript, "um quell"); XCTAssertEqual(editor.writes, 1)
        dictation.start()
        XCTAssertEqual(dictation.state, .paste); XCTAssertEqual(dictation.transcript, "um quell"); XCTAssertEqual(fixture.audio.starts, 1)
        XCTAssertEqual(try fixture.pending.load()?.transcript, "um quell")
    }
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
        XCTAssertTrue(dictation.testCaptureSucceeded); XCTAssertFalse(dictation.testHeard)
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
