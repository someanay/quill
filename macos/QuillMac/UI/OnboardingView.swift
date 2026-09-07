import SwiftUI
import QuillCore

struct OnboardingView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
    @Environment(\.colorScheme) private var scheme
    @State private var step = 0
    @State private var warningReady = false
    @State private var accepted = false
    init() {
        var first = 0
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if AppPaths.testMode, let index = arguments.firstIndex(of: "--ui-onboarding-stage"), arguments.indices.contains(index + 1), let value = Int(arguments[index + 1]), (0...8).contains(value) { first = value }
        #endif
        _step = State(initialValue: first)
    }
    private let titles = ["onboarding.splash.tagline","onboarding.welcome.title","compliance.key.required_title","onboarding.key.title","onboarding.checklist.title","onboarding.mictest.title","onboarding.overlay.title","onboarding.accessibility.enable_title","onboarding.allset.title"]
    var body: some View {
        GeometryReader { geometry in
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if step < 2 { intro(minHeight: max(0, geometry.size.height - 64)) }
                else {
                HStack { Wordmark(); Spacer(); if step > 1 { Button(Copy.text("action.back")) { dictation.stopMicTest(); step -= 1 } } }
                if step != 3 { HStack { Spacer(); PermissionArt(symbol: symbol); Spacer() }.padding(.top, 10) }
                CopyText(titles[step]).font(QuillTheme.serif(32)).fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("onboarding-title")
                Group {
                    switch step {
                    case 2:
                        HelpText(key: "compliance.key.required_body", size: 14)
                        QuillCard { HelpText(key: "onboarding.key.warning.free_tier"); HelpText(key: "onboarding.key.warning.usage"); HelpText(key: "compliance.privacy.promise") }
                        QuillButton(key: warningReady ? "onboarding.key.warning.accept" : "onboarding.key.warning.reading") { step = 3 }.disabled(!warningReady)
                        QuillButton(key: "action.not_now", primary: false) { step = 1 }
                    case 3:
                        HelpText(key: "onboarding.key.body", size: 14)
                        KeyEditor(onboarding: true) { step = 4 }
                    case 4:
                        QuillCard {
                            checklist("onboarding.checklist.key", done: keys.active(settings.value.provider, settings: settings.value) != nil)
                            checklist("onboarding.checklist.mic", done: dictation.microphoneGranted)
                            checklist("onboarding.checklist.overlay", done: settings.value.bubbleActive)
                            checklist("onboarding.checklist.accessibility", done: dictation.accessibilityGranted)
                        }
                        HelpText(key: "compliance.rationale.mic")
                        QuillButton(key: dictation.microphoneGranted ? "onboarding.checklist.cta" : "onboarding.mic.cta") {
                            Task { let allowed = dictation.microphoneGranted ? true : await AudioCapture.requestPermission(); if allowed { dictation.refreshPermissions(); step = 5 } else { dictation.message = "mac.error.microphone" } }
                        }
                        if !dictation.microphoneGranted { Button(Copy.text("onboarding.mic.open_settings")) { SystemLinks.open("Privacy_Microphone") } }
                    case 5:
                        BodyCopy(key: "onboarding.mictest.body")
                        HStack { Spacer(); InkMeter(amplitude: dictation.level); Spacer() }
                        if dictation.testHeard { Label(Copy.text("onboarding.mictest.heard"), systemImage: "checkmark.circle.fill").foregroundStyle(QuillTheme.success) }
                        QuillButton(key: dictation.microphoneTest ? "mac.mic.stop_test" : "mac.mic.test") { if dictation.microphoneTest { dictation.stopMicTest() } else { dictation.startMicTest() } }
                        QuillButton(key: "onboarding.mictest.cta") { dictation.stopMicTest(); step = 6 }.disabled(!dictation.microphoneGranted || !dictation.testCaptureSucceeded)
                    case 6:
                        BodyCopy(key: "mac.onboarding.overlay")
                        QuillButton(key: "mac.bubble.show") { settings.value.bubbleActive = true; settings.value.bubbleVisible = true; step = 7 }
                    case 7:
                        QuillCard { HelpText(key: "mac.accessibility.disclosure"); Toggle(Copy.text("mac.accessibility.consent"), isOn: $accepted) }
                        QuillButton(key: "onboarding.accessibility.enable_cta") { settings.value.accessibilityConsent = true; TextInjector.requestPermission(); SystemLinks.open("Privacy_Accessibility") }.disabled(!accepted)
                        if dictation.accessibilityGranted { QuillButton(key: "action.continue") { settings.value.accessibilityConsent = true; step = 8 }.disabled(!accepted) }
                    default:
                        BodyCopy(key: "onboarding.allset.body")
                        QuillCard { HStack(spacing: 14) { BubbleFace(state: .idle, diameter: 40); CopyText("onboarding.allset.look").font(QuillTheme.sans(14)) } }
                        QuillButton(key: "onboarding.allset.cta") { finish(); NSApp.hide(nil) }
                        QuillButton(key: "onboarding.allset.home_link", primary: false) { finish() }
                    }
                }
                if !dictation.message.isEmpty { CopyText(dictation.message).foregroundStyle(QuillTheme.error) }
                HStack(spacing: 6) { Spacer(); ForEach(1..<9) { index in Capsule().fill(index == step ? QuillTheme.brand : QuillTheme.brand.opacity(0.15)).frame(width: index == step ? 22 : 7, height: 7) }; Spacer() }.accessibilityHidden(true)
                }
            }.padding(32).frame(maxWidth: 590)
        }.frame(maxWidth: .infinity)
        }.task(id: step) {
            #if DEBUG
            // Presentation coverage only: no keys or permissions are fabricated,
            // and screenshots must not start the physical microphone or advance setup.
            if AppPaths.testMode && ProcessInfo.processInfo.arguments.contains("--ui-onboarding-stage") { return }
            #endif
            if step == 0 { try? await Task.sleep(nanoseconds: 1_200_000_000); if !Task.isCancelled { step = 1 } }
            if step == 2 { warningReady = false; try? await Task.sleep(nanoseconds: 3_000_000_000); if !Task.isCancelled { warningReady = true } }
            if step == 5 { dictation.startMicTest() }
        }.onDisappear { dictation.stopMicTest() }
    }
    @ViewBuilder private func intro(minHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            Wordmark()
            if step == 0 {
                CopyText("onboarding.splash.tagline").font(QuillTheme.sans(14)).foregroundStyle(QuillTheme.muted(scheme == .dark))
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 12).accessibilityIdentifier("onboarding-title")
            } else {
                CopyText("onboarding.welcome.title").font(QuillTheme.serif(24)).multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true).padding(.top, 24).accessibilityIdentifier("onboarding-title")
                BodyCopy(key: "onboarding.welcome.body").multilineTextAlignment(.center).padding(.top, 10)
                HStack(spacing: 6) {
                    Circle().fill(scheme == .dark ? QuillTheme.brandInk(true) : QuillTheme.brand).frame(width: 8, height: 8).accessibilityHidden(true)
                    CopyText("compliance.privacy.welcome_pill").font(QuillTheme.sans(12)).foregroundStyle(QuillTheme.liveInk(scheme == .dark))
                }.padding(.horizontal, 10).padding(.vertical, 5).background(QuillTheme.soft(scheme == .dark), in: Capsule()).padding(.top, 20)
                QuillButton(key: "onboarding.welcome.cta") { step = 2 }.padding(.top, 28)
            }
            Spacer(minLength: 24)
            if step == 1 {
                HStack(spacing: 8) {
                    CopyText("onboarding.welcome.already").font(QuillTheme.sans(14)).foregroundStyle(QuillTheme.muted(scheme == .dark)).accessibilityIdentifier("onboarding.welcome.already")
                    Button(Copy.text("onboarding.welcome.open")) { settings.value.onboardingComplete = true }
                        .buttonStyle(.plain).font(QuillTheme.sans(14)).foregroundStyle(QuillTheme.brandInk(scheme == .dark)).accessibilityIdentifier("onboarding.welcome.open")
                }.padding(.bottom, 4)
            }
        }.frame(maxWidth: .infinity, minHeight: minHeight)
    }
    private var symbol: String { ["pencil.tip","waveform","key.fill","key.fill","checklist","mic.fill","rectangle.on.rectangle","accessibility","checkmark"][step] }
    private func checklist(_ key: String, done: Bool) -> some View { HStack { CopyText(key); Spacer(); Image(systemName: done ? "checkmark.circle.fill" : "circle").foregroundStyle(done ? QuillTheme.success : Color.secondary) } }
    private func finish() { settings.value.onboardingComplete = true; settings.value.bubbleActive = true; settings.value.bubbleVisible = true; dictation.configureLogin() }
}

enum SystemLinks {
    static func open(_ pane: String) { if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?\(pane)") { NSWorkspace.shared.open(url) } }
}
