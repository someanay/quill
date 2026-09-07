import SwiftUI
import QuillCore

struct MainView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    @Environment(\.colorScheme) private var scheme
    @State private var tab = "home"
    var body: some View {
        VStack(spacing: 0) {
            if settings.value.onboardingComplete {
                Group {
                    switch tab {
                    case "history": HistoryView()
                    case "settings": SettingsView()
                    default: HomeView(openSettings: { tab = "settings" })
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
                Divider()
                HStack {
                    nav("home", icon: "house"); nav("history", icon: "clock.arrow.circlepath"); nav("settings", icon: "gearshape")
                }.padding(12).background(QuillTheme.surface(scheme == .dark))
            } else { OnboardingView() }
        }.font(QuillTheme.sans()).foregroundStyle(QuillTheme.ink(scheme == .dark))
        .background(QuillTheme.paper(scheme == .dark)).tint(QuillTheme.brand)
        .preferredColorScheme(settings.value.appTheme == "Dark" ? .dark : settings.value.appTheme == "Light" ? .light : nil)
        .frame(minWidth: 510, idealWidth: 600, minHeight: 620, idealHeight: 790)
        .onChange(of: settings.value.privateMode) { value in if value { dictation.cancel() } }
        .onChange(of: settings.value.bootRestore) { _ in dictation.configureLogin() }
    }
    private func nav(_ id: String, icon: String) -> some View {
        Button { tab = id } label: {
            VStack(spacing: 4) { Image(systemName: icon).font(.system(size: 19)).frame(width: 54, height: 30).background(tab == id ? QuillTheme.brand.opacity(0.12) : .clear, in: Capsule()); CopyText("nav.\(id)").font(QuillTheme.sans(12)) }.frame(maxWidth: .infinity).frame(minHeight: 48).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(tab == id ? QuillTheme.brand : .secondary).accessibilityIdentifier("nav.\(id)")
    }
}

struct HomeView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var history: HistoryStore
    @EnvironmentObject var dictation: Dictation
    var openSettings: () -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack { Wordmark(); Spacer(); Button(action: openSettings) { Image(systemName: "gearshape") }.accessibilityLabel(Copy.text("a11y.settings")) }
                if settings.value.accessibilityEnabledOnce && !dictation.accessibilityGranted {
                    QuillCard { Label(Copy.text("home.banner.a11y_off_text"), systemImage: "exclamationmark.shield").foregroundStyle(QuillTheme.error); Button(Copy.text("onboarding.accessibility.enable_cta")) { SystemLinks.open("Privacy_Accessibility") } }
                }
                QuillCard {
                    Toggle(isOn: Binding(get: { settings.value.bubbleActive && settings.value.bubbleVisible }, set: { settings.value.bubbleActive = true; settings.value.bubbleVisible = $0 })) {
                        VStack(alignment: .leading, spacing: 5) { CopyText(settings.value.bubbleVisible && settings.value.bubbleActive ? "home.toggle.on_title" : "home.toggle.off_title").font(QuillTheme.serif(24)); HelpText(key: settings.value.bubbleVisible && settings.value.bubbleActive ? "home.toggle.on_sub" : "home.toggle.off_sub") }
                    }.toggleStyle(.switch)
                    if dictation.state == .recording { InkMeter(amplitude: dictation.level); Text(formatDuration(dictation.elapsed)).monospacedDigit() }
                    if dictation.state == .transcribing { HStack { ProgressView().controlSize(.small); CopyText("mac.status.transcribing") } }
                    if !dictation.message.isEmpty { CopyText(dictation.message).foregroundStyle(dictation.state == .error ? QuillTheme.error : .secondary).fixedSize(horizontal: false, vertical: true) }
                    if !dictation.transcript.isEmpty { Text(dictation.transcript).textSelection(.enabled) }
                    QuillButton(key: dictation.state == .recording ? "mac.dictation.stop" : "mac.dictation.start") { dictation.toggle() }.disabled(dictation.state == .transcribing)
                    if dictation.state != .idle { HStack { Button(Copy.text("action.cancel")) { dictation.cancel() }; Spacer(); if dictation.state == .error { Button(Copy.text("mac.action.retry")) { dictation.retry() } }; if !dictation.transcript.isEmpty { Button(Copy.text("history.sheet.copy")) { dictation.copyTranscript() } } } }
                }
                SectionLabel(key: "home.group.today")
                QuillCard {
                    let today = history.entries.filter { Calendar.current.isDateInToday($0.created) }
                    Text(Copy.text("mac.home.count", String(today.count))).font(QuillTheme.serif(26))
                    Text(Copy.text("mac.home.words", String(today.reduce(0) { $0 + $1.text.split(whereSeparator: \.isWhitespace).count }))).foregroundStyle(.secondary)
                    if let last = today.first { Text(last.created, style: .time).foregroundStyle(.secondary) }
                }
                SectionLabel(key: "home.group.tip")
                QuillCard { CopyText("home.tip.title").fontWeight(.medium); HelpText(key: "mac.home.tip"); Label(Copy.text("mac.hotkey.label"), systemImage: "keyboard") }
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
                        if settings.value.shouldSaveHistory { QuillButton(key: "history.empty.on_cta") { dictation.start() } }
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
