import XCTest

final class QuillUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
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
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let open = app.buttons["Open Quill"]; XCTAssertTrue(open.waitForExistence(timeout: 10)); open.tap()
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Speech & model"].waitForExistence(timeout: 5))
        app.staticTexts["Speech & model"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Spoken language"].waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.swipeUp()
        app.staticTexts["Privacy and History"].firstMatch.tap()
        XCTAssertTrue(app.switches["Private mode"].waitForExistence(timeout: 5))
        app.switches["Private mode"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-privacy"; shot.lifetime = .keepAlways; add(shot)
    }
    func testSettingsScreensAndDarkAppearance() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let open = app.buttons["Open Quill"]; XCTAssertTrue(open.waitForExistence(timeout: 10)); open.tap()
        app.tabBars.buttons["Settings"].tap(); app.swipeUp()
        let visual = app.staticTexts["Visual preferences"].firstMatch
        XCTAssertTrue(visual.waitForExistence(timeout: 5)); visual.tap()
        app.buttons["Dark"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-dark-appearance"; shot.lifetime = .keepAlways; add(shot)
        XCTAssertTrue(app.buttons["Match system"].exists)
    }
}
