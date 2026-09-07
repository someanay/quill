import SwiftUI
import QuillCore

struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var warning = false
    @State private var warningReady = false
    @State private var warningProgress = 0.0
    @State private var keyboardTestText = ""
    private var step: Int { model.settings.onboardingStep }
    var body: some View {
        NavigationStack {
            Group {
                if step < 2 {
                    GeometryReader { geometry in
                        ScrollView {
                            Group { if step == 0 { splash } else { welcome } }
                                .frame(maxWidth: 680).frame(minHeight: max(0, geometry.size.height - 32))
                                .padding(16).frame(maxWidth: .infinity)
                        }.background(Q.paper).foregroundStyle(Q.ink).font(Q.sans())
                    }
                } else {
                    QPage {
                        HStack { Wordmark(); Spacer(); Button(model.c("action.back")) { model.stopMicTest(); model.settings.onboardingStep -= 1 }.frame(minHeight: 48) }
                        switch step {
                        case 2: key
                        case 3: checklist
                        case 4: microphone
                        case 5: recordingExplainer
                        case 6: keyboard
                        default: ready
                        }
                        HStack(spacing: 6) { ForEach(2..<8, id: \.self) { n in Capsule().fill(n == step ? Q.brand : Q.line).frame(width: n == step ? 22 : 7, height: 7) } }.frame(maxWidth: .infinity).padding(.vertical)
                            .accessibilityLabel(model.c("onboarding.checklist.progress", String(max(1, step - 1)), "6"))
                    }
                }
            }.toolbar(.hidden, for: .navigationBar)
                .sheet(isPresented: $warning) { warningSheet }
        }.onDisappear { model.stopMicTest() }
    }
    private var splash: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 24)
            Wordmark()
            Text(model.c("ios.onboarding.tagline")).multilineTextAlignment(.center).foregroundStyle(Q.muted)
            Spacer(minLength: 24)
        }.frame(maxWidth: .infinity).task {
            try? await Task.sleep(for: .milliseconds(1200))
            if !Task.isCancelled && model.settings.onboardingStep == 0 { model.settings.onboardingStep = 1 }
        }
    }
    private var welcome: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)
            Wordmark().padding(.bottom, 24)
            Text(model.c("onboarding.welcome.title")).font(Q.serif(24)).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            Text(model.c("ios.onboarding.welcome_body")).foregroundStyle(Q.muted).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true).padding(.top, 10)
            HStack(spacing: 6) {
                Circle().fill(Q.brand).frame(width: 8, height: 8).accessibilityHidden(true)
                Text(model.c("compliance.privacy.welcome_pill")).font(Q.sans(12)).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(Q.brandInk).padding(.horizontal, 10).padding(.vertical, 5).background(Q.brandSoft, in: Capsule()).padding(.top, 20)
            QButton(title: model.c("onboarding.welcome.cta")) { warningReady = false; warning = true }.accessibilityIdentifier("onboarding.getStarted").padding(.top, 28)
            Spacer(minLength: 24)
            HStack { Text(model.c("onboarding.welcome.already")).foregroundStyle(Q.muted); Button(model.c("onboarding.welcome.open")) { model.settings.onboardingComplete = true } }.font(Q.sans(14)).frame(maxWidth: .infinity, minHeight: 48)
        }
    }
    private var warningSheet: some View {
        QPage {
            Image(systemName: "key.fill").font(.system(size: 19)).foregroundStyle(Q.brandInk).frame(width: 34, height: 34).background(Q.brandSoft, in: RoundedRectangle(cornerRadius: 10)).accessibilityHidden(true)
            Text(model.c("compliance.key.required_title")).font(Q.sans(17)).fontWeight(.medium).accessibilityAddTraits(.isHeader)
            Text(model.c("compliance.key.required_body")).font(Q.sans(14)).foregroundStyle(Q.muted)
            QCard {
                VStack(alignment: .leading, spacing: 8) {
                    warningRow("onboarding.key.warning.free_tier", icon: "key", tint: Q.brandInk)
                    warningRow("onboarding.key.warning.usage", icon: "info.circle", tint: Q.brandInk)
                    warningRow("compliance.privacy.promise", icon: "lock", tint: Q.success)
                }
            }
            if warningReady {
                QButton(title: model.c("onboarding.key.warning.accept")) { warning = false; model.settings.onboardingStep = 2 }
                Button(model.c("action.not_now")) { warning = false }.frame(maxWidth: .infinity, minHeight: 48)
            } else {
                ProgressView(value: warningProgress).tint(Q.brand).accessibilityLabel(model.c("onboarding.key.warning.reading"))
                QButton(title: model.c("action.continue")) { }.disabled(true).opacity(0.5)
                Text(model.c("onboarding.key.warning.reading")).font(Q.sans(12)).foregroundStyle(Q.muted).frame(maxWidth: .infinity)
            }
        }.presentationDetents([.large]).task {
            warningProgress = 0
            withAnimation(.linear(duration: 3)) { warningProgress = 1 }
            try? await Task.sleep(for: .seconds(3))
            if !Task.isCancelled { warningReady = true }
        }
    }
    private func warningRow(_ copyKey: String, icon: String, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 9) {
            Image(systemName: icon).font(.system(size: 17)).foregroundStyle(tint).frame(width: 17).accessibilityHidden(true)
            Text(model.c(copyKey)).font(Q.sans(12)).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true)
        }
    }
    private var key: some View {
        VStack(alignment: .leading, spacing: 16) {
            QHeading(title: model.c("onboarding.key.title"))
            Text(model.c("onboarding.key.body")).foregroundStyle(Q.muted)
            GuidedKeySetup(activateSpeechProvider: true, onSaved: { model.settings.onboardingStep = 3 })
            if model.hasActiveKey { QButton(title: model.c("action.continue")) { model.settings.onboardingStep = 3 } }
        }
    }
    private var checklist: some View {
        VStack(alignment: .leading, spacing: 18) {
            PermissionIllustration(icon: "checkmark").frame(maxWidth: .infinity).padding(.top, 24)
            QHeading(title: model.c("onboarding.checklist.title")).accessibilityIdentifier("onboarding.checklist")
            QCard {
                VStack {
                    QRow(title: model.c("onboarding.checklist.key"), subtitle: model.c("onboarding.checklist.provider_connected", model.settings.provider.title), icon: "key.fill", value: model.c("status.done"))
                    Divider()
                    QRow(title: model.c("onboarding.checklist.mic"), subtitle: model.c("onboarding.checklist.mic_sub"), icon: "mic")
                    Divider()
                    QRow(title: model.c("ios.keyboard.title"), subtitle: model.c("ios.keyboard.checklist"), icon: "keyboard")
                }
            }
            QBanner(text: model.c("ios.mic.rationale"), icon: "mic")
            QButton(title: model.microphoneGranted ? model.c("onboarding.mictest.title") : model.c("onboarding.mic.cta")) {
                Task { if !model.microphoneGranted { await model.requestMicrophone() }; if model.microphoneGranted { model.settings.onboardingStep = 4 } }
            }.accessibilityIdentifier("onboarding.permission")
            Button(model.c("onboarding.mic.open_settings")) { openSettings() }.frame(minHeight: 48)
        }
    }
    private var microphone: some View {
        VStack(spacing: 20) {
            PermissionIllustration(icon: "mic").padding(.top, 30)
            QHeading(title: model.c("onboarding.mictest.title")).accessibilityIdentifier("onboarding.microphone")
            Text(model.c("onboarding.mictest.body")).foregroundStyle(Q.muted)
            MicTestPanel(recorder: model.recorder)
            MicTestControls(recorder: model.recorder)
            QBanner(text: model.c("ios.mic.test_private"), icon: "lock")
            QButton(title: model.c("onboarding.mictest.cta"), secondary: true) { model.stopMicTest(); model.settings.onboardingStep = 5 }.accessibilityIdentifier("onboarding.microphone.next")
        }.task { await model.startMicTest() }.onDisappear { model.stopMicTest() }
    }
    private var recordingExplainer: some View {
        VStack(alignment: .leading, spacing: 20) {
            PermissionIllustration(icon: "waveform").frame(maxWidth: .infinity).padding(.top, 24)
            QHeading(title: model.c("ios.onboarding.record_title")).accessibilityIdentifier("onboarding.record")
            Text(model.c("ios.onboarding.record_body"))
            QCard {
                VStack(alignment: .leading, spacing: 15) {
                    Label(model.c("ios.workflow.step1"), systemImage: "1.circle")
                    Label(model.c("ios.workflow.step2"), systemImage: "2.circle")
                    Label(model.c("ios.workflow.step3"), systemImage: "3.circle")
                }
            }
            QBanner(text: model.c("ios.onboarding.overlay_limit"))
            QButton(title: model.c("action.continue")) { model.settings.onboardingStep = 6 }.accessibilityIdentifier("onboarding.record.next")
        }
    }
    private var keyboard: some View {
        VStack(alignment: .leading, spacing: 20) {
            PermissionIllustration(icon: "keyboard").frame(maxWidth: .infinity).padding(.top, 24)
            QHeading(title: model.c("ios.keyboard.setup_title")).accessibilityIdentifier("onboarding.keyboard")
            Text(model.c("ios.keyboard.setup_steps"))
            QBanner(title: model.c("ios.keyboard.full_access_title"), text: model.c("ios.keyboard.full_access_body"), icon: "lock.shield")
            QButton(title: model.c("ios.settings.open")) { openSettings() }
            TextField(model.c("ios.keyboard.test_field"), text: $keyboardTestText).textFieldStyle(.roundedBorder).frame(minHeight: 48)
            QButton(title: model.c("ios.keyboard.setup_done"), secondary: true) { model.settings.keyboardSetupAcknowledged = true; model.settings.onboardingStep = 7 }
            Button(model.c("action.not_now")) { model.settings.onboardingStep = 7 }.frame(minHeight: 48).accessibilityIdentifier("onboarding.keyboard.later")
        }
    }
    private var ready: some View {
        VStack(spacing: 22) {
            PermissionIllustration(icon: "checkmark").padding(.top, 45)
            QHeading(title: model.c("onboarding.allset.title")).accessibilityIdentifier("onboarding.ready")
            Text(model.c("ios.onboarding.ready_body")).foregroundStyle(Q.muted)
            QCard { HStack(spacing: 12) { RecordingBubble(size: 40); Text(model.c("ios.onboarding.bubble_look")).font(Q.sans(14)).foregroundStyle(Q.muted) } }
            QButton(title: model.c("onboarding.allset.cta")) { model.settings.onboardingComplete = true; model.tab = 0 }.accessibilityIdentifier("onboarding.finish")
        }
    }
}

struct MicTestPanel: View {
    @ObservedObject var recorder: AudioRecorder
    var body: some View { QCard { InkMeter(amplitude: recorder.amplitude).frame(maxWidth: .infinity).padding(.vertical, 20) } }
}

struct MicTestControls: View {
    @EnvironmentObject private var model: AppModel
    @ObservedObject var recorder: AudioRecorder
    var body: some View {
        QButton(title: model.c(recorder.isRecording ? "ios.recording.stop_test" : "ios.recording.start_test")) {
            if recorder.isRecording { model.stopMicTest() } else { Task { await model.startMicTest() } }
        }
    }
}

func openSettings() { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }

struct KeyEditor: View {
    @EnvironmentObject private var model: AppModel
    var activateSpeechProvider = false
    var onSaved: () -> Void = {}
    @State private var provider: Provider = .openai
    @State private var secret = ""
    @State private var label = ""
    @State private var reveal = false
    @State private var showGuide = false
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach([Provider.openai, .custom]) { item in
                QCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Button { provider = item; secret = ""; label = "" } label: {
                            HStack { Text(item.title).font(Q.sans(17)).foregroundStyle(Q.ink); Spacer(); Image(systemName: provider == item ? "chevron.up" : "chevron.down").foregroundStyle(Q.muted) }.frame(minHeight: 48)
                        }.buttonStyle(.plain).accessibilityIdentifier("key.provider.\(item.rawValue)")
                            .accessibilityAddTraits(provider == item ? [.isSelected] : [])
                        if provider == item {
                            if provider == .custom { QField(title: model.c("settings.speech.custom_url_label"), text: $model.settings.customBaseURL); QField(title: model.c("settings.speech.model_field_label"), text: $model.settings.customModel) }
                            QField(title: model.c("ios.keys.label"), text: $label)
                            HStack {
                                Group {
                                    if reveal { TextField(model.c("onboarding.key.field_label"), text: $secret) }
                                    else { SecureField(model.c("onboarding.key.field_label"), text: $secret) }
                                }.textInputAutocapitalization(.never).autocorrectionDisabled().font(.system(.body, design: .monospaced)).privacySensitive()
                                Button { reveal.toggle() } label: { Image(systemName: reveal ? "eye.slash" : "eye").frame(width: 48, height: 48) }.accessibilityLabel(model.c(reveal ? "a11y.hide_key" : "a11y.show_key"))
                            }.padding(.leading, 14).background(Q.sunken, in: RoundedRectangle(cornerRadius: Q.fieldRadius))
                            QButton(title: model.keyValidationRunning ? model.c("onboarding.key.verifying") : model.c("onboarding.key.cta")) {
                                let candidate = secret, chosen = provider
                                Task { if await model.verifyAndSaveKey(provider: chosen, secret: candidate, label: label, activateSpeechProvider: activateSpeechProvider) { secret = ""; onSaved() } }
                            }.disabled(secret.isEmpty || model.keyValidationRunning).accessibilityIdentifier("onboarding.key.verify")
                            if provider.consoleURL != nil {
                                Button(model.c("onboarding.key.where_link")) { showGuide = true }.frame(minHeight: 48)
                            }
                        }
                    }
                }
            }
            QBanner(text: model.c("ios.keys.security"), icon: "lock.shield")
        }.sheet(isPresented: $showGuide) {
            QPage {
                QHeading(title: model.c("onboarding.key.guide.title", provider.title))
                Text(model.c("onboarding.key.guide.\(provider.rawValue)_steps"))
                if let url = provider.consoleURL { Link(model.c("onboarding.key.guide.open", provider.title), destination: url).frame(minHeight: 48) }
                QButton(title: model.c("status.done")) { showGuide = false }
            }.presentationDetents([.large])
        }.onDisappear { secret = "" }
    }
}
