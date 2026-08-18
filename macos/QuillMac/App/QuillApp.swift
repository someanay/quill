import SwiftUI
import AppKit
import Combine

// Shared service container (mirrors windows/.../Core/AppServices.cs).
@MainActor
final class AppServices {
    let paths = AppPaths()
    let secrets = SecretStore()
    let injector = FocusedTextInjector()
    lazy var settingsStore = SettingsStore(paths: paths)
    lazy var history = HistoryStore(paths: paths)
    lazy var audio = AudioCaptureService(paths: paths)
    let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 45
        c.timeoutIntervalForResource = 120
        return URLSession(configuration: c)
    }()
    lazy var orchestrator = DictationOrchestrator(
        settingsStore: settingsStore, history: history, audio: audio, injector: injector, session: session)
    lazy var keyValidator = KeyValidator(session: session)
}

@main
struct QuillApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        // Real windows are managed by AppDelegate (menu-bar agent app); keep an empty Settings scene
        // so the app has a Scene without showing a main window.
        Settings { EmptyView() }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    let services = AppServices()
    private var menuBar: MenuBarController!
    private let hotkey = GlobalHotkeyService()
    private let hud = DictationHUD()
    private var settingsWindow: NSWindow?
    private var setupWindow: NSWindow?
    private var statusObserver: AnyCancellable?

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Skip agent/UI setup when hosting unit tests (CI): don't spin up the menu bar, global hotkey,
        // or the Accessibility prompt in a headless test run.
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil { return }
        menuBar = MenuBarController(
            onOpen: { [weak self] in self?.showSettings() },
            onToggle: { [weak self] in Task { await self?.services.orchestrator.toggle() } },
            onSettings: { [weak self] in self?.showSettings() },
            onQuit: { NSApp.terminate(nil) })

        hotkey.onPressed = { [weak self] in Task { await self?.services.orchestrator.toggle() } }

        statusObserver = services.orchestrator.$status.sink { [weak self] status in
            self?.menuBar.setStatus("Quill — \(status.title)")
            self?.hud.update(status)
        }

        Task { @MainActor in
            let settings = await self.services.settingsStore.load()
            _ = self.hotkey.register(settings.hotkey)
            if !settings.onboardingComplete { self.showSetup() }
            // Accessibility is required to type into other apps; nudge the user to grant it.
            if !self.services.injector.hasAccessibilityPermission() { self.services.injector.requestAccessibilityPermission() }
        }
    }

    func reloadHotkey() {
        Task { @MainActor in _ = self.hotkey.register(await self.services.settingsStore.load().hotkey) }
    }

    private func showSettings() {
        NSApp.activate(ignoringOtherApps: true)
        if let settingsWindow { settingsWindow.makeKeyAndOrderFront(nil); return }
        let view = SettingsView(services: services, onHotkeyChanged: { [weak self] in self?.reloadHotkey() })
        let window = Self.makeWindow(title: "Quill Settings", size: NSSize(width: 560, height: 640), content: view)
        window.delegate = WindowCloser { [weak self] in self?.settingsWindow = nil }
        settingsWindow = window
        window.makeKeyAndOrderFront(nil)
    }

    private func showSetup() {
        NSApp.activate(ignoringOtherApps: true)
        if let setupWindow { setupWindow.makeKeyAndOrderFront(nil); return }
        let view = SetupView(services: services) { [weak self] in
            self?.setupWindow?.close(); self?.setupWindow = nil; self?.reloadHotkey()
        }
        let window = Self.makeWindow(title: "Welcome to Quill", size: NSSize(width: 560, height: 620), content: view)
        window.delegate = WindowCloser { [weak self] in self?.setupWindow = nil }
        setupWindow = window
        window.makeKeyAndOrderFront(nil)
    }

    private static func makeWindow<Content: View>(title: String, size: NSSize, content: Content) -> NSWindow {
        let window = NSWindow(contentRect: NSRect(origin: .zero, size: size),
                              styleMask: [.titled, .closable, .miniaturizable], backing: .buffered, defer: false)
        window.title = title
        window.contentViewController = NSHostingController(rootView: content)
        window.isReleasedWhenClosed = false
        window.center()
        return window
    }
}

// Small NSWindowDelegate that fires a closure when the window closes (so we can drop our reference).
final class WindowCloser: NSObject, NSWindowDelegate {
    private let onClose: () -> Void
    init(_ onClose: @escaping () -> Void) { self.onClose = onClose }
    func windowWillClose(_ notification: Notification) { onClose() }
}

// Non-activating floating panel that shows dictation state WITHOUT stealing keyboard focus, so the
// underlying app keeps focus for the synthesized keystrokes. The macOS stand-in for the Windows
// non-activating dictation ribbon (and the Android overlay bubble's caption).
final class DictationHUD {
    private let panel: NSPanel
    private let label = NSTextField(labelWithString: "Quill")
    private var hideWork: DispatchWorkItem?

    init() {
        panel = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 300, height: 46),
                        styleMask: [.nonactivatingPanel, .hudWindow, .titled, .fullSizeContentView],
                        backing: .buffered, defer: true)
        panel.level = .floating
        panel.isFloatingPanel = true
        panel.hidesOnDeactivate = false
        panel.titleVisibility = .hidden
        panel.titlebarAppearsTransparent = true
        panel.becomesKeyOnlyIfNeeded = true
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .ignoresCycle]
        label.font = .systemFont(ofSize: 13, weight: .medium)
        label.alignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        let content = panel.contentView!
        content.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 14),
            label.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -14),
            label.centerYAnchor.constraint(equalTo: content.centerYAnchor),
        ])
    }

    func update(_ status: DictationOrchestrator.Status) {
        label.stringValue = status.detail.isEmpty ? status.title : "\(status.title) — \(status.detail)"
        switch status.state {
        case .idle, .error: scheduleHide()
        default: show()
        }
    }

    private func show() {
        hideWork?.cancel()
        if let screen = NSScreen.main {
            let f = screen.visibleFrame
            panel.setFrameOrigin(NSPoint(x: f.midX - panel.frame.width / 2, y: f.minY + 120))
        }
        panel.orderFrontRegardless()
    }

    private func scheduleHide() {
        show()
        let work = DispatchWorkItem { [weak self] in self?.panel.orderOut(nil) }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: work)
    }
}
