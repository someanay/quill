import SwiftUI

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var settings = AppSettings()
    @Published var apiKey = ""
    @Published var keyStatus = ""
    @Published var customWords: [WordReplacement] = []
    @Published var newHeard = ""
    @Published var newReplacement = ""

    private let settingsStore = SettingsStore(paths: AppPaths())
    private let secrets = SecretStore()
    private let validator = KeyValidator(session: .shared)
    private var loaded = false

    init() { Task { await load() } }

    func load() async {
        settings = await settingsStore.load()
        customWords = settings.customWords
        apiKey = secrets.get(settings.provider) ?? ""
        loaded = true
    }

    func persist() {
        guard loaded else { return }
        var s = settings; s.customWords = customWords
        Task { await settingsStore.save(s) }
    }

    func onProviderChanged() {
        settings.model = ProviderCatalog.all[settings.provider]!.defaultModel
        settings.cleanupModel = CleanupModelCatalog.defaultFor(settings.provider)
        apiKey = secrets.get(settings.provider) ?? ""
        keyStatus = ""; persist()
    }

    func saveKey() {
        secrets.set(settings.provider, apiKey)
        keyStatus = "Saved — verifying…"
        let p = settings.provider, k = apiKey, m = settings.model, u = settings.customBaseURL
        Task {
            switch await validator.validate(provider: p, key: k, model: m, customBaseURL: u) {
            case .valid: keyStatus = "Key verified ✓"
            case .rejected: keyStatus = "Key rejected ✗ — check you copied it fully"
            case .unverified(let r): keyStatus = "Saved (couldn't verify: \(r))"
            }
        }
    }

    func addWord() {
        let h = newHeard.trimmed, r = newReplacement.trimmed
        guard !h.isEmpty, !r.isEmpty else { return }
        customWords.removeAll { $0.heard.caseInsensitiveCompare(h) == .orderedSame }
        customWords.append(WordReplacement(heard: h, replacement: r))
        newHeard = ""; newReplacement = ""; persist()
    }

    func removeWord(_ word: WordReplacement) { customWords.removeAll { $0 == word }; persist() }
}

struct SettingsView: View {
    @StateObject private var vm = SettingsViewModel()

    var body: some View {
        NavigationStack {
            Form {
                Section("Provider & key") {
                    Picker("Provider", selection: $vm.settings.provider) {
                        ForEach(SttProvider.allCases, id: \.self) { Text($0.displayName).tag($0) }
                    }
                    .onChange(of: vm.settings.provider) { _ in vm.onProviderChanged() }

                    if vm.settings.provider == .custom {
                        TextField("HTTPS base URL", text: $vm.settings.customBaseURL)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                        TextField("Model id", text: $vm.settings.model).autocorrectionDisabled()
                    } else {
                        Picker("Model", selection: $vm.settings.model) {
                            ForEach(ProviderCatalog.all[vm.settings.provider]!.models, id: \.self) { Text($0).tag($0) }
                        }
                    }
                    SecureField("API key", text: $vm.apiKey)
                    Button("Save key", action: vm.saveKey)
                    if !vm.keyStatus.isEmpty { Text(vm.keyStatus).font(.caption).foregroundStyle(.secondary) }
                    if let url = ProviderCatalog.all[vm.settings.provider]!.keyConsoleURL, let link = URL(string: url) {
                        Link("Open the provider key console", destination: link).font(.caption)
                    }
                }

                Section("Language & cleanup") {
                    TextField("Language (ISO code, or 'auto')", text: $vm.settings.language)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    Toggle("Clean up transcripts", isOn: $vm.settings.cleanupEnabled)
                        .disabled(!CleanupModelCatalog.supports(vm.settings.provider))
                    if vm.settings.cleanupEnabled, CleanupModelCatalog.supports(vm.settings.provider) {
                        Picker("Cleanup model", selection: $vm.settings.cleanupModel) {
                            ForEach(CleanupModelCatalog.forProvider(vm.settings.provider), id: \.self) { Text($0).tag($0) }
                        }
                        Picker("Writing style", selection: $vm.settings.defaultWritingStyle) {
                            ForEach(WritingStyle.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
                        }
                    }
                }

                Section("Recording") {
                    Picker("Silence auto-stop", selection: $vm.settings.silenceAutoStopSeconds) {
                        ForEach(SettingsPolicy.silenceChoices, id: \.seconds) { Text($0.label).tag($0.seconds) }
                    }
                }

                Section("History & privacy") {
                    Toggle("Save dictation history", isOn: $vm.settings.historyEnabled)
                    Picker("Keep history for", selection: $vm.settings.historyRetentionMinutes) {
                        ForEach(SettingsPolicy.retentionChoices, id: \.minutes) { Text($0.label).tag($0.minutes) }
                    }
                    Toggle("Private mode (never save transcripts)", isOn: $vm.settings.privateMode)
                }

                Section("Quill keyboard") {
                    Text("To dictate into any app, add the Quill keyboard in Settings › General › Keyboard › Keyboards, then enable Allow Full Access (required for the microphone and network).")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section("Custom words") {
                    HStack {
                        TextField("Heard as", text: $vm.newHeard).autocorrectionDisabled()
                        TextField("Replace with", text: $vm.newReplacement)
                        Button("Add", action: vm.addWord)
                    }
                    ForEach(vm.customWords) { word in
                        HStack {
                            Text(word.heard); Image(systemName: "arrow.right").font(.caption2); Text(word.replacement).bold()
                            Spacer()
                            Button(role: .destructive) { vm.removeWord(word) } label: { Image(systemName: "trash") }
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .onChange(of: vm.settings) { _ in vm.persist() }
        }
    }
}
