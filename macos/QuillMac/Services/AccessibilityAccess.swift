import AppKit
import ApplicationServices
import Carbon

struct FocusApplication {
    let process: pid_t
    let bundleID: String?
    let name: String
}

// Keeping the native calls behind this interface lets tests exercise the real insertion
// decisions, including editors which acknowledge writes without actually changing text.
@MainActor protocol AccessibilityAccess {
    var permitted: Bool { get }
    var secureInputActive: Bool { get }
    func frontmostApplication() -> FocusApplication?
    func focusedElement(process: pid_t) -> AXUIElement?
    func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef?
    func isSettable(_ element: AXUIElement, _ name: String) -> Bool
    func set(_ element: AXUIElement, _ name: String, _ value: CFTypeRef) -> Bool
}

@MainActor final class SystemAccessibilityAccess: AccessibilityAccess {
    var permitted: Bool { AXIsProcessTrusted() }
    var secureInputActive: Bool { IsSecureEventInputEnabled() }
    func frontmostApplication() -> FocusApplication? {
        guard let app = NSWorkspace.shared.frontmostApplication else { return nil }
        return FocusApplication(process: app.processIdentifier, bundleID: app.bundleIdentifier, name: app.localizedName ?? "")
    }
    func focusedElement(process: pid_t) -> AXUIElement? {
        guard let value = attribute(AXUIElementCreateApplication(process), kAXFocusedUIElementAttribute), CFGetTypeID(value) == AXUIElementGetTypeID() else { return nil }
        return value as! AXUIElement
    }
    func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        var result: CFTypeRef?
        return AXUIElementCopyAttributeValue(element, name as CFString, &result) == .success ? result : nil
    }
    func isSettable(_ element: AXUIElement, _ name: String) -> Bool {
        var settable: DarwinBoolean = false
        return AXUIElementIsAttributeSettable(element, name as CFString, &settable) == .success && settable.boolValue
    }
    func set(_ element: AXUIElement, _ name: String, _ value: CFTypeRef) -> Bool {
        AXUIElementSetAttributeValue(element, name as CFString, value) == .success
    }
}
