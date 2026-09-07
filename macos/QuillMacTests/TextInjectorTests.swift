import XCTest
import ApplicationServices
import QuillCore
@testable import QuillMac

@MainActor final class TestEditorAccess: AccessibilityAccess {
    var permitted = true
    var secureInputActive = false
    var process: pid_t = 123456
    var element = AXUIElementCreateApplication(123456)
    var text = "Hello"
    var selection: CFRange? = CFRange(location: 5, length: 0)
    var subrole = ""
    var bundleID = "test.editor"
    var selectionWritable = true
    var valueWritable = true
    var selectionDelay: UInt64 = 0
    var valueDelay: UInt64 = 0
    var afterValueWrite: (() -> Void)?
    var acceptsSelection = true
    var acceptsValue = true
    var mutatesSelection = true
    var mutatesOnFailure = false
    var writes = 0
    var writtenAttributes: [String] = []
    var rangeWriteValues: [String] = []
    func frontmostApplication() -> FocusApplication? { FocusApplication(process: process, bundleID: bundleID, name: "Test Editor") }
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
    func isSettable(_ element: AXUIElement, _ name: String) -> Bool { name == kAXValueAttribute ? valueWritable : name == kAXSelectedTextAttribute ? selectionWritable : true }
    func set(_ element: AXUIElement, _ name: String, _ value: CFTypeRef) -> Bool {
        writes += 1
        writtenAttributes.append(name)
        if name == kAXSelectedTextAttribute {
            if (acceptsSelection && mutatesSelection) || mutatesOnFailure, let range = selection, let replacement = value as? String {
                let result = (text as NSString).replacingCharacters(in: NSRange(location: range.location, length: range.length), with: replacement)
                let caret = CFRange(location: range.location + replacement.utf16.count, length: 0)
                if selectionDelay > 0 {
                    Task { @MainActor [weak self, selectionDelay] in try? await Task.sleep(nanoseconds: selectionDelay); self?.text = result; self?.selection = caret }
                } else { text = result; selection = caret }
            }
            return acceptsSelection
        }
        if name == kAXValueAttribute, let replacement = value as? String {
            if valueDelay > 0 { Task { @MainActor [weak self, valueDelay] in try? await Task.sleep(nanoseconds: valueDelay); self?.text = replacement } }
            else { text = replacement }
            afterValueWrite?()
            return acceptsValue
        }
        if name == kAXSelectedTextRangeAttribute { rangeWriteValues.append(text); var range = CFRange(); if AXValueGetValue(value as! AXValue, .cfRange, &range) { selection = range } }
        return true
    }
}

final class TextInjectorTests: XCTestCase {
    @MainActor func testSelectedTextWriteConfirmsExpectedAppend() async throws {
        let editor = TestEditorAccess()
        let targetInjector = TextInjector(access: editor)
        let result = try await targetInjector.insert("world", target: targetInjector.snapshot())
        XCTAssertEqual(result, .confirmed)
        XCTAssertEqual(editor.text, "Hello world"); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testEditorAcknowledgementWithoutMutationIsUnconfirmed() async throws {
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .unconfirmed)
        XCTAssertEqual(editor.text, "Hello"); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testChangingApplicationPreventsAnyWrite() async throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.process = 987654
        await expectError(.focusChanged, injector: injector, target: target)
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testChangedOrMissingSelectionPreventsAnyWrite() async throws {
        for missing in [false, true] {
            let editor = TestEditorAccess(), injector: TextInjector
            injector = TextInjector(access: editor); let target = try injector.snapshot()
            editor.selection = missing ? nil : CFRange(location: 0, length: 0)
            await expectError(.focusChanged, injector: injector, target: target)
            XCTAssertEqual(editor.writes, 0)
        }
    }
    @MainActor func testChangedTextPreventsAnyWrite() async throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.text = "Hello, edited"
        await expectError(.focusChanged, injector: injector, target: target)
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testInvalidEditorRangeCannotReplaceWholeDocument() async throws {
        let editor = TestEditorAccess(); editor.selection = CFRange(location: 100, length: 50)
        let injector = TextInjector(access: editor)
        await expectError(.focusChanged, injector: injector, target: try injector.snapshot())
        XCTAssertEqual(editor.text, "Hello"); XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testSecureFieldsAndRevokedPermissionAreRefused() async throws {
        let editor = TestEditorAccess(), injector: TextInjector
        injector = TextInjector(access: editor); let target = try injector.snapshot(); editor.secureInputActive = true
        await expectError(.sensitiveField, injector: injector, target: target)
        editor.secureInputActive = false; editor.subrole = "AXSecureTextField"
        XCTAssertThrowsError(try injector.snapshot()) { XCTAssertEqual($0 as? QuillError, .sensitiveField) }
        editor.subrole = ""; editor.permitted = false
        await expectError(.noAccessibility, injector: injector, target: target)
        XCTAssertEqual(editor.writes, 0)
    }
    @MainActor func testWritableValueRoutePreservesSelectedReplacement() async throws {
        let editor = TestEditorAccess(); editor.text = "Hello world"; editor.selection = CFRange(location: 6, length: 5); editor.selectionWritable = false
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("there", target: injector.snapshot())
        XCTAssertEqual(result, .confirmed)
        XCTAssertEqual(editor.text, "Hello there")
    }
    @MainActor func testEditorMutationDespiteFailureDoesNotCauseSecondWrite() async throws {
        let editor = TestEditorAccess(); editor.acceptsSelection = false; editor.mutatesOnFailure = true
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .unconfirmed)
        XCTAssertEqual(editor.writes, 1); XCTAssertEqual(editor.text, "Hello world")
    }
    @MainActor func testDelayedMutationAfterFailedSetterCannotSubmitASecondTextWrite() async throws {
        for safari in [false, true] {
            let editor = TestEditorAccess()
            if safari { editor.bundleID = "com.apple.Safari"; editor.acceptsValue = false; editor.valueDelay = 75_000_000 }
            else { editor.acceptsSelection = false; editor.mutatesOnFailure = true; editor.selectionDelay = 75_000_000 }
            let injector = TextInjector(access: editor)
            let result = try await injector.insert("world", target: injector.snapshot())
            XCTAssertEqual(result, .unconfirmed)
            try await Task.sleep(nanoseconds: 125_000_000)
            XCTAssertEqual(editor.text, "Hello world"); XCTAssertEqual(editor.writes, 1)
            XCTAssertEqual(editor.writtenAttributes, [safari ? kAXValueAttribute : kAXSelectedTextAttribute])
        }
    }
    @MainActor func testSafariUsesWritableWholeValueBeforeSubmittingAnySelectedText() async throws {
        let editor = TestEditorAccess(); editor.bundleID = "com.apple.Safari"; editor.mutatesSelection = false; editor.valueDelay = 75_000_000
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("भारत 🌍", target: injector.snapshot())
        XCTAssertEqual(result, .confirmed); XCTAssertEqual(editor.text, "Hello भारत 🌍")
        XCTAssertEqual(editor.writtenAttributes.filter { $0 == kAXValueAttribute }.count, 1)
        XCTAssertFalse(editor.writtenAttributes.contains(kAXSelectedTextAttribute))
        XCTAssertEqual(editor.selection?.location, editor.text.utf16.count)
        XCTAssertEqual(editor.rangeWriteValues.first, "Hello भारत 🌍", "Caret placement must wait for the new value to exist")
    }
    @MainActor func testSafariUnsupportedRichValueIsRetainedWithoutAnyWrite() async throws {
        let editor = TestEditorAccess(); editor.bundleID = "com.apple.Safari"; editor.valueWritable = false
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .unavailable); XCTAssertEqual(editor.writes, 0); XCTAssertEqual(editor.text, "Hello")
    }
    @MainActor func testReadOnlySelectedTextIsNotSubmitted() async throws {
        let editor = TestEditorAccess(); editor.selectionWritable = false
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .confirmed); XCTAssertFalse(editor.writtenAttributes.contains(kAXSelectedTextAttribute))
    }
    @MainActor func testDelayedSelectedTextReadbackConfirmsWithoutResending() async throws {
        let editor = TestEditorAccess(); editor.selectionDelay = 75_000_000
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .confirmed); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testFocusChangesDuringConfirmationNeverResend() async throws {
        let editor = TestEditorAccess(); editor.bundleID = "com.apple.Safari"; editor.valueDelay = 150_000_000
        let injector = TextInjector(access: editor)
        let focusChange = Task { @MainActor in try? await Task.sleep(nanoseconds: 40_000_000); editor.process = 987654 }
        defer { focusChange.cancel() }
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .unconfirmed)
        XCTAssertEqual(editor.writtenAttributes.filter { $0 == kAXValueAttribute }.count, 1)
        XCTAssertFalse(editor.writtenAttributes.contains(kAXSelectedTextAttribute))
    }
    @MainActor func testRevocationSecureFocusAndUnrelatedEditCannotBeConfirmed() async throws {
        for kind in 0..<4 {
            let editor = TestEditorAccess(); editor.bundleID = "com.apple.Safari"; editor.valueDelay = 150_000_000
            editor.afterValueWrite = { [weak editor] in
                if kind == 0 { editor?.permitted = false }
                else if kind == 1 { editor?.secureInputActive = true }
                else if kind == 2 { editor?.text = "manual edit" }
                else { editor?.element = AXUIElementCreateApplication(654321) }
            }
            let injector = TextInjector(access: editor)
            let result = try await injector.insert("world", target: injector.snapshot())
            XCTAssertEqual(result, .unconfirmed)
            XCTAssertEqual(editor.writtenAttributes.filter { $0 == kAXValueAttribute }.count, 1)
            XCTAssertFalse(editor.writtenAttributes.contains(kAXSelectedTextAttribute))
        }
    }
    @MainActor func testCancellationDuringConfirmationDoesNotResendAcceptedWrite() async throws {
        let editor = TestEditorAccess(); editor.mutatesSelection = false
        let injector = TextInjector(access: editor), target = try TextInjector(access: editor).snapshot()
        let insertion = Task { @MainActor in try await injector.insert("world", target: target) }
        try await Task.sleep(nanoseconds: 40_000_000); insertion.cancel()
        let result = try await insertion.value
        XCTAssertEqual(result, .unconfirmed); XCTAssertEqual(editor.writes, 1)
    }
    @MainActor func testUnrelatedSelectionDuringValueConfirmationIsNotOverwritten() async throws {
        let editor = TestEditorAccess(); editor.bundleID = "com.apple.Safari"; editor.valueDelay = 75_000_000
        editor.afterValueWrite = { [weak editor] in editor?.selection = CFRange(location: 1, length: 0) }
        let injector = TextInjector(access: editor)
        let result = try await injector.insert("world", target: injector.snapshot())
        XCTAssertEqual(result, .unconfirmed); XCTAssertEqual(editor.selection?.location, 1)
        XCTAssertFalse(editor.writtenAttributes.contains(kAXSelectedTextRangeAttribute))
        XCTAssertEqual(editor.writtenAttributes.filter { $0 == kAXValueAttribute }.count, 1)
    }
    @MainActor private func expectError(_ expected: QuillError, injector: TextInjector, target: FocusSnapshot?, file: StaticString = #filePath, line: UInt = #line) async {
        do { _ = try await injector.insert("world", target: target); XCTFail("Expected insertion refusal", file: file, line: line) }
        catch { XCTAssertEqual(error as? QuillError, expected, file: file, line: line) }
    }
}
