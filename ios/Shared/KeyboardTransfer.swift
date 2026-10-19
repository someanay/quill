import Foundation
import QuillCore

/// The shared boundary holds an explicitly published, single-use transcript and a setup receipt
/// (time + opaque insertion ID). No keys, history, recordings, or destination text are shared.
enum KeyboardTransfer {
    static let groupID = "group.com.tihor.quill.ios"
    private static var store: CoordinatedHandoffStore? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID).map {
            CoordinatedHandoffStore(url: $0.appendingPathComponent("pending-dictation.json"))
        }
    }
    @discardableResult static func publish(_ text: String) throws -> UUID { guard let store else { throw QuillError.storage }; return try store.publish(text) }
    static func peek() -> KeyboardHandoff? { store?.peek() }
    static func consume(_ id: UUID) throws -> String { guard let store else { throw QuillError.storage }; return try store.consume(id) }
    static func clear() throws { try store?.clear() }
    static func prune() { store?.prune() }
    private static var setupURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)?.appendingPathComponent("keyboard-setup.json")
    }
    static func setupReceipt() -> KeyboardSetupReceipt? {
        guard let url = setupURL, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(KeyboardSetupReceipt.self, from: data)
    }
    static func markConnected() { writeReceipt(KeyboardSetupReceipt(insertedTransferID: setupReceipt()?.insertedTransferID)) }
    static func markInserted(_ id: UUID) { writeReceipt(KeyboardSetupReceipt(insertedTransferID: id)) }
    private static func writeReceipt(_ receipt: KeyboardSetupReceipt) {
        guard let url = setupURL, let data = try? JSONEncoder().encode(receipt) else { return }
        try? data.write(to: url, options: [.atomic, .completeFileProtection])
        var directory = url.deletingLastPathComponent()
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? directory.setResourceValues(values)
    }
}

/// The actual file adapter accepts a location so native tests can verify coordination, protection
/// and stale-transfer handling in an isolated folder without touching a user's pending transfer.
struct CoordinatedHandoffStore {
    let url: URL
    private let writeFile: (Data, URL, Data.WritingOptions) throws -> Void
    init(url: URL, writeFile: @escaping (Data, URL, Data.WritingOptions) throws -> Void = { data, target, options in try data.write(to: target, options: options) }) {
        self.url = url; self.writeFile = writeFile
    }
    @discardableResult func publish(_ text: String) throws -> UUID {
        let handoff = KeyboardHandoff(text: text)
        guard handoff.isValid() else { throw QuillError.noSpeech }
        var directory = url.deletingLastPathComponent()
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        let data = try JSONEncoder().encode(handoff)
        var coordinationError: NSError?
        var writeError: Error?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forReplacing, error: &coordinationError) { target in
            do { try writeFile(data, target, [.atomic, .completeFileProtection]) }
            catch { writeError = error }
        }
        if let coordinationError { throw coordinationError }
        if let writeError { throw writeError }
        return handoff.id
    }
    func peek() -> KeyboardHandoff? {
        guard let data = try? Data(contentsOf: url), let item = try? JSONDecoder().decode(KeyboardHandoff.self, from: data), item.isValid() else { return nil }
        return item
    }
    func consume(_ id: UUID) throws -> String {
        var coordinationError: NSError?
        var consumeError: Error?
        var text: String?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forDeleting, error: &coordinationError) { target in
            do {
                let item = try JSONDecoder().decode(KeyboardHandoff.self, from: Data(contentsOf: target))
                guard item.id == id, item.isValid() else { throw QuillError.expired }
                // The coordinated read+delete is one transaction across app and keyboard. A new
                // publish cannot be deleted by an older keyboard tap racing with the replacement.
                try FileManager.default.removeItem(at: target)
                text = item.text
            } catch { consumeError = error }
        }
        if let coordinationError { throw coordinationError }
        if let consumeError { throw consumeError }
        guard let text else { throw QuillError.expired }
        return text
    }
    func clear() throws {
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        var coordinationError: NSError?
        var deletionError: Error?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forDeleting, error: &coordinationError) { target in
            do { if FileManager.default.fileExists(atPath: target.path) { try FileManager.default.removeItem(at: target) } }
            catch { deletionError = error }
        }
        if let coordinationError { throw coordinationError }
        if let deletionError { throw deletionError }
    }
    func prune() {
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        var coordinationError: NSError?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: url, options: .forDeleting, error: &coordinationError) { target in
            guard let data = try? Data(contentsOf: target) else { return }
            if let item = try? JSONDecoder().decode(KeyboardHandoff.self, from: data), item.isValid() { return }
            try? FileManager.default.removeItem(at: target)
        }
    }
}
