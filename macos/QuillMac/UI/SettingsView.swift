import SwiftUI

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var settings = AppSettings()
    @Published var apiKey = ""
    @Published var keyStatus = ""
    @Published var customWords: [WordReplacement] = []
    @Published var newHeard = ""
    @Published var newReplacement = ""
    @Published var hotkeyText = "Alt+Q"

    private let services: AppServices
    let onHotkeyChanged: () -> Void
    private var loaded = false

    init(services: AppServices, onHotkeyChanged: @escaping () -> Void) {
        self.services = services
        self.onHotkeyChanged = onHotkeyChanged
        Task { await self.load() }
    }

    func load() async {
        settings = await services.settingsStore.load()
        customWords = settings.customWords
        hotkeyText = settings.hotkey
        apiKey = services.secrets.get(settings.provider) ?? ""
        loaded = true
    }

    func persist() {
        guard loaded else { return }
        var s = settings
        s.customWords = customWords
        Task { await self.services.settingsStore.save(s) }
    }

    func onProviderChanged() {
        settings.model = ProviderCatalog.all[settings.provider]!.defaultModel
        settings.cleanupModel = CleanupModelCatalog.defaultFor(settings.provider)
        apiKey = services.secrets.get(settings.provider) ?? ""
        keyStatus = ""
        persist()
    }

    func saveKey() {
        services.secrets.set(settings.provider, apiKey)
        keyStatus = "Saved — verifying…"
        let provider = settings.provider, key = apiKey, model = settings.model, url = settings.customBaseURL
        Task {
            let result = await self.services.keyValidator.validate(provider: provider, key: key, model: model, customBaseURL: url)
            switch result {
            case .valid: self.keyStatus = "Key verified ✓"
            case .rejected: self.keyStatus = "Key rejected ✗ — check you copied it fully"
            case .unverified(let reason): self.keyStatus = "Saved (couldn't verify: \(reason))"
            }
        }
    }

    func applyHotkey() {
        guard let normalized = GlobalHotkeyService.normalize(hotkeyText) else { keyStatus = "Invalid shortcut"; return }
        settings.hotkey = normalized
        hotkeyText = normalized
        persist()
        onHotkeyChanged()
    }

    func addWord() {
        let h = newHeard.trimmed, r = newReplacement.trimmed
        guard !h.isEmpty, !r.isEmpty else { return }
        customWords.removeAll { $0.heard.caseInsensitiveCompare(h) == .orderedSame }
        customWords.append(WordReplacement(heard: h, replacement: r))
        newHeard = ""; newReplacement = ""; persist()
    }

    func removeWord(_ word: WordReplacement) { customWords.removeAll { $0 == word }; persist() }

    func setStartAtLogin(_ on: Bool) { settings.startAtLogin = on; LoginItemService.setEnabled(on); persist() }
}

struct SettingsView: View {
    @StateObject private var vm: SettingsViewModel
    @State private var showHistory = false

    init(services: AppServices, onHotkeyChanged: @escaping () -> Void) {
        _vm = StateObject(wrappedValue: SettingsViewModel(services: services, onHotkeyChanged: onHotkeyChanged))
        self.services = services
    }
    private let services: AppServices

    var body: some View {
        Form {
            Section("Provider & key") {
                Picker("Provider", selection: $vm.settings.provider) {
                    ForEach(SttProvider.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }
                .onChange(of: vm.settings.provider) { _ in vm.onProviderChanged() }

                if vm.settings.provider == .custom {
                    TextField("HTTPS base URL", text: $vm.settings.customBaseURL)
                    TextField("Model id", text: $vm.settings.model)
                } else {
                    Picker("Model", selection: $vm.settings.model) {
                        ForEach(ProviderCatalog.all[vm.settings.provider]!.models, id: \.self) { Text($0).tag($0) }
                    }
                }
                HStack {
                    SecureField("API key", text: $vm.apiKey)
                    Button("Save key", action: vm.saveKey)
                }
                if !vm.keyStatus.isEmpty { Text(vm.keyStatus).font(.caption).foregroundStyle(.secondary) }
                if let url = ProviderCatalog.all[vm.settings.provider]!.keyConsoleURL, let link = URL(string: url) {
                    Link("Open the provider key console", destination: link).font(.caption)
                }
            }

            Section("Language & cleanup") {
                TextField("Language (ISO code, or 'auto')", text: $vm.settings.language)
                Toggle("Clean up transcripts (punctuation & casing)", isOn: $vm.settings.cleanupEnabled)
                    .disabled(!CleanupModelCatalog.supports(vm.settings.provider))
                if vm.settings.cleanupEnabled, CleanupModelCatalog.supports(vm.settings.provider) {
                    Picker("Cleanup model", selection: $vm.settings.cleanupModel) {
                        ForEach(CleanupModelCatalog.forProvider(vm.settings.provider), id: \.self) { Text($0).tag($0) }
                    }
                    Picker("Default writing style", selection: $vm.settings.defaultWritingStyle) {
                        ForEach(WritingStyle.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
                    }
                }
            }

            Section("Recording") {
                Picker("Silence auto-stop", selection: $vm.settings.silenceAutoStopSeconds) {
                    ForEach(SettingsPolicy.silenceChoices, id: \.seconds) { Text($0.label).tag($0.seconds) }
                }
                Stepper("Session cap: \(vm.settings.sessionCapSeconds == 0 ? "Uncapped" : "\(vm.settings.sessionCapSeconds)s")",
                        value: $vm.settings.sessionCapSeconds, in: 0...600, step: 15)
            }

            Section("History & privacy") {
                Toggle("Save dictation history on this device", isOn: $vm.settings.historyEnabled)
                Picker("Keep history for", selection: $vm.settings.historyRetentionMinutes) {
                    ForEach(SettingsPolicy.retentionChoices, id: \.minutes) { Text($0.label).tag($0.minutes) }
                }
                Toggle("Private mode (never save transcripts)", isOn: $vm.settings.privateMode)
                Button("View history…") { showHistory = true }
            }

            Section("System") {
                HStack {
                    TextField("Shortcut (e.g. Alt+Q)", text: $vm.hotkeyText)
                    Button("Set", action: vm.applyHotkey)
                }
                Toggle("Start Quill at login", isOn: Binding(
                    get: { vm.settings.startAtLogin }, set: { vm.setStartAtLogin($0) }))
                Text(services.injector.hasAccessibilityPermission()
                     ? "Accessibility access granted."
                     : "Grant Accessibility access in System Settings › Privacy & Security to type into other apps.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section("Custom words") {
                HStack {
                    TextField("Heard as", text: $vm.newHeard)
                    TextField("Replace with", text: $vm.newReplacement)
                    Button("Add", action: vm.addWord)
                }
                ForEach(vm.customWords) { word in
                    HStack {
                        Text(word.heard); Image(systemName: "arrow.right").font(.caption2); Text(word.replacement).bold()
                        Spacer()
                        Button(role: .destructive) { vm.removeWord(word) } label: { Image(systemName: "trash") }
                            .buttonStyle(.borderless)
                    }
                }
            }
        }
        .formStyle(.grouped)
        .onChange(of: vm.settings) { _ in vm.persist() }
        .sheet(isPresented: $showHistory) { HistoryView(services: services) }
        .frame(width: 560, height: 640)
    }
}
