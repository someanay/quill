import Foundation

// Actor-serialized settings persistence (the Swift equivalent of the Windows SemaphoreSlim gate).
// Atomic on disk via Data.write(.atomic) = write-temp-then-rename. A corrupt/partial file degrades to
// normalized defaults rather than throwing (matches SettingsStore.cs).
actor SettingsStore {
    private let paths: AppPaths
    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        e.dateEncodingStrategy = .iso8601
        return e
    }()
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    init(paths: AppPaths) { self.paths = paths }

    func load() -> AppSettings {
        guard let data = try? Data(contentsOf: paths.settings),
              let decoded = try? decoder.decode(AppSettings.self, from: data)
        else { return SettingsPolicy.normalize(AppSettings()) }
        return SettingsPolicy.normalize(decoded)
    }

    func save(_ settings: AppSettings) {
        guard let data = try? encoder.encode(SettingsPolicy.normalize(settings)) else { return }
        try? data.write(to: paths.settings, options: .atomic)
    }
}
