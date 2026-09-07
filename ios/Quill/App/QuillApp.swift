import SwiftUI

@main struct QuillApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(model).tint(Q.brand)
                .preferredColorScheme(model.settings.theme == "Dark" ? .dark : model.settings.theme == "Light" ? .light : nil)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { model.resume() }
                    if phase == .background && model.settings.privateMode && !model.busy { model.result = "" }
                }
                .onOpenURL { url in
                    guard url.scheme == "quill", url.host == "record" else { return }
                    model.tab = 0
                    // A URL opens the recorder screen. Recording still requires the visible user's tap.
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Group {
            if model.settings.onboardingComplete { MainTabs() }
            else { OnboardingView() }
        }.alert(model.c("ios.notice.title"), isPresented: Binding(get: { model.errorKey != nil }, set: { if !$0 { model.errorKey = nil } })) {
            Button(model.c("status.done"), role: .cancel) { model.errorKey = nil }
        } message: { Text(model.c(model.errorKey ?? "")) }
    }
}

struct MainTabs: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        TabView(selection: $model.tab) {
            NavigationStack { HomeView() }.tabItem { Label(model.c("nav.home"), systemImage: "house") }.tag(0)
            NavigationStack { HistoryView() }.tabItem { Label(model.c("nav.history"), systemImage: "clock") }.tag(1)
            NavigationStack { SettingsView() }.tabItem { Label(model.c("nav.settings"), systemImage: "gearshape") }.tag(2)
        }.font(Q.sans()).toolbarBackground(Q.surface, for: .tabBar)
    }
}
