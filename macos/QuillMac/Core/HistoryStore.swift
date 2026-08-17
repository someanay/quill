import Foundation

// Actor-serialized local dictation history with time-based retention + a 1000-entry cap. Faithful port
// of windows/.../Core/HistoryStore.cs (private mode / history-off gate on write, prune on read/add).
actor HistoryStore {
    private static let maximumEntries = 1000
    private let paths: AppPaths
    private let encoder: JSONEncoder = {
        let e = JSONEncoder(); e.outputFormatting = [.prettyPrinted]; e.dateEncodingStrategy = .iso8601; return e
    }()
    private let decoder: JSONDecoder = {
        let d = JSONDecoder(); d.dateDecodingStrategy = .iso8601; return d
    }()

    init(paths: AppPaths) { self.paths = paths }

    func read(retentionMinutes: Int) -> [HistoryEntry] {
        let entries = readUnsafe()
        let pruned = HistoryStore.prune(entries, minutes: retentionMinutes, now: Date())
        if pruned.count != entries.count { writeUnsafe(pruned) }
        return pruned
    }

    func add(_ entry: HistoryEntry, settings: AppSettings) {
        guard settings.historyEnabled, !settings.privateMode else { return }
        var entries = readUnsafe()
        entries.insert(entry, at: 0)
        writeUnsafe(HistoryStore.prune(entries, minutes: settings.historyRetentionMinutes, now: Date()))
    }

    func clear() { try? FileManager.default.removeItem(at: paths.history) }

    static func prune(_ entries: [HistoryEntry], minutes: Int, now: Date) -> [HistoryEntry] {
        let cutoff = minutes <= 0 ? Date.distantPast : now.addingTimeInterval(TimeInterval(-minutes * 60))
        return entries.filter { $0.createdAt >= cutoff }
            .sorted { $0.createdAt > $1.createdAt }
            .prefix(maximumEntries)
            .map { $0 }
    }

    private func readUnsafe() -> [HistoryEntry] {
        guard let data = try? Data(contentsOf: paths.history),
              let entries = try? decoder.decode([HistoryEntry].self, from: data) else { return [] }
        return entries
    }

    private func writeUnsafe(_ entries: [HistoryEntry]) {
        guard let data = try? encoder.encode(entries) else { return }
        try? data.write(to: paths.history, options: .atomic)
    }
}
