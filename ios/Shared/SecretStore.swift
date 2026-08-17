import Foundation
import Security

// Per-provider API-key storage in the iOS Keychain. To share keys with the keyboard extension, set
// `SecretStore.accessGroup` to a team-prefixed keychain group declared in BOTH targets'
// keychain-access-groups entitlements (see ios/README.md). nil = the target's private keychain.
struct SecretStore {
    private let service = "com.tihor.quill.byok.v1"

    /// Shared keychain group for app ↔ keyboard-extension key access. Configure per your team; nil keeps
    /// keys in the app's private keychain (the in-app dictation screen still works).
    static var accessGroup: String? = nil

    func set(_ provider: SttProvider, _ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { delete(provider); return }
        var base = query(provider)
        SecItemDelete(base as CFDictionary)
        base[kSecValueData as String] = Data(trimmed.utf8)
        base[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(base as CFDictionary, nil)
    }

    func get(_ provider: SttProvider) -> String? {
        var q = query(provider)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data, let value = String(data: data, encoding: .utf8) else { return nil }
        return value
    }

    func has(_ provider: SttProvider) -> Bool { (get(provider)?.isEmpty == false) }
    func delete(_ provider: SttProvider) { SecItemDelete(query(provider) as CFDictionary) }

    private func query(_ provider: SttProvider) -> [String: Any] {
        var q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue,
        ]
        if let group = SecretStore.accessGroup { q[kSecAttrAccessGroup as String] = group }
        return q
    }
}
