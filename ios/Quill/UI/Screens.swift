import SwiftUI
import UIKit

// Local dictation history (mirrors the Android/Windows history), pruned to the retention window on open.
struct HistoryView: View {
    @State private var entries: [HistoryEntry] = []
    private let store = HistoryStore(paths: AppPaths())
    private let settingsStore = SettingsStore(paths: AppPaths())

    var body: some View {
        NavigationStack {
            List {
                if entries.isEmpty {
                    Text("No dictations yet.").foregroundStyle(.secondary)
                } else {
                    ForEach(entries) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.text)
                            HStack(spacing: 8) {
                                Text(entry.application).font(.caption).foregroundStyle(.secondary)
                                Text(entry.createdAt, style: .relative).font(.caption).foregroundStyle(.secondary)
                                if entry.wasCleaned { Text("cleaned").font(.caption2).foregroundStyle(.tertiary) }
                            }
                        }
                        .swipeActions {
                            Button("Copy") { UIPasteboard.general.string = entry.text }.tint(.blue)
                        }
                    }
                }
            }
            .navigationTitle("History")
            .toolbar {
                if !entries.isEmpty {
                    Button("Clear", role: .destructive) { Task { await store.clear(); await reload() } }
                }
            }
            .task { await reload() }
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        let retention = await settingsStore.load().historyRetentionMinutes
        entries = await store.read(retentionMinutes: retention)
    }
}

// First-run: provider + key (a key is required), then the privacy/keyboard notes.
struct OnboardingView: View {
    let onDone: () -> Void
    @State private var settings = AppSettings()
    @State private var apiKey = ""
    @State private var status = ""
    private let settingsStore = SettingsStore(paths: AppPaths())
    private let secrets = SecretStore()
    private let validator = KeyValidator(session: .shared)

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Dictate with your own speech-to-text key. Your key and audio stay on your device — Quill has no account and no backend for your data. Audio is deleted after each dictation.")
                }
                Section("Provider & key") {
                    Picker("Provider", selection: $settings.provider) {
                        ForEach(SttProvider.allCases.filter { $0 != .custom }, id: \.self) { Text($0.displayName).tag($0) }
                    }
                    .onChange(of: settings.provider) { _ in
                        settings.model = ProviderCatalog.all[settings.provider]!.defaultModel
                        apiKey = secrets.get(settings.provider) ?? ""
                    }
                    SecureField("API key", text: $apiKey)
                    if let url = ProviderCatalog.all[settings.provider]!.keyConsoleURL, let link = URL(string: url) {
                        Link("Get a key from the provider console", destination: link).font(.caption)
                    }
                    Button("Save & verify", action: save)
                    if !status.isEmpty { Text(status).font(.caption).foregroundStyle(.secondary) }
                }
                Section {
                    Text("A key is required — without one, Quill cannot transcribe.").font(.caption).foregroundStyle(.orange)
                    Text("To dictate into other apps, add the Quill keyboard later (Settings › General › Keyboard) and allow Full Access.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Welcome to Quill")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done", action: finish) } }
            .task { settings = await settingsStore.load() }
        }
    }

    private func save() {
        secrets.set(settings.provider, apiKey)
        status = "Verifying…"
        let p = settings.provider, k = apiKey, m = settings.model
        Task {
            switch await validator.validate(provider: p, key: k, model: m, customBaseURL: "") {
            case .valid: status = "Key verified ✓"
            case .rejected: status = "Key rejected ✗"
            case .unverified(let r): status = "Saved (couldn't verify: \(r))"
            }
        }
    }

    private func finish() {
        settings.onboardingComplete = true
        let snapshot = settings
        Task { await settingsStore.save(snapshot); onDone() }
    }
}
