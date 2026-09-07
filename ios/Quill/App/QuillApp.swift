import SwiftUI
import QuillCore

@main struct QuillApp: App {
    @StateObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    init() {
        #if DEBUG
        let api = UITestProvider.makeIfRequested() ?? ProviderAPI()
        #else
        let api = ProviderAPI()
        #endif
        _model = StateObject(wrappedValue: AppModel(providerAPI: api))
    }
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(model).tint(Q.brandInk)
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

#if DEBUG
/// Only a debug UI-test launch can install this synthetic transport. Request construction,
/// validation, Keychain storage and the complete production pipeline still execute normally.
private struct UITestProvider: HTTPTransport {
    static func makeIfRequested() -> ProviderAPI? {
        guard AppFiles.isUITesting, ProcessInfo.processInfo.arguments.contains("--ui-provider-fixtures") else { return nil }
        return ProviderAPI(transport: UITestProvider())
    }
    func send(_ request: URLRequest) async throws -> HTTPResult {
        guard request.url?.host == "api.groq.com", request.value(forHTTPHeaderField: "Authorization") == "Bearer gsk_quill_ui_fixture0000000000" else {
            return HTTPResult(data: Data("{}".utf8), status: 401)
        }
        let body: String
        switch request.url?.path {
        case "/openai/v1/models": body = #"{"data":[{"id":"whisper-large-v3-turbo"},{"id":"llama-3.3-70b-versatile"}]}"#
        case "/openai/v1/audio/transcriptions": body = #"{"text":"This is a simulated provider response."}"#
        case "/openai/v1/chat/completions": body = #"{"choices":[{"message":{"content":"This is a simulated provider response."},"finish_reason":"stop"}]}"#
        default: return HTTPResult(data: Data("{}".utf8), status: 404)
        }
        return HTTPResult(data: Data(body.utf8), status: 200)
    }
}
#endif

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
