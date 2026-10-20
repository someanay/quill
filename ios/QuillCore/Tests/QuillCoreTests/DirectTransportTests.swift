import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private final class OverloadProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var seen: [Data?] = []
    static var alwaysUnavailable = false
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); Self.seen.append(request.httpBody); let unavailable = Self.alwaysUnavailable || Self.seen.count == 1; Self.lock.unlock()
        let response = HTTPURLResponse(url: request.url!,statusCode: unavailable ? 503 : 200,httpVersion: "HTTP/1.1",headerFields:nil)!
        client?.urlProtocol(self,didReceive:response,cacheStoragePolicy:.notAllowed)
        client?.urlProtocol(self,didLoad:Data((unavailable ? "{}" : "{\"text\":\"same take\"}").utf8)); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
    static func reset(always: Bool = false) { lock.lock(); defer { lock.unlock() }; seen = []; alwaysUnavailable = always }
    static func bodies() -> [Data?] { lock.lock(); defer { lock.unlock() }; return seen }
}

final class DirectTransportTests: XCTestCase {
    private func transport() -> DirectTransport { let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [OverloadProtocol.self]; return DirectTransport(configuration:config) }
    private func request(timeout: Double = 10) -> URLRequest {
        var request = URLRequest(url:URL(string:"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent")!)
        request.httpMethod = "POST"; request.httpBody = Data("same synthetic take".utf8); request.timeoutInterval = timeout; request.setValue("fixture-key",forHTTPHeaderField:"x-goog-api-key"); return request
    }
    func testExplicitGeminiOverloadRetriesIdenticalBody() async throws {
        OverloadProtocol.reset()
        let result = try await transport().send(request())
        XCTAssertEqual(result.status,200); let bodies = OverloadProtocol.bodies(); XCTAssertEqual(bodies.count,2); XCTAssertEqual(bodies.first!,bodies.last!)
    }
    func testOriginalResourceDeadlineIncludesBackoff() async throws {
        OverloadProtocol.reset(always:true); let start = Date()
        do { _ = try await transport().send(request(timeout:0.12)); XCTFail("Expected a bounded deadline") } catch { XCTAssertLessThan(Date().timeIntervalSince(start),2) }
        XCTAssertEqual(OverloadProtocol.bodies().count,1)
    }
    func testCancellationDuringBackoffDoesNotResend() async throws {
        OverloadProtocol.reset(always:true); let client = transport(), req = request()
        let work = Task { try await client.send(req) }
        try await Task.sleep(nanoseconds:100_000_000); work.cancel()
        do { _ = try await work.value; XCTFail("Expected cancellation") } catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertEqual(OverloadProtocol.bodies().count,1)
    }
}
