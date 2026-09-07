import Foundation
import Security
import Combine
import QuillCore

final class KeySetupDraftStore {
    private let service: String
    private let expansionPath: URL
    init(directory: URL = AppPaths.root, namespace: String? = nil) {
        service = namespace ?? (AppPaths.testMode ? "com.tihor.quill.mac.tests.key-drafts" : "com.tihor.quill.mac.key-drafts")
        expansionPath = directory.appendingPathComponent("key-setup-expanded.txt")
    }
    private func query(_ provider: Provider) -> [String:Any] { [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service, kSecAttrAccount as String:provider.rawValue] }
    func read(_ provider: Provider) throws -> String {
        var lookup = query(provider); lookup[kSecReturnData as String] = true; lookup[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?; let status = SecItemCopyMatching(lookup as CFDictionary, &result)
        if status == errSecItemNotFound { return "" }
        guard status == errSecSuccess, let data = result as? Data, let value = String(data: data, encoding: .utf8) else { throw QuillError.storage }
        return value
    }
    func write(_ provider: Provider, value: String) throws {
        if value.isEmpty { try clear(provider); return }
        guard value.count <= 4096 else { throw QuillError.badKey }
        let attributes = [kSecValueData as String:Data(value.utf8)]
        let status = SecItemUpdate(query(provider) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var item = query(provider); item[kSecValueData as String] = Data(value.utf8); item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw QuillError.storage }
        } else if status != errSecSuccess { throw QuillError.storage }
    }
    func clear(_ provider: Provider) throws {
        let status = SecItemDelete(query(provider) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw QuillError.storage }
    }
    func clearAll() throws { for provider in Provider.allCases { try clear(provider) }; try? FileManager.default.removeItem(at: expansionPath) }
    func expanded(default provider: Provider) -> String { (try? String(contentsOf: expansionPath, encoding: .utf8)) ?? provider.rawValue }
    func rememberExpansion(_ value: String) throws {
        try value.write(to: expansionPath, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: expansionPath.path)
    }
}

enum KeyCheckState: Equatable { case empty, format, waiting, checking, valid, rejected, unverified, needsCheck }

@MainActor final class KeySetupController: ObservableObject {
    static let guidedProviders: [Provider] = [.gemini, .groq, .openrouter]
    @Published private(set) var drafts: [Provider:String] = [:]
    @Published private(set) var checks: [Provider:KeyCheckState] = [:]
    @Published private(set) var expanded = "gemini"
    @Published private(set) var saving = false
    @Published private(set) var storageError = false
    private let settings: SettingsStore
    private let keys: KeychainStore
    private let client: ProviderClient
    private let draftStore: KeySetupDraftStore
    private let activateSpeech: Bool
    private let prefill: Bool
    private var validation: Task<Void,Never>?
    private var saveTask: Task<Void,Never>?
    private var generation = UUID()
    private var verified: [Provider:(candidate: String, baseURL: String)] = [:]
    init(settings: SettingsStore, keys: KeychainStore, client: ProviderClient, draftStore: KeySetupDraftStore = KeySetupDraftStore(), activateSpeech: Bool, prefill: Bool = false, initialProvider: Provider = .gemini) {
        self.settings = settings; self.keys = keys; self.client = client; self.draftStore = draftStore; self.activateSpeech = activateSpeech; self.prefill = prefill
        expanded = draftStore.expanded(default: initialProvider)
        for provider in Provider.allCases {
            do { let draft = try draftStore.read(provider); drafts[provider] = draft.isEmpty && prefill ? keys.active(provider, settings: settings.value) ?? "" : draft }
            catch { drafts[provider] = ""; storageError = true }
        }
    }
    func appear() { if let provider = Provider(rawValue: expanded) { validate(provider) } }
    func disappear() { generation = UUID(); validation?.cancel(); saveTask?.cancel(); saving = false }
    func expand(_ provider: Provider) {
        guard !saving else { return }
        generation = UUID(); validation?.cancel()
        expanded = expanded == provider.rawValue ? "" : provider.rawValue
        do { try draftStore.rememberExpansion(expanded) } catch { storageError = true }
        if expanded == provider.rawValue { validate(provider) }
    }
    func update(_ provider: Provider, value: String) {
        guard !saving, value.count <= 4096 else { return }
        drafts[provider] = value
        do { try draftStore.write(provider, value: value); storageError = false } catch { storageError = true }
        validate(provider)
    }
    func validate(_ provider: Provider, explicitCustomCheck: Bool = false) {
        generation = UUID(); let id = generation; validation?.cancel()
        verified[provider] = nil
        let candidate = (drafts[provider] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !candidate.isEmpty else { checks[provider] = .empty; return }
        guard ProviderClient.plausibleKey(provider: provider, key: candidate) else { checks[provider] = .format; return }
        guard provider != .custom || explicitCustomCheck else { checks[provider] = .needsCheck; return }
        checks[provider] = .waiting
        let customURL = settings.value.customBaseURL
        validation = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: 650_000_000)
                guard let self, self.generation == id else { return }
                self.checks[provider] = .checking
                do { try await self.client.validateKey(provider: provider, key: candidate, baseURL: customURL); try Task.checkCancellation(); guard self.generation == id else { return }; self.verified[provider] = (candidate, customURL); self.checks[provider] = .valid }
                catch is CancellationError { }
                catch { guard self.generation == id else { return }; self.checks[provider] = error as? QuillError == .badKey || error as? QuillError == .provider(400) ? .rejected : .unverified }
            } catch { }
        }
    }
    func save(_ provider: Provider, completion: @escaping () -> Void) {
        guard checks[provider] == .valid, !saving else { return }
        let candidate = (drafts[provider] ?? "").trimmingCharacters(in: .whitespacesAndNewlines), customURL = settings.value.customBaseURL
        guard ProviderClient.plausibleKey(provider: provider, key: candidate) else { return }
        guard let checked = verified[provider], checked.candidate == candidate, provider != .custom || checked.baseURL == customURL else { validate(provider); return }
        saving = true; storageError = false; let id = generation
        saveTask = Task { [weak self] in
            guard let self else { return }
            defer { self.saving = false }
            do {
                let models = self.activateSpeech ? (try? await self.client.models(provider: provider, use: .dictation, baseURL: customURL, key: candidate)) : nil
                try Task.checkCancellation(); guard self.generation == id else { return }
                let slot = try self.keys.save(provider: provider, label: "", secret: candidate, status: "valid", customURL: customURL)
                self.settings.selectSavedKey(slot, activateSpeechProvider: self.activateSpeech, preferredModel: models?.first(where: \.isFree)?.id)
                guard !self.settings.storageFailed else { throw QuillError.storage }
                try self.draftStore.clear(provider)
                self.drafts[provider] = self.prefill ? candidate : ""
                self.checks[provider] = self.prefill ? .valid : .empty
                completion()
            } catch is CancellationError { }
            catch { self.storageError = true }
        }
    }
}
