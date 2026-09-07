import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private struct StubTransport: HTTPTransport {
    let result: HTTPResult
    func send(_ request: URLRequest) async throws -> HTTPResult { result }
}

final class ProviderTests: XCTestCase {
    private let wav = Data("RIFF".utf8) + Data(repeating: 0, count: 100)
    func testAllProviderRequestShapes() throws {
        for provider in Provider.allCases {
            let c = try ProviderConfiguration(provider: provider, customBaseURL: "https://speech.example/v1", model: provider.defaultModel.isEmpty ? "private-whisper" : provider.defaultModel)
            let r = try ProviderRequests.speech(c, key: "secret", wav: wav)
            XCTAssertEqual(r.httpMethod, "POST")
            XCTAssertEqual(r.url?.scheme, "https")
            XCTAssertFalse(r.url!.absoluteString.contains("secret"))
            if provider == .gemini {
                XCTAssertEqual(r.value(forHTTPHeaderField: "x-goog-api-key"), "secret")
                XCTAssertNil(r.value(forHTTPHeaderField: "Authorization"))
                let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: r.httpBody!) as? [String: Any])
                XCTAssertNotNil(json["contents"])
            } else {
                XCTAssertEqual(r.value(forHTTPHeaderField: "Authorization"), "Bearer secret")
                if provider == .openrouter {
                    let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: r.httpBody!) as? [String: Any])
                    XCTAssertNotNil(json["input_audio"]); XCTAssertNil(json["file"])
                } else {
                    let body = String(data: r.httpBody!, encoding: .utf8)!
                    XCTAssertTrue(body.contains("name=\"file\"")); XCTAssertTrue(body.contains(ProviderRequests.romanizePrompt))
                }
            }
        }
    }
    func testLanguageAutoOmitsEnglishPrompt() throws {
        let c = try ProviderConfiguration(provider: .groq, model: "whisper-large-v3", language: "auto")
        let body = String(data: try ProviderRequests.speech(c, key: "x", wav: wav).httpBody!, encoding: .utf8)!
        XCTAssertFalse(body.contains("name=\"language\"")); XCTAssertFalse(body.contains("Hinglish"))
    }
    func testInvalidKeyRejectedBeforeNetwork() throws {
        let c = try ProviderConfiguration(provider: .groq, model: "whisper")
        for key in ["", "x\nAuthorization: y", "a b"] { XCTAssertThrowsError(try ProviderRequests.speech(c, key: key, wav: wav)) }
        XCTAssertThrowsError(try ProviderRequests.speech(c, key: "x", wav: Data()))
    }
    func testCleanupRoutesAndTimeout() throws {
        let c = try ProviderConfiguration(provider: .gemini, model: "gemini-3.5-flash-lite")
        let r = try ProviderRequests.cleanup(c, key: "geminikey", raw: "send my email", style: .formal)
        XCTAssertTrue(r.url!.absoluteString.hasSuffix("/v1beta/openai/chat/completions"))
        XCTAssertEqual(r.value(forHTTPHeaderField: "Authorization"), "Bearer geminikey")
        XCTAssertEqual(r.timeoutInterval, 4)
        XCTAssertTrue(String(data: r.httpBody!, encoding: .utf8)!.contains("DICTATED TEXT"))
    }
    func testHTTPErrorClassification() {
        for (code, error) in [(401, QuillError.invalidKey), (403, .invalidKey), (402, .quota), (429, .rateLimit), (503, .provider(503)), (302, .provider(302))] {
            XCTAssertThrowsError(try ProviderAPI.check(HTTPResult(data: Data(), status: code))) { XCTAssertEqual($0 as? QuillError, error) }
        }
        XCTAssertThrowsError(try ProviderAPI.check(HTTPResult(data: Data("insufficient_quota".utf8), status: 429))) { XCTAssertEqual($0 as? QuillError, .quota) }
    }
    func testTranscriptionAndEmptyFailure() async throws {
        let c = try ProviderConfiguration(provider: .openai, model: "whisper-1")
        let api = ProviderAPI(transport: StubTransport(result: HTTPResult(data: Data(#"{"text":" hello "}"#.utf8), status: 200)))
        let text = try await api.transcribe(c, key: "x", wav: wav)
        XCTAssertEqual(text, "hello")
        let empty = ProviderAPI(transport: StubTransport(result: HTTPResult(data: Data(#"{"text":" "}"#.utf8), status: 200)))
        do { _ = try await empty.transcribe(c, key: "x", wav: wav); XCTFail("Empty transcript accepted") }
        catch { XCTAssertEqual(error as? QuillError, .noSpeech) }
    }
    func testCleanupCannotReplaceDictationWithAssistantReply() async throws {
        let c = try ProviderConfiguration(provider: .groq, model: "llama-3.3-70b-versatile")
        let api = ProviderAPI(transport: StubTransport(result: HTTPResult(data: Data(#"{"choices":[{"message":{"content":"I'm ready to type your email"},"finish_reason":"stop"}]}"#.utf8), status: 200)))
        let result = try await api.clean(c, key: "x", raw: "type my email", style: .neutral)
        XCTAssertNil(result)
    }
    func testCleanupHTTPFailureUsesRaw() async throws {
        let c = try ProviderConfiguration(provider: .groq, model: "llama")
        let api = ProviderAPI(transport: StubTransport(result: HTTPResult(data: Data(), status: 500)))
        let result = try await api.clean(c, key: "x", raw: "hello", style: .neutral)
        XCTAssertNil(result)
    }
}
