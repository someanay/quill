import Foundation
import Security
import CryptoKit
import QuillCore

struct PendingTake: Codable, Equatable {
    var audio: Data?
    var transcript: String?
    var settings: QuillSettings
    var duration: Double
    var clipboardOnly: Bool
    var wordCapture: Bool
}

protocol PendingTakeStoring {
    func load() throws -> PendingTake?
    func save(_ take: PendingTake) throws
    func clear() throws
}

final class PendingAudioStore: PendingTakeStoring {
    private let path: URL
    private let service: String
    init(directory: URL = AppPaths.root, namespace: String? = nil) {
        path = directory.appendingPathComponent("pending-take.encrypted")
        service = namespace ?? (AppPaths.testMode ? "com.tihor.quill.mac.tests.pending" : "com.tihor.quill.mac.pending")
    }
    private var query: [String:Any] { [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service, kSecAttrAccount as String:"encryption-key"] }
    private func key(create: Bool) throws -> SymmetricKey {
        var lookup = query; lookup[kSecReturnData as String] = true; lookup[kSecMatchLimit as String] = kSecMatchLimitOne
        var found: CFTypeRef?
        let status = SecItemCopyMatching(lookup as CFDictionary, &found)
        if status == errSecSuccess, let bytes = found as? Data, bytes.count == 32 { return SymmetricKey(data: bytes) }
        guard status == errSecItemNotFound && create else { throw QuillError.storage }
        var bytes = Data(count: 32)
        let random = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard random == errSecSuccess else { throw QuillError.storage }
        defer { bytes.resetBytes(in: 0..<bytes.count) }
        var addition = query; addition[kSecValueData as String] = bytes; addition[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(addition as CFDictionary, nil) == errSecSuccess else { throw QuillError.storage }
        return SymmetricKey(data: bytes)
    }
    func load() throws -> PendingTake? {
        guard FileManager.default.fileExists(atPath: path.path) else { return nil }
        let encrypted = try Data(contentsOf: path)
        var clear = try AES.GCM.open(AES.GCM.SealedBox(combined: encrypted), using: key(create: false))
        defer { clear.resetBytes(in: 0..<clear.count) }
        return try JSONDecoder().decode(PendingTake.self, from: clear)
    }
    func save(_ take: PendingTake) throws {
        var clear = try JSONEncoder().encode(take)
        defer { clear.resetBytes(in: 0..<clear.count) }
        let sealed = try AES.GCM.seal(clear, using: key(create: true))
        guard let bytes = sealed.combined else { throw QuillError.storage }
        try bytes.write(to: path, options: .atomic)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path.path)
    }
    func clear() throws {
        if FileManager.default.fileExists(atPath: path.path) { try FileManager.default.removeItem(at: path) }
        _ = SecItemDelete(query as CFDictionary)
    }
}
