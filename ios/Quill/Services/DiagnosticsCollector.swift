import Foundation
import MetricKit

struct DiagnosticSummary: Codable, Sendable {
    let receivedAt: Date
    let crashes: Int
    let hangs: Int
    let cpuExceptions: Int
    let diskWriteExceptions: Int
}

/// MetricKit delivery is opt-in. Never persist Apple's raw payload: it can contain sensitive stack
/// data and binary metadata. Only fixed numeric category counts survive this boundary.
@MainActor final class DiagnosticsCollector: NSObject, MXMetricManagerSubscriber {
    private(set) var enabled = false
    private(set) var latest = AppFiles.read(DiagnosticSummary.self, name: "diagnostic-summary.json")
    var onUpdate: (() -> Void)?
    func setEnabled(_ value: Bool) {
        guard value != enabled else { return }
        enabled = value
        if value { MXMetricManager.shared.add(self) }
        else { MXMetricManager.shared.remove(self); clear() }
    }
    func clear() {
        latest = nil
        try? FileManager.default.removeItem(at: AppFiles.root.appendingPathComponent("diagnostic-summary.json"))
        onUpdate?()
    }
    nonisolated func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let summary = DiagnosticSummary(receivedAt: Date(), crashes: payloads.reduce(0) { $0 + ($1.crashDiagnostics?.count ?? 0) }, hangs: payloads.reduce(0) { $0 + ($1.hangDiagnostics?.count ?? 0) }, cpuExceptions: payloads.reduce(0) { $0 + ($1.cpuExceptionDiagnostics?.count ?? 0) }, diskWriteExceptions: payloads.reduce(0) { $0 + ($1.diskWriteExceptionDiagnostics?.count ?? 0) })
        Task { @MainActor [weak self] in
            guard let self, self.enabled else { return }
            do { try AppFiles.write(summary, name: "diagnostic-summary.json"); self.latest = summary; self.onUpdate?() }
            catch { /* Reporting must never interrupt a dictation or disclose raw diagnostics. */ }
        }
    }
    nonisolated func didReceive(_ payloads: [MXMetricPayload]) { }
}
