import Foundation
import Combine
import Security
import SQLite3
import QuillCore

enum AppPaths {
    static var testMode: Bool { ProcessInfo.processInfo.arguments.contains("--ui-testing") }
    static let root: URL = {
        let parent = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let url = parent.appendingPathComponent(testMode ? "QuillUITests" : "Quill", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        var mutable = url; var values = URLResourceValues(); values.isExcludedFromBackup = true; try? mutable.setResourceValues(values)
        return url
    }()
}

@MainActor final class SettingsStore: ObservableObject {
    @Published var value: QuillSettings { didSet { persist() } }
    @Published var storageFailed = false
    private let path = AppPaths.root.appendingPathComponent("settings.json")
    init() {
        if AppPaths.testMode { value = QuillSettings() }
        else { value = (try? JSONDecoder().decode(QuillSettings.self, from: Data(contentsOf: path))) ?? QuillSettings(); value.normalize() }
    }
    private func persist() {
        do { var safe = value; safe.normalize(); try JSONEncoder().encode(safe).write(to: path, options: .atomic); try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path.path); storageFailed = false }
        catch { storageFailed = true }
    }
    func reset() { let onboarded = value.onboardingComplete, grantedOnce = value.accessibilityEnabledOnce; var fresh = QuillSettings(); fresh.onboardingComplete = onboarded; fresh.accessibilityEnabledOnce = grantedOnce; fresh.activeKeys = value.activeKeys; fresh.words = value.words; fresh.contactNames = value.contactNames; fresh.styleOverrides = value.styleOverrides; value = fresh }
}

@MainActor final class KeychainStore: ObservableObject {
    @Published private(set) var slots: [KeySlot] = []
    private let service = AppPaths.testMode ? "com.tihor.quill.mac.tests.keys" : "com.tihor.quill.mac.keys"
    private let path = AppPaths.root.appendingPathComponent("key-slots.json")
    init() { slots = (try? JSONDecoder().decode([KeySlot].self, from: Data(contentsOf: path))) ?? [] }
    private func query(_ id: UUID) -> [String:Any] { [kSecClass as String:kSecClassGenericPassword, kSecAttrService as String:service, kSecAttrAccount as String:id.uuidString] }
    func read(_ slot: KeySlot, customURL: String) -> String? {
        if slot.provider == .custom, slot.customOrigin != (try? Endpoint.validated(customURL).absoluteString) { return nil }
        var query = query(slot.id); query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    func active(_ provider: Provider, settings: QuillSettings) -> String? {
        guard let id = settings.activeKeys[provider.rawValue], let slot = slots.first(where: { $0.id == id && $0.provider == provider }) else { return nil }
        return read(slot, customURL: settings.customBaseURL)
    }
    func save(provider: Provider, label: String, secret: String, status: String, customURL: String) throws -> KeySlot {
        let clean = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, !clean.contains("\r"), !clean.contains("\n") else { throw QuillError.badKey }
        let slot = KeySlot(provider: provider, label: label.trimmingCharacters(in: .whitespacesAndNewlines), status: status, customOrigin: provider == .custom ? try Endpoint.validated(customURL).absoluteString : nil)
        var query = query(slot.id); query[kSecValueData as String] = Data(clean.utf8); query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else { throw QuillError.storage }
        do { slots.append(slot); try persist(); return slot }
        catch { slots.removeAll { $0.id == slot.id }; SecItemDelete(self.query(slot.id) as CFDictionary); throw error }
    }
    func updateStatus(_ id: UUID, status: String) throws { if let index = slots.firstIndex(where: { $0.id == id }) { slots[index].status = status; try persist() } }
    func delete(_ slot: KeySlot) throws {
        let code = SecItemDelete(query(slot.id) as CFDictionary)
        guard code == errSecSuccess || code == errSecItemNotFound else { throw QuillError.storage }
        slots.removeAll { $0.id == slot.id }; try persist()
    }
    func clear() throws { for slot in slots { try delete(slot) } }
    private func persist() throws { try JSONEncoder().encode(slots).write(to: path, options: .atomic); try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path.path) }
}

@MainActor final class HistoryStore: ObservableObject {
    @Published private(set) var entries: [HistoryEntry] = []
    @Published var storageFailed = false
    private var db: OpaquePointer?
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
    init() {
        let path = AppPaths.root.appendingPathComponent("history.sqlite").path
        guard sqlite3_open_v2(path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else { storageFailed = true; return }
        execute("PRAGMA journal_mode=DELETE"); execute("PRAGMA secure_delete=ON")
        execute("CREATE TABLE IF NOT EXISTS history(id TEXT PRIMARY KEY, text TEXT NOT NULL, created REAL NOT NULL, provider TEXT NOT NULL, app TEXT NOT NULL, duration REAL NOT NULL)")
        try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path)
    }
    deinit { sqlite3_close(db) }
    private func execute(_ sql: String) { if sqlite3_exec(db, sql, nil, nil, nil) != SQLITE_OK { storageFailed = true } }
    func refresh(settings: QuillSettings) {
        if settings.historyRetentionMinutes > 0 { execute("DELETE FROM history WHERE created <= \(Date().addingTimeInterval(-Double(settings.historyRetentionMinutes) * 60).timeIntervalSince1970)") }
        execute("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY created DESC LIMIT 1000)")
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT id,text,created,provider,app,duration FROM history ORDER BY created DESC", -1, &statement, nil) == SQLITE_OK else { storageFailed = true; return }
        defer { sqlite3_finalize(statement) }
        var rows: [HistoryEntry] = []
        while sqlite3_step(statement) == SQLITE_ROW {
            func string(_ index: Int32) -> String { sqlite3_column_text(statement, index).map { String(cString: $0) } ?? "" }
            guard let id = UUID(uuidString: string(0)) else { continue }
            rows.append(HistoryEntry(id: id, text: string(1), created: Date(timeIntervalSince1970: sqlite3_column_double(statement, 2)), provider: Provider(rawValue: string(3)) ?? .groq, appName: string(4), duration: sqlite3_column_double(statement, 5)))
        }
        entries = rows
    }
    func insert(_ entry: HistoryEntry, settings: QuillSettings) {
        guard settings.shouldSaveHistory else { return }
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "INSERT INTO history VALUES(?,?,?,?,?,?)", -1, &statement, nil) == SQLITE_OK else { storageFailed = true; return }
        defer { sqlite3_finalize(statement) }
        sqlite3_bind_text(statement, 1, entry.id.uuidString, -1, transient); sqlite3_bind_text(statement, 2, entry.text, -1, transient)
        sqlite3_bind_double(statement, 3, entry.created.timeIntervalSince1970); sqlite3_bind_text(statement, 4, entry.provider.rawValue, -1, transient)
        sqlite3_bind_text(statement, 5, entry.appName, -1, transient); sqlite3_bind_double(statement, 6, entry.duration)
        if sqlite3_step(statement) != SQLITE_DONE { storageFailed = true }; refresh(settings: settings)
    }
    func delete(_ id: UUID, settings: QuillSettings) { execute("DELETE FROM history WHERE id='\(id.uuidString)'"); refresh(settings: settings) }
    func clear() { execute("DELETE FROM history"); execute("VACUUM"); entries = [] }
}
