import AppKit
import ApplicationServices
import Carbon
import QuillCore

struct FocusSnapshot {
    let element: AXUIElement
    let process: pid_t
    let bundleID: String?
    let appName: String
    let originalText: String?
    let originalRange: CFRange?
}
enum InsertionResult { case confirmed, unconfirmed, unavailable }

@MainActor final class TextInjector {
    static var permitted: Bool { AXIsProcessTrusted() }
    static func requestPermission() { _ = AXIsProcessTrustedWithOptions([kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String:true] as CFDictionary) }
    private func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        var result: CFTypeRef?
        return AXUIElementCopyAttributeValue(element, name as CFString, &result) == .success ? result : nil
    }
    private func string(_ element: AXUIElement, _ name: String) -> String? { attribute(element, name) as? String }
    private func range(_ element: AXUIElement) -> CFRange? {
        guard let value = attribute(element, kAXSelectedTextRangeAttribute), CFGetTypeID(value) == AXValueGetTypeID() else { return nil }
        var range = CFRange(); guard AXValueGetValue(value as! AXValue, .cfRange, &range) else { return nil }; return range
    }
    private func isSensitive(_ element: AXUIElement) -> Bool {
        if IsSecureEventInputEnabled() { return true }
        let subrole = string(element, kAXSubroleAttribute)?.lowercased() ?? ""
        let role = string(element, kAXRoleAttribute)?.lowercased() ?? ""
        return subrole.contains("secure") || subrole.contains("password") || role.contains("secure")
    }
    func snapshot() throws -> FocusSnapshot? {
        guard Self.permitted else { throw QuillError.noAccessibility }
        guard let app = NSWorkspace.shared.frontmostApplication, app.processIdentifier != ProcessInfo.processInfo.processIdentifier else { return nil }
        let root = AXUIElementCreateApplication(app.processIdentifier)
        guard let value = attribute(root, kAXFocusedUIElementAttribute), CFGetTypeID(value) == AXUIElementGetTypeID() else { return nil }
        let element = value as! AXUIElement
        guard !isSensitive(element) else { throw QuillError.sensitiveField }
        return FocusSnapshot(element: element, process: app.processIdentifier, bundleID: app.bundleIdentifier, appName: app.localizedName ?? "", originalText: string(element, kAXValueAttribute), originalRange: range(element))
    }
    private func matches(_ target: FocusSnapshot) throws -> Bool {
        guard Self.permitted else { throw QuillError.noAccessibility }
        guard !isSensitive(target.element) else { throw QuillError.sensitiveField }
        guard let current = try snapshot(), current.process == target.process, CFEqual(current.element, target.element) else { return false }
        if let text = target.originalText, string(target.element, kAXValueAttribute) != text { return false }
        if let before = target.originalRange, let now = range(target.element), before.location != now.location || before.length != now.length { return false }
        return true
    }
    func insert(_ transcript: String, target: FocusSnapshot?) throws -> InsertionResult {
        guard let target else { return .unavailable }
        guard try matches(target) else { throw QuillError.focusChanged }
        let expected: String? = {
            guard let text = target.originalText, let range = target.originalRange, range.location >= 0, range.length >= 0 else { return nil }
            return InsertionComposer.compose(existing: text, transcript: transcript, selection: NSRange(location: range.location, length: range.length)).text
        }()
        // Writing the selection preserves native undo stacks in applications supporting this attribute.
        let payload: String = {
            guard let expected, let before = target.originalText, let range = target.originalRange, range.location >= 0 else { return transcript }
            let length = expected.utf16.count - before.utf16.count + range.length
            guard length >= 0, range.location + length <= expected.utf16.count else { return transcript }
            return (expected as NSString).substring(with: NSRange(location: range.location, length: length))
        }()
        if AXUIElementSetAttributeValue(target.element, kAXSelectedTextAttribute as CFString, payload as CFString) == .success {
            return expected != nil && string(target.element, kAXValueAttribute) == expected ? .confirmed : .unconfirmed
        }
        guard let text = target.originalText, let selection = target.originalRange, selection.location >= 0, selection.length >= 0 else { return .unavailable }
        let composed = InsertionComposer.compose(existing: text, transcript: transcript, selection: NSRange(location: selection.location, length: selection.length))
        var settable: DarwinBoolean = false
        guard AXUIElementIsAttributeSettable(target.element, kAXValueAttribute as CFString, &settable) == .success, settable.boolValue else { return .unavailable }
        guard AXUIElementSetAttributeValue(target.element, kAXValueAttribute as CFString, composed.text as CFString) == .success else { return .unavailable }
        var caret = CFRange(location: composed.caret, length: 0)
        if let value = AXValueCreate(.cfRange, &caret) { _ = AXUIElementSetAttributeValue(target.element, kAXSelectedTextRangeAttribute as CFString, value) }
        return string(target.element, kAXValueAttribute) == composed.text ? .confirmed : .unconfirmed
    }
    func paste(_ transcript: String, target: FocusSnapshot?) throws -> Bool {
        guard let target, try matches(target) else { throw QuillError.focusChanged }
        ClipboardLease.write(transcript, expires: true)
        guard try matches(target) else { throw QuillError.focusChanged }
        guard let source = CGEventSource(stateID: .hidSystemState), let down = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: true), let up = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: false) else { return false }
        down.flags = .maskCommand; up.flags = .maskCommand; down.post(tap: .cghidEventTap); up.post(tap: .cghidEventTap)
        return true
    }
}

@MainActor enum ClipboardLease {
    private static var clearTask: Task<Void,Never>?
    static func write(_ text: String, expires: Bool = false) {
        clearTask?.cancel()
        let clipboard = NSPasteboard.general
        clipboard.clearContents(); clipboard.setString(text, forType: .string)
        guard expires else { return }
        let change = clipboard.changeCount
        clearTask = Task { try? await Task.sleep(nanoseconds: 60_000_000_000); guard !Task.isCancelled, clipboard.changeCount == change else { return }; clipboard.clearContents() }
    }
}
