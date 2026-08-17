import AppKit

// Menu-bar status item — the macOS equivalent of the Windows TrayIconService (open, start/stop
// dictation, settings, quit + a live status tooltip).
final class MenuBarController: NSObject {
    private let statusItem: NSStatusItem
    private let onOpen: () -> Void
    private let onToggle: () -> Void
    private let onSettings: () -> Void
    private let onQuit: () -> Void

    init(onOpen: @escaping () -> Void, onToggle: @escaping () -> Void,
         onSettings: @escaping () -> Void, onQuit: @escaping () -> Void) {
        self.onOpen = onOpen; self.onToggle = onToggle; self.onSettings = onSettings; self.onQuit = onQuit
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        super.init()
        statusItem.button?.image = NSImage(systemSymbolName: "mic.circle", accessibilityDescription: "Quill")
        statusItem.button?.toolTip = "Quill — ready"

        let menu = NSMenu()
        menu.addItem(item("Open Quill", #selector(open)))
        menu.addItem(item("Start / stop dictation", #selector(toggle)))
        menu.addItem(item("Settings…", #selector(settings)))
        menu.addItem(.separator())
        menu.addItem(item("Quit Quill", #selector(quit)))
        statusItem.menu = menu
    }

    func setStatus(_ text: String) { statusItem.button?.toolTip = String(text.prefix(120)) }

    private func item(_ title: String, _ action: Selector) -> NSMenuItem {
        let i = NSMenuItem(title: title, action: action, keyEquivalent: "")
        i.target = self
        return i
    }

    @objc private func open() { onOpen() }
    @objc private func toggle() { onToggle() }
    @objc private func settings() { onSettings() }
    @objc private func quit() { onQuit() }
}
