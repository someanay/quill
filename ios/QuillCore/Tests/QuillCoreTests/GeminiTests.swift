import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private struct GeminiStub: HTTPTransport {
    let body: String
    let status: Int
    init(_ body: String, status: Int = 200) { self.body = body; self.status = status }
    func send(_ request: URLRequest) async throws -> HTTPResult { HTTPResult(data: Data(body.utf8), status: status) }
}
final class GeminiTests: XCTestCase {
    private let wav = Data("RIFF".utf8) + Data(repeating: 0, count: 100)
    private var config: ProviderConfiguration { try! ProviderConfiguration(provider: .gemini, model: "gemini-3.1-flash-lite") }
    func testThoughtPartsNeverBecomeTranscript() async throws {
        let api = ProviderAPI(transport: GeminiStub(#"{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"Internal reasoning"},{"text":"Transcript: Hello."}]}}]}"#))
        let result = try await api.transcribe(config, key: "x", wav: wav)
        XCTAssertEqual(result, "Hello.")
    }
    func testNoFinishReasonIsAcceptedLikeAndroid() async throws {
        let api = ProviderAPI(transport: GeminiStub(#"{"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"#))
        let result = try await api.transcribe(config, key: "x", wav: wav)
        XCTAssertEqual(result, "Hello")
    }
    func testBlockedAndIncompleteOutputsAreRejected() async throws {
        for reason in ["SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY", "MAX_TOKENS"] {
            let body = "{\"candidates\":[{\"finishReason\":\"\(reason)\",\"content\":{\"parts\":[{\"text\":\"partial\"}]}}]}"
            do { _ = try await ProviderAPI(transport: GeminiStub(body)).transcribe(config, key: "x", wav: wav); XCTFail(reason) }
            catch { XCTAssertEqual(error as? QuillError, .malformedResponse) }
        }
        do { _ = try await ProviderAPI(transport: GeminiStub(#"{"promptFeedback":{"blockReason":"SAFETY"}}"#)).transcribe(config, key: "x", wav: wav); XCTFail("Blocked prompt accepted") }
        catch { XCTAssertEqual(error as? QuillError, .malformedResponse) }
    }
    func testProviderSpecificErrors() async throws {
        for (body, status, expected) in [("API_KEY_INVALID", 400, QuillError.invalidKey), ("RESOURCE_EXHAUSTED", 429, .quota)] {
            do { _ = try await ProviderAPI(transport: GeminiStub(body, status: status)).transcribe(config, key: "x", wav: wav); XCTFail("Expected failure") }
            catch { XCTAssertEqual(error as? QuillError, expected) }
        }
    }
    func testConservativeWrappingMatchesAndroid() {
        XCTAssertEqual(ProviderAPI.stripGeminiWrapping("```text\nTranscript: Hello.\n```"), "Hello.")
        XCTAssertEqual(ProviderAPI.stripGeminiWrapping("Here is the transcription: Hello"), "Hello")
        XCTAssertEqual(ProviderAPI.stripGeminiWrapping("transcription is useful"), "transcription is useful")
        XCTAssertEqual(ProviderAPI.stripGeminiWrapping("\"Hello\""), "\"Hello\"")
    }
}
