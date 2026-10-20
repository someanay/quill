import SwiftUI
import Contacts
import QuillCore

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showKeys: Bool
    private let setupContext: Bool
    init(openAPIKeys: Bool = false) { _showKeys = State(initialValue: openAPIKeys); setupContext = openAPIKeys }
    var body: some View {
        Form {
            Section(model.c("settings.group.dictation")) {
                NavigationLink { SpeechSettingsView() } label: { Label(model.c("settings.row.speech_model"), systemImage: "waveform") }.accessibilityIdentifier("settings.speech")
                NavigationLink { APIKeysView() } label: { Label(model.c("settings.row.api_keys"), systemImage: "key") }.accessibilityIdentifier("settings.keys")
                NavigationLink { CleanupSettingsView() } label: { Label(model.c("settings.row.cleanup"), systemImage: "sparkles") }.accessibilityIdentifier("settings.cleanup")
                NavigationLink { WritingStyleView() } label: { Label(model.c("settings.row.writing_style"), systemImage: "textformat") }.accessibilityIdentifier("settings.style")
                NavigationLink { CustomWordsView() } label: { Label(model.c("settings.row.custom_words"), systemImage: "text.book.closed") }.accessibilityIdentifier("settings.words")
            }
            Section(model.c("settings.group.capture")) {
                NavigationLink { AudioSettingsView() } label: { Label(model.c("settings.row.audio"), systemImage: "mic") }.accessibilityIdentifier("settings.audio")
                NavigationLink { VisualSettingsView() } label: { Label(model.c("ios.settings.appearance"), systemImage: "paintpalette") }.accessibilityIdentifier("settings.visual")
                NavigationLink { KeyboardHelpView() } label: { Label(model.c("ios.keyboard.title"), systemImage: "keyboard") }.accessibilityIdentifier("settings.keyboard")
            }
            Section(model.c("settings.group.privacy")) {
                NavigationLink { PrivacySettingsView() } label: { Label(model.c("settings.row.privacy"), systemImage: "lock.shield") }.accessibilityIdentifier("settings.privacy")
                NavigationLink { AdvancedSettingsView() } label: { Label(model.c("settings.row.advanced"), systemImage: "slider.horizontal.3") }.accessibilityIdentifier("settings.advanced")
                NavigationLink { AboutView() } label: { Label(model.c("settings.row.about"), systemImage: "info.circle") }.accessibilityIdentifier("settings.about")
            }
        }.navigationTitle(model.c("nav.settings")).navigationBarTitleDisplayMode(.inline)
            .scrollContentBackground(.hidden).background(Q.paper)
            .navigationDestination(isPresented: $showKeys) { APIKeysView(showSetupHelp: setupContext) }
    }
}

struct SpeechSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var connecting = false
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.speech.title")).accessibilityIdentifier("screen.speech")
            Text(model.c("ios.speech.body")).font(Q.sans(16)).foregroundStyle(Q.muted)
            QCard { Picker(model.c("settings.speech.provider"), selection: Binding(get: { model.settings.provider }, set: model.selectProvider)) { ForEach(Provider.allCases) { Text($0.title).tag($0) } }.pickerStyle(.menu) }
            Button(model.c("ios.speech.manage_key")) { connecting = true }.frame(minHeight: 44)
            if model.settings.provider == .custom {
                QField(title: model.c("settings.speech.custom_url_label"), text: $model.settings.customBaseURL)
                QBanner(text: model.c("ios.keys.endpoint_binding"))
            }
            ModelPickerView(cleanup: false)
            QSection(title: model.c("settings.speech.language")).accessibilityIdentifier("speech.language.section")
            QCard {
                Picker(model.c("settings.speech.language"), selection: $model.settings.language) {
                    Text(model.c("settings.speech.lang_en")).tag("en")
                    Text(model.c("settings.speech.lang_auto")).tag("auto")
                    Text(model.c("settings.speech.lang_hi")).tag("hi")
                    Text("Español").tag("es"); Text("Français").tag("fr"); Text("Deutsch").tag("de")
                    Text("Português").tag("pt"); Text("日本語").tag("ja"); Text("中文").tag("zh"); Text("العربية").tag("ar")
                }.pickerStyle(.menu)
            }
            QBanner(text: model.c("settings.speech.language_help"))
            if model.settings.provider == .gemini { QBanner(text: model.c("settings.speech.gemini_caveat")) }
            if !model.hasActiveKey { NavigationLink(model.c("settings.speech.key_required_cta")) { APIKeysView() }.frame(minHeight: 48) }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .sheet(isPresented: $connecting) {
                NavigationStack {
                    QPage {
                        QHeading(title: model.c("ios.setup.provider_title"))
                        GuidedKeySetup(activateSpeechProvider: true, prefillSavedKey: true, initialProvider: KeySetupPolicy.guidedProviders.contains(model.settings.provider) ? model.settings.provider : .gemini, onSaved: { connecting = false })
                        DisclosureGroup(model.c("ios.keys.other_providers")) { KeyEditor(activateSpeechProvider: true, onSaved: { connecting = false }) }
                    }.toolbar { ToolbarItem(placement: .cancellationAction) { Button(model.c("action.cancel")) { connecting = false } } }
                }.presentationDetents([.large])
            }
    }
}

struct ModelPickerView: View {
    @EnvironmentObject private var model: AppModel
    let cleanup: Bool
    @State private var manual = false
    private var provider: Provider { cleanup ? model.settings.effectiveCleanupProvider : model.settings.provider }
    private var selection: Binding<String> {
        Binding(get: { cleanup ? model.settings.effectiveCleanupModel : model.settings.model }, set: { value in
            if cleanup { model.settings.setCleanupModel(value) }
            else { model.settings.model = value; if provider == .custom { model.settings.customModel = value } }
        })
    }
    private var choices: [ModelOption] {
        let fallback = cleanup ? [provider.defaultCleanupModel] : provider.speechModels
        var result = model.modelChoices
        for id in fallback + [selection.wrappedValue] where !id.isEmpty && !result.contains(where: { $0.id == id }) { result.append(ModelOption(id, isFree: ModelCatalog.knownFree(provider, id))) }
        return result
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            QSection(title: model.c(cleanup ? "settings.cleanup.model" : "settings.speech.model"))
            QCard {
                Picker(model.c("settings.speech.model"), selection: selection) { ForEach(choices) { option in Text(option.name + (option.isFree ? " · " + model.c("onboarding.key.console_free") : "")).tag(option.id) } }.pickerStyle(.menu)
            }
            if provider != .custom { Button(model.c("ios.models.manual")) { manual.toggle() }.frame(minHeight: 44) }
            if provider == .custom || manual { QField(title: model.c("settings.speech.model_field_label"), text: selection) }
            QButton(title: model.loadingModels ? model.c("ios.models.loading") : model.c("ios.models.refresh"), icon: "arrow.clockwise", secondary: true) { Task { await model.refreshModels(cleanup: cleanup) } }.disabled(model.loadingModels)
        }.onDisappear { model.invalidateModelChoices() }
    }
}

struct APIKeysView: View {
    @EnvironmentObject private var model: AppModel
    var showSetupHelp = false
    @State private var removing: SavedKey?
    @State private var showRemove = false
    @State private var addingFor: Provider?
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.keys.title")).accessibilityIdentifier("screen.keys")
            if showSetupHelp {
                Text(model.c("onboarding.key.settings_help")).font(Q.sans(13)).foregroundStyle(Q.muted)
                NavigationLink(model.c("settings.row.speech_model")) { SpeechSettingsView() }.frame(minHeight: 44)
            }
            QBanner(text: model.c("ios.keys.security"), icon: "lock.shield")
            if let addingFor {
                QSection(title: model.c("settings.keys.add"))
                GuidedKeySetup(activateSpeechProvider: false, initialProvider: KeySetupPolicy.guidedProviders.contains(addingFor) ? addingFor : .gemini, onSaved: { self.addingFor = nil })
                DisclosureGroup(model.c("ios.keys.other_providers")) { KeyEditor(onSaved: { self.addingFor = nil }) }
                QButton(title: model.c("action.cancel"), secondary: true) { self.addingFor = nil }
            } else {
            if model.keys.isEmpty {
                QCard { VStack(alignment: .leading, spacing: 10) { Text(model.c("settings.keys.empty_title")).font(Q.serif(24)); Text(model.c("settings.keys.empty_body")).foregroundStyle(Q.muted) } }
            } else {
                QSection(title: model.c("settings.keys.section"))
                ForEach(model.keys) { key in
                    QCard {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack { Text(key.provider.title).fontWeight(.medium); Spacer(); if model.activeKey(key.provider) == key.id { Text(model.c("settings.keys.active")).font(Q.sans(12)).foregroundStyle(Q.success) } }
                            if !key.label.isEmpty && key.label.caseInsensitiveCompare(key.provider.title) != .orderedSame {
                                Text(key.label).foregroundStyle(Q.muted)
                            }
                            Text("••••••••" + key.suffix).font(.system(.body, design: .monospaced))
                            if key.provider == .custom { Text(key.endpoint).font(Q.sans(12)).foregroundStyle(Q.muted) }
                            HStack {
                                Button(model.c("settings.keys.use")) { model.activateKey(key) }.frame(minHeight: 48)
                                Spacer()
                                Button(model.c("settings.keys.test")) { Task { await model.testKey(key) } }.frame(minHeight: 48)
                                Button(role: .destructive) { removing = key; showRemove = true } label: { Image(systemName: "trash").frame(width: 48, height: 48) }.accessibilityLabel(model.c("settings.keys.remove"))
                            }
                            Button(model.c("settings.keys.replace")) { addingFor = key.provider }.frame(minHeight: 48)
                        }
                    }
                }
            }
            QButton(title: model.c("settings.keys.add"), icon: "key") { addingFor = model.settings.provider }.accessibilityIdentifier("keys.add")
            }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .confirmationDialog(model.c("ios.keys.remove_confirm"), isPresented: $showRemove, titleVisibility: .visible) {
                Button(model.c("settings.keys.remove"), role: .destructive) { if let removing { model.deleteKey(removing) } }
            }
    }
}

struct CleanupSettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.cleanup.title")).accessibilityIdentifier("screen.cleanup")
            QCard { Toggle(model.c("settings.cleanup.tidy_title"), isOn: Binding(get: { model.settings.effectiveCleanupEnabled }, set: { model.settings.setCleanupEnabled($0) })).frame(minHeight: 48) }
            QBanner(text: model.c("settings.cleanup.provider_help"), icon: "sparkles")
            QCard { Toggle(model.c("settings.cleanup.automatic"), isOn: Binding(get: { model.settings.cleanupAutomatic }, set: { model.settings.setCleanupAutomatic($0) })).frame(minHeight: 48) }
            Text(model.c("settings.cleanup.automatic_help")).font(Q.sans(13)).foregroundStyle(Q.muted)
            QCard { Picker(model.c("settings.speech.provider"), selection: Binding(get: { model.settings.effectiveCleanupProvider }, set: model.selectCleanupProvider)) { ForEach(Provider.allCases) { Text($0.title).tag($0) } }.pickerStyle(.menu) }
            ModelPickerView(cleanup: true)
            if !model.settings.effectiveCleanupEnabled { QBanner(text: model.c("ios.cleanup.inactive")) }
            QSection(title: model.c("settings.cleanup.what"))
            QCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(model.c("settings.cleanup.before")).font(Q.sans(12)).foregroundStyle(Q.muted)
                    Text(model.c("ios.cleanup.example_before"))
                    Divider()
                    Text(model.c("settings.cleanup.after")).font(Q.sans(12)).foregroundStyle(Q.brandInk)
                    Text(model.c("ios.cleanup.example_after"))
                }
            }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct WritingStyleView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.style.title")).accessibilityIdentifier("screen.style")
            Text(model.c("ios.style.body")).foregroundStyle(Q.muted)
            ForEach(WritingStyle.allCases) { style in
                Button { model.settings.writingStyle = style } label: {
                    QCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 5) { Text(model.c("settings.style.\(style.rawValue)")).font(Q.sans(17)).foregroundStyle(Q.ink); Text(model.c("settings.style.\(style.rawValue)_sub")).font(Q.sans(13)).foregroundStyle(Q.muted).multilineTextAlignment(.leading) }
                            Spacer(); if model.settings.writingStyle == style { Image(systemName: "checkmark.circle.fill").foregroundStyle(Q.brandInk) }
                        }.frame(minHeight: 48)
                    }
                }.buttonStyle(.plain).accessibilityAddTraits(model.settings.writingStyle == style ? [.isSelected] : [])
            }
            QBanner(text: model.c("settings.style.words_promise"), icon: "textformat")
            QBanner(text: model.c("ios.style.app_limit"))
            if !model.settings.effectiveCleanupEnabled { QBanner(text: model.c("settings.style.needs_cleanup")) }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct CustomWordsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var adding = false
    @State private var heard = ""
    @State private var replacement = ""
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.words.title")).accessibilityIdentifier("screen.words")
            Text(model.c("settings.words.body")).foregroundStyle(Q.muted)
            QButton(title: model.c("settings.words.add"), icon: "plus") { heard = ""; replacement = ""; adding = true }
            if model.words.isEmpty { QBanner(text: model.c("settings.words.empty_title"), icon: "text.book.closed") }
            ForEach(model.words) { word in
                QCard {
                    HStack {
                        VStack(alignment: .leading, spacing: 6) { Text(word.heard).foregroundStyle(Q.muted); Label(word.replacement, systemImage: "arrow.turn.down.right") }
                        Spacer(); Button { model.words.removeAll { $0.id == word.id }; model.saveWords() } label: { Image(systemName: "trash").frame(width: 48, height: 48) }.accessibilityLabel(model.c("settings.words.delete"))
                    }
                }
            }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar).sheet(isPresented: $adding) {
            QPage {
                QHeading(title: model.c("settings.words.add_title"))
                QField(title: model.c("settings.words.heard_label"), text: $heard)
                QButton(title: model.c(model.phase == .recording ? "settings.words.listening" : "settings.words.record"), icon: "mic", secondary: true) {
                    if model.phase == .recording { model.stopAndTranscribe() }
                    else { Task { await model.start(voiceSample: true) } }
                }.disabled(model.phase == .transcribing || model.phase == .cleaning)
                if model.phase == .transcribing { ProgressView(model.c("settings.words.transcribing")) }
                QField(title: model.c("settings.words.replacement_label"), text: $replacement)
                QButton(title: model.c("settings.words.save")) { model.addWord(heard: heard, replacement: replacement); adding = false }.disabled(heard.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || replacement.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                QButton(title: model.c("action.cancel"), secondary: true) { model.cancel(); adding = false }
            }.onChange(of: model.voiceWordResult) { _, value in if !value.isEmpty { heard = value; model.voiceWordResult = "" } }.onDisappear { if model.busy { model.cancel() } }
        }
    }
}

struct ContactsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var importing = false
    @State private var clear = false
    @State private var permissionDenied = Self.contactsDenied
    private static var contactsDenied: Bool {
        let permission = CNContactStore.authorizationStatus(for: .contacts)
        return permission == .denied || permission == .restricted
    }
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.vocab.title")).accessibilityIdentifier("screen.contacts")
            QBanner(text: model.c("settings.vocab.body"), icon: "person.crop.rectangle")
            if permissionDenied {
                QBanner(text: model.c("settings.vocab.permission_needed"), icon: "person.crop.rectangle", error: true)
                QButton(title: model.c("settings.vocab.open_settings"), secondary: true) { openSettings() }
            }
            QButton(title: model.c(importing ? "settings.vocab.importing" : "settings.vocab.import"), icon: "person.badge.plus") { importing = true; Task { await model.importContacts(); importing = false; permissionDenied = Self.contactsDenied } }.disabled(importing)
            Text(model.c("settings.vocab.imported", String(model.contactNames.count))).foregroundStyle(Q.muted)
            if !model.contactNames.isEmpty {
                QCard { LazyVStack(alignment: .leading, spacing: 12) { ForEach(model.contactNames, id: \.self) { Text($0).privacySensitive(); Divider() } } }
                QButton(title: model.c("settings.vocab.clear"), secondary: true) { clear = true }
            }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .onChange(of: scenePhase) { _, phase in if phase == .active { permissionDenied = Self.contactsDenied } }
            .confirmationDialog(model.c("settings.vocab.clear_confirm_title"), isPresented: $clear, titleVisibility: .visible) { Button(model.c("settings.vocab.clear_confirm_cta"), role: .destructive) { model.clearContacts() } }
    }
}
