import SwiftUI
import QuillCore

struct OnboardingView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    @Environment(\.colorScheme) private var scheme
    @State private var step = 0
    @State private var accepted = false
    @State private var practice = ""
    private let stages = ["welcome", "privacy", "provider", "audio", "access", "shortcut", "ready"]
    init() {
        var initial = 0
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if AppPaths.testMode, let index = args.firstIndex(of: "--ui-onboarding-stage"), args.indices.contains(index + 1), let value = Int(args[index + 1]), (0...6).contains(value) { initial = value }
        #endif
        _step = State(initialValue: initial)
    }
    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 0) {
                if geometry.size.width >= 900 { ScrollView { progressRail }.frame(width: 200); Divider() }
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        HStack { if geometry.size.width < 900 { Wordmark() }; Spacer(); if step > 0 { Button(Copy.text("action.back")) { step -= 1 }.accessibilityIdentifier("action.back") } }
                        if step > 0 { Text(Copy.text("desktop.setup.progress", String(step + 1), "7")).font(QuillTheme.sans(12)).foregroundStyle(.secondary) }
                        Text(Copy.text(title).replacingOccurrences(of: ", ", with: step == 0 ? ",\n" : ", ")).font(QuillTheme.serif(step == 0 && geometry.size.width >= 900 ? 64 : 34)).fontWeight(step == 0 ? .semibold : .regular).fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("onboarding-title")
                        stepContent(contentWidth: geometry.size.width - (geometry.size.width >= 900 ? 200 : 0) - (geometry.size.width < 700 ? 48 : 88))
                        if !dictation.message.isEmpty { CopyText(dictation.message).foregroundStyle(QuillTheme.error).fixedSize(horizontal: false, vertical: true) }
                    }.padding(geometry.size.width < 700 ? 24 : 44).frame(maxWidth: .infinity, alignment: .leading)
                        .frame(minHeight: step == 0 ? geometry.size.height : 0, alignment: .center)
                }.frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityIdentifier("onboarding-content")
                if step > 0 && geometry.size.width >= 1200 {
                    Divider()
                    DesktopExample(title: "desktop.setup.companion.\(stages[step])", bodyKey: "desktop.setup.companion_body.\(stages[step])")
                        .frame(width: geometry.size.width * 0.3, height: geometry.size.height).background(QuillTheme.paper(scheme == .dark))
                }
            }
        }.onChange(of: step) { _ in dictation.stopMicTest() }.onDisappear { dictation.stopMicTest() }
    }
    private var title: String {
        ["desktop.setup.welcome_title", "compliance.key.required_title", "desktop.setup.provider_title", "desktop.setup.audio_title", "desktop.setup.access_title", "desktop.setup.shortcut_title", "desktop.setup.ready_title"][step]
    }
    private var progressRail: some View {
        VStack(alignment: .leading, spacing: 24) {
            Wordmark().padding(.horizontal, 18)
            VStack(spacing: 4) {
                ForEach(0..<stages.count, id: \.self) { index in
                    Button { step = index } label: {
                        HStack(spacing: 14) { Image(systemName: index <= step ? "circle.fill" : "circle").font(.system(size: 11)); CopyText("desktop.setup.stage.\(stages[index])"); Spacer(minLength: 0) }
                            .font(QuillTheme.sans(14)).padding(12).contentShape(Rectangle())
                    }.buttonStyle(.plain).disabled(index > step)
                        .foregroundStyle(index == step ? QuillTheme.brandInk(scheme == .dark) : QuillTheme.ink(scheme == .dark))
                }
            }
            Spacer(minLength: 12)
            HelpText(key: "desktop.setup.local").padding(.horizontal, 12)
        }.padding(.horizontal, 12).padding(.vertical, 30)
    }
    @ViewBuilder private func stepContent(contentWidth: CGFloat) -> some View {
        switch step {
        case 0:
            if contentWidth >= 700 {
                HStack(alignment: .top, spacing: 36) {
                    welcomeInstructions.frame(width: (contentWidth - 36) / 2, alignment: .leading)
                    welcomeExample.frame(width: (contentWidth - 36) / 2, alignment: .leading)
                }
            } else {
                VStack(alignment: .leading, spacing: 26) { welcomeInstructions; welcomeExample }
            }
            HStack(spacing: 18) {
                QuillButton(key: "onboarding.welcome.cta") { step = 1 }.fixedSize(horizontal: true, vertical: false)
                QuillButton(key: "onboarding.welcome.open", primary: false) { settings.value.onboardingComplete = true }.fixedSize(horizontal: true, vertical: false)
            }.padding(.top, 18)
            HelpText(key: "desktop.setup.privacy_line", size: 13).padding(.top, 8)
        case 1:
            BodyCopy(key: "desktop.setup.privacy_body")
            QuillCard { HelpText(key: "onboarding.key.warning.free_tier", size: 14); HelpText(key: "onboarding.key.warning.usage", size: 14); HelpText(key: "compliance.privacy.promise", size: 14) }
            QuillButton(key: "onboarding.key.warning.accept") { step = 2 }
            QuillButton(key: "action.not_now", primary: false) { step = 0 }
        case 2:
            BodyCopy(key: "desktop.setup.provider_body")
            KeyEditor(onboarding: true) { step = 3 }
        case 3:
            BodyCopy(key: "mac.setup.audio_body")
            QuillCard {
                InkMeter(amplitude: dictation.level).frame(maxWidth: .infinity)
                if dictation.testHeard { Label(Copy.text("onboarding.mictest.heard"), systemImage: "checkmark.circle.fill").foregroundStyle(QuillTheme.success) }
                QuillButton(key: dictation.microphoneTest ? "mac.mic.stop_test" : "mac.mic.test") {
                    if dictation.microphoneTest { dictation.stopMicTest() }
                    else { Task { if !dictation.microphoneGranted { _ = await AudioCapture.requestPermission(); dictation.refreshPermissions() }; if dictation.microphoneGranted { dictation.startMicTest() } } }
                }
                Button(Copy.text("onboarding.mic.open_settings")) { SystemLinks.open("Privacy_Microphone") }
            }
            QuillButton(key: "action.continue") { step = 4 }.disabled(!dictation.microphoneGranted || !dictation.testCaptureSucceeded).accessibilityIdentifier("onboarding.mictest.cta")
        case 4:
            BodyCopy(key: "mac.setup.access_body")
            QuillCard { HelpText(key: "mac.accessibility.disclosure", size: 14); Toggle(Copy.text("mac.accessibility.consent"), isOn: $accepted) }
            QuillButton(key: "onboarding.accessibility.enable_cta") { settings.value.accessibilityConsent = true; TextInjector.requestPermission(); SystemLinks.open("Privacy_Accessibility") }.disabled(!accepted)
            QuillButton(key: "action.continue") { settings.value.accessibilityConsent = true; step = 5 }.disabled(!accepted || !dictation.accessibilityGranted)
        case 5:
            BodyCopy(key: "desktop.setup.shortcut_body")
            QuillCard { if dictation.shortcutAvailable { CopyText("mac.hotkey.label").font(QuillTheme.sans(26)) } else { HelpText(key: "mac.setup.shortcut_unavailable", size: 14, bad: true) }; HelpText(key: "mac.setup.controls", size: 14); Toggle(Copy.text("desktop.setup.bubble_choice"), isOn: $settings.value.bubbleActive) }
            CopyText("desktop.practice.title").font(QuillTheme.sans(17))
            HelpText(key: "desktop.practice.body")
            TextEditor(text: $practice).font(QuillTheme.sans(16)).frame(minHeight: 160).border(QuillTheme.line(scheme == .dark)).accessibilityLabel(Copy.text("desktop.practice.title"))
            if dictation.state == .recording { InkMeter(amplitude: dictation.level) }
            if dictation.state == .transcribing { ProgressView() }
            QuillButton(key: "action.continue") { step = 6 }.disabled(dictation.state == .recording || dictation.state == .transcribing)
        default:
            BodyCopy(key: "desktop.setup.ready_body")
            QuillButton(key: "desktop.setup.practice") { finish() }
            QuillButton(key: "mac.setup.finish", primary: false) { finish(); NSApp.hide(nil) }
        }
    }
    private var welcomeInstructions: some View {
        VStack(alignment: .leading, spacing: 24) {
            BodyCopy(key: "desktop.setup.welcome_body")
            ShortcutKeycaps()
            HelpText(key: "desktop.setup.shortcut_caption", size: 14)
        }
    }
    private var welcomeExample: some View {
        VStack(alignment: .leading, spacing: 12) {
            HelpText(key: "desktop.demo.label", size: 13)
            QuillCard { CopyText("desktop.demo.spoken").font(QuillTheme.sans(20)).lineSpacing(6).frame(minHeight: 130, alignment: .topLeading) }
        }
    }
    private func finish() { settings.value.onboardingComplete = true; settings.value.bubbleVisible = settings.value.bubbleActive; dictation.configureLogin() }
}

struct DesktopExample: View {
    let title: String
    let bodyKey: String
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 22) {
            CopyText(title).font(QuillTheme.serif(30)).fixedSize(horizontal: false, vertical: true)
            BodyCopy(key: bodyKey)
            QuillCard { HelpText(key: "desktop.demo.label"); CopyText("desktop.demo.spoken").font(QuillTheme.serif(23)); Divider(); Label(Copy.text("mac.hotkey.label"), systemImage: "keyboard") }
        }.padding(32).frame(maxWidth: .infinity, alignment: .leading) }
    }
}

enum SystemLinks {
    static func open(_ pane: String) { if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?\(pane)") { NSWorkspace.shared.open(url) } }
}
