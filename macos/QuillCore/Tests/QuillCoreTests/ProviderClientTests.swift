import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private final class StubProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var responder: ((URLRequest) throws -> (Int, Data))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            Self.lock.lock(); let responder = Self.responder; Self.lock.unlock()
            let (status, data) = try XCTUnwrap(responder)(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: ["Content-Type":"application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}

private final class DripProtocol: URLProtocol, @unchecked Sendable {
    private let lock = NSLock()
    private var timer: DispatchSourceTimer?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type":"application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        let source = DispatchSource.makeTimerSource()
        source.schedule(deadline: .now(), repeating: .milliseconds(50))
        source.setEventHandler { [weak self] in
            guard let self else { return }
            self.lock.lock(); defer { self.lock.unlock() }
            guard self.timer != nil else { return }
            self.client?.urlProtocol(self, didLoad: Data(" ".utf8))
        }
        lock.lock(); timer = source; lock.unlock(); source.resume()
    }
    override func stopLoading() { lock.lock(); timer?.cancel(); timer = nil; lock.unlock() }
}

private final class OversizedStreamProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(repeating: 32, count: 4 * 1024 * 1024 + 1))
        // No completion: the byte cap must cancel an ongoing chunked transfer.
    }
    override func stopLoading() {}
}

final class ProviderClientTests: XCTestCase {
    func testProviderBusyCopyDistinguishesOverloadFromCredentialsAndQuota() {
        XCTAssertEqual(QuillError.provider(503).copyKey, "mac.error.provider_busy")
        XCTAssertEqual(QuillError.badKey.copyKey, "mac.error.bad_key")
        XCTAssertEqual(QuillError.quota.copyKey, "mac.error.quota")
    }
    func testGeminiOverloadRetriesIdenticalAudioAndCredential() async throws {
        var bodies: [Data?] = []
        let client = client { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "x-goog-api-key"), "gemini-fixture")
            bodies.append(request.httpBody)
            if bodies.count == 1 { return (503,Data("{}".utf8)) }
            return (200,Data(#"{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"same recorded words"}]}}]}"#.utf8))
        }
        let result = try await client.transcribe(audio: WAV.encode(pcm: Data(repeating: 12,count: 3200)), options: SpeechRequest(provider: .gemini,model: "gemini-3.1-flash-lite"), key: "gemini-fixture")
        XCTAssertEqual(result,"same recorded words"); XCTAssertEqual(bodies.count,2); XCTAssertEqual(bodies.first!,bodies.last!)
    }
    private func client(_ responder: @escaping (URLRequest) throws -> (Int, Data)) -> ProviderClient {
        StubProtocol.lock.lock(); StubProtocol.responder = responder; StubProtocol.lock.unlock()
        let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [StubProtocol.self]
        return ProviderClient(session: URLSession(configuration: config))
    }
    func testMetadataValidationNeverUploadsAudio() async throws {
        let client = client { request in
            XCTAssertEqual(request.httpMethod, "GET"); XCTAssertNil(request.httpBody)
            XCTAssertEqual(request.url?.path, "/openai/v1/models")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer gsk_123456789012345678901234")
            return (200, Data("{}".utf8))
        }
        try await client.validateKey(provider: .groq, key: "gsk_123456789012345678901234", baseURL: "")
    }
    func testInvalidKeyRejectedBeforeNetwork() async throws {
        let client = client { _ in XCTFail("Invalid key must not issue a request"); return (200, Data()) }
        do { try await client.validateKey(provider: .groq, key: "invalid", baseURL: ""); XCTFail("Expected rejection") }
        catch { XCTAssertEqual(error as? QuillError, .badKey) }
    }
    func testCustomKeysHaveNoInventedProviderPrefixOrMinimumLength() async throws {
        let client = client { request in
            XCTAssertEqual(request.url?.host, "custom.example")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer local=key/+")
            return (200, Data("{}".utf8))
        }
        try await client.validateKey(provider: .custom, key: "local=key/+", baseURL: "https://custom.example/v1")
    }
    func testSuccessfulTranscriptionThroughURLSession() async throws {
        let client = client { request in
            XCTAssertEqual(request.url?.host, "api.openai.com")
            XCTAssertEqual(request.url?.path, "/v1/audio/transcriptions")
            return (200, Data("{\"text\":\"Aaj ka plan kya hai?\"}".utf8))
        }
        let text = try await client.transcribe(audio: WAV.encode(pcm: Data(repeating: 0, count: 100)), options: SpeechRequest(provider: .openai, model: "whisper-1"), key: "test-token")
        XCTAssertEqual(text, "Aaj ka plan kya hai?")
    }
    func testHTTPAuthenticationAndQuotaErrors() async throws {
        for (status, body, expected) in [(401,"{}",QuillError.badKey), (402,"{}",.quota), (429,"{\"error\":{\"code\":\"insufficient_quota\"}}",.quota), (500,"{}",.provider(500))] {
            let client = client { _ in (status, Data(body.utf8)) }
            do { _ = try await client.transcribe(audio: Data(), options: SpeechRequest(provider: .openai, model: "whisper-1"), key: "test-token"); XCTFail("Expected failure") }
            catch { XCTAssertEqual(error as? QuillError, expected) }
        }
    }
    func testCleanupTimeoutAndInvalidEndpointPreserveRaw() async throws {
        let client = client { _ in throw URLError(.timedOut) }
        let timeout = try await client.clean("type my email", provider: .groq, model: "cleanup-model", baseURL: "", key: "test", style: .formal)
        XCTAssertEqual(timeout, "type my email")
        let invalid = try await client.clean("type my email", provider: .custom, model: "cleanup-model", baseURL: "http://example.com", key: "test", style: .neutral)
        XCTAssertEqual(invalid, "type my email")
    }
    func testDripFedCleanupHasBoundedTotalDeadline() async throws {
        let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [DripProtocol.self]
        let client = ProviderClient(session: URLSession(configuration: config)), started = Date()
        let result = try await client.clean("Keep these words", provider: .groq, model: "model", baseURL: "", key: "test", style: .neutral)
        XCTAssertEqual(result, "Keep these words")
        XCTAssertGreaterThan(Date().timeIntervalSince(started), 24); XCTAssertLessThan(Date().timeIntervalSince(started), 32)
    }
    func testCleanupResponseGuardAndFinishReason() async throws {
        for (text, finish, expected) in [("Type my email.", "stop", "Type my email."), ("I am ready to type your email.", "stop", "type my email"), ("Type my email.", "length", "type my email")] {
            let client = client { request in
                XCTAssertEqual(request.timeoutInterval, 25)
                let body = try JSONSerialization.data(withJSONObject: ["choices":[["finish_reason":finish,"message":["content":text]]]])
                return (200, body)
            }
            let result = try await client.clean("type my email", provider: .openrouter, model: "openrouter/free", baseURL: "", key: "router-key", style: .neutral)
            XCTAssertEqual(result, expected)
        }
    }
    func testCleanupPropagatesCancellation() async throws {
        let client = client { _ in throw URLError(.cancelled) }
        do { _ = try await client.clean("Hello", provider: .groq, model: "model", baseURL: "", key: "test", style: .neutral); XCTFail("Expected cancellation") }
        catch { XCTAssertTrue(error is CancellationError) }
    }
    func testGeminiMetadataPaginationAndNoKeyInURL() async throws {
        let client = client { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "x-goog-api-key"), "google-test")
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertFalse(request.url!.absoluteString.contains("google-test"))
            if request.url!.query!.contains("pageToken=next") {
                return (200, Data("{\"models\":[{\"name\":\"models/gemini-3.5-flash-lite\",\"supportedGenerationMethods\":[\"generateContent\"]}]}".utf8))
            }
            return (200, Data("{\"models\":[{\"name\":\"models/gemini-live\",\"supportedGenerationMethods\":[\"bidiGenerateContent\"]}],\"nextPageToken\":\"next\"}".utf8))
        }
        let models = try await client.models(provider: .gemini, use: .dictation, baseURL: "", key: "google-test")
        XCTAssertEqual(models.map(\.id), ["gemini-3.5-flash-lite"])
    }
    func testCatalogRejectsRepeatedTokensAndMoreThanTwentyPages() async throws {
        for repeats in [true, false] {
            var calls = 0
            let client = client { _ in
                calls += 1
                return (200, try JSONSerialization.data(withJSONObject: ["models":[["name":"models/gemini-flash", "supportedGenerationMethods":["generateContent"]]], "nextPageToken": repeats ? "same" : String(calls)]))
            }
            do { _ = try await client.models(provider: .gemini, use: .dictation, baseURL: "", key: "test"); XCTFail("Expected bounded pagination rejection") }
            catch { XCTAssertEqual(error as? QuillError, .emptyTranscript) }
            XCTAssertEqual(calls, repeats ? 2 : 20)
        }
    }
    func testProviderResponseAboveFourMiBIsRejectedBeforeParsing() async throws {
        let client = client { _ in (200, Data(repeating: 32, count: 4 * 1024 * 1024 + 1)) }
        do { _ = try await client.models(provider: .groq, use: .dictation, baseURL: "", key: "test"); XCTFail("Expected oversized response rejection") }
        catch { XCTAssertEqual(error as? QuillError, .responseTooLarge) }
    }
    func testOversizedStreamIsCancelledBeforeItFinishes() async throws {
        let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [OversizedStreamProtocol.self]
        let client = ProviderClient(session: URLSession(configuration: config)), started = Date()
        var request = URLRequest(url: URL(string: "https://example.com/models")!); request.timeoutInterval = 10
        do { _ = try await client.send(request, provider: .groq); XCTFail("Expected in-flight byte limit") }
        catch { XCTAssertEqual(error as? QuillError, .responseTooLarge) }
        XCTAssertLessThan(Date().timeIntervalSince(started), 3)
    }
    func testGeminiMissingFinishAndBlockedOrIncompleteOutput() throws {
        let normal = Data("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"},{\"text\":\" world\"}]}}]}".utf8)
        XCTAssertEqual(try RequestBuilder.parseTranscript(normal, provider: .gemini), "Hello world")
        for (reason, expected) in [("SAFETY",QuillError.blockedTranscript), ("MAX_TOKENS",.incompleteTranscript)] {
            let data = try JSONSerialization.data(withJSONObject: ["candidates":[["finishReason":reason,"content":["parts":[["text":"partial"]]]]]])
            XCTAssertThrowsError(try RequestBuilder.parseTranscript(data, provider: .gemini)) { XCTAssertEqual($0 as? QuillError, expected) }
        }
        XCTAssertThrowsError(try RequestBuilder.parseTranscript(Data("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}".utf8), provider: .gemini)) { XCTAssertEqual($0 as? QuillError, .blockedTranscript) }
        XCTAssertEqual(Prompts.stripWrapping("```text\nHere is the transcription: hello\n```"), "hello")
    }
}
