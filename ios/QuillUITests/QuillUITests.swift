import XCTest

final class QuillUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    override func tearDownWithError() throws {
        let app = XCUIApplication()
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription); hierarchy.name = "UI hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        app.terminate()
    }
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 10))
        for _ in 0..<5 { if element.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(element.isHittable); element.tap()
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
    private func settingsTab(_ app: XCUIApplication) -> XCUIElement {
        let phoneTab = app.tabBars.buttons["Settings"]
        if phoneTab.exists { return phoneTab }
        // iPad's native floating tab strip exposes nested buttons, not a TabBar.
        // Both observed nodes have the same frame; firstMatch avoids ambiguity.
        return app.buttons.matching(identifier: "gearshape").matching(NSPredicate(format: "label == %@", "Settings")).firstMatch
    }
    private func openHome(arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"] + arguments; app.launch()
        tap(app.buttons["Open Quill"], in: app)
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 10))
        XCTAssertTrue(settingsTab(app).waitForExistence(timeout: 10))
        return app
    }
    func testWelcomeAndKeyWarning() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let start = app.buttons["onboarding.getStarted"]
        XCTAssertTrue(start.waitForExistence(timeout: 10)); start.tap()
        XCTAssertTrue(app.staticTexts["Bring your own key"].waitForExistence(timeout: 5))
        let next = app.buttons["Help me get & add a key"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        let predicate = NSPredicate(format: "enabled == true")
        expectation(for: predicate, evaluatedWith: next); waitForExpectations(timeout: 6)
        next.tap()
        XCTAssertTrue(app.secureTextFields["API key"].waitForExistence(timeout: 5))
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-onboarding-key"; shot.lifetime = .keepAlways; add(shot)
    }
    func testHomeSettingsPrivacyAndKeyboardHelp() throws {
        let app = openHome()
        settingsTab(app).tap()
        tap(app.buttons["settings.speech"], in: app)
        let language = app.staticTexts["speech.language.section"]
        for _ in 0..<4 { if language.exists && language.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(language.waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        tap(app.buttons["settings.privacy"], in: app)
        XCTAssertTrue(app.switches["Private mode"].waitForExistence(timeout: 5))
        app.switches["Private mode"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-privacy"; shot.lifetime = .keepAlways; add(shot)
    }
    func testSettingsScreensAndDarkAppearance() throws {
        let app = openHome()
        settingsTab(app).tap()
        tap(app.buttons["settings.visual"], in: app)
        app.buttons["Dark"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-dark-appearance"; shot.lifetime = .keepAlways; add(shot)
        XCTAssertTrue(app.buttons["Match system"].exists)
    }
    func testCompleteOnboardingWithSyntheticProviderAndRealMicrophonePermission() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-provider-fixtures"]; app.launch()
        tap(app.buttons["onboarding.getStarted"], in: app)
        let accept = app.buttons["Help me get & add a key"]
        XCTAssertTrue(accept.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: accept); waitForExpectations(timeout: 6)
        tap(accept, in: app)
        let key = app.secureTextFields["API key"]
        tap(key, in: app); key.typeText("quill-ui-fixture")
        tap(app.buttons["onboarding.key.verify"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.checklist"].waitForExistence(timeout: 10)); capture(app, "ios-setup-checklist")
        tap(app.buttons["onboarding.permission"], in: app)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let permission = springboard.alerts.firstMatch
        if permission.waitForExistence(timeout: 3) {
            let allow = permission.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] 'Allow' OR label == 'OK'")).firstMatch
            XCTAssertTrue(allow.exists); allow.tap()
        }
        XCTAssertTrue(app.staticTexts["onboarding.microphone"].waitForExistence(timeout: 10)); capture(app, "ios-setup-microphone")
        tap(app.buttons["onboarding.microphone.next"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.record"].waitForExistence(timeout: 5)); capture(app, "ios-setup-recording")
        tap(app.buttons["onboarding.record.next"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.keyboard"].waitForExistence(timeout: 5)); capture(app, "ios-setup-keyboard")
        tap(app.buttons["onboarding.keyboard.later"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.ready"].waitForExistence(timeout: 5)); capture(app, "ios-setup-ready")
        tap(app.buttons["onboarding.finish"], in: app)
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 10))
    }
    func testEverySettingsDetailScreen() throws {
        let app = openHome(); settingsTab(app).tap()
        for section in ["speech", "keys", "cleanup", "style", "words", "contacts", "audio", "visual", "keyboard", "privacy", "advanced", "about"] {
            tap(app.buttons["settings." + section], in: app)
            XCTAssertTrue(app.staticTexts["screen." + section].waitForExistence(timeout: 5), section)
            capture(app, "ios-settings-" + section)
            let back = app.navigationBars.buttons.element(boundBy: 0)
            XCTAssertTrue(back.waitForExistence(timeout: 5)); back.tap()
        }
    }
    func testLargerTextDarkSettingsAndLandscapeLayout() throws {
        let app = openHome(arguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        settingsTab(app).tap()
        tap(app.buttons["settings.visual"], in: app)
        tap(app.buttons["Dark"], in: app); capture(app, "ios-large-text-dark")
        XCTAssertTrue(app.buttons["Match system"].exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        XCTAssertTrue(app.staticTexts["screen.visual"].waitForExistence(timeout: 5))
        capture(app, "ios-large-text-landscape")
    }
}
