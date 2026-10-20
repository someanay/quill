#if DEBUG
import Foundation

// Real HTTPS pass-through, used only by standalone QA targets / DEBUG UI tests.
// Responses are never fabricated. Evidence excludes request headers, keys and bodies.
final class LiveHTTPAudit: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private static var events: [[String: Any]] = []
    static var outputURL: URL? {
        didSet {
            // The installed-keyboard journey deliberately cold-launches the host.
            // Retain earlier real HTTP evidence from this same isolated session.
            let prior: [[String: Any]]
            if let outputURL, let data = try? Data(contentsOf: outputURL), data.count <= 4 * 1024 * 1024,
               let entries = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] { prior = entries }
            else { prior = [] }
            lock.lock(); events = prior; lock.unlock()
        }
    }
    private var work: Task<Void, Never>?
    private final class RefuseRedirect: NSObject, URLSessionTaskDelegate {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
    }
    override class func canInit(with request: URLRequest) -> Bool { ["generativelanguage.googleapis.com", "openrouter.ai"].contains(request.url?.host ?? "") }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    static func configuration() -> URLSessionConfiguration { let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [LiveHTTPAudit.self]; return config }
    static func snapshot() -> [[String: Any]] { lock.lock(); defer { lock.unlock() }; return events }
    static func record(request: URLRequest, data: Data?, status: Int, started: Date, error: String? = nil) {
        var entry: [String: Any] = ["host": request.url?.host ?? "", "path": request.url?.path ?? "", "status": status, "milliseconds": Int(Date().timeIntervalSince(started) * 1000)]
        if let error { entry["error"] = error }
        if let body = request.httpBody, let json = try? JSONSerialization.jsonObject(with: body) as? [String: Any], let model = json["model"] as? String { entry["model"] = model }
        if let data, let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let choice = (root["choices"] as? [[String: Any]])?.first {
                entry["output"] = (choice["message"] as? [String: Any])?["content"] as? String
                entry["finish"] = choice["finish_reason"] as? String
            } else if let candidate = (root["candidates"] as? [[String: Any]])?.first {
                entry["finish"] = candidate["finishReason"] as? String
                entry["output"] = (((candidate["content"] as? [String: Any])?["parts"] as? [[String: Any]])?.filter { $0["thought"] as? Bool != true }.compactMap { $0["text"] as? String }.joined())
            } else { entry["output"] = root["text"] as? String }
            entry["actualModel"] = root["model"] as? String
        }
        lock.lock(); defer { lock.unlock() }; events.append(entry)
        if let outputURL, let data = try? JSONSerialization.data(withJSONObject: events, options: [.prettyPrinted, .sortedKeys]) { try? data.write(to: outputURL, options: .atomic) }
    }
    override func startLoading() {
        let incoming = request, started = Date()
        work = Task { [weak self] in
            guard let self else { return }
            let config = URLSessionConfiguration.ephemeral
            config.urlCache = nil; config.httpCookieStorage = nil; config.urlCredentialStorage = nil
            config.timeoutIntervalForResource = incoming.timeoutInterval
            let session = URLSession(configuration: config, delegate: RefuseRedirect(), delegateQueue: nil)
            defer { session.invalidateAndCancel() }
            do {
                let (data, response) = try await session.data(for: incoming)
                Self.record(request: incoming, data: data, status: (response as? HTTPURLResponse)?.statusCode ?? 0, started: started)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data); client?.urlProtocolDidFinishLoading(self)
            } catch {
                Self.record(request: incoming, data: nil, status: 0, started: started, error: error is CancellationError ? "cancelled" : "network-or-timeout")
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
    }
    override func stopLoading() { work?.cancel() }
}
#endif
