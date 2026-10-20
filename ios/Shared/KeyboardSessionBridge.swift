import Foundation
import QuillCore

/// Only opaque commands/state cross this boundary. Audio and provider credentials stay in Quill.
enum KeyboardSessionBridge {
    static var store: CoordinatedKeyboardSessionStore? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: KeyboardTransfer.groupID).map {
            CoordinatedKeyboardSessionStore(directory: $0.appendingPathComponent("keyboard-session", isDirectory: true))
        }
    }
    static func state() -> KeyboardRecordingSession? { store?.state() }
    static func send(_ command: KeyboardSessionCommand) throws {
        guard let store else { throw QuillError.storage }; try store.send(command)
    }
}

struct CoordinatedKeyboardSessionStore {
    let directory: URL
    private let writeFile: (Data, URL, Data.WritingOptions) throws -> Void
    init(directory: URL, writeFile: @escaping (Data, URL, Data.WritingOptions) throws -> Void = { data, url, options in try data.write(to: url, options: options) }) {
        self.directory = directory; self.writeFile = writeFile
    }
    private var stateURL: URL { directory.appendingPathComponent("state.json") }
    private var commandURL: URL { directory.appendingPathComponent("command.json") }
    private func read<T: Decodable>(_ type: T.Type, at url: URL) -> T? {
        guard let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size <= 4096,
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
    func state() -> KeyboardRecordingSession? { read(KeyboardRecordingSession.self, at: stateURL) }
    func publish(_ state: KeyboardRecordingSession) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete])
        var folder = directory; var values = URLResourceValues(); values.isExcludedFromBackup = true
        try folder.setResourceValues(values)
        try writeFile(JSONEncoder().encode(state), stateURL, [.atomic, .completeFileProtection])
    }
    func send(_ command: KeyboardSessionCommand) throws {
        try coordinated {
            guard let state = state(), command.isValid(for: state) else { throw QuillError.expired }
            // One acknowledged command at a time. A stale abandoned command may be replaced.
            if let existing = read(KeyboardSessionCommand.self, at: commandURL),
               existing.id != state.acknowledgedCommandID, existing.isValid(for: state) { throw QuillError.audio }
            try writeFile(JSONEncoder().encode(command), commandURL, [.atomic, .completeFileProtection])
        }
    }
    func takeCommand() throws -> KeyboardSessionCommand? {
        var command: KeyboardSessionCommand?
        try coordinated {
            command = read(KeyboardSessionCommand.self, at: commandURL)
            if FileManager.default.fileExists(atPath: commandURL.path) { try FileManager.default.removeItem(at: commandURL) }
        }
        return command
    }
    func clearCommand() { try? FileManager.default.removeItem(at: commandURL) }
    private func coordinated(_ action: () throws -> Void) throws {
        var coordinationError: NSError?, operationError: Error?
        NSFileCoordinator(filePresenter: nil).coordinate(writingItemAt: directory, options: .forMerging, error: &coordinationError) { _ in
            do { try action() } catch { operationError = error }
        }
        if let coordinationError { throw coordinationError }
        if let operationError { throw operationError }
    }
}
