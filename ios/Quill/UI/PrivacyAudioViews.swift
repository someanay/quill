import SwiftUI
import QuillCore

struct AudioSettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.audio.title"))
            QSection(title: model.c("settings.audio.start_stop"))
            QCard { VStack(alignment: .leading, spacing: 12) { Text(model.c("settings.audio.trigger_value")); Text(model.c("ios.audio.trigger_help")).font(Q.sans(13)).foregroundStyle(Q.muted) } }
            QSection(title: model.c("settings.audio.max_length"))
            QCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(model.c("ios.audio.cap", String(model.settings.sessionCapSeconds)))
                    Slider(value: Binding(get: { Double(model.settings.sessionCapSeconds) }, set: { model.settings.sessionCapSeconds = Int($0) }), in: 30...600, step: 30).accessibilityLabel(model.c("settings.audio.max_length"))
                }
            }
            QCard { VStack(alignment: .leading, spacing: 8) { Text(model.c("settings.audio.silence")); Text(model.c("ios.audio.silence_detail")).font(Q.sans(13)).foregroundStyle(Q.muted) } }
            QBanner(text: model.c("settings.audio.silence_banner"))
            QSection(title: model.c("settings.audio.format"))
            QCard { Text(model.c("ios.audio.format")) }
            QBanner(text: model.c("ios.audio.route"), icon: "mic")
            QButton(title: model.c("onboarding.mic.open_settings"), secondary: true) { openSettings() }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct VisualSettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.bubble.title"))
            QCard { RecordingBubble(size: model.settings.bubbleSize == "Small" ? 56 : model.settings.bubbleSize == "Large" ? 88 : 72).frame(maxWidth: .infinity).padding(.vertical, 25) }
            QSection(title: model.c("settings.bubble.size"))
            QCard { Picker(model.c("settings.bubble.size"), selection: $model.settings.bubbleSize) { Text(model.c("ios.size.small")).tag("Small"); Text(model.c("ios.size.medium")).tag("Medium"); Text(model.c("ios.size.large")).tag("Large") }.pickerStyle(.segmented) }
            QSection(title: model.c("settings.bubble.theme"))
            QCard { Picker(model.c("settings.bubble.theme"), selection: $model.settings.theme) { Text(model.c("ios.theme.system")).tag("Match system"); Text(model.c("ios.theme.light")).tag("Light"); Text(model.c("ios.theme.dark")).tag("Dark") }.pickerStyle(.segmented) }
            QBanner(text: model.c("ios.visual.help"))
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct PrivacySettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var clear = false
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.privacy.title"))
            QBanner(title: model.c("settings.privacy.banner_title"), text: model.c("ios.privacy.direct"), icon: "lock.shield")
            QCard {
                VStack(alignment: .leading, spacing: 12) {
                    Toggle(model.c("settings.privacy.private_mode"), isOn: Binding(get: { model.settings.privateMode }, set: model.setPrivateMode)).frame(minHeight: 48)
                    Text(model.c("ios.privacy.private_detail")).font(Q.sans(13)).foregroundStyle(Q.muted)
                    Divider()
                    Toggle(model.c("settings.privacy.history"), isOn: $model.settings.historyEnabled).disabled(model.settings.privateMode).frame(minHeight: 48)
                    Picker(model.c("settings.privacy.history_retention"), selection: $model.settings.retentionMinutes) {
                        Text(model.c("settings.privacy.history_retention.hour")).tag(60)
                        Text(model.c("settings.privacy.history_retention.day")).tag(1440)
                        Text(model.c("settings.privacy.history_retention.week")).tag(10080)
                        Text(model.c("settings.privacy.history_retention.month")).tag(43200)
                        Text(model.c("settings.privacy.history_retention.forever")).tag(0)
                    }.pickerStyle(.menu).onChange(of: model.settings.retentionMinutes) { _, _ in model.refreshHistory() }
                }
            }
            QButton(title: model.c("ios.history.clear"), icon: "trash", secondary: true) { clear = true }
            QButton(title: model.c("ios.transfer.clear"), secondary: true) { model.clearTransfer() }
            QSection(title: model.c("settings.privacy.permissions"))
            QCard { VStack { Button { openSettings() } label: { QRow(title: model.c("settings.privacy.mic"), icon: "mic", value: model.c(model.microphoneGranted ? "ios.permission.allowed" : "ios.permission.off")) }; Divider(); NavigationLink { KeyboardHelpView() } label: { QRow(title: model.c("ios.keyboard.title"), icon: "keyboard") } } }
            QSection(title: model.c("settings.privacy.diagnostics"))
            QCard { Toggle(model.c("ios.diagnostics.opt_in"), isOn: $model.settings.crashReportingEnabled).frame(minHeight: 48) }
            Text(model.c("ios.diagnostics.body")).font(Q.sans(13)).foregroundStyle(Q.muted)
            NavigationLink(model.c("ios.diagnostics.review")) { DiagnosticsView() }.frame(minHeight: 48)
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar).confirmationDialog(model.c("ios.history.clear_confirm"), isPresented: $clear, titleVisibility: .visible) { Button(model.c("ios.history.clear"), role: .destructive) { model.clearHistory() } }
    }
}

struct DiagnosticsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("ios.diagnostics.review"))
            QBanner(text: model.c("ios.diagnostics.body"), icon: "shield")
            QCard { Text(model.diagnostics).font(.system(.body, design: .monospaced)).textSelection(.enabled) }
            if model.settings.crashReportingEnabled { ShareLink(item: model.diagnostics) { Label(model.c("ios.diagnostics.share"), systemImage: "square.and.arrow.up").frame(minHeight: 48) } }
            else { Text(model.c("ios.diagnostics.enable")).foregroundStyle(Q.muted) }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct AdvancedSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var reset = false
    @State private var clear = false
    var body: some View {
        QPage {
            QHeading(title: model.c("settings.advanced.title"))
            QField(title: model.c("settings.advanced.endpoint"), text: $model.settings.customBaseURL)
            QBanner(text: model.c("ios.keys.endpoint_binding"), icon: "lock")
            QCard { VStack(alignment: .leading, spacing: 10) { Text(model.c("settings.advanced.timeout")); Text(model.c("ios.advanced.timeout_detail")).font(Q.sans(13)).foregroundStyle(Q.muted) } }
            QBanner(text: model.c("ios.advanced.background"))
            QSection(title: model.c("settings.advanced.reset"))
            QButton(title: model.c("settings.advanced.reset_settings"), secondary: true) { reset = true }
            QButton(title: model.c("settings.advanced.clear"), icon: "trash", destructive: true) { clear = true }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .confirmationDialog(model.c("settings.advanced.reset_confirm_title"), isPresented: $reset, titleVisibility: .visible) {
                Button(model.c("settings.advanced.reset_settings"), role: .destructive) { model.cancel(); var defaults = QuillSettings(); defaults.onboardingComplete = true; model.settings = defaults }
            }.confirmationDialog(model.c("settings.advanced.clear_confirm_title"), isPresented: $clear, titleVisibility: .visible) {
                Button(model.c("settings.advanced.clear_confirm_cta"), role: .destructive) { model.reset() }
            } message: { Text(model.c("settings.advanced.clear_confirm_body")) }
    }
}

struct AboutView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            Wordmark().padding(.vertical, 20)
            QHeading(title: model.c("settings.row.about"))
            Text(model.c("ios.about.version", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"))
            QBanner(text: model.c("ios.privacy.direct"), icon: "lock.shield")
            Text(model.c("ios.about.provider_terms")).font(Q.sans(13)).foregroundStyle(Q.muted)
            NavigationLink(model.c("ios.about.privacy")) { PrivacyDetailsView() }.frame(minHeight: 48)
            NavigationLink(model.c("ios.about.licenses")) { LicenseView() }.frame(minHeight: 48)
            NavigationLink(model.c("ios.keyboard.title")) { KeyboardHelpView() }.frame(minHeight: 48)
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct PrivacyDetailsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage {
            QHeading(title: model.c("ios.about.privacy"))
            Text(model.c("ios.privacy.direct"))
            Text(model.c("ios.keys.security"))
            Text(model.c("ios.privacy.private_detail"))
            Text(model.c("ios.keyboard.full_access_body"))
            Text(model.c("ios.diagnostics.body"))
            Text(model.c("settings.vocab.body"))
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct LicenseView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        QPage { QHeading(title: model.c("ios.about.licenses")); Text((Bundle.main.url(forResource: "FONT-LICENSE", withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) }) ?? "").font(Q.sans(13)).textSelection(.enabled) }
    }
}
