import XCTest
import Foundation
import QuillCore
@testable import Quill

@MainActor private final class MemoryKeyDrafts: KeyDraftStorage {
    var values: [Provider: String] = [:]
    var fail = false
    func read(_ provider: Provider) throws -> String { values[provider] ?? "" }
    func write(_ value: String, provider: Provider) throws { if fail { throw QuillError.storage }; values[provider] = value }
    func clear(_ provider: Provider) throws { if fail { throw QuillError.storage }; values.removeValue(forKey: provider) }
}
private actor KeySetupTransport: HTTPTransport {
    private(set) var requests = 0
    let status: Int
    let simulateStale: Bool
    let slowSave: Bool
    init(status: Int = 200, simulateStale: Bool = false, slowSave: Bool = false) { self.status = status; self.simulateStale = simulateStale; self.slowSave = slowSave }
    func send(_ request: URLRequest) async throws -> HTTPResult {
        requests += 1
        if slowSave && requests == 2 {
            return await withCheckedContinuation { continuation in
                DispatchQueue.global().asyncAfter(deadline: .now() + 0.15) {
                    continuation.resume(returning: HTTPResult(data: Data(), status: 200))
                }
            }
        }
        if simulateStale {
            let old = request.value(forHTTPHeaderField: "Authorization")?.contains("old") == true
            // Deliberately ignore task cancellation to test the controller's generation guard.
            return await withCheckedContinuation { continuation in
                DispatchQueue.global().asyncAfter(deadline: .now() + (old ? 0.18 : 0.04)) {
                    continuation.resume(returning: HTTPResult(data: Data(), status: old ? 200 : 401))
                }
            }
        }
        return HTTPResult(data: Data(#"{"data":[{"id":"whisper-large-v3-turbo"}]}"#.utf8), status: status)
    }
}

@MainActor final class KeySetupTests: XCTestCase {
    func testGroqOnboardingIgnoresSettingsExpansionAndKeepsBothDrafts() throws {
        let model = AppModel(), memory = MemoryKeyDrafts(), preferences = UUID().uuidString + ".json"
        let settings = KeySetupController(model: model, activateSpeechProvider: false, store: memory, preferencesName: preferences)
        settings.toggle(.openrouter); settings.update("router-draft", provider: .openrouter); settings.stopValidation()
        let onboarding = KeySetupController(model: model, activateSpeechProvider: true, store: memory, preferencesName: preferences, fixedProvider: .groq)
        XCTAssertEqual(onboarding.expanded, .groq); XCTAssertEqual(onboarding.drafts[.openrouter], "router-draft")
        onboarding.update("groq-draft", provider: .groq); onboarding.stopValidation()
        let restored = KeySetupController(model: model, activateSpeechProvider: false, store: memory, preferencesName: preferences)
        XCTAssertEqual(restored.expanded, .openrouter); XCTAssertEqual(restored.drafts[.groq], "groq-draft")
        XCTAssertEqual(model.settings.provider, .groq)
    }
    private let candidate = "gsk_abcdefghijklmnop0123456789"
    private func controller(_ model: AppModel, store: MemoryKeyDrafts? = nil, activate: Bool = false, prefill: Bool = false, preferences: String = UUID().uuidString + ".json") -> KeySetupController {
        KeySetupController(model: model, activateSpeechProvider: activate, prefillSavedKey: prefill, store: store ?? MemoryKeyDrafts(), preferencesName: preferences, debounce: 5_000_000)
    }
    private func wait(_ setup: KeySetupController, _ phase: KeySetupPhase) async throws {
        for _ in 0..<100 { if setup.phase == phase { return }; try await Task.sleep(nanoseconds: 10_000_000) }
        XCTFail("Expected \(phase), found \(setup.phase)")
    }
    func testInitialProviderAndRememberedExpansionContainNoDraftSecret() throws {
        let model = AppModel(), memory = MemoryKeyDrafts(), preferences = UUID().uuidString + ".json"
        let setup = controller(model, store: memory, preferences: preferences)
        XCTAssertEqual(setup.expanded, .gemini)
        setup.toggle(.groq); setup.update(candidate, provider: .groq); setup.stopValidation()
        let restored = controller(model, store: memory, preferences: preferences)
        XCTAssertEqual(restored.expanded, .groq); XCTAssertEqual(restored.drafts[.groq], candidate)
        let data = try String(contentsOf: AppFiles.root.appendingPathComponent(preferences))
        XCTAssertTrue(data.contains("groq")); XCTAssertFalse(data.contains(candidate))
        setup.toggle(.groq)
        XCTAssertNil(controller(model, preferences: preferences).expanded)
    }
    func testDraftsRemainSeparateWhenSwitchingProviders() throws {
        let model = AppModel(), memory = MemoryKeyDrafts(), setup = controller(model, store: memory)
        setup.update("AIzaabcdefghijklmnop0123456789", provider: .gemini)
        setup.toggle(.groq); setup.update(candidate, provider: .groq)
        setup.toggle(.gemini); setup.stopValidation()
        XCTAssertEqual(setup.drafts[.groq], candidate)
        XCTAssertEqual(setup.drafts[.gemini], "AIzaabcdefghijklmnop0123456789")
        XCTAssertEqual(controller(model, store: memory).drafts, setup.drafts)
    }
    func testAutomaticValidationAndFormatGate() async throws {
        let transport = KeySetupTransport(), model = AppModel(providerAPI: ProviderAPI(transport: transport))
        let setup = controller(model)
        setup.toggle(.groq); setup.update("gsk_short", provider: .groq)
        try await Task.sleep(nanoseconds: 30_000_000)
        let emptyRequests = await transport.requests; XCTAssertEqual(emptyRequests, 0); XCTAssertFalse(setup.canSave)
        setup.update(candidate, provider: .groq)
        XCTAssertFalse(setup.canSave)
        try await wait(setup, .verified)
        XCTAssertTrue(setup.canSave)
        let requests = await transport.requests; XCTAssertEqual(requests, 1)
    }
    func testCanceledOldCandidateNeverVerifiesNewRejectedKey() async throws {
        let transport = KeySetupTransport(simulateStale: true), model = AppModel(providerAPI: ProviderAPI(transport: transport))
        let setup = controller(model); setup.toggle(.groq)
        setup.update("gsk_old_abcdefghijklmnop012345", provider: .groq)
        try await Task.sleep(nanoseconds: 25_000_000)
        setup.update("gsk_new_abcdefghijklmnop012345", provider: .groq)
        try await wait(setup, .rejected)
        try await Task.sleep(nanoseconds: 220_000_000)
        XCTAssertEqual(setup.phase, .rejected); XCTAssertFalse(setup.canSave)
    }
    func testUnavailableProviderRetainsDraftAndCannotSave() async throws {
        let model = AppModel(providerAPI: ProviderAPI(transport: KeySetupTransport(status: 503))), memory = MemoryKeyDrafts()
        let setup = controller(model, store: memory); setup.toggle(.groq); setup.update(candidate, provider: .groq)
        try await wait(setup, .unverified)
        XCTAssertEqual(memory.values[.groq], candidate)
        let saved = await setup.save(); XCTAssertFalse(saved); XCTAssertFalse(setup.canSave)
    }
    func testVerifiedSaveClearsDraftAndPreservesSpeechAndCleanup() async throws {
        let model = AppModel(providerAPI: ProviderAPI(transport: KeySetupTransport())), memory = MemoryKeyDrafts()
        model.settings = QuillSettings(); model.settings.selectProvider(.gemini); model.settings.setCleanupAutomatic(false); model.settings.selectCleanupProvider(.openrouter)
        let original = model.settings
        let setup = controller(model, store: memory); setup.toggle(.groq); setup.update(candidate, provider: .groq)
        try await wait(setup, .verified)
        let saved = await setup.save(); XCTAssertTrue(saved)
        let key = try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL)); defer { try? model.vault.delete(key) }
        XCTAssertEqual(try model.vault.read(key), candidate)
        XCTAssertNil(memory.values[.groq]); XCTAssertEqual(model.settings, original)
    }
    func testSpeechSetupPrefillsSavedKeyAndActivatesVerifiedProvider() async throws {
        let model = AppModel(providerAPI: ProviderAPI(transport: KeySetupTransport()))
        model.settings = QuillSettings(); model.settings.selectProvider(.gemini)
        try model.vault.save(candidate, provider: .groq, endpoint: Provider.groq.baseURL, label: "Prefill")
        let original = try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL))
        defer { for key in model.vault.records(.groq) where key.id == original.id || key.label == Provider.groq.title { try? model.vault.delete(key) } }
        let setup = controller(model, activate: true, prefill: true)
        XCTAssertEqual(setup.drafts[.groq], candidate)
        setup.toggle(.groq); try await wait(setup, .verified)
        let saved = await setup.save(); XCTAssertTrue(saved)
        XCTAssertEqual(model.settings.provider, .groq); XCTAssertEqual(model.settings.model, "whisper-large-v3-turbo")
        XCTAssertEqual(setup.drafts[.groq], candidate)
    }
    func testRealDraftKeychainRoundtripAndDeletion() throws {
        let vault = KeyDraftVault(namespace: "com.tihor.quill.tests.drafts." + UUID().uuidString)
        defer { try? vault.clearAll() }
        try vault.write(candidate, provider: .groq)
        try vault.write("AIzaabcdefghijklmnop0123456789", provider: .gemini)
        XCTAssertEqual(try vault.read(.groq), candidate)
        XCTAssertNotEqual(try vault.read(.gemini), candidate)
        try vault.write("", provider: .groq); XCTAssertEqual(try vault.read(.groq), "")
        XCTAssertFalse(try vault.read(.gemini).isEmpty)
    }
    func testLeavingDuringSlowSaveCancelsBeforeKeyOrProviderCommit() async throws {
        let transport = KeySetupTransport(slowSave: true), model = AppModel(providerAPI: ProviderAPI(transport: transport)), memory = MemoryKeyDrafts()
        model.settings = QuillSettings(); model.settings.selectProvider(.gemini)
        let original = model.settings, originalKeys = model.vault.keys
        let setup = controller(model, store: memory, activate: true); setup.toggle(.groq); setup.update(candidate, provider: .groq)
        try await wait(setup, .verified)
        let saving = Task { await setup.save() }
        for _ in 0..<100 { if await transport.requests >= 2 { break }; try await Task.sleep(nanoseconds: 5_000_000) }
        setup.stopValidation()
        let saved = await saving.value
        XCTAssertFalse(saved); XCTAssertEqual(setup.phase, .idle)
        XCTAssertEqual(model.settings, original); XCTAssertEqual(model.vault.keys, originalKeys)
        XCTAssertEqual(memory.values[.groq], candidate)
    }
    func testDraftStorageFailureIsVisibleAndKeepsEditableValue() {
        let memory = MemoryKeyDrafts(); memory.fail = true
        let setup = controller(AppModel(), store: memory)
        setup.update("partial key", provider: .gemini)
        XCTAssertTrue(setup.storageError); XCTAssertEqual(setup.drafts[.gemini], "partial key")
    }
}
