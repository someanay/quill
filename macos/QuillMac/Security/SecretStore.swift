import Foundation
import Security

// Per-provider API-key storage in the macOS Keychain — the DPAPI-equivalent for this port. Keys are
// encrypted at rest by the OS, scoped to this app + user, never written to settings.json, never logged.
// Mirrors windows/.../Security/SecretStore.cs (per-provider isolation, blank clears, read failure → nil).
struct SecretStore {
    private let service = "com.tihor.quill.mac.byok.v1"

    func set(_ provider: SttProvider, _ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { delete(provider); return }
        let account = provider.rawValue
        let data = Data(trimmed.utf8)
        // Upsert: delete any existing item, then add. kSecAttrAccessibleAfterFirstUnlock keeps it
        // readable for a background/login-item launch without prompting.
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    func get(_ provider: SttProvider) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, let value = String(data: data, encoding: .utf8) else { return nil }
        return value
    }

    func has(_ provider: SttProvider) -> Bool { (get(provider)?.isEmpty == false) }

    func delete(_ provider: SttProvider) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
