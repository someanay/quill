import SwiftUI
import QuillCore

extension Notification.Name { static let quillOpenSettings = Notification.Name("QuillOpenSettings") }

struct MainView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var dictation: Dictation
    @EnvironmentObject var crashReports: CrashReports
    @Environment(\.colorScheme) private var scheme
    @State private var tab = "home"
    @State private var columns: NavigationSplitViewVisibility = .all
    var body: some View {
        Group {
            if settings.value.onboardingComplete {
                GeometryReader { geometry in
                    NavigationSplitView(columnVisibility: $columns) {
                        List {
                            nav("home", icon: "house")
                            nav("history", icon: "clock.arrow.circlepath")
                            Button(action: openSettings) { Label(Copy.text("nav.settings"), systemImage: "gearshape").frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8) }.buttonStyle(.plain).accessibilityIdentifier("nav.settings")
                        }.listStyle(.sidebar).navigationTitle("Quill").navigationSplitViewColumnWidth(min: 160, ideal: 190, max: 240)
                    } detail: {
                        Group {
                            if tab == "history" { HistoryView() }
                            else { HomeView(openSettings: openSettings, openHistory: { tab = "history" }) }
                        }.toolbar {
                            ToolbarItemGroup {
                                if geometry.size.width < 800 {
                                    Button { tab = "home" } label: { Label(Copy.text("nav.home"), systemImage: "house") }
                                    Button { tab = "history" } label: { Label(Copy.text("nav.history"), systemImage: "clock") }
                                }
                                Button(action: openSettings) { Label(Copy.text("nav.settings"), systemImage: "gearshape") }
                            }
                        }
                    }.onAppear { columns = geometry.size.width < 800 ? .detailOnly : .all }
                        .onChange(of: geometry.size.width < 800) { compact in columns = compact ? .detailOnly : .all }
                }
            } else { OnboardingView() }
        }.font(QuillTheme.sans()).foregroundStyle(QuillTheme.ink(scheme == .dark))
            .background(QuillTheme.paper(scheme == .dark)).tint(QuillTheme.brandInk(scheme == .dark))
            .preferredColorScheme(settings.value.appTheme == "Dark" ? .dark : settings.value.appTheme == "Light" ? .light : nil)
            .frame(minWidth: 640, minHeight: 440)
            .onChange(of: settings.value.bootRestore) { _ in dictation.configureLogin() }
            .sheet(isPresented: Binding(get: { crashReports.pendingReport != nil }, set: { if !$0 { crashReports.discard() } })) {
                DiagnosticReportView(reportText: crashReports.pendingReport, onClose: { crashReports.discard() })
            }
    }
    private func openSettings() { NotificationCenter.default.post(name: .quillOpenSettings, object: nil) }
    private func nav(_ id: String, icon: String) -> some View {
        Button { tab = id } label: {
            Label(Copy.text("nav.\(id)"), systemImage: icon).frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(tab == id ? QuillTheme.brandInk(scheme == .dark) : .primary).accessibilityIdentifier("nav.\(id)")
    }
}

struct HomeView: View {
    @Environment(\.colorScheme) private var scheme
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    @State private var practice = ""
    var openSettings: () -> Void
    var openHistory: () -> Void
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack { CopyText("desktop.workspace.title").font(QuillTheme.serif(34)); Spacer(); if settings.value.privateMode { Image(systemName: "eye.slash").accessibilityLabel(Copy.text("settings.privacy.private_mode")) } }
                    if !dictation.microphoneGranted || !dictation.accessibilityGranted || keys.active(settings.value.provider, settings: settings.value) == nil {
                        QuillCard { CopyText("desktop.workspace.setup_needed").fontWeight(.medium); QuillButton(key: "nav.settings", primary: false, action: openSettings) }
                    }
                    if geometry.size.width >= 960 {
                        HStack(alignment: .top, spacing: 24) { workspace.frame(maxWidth: .infinity); supporting.frame(width: max(300, geometry.size.width * 0.32)) }
                    } else { workspace; supporting }
                }.padding(28).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
    private var workspace: some View {
        VStack(alignment: .leading, spacing: 20) {
            QuillCard {
                CopyText("desktop.practice.title").font(QuillTheme.serif(25))
                HelpText(key: "desktop.practice.body", size: 14)
                TextEditor(text: $practice).font(QuillTheme.sans(17)).frame(minHeight: 230).border(QuillTheme.line(scheme == .dark)).accessibilityLabel(Copy.text("desktop.practice.title"))
                if dictation.shortcutAvailable { Label(Copy.text("mac.hotkey.label"), systemImage: "keyboard").font(QuillTheme.sans(14)) } else { HelpText(key: "mac.setup.shortcut_unavailable", size: 14, bad: true) }
                if dictation.state == .recording { InkMeter(amplitude: dictation.level); Text(formatDuration(dictation.elapsed)).monospacedDigit(); QuillButton(key: "mac.dictation.stop") { dictation.stop() } }
                if dictation.state == .transcribing { ProgressView() }
                if !dictation.message.isEmpty { CopyText(dictation.message).fixedSize(horizontal: false, vertical: true).foregroundStyle(dictation.state == .error ? QuillTheme.error : QuillTheme.muted(scheme == .dark)) }
                if !dictation.transcript.isEmpty { Text(dictation.transcript).textSelection(.enabled) }
                if dictation.state != .idle {
                    HStack { Button(Copy.text("action.cancel")) { dictation.cancel() }; Spacer(); if dictation.state == .error { Button(Copy.text("mac.action.retry")) { dictation.retry() } }; if !dictation.transcript.isEmpty { Button(Copy.text("history.sheet.copy")) { dictation.copyTranscript() } } }
                }
            }
            HelpText(key: "mac.setup.controls", size: 14)
        }
    }
    private var supporting: some View {
        VStack(alignment: .leading, spacing: 20) {
            QuillCard {
                CopyText("desktop.workspace.configuration").font(QuillTheme.sans(17)).fontWeight(.medium)
                Text(settings.value.provider.name + " · " + settings.value.speechModel).font(QuillTheme.sans(14)).textSelection(.enabled)
                Toggle(Copy.text("desktop.setup.bubble_choice"), isOn: Binding(get: { settings.value.bubbleActive && settings.value.bubbleVisible }, set: { value in NotificationCenter.default.post(name: .quillExplicitVisibility, object: nil); settings.value.bubbleActive = value; settings.value.bubbleVisible = value }))
                QuillButton(key: "nav.settings", primary: false, action: openSettings)
            }
            QuillCard {
                CopyText("desktop.workspace.recent").font(QuillTheme.sans(17)).fontWeight(.medium)
                if history.entries.isEmpty { HelpText(key: settings.value.shouldSaveHistory ? "mac.history.empty" : "mac.history.off", size: 14) }
                ForEach(Array(history.entries.prefix(3))) { entry in
                    Divider(); Text(entry.text).lineLimit(3).font(QuillTheme.sans(14)); Text(entry.created, style: .time).font(QuillTheme.sans(12)).foregroundStyle(.secondary)
                }
                Button(Copy.text("nav.history"), action: openHistory)
            }
        }
    }
}

func formatDuration(_ seconds: Double) -> String { String(format: "%d:%02d", Int(seconds) / 60, Int(seconds) % 60) }

struct HistoryView: View {
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var settings: SettingsStore
    @State private var selected: UUID?
    @State private var confirmClear = false
    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 20) {
                HStack { CopyText("history.title").font(QuillTheme.serif(34)); Spacer(); Button(Copy.text("history.clear_all"), role: .destructive) { confirmClear = true }.disabled(history.entries.isEmpty) }
                if history.storageFailed { CopyText("mac.error.storage").foregroundStyle(QuillTheme.error) }
                if history.entries.isEmpty {
                    QuillCard { CopyText(settings.value.shouldSaveHistory ? "history.empty.on_title" : "history.empty.off_title").font(QuillTheme.serif(24)); HelpText(key: settings.value.shouldSaveHistory ? "mac.history.empty" : "mac.history.off", size: 14) }; Spacer()
                } else if geometry.size.width >= 760 {
                    HSplitView { entries.frame(minWidth: 220, idealWidth: 300, maxWidth: geometry.size.width * 0.4); reader.frame(minWidth: 280, maxWidth: .infinity) }
                } else { entries.frame(height: min(200,geometry.size.height * 0.35)); reader }
            }.padding(28)
        }.onAppear { history.refresh(settings: settings.value); ensureSelection() }
            .onChange(of: history.entries.map(\.id)) { _ in ensureSelection() }
            .confirmationDialog(Copy.text("history.clear.title"), isPresented: $confirmClear) { Button(Copy.text("history.clear.confirm"), role: .destructive) { history.clear() } } message: { Text(Copy.text("history.clear.body", String(history.entries.count))) }
    }
    private var entries: some View {
        List(selection: $selected) {
            ForEach(history.entries) { entry in
                VStack(alignment: .leading, spacing: 6) { Text(entry.created, style: .time).font(QuillTheme.sans(12)).foregroundStyle(.secondary); Text(entry.text).font(QuillTheme.sans(14)).lineLimit(2) }.padding(.vertical, 7).tag(entry.id)
            }
        }.accessibilityIdentifier("history-list")
    }
    private var reader: some View {
        VStack(alignment: .leading, spacing: 18) {
            if let entry = history.entries.first(where: { $0.id == selected }) {
                HStack { Text(entry.created, style: .date); Spacer(); Text(entry.provider.name) }.font(QuillTheme.sans(12)).foregroundStyle(.secondary)
                ScrollView { Text(entry.text).font(QuillTheme.sans(17)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }.accessibilityIdentifier("history-reader")
                HStack { Button(Copy.text("history.sheet.copy")) { ClipboardLease.write(entry.text) }; Spacer(); Button(Copy.text("history.sheet.delete"), role: .destructive) { history.delete(entry.id, settings: settings.value) } }
            }
        }.padding(18).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
    private func ensureSelection() { if !history.entries.contains(where: { $0.id == selected }) { selected = history.entries.first?.id } }
}
