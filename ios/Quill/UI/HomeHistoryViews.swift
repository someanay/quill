import SwiftUI
import QuillCore

struct DictationControls: View {
    @EnvironmentObject private var model: AppModel
    @ObservedObject var recorder: AudioRecorder
    var body: some View {
        QCard {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    Button {
                        if model.phase == .recording { model.stopAndTranscribe() }
                        else if !model.busy { Task { await model.start() } }
                    } label: { RecordingBubble(phase: model.phase, size: model.settings.bubbleSize == "Small" ? 56 : model.settings.bubbleSize == "Large" ? 88 : 64) }
                        .buttonStyle(.plain).disabled(model.phase == .transcribing || model.phase == .cleaning || model.canRetry || model.cleanupRequired)
                        .accessibilityLabel(model.c(model.phase == .recording ? "ios.recording.stop" : "ios.recording.start"))
                        .accessibilityIdentifier("dictation.record")
                    VStack(alignment: .leading, spacing: 5) {
                        Text(model.c("ios.phase.\(model.phase.rawValue)")).font(Q.serif(24)).fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.updatesFrequently)
                        Text(model.c("ios.home.subtitle")).font(Q.sans(12)).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true)
                    }.multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                }
                if model.phase == .recording {
                    InkMeter(amplitude: recorder.amplitude)
                    Text(String(format: "%d:%02d", Int(recorder.elapsed) / 60, Int(recorder.elapsed) % 60)).monospacedDigit().foregroundStyle(Q.brandInk)
                    if recorder.warning { QBanner(text: model.c("ios.recording.cap_warning")) }
                }
                if model.busy || model.canRetry { QButton(title: model.c("action.cancel"), secondary: true) { model.cancel() } }
                if model.cleanupRequired {
                    QBanner(text: model.c("ios.recording.cleanup_pending"), error: true)
                    QButton(title: model.c("ios.recording.cleanup_retry"), icon: "arrow.clockwise", secondary: true) { model.retryRecordingCleanup() }
                }
                if model.canRetry {
                    QButton(title: model.c("ios.recording.retry"), icon: "arrow.clockwise") { model.retry() }
                    Text(model.c("ios.recording.retry_retention")).font(Q.sans(12)).foregroundStyle(Q.muted)
                }
            }.frame(maxWidth: .infinity).padding(.vertical, 6)
        }
    }
}

struct KeyboardHelpView: View {
    @EnvironmentObject private var model: AppModel
    @State private var testText = ""
    var body: some View {
        QPage {
            QHeading(title: model.c("ios.keyboard.setup_title")).accessibilityIdentifier("screen.keyboard")
            Text(model.c("ios.keyboard.setup_steps"))
            QBanner(title: model.c("ios.keyboard.full_access_title"), text: model.c("ios.keyboard.full_access_body"), icon: "lock.shield")
            KeyboardSessionPanel(session: model.keyboardSession)
            QButton(title: model.c("ios.settings.open")) { openSettings() }
            QField(title: model.c("ios.keyboard.test_field"), text: $testText)
            QSection(title: model.c("ios.keyboard.how_title"))
            Text(model.c("ios.workflow.step1")); Text(model.c("ios.workflow.step2")); Text(model.c("ios.workflow.step3"))
            QBanner(text: model.c("ios.keyboard.sensitive_limit"))
            QButton(title: model.c("ios.transfer.clear"), secondary: true) { model.clearTransfer() }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}
