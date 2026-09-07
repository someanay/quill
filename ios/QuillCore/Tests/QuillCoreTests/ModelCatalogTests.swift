import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private actor CatalogPages: HTTPTransport {
    var pages: [String]
    var requests: [URLRequest] = []
    init(_ pages: [String]) { self.pages = pages }
    func send(_ request: URLRequest) async throws -> HTTPResult {
        requests.append(request)
        guard !pages.isEmpty else { throw QuillError.network }
        return HTTPResult(data: Data(pages.removeFirst().utf8), status: 200)
    }
}
final class ModelCatalogTests: XCTestCase {
    func testRouterRequiresTranscriptionOutputAndRejectsAudioChatModel() throws {
        let data = Data(#"{"data":[{"id":"chat-audio","architecture":{"input_modalities":["audio"],"output_modalities":["text"]}},{"id":"speech","architecture":{"output_modalities":["transcription"]}},{"id":"inactive","active":false,"architecture":{"output_modalities":["transcription"]}}]}"#.utf8)
        XCTAssertEqual(try ModelCatalog.parse(data: data, provider: .openrouter, use: .dictation).models.map(\.id), ["speech"])
        let config = try ProviderConfiguration(provider: .openrouter, model: "speech")
        let request = try ProviderRequests.models(config, key: "")
        XCTAssertEqual(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems, [URLQueryItem(name: "output_modalities", value: "transcription")])
        XCTAssertNil(request.value(forHTTPHeaderField: "Authorization")); XCTAssertEqual(request.timeoutInterval, 12)
    }
    func testCleanupFiltersNonTextCapabilitiesAndRanksFreeModels() throws {
        let data = Data(#"{"data":[{"id":"guard-safety","architecture":{"output_modalities":["text"]}},{"id":"llm","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","audio":"0.1"}},{"id":"llm:free","architecture":{"output_modalities":["text"]}},{"id":"image-generator","architecture":{"output_modalities":["image"]}}]}"#.utf8)
        let models = try ModelCatalog.parse(data: data, provider: .openrouter, use: .cleanup).models
        XCTAssertEqual(models.map(\.id), ["llm:free", "llm"])
        XCTAssertTrue(models[0].isFree); XCTAssertFalse(models[1].isFree); XCTAssertTrue(models[1].isPaid)
    }
    func testGeminiPaginationIncludesEncodedTokenAndRetainsAllModels() async throws {
        let pages = CatalogPages([#"{"models":[{"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"a+b/="}"#, #"{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}]}"#])
        let config = try ProviderConfiguration(provider: .gemini, model: Provider.gemini.defaultModel)
        let models = try await ProviderAPI(transport: pages).models(config, key: "synthetic-key", cleanup: false)
        XCTAssertEqual(models.map(\.id), ["gemini-3.8-flash", "gemini-3.1-flash-lite"])
        let requests = await pages.requests
        XCTAssertEqual(requests.count, 2)
        XCTAssertEqual(URLComponents(url: requests[1].url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "pageToken" })?.value, "a+b/=")
    }
    func testRepeatedPageTokenStopsCatalogLoop() async throws {
        let body = #"{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"repeat"}"#
        let pages = CatalogPages([body, body])
        do {
            _ = try await ProviderAPI(transport: pages).models(ProviderConfiguration(provider: .gemini, model: "gemini-3.8-flash"), key: "synthetic-key", cleanup: false)
            XCTFail("Repeated token accepted")
        } catch { XCTAssertEqual(error as? QuillError, .malformedResponse) }
        let requests = await pages.requests; XCTAssertEqual(requests.count, 2)
    }
    func testMissingCatalogRejectedAndSpeechDiarizationExcluded() throws {
        XCTAssertThrowsError(try ModelCatalog.parse(data: Data("{}".utf8), provider: .groq, use: .dictation))
        let data = Data(#"{"data":[{"id":"gpt-transcribe-diarize"},{"id":"whisper-realtime"},{"id":"whisper-large-v3-turbo"}]}"#.utf8)
        XCTAssertEqual(try ModelCatalog.parse(data: data, provider: .groq, use: .dictation).models.map(\.id), ["whisper-large-v3-turbo"])
    }
}
