import SwiftUI
import QuillCore

struct KeyboardSessionPanel: View {
    @EnvironmentObject private var model: AppModel
    @ObservedObject var session: KeyboardSessionController
    var body: some View {
        QCard {
            VStack(alignment: .leading, spacing: 14) {
                Label(model.c(session.isActive ? "ios.session.active_title" : "ios.session.title"), systemImage: session.isActive ? "mic.fill" : "keyboard")
                    .font(Q.sans(18)).foregroundStyle(session.isActive ? Q.brandInk : Q.ink)
                Text(model.c(session.isActive ? "ios.session.active_body" : "ios.session.body"))
                    .font(Q.sans(14)).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true)
                if session.isActive {
                    Text(model.c("ios.session.remaining", String((session.remainingSeconds + 59) / 60)))
                        .font(Q.sans(13)).foregroundStyle(Q.brandInk).accessibilityIdentifier("session.active")
                    QButton(title: model.c("ios.session.end"), icon: "stop.circle", secondary: true) { session.end() }
                        .accessibilityIdentifier("session.end")
                } else {
                    QButton(title: model.c("ios.session.start"), icon: "mic") { Task { await session.enable() } }
                        .disabled(!model.hasActiveKey || model.busy || model.canRetry || model.cleanupRequired)
                        .accessibilityIdentifier("session.start")
                }
            }
        }
    }
}
