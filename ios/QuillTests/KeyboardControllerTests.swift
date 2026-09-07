import XCTest
import UIKit
import QuillCore

@MainActor private final class TextProxyFixture: NSObject, UITextDocumentProxy {
    var content = ""
    var selection = NSRange(location: 0, length: 0)
    var isSecureTextEntry = false
    var keyboardType: UIKeyboardType = .default
    let documentIdentifier = UUID()
    var documentInputMode: UITextInputMode? { nil }
    var documentContextBeforeInput: String? { (content as NSString).substring(to: selection.location) }
    var documentContextAfterInput: String? { (content as NSString).substring(from: NSMaxRange(selection)) }
    var selectedText: String? { selection.length > 0 ? (content as NSString).substring(with: selection) : nil }
    var hasText: Bool { !content.isEmpty }
    func insertText(_ text: String) {
        content = (content as NSString).replacingCharacters(in: selection, with: text)
        selection = NSRange(location: selection.location + (text as NSString).length, length: 0)
    }
    func deleteBackward() {
        if selection.length > 0 { insertText("") }
        else if selection.location > 0 { selection = NSRange(location: selection.location - 1, length: 1); insertText("") }
    }
    func adjustTextPosition(byCharacterOffset offset: Int) { selection = NSRange(location: max(0, min((content as NSString).length, selection.location + offset)), length: 0) }
    func setMarkedText(_ markedText: String, selectedRange: NSRange) { insertText(markedText) }
    func unmarkText() { }
}

@MainActor private final class HandoffFixture {
    var item: KeyboardHandoff? = KeyboardHandoff(text: "world")
    var fullAccess = true
    var fieldAvailable = true
    var failConsume = false
    var consumed = 0
    func consume(_ id: UUID) throws -> String {
        if failConsume { throw QuillError.storage }
        guard let item, item.id == id, item.isValid() else { throw QuillError.expired }
        self.item = nil; consumed += 1; return item.text
    }
}

@MainActor final class KeyboardControllerTests: XCTestCase {
    private func harness(_ proxy: TextProxyFixture, _ store: HandoffFixture) -> KeyboardViewController {
        let keyboard = KeyboardViewController()
        keyboard.environment = KeyboardEnvironment(proxy: { store.fieldAvailable ? proxy : nil }, fullAccess: { store.fullAccess }, peek: { store.item }, consume: store.consume, prune: {})
        keyboard.loadViewIfNeeded(); return keyboard
    }
    private func find<T: UIView>(_ type: T.Type, identifier: String, in view: UIView) -> T? {
        if view.accessibilityIdentifier == identifier { return view as? T }
        for child in view.subviews { if let found = find(type, identifier: identifier, in: child) { return found } }
        return nil
    }
    private func insert(_ keyboard: KeyboardViewController) throws -> UIButton { try XCTUnwrap(find(UIButton.self, identifier: "keyboard.insert", in: keyboard.view)) }
    func testRealControllerAppendAndConsumeOnce() throws {
        let proxy = TextProxyFixture(); proxy.content = "Hello"; proxy.selection.location = 5
        let store = HandoffFixture(); let keyboard = harness(proxy, store)
        let button = try insert(keyboard); XCTAssertTrue(button.isEnabled); button.sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, "Hello world"); XCTAssertEqual(store.consumed, 1)
        button.sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, "Hello world"); XCTAssertEqual(store.consumed, 1)
    }
    func testSelectedTextIsReplacedAndMiddleHasNoAddedSpace() throws {
        let proxy = TextProxyFixture(); proxy.content = "Hello friend!"; proxy.selection = NSRange(location: 6, length: 6)
        let store = HandoffFixture(); let keyboard = harness(proxy, store)
        try insert(keyboard).sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, "Hello world!")
        store.item = KeyboardHandoff(text: "X"); proxy.content = "ab cd"; proxy.selection = NSRange(location: 3, length: 0)
        keyboard.textDidChange(nil); try insert(keyboard).sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, "ab Xcd")
    }
    func testCaretAtStartPreservesExistingContent() throws {
        let proxy = TextProxyFixture(); proxy.content = "existing"; let store = HandoffFixture()
        let keyboard = harness(proxy, store); try insert(keyboard).sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, "worldexisting")
    }
    func testExpiredMissingAndRevokedFullAccessDoNotConsume() throws {
        for state in 0..<3 {
            let proxy = TextProxyFixture(), store = HandoffFixture()
            if state == 0 { store.item = KeyboardHandoff(text: "old", now: Date().addingTimeInterval(-61)) }
            if state == 1 { store.item = nil }
            let keyboard = harness(proxy, store)
            if state == 2 { store.fullAccess = false; keyboard.textDidChange(nil) }
            let button = try insert(keyboard); XCTAssertFalse(button.isEnabled); button.sendActions(for: .touchUpInside)
            XCTAssertEqual(proxy.content, ""); XCTAssertEqual(store.consumed, 0)
        }
    }
    func testSecurePhonePadAndUnavailableFieldStayUntouched() throws {
        for state in 0..<3 {
            let proxy = TextProxyFixture(), store = HandoffFixture()
            if state == 0 { proxy.isSecureTextEntry = true }
            if state == 1 { proxy.keyboardType = .phonePad }
            if state == 2 { store.fieldAvailable = false }
            let keyboard = harness(proxy, store); let button = try insert(keyboard)
            XCTAssertFalse(button.isEnabled); button.sendActions(for: .touchUpInside)
            XCTAssertEqual(proxy.content, ""); XCTAssertEqual(store.consumed, 0)
        }
    }
    func testAppGroupFailureLeavesTranscriptRecoverable() throws {
        let proxy = TextProxyFixture(), store = HandoffFixture(); store.failConsume = true
        let keyboard = harness(proxy, store); try insert(keyboard).sendActions(for: .touchUpInside)
        XCTAssertEqual(proxy.content, ""); XCTAssertNotNil(store.item)
        XCTAssertEqual(store.consumed, 0)
    }
    func testKeyboardCompactWidthInLightAndDark() throws {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let keyboard = harness(TextProxyFixture(), HandoffFixture())
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 420))
            window.overrideUserInterfaceStyle = style
            window.rootViewController = keyboard; window.isHidden = false
            keyboard.overrideUserInterfaceStyle = style
            defer { window.isHidden = true; window.rootViewController = nil }
            XCTAssertEqual(keyboard.traitCollection.userInterfaceStyle, style)
            let size = keyboard.view.systemLayoutSizeFitting(CGSize(width: 320, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
            XCTAssertLessThan(size.height, 550)
            keyboard.view.frame = CGRect(origin: .zero, size: size)
            keyboard.view.setNeedsLayout(); keyboard.view.layoutIfNeeded()
            var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
            keyboard.view.backgroundColor?.resolvedColor(with: keyboard.traitCollection).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            if style == .dark { XCTAssertLessThan(red, 0.1) } else { XCTAssertGreaterThan(red, 0.9) }
            let button = try insert(keyboard)
            XCTAssertTrue(keyboard.view.bounds.contains(button.convert(button.bounds, to: keyboard.view)))
            let screenshot = UIGraphicsImageRenderer(size: size).image { context in keyboard.view.layer.render(in: context.cgContext) }
            let attachment = XCTAttachment(image: screenshot)
            attachment.name = "Keyboard compact width " + (style == .dark ? "dark" : "light")
            attachment.lifetime = .keepAlways; add(attachment)
        }
    }
}
