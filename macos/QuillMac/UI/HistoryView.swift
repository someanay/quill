import SwiftUI

// Local dictation history (mirrors the Windows history panel): per-item copy/delete + clear all,
// pruned to the configured retention window on open.
struct HistoryView: View {
    let services: AppServices
    @Environment(\.dismiss) private var dismiss
    @State private var entries: [HistoryEntry] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("History").font(.title2.bold())
                Spacer()
                Button("Clear all", role: .destructive) { Task { await services.history.clear(); await reload() } }
                    .disabled(entries.isEmpty)
                Button("Done") { dismiss() }
            }
            .padding()
            Divider()
            if entries.isEmpty {
                Spacer()
                Text("No dictations yet.").foregroundStyle(.secondary).frame(maxWidth: .infinity)
                Spacer()
            } else {
                List(entries) { entry in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(entry.text)
                        HStack(spacing: 8) {
                            Text(entry.application).font(.caption).foregroundStyle(.secondary)
                            Text(entry.createdAt, style: .relative).font(.caption).foregroundStyle(.secondary)
                            if entry.wasCleaned { Text("cleaned").font(.caption2).foregroundStyle(.tertiary) }
                            Spacer()
                            Button { copy(entry.text) } label: { Image(systemName: "doc.on.doc") }.buttonStyle(.borderless)
                        }
                    }.padding(.vertical, 2)
                }
            }
        }
        .frame(width: 520, height: 560)
        .task { await reload() }
    }

    private func reload() async {
        let retention = await services.settingsStore.load().historyRetentionMinutes
        entries = await services.history.read(retentionMinutes: retention)
    }

    private func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
    }
}
