import SwiftUI
import AppKit
import Contacts
import Combine
import QuillCore

struct StyleSettings: View {
    @EnvironmentObject var settings: SettingsStore
    @State private var search = ""
    @State private var apps: [(id: String, name: String)] = []
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HelpText(key: "settings.style.intro")
            QuillCard {
                SectionLabel(key: "settings.style.default_section")
                Picker(Copy.text("settings.style.title"), selection: $settings.value.writingStyle) { ForEach(WritingStyle.allCases) { CopyText("settings.style.\($0.rawValue)").tag($0) } }.pickerStyle(.segmented)
                HelpText(key: "settings.style.words_promise")
            }
            if !settings.value.shouldClean { HelpText(key: "settings.style.needs_cleanup") }
            SectionLabel(key: "settings.style.per_app_section")
            TextField(Copy.text("settings.style.search_hint"), text: $search).textFieldStyle(.roundedBorder)
            ForEach(apps.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }, id: \.id) { app in
                QuillCard {
                    HStack { Text(app.name); Spacer(); Picker(Copy.text("settings.style.assign_title"), selection: Binding<String>(get: { settings.value.styleOverrides[app.id]?.rawValue ?? "default" }, set: { settings.value.styleOverrides[app.id] = WritingStyle(rawValue: $0) })) { CopyText("settings.style.use_default").tag("default"); ForEach(WritingStyle.allCases) { CopyText("settings.style.\($0.rawValue)").tag($0.rawValue) } }.labelsHidden().frame(maxWidth: 180) }
                    Text(Copy.text("mac.style.effective", Copy.text("settings.style.\(settings.value.style(for: app.id).rawValue)"))).font(QuillTheme.sans(12)).foregroundStyle(.secondary)
                }
            }
            HelpText(key: "settings.style.help")
        }.onAppear {
            var found: [String:String] = [:]
            for path in ["/Applications", "/System/Applications", NSHomeDirectory() + "/Applications"] {
                guard let enumerator = FileManager.default.enumerator(at: URL(fileURLWithPath: path), includingPropertiesForKeys: [.isDirectoryKey], options: [.skipsHiddenFiles, .skipsPackageDescendants]) else { continue }
                for case let url as URL in enumerator where url.pathExtension == "app" {
                    if let bundle = Bundle(url: url), let id = bundle.bundleIdentifier { found[id] = bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? bundle.object(forInfoDictionaryKey: "CFBundleName") as? String ?? url.deletingPathExtension().lastPathComponent }
                }
            }
            apps = found.map { (id: $0.key, name: $0.value) }.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        }
    }
}

struct WordsSettings: View {
    @Environment(\.colorScheme) private var scheme
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var dictation: Dictation
    @State private var heard = ""
    @State private var replacement = ""
    @State private var editing: UUID?
    @State private var capturing = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HelpText(key: "settings.words.body")
            QuillCard {
                TextField(Copy.text("settings.words.heard_label"), text: $heard).textFieldStyle(.roundedBorder).accessibilityIdentifier("word-heard")
                HStack {
                    Button(Copy.text(capturing && dictation.state == .recording ? "mac.dictation.stop" : "settings.words.record")) {
                        if capturing && dictation.state == .recording { dictation.stop() }
                        else { dictation.start(wordCapture: true); capturing = dictation.state == .recording; dictation.onWordCaptured = { text in heard = text; capturing = false } }
                    }.disabled(dictation.state == .transcribing)
                    if capturing { InkMeter(amplitude: dictation.level, bars: 6).frame(height: 34) }
                }
                TextField(Copy.text("settings.words.replacement_label"), text: $replacement).textFieldStyle(.roundedBorder).accessibilityIdentifier("word-replacement")
                QuillButton(key: "settings.words.save") {
                    let word = CustomWord(id: editing ?? UUID(), heard: heard.trimmingCharacters(in: .whitespacesAndNewlines), replacement: replacement.trimmingCharacters(in: .whitespacesAndNewlines))
                    if let index = settings.value.words.firstIndex(where: { $0.id == editing }) { settings.value.words[index] = word } else { settings.value.words.append(word) }
                    heard = ""; replacement = ""; editing = nil
                }.disabled(heard.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || replacement.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            ForEach(settings.value.words) { word in
                QuillCard { HStack { VStack(alignment: .leading) { Text(word.heard); Text("→ " + word.replacement).foregroundStyle(QuillTheme.brandInk(scheme == .dark)) }; Spacer(); Button(Copy.text("mac.action.edit")) { heard = word.heard; replacement = word.replacement; editing = word.id }; Button(Copy.text("settings.words.delete"), role: .destructive) { settings.value.words.removeAll { $0.id == word.id } }.accessibilityIdentifier("word-delete") } }
            }
        }.onDisappear { if capturing { dictation.cancel() } }
    }
}

struct VocabularySettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var dictation: Dictation
    @State private var importing = false
    @State private var clear = false
    @State private var failed = false
    @State private var granted = CNContactStore.authorizationStatus(for: .contacts) == .authorized
    var body: some View {
        QuillCard {
            HelpText(key: "settings.vocab.body")
            if !granted { Label(Copy.text("settings.vocab.permission_needed"), systemImage: "person.crop.circle.badge.exclamationmark").foregroundStyle(QuillTheme.error) }
            Text(Copy.text("settings.vocab.imported", String(settings.value.contactNames.count)))
            QuillButton(key: importing ? "settings.vocab.importing" : settings.value.contactNames.isEmpty ? "settings.vocab.import" : "settings.vocab.reimport") { importing = true; failed = false; Task { failed = !(await dictation.importContacts()); granted = CNContactStore.authorizationStatus(for: .contacts) == .authorized; importing = false } }.disabled(importing)
            if failed { HelpText(key: "mac.error.contacts").foregroundStyle(QuillTheme.error) }
            Button(Copy.text("settings.vocab.open_settings")) { SystemLinks.open("Privacy_Contacts") }
            if !settings.value.contactNames.isEmpty {
                DisclosureGroup(Copy.text("settings.vocab.title")) { Text(settings.value.contactNames.joined(separator: ", ")).font(QuillTheme.sans(13)).textSelection(.enabled) }
                Button(Copy.text("settings.vocab.clear"), role: .destructive) { clear = true }
            }
        }.onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in granted = CNContactStore.authorizationStatus(for: .contacts) == .authorized }
        .confirmationDialog(Copy.text("settings.vocab.clear_confirm_title"), isPresented: $clear) { Button(Copy.text("settings.vocab.clear_confirm_cta"), role: .destructive) { settings.value.contactNames = [] } } message: { CopyText("settings.vocab.clear_confirm_body") }
    }
}

struct AudioSettings: View {
    @EnvironmentObject var settings: SettingsStore
    var body: some View {
        QuillCard {
            SectionLabel(key: "settings.audio.start_stop")
            HStack { CopyText("settings.audio.trigger"); Spacer(); CopyText("settings.audio.trigger_value") }
            HelpText(key: "mac.hotkey.label")
            SectionLabel(key: "settings.audio.max_length")
            Slider(value: Binding(get: { Double(settings.value.sessionCapSeconds) }, set: { settings.value.sessionCapSeconds = Int($0) }), in: 60...300, step: 60).accessibilityLabel(Copy.text("settings.audio.max_length"))
            Text(Copy.text("mac.audio.limit", String(settings.value.sessionCapSeconds)))
            HelpText(key: "mac.audio.silence")
            HelpText(key: "settings.audio.silence_banner")
            SectionLabel(key: "settings.audio.format")
            HelpText(key: "mac.audio.format")
            HelpText(key: "mac.audio.input")
        }
    }
}

struct BubbleSettings: View {
    @EnvironmentObject var settings: SettingsStore
    var body: some View {
        QuillCard {
            HStack(spacing: 20) {
                ForEach([false,true], id: \.self) { dark in VStack { BubbleFace(state: .idle, diameter: settings.value.bubbleDiameter, dark: dark); CopyText(dark ? "settings.bubble.preview_dark" : "settings.bubble.preview_light").font(QuillTheme.sans(12)).foregroundStyle(dark ? .white : .black) }.frame(maxWidth: .infinity).padding(18).background(dark ? Color(hex: 0x15161C) : Color(hex: 0xF0F1F6), in: RoundedRectangle(cornerRadius: 12)) }
            }
            HelpText(key: "settings.bubble.help")
            Picker(Copy.text("settings.bubble.size"), selection: $settings.value.bubbleSize) { ForEach(["Small","Medium","Large"], id: \.self) { CopyText("mac.size.\($0.lowercased())").tag($0) } }.pickerStyle(.segmented)
            Picker(Copy.text("settings.bubble.theme"), selection: $settings.value.appTheme) { CopyText("mac.theme.system").tag("Match system"); CopyText("mac.theme.light").tag("Light"); CopyText("mac.theme.dark").tag("Dark") }.pickerStyle(.segmented)
        }
    }
}

struct PrivacySettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var dictation: Dictation
    @State private var report = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            QuillCard { CopyText("settings.privacy.banner_title").fontWeight(.medium); HelpText(key: "mac.privacy.body") }
            QuillCard {
                Toggle(Copy.text("settings.privacy.private_mode"), isOn: $settings.value.privateMode).toggleStyle(.switch).accessibilityIdentifier("privacy-private-mode")
                HelpText(key: "settings.privacy.private_mode_sub")
                Toggle(Copy.text("settings.privacy.history"), isOn: $settings.value.historyEnabled).toggleStyle(.switch).disabled(settings.value.privateMode)
                Picker(Copy.text("settings.privacy.history_retention"), selection: $settings.value.historyRetentionMinutes) {
                    CopyText("settings.privacy.history_retention.hour").tag(60); CopyText("settings.privacy.history_retention.day").tag(1440); CopyText("settings.privacy.history_retention.week").tag(10080); CopyText("settings.privacy.history_retention.month").tag(43200); CopyText("settings.privacy.history_retention.forever").tag(0)
                }.disabled(settings.value.privateMode || !settings.value.historyEnabled)
            }
            QuillCard {
                SectionLabel(key: "settings.privacy.diagnostics")
                Toggle(Copy.text("settings.privacy.crash"), isOn: $settings.value.crashReportingEnabled).toggleStyle(.switch)
                HelpText(key: "settings.privacy.diag_help")
                Button(Copy.text("settings.privacy.example")) { report = true }
            }
            QuillCard {
                SectionLabel(key: "settings.privacy.permissions")
                permission("settings.privacy.mic", granted: dictation.microphoneGranted, pane: "Privacy_Microphone")
                permission("settings.privacy.accessibility", granted: dictation.accessibilityGranted, pane: "Privacy_Accessibility")
                Button(Copy.text("settings.vocab.open_settings")) { SystemLinks.open("Privacy_Contacts") }
            }
        }.sheet(isPresented: $report) { DiagnosticReportView() }
    }
    private func permission(_ key: String, granted: Bool, pane: String) -> some View {
        HStack { CopyText(key); Spacer(); CopyText(granted ? "value.granted" : "value.off").foregroundStyle(granted ? QuillTheme.success : QuillTheme.error); Button(Copy.text("settings.vocab.open_settings")) { SystemLinks.open(pane) } }
    }
}

struct AdvancedSettings: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    @State private var reset = false
    @State private var clear = false
    var body: some View {
        QuillCard {
            Toggle(Copy.text("mac.login.enable"), isOn: $settings.value.bootRestore).toggleStyle(.switch)
            HelpText(key: "mac.login.help")
            Button(Copy.text("mac.login.settings")) { if let url = URL(string: "x-apple.systempreferences:com.apple.LoginItems-Settings.extension") { NSWorkspace.shared.open(url) } }
            SectionLabel(key: "settings.advanced.reset")
            Button(Copy.text("settings.advanced.reset_settings")) { reset = true }
            Button(Copy.text("settings.advanced.clear"), role: .destructive) { clear = true }
        }.confirmationDialog(Copy.text("settings.advanced.reset_confirm_title"), isPresented: $reset) { Button(Copy.text("settings.advanced.reset_confirm_cta"), role: .destructive) { dictation.cancel(); settings.reset() } } message: { CopyText("settings.advanced.reset_confirm_body") }
        .confirmationDialog(Copy.text("settings.advanced.clear_confirm_title"), isPresented: $clear) { Button(Copy.text("settings.advanced.clear_confirm_cta"), role: .destructive) {
            dictation.cancel()
            do {
                try keys.clear(); history.clear(); settings.value = QuillSettings()
                let files = try FileManager.default.contentsOfDirectory(at: AppPaths.root, includingPropertiesForKeys: nil)
                for file in files where file.lastPathComponent.hasPrefix("models-") || file.lastPathComponent == "diagnostic.json" { try FileManager.default.removeItem(at: file) }
            } catch { dictation.report(QuillError.storage) }
        } } message: { CopyText("settings.advanced.clear_confirm_body") }
    }
}

struct AboutSettings: View {
    @State private var licenses = false
    var body: some View {
        QuillCard {
            Wordmark()
            Text(Copy.text("settings.about.version", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0", Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"))
            DisclosureGroup(Copy.text("settings.about.privacy_policy")) { HelpText(key: "mac.legal.privacy") }
            DisclosureGroup(Copy.text("settings.about.terms")) { HelpText(key: "mac.legal.terms") }
            DisclosureGroup(Copy.text("settings.about.licenses"), isExpanded: $licenses) { Text(licenseText).font(QuillTheme.sans(12)).textSelection(.enabled) }
            HelpText(key: "settings.about.footer")
            HelpText(key: "mac.about.copy")
        }
    }
    private var licenseText: String {
        guard let url = Bundle.main.url(forResource: "LICENSE", withExtension: "txt", subdirectory: "fonts") ?? Bundle.main.url(forResource: "LICENSE", withExtension: "txt") else { return Copy.text("mac.error.storage") }
        return (try? String(contentsOf: url, encoding: .utf8)) ?? Copy.text("mac.error.storage")
    }
}

struct DiagnosticReportView: View {
    @Environment(\.dismiss) private var dismiss
    var reportText: String? = nil
    var onClose: (() -> Void)? = nil
    @State private var saveFailed = false
    private var text: String { reportText ?? Diagnostics.report() }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            CopyText("settings.privacy.example").font(QuillTheme.serif(24))
            HelpText(key: "settings.privacy.diag_help")
            ScrollView { Text(text).font(.system(.body, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }
            if saveFailed { CopyText("mac.error.storage").foregroundStyle(QuillTheme.error) }
            HStack { Button(Copy.text("mac.action.close")) { onClose?(); dismiss() }; Spacer(); Button(Copy.text("mac.report.save")) { saveFailed = false; let panel = NSSavePanel(); panel.nameFieldStringValue = "Quill-diagnostic.txt"; if panel.runModal() == .OK, let url = panel.url { do { try text.write(to: url, atomically: true, encoding: .utf8); onClose?(); dismiss() } catch { saveFailed = true } } } }
        }.padding(28).frame(width: 570, height: 420)
    }
}

@MainActor enum Diagnostics {
    static func report() -> String {
        // Deliberately constructs an allowlist instead of trying to redact arbitrary logs or errors.
        "Quill macOS\nVersion: \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown")\nOS: \(ProcessInfo.processInfo.operatingSystemVersionString)\nArchitecture: \(architecture)\nMicrophone authorized: \(AudioCapture.permitted)\nAccessibility authorized: \(TextInjector.permitted)\n"
    }
    private static var architecture: String {
        #if arch(arm64)
        return "arm64"
        #else
        return "x86_64"
        #endif
    }
}
