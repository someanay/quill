import SwiftUI
import QuillCore

struct KeyEditor: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    var onboarding = false
    var saved: (() -> Void)?
    @State private var provider: Provider = .groq
    @State private var secret = ""
    @State private var label = ""
    @State private var visible = false
    @State private var busy = false
    @State private var error = ""
    @State private var task: Task<Void,Never>?
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HelpText(key: "onboarding.key.body")
            Picker(Copy.text("settings.speech.provider"), selection: $provider) { ForEach(Provider.allCases) { Text($0.name).tag($0) } }.pickerStyle(.segmented).disabled(busy)
            if provider != .custom {
                DisclosureGroup(Copy.text("onboarding.key.where_link")) {
                    VStack(alignment: .leading, spacing: 12) {
                        HelpText(key: "onboarding.key.guide.\(provider.rawValue)_steps")
                        if let console = provider.console { Link(Copy.text("onboarding.key.guide.open", provider.name), destination: console) }
                    }.padding(.top, 10)
                }
            }
            if provider == .custom {
                TextField(Copy.text("settings.speech.custom_url_label"), text: $settings.value.customBaseURL).textFieldStyle(.roundedBorder)
                TextField(Copy.text("settings.speech.model_field_label"), text: $settings.value.customModel).textFieldStyle(.roundedBorder)
                HelpText(key: "settings.speech.custom_url_help")
            }
            QuillCard {
                CopyText("onboarding.key.field_label").fontWeight(.medium)
                HStack {
                    Group { if visible { TextField("", text: $secret) } else { SecureField("", text: $secret) } }.textFieldStyle(.roundedBorder).font(.system(.body, design: .monospaced)).accessibilityLabel(Copy.text("onboarding.key.field_label")).accessibilityIdentifier("key-input")
                    Button { visible.toggle() } label: { Image(systemName: visible ? "eye.slash" : "eye") }.accessibilityLabel(Copy.text(visible ? "a11y.hide_key" : "mac.key.show"))
                    Button(Copy.text("onboarding.key.paste")) { secret = NSPasteboard.general.string(forType: .string) ?? "" }
                }
                TextField(Copy.text("mac.key.label"), text: $label).textFieldStyle(.roundedBorder)
                HelpText(key: "onboarding.key.free_note")
            }
            if !error.isEmpty { CopyText(error).foregroundStyle(QuillTheme.error).fixedSize(horizontal: false, vertical: true) }
            if busy { HStack { ProgressView().controlSize(.small); CopyText("onboarding.key.verifying") } }
            QuillButton(key: "onboarding.key.cta") { verify() }.disabled(secret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
            HelpText(key: "mac.key.security")
        }.onAppear { provider = onboarding ? .gemini : settings.value.provider }.onDisappear { task?.cancel(); secret = "" }
        .onChange(of: provider) { _ in secret = ""; error = "" }
    }
    private func verify() {
        busy = true; error = ""
        let selected = provider, entered = secret.trimmingCharacters(in: .whitespacesAndNewlines), name = label, customURL = settings.value.customBaseURL
        task = Task { @MainActor in
            defer { busy = false }
            do {
                try await dictation.client.validateKey(provider: selected, key: entered, baseURL: customURL)
                let models = onboarding ? (try? await dictation.client.models(provider: selected, use: .dictation, baseURL: customURL, key: entered)) : nil
                try Task.checkCancellation()
                let slot = try keys.save(provider: selected, label: name, secret: entered, status: "valid", customURL: customURL)
                settings.selectSavedKey(slot, activateSpeechProvider: onboarding, preferredModel: models?.first(where: \.isFree)?.id)
                secret = ""
                try Task.checkCancellation(); saved?()
            } catch is CancellationError { }
            catch { self.error = (error as? QuillError)?.copyKey ?? "onboarding.key.unverified" }
        }
    }
}
