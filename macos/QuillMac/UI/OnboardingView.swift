import SwiftUI
import QuillCore

struct OnboardingView: View {
    @EnvironmentObject var settings: SettingsStore
    @EnvironmentObject var keys: KeychainStore
    @EnvironmentObject var dictation: Dictation
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
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack { Wordmark(); Spacer(); if step > 1 { Button(Copy.text("action.back")) { dictation.stopMicTest(); step -= 1 } } }
                if step != 3 { HStack { Spacer(); PermissionArt(symbol: symbol); Spacer() }.padding(.top, 10) }
                CopyText(titles[step]).font(QuillTheme.serif(32)).fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("onboarding-title")
                Group {
                    switch step {
                    case 0: QuillButton(key: "action.continue") { step = 1 }
                    case 1:
                        BodyCopy(key: "onboarding.welcome.body")
                        QuillCard { Label(Copy.text("compliance.privacy.welcome_pill"), systemImage: "lock.shield") }
                        QuillButton(key: "onboarding.welcome.cta") { step = 2 }
                        Button(Copy.text("onboarding.welcome.open")) { if keys.active(settings.value.provider, settings: settings.value) != nil { step = 4 } else { step = 3 } }
                    case 2:
                        HelpText(key: "compliance.key.required_body", size: 14)
                        QuillCard { HelpText(key: "onboarding.key.warning.free_tier"); HelpText(key: "onboarding.key.warning.usage"); HelpText(key: "compliance.privacy.promise") }
                        QuillButton(key: warningReady ? "onboarding.key.warning.accept" : "onboarding.key.warning.reading") { step = 3 }.disabled(!warningReady)
                        QuillButton(key: "action.not_now", primary: false) { step = 1 }
                    case 3: KeyEditor(onboarding: true) { step = 4 }
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
                        QuillButton(key: "onboarding.mictest.cta") { dictation.stopMicTest(); step = 6 }.disabled(!dictation.testHeard)
                    case 6:
                        BodyCopy(key: "mac.onboarding.overlay")
                        QuillButton(key: "mac.bubble.show") { settings.value.bubbleActive = true; settings.value.bubbleVisible = true; step = 7 }
                    case 7:
                        QuillCard { HelpText(key: "mac.accessibility.disclosure"); Toggle(Copy.text("mac.accessibility.consent"), isOn: $accepted) }
                        QuillButton(key: "onboarding.accessibility.enable_cta") { settings.value.accessibilityConsent = true; TextInjector.requestPermission(); SystemLinks.open("Privacy_Accessibility") }.disabled(!accepted)
                        if dictation.accessibilityGranted { QuillButton(key: "action.continue") { settings.value.accessibilityConsent = true; step = 8 }.disabled(!accepted) }
                    default:
                        BodyCopy(key: "onboarding.allset.body")
                        QuillButton(key: "onboarding.allset.cta") { finish(); NSApp.hide(nil) }
                        QuillButton(key: "onboarding.allset.home_link", primary: false) { finish() }
                    }
                }
                if !dictation.message.isEmpty { CopyText(dictation.message).foregroundStyle(QuillTheme.error) }
                HStack(spacing: 6) { Spacer(); ForEach(1..<9) { index in Capsule().fill(index == step ? QuillTheme.brand : QuillTheme.brand.opacity(0.15)).frame(width: index == step ? 22 : 7, height: 7) }; Spacer() }.accessibilityHidden(true)
            }.padding(32).frame(maxWidth: 590)
        }.frame(maxWidth: .infinity).task(id: step) {
            #if DEBUG
            // Presentation coverage only: no keys or permissions are fabricated,
            // and screenshots must not start the physical microphone or advance setup.
            if AppPaths.testMode && ProcessInfo.processInfo.arguments.contains("--ui-onboarding-stage") { return }
            #endif
            if step == 0 { try? await Task.sleep(nanoseconds: 800_000_000); if !Task.isCancelled { step = 1 } }
            if step == 2 { warningReady = false; try? await Task.sleep(nanoseconds: 3_000_000_000); if !Task.isCancelled { warningReady = true } }
            if step == 5 { dictation.startMicTest() }
        }.onDisappear { dictation.stopMicTest() }
    }
    private var symbol: String { ["pencil.tip","waveform","key.fill","key.fill","checklist","mic.fill","rectangle.on.rectangle","accessibility","checkmark"][step] }
    private func checklist(_ key: String, done: Bool) -> some View { HStack { CopyText(key); Spacer(); Image(systemName: done ? "checkmark.circle.fill" : "circle").foregroundStyle(done ? QuillTheme.success : Color.secondary) } }
    private func finish() { settings.value.onboardingComplete = true; settings.value.bubbleActive = true; settings.value.bubbleVisible = true; dictation.configureLogin() }
}

enum SystemLinks {
    static func open(_ pane: String) { if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?\(pane)") { NSWorkspace.shared.open(url) } }
}
