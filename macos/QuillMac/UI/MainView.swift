import SwiftUI
import QuillCore

struct MainView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    @EnvironmentObject var crashReports: CrashReports
    @Environment(\.colorScheme) private var scheme
    @State private var tab = "home"
    var body: some View {
        VStack(spacing: 0) {
            if settings.value.onboardingComplete {
                Group {
                    switch tab {
                    case "history": HistoryView()
                    case "settings": SettingsView()
                    default: HomeView(openSettings: { tab = "settings" }, openHistory: { tab = "history" })
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
                Divider()
                HStack {
                    nav("home", icon: "house"); nav("history", icon: "clock.arrow.circlepath"); nav("settings", icon: "gearshape")
                }.padding(12).background(QuillTheme.surface(scheme == .dark))
            } else { OnboardingView() }
        }.font(QuillTheme.sans()).foregroundStyle(QuillTheme.ink(scheme == .dark))
        .background(QuillTheme.paper(scheme == .dark)).tint(QuillTheme.brandInk(scheme == .dark))
        .preferredColorScheme(settings.value.appTheme == "Dark" ? .dark : settings.value.appTheme == "Light" ? .light : nil)
        .frame(minWidth: 510, idealWidth: 600, minHeight: 620, idealHeight: 790)
        .onChange(of: settings.value.bootRestore) { _ in dictation.configureLogin() }
        .sheet(isPresented: Binding(get: { crashReports.pendingReport != nil }, set: { if !$0 { crashReports.discard() } })) {
            DiagnosticReportView(reportText: crashReports.pendingReport, onClose: { crashReports.discard() })
        }
    }
    private func nav(_ id: String, icon: String) -> some View {
        Button { tab = id } label: {
            VStack(spacing: 4) { Image(systemName: icon).font(.system(size: 19)).frame(width: 54, height: 30).background(tab == id ? QuillTheme.soft(scheme == .dark) : .clear, in: Capsule()); CopyText("nav.\(id)").font(QuillTheme.sans(12)) }.frame(maxWidth: .infinity).frame(minHeight: 48).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(tab == id ? QuillTheme.brandInk(scheme == .dark) : .secondary).accessibilityIdentifier("nav.\(id)")
    }
}

struct HomeView: View {
    @Environment(\.colorScheme) private var scheme
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    var openSettings: () -> Void
    var openHistory: () -> Void
    private var bubbleOn: Bool { settings.value.bubbleActive && settings.value.bubbleVisible }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack { Wordmark(); Spacer(); Button(action: openSettings) { Image(systemName: "gearshape") }.accessibilityLabel(Copy.text("a11y.settings")) }
                if settings.value.accessibilityEnabledOnce && !dictation.accessibilityGranted {
                    QuillCard { Label(Copy.text("home.banner.a11y_off_text"), systemImage: "exclamationmark.shield").foregroundStyle(QuillTheme.error); Button(Copy.text("onboarding.accessibility.enable_cta")) { SystemLinks.open("Privacy_Accessibility") } }
                }
                QuillCard {
                    HStack(spacing: 14) {
                        BubbleFace(state: .idle, diameter: 64).frame(width: 72, height: 80)
                        Toggle(isOn: Binding(get: { bubbleOn }, set: { NotificationCenter.default.post(name: .quillExplicitVisibility, object: nil); settings.value.bubbleActive = true; settings.value.bubbleVisible = $0 })) {
                            VStack(alignment: .leading, spacing: 5) { CopyText(bubbleOn ? "home.toggle.on_title" : "home.toggle.off_title").font(QuillTheme.serif(24)); HelpText(key: bubbleOn ? "home.toggle.on_sub" : "home.toggle.off_sub") }
                        }.toggleStyle(.switch)
                    }
                    if dictation.state == .recording { InkMeter(amplitude: dictation.level); Text(formatDuration(dictation.elapsed)).monospacedDigit() }
                    if dictation.state == .transcribing { HStack { ProgressView().controlSize(.small); CopyText("mac.status.transcribing") } }
                    if !dictation.message.isEmpty { CopyText(dictation.message).foregroundStyle(dictation.state == .error ? QuillTheme.error : .secondary).fixedSize(horizontal: false, vertical: true) }
                    if !dictation.transcript.isEmpty { Text(dictation.transcript).textSelection(.enabled) }
                    if dictation.state == .recording { QuillButton(key: "mac.dictation.stop") { dictation.stop() } }
                    if dictation.state != .idle { HStack { Button(Copy.text("action.cancel")) { dictation.cancel() }; Spacer(); if dictation.state == .error { Button(Copy.text("mac.action.retry")) { dictation.retry() } }; if !dictation.transcript.isEmpty { Button(Copy.text("history.sheet.copy")) { dictation.copyTranscript() } } } }
                }
                if bubbleOn {
                    SectionLabel(key: "home.group.today")
                    QuillCard {
                        let today = history.entries.filter { Calendar.current.isDateInToday($0.created) }
                        HStack(spacing: 14) {
                            Image(systemName: "waveform").foregroundStyle(QuillTheme.brandInk(scheme == .dark)).frame(width: 40, height: 40).background(QuillTheme.soft(scheme == .dark), in: RoundedRectangle(cornerRadius: 8))
                            VStack(alignment: .leading, spacing: 4) { Text(Copy.text("mac.home.count", String(today.count))); Text(Copy.text("mac.home.words", String(today.reduce(0) { $0 + $1.text.split(whereSeparator: \.isWhitespace).count }))).font(QuillTheme.sans(13)).foregroundStyle(.secondary) }; Spacer()
                        }
                        Divider()
                        Button(action: openHistory) {
                            HStack(spacing: 14) { Image(systemName: "clock").foregroundStyle(.secondary).frame(width: 40, height: 40); VStack(alignment: .leading, spacing: 4) { CopyText("home.today.last_used"); if let last = today.first { Text(last.created, style: .time).font(QuillTheme.sans(13)).foregroundStyle(.secondary) } else { HelpText(key: "home.today.last_used_sub") } }; Spacer(); Image(systemName: "chevron.right").foregroundStyle(.secondary) }
                        }.buttonStyle(.plain)
                    }
                    SectionLabel(key: "home.group.tip")
                    QuillCard { CopyText("home.tip.title").fontWeight(.medium); HelpText(key: "mac.home.tip"); Label(Copy.text("mac.hotkey.label"), systemImage: "keyboard") }
                } else {
                    QuillCard { Label(Copy.text("home.paused.banner_title"), systemImage: "info.circle").fontWeight(.medium); HelpText(key: "home.paused.banner_text") }
                    CopyText("home.paused.footer").foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.top, 70)
                }
                if settings.value.privateMode { Label(Copy.text("settings.privacy.private_mode"), systemImage: "eye.slash").foregroundStyle(.secondary) }
            }.padding(28).frame(maxWidth: 760)
        }.frame(maxWidth: .infinity)
    }
}
func formatDuration(_ seconds: Double) -> String { String(format: "%d:%02d", Int(seconds) / 60, Int(seconds) % 60) }

struct HistoryView: View {
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var dictation: Dictation
    @State private var confirmClear = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack { CopyText("history.title").font(QuillTheme.serif()); Spacer(); Button(Copy.text("history.clear_all"), role: .destructive) { confirmClear = true }.disabled(history.entries.isEmpty) }
                if history.storageFailed { CopyText("mac.error.storage").foregroundStyle(QuillTheme.error) }
                if history.entries.isEmpty {
                    QuillCard {
                        HStack { Spacer(); PermissionArt(symbol: "clock.arrow.circlepath"); Spacer() }
                        CopyText(settings.value.shouldSaveHistory ? "history.empty.on_title" : "history.empty.off_title").font(QuillTheme.serif(24))
                        HelpText(key: settings.value.shouldSaveHistory ? "mac.history.empty" : "mac.history.off")
                        if settings.value.shouldSaveHistory { QuillButton(key: "history.empty.on_cta") { settings.value.bubbleActive = true; settings.value.bubbleVisible = true; NSApp.hide(nil) } }
                    }
                }
                ForEach(history.entries) { entry in
                    QuillCard {
                        HStack { Text(entry.created, style: .date); Text(entry.created, style: .time); Spacer(); Text(entry.provider.name) }.font(QuillTheme.sans(12)).foregroundStyle(.secondary)
                        Text(entry.text).textSelection(.enabled)
                        HStack { Text(entry.appName).foregroundStyle(.secondary); Spacer(); Button(Copy.text("history.sheet.copy")) { ClipboardLease.write(entry.text) }; Button(Copy.text("history.sheet.delete"), role: .destructive) { history.delete(entry.id, settings: settings.value) } }
                    }
                }
            }.padding(28).frame(maxWidth: 760)
        }.frame(maxWidth: .infinity).onAppear { history.refresh(settings: settings.value) }
        .confirmationDialog(Copy.text("history.clear.title"), isPresented: $confirmClear) { Button(Copy.text("history.clear.confirm"), role: .destructive) { history.clear() } } message: { Text(Copy.text("history.clear.body", String(history.entries.count))) }
    }
}
