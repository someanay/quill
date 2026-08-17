import Foundation

// Mirrors windows/.../Core/AppPaths.cs — Application Support/Quill with settings, history, recordings.
struct AppPaths {
    let root: URL

    init(root: URL? = nil) {
        if let root {
            self.root = root
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            self.root = base.appendingPathComponent("Quill", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: recordings, withIntermediateDirectories: true)
    }

    var settings: URL { root.appendingPathComponent("settings.json") }
    var history: URL { root.appendingPathComponent("history.json") }
    var recordings: URL { root.appendingPathComponent("recordings", isDirectory: true) }
}
