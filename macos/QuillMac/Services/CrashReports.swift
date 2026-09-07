import Foundation
import Combine

/// Captures a strict allowlist; arbitrary exception reasons, logs and application data are never read.
private final class ExceptionCapture: @unchecked Sendable {
    static let shared = ExceptionCapture()
    private let lock = NSLock()
    private var destination: URL?
    func configure(_ url: URL?) { lock.lock(); destination = url; lock.unlock() }
    func record(_ exception: NSException) {
        lock.lock(); let url = destination; lock.unlock()
        guard let url else { return }
        let payload: [String:Any] = ["event":"uncaught_exception", "stackAddresses":exception.callStackReturnAddresses.prefix(48).map { $0.uint64Value }, "os":ProcessInfo.processInfo.operatingSystemVersionString, "version":Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"]
        if let data = try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted,.sortedKeys]) { try? data.write(to: url, options: .atomic) }
    }
}

@MainActor final class CrashReports: ObservableObject {
    @Published var pendingReport: String?
    private let reportURL = AppPaths.root.appendingPathComponent("diagnostic.json")
    private let runningURL = AppPaths.root.appendingPathComponent("running-marker")
    private var subscription: AnyCancellable?
    init(settings: SettingsStore) {
        if settings.value.crashReportingEnabled && !AppPaths.testMode {
            if let data = try? Data(contentsOf: reportURL), let text = String(data: data, encoding: .utf8) { pendingReport = text }
            else if FileManager.default.fileExists(atPath: runningURL.path) { pendingReport = "Event: previous session ended unexpectedly. This may be a crash, forced quit, or shutdown.\n" + Diagnostics.report() }
        }
        subscription = settings.$value.map(\.crashReportingEnabled).removeDuplicates().sink { [weak self] enabled in self?.configure(enabled: enabled && !AppPaths.testMode) }
    }
    private func configure(enabled: Bool) {
        ExceptionCapture.shared.configure(enabled ? reportURL : nil)
        if enabled {
            NSSetUncaughtExceptionHandler { exception in ExceptionCapture.shared.record(exception) }
            try? Data("running".utf8).write(to: runningURL, options: .atomic)
        } else {
            NSSetUncaughtExceptionHandler(nil)
            try? FileManager.default.removeItem(at: runningURL); discard()
        }
    }
    func discard() { pendingReport = nil; try? FileManager.default.removeItem(at: reportURL) }
    func finish() { try? FileManager.default.removeItem(at: runningURL) }
}
