import AppKit
import ApplicationServices

// Focused-field safety + text insertion. Mirrors windows/.../Platform/FocusedTextInjector.cs:
//  - CaptureTarget reads the system-wide focused element via the Accessibility API to name the app,
//    detect a secure (password) field, and detect whether a caret/editable field exists.
//  - Insert synthesizes Unicode key events (CGEvent) — the macOS equivalent of Windows' Unicode
//    SendInput — and falls back to the clipboard when typing is refused.
// Requires the user to grant Accessibility in System Settings › Privacy & Security › Accessibility.
struct FocusTarget { let application: String; let isSensitive: Bool; let hasEditableField: Bool }
struct InjectionResult { let inserted: Bool; let copiedToClipboard: Bool; let error: String? }

final class FocusedTextInjector {
    func hasAccessibilityPermission() -> Bool { AXIsProcessTrusted() }

    @discardableResult
    func requestAccessibilityPermission() -> Bool {
        // The prompt-option key is imported as CFString; bridge it to a String dictionary key.
        let options = [kAXTrustedCheckOptionPrompt as String: true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    func captureTarget() -> FocusTarget {
        let appName = NSWorkspace.shared.frontmostApplication?.localizedName ?? "Unknown app"
        var sensitive = false
        var editable = true // assume editable when AX can't answer (blind typing was the old behaviour)

        let system = AXUIElementCreateSystemWide()
        var focused: CFTypeRef?
        if AXUIElementCopyAttributeValue(system, kAXFocusedUIElementAttribute as CFString, &focused) == .success,
           let value = focused, CFGetTypeID(value) == AXUIElementGetTypeID() {
            let element = value as! AXUIElement
            let role = stringAttr(element, kAXRoleAttribute)
            let subrole = stringAttr(element, kAXSubroleAttribute)
            sensitive = (subrole == (kAXSecureTextFieldSubrole as String))
            editable = isEditable(element, role: role)
        }
        return FocusTarget(application: appName, isSensitive: sensitive, hasEditableField: editable)
    }

    private func isEditable(_ element: AXUIElement, role: String?) -> Bool {
        if role == (kAXTextFieldRole as String) || role == (kAXTextAreaRole as String) { return true }
        var settable: DarwinBoolean = false
        if AXUIElementIsAttributeSettable(element, kAXValueAttribute as CFString, &settable) == .success, settable.boolValue {
            return true
        }
        return false
    }

    func insert(_ target: FocusTarget, _ text: String) -> InjectionResult {
        if target.isSensitive { return .init(inserted: false, copiedToClipboard: false, error: "Quill never types into password fields.") }
        if text.isEmpty { return .init(inserted: false, copiedToClipboard: false, error: "There is no text to insert.") }
        if typeUnicode(text) { return .init(inserted: true, copiedToClipboard: false, error: nil) }
        return copyToClipboard(text, note: "Typing was blocked; the transcript was copied.")
    }

    func copyToClipboard(_ text: String, note: String = "Dictation copied to your clipboard.") -> InjectionResult {
        NSPasteboard.general.clearContents()
        let ok = NSPasteboard.general.setString(text, forType: .string)
        return .init(inserted: false, copiedToClipboard: ok, error: ok ? note : "Could not write to the clipboard.")
    }

    private func typeUnicode(_ text: String) -> Bool {
        guard let source = CGEventSource(stateID: .combinedSessionState) else { return false }
        let units = Array(text.utf16)
        let chunk = 20 // stay well under the per-event UniChar limit; also helps slower editors keep up
        var i = 0
        while i < units.count {
            let slice = Array(units[i..<min(i + chunk, units.count)])
            guard let down = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true),
                  let up = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false) else { return false }
            down.keyboardSetUnicodeString(stringLength: slice.count, unicodeString: slice)
            up.keyboardSetUnicodeString(stringLength: slice.count, unicodeString: slice)
            down.post(tap: .cgAnnotatedSessionEventTap)
            up.post(tap: .cgAnnotatedSessionEventTap)
            i += chunk
        }
        return true
    }

    private func stringAttr(_ element: AXUIElement, _ attribute: String) -> String? {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, attribute as CFString, &value) == .success else { return nil }
        return value as? String
    }
}
