import Foundation
import QuillCore

/// This file is the sole shared data boundary. The container holds only an explicitly published,
/// single-use transcript. No keys, history, recordings, or destination field text are shared.
enum KeyboardTransfer {
    static let groupID = "group.com.tihor.quill.ios"
    private static var url: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)?.appendingPathComponent("pending-dictation.json") }
    static func publish(_ text: String) throws {
        guard let url else { throw QuillError.storage }
        let handoff = KeyboardHandoff(text: text)
        guard handoff.isValid() else { throw QuillError.noSpeech }
        var directory = url.deletingLastPathComponent()
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        try JSONEncoder().encode(handoff).write(to: url, options: [.atomic, .completeFileProtection])
    }
    static func peek() -> KeyboardHandoff? {
        guard let url, let data = try? Data(contentsOf: url), let item = try? JSONDecoder().decode(KeyboardHandoff.self, from: data), item.isValid() else { return nil }
        return item
    }
    static func consume(_ id: UUID) throws -> String {
        guard let url, let item = peek(), item.id == id else { throw QuillError.expired }
        // Fail closed: if Full Access is off or deletion fails, insertion does not happen. This
        // prevents repeated insertion after a keyboard process restart and leaves retry visible.
        try FileManager.default.removeItem(at: url)
        return item.text
    }
    static func clear() throws {
        guard let url, FileManager.default.fileExists(atPath: url.path) else { return }
        try FileManager.default.removeItem(at: url)
    }
    static func prune() { if peek() == nil { try? clear() } }
}
