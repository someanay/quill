import XCTest

/// Runs separately from the normal suite. This uses the real Settings process, App Group,
/// keyboard extension and a second native host application; no injected text proxy is involved.
final class KeyboardIntegrationUITests: XCTestCase {
    private let host = XCUIApplication(bundleIdentifier: "com.tihor.quill.ios")
    private let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
    private let fixture = XCUIApplication(bundleIdentifier: "com.tihor.quill.ios.qa.keyboard-field")
    private var stage = "starting"
    private var outcome = "incomplete"
    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
    }
    override func tearDownWithError() throws {
        attach("final-" + stage, app: fixture)
        let report = XCTAttachment(string: "status=\(outcome)\nstage=\(stage)\ntransport=DEBUG synthetic provider; real microphone and installed keyboard extension\n")
        report.name = "Keyboard integration outcome"; report.lifetime = .keepAlways; add(report)
        fixture.terminate(); host.terminate(); settings.terminate()
    }
    private func attach(_ name: String, app: XCUIApplication) {
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = name + " hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
    }
    private func blocked(_ reason: String, app: XCUIApplication) throws -> Never {
        outcome = "blocked: " + reason
        attach("blocked-" + stage, app: app)
        throw XCTSkip(reason)
    }
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 10))
        for _ in 0..<6 { if element.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(element.isHittable); element.tap()
    }
    private func settingsRow(_ title: String, prefix: Bool = false) -> XCUIElement {
        let predicate = NSPredicate(format: prefix ? "label BEGINSWITH[c] %@" : "label == %@", title)
        let text = settings.staticTexts.matching(predicate).firstMatch
        let cell = settings.cells.containing(.staticText, identifier: title).firstMatch
        if cell.exists { return cell }
        let button = settings.buttons.matching(predicate).firstMatch
        return button.exists ? button : text
    }
    private func openSettingsRow(_ title: String, prefix: Bool = false) throws {
        for _ in 0..<8 {
            let row = settingsRow(title, prefix: prefix)
            if row.exists && row.isHittable { row.tap(); return }
            settings.swipeUp()
        }
        try blocked("Simulator Settings did not expose a reachable \(title) row.", app: settings)
    }
    private func completeOnboarding() throws {
        stage = "synthetic-key-onboarding"
        host.launchArguments = ["--ui-testing", "--ui-provider-fixtures"]
        host.launch()
        tap(host.buttons["onboarding.getStarted"], in: host)
        let next = host.buttons["Help me get & add a key"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: next)
        waitForExpectations(timeout: 7)
        tap(next, in: host)
        tap(host.buttons["key.provider.groq"], in: host)
        let key = host.secureTextFields["API key"]
        tap(key, in: host); key.typeText("gsk_quill_ui_fixture0000000000")
        XCTAssertTrue(host.descendants(matching: .any).matching(identifier: "key.verified").firstMatch.waitForExistence(timeout: 10))
        tap(host.buttons["onboarding.key.verify"], in: host)
        tap(host.buttons["onboarding.permission"], in: host)
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 3) {
            let allow = alert.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] 'Allow' OR label == 'OK'")).firstMatch
            XCTAssertTrue(allow.exists); allow.tap()
        }
        tap(host.buttons["onboarding.microphone.next"], in: host)
        tap(host.buttons["onboarding.record.next"], in: host)
        tap(host.buttons["onboarding.keyboard.later"], in: host)
        tap(host.buttons["onboarding.finish"], in: host)
        XCTAssertTrue(host.buttons["dictation.record"].waitForExistence(timeout: 10))
    }
    private func enableKeyboardThroughSettings() throws {
        stage = "enable-keyboard-in-settings"
        settings.launch()
        try openSettingsRow("General")
        try openSettingsRow("Keyboard")
        try openSettingsRow("Keyboards")
        if !settingsRow("Quill", prefix: true).exists {
            try openSettingsRow("Add New Keyboard", prefix: true)
            try openSettingsRow("Quill", prefix: true)
        }
        try openSettingsRow("Quill", prefix: true)
        let fullAccess = settings.switches.matching(NSPredicate(format: "label CONTAINS[c] 'Allow Full Access'")).firstMatch
        guard fullAccess.waitForExistence(timeout: 5) else {
            try blocked("Quill was listed, but Simulator Settings did not expose its Full Access switch.", app: settings)
        }
        if fullAccess.value as? String != "1" {
            fullAccess.tap()
            let alert = settings.alerts.firstMatch
            if alert.waitForExistence(timeout: 3) { tap(alert.buttons["Allow"], in: settings) }
        }
        XCTAssertEqual(fullAccess.value as? String, "1")
        attach("keyboard-full-access-enabled", app: settings)
    }
    private func showQuillKeyboard() throws {
        for _ in 0..<8 {
            if fixture.buttons["keyboard.insert"].waitForExistence(timeout: 2) { return }
            let globe = fixture.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Next keyboard' OR label CONTAINS[c] 'Next Keyboard' OR identifier == 'Next keyboard'")).firstMatch
            if globe.exists && globe.isHittable { globe.tap(); continue }
            let language = fixture.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Switch keyboard' OR label CONTAINS[c] 'Change keyboard'")).firstMatch
            if language.exists && language.isHittable { language.tap(); continue }
            try blocked("The real fixture field did not expose a system keyboard-switch key; hardware keyboard or Simulator keyboard selection may be unavailable.", app: fixture)
        }
        try blocked("Cycling the real system keyboard selector did not expose the installed Quill extension.", app: fixture)
    }
    func testRealRecordingHandoffIntoSeparateAppThroughInstalledKeyboard() throws {
        try completeOnboarding()
        try enableKeyboardThroughSettings()
        stage = "prepare-separate-native-field"
        fixture.launch()
        let editor = fixture.textViews["quill.qa.editor"]
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        XCTAssertEqual(editor.value as? String, "Before")
        tap(editor, in: fixture)
        try showQuillKeyboard()
        attach("real-keyboard-before-handoff", app: fixture)

        stage = "record-and-publish"
        host.activate()
        let record = host.buttons["dictation.record"]
        tap(record, in: host)
        XCTAssertTrue(host.buttons["Stop and transcribe"].waitForExistence(timeout: 5))
        RunLoop.current.run(until: Date().addingTimeInterval(1.5))
        tap(record, in: host)
        let send = host.buttons["Send to keyboard"]
        XCTAssertTrue(send.waitForExistence(timeout: 15))
        tap(send, in: host)
        attach("host-published-real-handoff", app: host)

        stage = "insert-through-installed-extension"
        fixture.activate()
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        tap(editor, in: fixture)
        try showQuillKeyboard()
        let insert = fixture.buttons["keyboard.insert"]
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: insert)
        waitForExpectations(timeout: 10)
        tap(insert, in: fixture)
        let expected = "Before This is a simulated provider response."
        expectation(for: NSPredicate(format: "value == %@", expected), evaluatedWith: editor)
        waitForExpectations(timeout: 5)
        XCTAssertEqual(editor.value as? String, expected)
        XCTAssertFalse(insert.isEnabled, "A consumed handoff must not be insertable again.")
        attach("real-extension-inserted-and-consumed", app: fixture)
        outcome = "passed"
    }
}
