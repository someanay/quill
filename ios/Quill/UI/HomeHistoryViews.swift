import SwiftUI
import QuillCore

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    private var today: [HistoryEntry] { model.history.filter { Calendar.current.isDateInToday($0.date) } }
    private var todayWords: Int { today.reduce(0) { $0 + $1.text.split(whereSeparator: \.isWhitespace).count } }
    var body: some View {
        QPage {
            Wordmark().padding(.vertical, 8)
            if !model.microphoneGranted {
                QBanner(text: model.c("ios.mic.blocked"), icon: "mic.slash", error: true)
                QButton(title: model.c("onboarding.mic.cta")) { Task { await model.requestMicrophone() } }
            }
            if !model.hasActiveKey { NavigationLink { APIKeysView() } label: { QCard { QRow(title: model.c("settings.keys.add"), subtitle: model.c("settings.speech.key_required", model.settings.provider.title), icon: "key") } }.buttonStyle(.plain) }
            DictationControls(recorder: model.recorder)
            if !model.result.isEmpty {
                QCard {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(model.c("ios.result.title")).font(Q.sans(17)).fontWeight(.medium)
                        Text(model.result).textSelection(.enabled).font(Q.sans()).privacySensitive()
                        QButton(title: model.c("ios.result.send_keyboard"), icon: "keyboard") { model.sendToKeyboard(model.result) }
                        QButton(title: model.c("ios.result.copy"), icon: "doc.on.doc", secondary: true) { model.copyText(model.result) }
                        Button(model.c("ios.result.dismiss")) { model.result = "" }.frame(minHeight: 48)
                    }
                }
            }
            if let notice = model.notice { QBanner(text: notice, icon: "checkmark.circle").accessibilityAddTraits(.updatesFrequently) }
            QSection(title: model.c("home.group.today"))
            QCard {
                VStack(spacing: 10) {
                    QRow(title: model.c("ios.home.dictations_count", String(today.count)), subtitle: model.c("ios.home.words_count", String(todayWords)), icon: "waveform", chevron: false)
                    Divider()
                    Button { model.tab = 1 } label: {
                        QRow(title: model.c("home.today.last_used"), subtitle: model.history.first.map { $0.date.formatted(date: .abbreviated, time: .shortened) } ?? model.c("ios.home.no_last_used"), icon: "clock")
                    }.buttonStyle(.plain)
                }
            }
            QSection(title: model.c("home.group.tip"))
            NavigationLink { KeyboardHelpView() } label: {
                QBanner(title: model.c("ios.home.tip_title"), text: model.c("ios.home.tip_body"))
            }.buttonStyle(.plain)
        }.toolbar(.hidden, for: .navigationBar).onAppear { model.refreshHistory() }
    }
}

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
                        .buttonStyle(.plain).disabled(model.phase == .transcribing || model.phase == .cleaning || model.canRetry)
                        .accessibilityLabel(model.c(model.phase == .recording ? "ios.recording.stop" : "ios.recording.start"))
                        .accessibilityIdentifier("dictation.record")
                    VStack(alignment: .leading, spacing: 5) {
                        Text(model.c("ios.phase.\(model.phase.rawValue)")).font(Q.serif(24)).accessibilityAddTraits(.updatesFrequently)
                        Text(model.c("ios.home.subtitle")).font(Q.sans(12)).foregroundStyle(Q.muted)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
                if model.phase == .recording {
                    InkMeter(amplitude: recorder.amplitude)
                    Text(String(format: "%d:%02d", Int(recorder.elapsed) / 60, Int(recorder.elapsed) % 60)).monospacedDigit().foregroundStyle(Q.brandInk)
                    if recorder.warning { QBanner(text: model.c("ios.recording.cap_warning")) }
                }
                if model.busy || model.canRetry { QButton(title: model.c("action.cancel"), secondary: true) { model.cancel() } }
                if model.canRetry {
                    QButton(title: model.c("ios.recording.retry"), icon: "arrow.clockwise") { model.retry() }
                    Text(model.c("ios.recording.retry_retention")).font(Q.sans(12)).foregroundStyle(Q.muted)
                }
            }.frame(maxWidth: .infinity).padding(.vertical, 6)
        }
    }
}

struct HistoryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var clearConfirmation = false
    @State private var selected: HistoryEntry?
    var body: some View {
        QPage {
            QHeading(title: model.c("nav.history"))
            if model.settings.privateMode { QBanner(text: model.c("ios.history.private"), icon: "lock.shield") }
            if model.history.isEmpty {
                VStack(spacing: 18) {
                    PermissionIllustration(icon: "clock")
                    Text(model.c(model.settings.shouldSaveHistory ? "history.empty.on_title" : "history.empty.off_title")).font(Q.serif(24))
                    Text(model.c(model.settings.shouldSaveHistory ? "history.empty.on_body" : "history.empty.off_body")).foregroundStyle(Q.muted)
                    if model.settings.shouldSaveHistory {
                        QButton(title: model.c("history.empty.on_cta"), secondary: true) { model.tab = 0 }
                    } else {
                        NavigationLink(model.c("history.empty.off_cta")) { PrivacySettingsView() }.frame(minHeight: 48)
                    }
                }.frame(maxWidth: .infinity).padding(.vertical, 45)
            } else {
                ForEach(model.history) { entry in
                    Button { selected = entry } label: {
                        QCard {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(entry.text).font(Q.sans()).foregroundStyle(Q.ink).lineLimit(5).multilineTextAlignment(.leading).privacySensitive()
                                HStack { Text(entry.date, style: .date); Text(entry.date, style: .time); Spacer(); Text(entry.provider.title) }.font(Q.sans(12)).foregroundStyle(Q.muted)
                            }
                        }
                    }.buttonStyle(.plain)
                }
                QButton(title: model.c("ios.history.clear"), icon: "trash", secondary: true) { clearConfirmation = true }
            }
        }.toolbar(.hidden, for: .navigationBar).onAppear { model.refreshHistory() }
            .confirmationDialog(model.c("ios.history.clear_confirm"), isPresented: $clearConfirmation, titleVisibility: .visible) {
                Button(model.c("ios.history.clear"), role: .destructive) { model.clearHistory() }
            }.sheet(item: $selected) { entry in
                QPage {
                    QHeading(title: model.c("ios.result.title"))
                    Text(entry.text).textSelection(.enabled).privacySensitive()
                    QButton(title: model.c("ios.result.send_keyboard"), icon: "keyboard") { model.sendToKeyboard(entry.text); selected = nil }
                    QButton(title: model.c("ios.result.copy"), icon: "doc.on.doc", secondary: true) { model.copyText(entry.text); selected = nil }
                    QButton(title: model.c("ios.history.delete"), icon: "trash", destructive: true) { model.deleteHistory(entry); selected = nil }
                    Button(model.c("status.done")) { selected = nil }.frame(minHeight: 48)
                }.presentationDetents([.medium, .large])
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
            QButton(title: model.c("ios.settings.open")) { openSettings() }
            QField(title: model.c("ios.keyboard.test_field"), text: $testText)
            QSection(title: model.c("ios.keyboard.how_title"))
            Text(model.c("ios.workflow.step1")); Text(model.c("ios.workflow.step2")); Text(model.c("ios.workflow.step3"))
            QBanner(text: model.c("ios.keyboard.sensitive_limit"))
            QButton(title: model.c("ios.transfer.clear"), secondary: true) { model.clearTransfer() }
        }.navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}
