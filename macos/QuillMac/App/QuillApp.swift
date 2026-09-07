import SwiftUI
import AppKit
import Carbon
import Combine
import QuillCore

@main struct QuillMacApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene { Settings { EmptyView() } }
}

@MainActor final class AppDelegate: NSObject, NSApplicationDelegate {
    let settings = SettingsStore()
    let keys = KeychainStore()
    let history = HistoryStore()
    lazy var dictation = Dictation(settings: settings, keys: keys, history: history)
    lazy var crashReports = CrashReports(settings: settings)
    private var mainWindow: NSWindow?
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
        #endif
        let root = MainView().environmentObject(settings).environmentObject(keys).environmentObject(history).environmentObject(dictation).environmentObject(crashReports)
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 610, height: 790), styleMask: [.titled,.closable,.miniaturizable,.resizable], backing: .buffered, defer: false)
        window.title = "Quill"; window.identifier = NSUserInterfaceItemIdentifier("QuillMain"); window.contentView = NSHostingView(rootView: root); window.center(); window.isReleasedWhenClosed = false; mainWindow = window
        let menu = NSMenu(); let appMenu = NSMenu(); let item = NSMenuItem(); item.submenu = appMenu; menu.addItem(item)
        appMenu.addItem(withTitle: Copy.text("mac.menu.quit"), action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        let edit = NSMenu(title: "Edit"); let editItem = NSMenuItem(); editItem.submenu = edit; menu.addItem(editItem)
        for (title, action, key) in [("Undo", "undo:", "z"), ("Cut", "cut:", "x"), ("Copy", "copy:", "c"), ("Paste", "paste:", "v"), ("Select All", "selectAll:", "a")] { edit.addItem(withTitle: title, action: Selector(action), keyEquivalent: key) }
        NSApp.mainMenu = menu
        if !AppPaths.testMode {
            bubble = BubbleController(settings: settings, dictation: dictation)
            createStatusMenu(); hotkey = GlobalHotkey { [weak self] in self?.dictation.toggle() }
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
    }
    deinit { if let reference { UnregisterEventHotKey(reference) }; if let handler { RemoveEventHandler(handler) } }
}
