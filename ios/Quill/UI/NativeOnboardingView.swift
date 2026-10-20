import SwiftUI
import QuillCore

struct OnboardingView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var warning = false
    @State private var keyboardText = ""
    @State private var practiceText = ""
    @State private var receipt: KeyboardSetupReceipt?
    @State private var connectionStarted = Date()
    @State private var practiceTransfer: UUID?
    @State private var practiceTranscript = ""
    @FocusState private var fieldFocused: Bool
    private var step: Int { max(1, min(6, model.settings.onboardingStep)) }
    private var connected: Bool { receipt?.isRecent(since: connectionStarted) == true }
    private var practiced: Bool {
        receipt?.confirmsPractice(transferID: practiceTransfer, transcript: practiceTranscript, field: practiceText) == true
    }
    private let stages = ["ios.setup.welcome", "ios.setup.provider", "ios.setup.keyboard", "ios.setup.microphone", "ios.setup.practice", "ios.setup.ready"]
    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                let wide = geometry.size.width >= 820 && !typeSize.isAccessibilitySize
                HStack(spacing: 0) {
                    // Keep the form in one structural location so rotation and window resizing
                    // retain provider drafts, focus and the practice field.
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            HStack { Wordmark(); Spacer(); Text(model.c("ios.setup.progress", String(step), "6")).font(Q.sans(14)).foregroundStyle(Q.muted) }
                            if step > 1 { ProgressView(value: Double(step), total: 6).tint(Q.brand) }
                            stageContent
                        }.padding(wide ? 40 : 24).frame(maxWidth: 720, alignment: .leading).frame(maxWidth: .infinity)
                    }.scrollDismissesKeyboard(.interactively)
                    if wide { Divider(); companion.frame(width: min(340, geometry.size.width * 0.32)) }
                }.background(Q.paper).foregroundStyle(Q.ink).font(Q.sans())
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(step == 1 ? .hidden : .visible, for: .navigationBar)
            .toolbar {
                if step > 1 {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { move(to: step - 1) } label: { Label(model.c("action.back"), systemImage: "chevron.left") }.accessibilityIdentifier("onboarding.back")
                    }
                }
                ToolbarItem(placement: .keyboard) { Button(model.c("status.done")) {
                    fieldFocused = false
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                } }
            }
            .sheet(isPresented: $warning) { keyDisclosure }
        }
        .onDisappear { if model.busy || model.canRetry { model.cancel() } else { model.stopMicTest() } }
        .task {
            while !Task.isCancelled {
                receipt = KeyboardTransfer.setupReceipt()
                if practiced && !model.settings.keyboardSetupAcknowledged { model.settings.keyboardSetupAcknowledged = true }
                try? await Task.sleep(for: .milliseconds(400))
            }
        }
    }
    @ViewBuilder private var stageContent: some View {
        switch step {
        case 1: welcome
        case 2: provider
        case 3: keyboard
        case 4: microphone
        case 5: practice
        default: ready
        }
    }
    private func move(to stage: Int) {
        fieldFocused = false
        if model.busy || model.canRetry { model.cancel() } else { model.stopMicTest() }
        model.settings.onboardingStep = stage
        if stage == 3 { connectionStarted = Date(); receipt = nil }
    }
    private func heading(_ title: String, body: String, id: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.c(title)).font(Q.serif(34)).accessibilityAddTraits(.isHeader).accessibilityIdentifier(id)
            Text(model.c(body)).font(.body).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true)
        }
    }
    private var welcome: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(model.c("ios.setup.welcome_title")).font(Q.serif(38)).fontWeight(.semibold).accessibilityAddTraits(.isHeader).accessibilityIdentifier("onboarding.welcome")
            Text(model.c("ios.setup.welcome_body")).font(Q.sans(16)).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true)
            QCard { Text(model.c("ios.setup.example")).font(Q.sans(18)).frame(minHeight: 100, alignment: .topLeading).padding(4) }
            VStack(alignment: .leading, spacing: 12) {
                instruction(1, "ios.setup.flow_short_record")
                instruction(2, "ios.setup.flow_short_send")
                instruction(3, "ios.setup.flow_short_insert")
            }
            QButton(title: model.c("onboarding.welcome.cta")) { warning = true }.accessibilityIdentifier("onboarding.getStarted")
            Button(model.c("onboarding.welcome.open")) { model.settings.onboardingComplete = true }.frame(minHeight: 44)
            Text(model.c("ios.setup.no_account")).font(Q.sans(12)).foregroundStyle(Q.muted)
        }
    }
    private var keyDisclosure: some View {
        NavigationStack {
            QPage {
                heading("compliance.key.required_title", body: "compliance.key.required_body", id: "onboarding.key.disclosure")
                QCard {
                    VStack(alignment: .leading, spacing: 20) {
                        Label(model.c("onboarding.key.warning.free_tier"), systemImage: "key")
                        Label(model.c("onboarding.key.warning.usage"), systemImage: "info.circle")
                    }.font(Q.sans(14))
                }
                QBanner(text: model.c("compliance.privacy.promise"), icon: "lock.shield")
                QButton(title: model.c("onboarding.key.warning.accept")) { warning = false; move(to: 2) }
            }.navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(model.c("action.not_now")) { warning = false } } }
        }.presentationDetents([.large]).presentationDragIndicator(.visible)
    }
    private var provider: some View {
        VStack(alignment: .leading, spacing: 22) {
            heading("ios.setup.provider_title", body: "ios.setup.provider_body", id: "onboarding.provider")
            GuidedKeySetup(activateSpeechProvider: true, onSaved: { move(to: 3) })
            if model.hasActiveKey { QButton(title: model.c("action.continue")) { move(to: 3) } }
        }
    }
    private var keyboard: some View {
        VStack(alignment: .leading, spacing: 22) {
            heading("ios.keyboard.setup_title", body: "ios.setup.keyboard_body", id: "onboarding.keyboard")
            QCard {
                VStack(alignment: .leading, spacing: 18) {
                    instruction(1, "ios.setup.keyboard_add")
                    instruction(2, "ios.setup.keyboard_access")
                    instruction(3, "ios.setup.keyboard_return")
                }
            }
            DisclosureGroup(model.c("ios.setup.keyboard_missing")) { Text(model.c("ios.keyboard.setup_steps")).font(Q.sans(14)).foregroundStyle(Q.muted) }
            QBanner(title: model.c("ios.keyboard.full_access_title"), text: model.c("ios.keyboard.full_access_body"), icon: "lock.shield")
            QButton(title: model.c("ios.settings.open"), icon: "arrow.up.forward.app") { openSettings() }.accessibilityIdentifier("onboarding.keyboard.settings")
            Text(model.c("ios.setup.keyboard_switch")).font(Q.sans(14)).foregroundStyle(Q.muted)
            TextField(model.c("ios.keyboard.test_field"), text: $keyboardText)
                .modifier(QInputSurface()).focused($fieldFocused).accessibilityIdentifier("onboarding.keyboard.test")
            Label(model.c(connected ? "ios.setup.keyboard_connected" : "ios.setup.keyboard_waiting"), systemImage: connected ? "checkmark.circle.fill" : "keyboard")
                .font(Q.sans(14)).foregroundStyle(connected ? Q.success : Q.muted).accessibilityIdentifier("onboarding.keyboard.status")
            QButton(title: model.c("action.continue")) { move(to: 4) }.disabled(!connected).accessibilityIdentifier("onboarding.keyboard.next")
            Button(model.c("ios.setup.keyboard_later")) { move(to: 4) }.frame(minHeight: 44).accessibilityIdentifier("onboarding.keyboard.later")
        }
    }
    private var microphone: some View {
        VStack(alignment: .leading, spacing: 22) {
            heading("ios.setup.mic_title", body: "ios.mic.rationale", id: "onboarding.microphone")
            if model.microphoneGranted {
                MicTestPanel(recorder: model.recorder)
                MicTestControls(recorder: model.recorder)
                QBanner(text: model.c("ios.mic.test_private"), icon: "lock")
            } else {
                QButton(title: model.c("onboarding.mic.cta"), icon: "mic") { Task { await model.startMicTest() } }.accessibilityIdentifier("onboarding.permission")
                Button(model.c("onboarding.mic.open_settings")) { openSettings() }.frame(minHeight: 44)
            }
            QButton(title: model.c("ios.setup.mic_next")) { move(to: 5) }.disabled(!model.microphoneGranted).accessibilityIdentifier("onboarding.microphone.next")
        }
    }
    private var practice: some View {
        VStack(alignment: .leading, spacing: 22) {
            heading("ios.setup.practice_title", body: "ios.setup.practice_body", id: "onboarding.practice")
            DictationControls(recorder: model.recorder)
            if !model.result.isEmpty {
                QCard { Text(model.result).textSelection(.enabled).privacySensitive() }
                QButton(title: model.c("ios.result.send_keyboard"), icon: "keyboard") {
                    practiceTranscript = model.result
                    practiceTransfer = model.sendToKeyboard(model.result)
                    fieldFocused = true
                }.accessibilityIdentifier("onboarding.practice.send")
            }
            Text(model.c("ios.setup.practice_switch")).font(Q.sans(14)).foregroundStyle(Q.muted)
            TextField(model.c("ios.setup.practice_field"), text: $practiceText)
                .modifier(QInputSurface()).focused($fieldFocused).accessibilityIdentifier("onboarding.practice.field")
            if practiced {
                Label(model.c("ios.setup.practice_success"), systemImage: "checkmark.circle.fill").foregroundStyle(Q.success).accessibilityIdentifier("onboarding.practice.success")
            }
            QButton(title: model.c("action.continue")) { move(to: 6) }.disabled(!practiced).accessibilityIdentifier("onboarding.practice.next")
            Button(model.c("ios.setup.practice_later")) { move(to: 6) }.frame(minHeight: 44).accessibilityIdentifier("onboarding.practice.later")
        }
    }
    private var ready: some View {
        VStack(alignment: .leading, spacing: 24) {
            Image(systemName: "checkmark.circle.fill").font(.system(size: 48)).foregroundStyle(Q.success).accessibilityHidden(true)
            heading("ios.setup.ready_title", body: model.settings.keyboardSetupAcknowledged ? "ios.setup.ready_body" : "ios.setup.ready_pending", id: "onboarding.ready")
            workflow
            if !model.settings.keyboardSetupAcknowledged {
                Button(model.c("ios.setup.return_practice")) { move(to: 3) }.frame(minHeight: 44)
            }
            QButton(title: model.c("onboarding.allset.cta")) { model.settings.onboardingComplete = true; model.tab = 0 }.accessibilityIdentifier("onboarding.finish")
        }
    }
    private var workflow: some View {
        VStack(alignment: .leading, spacing: 20) {
            instruction(1, "ios.setup.flow_record")
            instruction(2, "ios.setup.flow_send")
            instruction(3, "ios.setup.flow_insert")
        }
    }
    private func instruction(_ number: Int, _ key: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(String(number)).font(.subheadline.weight(.semibold)).foregroundStyle(Q.brandInk).frame(width: 28, height: 28).background(Q.brandSoft, in: Circle()).accessibilityHidden(true)
            Text(model.c(key)).fixedSize(horizontal: false, vertical: true)
        }
    }
    private var companion: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                Text(model.c("ios.setup.companion_title")).font(Q.serif(28))
                ForEach(Array(stages.enumerated()), id: \.offset) { index, key in
                    HStack(spacing: 14) {
                        Image(systemName: index + 1 < step ? "checkmark.circle.fill" : index + 1 == step ? "circle.inset.filled" : "circle").foregroundStyle(index + 1 <= step ? Q.brandInk : Q.muted)
                        Text(model.c(key)).foregroundStyle(index + 1 == step ? Q.ink : Q.muted)
                    }.font(Q.sans(14)).accessibilityElement(children: .combine)
                }
                Divider()
                Text(model.c("ios.setup.companion_body")).font(Q.sans(14)).foregroundStyle(Q.muted)
                Label(model.c("ios.setup.no_account"), systemImage: "lock.shield").font(Q.sans(12)).foregroundStyle(Q.brandInk)
            }.padding(32).frame(maxWidth: .infinity, alignment: .leading)
        }.background(Q.surface).accessibilityIdentifier("onboarding.companion")
    }
}
