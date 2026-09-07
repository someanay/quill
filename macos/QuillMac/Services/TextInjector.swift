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
    func insert(_ transcript: String, target: FocusSnapshot?) async throws -> InsertionResult {
        try Task.checkCancellation()
        guard let target else { return .unavailable }
        guard try matches(target) else { throw QuillError.focusChanged }
        if let text = target.originalText, let range = target.originalRange {
            guard range.location >= 0, range.length >= 0, range.location <= text.utf16.count, range.length <= text.utf16.count - range.location else { throw QuillError.focusChanged }
        }
        let expected: String? = {
            guard let text = target.originalText, let range = target.originalRange, range.location >= 0, range.length >= 0 else { return nil }
            return InsertionComposer.compose(existing: text, transcript: transcript, selection: NSRange(location: range.location, length: range.length)).text
        }()
        // Writing the selection preserves native undo stacks where it is implemented.
        // Safari/WebKit acknowledges AXSelectedText but its base implementation is a
        // no-op. Choose the supported AXValue route before submitting any write; never
        // resend a selected-text write merely because confirmation is late or absent.
        let usesWebKit = ["com.apple.Safari", "com.apple.SafariTechnologyPreview"].contains(target.bundleID ?? "")
        let payload: String = {
            guard let expected, let before = target.originalText, let range = target.originalRange, range.location >= 0 else { return transcript }
            let length = expected.utf16.count - before.utf16.count + range.length
            guard length >= 0, range.location + length <= expected.utf16.count else { return transcript }
            return (expected as NSString).substring(with: NSRange(location: range.location, length: length))
        }()
        if !usesWebKit, access.isSettable(target.element, kAXSelectedTextAttribute) {
            // A failure/timeout can still represent a queued mutation. The Boolean
            // native adapter cannot prove non-submission, so do not try another write.
            guard access.set(target.element, kAXSelectedTextAttribute, payload as CFString) else { return .unconfirmed }
            return await confirm(expected, target: target)
        }
        guard let text = target.originalText, let selection = target.originalRange, selection.location >= 0, selection.length >= 0 else { return .unavailable }
        let composed = InsertionComposer.compose(existing: text, transcript: transcript, selection: NSRange(location: selection.location, length: selection.length))
        guard access.isSettable(target.element, kAXValueAttribute) else { return .unavailable }
        guard try matches(target) else { throw QuillError.focusChanged }
        guard access.set(target.element, kAXValueAttribute, composed.text as CFString) else { return .unconfirmed }
        let deadline = ProcessInfo.processInfo.systemUptime + 0.75
        guard await confirm(composed.text, target: target, deadline: deadline) == .confirmed,
              !Task.isCancelled, stillFocused(target), let currentRange = range(target.element) else { return .unconfirmed }
        if currentRange.location == composed.caret && currentRange.length == 0 { return .confirmed }
        // Wait for the value before placing the caret: WebKit can clamp a new end
        // offset against the old, shorter value. Preserve unrelated user selection.
        let unchangedSelection = currentRange.location == selection.location && currentRange.length == selection.length
        let automaticEnd = currentRange.location == composed.text.utf16.count && currentRange.length == 0
        guard unchangedSelection || automaticEnd, access.isSettable(target.element, kAXSelectedTextRangeAttribute) else { return .unconfirmed }
        var caret = CFRange(location: composed.caret, length: 0)
        guard let value = AXValueCreate(.cfRange, &caret), access.set(target.element, kAXSelectedTextRangeAttribute, value) else { return .unconfirmed }
        return await confirm(composed.text, target: target, caret: composed.caret, deadline: deadline)
    }
    private func stillFocused(_ target: FocusSnapshot) -> Bool {
        guard access.permitted, !access.secureInputActive,
              access.frontmostApplication()?.process == target.process,
              let current = access.focusedElement(process: target.process), CFEqual(current, target.element) else { return false }
        return !isSensitive(current)
    }
    private func confirm(_ expected: String?, target: FocusSnapshot, caret: Int? = nil, deadline: TimeInterval? = nil) async -> InsertionResult {
        guard let expected else { return .unconfirmed }
        let deadline = deadline ?? ProcessInfo.processInfo.systemUptime + 0.75
        while !Task.isCancelled {
            guard stillFocused(target) else { return .unconfirmed }
            let actual = string(target.element, kAXValueAttribute)
            if actual == expected {
                let currentRange = range(target.element)
                if caret == nil || (currentRange?.location == caret && currentRange?.length == 0) { return stillFocused(target) ? .confirmed : .unconfirmed }
            }
            // Preserve an unrelated edit rather than waiting for it to be overwritten.
            if let actual, actual != expected && actual != target.originalText { return .unconfirmed }
            guard ProcessInfo.processInfo.systemUptime < deadline else { return .unconfirmed }
            do { try await Task.sleep(nanoseconds: 25_000_000) }
            catch { return .unconfirmed }
        }
        return .unconfirmed
    }
    func paste(_ transcript: String, target: FocusSnapshot?) throws -> Bool {
        guard let target, try matches(target) else { throw QuillError.focusChanged }
        guard ClipboardLease.write(transcript, expires: true) else { return false }
        let clipboardChange = NSPasteboard.general.changeCount
        guard try matches(target) else { throw QuillError.focusChanged }
        guard NSPasteboard.general.changeCount == clipboardChange else { return false }
        guard let source = CGEventSource(stateID: .hidSystemState), let down = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: true), let up = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: false) else { return false }
        down.flags = .maskCommand; up.flags = .maskCommand; down.post(tap: .cghidEventTap); up.post(tap: .cghidEventTap)
        return true
    }
}

@MainActor enum ClipboardLease {
    private static var clearTask: Task<Void,Never>?
    @discardableResult static func write(_ text: String, expires: Bool = false) -> Bool {
        clearTask?.cancel()
        let clipboard = NSPasteboard.general
        clipboard.clearContents(); guard clipboard.setString(text, forType: .string) else { return false }
        guard expires else { return true }
        let change = clipboard.changeCount
        clearTask = Task { try? await Task.sleep(nanoseconds: 60_000_000_000); guard !Task.isCancelled, clipboard.changeCount == change else { return }; clipboard.clearContents() }
        return true
    }
}
