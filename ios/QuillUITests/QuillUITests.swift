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
    private func openHome() -> XCUIApplication {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        tap(app.buttons["Open Quill"], in: app)
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
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
        app.tabBars.buttons["Settings"].tap()
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
        app.tabBars.buttons["Settings"].tap()
        tap(app.buttons["settings.visual"], in: app)
        app.buttons["Dark"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-dark-appearance"; shot.lifetime = .keepAlways; add(shot)
        XCTAssertTrue(app.buttons["Match system"].exists)
    }
}
