import Foundation
import Security
import SQLite3
import QuillCore

enum AppFiles {
    static let groupID = "group.com.tihor.quill.ios"
    static let isUITesting = ProcessInfo.processInfo.arguments.contains("--ui-testing")
    static let isTestProcess = isUITesting || ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    /// A fresh UUID gives a UI integration run isolated storage that survives process recreation.
    /// Release builds cannot select a QA session; no settings or keys are seeded by this option.
    static let uiSessionID: String? = {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard isUITesting, let index = arguments.firstIndex(of: "--ui-session-id"),
              arguments.indices.contains(index + 1), let id = UUID(uuidString: arguments[index + 1]) else { return nil }
        return id.uuidString
        #else
        return nil
        #endif
    }()
    static var resetsUISettingsOnLaunch: Bool { isUITesting && uiSessionID == nil }
    static var keychainTestSuffix: String { isTestProcess ? ".tests" + (uiSessionID.map { ".session." + $0 } ?? "") : "" }
    static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let directory = base.appendingPathComponent(isTestProcess ? "QuillTests" : "Quill", isDirectory: true)
        if let uiSessionID { return directory.appendingPathComponent("Sessions", isDirectory: true).appendingPathComponent(uiSessionID, isDirectory: true) }
        return directory
    }
    static var audio: URL { root.appendingPathComponent("Audio", isDirectory: true) }
    static func prepare() throws {
        for var directory in [root, audio] {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try directory.setResourceValues(values)
        }
    }
    static func write<T: Encodable>(_ object: T, name: String) throws {
        try prepare()
        let data = try JSONEncoder().encode(object)
        try data.write(to: root.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
    }
    static func read<T: Decodable>(_ type: T.Type, name: String) -> T? {
        guard let data = try? Data(contentsOf: root.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}

struct PendingRecording: Codable {
    enum Disposition: String, Codable { case pending, completed, discarded }
    let filename: String
    let settings: QuillSettings
    let words: [CustomWord]
    let voiceSample: Bool
    var disposition: Disposition = .pending
    var url: URL? {
        guard filename.hasSuffix(".wav"), UUID(uuidString: String(filename.dropLast(4))) != nil else { return nil }
        return AppFiles.audio.appendingPathComponent(filename)
    }
}

extension PendingRecording {
    private enum CodingKeys: String, CodingKey { case filename, settings, words, voiceSample, disposition }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        filename = try values.decode(String.self, forKey: .filename)
        settings = try values.decode(QuillSettings.self, forKey: .settings)
        words = try values.decode([CustomWord].self, forKey: .words)
        voiceSample = try values.decode(Bool.self, forKey: .voiceSample)
        disposition = try values.decodeIfPresent(Disposition.self, forKey: .disposition) ?? .pending
    }
}

@MainActor protocol RecordingRecoveryStore: AnyObject {
    func load() -> PendingRecording?
    func save(_ recording: PendingRecording) throws
    func removeAudio(at url: URL) throws
    func removeManifest() throws
    func audioFiles() throws -> [URL]
}

@MainActor final class FileRecordingRecoveryStore: RecordingRecoveryStore {
    func load() -> PendingRecording? { AppFiles.read(PendingRecording.self, name: "pending-recording.json") }
    func save(_ recording: PendingRecording) throws { try AppFiles.write(recording, name: "pending-recording.json") }
    func removeAudio(at url: URL) throws { try removeIfPresent(url) }
    func removeManifest() throws { try removeIfPresent(AppFiles.root.appendingPathComponent("pending-recording.json")) }
    func audioFiles() throws -> [URL] {
        try FileManager.default.contentsOfDirectory(at: AppFiles.audio, includingPropertiesForKeys: nil).filter {
            $0.pathExtension == "wav" && UUID(uuidString: $0.deletingPathExtension().lastPathComponent) != nil
        }
    }
    private func removeIfPresent(_ url: URL) throws {
        do { try FileManager.default.removeItem(at: url) }
        catch let error as CocoaError where error.code == .fileNoSuchFile { }
    }
}

struct SavedKey: Identifiable, Codable, Equatable {
    let id: UUID
    var label: String
    let provider: Provider
    let endpoint: String
    let suffix: String
    let createdAt: Date
}

struct KeychainFailure: Error, LocalizedError {
    let status: OSStatus
    var errorDescription: String? { "Keychain operation failed (OSStatus \(status))." }
}

/// API-key bytes exist only in the host app's private Keychain access group. No Keychain sharing
/// entitlement is granted to the keyboard. Records are local to this device and never synchronize.
@MainActor final class KeyVault {
    private let service = "com.tihor.quill.ios.provider-keys" + AppFiles.keychainTestSuffix
    private(set) var keys: [SavedKey] = AppFiles.read([SavedKey].self, name: "key-index.json") ?? []
    private var selected: [String: UUID] = AppFiles.read([String: UUID].self, name: "active-keys.json") ?? [:]
    func records(_ provider: Provider) -> [SavedKey] { keys.filter { $0.provider == provider } }
    func active(_ provider: Provider, endpoint: String) -> SavedKey? {
        let matching = keys.filter { $0.provider == provider && $0.endpoint == endpoint }
        return matching.first { $0.id == selected[provider.rawValue] } ?? matching.first
    }
    func read(_ record: SavedKey) throws -> String {
        var value: CFTypeRef?
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
            kSecAttrAccount as String: record.id.uuidString, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        let status = SecItemCopyMatching(query as CFDictionary, &value)
        guard status == errSecSuccess, let data = value as? Data, let text = String(data: data, encoding: .utf8) else { throw QuillError.noKey }
        return text
    }
    func secret(_ provider: Provider, endpoint: String) throws -> String {
        guard let record = active(provider, endpoint: endpoint) else { throw QuillError.noKey }
        return try read(record)
    }
    func save(_ secret: String, provider: Provider, endpoint: String, label: String) throws {
        let id = UUID()
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString, kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrSynchronizable as String: false, kSecValueData as String: Data(secret.utf8)]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainFailure(status: status) }
        let trimmedLabel = label.trimmingCharacters(in: .whitespacesAndNewlines)
        let record = SavedKey(id: id, label: trimmedLabel.isEmpty ? provider.title : trimmedLabel, provider: provider, endpoint: endpoint, suffix: String(secret.suffix(4)), createdAt: Date())
        keys.append(record); selected[provider.rawValue] = id
        do { try persist() }
        catch { try? delete(record); throw error }
    }
    func activate(_ key: SavedKey) throws { selected[key.provider.rawValue] = key.id; try persist() }
    func delete(_ key: SavedKey) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key.id.uuidString]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw QuillError.storage }
        keys.removeAll { $0.id == key.id }
        if selected[key.provider.rawValue] == key.id { selected.removeValue(forKey: key.provider.rawValue) }
        try persist()
    }
    func clear() throws {
        let status = SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw QuillError.storage }
        keys = []; selected = [:]; try persist()
    }
    private func persist() throws { try AppFiles.write(keys, name: "key-index.json"); try AppFiles.write(selected, name: "active-keys.json") }
}

/// SQLite is confined to the main actor. All text is bound, never interpolated into SQL. Secure
/// deletion and DELETE journaling avoid a persistent WAL copy of expired transcripts.
@MainActor protocol HistoryRepository: AnyObject {
    func save(_ entry: HistoryEntry) throws
    func entries(retentionMinutes: Int) throws -> [HistoryEntry]
    func delete(_ id: UUID) throws
    func clear() throws
}

@MainActor final class HistoryStore: HistoryRepository {
    private var database: OpaquePointer?
    init() throws {
        try AppFiles.prepare()
        let path = AppFiles.root.appendingPathComponent("history.sqlite").path
        guard sqlite3_open_v2(path, &database, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else { throw QuillError.storage }
        try execute("PRAGMA secure_delete=ON")
        try execute("PRAGMA journal_mode=DELETE")
        try execute("CREATE TABLE IF NOT EXISTS history(id TEXT PRIMARY KEY, text TEXT NOT NULL, provider TEXT NOT NULL, created REAL NOT NULL, destination TEXT NOT NULL, status TEXT NOT NULL)")
        try execute("CREATE INDEX IF NOT EXISTS history_time ON history(created)")
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: path)
    }
    deinit { sqlite3_close(database) }
    private func execute(_ sql: String) throws { guard sqlite3_exec(database, sql, nil, nil, nil) == SQLITE_OK else { throw QuillError.storage } }
    private func statement(_ sql: String) throws -> OpaquePointer {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK, let statement else { throw QuillError.storage }
        return statement
    }
    private func bind(_ value: String, _ index: Int32, to stmt: OpaquePointer) {
        _ = value.withCString { sqlite3_bind_text(stmt, index, $0, -1, unsafeBitCast(-1, to: sqlite3_destructor_type.self)) }
    }
    func save(_ entry: HistoryEntry) throws {
        let s = try statement("INSERT INTO history VALUES(?,?,?,?,?,?)"); defer { sqlite3_finalize(s) }
        bind(entry.id.uuidString, 1, to: s); bind(entry.text, 2, to: s); bind(entry.provider.rawValue, 3, to: s)
        sqlite3_bind_double(s, 4, entry.date.timeIntervalSince1970); bind(entry.destination, 5, to: s); bind(entry.status, 6, to: s)
        guard sqlite3_step(s) == SQLITE_DONE else { throw QuillError.storage }
    }
    func entries(retentionMinutes: Int) throws -> [HistoryEntry] {
        if retentionMinutes > 0 {
            let p = try statement("DELETE FROM history WHERE created <= ?"); defer { sqlite3_finalize(p) }
            sqlite3_bind_double(p, 1, Date().addingTimeInterval(-Double(retentionMinutes) * 60).timeIntervalSince1970)
            guard sqlite3_step(p) == SQLITE_DONE else { throw QuillError.storage }
        }
        try execute("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY created DESC LIMIT 1000)")
        let s = try statement("SELECT id,text,provider,created,destination,status FROM history ORDER BY created DESC")
        defer { sqlite3_finalize(s) }
        func string(_ index: Int32) -> String { guard let ptr = sqlite3_column_text(s, index) else { return "" }; return String(cString: ptr) }
        var entries = [HistoryEntry]()
        var step = sqlite3_step(s)
        while step == SQLITE_ROW {
            if let id = UUID(uuidString: string(0)) {
                entries.append(HistoryEntry(text: string(1), provider: Provider(rawValue: string(2)) ?? .groq, date: Date(timeIntervalSince1970: sqlite3_column_double(s, 3)), destination: string(4), status: string(5), id: id))
            }
            step = sqlite3_step(s)
        }
        guard step == SQLITE_DONE else { throw QuillError.storage }
        return entries
    }
    func delete(_ id: UUID) throws {
        let s = try statement("DELETE FROM history WHERE id=?"); defer { sqlite3_finalize(s) }
        bind(id.uuidString, 1, to: s); guard sqlite3_step(s) == SQLITE_DONE else { throw QuillError.storage }
    }
    func clear() throws { try execute("DELETE FROM history"); try execute("VACUUM") }
}
