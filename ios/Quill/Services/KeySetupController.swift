import Foundation
import Combine
import Security
import QuillCore

@MainActor protocol KeyDraftStorage {
    func read(_ provider: Provider) throws -> String
    func write(_ value: String, provider: Provider) throws
    func clear(_ provider: Provider) throws
}

/// Unfinished keys have the same host-only Keychain boundary as saved credentials.
/// Only the expanded provider identifier goes to the protected preferences file.
@MainActor final class KeyDraftVault: KeyDraftStorage {
    private let service: String
    init(namespace: String? = nil) { service = namespace ?? "com.tihor.quill.ios.key-drafts" + (AppFiles.isTestProcess ? ".tests" : "") }
    private func query(_ provider: Provider) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: provider.rawValue, kSecAttrSynchronizable as String: false]
    }
    func read(_ provider: Provider) throws -> String {
        var query = query(provider); query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &value)
        if status == errSecItemNotFound { return "" }
        guard status == errSecSuccess, let data = value as? Data, let text = String(data: data, encoding: .utf8) else { throw KeychainFailure(status: status) }
        return text
    }
    func write(_ value: String, provider: Provider) throws {
        if value.isEmpty { try clear(provider); return }
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8), kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        var status = SecItemUpdate(query(provider) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query(provider).merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw KeychainFailure(status: status) }
    }
    func clear(_ provider: Provider) throws {
        let status = SecItemDelete(query(provider) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainFailure(status: status) }
    }
    func clearAll() throws { for provider in KeySetupPolicy.guidedProviders { try clear(provider) } }
}

enum KeySetupPhase: Equatable { case idle, checking, verified, rejected, unverified, saving }
private struct KeySetupPreferences: Codable { let expanded: String }

@MainActor final class KeySetupController: ObservableObject {
    @Published private(set) var expanded: Provider?
    @Published private(set) var phase: KeySetupPhase = .idle
    @Published private(set) var drafts: [Provider: String] = [:]
    @Published private(set) var storageError = false
    private let model: AppModel
    private let store: any KeyDraftStorage
    private let preferencesName: String
    private let activateSpeechProvider: Bool
    private let prefillSavedKey: Bool
    private let debounce: UInt64
    private var work: Task<Void, Never>?
    private var saveWork: Task<Bool, Never>?
    private var generation = UUID()
    private var verifiedCandidate: String?

    init(model: AppModel, activateSpeechProvider: Bool, prefillSavedKey: Bool = false, initialProvider: Provider = .gemini,
         store: (any KeyDraftStorage)? = nil, preferencesName: String = "key-setup.json", debounce: UInt64 = 650_000_000) {
        self.model = model; self.activateSpeechProvider = activateSpeechProvider; self.prefillSavedKey = prefillSavedKey
        self.store = store ?? KeyDraftVault(); self.preferencesName = preferencesName; self.debounce = debounce
        let remembered = AppFiles.isUITesting ? nil : AppFiles.read(KeySetupPreferences.self, name: preferencesName)
        if let remembered { expanded = Provider(rawValue: remembered.expanded) } else { expanded = initialProvider }
        if let expanded, !KeySetupPolicy.guidedProviders.contains(expanded) { self.expanded = .gemini }
        for provider in KeySetupPolicy.guidedProviders {
            do {
                let draft = try self.store.read(provider)
                drafts[provider] = !draft.isEmpty ? draft : prefillSavedKey ? ((try? model.vault.secret(provider, endpoint: provider.baseURL)) ?? "") : ""
            } catch { storageError = true }
        }
    }
    deinit { work?.cancel(); saveWork?.cancel() }
    var canSave: Bool { phase == .verified && expanded.flatMap { drafts[$0] }?.trimmingCharacters(in: .whitespacesAndNewlines) == verifiedCandidate }
    func toggle(_ provider: Provider) {
        guard phase != .saving else { return }
        expanded = expanded == provider ? nil : provider
        do { try AppFiles.write(KeySetupPreferences(expanded: expanded?.rawValue ?? ""), name: preferencesName) } catch { storageError = true }
        scheduleValidation()
    }
    func update(_ text: String, provider: Provider) {
        guard phase != .saving, text.utf16.count <= 4096 else { return }
        drafts[provider] = text
        do { try store.write(text, provider: provider); storageError = false } catch { storageError = true }
        scheduleValidation()
    }
    func stopValidation() {
        generation = UUID(); work?.cancel(); work = nil; saveWork?.cancel(); saveWork = nil
        verifiedCandidate = nil; phase = .idle
    }
    func scheduleValidation() {
        stopValidation()
        guard let provider = expanded, let value = drafts[provider], KeySetupPolicy.plausible(provider, value) else { return }
        let candidate = value.trimmingCharacters(in: .whitespacesAndNewlines), token = generation
        work = Task { [weak self] in
            guard let self else { return }
            do {
                try await Task.sleep(nanoseconds: self.debounce)
                guard token == self.generation else { return }
                self.phase = .checking
                try await self.model.validateKeyCandidate(provider: provider, secret: candidate)
                try Task.checkCancellation()
                guard token == self.generation, self.expanded == provider, self.drafts[provider]?.trimmingCharacters(in: .whitespacesAndNewlines) == candidate else { return }
                self.verifiedCandidate = candidate; self.phase = .verified
            } catch is CancellationError { }
            catch {
                guard token == self.generation else { return }
                self.phase = (error as? QuillError) == .invalidKey ? .rejected : .unverified
            }
        }
    }
    func save() async -> Bool {
        guard canSave, let provider = expanded, let candidate = verifiedCandidate else { return false }
        phase = .saving; let token = generation
        let operation = Task { [model, activateSpeechProvider] in
            await model.verifyAndSaveKey(provider: provider, secret: candidate, label: "", activateSpeechProvider: activateSpeechProvider)
        }
        saveWork = operation
        let saved = await withTaskCancellationHandler(operation: { await operation.value }, onCancel: { operation.cancel() })
        guard token == generation, !Task.isCancelled else { return false }
        saveWork = nil
        guard saved else { phase = .unverified; return false }
        do {
            try store.clear(provider); storageError = false
            drafts[provider] = prefillSavedKey ? candidate : ""
            phase = prefillSavedKey ? .verified : .idle
            return true
        } catch { storageError = true; phase = .verified; return false }
    }
}
