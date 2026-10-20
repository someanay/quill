import SwiftUI
import QuillCore

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    private var today: [HistoryEntry] { model.history.filter { Calendar.current.isDateInToday($0.date) } }
    private var words: Int { today.reduce(0) { $0 + $1.text.split(whereSeparator: \.isWhitespace).count } }
    var body: some View {
        GeometryReader { geometry in
            let wide = geometry.size.width >= 850 && !typeSize.isAccessibilitySize
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Wordmark().padding(.vertical, 8)
                    HStack(alignment: .top, spacing: 28) {
                        VStack(alignment: .leading, spacing: 20) {
                            if !model.microphoneGranted {
                                QBanner(text: model.c("ios.mic.blocked"), icon: "mic.slash", error: true)
                                QButton(title: model.c("onboarding.mic.cta")) { Task { await model.requestMicrophone() } }
                            }
                            if !model.hasActiveKey { NavigationLink { APIKeysView() } label: { QCard { QRow(title: model.c("settings.keys.add"), icon: "key") } }.buttonStyle(.plain) }
                            KeyboardSessionPanel(session: model.keyboardSession)
                            DictationControls(recorder: model.recorder)
                            if !model.result.isEmpty { TranscriptResult(text: model.result) }
                            else {
                                QCard {
                                    VStack(alignment: .leading, spacing: 18) {
                                        Label(model.c("ios.workspace.title"), systemImage: "text.cursor").font(Q.serif(24))
                                        Text(model.c("ios.workspace.body")).foregroundStyle(Q.muted)
                                        Text(model.c("ios.workspace.provider", model.settings.provider.title)).font(Q.sans(12)).foregroundStyle(Q.brandInk)
                                    }.frame(minHeight: wide ? 180 : 80, alignment: .topLeading)
                                }
                            }
                            if let notice = model.notice { QBanner(text: notice, icon: "checkmark.circle").accessibilityAddTraits(.updatesFrequently) }
                            if !wide { context }
                        }.frame(maxWidth: .infinity)
                        if wide { context.frame(width: 290) }
                    }
                }.padding(geometry.size.width >= 700 ? 28 : 20).modifier(QScrollClearance()).frame(maxWidth: 1400).frame(maxWidth: .infinity)
            }
        }.background(Q.paper).foregroundStyle(Q.ink).font(Q.sans()).onAppear { model.refreshHistory() }
    }
    private var context: some View {
        VStack(alignment: .leading, spacing: 18) {
            QSection(title: model.c("home.group.today")).accessibilityIdentifier("home.today")
            QCard {
                VStack(spacing: 10) {
                    QRow(title: model.c(today.count == 1 ? "ios.home.dictation_count_one" : "ios.home.dictations_count", String(today.count)), subtitle: model.c(words == 1 ? "ios.home.word_count_one" : "ios.home.words_count", String(words)), icon: "waveform", chevron: false).accessibilityElement(children: .combine).accessibilityIdentifier("home.today.count")
                    Divider()
                    Button { model.tab = 1 } label: {
                        QRow(title: model.c("home.today.last_used"), subtitle: model.history.first.map { $0.date.formatted(date: .abbreviated, time: .shortened) } ?? model.c("ios.home.no_last_used"), icon: "clock")
                    }.buttonStyle(.plain).accessibilityIdentifier("home.today.last_used")
                }
            }
            QSection(title: model.c("ios.workspace.keyboard"))
            NavigationLink { KeyboardHelpView() } label: {
                QBanner(title: model.c("ios.home.tip_title"), text: model.c("ios.home.tip_body"), icon: "keyboard")
            }.buttonStyle(.plain)
        }
    }
}

struct TranscriptResult: View {
    @EnvironmentObject private var model: AppModel
    let text: String
    var dismissible = true
    var body: some View {
        QCard {
            VStack(alignment: .leading, spacing: 18) {
                Text(model.c("ios.result.title")).font(Q.sans(17))
                Text(text).textSelection(.enabled).font(Q.sans(18)).privacySensitive().frame(maxWidth: .infinity, alignment: .leading).accessibilityIdentifier("transcript.text")
                QButton(title: model.c("ios.result.send_keyboard"), icon: "keyboard") { model.sendToKeyboard(text) }.accessibilityIdentifier("transcript.send")
                QButton(title: model.c("ios.result.copy"), icon: "doc.on.doc", secondary: true) { model.copyText(text) }
                if dismissible { Button(model.c("ios.result.dismiss")) { model.result = "" }.frame(minHeight: 44) }
            }
        }
    }
}

struct HistoryView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var clearConfirmation = false
    @State private var selectedID: UUID?
    @State private var showDetail = false
    private var selected: HistoryEntry? { model.history.first { $0.id == selectedID } ?? model.history.first }
    var body: some View {
        GeometryReader { geometry in
            let wide = geometry.size.width >= 740 && !typeSize.isAccessibilitySize
            HStack(spacing: 0) {
                QPage {
                    QHeading(title: model.c("nav.history"))
                    if model.settings.privateMode { QBanner(text: model.c("ios.history.private"), icon: "lock.shield") }
                    if model.history.isEmpty { emptyState }
                    else {
                        ForEach(model.history) { entry in
                            Button { selectedID = entry.id; showDetail = !wide } label: {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text(entry.text).font(Q.sans()).foregroundStyle(Q.ink).lineLimit(3).multilineTextAlignment(.leading).privacySensitive()
                                    HStack { Text(entry.date, style: .time); Spacer(); Text(entry.provider.title) }.font(.caption).foregroundStyle(Q.muted)
                                }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                    .background(wide && selected?.id == entry.id ? Q.brandSoft : Q.surface, in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain).accessibilityIdentifier("history.entry.\(entry.id)")
                        }
                        Button(model.c("ios.history.clear"), role: .destructive) { clearConfirmation = true }.frame(minHeight: 44)
                    }
                }.frame(maxWidth: wide ? 320 : .infinity).accessibilityIdentifier("history.list")
                if wide && !model.history.isEmpty { Divider(); QPage { if let selected { reader(selected) } }.accessibilityIdentifier("history.reader") }
            }
        }.background(Q.paper).onAppear { model.refreshHistory() }
            .confirmationDialog(model.c("ios.history.clear_confirm"), isPresented: $clearConfirmation, titleVisibility: .visible) {
                Button(model.c("ios.history.clear"), role: .destructive) { model.clearHistory(); selectedID = nil }
            }.sheet(isPresented: $showDetail) {
                NavigationStack {
                    QPage { if let selected { reader(selected) } }.navigationTitle(model.c("ios.result.title")).navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(model.c("status.done")) { showDetail = false } } }
                }.presentationDetents([.large]).presentationDragIndicator(.visible)
            }
    }
    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 18) {
            Image(systemName: "clock").font(.system(size: 40, weight: .light)).foregroundStyle(Q.brandInk)
            Text(model.c(model.settings.shouldSaveHistory ? "history.empty.on_title" : "history.empty.off_title")).font(Q.serif(24))
            Text(model.c(model.settings.shouldSaveHistory ? "history.empty.on_body" : "history.empty.off_body")).foregroundStyle(Q.muted)
            if model.settings.shouldSaveHistory { Button(model.c("history.empty.on_cta")) { model.tab = 0 }.frame(minHeight: 44) }
            else { NavigationLink(model.c("history.empty.off_cta")) { PrivacySettingsView() }.frame(minHeight: 44) }
        }.padding(.vertical, 40)
    }
    private func reader(_ entry: HistoryEntry) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack { Text(entry.date, style: .date); Text(entry.date, style: .time) }.font(Q.sans(14)).foregroundStyle(Q.muted)
            TranscriptResult(text: entry.text, dismissible: false)
            Button(model.c("ios.history.delete"), role: .destructive) { model.deleteHistory(entry); selectedID = nil; showDetail = false }.frame(minHeight: 44)
        }
    }
}
