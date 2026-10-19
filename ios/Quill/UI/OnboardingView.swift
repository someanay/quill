import SwiftUI
import QuillCore

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
