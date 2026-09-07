import XCTest
import Combine
import QuillCore
@testable import QuillMac

private final class KeySetupProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var calls = 0
    static var status = 200
    static var delay = 0.0
    private let lock = NSLock()
    private var stopped = false
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); Self.calls += 1; let status = Self.status, delay = Self.delay; Self.lock.unlock()
        DispatchQueue.global().asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            self.lock.lock(); defer { self.lock.unlock() }; guard !self.stopped else { return }
            let response = HTTPURLResponse(url: self.request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
            self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            let body = Data("{\"models\":[{\"name\":\"models/gemini-3.1-flash-lite\",\"supportedGenerationMethods\":[\"generateContent\"]}]}".utf8)
            self.client?.urlProtocol(self, didLoad: body); self.client?.urlProtocolDidFinishLoading(self)
        }
    }
    override func stopLoading() { lock.lock(); stopped = true; lock.unlock() }
    static func configure(status: Int = 200, delay: Double = 0) { lock.lock(); calls = 0; Self.status = status; Self.delay = delay; lock.unlock() }
    static var count: Int { lock.lock(); defer { lock.unlock() }; return calls }
}

@MainActor private final class KeySetupFixture {
    let directory: URL
    let settings: SettingsStore
    let keys: KeychainStore
    let drafts: KeySetupDraftStore
    let client: ProviderClient
    init() throws {
        let configuration = URLSessionConfiguration.ephemeral; configuration.protocolClasses = [KeySetupProtocol.self]
        client = ProviderClient(session: URLSession(configuration: configuration))
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("QuillKeySetupTests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        settings = SettingsStore(directory: directory)
        let namespace = "com.tihor.quill.key-setup-tests." + UUID().uuidString
        keys = KeychainStore(directory: directory, namespace: namespace + ".keys")
        drafts = KeySetupDraftStore(directory: directory, namespace: namespace + ".drafts")
        KeySetupProtocol.configure()
    }
    func controller(activate: Bool = false, prefill: Bool = false) -> KeySetupController { KeySetupController(settings: settings, keys: keys, client: client, draftStore: drafts, activateSpeech: activate, prefill: prefill) }
    func dispose() { try? keys.clear(); try? drafts.clearAll(); try? FileManager.default.removeItem(at: directory) }
}

final class KeySetupTests: XCTestCase {
    private let candidate = "AIza123456789012345678901234"
    @MainActor func testCustomEndpointChangeCannotSendOrSavePreviouslyVerifiedDraft() async throws {
        let fixture = try KeySetupFixture(); defer { fixture.dispose() }
        let model = fixture.controller(); defer { model.disappear() }
        fixture.settings.value.customBaseURL = "https://first.example/v1"
        model.update(.custom, value: "short")
        XCTAssertEqual(model.checks[.custom], .needsCheck); XCTAssertEqual(KeySetupProtocol.count, 0)
        model.validate(.custom, explicitCustomCheck: true)
        try await Task.sleep(nanoseconds: 850_000_000); XCTAssertEqual(model.checks[.custom], .valid)
        fixture.settings.value.customBaseURL = "https://second.example/v1"
        model.save(.custom) { XCTFail("Changing the endpoint needs explicit revalidation") }
        XCTAssertEqual(model.checks[.custom], .needsCheck)
        try await Task.sleep(nanoseconds: 800_000_000)
        XCTAssertEqual(KeySetupProtocol.count, 1); XCTAssertTrue(fixture.keys.slots.isEmpty)
    }
    @MainActor func testDebounceCancelsInvalidCandidateAndDraftsStayInKeychain() async throws {
        let fixture = try KeySetupFixture(); defer { fixture.dispose() }
        let model = fixture.controller(); defer { model.disappear() }
        model.update(.gemini, value: candidate)
        XCTAssertEqual(model.checks[.gemini], .waiting)
        try await Task.sleep(nanoseconds: 250_000_000); XCTAssertEqual(KeySetupProtocol.count, 0)
        model.update(.gemini, value: "invalid")
        try await Task.sleep(nanoseconds: 800_000_000)
        XCTAssertEqual(model.checks[.gemini], .format); XCTAssertEqual(KeySetupProtocol.count, 0)
        model.update(.groq, value: "gsk_saved-draft"); model.expand(.groq)
        let reopened = fixture.controller(); XCTAssertEqual(reopened.expanded, "groq")
        XCTAssertEqual(reopened.drafts[.gemini], "invalid"); XCTAssertEqual(reopened.drafts[.groq], "gsk_saved-draft")
        for file in try FileManager.default.contentsOfDirectory(at: fixture.directory, includingPropertiesForKeys: nil) {
            let data = try Data(contentsOf: file)
            XCTAssertNil(data.range(of: Data("gsk_saved-draft".utf8))); XCTAssertNil(data.range(of: Data(candidate.utf8)))
        }
        try fixture.drafts.clearAll(); XCTAssertEqual(try fixture.drafts.read(.groq), "")
    }
    @MainActor func testStaleValidationCannotVerifyChangedCandidate() async throws {
        let fixture = try KeySetupFixture(); defer { fixture.dispose() }
        KeySetupProtocol.configure(delay: 0.7)
        let model = fixture.controller(); defer { model.disappear() }
        model.update(.gemini, value: candidate)
        try await Task.sleep(nanoseconds: 800_000_000); XCTAssertEqual(model.checks[.gemini], .checking)
        model.update(.gemini, value: "replacement-invalid")
        try await Task.sleep(nanoseconds: 900_000_000)
        XCTAssertEqual(model.checks[.gemini], .format)
        model.save(.gemini) { XCTFail("Unverified draft cannot save") }
        XCTAssertTrue(fixture.keys.slots.isEmpty)
    }
    @MainActor func testAutomaticCheckAndCleanupKeySavePreserveSpeechAndClearDraft() async throws {
        let fixture = try KeySetupFixture(); defer { fixture.dispose() }
        fixture.settings.value.provider = .openai; fixture.settings.value.speechModel = "saved-speech-model"
        let model = fixture.controller(); defer { model.disappear() }
        model.update(.gemini, value: candidate)
        try await Task.sleep(nanoseconds: 850_000_000)
        XCTAssertEqual(model.checks[.gemini], .valid); XCTAssertEqual(KeySetupProtocol.count, 1)
        let saved = expectation(description: "Save only verified key")
        model.save(.gemini) { saved.fulfill() }; await fulfillment(of: [saved], timeout: 3)
        XCTAssertEqual(fixture.settings.value.provider, .openai); XCTAssertEqual(fixture.settings.value.speechModel, "saved-speech-model")
        XCTAssertEqual(fixture.keys.active(.gemini, settings: fixture.settings.value), candidate)
        XCTAssertEqual(try fixture.drafts.read(.gemini), ""); XCTAssertEqual(KeySetupProtocol.count, 1)
        let speech = fixture.controller(activate: true, prefill: true)
        XCTAssertEqual(speech.drafts[.gemini], candidate)
    }
    @MainActor func testRejectedAndUnavailableMetadataNeedNewValidation() async throws {
        let fixture = try KeySetupFixture(); defer { fixture.dispose() }
        let model = fixture.controller(); defer { model.disappear() }
        for (status, expected) in [(401, KeyCheckState.rejected), (400, .rejected), (503, .unverified)] {
            KeySetupProtocol.configure(status: status); model.update(.gemini, value: candidate)
            try await Task.sleep(nanoseconds: 850_000_000)
            XCTAssertEqual(model.checks[.gemini], expected)
            model.save(.gemini) { XCTFail("Only verified keys can save") }; XCTAssertTrue(fixture.keys.slots.isEmpty)
        }
        KeySetupProtocol.configure(); model.validate(.gemini)
        try await Task.sleep(nanoseconds: 850_000_000); XCTAssertEqual(model.checks[.gemini], .valid)
    }
}
