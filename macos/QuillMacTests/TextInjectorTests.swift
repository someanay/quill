import XCTest
import ApplicationServices
import QuillCore
@testable import QuillMac

@MainActor private final class TestEditorAccess: AccessibilityAccess {
    var permitted = true
    var secureInputActive = false
    var process: pid_t = 123456
    var element = AXUIElementCreateApplication(123456)
    var text = "Hello"
    var selection: CFRange? = CFRange(location: 5, length: 0)
    var subrole = ""
    var acceptsSelection = true
    var mutatesSelection = true
    var mutatesOnFailure = false
    var writes = 0
    func frontmostApplication() -> FocusApplication? { FocusApplication(process: process, bundleID: "test.editor", name: "Test Editor") }
    func focusedElement(process: pid_t) -> AXUIElement? { element }
    func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        switch name {
        case kAXValueAttribute: return text as CFString
        case kAXRoleAttribute: return kAXTextAreaRole as CFString
        case kAXSubroleAttribute: return subrole as CFString
        case kAXSelectedTextRangeAttribute:
            guard var range = selection else { return nil }
            return AXValueCreate(.cfRange, &range)
        default: return nil
        }
    }
    func isSettable(_ element: AXUIElement, _ name: String) -> Bool { true }
    func set(_ element: AXUIElement, _ name: String, _ value: CFTypeRef) -> Bool {
        writes += 1
        if name == kAXSelectedTextAttribute {
            if (acceptsSelection && mutatesSelection) || mutatesOnFailure, let range = selection, let replacement = value as? String {
                text = (text as NSString).replacingCharacters(in: NSRange(location: range.location, length: range.length), with: replacement)
                selection = CFRange(location: range.location + replacement.utf16.count, length: 0)
            }
            return acceptsSelection
        }
        if name == kAXValueAttribute, let replacement = value as? String { text = replacement; return true }
        return true
    }
}

final class TextInjectorTests: XCTestCase {
    @MainActor func testSelectedTextWriteConfirmsExpectedAppend() throws {
        let editor = TestEditorAccess()
        let targetInjector = TextInjector(access: editor)
        XCTAssertEqual(try targetInjector.insert("world", target: targetInjector.snapshot()), .confirmed)
        XCTAssertEqual(editor.text, "Hello world"); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testEditorAcknowledgementWithoutMutationIsUnconfirmed() throws {
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        let injector = TextInjector(access: editor)
        XCTAssertEqual(try injector.insert("world", target: injector.snapshot()), .unconfirmed)
        XCTAssertEqual(editor.text, "Hello"); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testChangingApplicationPreventsAnyWrite() throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.process = 987654
        XCTAssertThrowsError(try injector.insert("world", target: target)) { XCTAssertEqual($0 as? QuillError, .focusChanged) }
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testChangedOrMissingSelectionPreventsAnyWrite() throws {
        for missing in [false, true] {
            let editor = TestEditorAccess(), injector: TextInjector
            injector = TextInjector(access: editor); let target = try injector.snapshot()
            editor.selection = missing ? nil : CFRange(location: 0, length: 0)
            XCTAssertThrowsError(try injector.insert("world", target: target)) { XCTAssertEqual($0 as? QuillError, .focusChanged) }
            XCTAssertEqual(editor.writes, 0)
        }
    }
    @MainActor func testChangedTextPreventsAnyWrite() throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.text = "Hello, edited"
        XCTAssertThrowsError(try injector.insert("world", target: target)) { XCTAssertEqual($0 as? QuillError, .focusChanged) }
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testInvalidEditorRangeCannotReplaceWholeDocument() throws {
        let editor = TestEditorAccess(); editor.selection = CFRange(location: 100, length: 50)
        let injector = TextInjector(access: editor)
        XCTAssertThrowsError(try injector.insert("world", target: injector.snapshot())) { XCTAssertEqual($0 as? QuillError, .focusChanged) }
        XCTAssertEqual(editor.text, "Hello"); XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testSecureFieldsAndRevokedPermissionAreRefused() throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.secureInputActive = true
        XCTAssertThrowsError(try injector.insert("world", target: target)) { XCTAssertEqual($0 as? QuillError, .sensitiveField) }
        editor.secureInputActive = false; editor.subrole = "AXSecureTextField"
        XCTAssertThrowsError(try injector.snapshot()) { XCTAssertEqual($0 as? QuillError, .sensitiveField) }
        editor.subrole = ""; editor.permitted = false
        XCTAssertThrowsError(try injector.insert("world", target: target)) { XCTAssertEqual($0 as? QuillError, .noAccessibility) }
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testFullValueFallbackPreservesSelectedReplacement() throws {
        let editor = TestEditorAccess(); editor.text = "Hello world"; editor.selection = CFRange(location: 6, length: 5); editor.acceptsSelection = false
        let injector = TextInjector(access: editor)
        XCTAssertEqual(try injector.insert("there", target: injector.snapshot()), .confirmed)
        XCTAssertEqual(editor.text, "Hello there")
    }
    @MainActor func testEditorMutationDespiteFailureDoesNotCauseSecondWrite() throws {
        let editor = TestEditorAccess(); editor.acceptsSelection = false; editor.mutatesOnFailure = true
        let injector = TextInjector(access: editor)
        XCTAssertThrowsError(try injector.insert("world", target: injector.snapshot())) { XCTAssertEqual($0 as? QuillError, .focusChanged) }
        XCTAssertEqual(editor.writes, 1); XCTAssertEqual(editor.text, "Hello world")
    }
}
