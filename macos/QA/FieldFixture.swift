import AppKit

// Separate QA application: only fixed synthetic values, with no settings or saved state.
@main @MainActor struct FieldFixture {
    static func main() {
        let app = NSApplication.shared
        let delegate = FieldFixtureDelegate()
        app.setActivationPolicy(.regular)
        app.delegate = delegate
        withExtendedLifetime(delegate) { app.run() }
    }
}

@MainActor private final class FieldFixtureDelegate: NSObject, NSApplicationDelegate {
    private var window: NSWindow?

    func applicationDidFinishLaunching(_ notification: Notification) {
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 520, height: 250),
            styleMask: [.titled, .closable, .miniaturizable],
            backing: .buffered, defer: false
        )
        window.title = "Quill AX QA Fixture"
        window.isRestorable = false
        window.isReleasedWhenClosed = false

        let normal = NSTextField(string: "Quill QA normal")
        normal.setAccessibilityIdentifier("quill-qa-normal")
        normal.setAccessibilityLabel("Quill normal text field")
        normal.isEditable = true
        normal.isSelectable = true
        normal.font = .systemFont(ofSize: 17)

        let secure = NSSecureTextField(string: "synthetic-password-only")
        secure.setAccessibilityIdentifier("quill-qa-password")
        secure.setAccessibilityLabel("Quill password field")
        secure.isEditable = true
        secure.isSelectable = true
        secure.font = .systemFont(ofSize: 17)

        let content = NSStackView(views: [
            NSTextField(labelWithString: "Normal text field"), normal,
            NSTextField(labelWithString: "Secure text field — synthetic data"), secure
        ])
        content.orientation = .vertical
        content.alignment = .leading
        content.spacing = 12
        content.translatesAutoresizingMaskIntoConstraints = false
        let root = NSView()
        root.addSubview(content)
        window.contentView = root
        NSLayoutConstraint.activate([
            content.leadingAnchor.constraint(equalTo: root.leadingAnchor, constant: 24),
            content.trailingAnchor.constraint(equalTo: root.trailingAnchor, constant: -24),
            content.topAnchor.constraint(equalTo: root.topAnchor, constant: 24),
            normal.widthAnchor.constraint(equalTo: content.widthAnchor),
            secure.widthAnchor.constraint(equalTo: content.widthAnchor),
            normal.heightAnchor.constraint(greaterThanOrEqualToConstant: 30),
            secure.heightAnchor.constraint(greaterThanOrEqualToConstant: 30)
        ])
        self.window = window
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApplication.shared.activate(ignoringOtherApps: true)
        window.makeFirstResponder(CommandLine.arguments.contains("--secure") ? secure : normal)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func applicationShouldSaveApplicationState(_ app: NSApplication) -> Bool { false }
    func applicationShouldRestoreApplicationState(_ app: NSApplication) -> Bool { false }
}
