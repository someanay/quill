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
enum InsertionResult: Equatable { case confirmed, unconfirmed, unavailable }

@MainActor final class TextInjector {
    private let access: AccessibilityAccess
    init(access: AccessibilityAccess? = nil) { self.access = access ?? SystemAccessibilityAccess() }
    static var permitted: Bool { AXIsProcessTrusted() }
    static func requestPermission() { _ = AXIsProcessTrustedWithOptions([kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String:true] as CFDictionary) }
    private func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        access.attribute(element, name)
    }
    private func string(_ element: AXUIElement, _ name: String) -> String? { attribute(element, name) as? String }
    private func range(_ element: AXUIElement) -> CFRange? {
        guard let value = attribute(element, kAXSelectedTextRangeAttribute), CFGetTypeID(value) == AXValueGetTypeID() else { return nil }
        var range = CFRange(); guard AXValueGetValue(value as! AXValue, .cfRange, &range) else { return nil }; return range
    }
    private func isSensitive(_ element: AXUIElement) -> Bool {
        if access.secureInputActive { return true }
        let subrole = string(element, kAXSubroleAttribute)?.lowercased() ?? ""
        let role = string(element, kAXRoleAttribute)?.lowercased() ?? ""
        return subrole.contains("secure") || subrole.contains("password") || role.contains("secure")
    }
    func snapshot() throws -> FocusSnapshot? {
        guard access.permitted else { throw QuillError.noAccessibility }
        guard let app = access.frontmostApplication(), app.process != ProcessInfo.processInfo.processIdentifier else { return nil }
        guard let element = access.focusedElement(process: app.process) else { return nil }
        guard !isSensitive(element) else { throw QuillError.sensitiveField }
        let selectedSettable = access.isSettable(element, kAXSelectedTextAttribute), valueSettable = access.isSettable(element, kAXValueAttribute)
        let role = string(element, kAXRoleAttribute) ?? ""
        let text = string(element, kAXValueAttribute)
        guard selectedSettable || (valueSettable && text != nil) || [kAXTextFieldRole, kAXTextAreaRole, kAXComboBoxRole].contains(role) else { return nil }
        return FocusSnapshot(element: element, process: app.process, bundleID: app.bundleID, appName: app.name, originalText: text, originalRange: range(element))
    }
    private func matches(_ target: FocusSnapshot) throws -> Bool {
        guard access.permitted else { throw QuillError.noAccessibility }
        guard !isSensitive(target.element) else { throw QuillError.sensitiveField }
        guard let current = try snapshot(), current.process == target.process, CFEqual(current.element, target.element) else { return false }
        if let text = target.originalText, string(target.element, kAXValueAttribute) != text { return false }
        if let before = target.originalRange {
            guard let now = range(target.element), before.location == now.location, before.length == now.length else { return false }
        }
        return true
    }
    func insert(_ transcript: String, target: FocusSnapshot?) throws -> InsertionResult {
        guard let target else { return .unavailable }
        guard try matches(target) else { throw QuillError.focusChanged }
        if let text = target.originalText, let range = target.originalRange {
            guard range.location >= 0, range.length >= 0, range.location <= text.utf16.count, range.length <= text.utf16.count - range.location else { throw QuillError.focusChanged }
        }
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
        if access.set(target.element, kAXSelectedTextAttribute, payload as CFString) {
            return expected != nil && string(target.element, kAXValueAttribute) == expected ? .confirmed : .unconfirmed
        }
        guard let text = target.originalText, let selection = target.originalRange, selection.location >= 0, selection.length >= 0 else { return .unavailable }
        let composed = InsertionComposer.compose(existing: text, transcript: transcript, selection: NSRange(location: selection.location, length: selection.length))
        guard access.isSettable(target.element, kAXValueAttribute) else { return .unavailable }
        guard try matches(target) else { throw QuillError.focusChanged }
        guard access.set(target.element, kAXValueAttribute, composed.text as CFString) else { return .unavailable }
        var caret = CFRange(location: composed.caret, length: 0)
        if let value = AXValueCreate(.cfRange, &caret) { _ = access.set(target.element, kAXSelectedTextRangeAttribute, value) }
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
