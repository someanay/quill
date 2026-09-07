import SwiftUI
import CryptoKit
import QuillCore

enum SettingsPage: String, CaseIterable, Identifiable {
    case speech, keys, cleanup, style, words, vocab, audio, bubble, privacy, advanced, about
    var id: String { rawValue }
    var key: String { "settings.\(rawValue).title" }
    var symbol: String { ["speech":"waveform", "keys":"key", "cleanup":"sparkles", "style":"textformat", "words":"pencil", "vocab":"person.crop.rectangle", "audio":"mic", "bubble":"rectangle.on.rectangle", "privacy":"lock.shield", "advanced":"slider.horizontal.3", "about":"info.circle"][rawValue]! }
}
struct SettingsView: View {
    @Environment(\.colorScheme) private var scheme
    @State private var page: SettingsPage?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack { if page != nil { Button { page = nil } label: { Image(systemName: "chevron.left") }.accessibilityLabel(Copy.text("action.back")) }; CopyText(page?.key ?? "settings.title").font(QuillTheme.serif()); Spacer() }
                if let page {
                    switch page {
                    case .speech: SpeechSettings()
                    case .keys: KeysSettings()
                    case .cleanup: CleanupSettings()
                    case .style: StyleSettings()
                    case .words: WordsSettings()
                    case .vocab: VocabularySettings()
                    case .audio: AudioSettings()
                    case .bubble: BubbleSettings()
                    case .privacy: PrivacySettings()
                    case .advanced: AdvancedSettings()
                    case .about: AboutSettings()
                    }
                } else {
                    ForEach(SettingsPage.allCases) { item in
                        if item == .speech { SectionLabel(key: "settings.group.dictation") }
                        if item == .audio { SectionLabel(key: "settings.group.capture") }
                        if item == .privacy { SectionLabel(key: "settings.group.privacy") }
                        Button { page = item } label: {
                            QuillCard { HStack { Image(systemName: item.symbol).font(.system(size: 20)).foregroundStyle(QuillTheme.brandInk(scheme == .dark)).frame(width: 40, height: 40).background(QuillTheme.soft(scheme == .dark), in: RoundedRectangle(cornerRadius: 8)); CopyText(item.key).font(QuillTheme.sans(16)); Spacer(); Image(systemName: "chevron.right").foregroundStyle(.secondary) } }
                        }.buttonStyle(.plain).accessibilityIdentifier("settings.\(item.rawValue)")
                    }
                }
            }.padding(28).frame(maxWidth: 800)
        }.frame(maxWidth: .infinity)
    }
}

struct ModelPicker: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    let provider: Provider
    let use: ModelUse
    @Binding var selection: String
    @State private var models: [ModelOption] = []
    @State private var loading = false
    @State private var failed = false
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker(Copy.text(use == .dictation ? "settings.speech.model" : "settings.cleanup.model"), selection: $selection) {
                ForEach(choices) { model in Text(model.id + (model.isFree ? " · " + Copy.text("onboarding.key.console_free") : model.isPaid ? " · " + Copy.text("models.paid") : "")).tag(model.id) }
            }
            TextField(Copy.text("settings.speech.model_field_label"), text: $selection).textFieldStyle(.roundedBorder).accessibilityIdentifier("model-id")
            HStack { Button(Copy.text("mac.models.refresh")) { Task { await refresh() } }.disabled(loading); if loading { ProgressView().controlSize(.small) }; if failed { HelpText(key: "mac.models.offline") } }
        }.task(id: provider.rawValue + use.rawValue + (provider == .custom ? settings.value.customBaseURL : "")) {
            models = cached()
            if models.isEmpty { await refresh() }
        }
    }
    private var choices: [ModelOption] {
        var result = models.isEmpty ? (use == .dictation ? provider.speechModels : [provider.defaultCleanup]).filter { !$0.isEmpty }.map { ModelOption(id: $0, isFree: ModelCatalog.knownFree(provider, $0), isPaid: provider == .openai) } : models
        if settings.value.freeTierProviders.contains(provider.rawValue) { result.removeAll { $0.isPaid } }
        if !selection.isEmpty && !result.contains(where: { $0.id == selection }) { result.insert(ModelOption(id: selection), at: 0) }
        return result
    }
    private var path: URL {
        let suffix = provider == .custom ? "-" + SHA256.hash(data: Data(settings.value.customBaseURL.utf8)).prefix(8).map { String(format: "%02x", $0) }.joined() : ""
        return AppPaths.root.appendingPathComponent("models-\(provider.rawValue)-\(use.rawValue)\(suffix).json")
    }
    private func cached() -> [ModelOption] { (try? JSONDecoder().decode([ModelOption].self, from: Data(contentsOf: path))) ?? [] }
    private func refresh() async {
        loading = true; failed = false; defer { loading = false }
        guard let key = keys.active(provider, settings: settings.value) else { failed = true; return }
        do { models = try await dictation.client.models(provider: provider, use: use, baseURL: settings.value.customBaseURL, key: key); try JSONEncoder().encode(models).write(to: path, options: .atomic) }
        catch { failed = true }
    }
}

struct SpeechSettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    var body: some View {
        QuillCard {
            Picker(Copy.text("settings.speech.provider"), selection: Binding(get: { settings.value.provider }, set: { settings.value.selectProvider($0) })) { ForEach(Provider.allCases) { Text($0.name).tag($0) } }
            if settings.value.provider == .custom {
                TextField(Copy.text("settings.speech.custom_url_label"), text: $settings.value.customBaseURL).textFieldStyle(.roundedBorder)
                HelpText(key: "mac.custom.key_scope")
            }
            ModelPicker(provider: settings.value.provider, use: .dictation, selection: $settings.value.speechModel)
            if keys.active(settings.value.provider, settings: settings.value) == nil { Text(Copy.text("settings.speech.key_required", settings.value.provider.name)).foregroundStyle(QuillTheme.error) }
            Picker(Copy.text("settings.speech.language"), selection: $settings.value.language) {
                Text(Copy.text("settings.speech.lang_en")).tag("en"); Text(Copy.text("settings.speech.lang_auto")).tag("auto"); Text(Copy.text("settings.speech.lang_hi")).tag("hi")
            }
            HelpText(key: "settings.speech.language_help")
            if settings.value.provider == .gemini { HelpText(key: "settings.speech.gemini_caveat") }
        }.onChange(of: settings.value.speechModel) { model in if settings.value.provider == .custom { settings.value.customModel = model } }
    }
}

struct KeysSettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    @State private var adding = false
    @State private var removing: KeySlot?
    @State private var message = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HelpText(key: "mac.key.security")
            ForEach(Provider.allCases) { provider in
                let slots = keys.slots.filter { $0.provider == provider }
                if !slots.isEmpty {
                    QuillCard {
                        Text(provider.name).font(QuillTheme.sans(18)).fontWeight(.medium)
                        ForEach(slots) { slot in
                            VStack(alignment: .leading, spacing: 10) {
                                HStack { Text(slot.label.isEmpty ? provider.name : slot.label); Spacer(); CopyText(slot.status == "valid" ? "value.valid" : "mac.key.unverified").foregroundStyle(slot.status == "valid" ? QuillTheme.success : .secondary) }
                                Text("••••••••••••••••").font(.system(.body, design: .monospaced))
                                HStack {
                                    Button(Copy.text(settings.value.activeKeys[provider.rawValue] == slot.id ? "settings.keys.active" : "settings.keys.use")) { settings.value.activeKeys[provider.rawValue] = slot.id }.disabled(settings.value.activeKeys[provider.rawValue] == slot.id)
                                    Button(Copy.text("settings.keys.test")) { Task { await test(slot) } }
                                    Spacer(); Button(Copy.text("settings.keys.remove"), role: .destructive) { removing = slot }
                                }
                            }.padding(.vertical, 6)
                        }
                    }
                }
            }
            if !message.isEmpty { CopyText(message).foregroundStyle(QuillTheme.error) }
            QuillButton(key: "settings.keys.add") { adding = true }
        }.sheet(isPresented: $adding) { ScrollView { VStack(alignment: .leading, spacing: 20) { HStack { CopyText("settings.keys.add").font(QuillTheme.serif()); Spacer(); Button(Copy.text("action.cancel")) { adding = false } }; KeyEditor { adding = false } }.padding(28) }.frame(width: 590, height: 690) }
        .confirmationDialog(Copy.text("settings.keys.remove"), isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            Button(Copy.text("settings.keys.remove"), role: .destructive) { if let slot = removing { do { try keys.delete(slot); if settings.value.activeKeys[slot.provider.rawValue] == slot.id { settings.value.activeKeys[slot.provider.rawValue] = keys.slots.first(where: { $0.provider == slot.provider })?.id } } catch { message = "mac.error.storage" } }; removing = nil }
        }
    }
    private func test(_ slot: KeySlot) async {
        guard let secret = keys.read(slot, customURL: settings.value.customBaseURL) else { message = "mac.error.bad_key"; return }
        do { try await dictation.client.validateKey(provider: slot.provider, key: secret, baseURL: settings.value.customBaseURL); try keys.updateStatus(slot.id, status: "valid"); message = "onboarding.key.valid" }
        catch { message = (error as? QuillError)?.copyKey ?? "mac.error.network"; try? keys.updateStatus(slot.id, status: "unverified") }
    }
}

struct CleanupSettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    var body: some View {
        QuillCard {
            Toggle(Copy.text("settings.cleanup.tidy_title"), isOn: Binding(get: { settings.value.shouldClean }, set: { settings.value.setCleanupEnabled($0) })).toggleStyle(.switch)
            HelpText(key: "settings.cleanup.tidy_sub")
            Toggle(Copy.text("settings.cleanup.automatic"), isOn: Binding(get: { settings.value.cleanupAutomatic }, set: { settings.value.setCleanupAutomatic($0) })).toggleStyle(.switch).accessibilityIdentifier("cleanup-automatic")
            HelpText(key: "settings.cleanup.automatic_help")
            if settings.value.cleanupAutomatic {
                Text(settings.value.provider.name + " · " + settings.value.effectiveCleanupModel).foregroundStyle(.secondary)
            } else {
                Picker(Copy.text("settings.speech.provider"), selection: Binding(get: { settings.value.cleanupProvider }, set: { settings.value.selectCleanupProvider($0) })) { ForEach(Provider.allCases) { Text($0.name).tag($0) } }
                ModelPicker(provider: settings.value.cleanupProvider, use: .cleanup, selection: $settings.value.cleanupModel)
                if settings.value.cleanupProvider == .custom { TextField(Copy.text("settings.speech.custom_url_label"), text: $settings.value.customBaseURL).textFieldStyle(.roundedBorder); HelpText(key: "settings.cleanup.custom_endpoint") }
            }
            if keys.active(settings.value.effectiveCleanupProvider, settings: settings.value) == nil { Text(Copy.text("settings.speech.key_required", settings.value.effectiveCleanupProvider.name)).foregroundStyle(QuillTheme.error) }
            HelpText(key: "settings.cleanup.provider_help")
            SectionLabel(key: "settings.cleanup.what")
            CopyText("settings.cleanup.before").fontWeight(.medium); CopyText("mac.cleanup.example.before")
            CopyText("settings.cleanup.after").fontWeight(.medium); CopyText("mac.cleanup.example.after")
        }
    }
}
