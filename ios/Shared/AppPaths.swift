import Foundation

// iOS variant: store settings/history/recordings in the shared App Group container so the main app
// and the keyboard extension read/write the same data. Falls back to Application Support if the group
// is unavailable (e.g. previews).
struct AppPaths {
    static let appGroup = "group.com.tihor.quill"
    let root: URL

    init(root: URL? = nil) {
        if let root {
            self.root = root
        } else if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: AppPaths.appGroup) {
            self.root = container.appendingPathComponent("Quill", isDirectory: true)
        } else {
            self.root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
                .appendingPathComponent("Quill", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: recordings, withIntermediateDirectories: true)
    }

    var settings: URL { root.appendingPathComponent("settings.json") }
    var history: URL { root.appendingPathComponent("history.json") }
    var recordings: URL { root.appendingPathComponent("recordings", isDirectory: true) }
}
