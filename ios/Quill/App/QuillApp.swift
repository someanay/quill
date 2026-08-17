import SwiftUI
import UIKit

@main
struct QuillApp: App {
    var body: some Scene {
        WindowGroup { RootView() }
    }
}

struct RootView: View {
    @StateObject private var engine = DictationEngine()
    @State private var showOnboarding = false
    private let settingsStore = SettingsStore(paths: AppPaths())

    var body: some View {
        TabView {
            DictateView(engine: engine)
                .tabItem { Label("Dictate", systemImage: "mic.fill") }
            HistoryView()
                .tabItem { Label("History", systemImage: "clock") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .task {
            let settings = await settingsStore.load()
            showOnboarding = !settings.onboardingComplete
        }
        .fullScreenCover(isPresented: $showOnboarding) {
            OnboardingView { showOnboarding = false }
        }
    }
}

// In-app dictation: hold-free tap to start/stop, then copy or share the transcript. (System-wide
// insertion into other apps is delivered by the Quill keyboard extension — see ios/README.md.)
struct DictateView: View {
    @ObservedObject var engine: DictationEngine

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()
                Button {
                    Task { await engine.toggle(application: "Quill") }
                } label: {
                    ZStack {
                        Circle()
                            .fill(engine.isListening ? Color.red : Color.accentColor)
                            .frame(width: 132, height: 132)
                            .shadow(radius: 8)
                        Image(systemName: engine.isListening ? "stop.fill" : "mic.fill")
                            .font(.system(size: 52))
                            .foregroundStyle(.white)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(engine.isListening ? "Stop dictation" : "Start dictation")

                Text(engine.statusText)
                    .font(.headline)
                    .foregroundStyle(engine.phase == .error ? .red : .secondary)
                    .multilineTextAlignment(.center)

                if !engine.lastText.isEmpty {
                    ScrollView {
                        Text(engine.lastText)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    }
                    .frame(maxHeight: 180)
                    HStack(spacing: 16) {
                        Button { UIPasteboard.general.string = engine.lastText } label: {
                            Label("Copy", systemImage: "doc.on.doc")
                        }
                        ShareLink(item: engine.lastText) { Label("Share", systemImage: "square.and.arrow.up") }
                    }
                }
                Spacer()
            }
            .padding()
            .navigationTitle("Quill")
        }
    }
}
