import SwiftUI
import AppKit
import QuillCore

struct KeyEditor: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    var onboarding = false
    var activateSpeechProvider = false
    var prefillSavedKey = false
    var saved: (() -> Void)?
    var body: some View {
        KeySetupContent(settings: settings, keys: keys, client: dictation.client, activateSpeech: onboarding || activateSpeechProvider, prefill: prefillSavedKey, onboarding: onboarding, saved: saved)
    }
}

private struct KeySetupContent: View {
    @Environment(\.colorScheme) private var scheme
    @ObservedObject var settings: SettingsStore
    @StateObject private var model: KeySetupController
    @State private var reveal = Set<Provider>()
    @State private var canPaste = false
    @State private var advanced = false
    @State private var browserError = false
    let saved: (() -> Void)?
    let onboarding: Bool
    private let clipboardTimer = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()
    init(settings: SettingsStore, keys: KeychainStore, client: ProviderClient, activateSpeech: Bool, prefill: Bool, onboarding: Bool, saved: (() -> Void)?) {
        self.settings = settings; self.saved = saved; self.onboarding = onboarding
        _model = StateObject(wrappedValue: KeySetupController(settings: settings, keys: keys, client: client, activateSpeech: activateSpeech, prefill: prefill, initialProvider: onboarding ? .groq : settings.value.provider, fixedProvider: onboarding ? .groq : nil))
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !onboarding { CopyText("desktop.provider.choose").font(QuillTheme.sans(17)).fontWeight(.medium) }
            VStack(spacing: 0) {
                ForEach(onboarding ? [.groq] : KeySetupController.guidedProviders) { provider in
                    if !onboarding && provider != .gemini { Divider() }
                    providerRow(provider)
                }
            }.background(QuillTheme.surface(scheme == .dark), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(QuillTheme.line(scheme == .dark), lineWidth: 1))
            if !onboarding { DisclosureGroup(Copy.text("mac.key.other_providers"), isExpanded: $advanced) {
                VStack(spacing: 0) { ForEach([Provider.openai, .custom]) { provider in providerRow(provider) } }.padding(.top, 8)
            }.disclosureGroupStyle(QuillDisclosureStyle()).font(QuillTheme.sans(12)) }
            if model.storageError { HelpText(key: "onboarding.key.storage_error", bad: true) }
            if browserError { HelpText(key: "onboarding.key.browser_error", bad: true) }
        }.onAppear { refreshClipboardMetadata(); advanced = ["openai", "custom"].contains(model.expanded); model.appear() }
        .onDisappear { model.disappear(); reveal = [] }
        .onReceive(clipboardTimer) { _ in refreshClipboardMetadata() }
        .onChange(of: settings.value.customBaseURL) { _ in if model.expanded == "custom" { model.validate(.custom) } }
    }
    private func refreshClipboardMetadata() {
        // Metadata only. Read the clipboard payload exclusively after explicit Paste.
        canPaste = NSPasteboard.general.availableType(from: [.string]) != nil
    }
    private func title(_ provider: Provider) -> String {
        switch provider {
        case .gemini: return Copy.text("desktop.provider.gemini")
        case .groq: return Copy.text("desktop.provider.groq")
        case .openrouter: return Copy.text("desktop.provider.openrouter")
        default: return provider.name
        }
    }
    private func mask(_ provider: Provider) -> String { let values: [Provider:String] = [.gemini:"AIza…", .groq:"gsk_…", .openrouter:"sk-or-…", .openai:"sk-…", .custom:Copy.text("onboarding.key.field_label")]; return values[provider]! }
    @ViewBuilder private func providerRow(_ provider: Provider) -> some View {
        let expanded = model.expanded == provider.rawValue
        VStack(alignment: .leading, spacing: 0) {
            if !onboarding { Button { model.expand(provider); reveal.remove(provider) } label: {
                HStack { Text(title(provider)).font(QuillTheme.sans(12)).fixedSize(horizontal: false, vertical: true); Spacer(minLength: 8); Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 12)) }.padding(16).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain).disabled(model.saving).accessibilityIdentifier("key-provider-" + provider.rawValue)
            .accessibilityValue(Copy.text(expanded ? "onboarding.key.expanded" : "onboarding.key.collapsed")) }
            if expanded {
                VStack(alignment: .leading, spacing: 8) {
                    instructions(provider)
                    if provider == .custom {
                        QuillTextField(key: "settings.speech.custom_url_label", text: $settings.value.customBaseURL).disabled(model.saving)
                        QuillTextField(key: "settings.speech.model_field_label", text: $settings.value.customModel).disabled(model.saving)
                        HelpText(key: "mac.custom.key_scope")
                    }
                    HStack(spacing: 7) {
                        Image(systemName: "key").foregroundStyle(QuillTheme.muted(scheme == .dark))
                        Group {
                            if reveal.contains(provider) { TextField(mask(provider), text: binding(provider)) }
                            else { SecureField(mask(provider), text: binding(provider)) }
                        }.textFieldStyle(.plain).font(.system(size: 13, design: .monospaced)).accessibilityLabel(Copy.text("onboarding.key.field_label")).accessibilityIdentifier("key-input").disabled(model.saving)
                        Button(Copy.text("onboarding.key.paste")) { if let value = NSPasteboard.general.string(forType: .string) { model.update(provider, value: value) } }.buttonStyle(.plain).foregroundStyle(QuillTheme.brandInk(scheme == .dark)).disabled(!canPaste || model.saving)
                        Button { if !reveal.insert(provider).inserted { reveal.remove(provider) } } label: { Image(systemName: reveal.contains(provider) ? "eye.slash" : "eye") }
                            .buttonStyle(.plain).accessibilityLabel(Copy.text(reveal.contains(provider) ? "a11y.hide_key" : "a11y.show_key")).accessibilityIdentifier("key-reveal")
                    }.padding(11).frame(minHeight: 48).background(QuillTheme.surface(scheme == .dark), in: RoundedRectangle(cornerRadius: 6)).overlay(RoundedRectangle(cornerRadius: 6).stroke(QuillTheme.line(scheme == .dark), lineWidth: 1))
                    status(provider)
                    QuillButton(key: "onboarding.key.save_provider", arguments: [title(provider).components(separatedBy: " (").first ?? provider.name]) { model.save(provider) { saved?() } }
                        .disabled(model.checks[provider] != .valid || model.saving).accessibilityIdentifier("key-save-" + provider.rawValue)
                    if model.saving { ProgressView().progressViewStyle(.linear) }
                }.padding(.horizontal, 16).padding(.bottom, 16).padding(.top, onboarding ? 16 : 0)
            }
        }
    }
    private func binding(_ provider: Provider) -> Binding<String> { Binding(get: { model.drafts[provider] ?? "" }, set: { model.update(provider, value: $0) }) }
    @ViewBuilder private func status(_ provider: Provider) -> some View {
        switch model.checks[provider] ?? .empty {
        case .valid: Label(Copy.text("onboarding.key.verified"), systemImage: "checkmark.circle.fill").font(QuillTheme.sans(14)).foregroundStyle(QuillTheme.success).accessibilityIdentifier("key-verified")
        case .format: Text(Copy.text("onboarding.key.format_help", mask(provider))).font(QuillTheme.sans(12)).foregroundStyle(QuillTheme.muted(scheme == .dark)).accessibilityIdentifier("key-format-help")
        case .checking: ProgressView().progressViewStyle(.linear); HelpText(key: "onboarding.key.verifying")
        case .rejected: HelpText(key: "key.error.invalid", bad: true)
        case .unverified: HelpText(key: "onboarding.key.unverified", bad: true); Button(Copy.text("onboarding.key.retry")) { model.validate(provider, explicitCustomCheck: true) }.accessibilityIdentifier("key-retry")
        case .needsCheck: HelpText(key: "mac.key.custom_check"); Button(Copy.text("onboarding.key.retry")) { model.validate(provider, explicitCustomCheck: true) }.accessibilityIdentifier("key-retry")
        case .empty, .waiting: EmptyView()
        }
    }
    @ViewBuilder private func instructions(_ provider: Provider) -> some View {
        if provider != .custom {
            let steps = Copy.text("onboarding.key.guide.\(provider.rawValue)_steps").split(separator: "\n").map(String.init).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            VStack(alignment: .leading, spacing: 3) {
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 5) {
                        Text("\(index + 1))").frame(width: 19, alignment: .leading)
                        if let part = step.split(separator: " ").first(where: { $0.hasPrefix("https://") }), let url = URL(string: String(part)) {
                            Button { browserError = !NSWorkspace.shared.open(url) } label: { Text(step).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true) }.buttonStyle(.plain).foregroundStyle(QuillTheme.brandInk(scheme == .dark))
                        } else { Text(step).fixedSize(horizontal: false, vertical: true) }
                    }.font(QuillTheme.sans(12))
                }
            }.padding(.bottom, 4)
        }
    }
}
