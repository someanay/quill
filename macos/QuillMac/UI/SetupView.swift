import SwiftUI

// Compact first-run wizard (the macOS counterpart of the Windows SetupWindow): product intro,
// provider/key, shortcut + privacy, first dictation. Compliance/privacy copy is binary-only (spec §2.3).
struct SetupView: View {
    let services: AppServices
    let onDone: () -> Void

    @State private var step = 0
    @State private var settings = AppSettings()
    @State private var apiKey = ""
    @State private var keyStatus = ""
    @State private var hotkeyText = "Alt+Q"

    private let steps = ["Welcome", "Your provider key", "Shortcut & privacy", "You're set"]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Quill").font(.largeTitle.bold())
            Text(steps[step]).font(.headline).foregroundStyle(.secondary)
            Divider()
            Group {
                switch step {
                case 0: welcome
                case 1: keyStep
                case 2: shortcutStep
                default: doneStep
                }
            }
            Spacer()
            HStack {
                if step > 0 { Button("Back") { step -= 1 } }
                Spacer()
                Button(step == steps.count - 1 ? "Start dictating" : "Continue") { advance() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(30)
        .frame(width: 560, height: 620)
        .task { settings = await services.settingsStore.load(); hotkeyText = settings.hotkey }
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Dictate into any app with your own speech-to-text key.")
                .font(.title3)
            Label("Your API key and audio stay on this device. Quill has no account and no backend for your data.", systemImage: "lock.shield")
            Label("Audio is sent only to the provider whose key you enter, over HTTPS, and deleted after each dictation.", systemImage: "waveform")
            Label("Quill never records or types into password fields.", systemImage: "hand.raised")
        }
    }

    private var keyStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Provider", selection: $settings.provider) {
                ForEach(SttProvider.allCases.filter { $0 != .custom }, id: \.self) { Text($0.displayName).tag($0) }
            }
            .onChange(of: settings.provider) { _ in
                settings.model = ProviderCatalog.all[settings.provider]!.defaultModel
                apiKey = services.secrets.get(settings.provider) ?? ""
            }
            SecureField("API key", text: $apiKey)
            if let url = ProviderCatalog.all[settings.provider]!.keyConsoleURL, let link = URL(string: url) {
                Link("Get a key from the provider console", destination: link).font(.caption)
            }
            Button("Save & verify") {
                services.secrets.set(settings.provider, apiKey)
                keyStatus = "Verifying…"
                let p = settings.provider, k = apiKey, m = settings.model
                Task {
                    switch await services.keyValidator.validate(provider: p, key: k, model: m, customBaseURL: "") {
                    case .valid: keyStatus = "Key verified ✓"
                    case .rejected: keyStatus = "Key rejected ✗"
                    case .unverified(let r): keyStatus = "Saved (couldn't verify: \(r))"
                    }
                }
            }
            if !keyStatus.isEmpty { Text(keyStatus).font(.caption).foregroundStyle(.secondary) }
            Text("A key is required — without one, Quill cannot transcribe.").font(.caption).foregroundStyle(.orange)
        }
    }

    private var shortcutStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Press your shortcut in any text field to start and stop dictation.")
            TextField("Shortcut", text: $hotkeyText)
            Toggle("Clean up transcripts before inserting", isOn: $settings.cleanupEnabled)
                .disabled(!CleanupModelCatalog.supports(settings.provider))
            Toggle("Private mode (never save transcripts)", isOn: $settings.privateMode)
            Toggle("Start Quill at login", isOn: $settings.startAtLogin)
            Text("Quill needs Accessibility access (System Settings › Privacy & Security) to type into other apps.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private var doneStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Setup complete.", systemImage: "checkmark.seal.fill").font(.title3)
            Text("Focus any text field, press \(GlobalHotkeyService.normalize(hotkeyText) ?? hotkeyText), and speak. Press again to finish.")
            Text("Quill lives in your menu bar. Open it any time to change settings or view history.")
                .foregroundStyle(.secondary)
        }
    }

    private func advance() {
        if step < steps.count - 1 { step += 1; return }
        if let hk = GlobalHotkeyService.normalize(hotkeyText) { settings.hotkey = hk }
        settings.onboardingComplete = true
        LoginItemService.setEnabled(settings.startAtLogin)
        if !services.injector.hasAccessibilityPermission() { services.injector.requestAccessibilityPermission() }
        let finished = settings
        Task { await services.settingsStore.save(finished); onDone() }
    }
}
