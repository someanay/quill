import SwiftUI
import AppKit
import Carbon
import Combine
import QuillCore

@main struct QuillMacApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene { Settings { EmptyView() }.commands { QuillCommands() } }
}

@MainActor struct QuillCommands: Commands {
    var body: some Commands {
        CommandGroup(replacing: .appSettings) {
            Button(Copy.text("nav.settings") + "…") { NotificationCenter.default.post(name: .quillOpenSettings, object: nil) }.keyboardShortcut(",", modifiers: .command)
        }
        CommandGroup(replacing: .newItem) {
            Button("Close Window") { NSApp.keyWindow?.performClose(nil) }.keyboardShortcut("w", modifiers: .command)
        }
        CommandGroup(replacing: .undoRedo) {
            Button("Undo") { NSApp.sendAction(Selector(("undo:")), to: nil, from: nil) }.keyboardShortcut("z", modifiers: .command)
            Button("Redo") { NSApp.sendAction(Selector(("redo:")), to: nil, from: nil) }.keyboardShortcut("z", modifiers: [.command, .shift])
        }
        CommandGroup(replacing: .pasteboard) {
            Button("Cut") { NSApp.sendAction(#selector(NSText.cut(_:)), to: nil, from: nil) }.keyboardShortcut("x", modifiers: .command)
            Button("Copy") { NSApp.sendAction(#selector(NSText.copy(_:)), to: nil, from: nil) }.keyboardShortcut("c", modifiers: .command)
            Button("Paste") { NSApp.sendAction(#selector(NSText.paste(_:)), to: nil, from: nil) }.keyboardShortcut("v", modifiers: .command)
            Button("Select All") { NSApp.sendAction(#selector(NSText.selectAll(_:)), to: nil, from: nil) }.keyboardShortcut("a", modifiers: .command)
        }
    }
}

@MainActor final class AppDelegate: NSObject, NSApplicationDelegate {
    let settings = SettingsStore()
    let keys = KeychainStore()
    let history = HistoryStore()
    lazy var dictation = Dictation(settings: settings, keys: keys, history: history)
    lazy var crashReports = CrashReports(settings: settings)
    private var mainWindow: NSWindow?
    private var settingsWindow: NSWindow?
    private var bubble: BubbleController?
    private var statusItem: NSStatusItem?
    private var hotkey: GlobalHotkey?
    private var statusSubscription: AnyCancellable?
    private var diagnosticsSubscription: Any?
    func applicationDidFinishLaunching(_ notification: Notification) {
        QuillTheme.registerFonts()
        NSApp.setActivationPolicy(.regular)
        if AppPaths.testMode { settings.value = QuillSettings(); history.clear() }
        #if DEBUG
        if AppPaths.testMode && ProcessInfo.processInfo.arguments.contains("--ui-settings") { settings.value.onboardingComplete = true }
        if AppPaths.testMode && ProcessInfo.processInfo.arguments.contains("--ui-dark") { settings.value.appTheme = "Dark" }
        if AppPaths.testMode && ProcessInfo.processInfo.arguments.contains("--ui-history-fixtures") {
            // Synthetic presentation data in the isolated UI-test store only.
            let paragraph = "Review the desktop onboarding, then send the revised plan to the team. Keep the keyboard shortcut easy to find and the transcript easy to read."
            for (index, text) in ["A short dictation for the weekly update.", Array(repeating: paragraph, count: 12).joined(separator: "\n\n")].enumerated() {
                history.insert(HistoryEntry(text: text, created: Date().addingTimeInterval(Double(index - 2) * 60), provider: .groq, appName: "Quill UI fixture", duration: 15), settings: settings.value)
            }
        }
        #endif
        let root = MainView().environmentObject(settings).environmentObject(keys).environmentObject(history).environmentObject(dictation).environmentObject(crashReports)
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1120, height: 760), styleMask: [.titled,.closable,.miniaturizable,.resizable], backing: .buffered, defer: false)
        if let area = NSScreen.main?.visibleFrame { window.setContentSize(NSSize(width: min(1120,area.width - 40), height: min(760,area.height - 80))) }
        NotificationCenter.default.addObserver(self, selector: #selector(showSettings), name: .quillOpenSettings, object: nil)
        window.title = "Quill"; window.identifier = NSUserInterfaceItemIdentifier("QuillMain"); window.contentView = NSHostingView(rootView: root); window.center(); window.isReleasedWhenClosed = false; mainWindow = window
        if !AppPaths.testMode {
            bubble = BubbleController(settings: settings, dictation: dictation)
            createStatusMenu(); hotkey = GlobalHotkey { [weak self] in self?.dictation.toggle() }
            dictation.shortcutAvailable = hotkey?.registered == true
            if hotkey?.registered == false { dictation.message = "mac.error.hotkey" }
            NSWorkspace.shared.notificationCenter.addObserver(self, selector: #selector(willSleep), name: NSWorkspace.willSleepNotification, object: nil)
            NSWorkspace.shared.notificationCenter.addObserver(self, selector: #selector(didWake), name: NSWorkspace.didWakeNotification, object: nil)
            if settings.value.onboardingComplete { dictation.configureLogin() }
        }
        if !settings.value.onboardingComplete || AppPaths.testMode || !ProcessInfo.processInfo.arguments.contains("--background") { showMain() }
        bubble?.refresh(); history.refresh(settings: settings.value)
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { showMain(); return true }
    func applicationWillTerminate(_ notification: Notification) { dictation.prepareForTermination(); crashReports.finish() }
    @objc func showSettings() {
        if settingsWindow == nil {
            let view = SettingsView().environmentObject(settings).environmentObject(keys).environmentObject(history).environmentObject(dictation).environmentObject(crashReports)
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1060, height: 740), styleMask: [.titled,.closable,.miniaturizable,.resizable], backing: .buffered, defer: false)
            if let area = NSScreen.main?.visibleFrame { window.setContentSize(NSSize(width: min(1060,area.width - 40), height: min(740,area.height - 80))) }
            window.title = "Quill " + Copy.text("nav.settings"); window.identifier = NSUserInterfaceItemIdentifier("QuillSettings")
            window.contentView = NSHostingView(rootView: view); window.isReleasedWhenClosed = false; window.center(); settingsWindow = window
        }
        settingsWindow?.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
    }
    @objc func showMain() { mainWindow?.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true) }
    @objc private func toggleRecording() { dictation.toggle() }
    @objc private func toggleBubble() { NotificationCenter.default.post(name: .quillExplicitVisibility, object: nil); settings.value.bubbleActive = true; settings.value.bubbleVisible.toggle() }
    @objc private func cancelRecording() { dictation.cancel() }
    @objc private func willSleep() { dictation.suspend() }
    @objc private func didWake() { dictation.refreshPermissions(); bubble?.refresh() }
    private func createStatusMenu() {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        item.button?.image = NSImage(systemSymbolName: "pencil.tip.crop.circle", accessibilityDescription: "Quill")
        let menu = NSMenu()
        var recordingItem: NSMenuItem?
        for (key, action) in [("mac.menu.open", #selector(showMain)), ("mac.dictation.start", #selector(toggleRecording)), ("mac.menu.toggle_bubble", #selector(toggleBubble)), ("action.cancel", #selector(cancelRecording))] { let entry = NSMenuItem(title: Copy.text(key), action: action, keyEquivalent: ""); entry.target = self; menu.addItem(entry); if key == "mac.dictation.start" { recordingItem = entry } }
        menu.addItem(.separator()); menu.addItem(withTitle: Copy.text("mac.menu.quit"), action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        item.menu = menu; statusItem = item
        statusSubscription = dictation.$state.sink { [weak item, weak recordingItem] state in
            let key = state == .recording ? "mac.dictation.stop" : state == .paste ? "mac.dictation.paste" : "mac.dictation.start"
            recordingItem?.title = Copy.text(key); recordingItem?.isEnabled = state != .transcribing
            item?.button?.setAccessibilityLabel("Quill · " + Copy.text(key))
            item?.button?.contentTintColor = state == .recording ? NSColor(QuillTheme.brand) : state == .error ? .systemRed : nil
        }
    }
}

@MainActor final class GlobalHotkey {
    #if DEBUG
    private(set) static var startupRegistrationSucceeded: Bool?
    #endif
    private var reference: EventHotKeyRef?
    private var handler: EventHandlerRef?
    private let action: () -> Void
    private(set) var registered = false
    init(action: @escaping () -> Void) {
        self.action = action
        var type = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
        InstallEventHandler(GetApplicationEventTarget(), { _, _, context -> OSStatus in
            guard let context else { return OSStatus(eventNotHandledErr) }
            let owner = Unmanaged<GlobalHotkey>.fromOpaque(context).takeUnretainedValue()
            Task { @MainActor in owner.action() }
            return noErr
        }, 1, &type, Unmanaged.passUnretained(self).toOpaque(), &handler)
        let id = EventHotKeyID(signature: OSType(0x514C4C31), id: 1)
        registered = RegisterEventHotKey(UInt32(kVK_Space), UInt32(cmdKey | optionKey), id, GetApplicationEventTarget(), 0, &reference) == noErr
        #if DEBUG
        Self.startupRegistrationSucceeded = registered
        #endif
    }
    deinit { if let reference { UnregisterEventHotKey(reference) }; if let handler { RemoveEventHandler(handler) } }
}
