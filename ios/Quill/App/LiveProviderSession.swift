#if DEBUG
import Foundation
import QuillCore

// Explicit manual CI launch only. Keys enter a fresh simulator's private container
// once, move into its actual Keychain, and never enter the keyboard or artifacts.
@MainActor enum LiveProviderSession {
    static var requested: Bool { AppFiles.isUITesting && AppFiles.uiSessionID != nil && ProcessInfo.processInfo.arguments.contains("--ui-live-providers") }
    static func apiIfRequested() -> ProviderAPI? {
        guard requested else { return nil }
        LiveHTTPAudit.outputURL = AppFiles.root.appendingPathComponent("live-http-audit.json")
        return ProviderAPI(transport: LiveDirectTransport())
    }
    static func configure(_ model: AppModel) throws {
        guard requested else { return }
        let path = AppFiles.root.appendingPathComponent("live-provider-bootstrap.json")
        guard FileManager.default.fileExists(atPath: path.path) else { return }
        defer { try? FileManager.default.removeItem(at: path) }
        let data = try Data(contentsOf: path)
        guard let config = try JSONSerialization.jsonObject(with: data) as? [String: String],
              let speech = config["speech"].flatMap(Provider.init(rawValue:)), let cleanup = config["cleanup"].flatMap(Provider.init(rawValue:)),
              [.gemini,.openrouter].contains(speech), [.gemini,.openrouter].contains(cleanup) else { throw QuillError.malformedResponse }
        for provider in [Provider.gemini,.openrouter] {
            guard let key = config[provider.rawValue], !key.isEmpty else { throw QuillError.noKey }
            try model.vault.save(key, provider: provider, endpoint: provider.baseURL, label: "Temporary live QA")
        }
        model.keys = model.vault.keys
        model.settings.provider = speech; model.settings.model = speech.defaultModel
        model.settings.cleanupProvider = cleanup; model.settings.cleanupModel = cleanup.defaultCleanupModel
        model.settings.cleanupAutomatic = false; model.settings.cleanupEnabled = true
        model.settings.privateMode = true; model.settings.historyEnabled = false
        model.settings.onboardingStep = 3; model.settings.onboardingComplete = false
    }
}

private struct LiveDirectTransport: HTTPTransport {
    let actual = DirectTransport()
    func send(_ request: URLRequest) async throws -> HTTPResult {
        let start = Date()
        do {
            let result = try await actual.send(request)
            LiveHTTPAudit.record(request: request, data: result.data, status: result.status, started: start)
            return result
        } catch {
            LiveHTTPAudit.record(request: request, data: nil, status: 0, started: start, error: error is CancellationError ? "cancelled" : "network-or-timeout")
            throw error
        }
    }
}
#endif
