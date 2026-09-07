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

final class ProviderClientTests: XCTestCase {
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
    func testCleanupResponseGuardAndFinishReason() async throws {
        for (text, finish, expected) in [("Type my email.", "stop", "Type my email."), ("I am ready to type your email.", "stop", "type my email"), ("Type my email.", "length", "type my email")] {
            let client = client { request in
                XCTAssertEqual(request.timeoutInterval, 4)
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
