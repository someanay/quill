import XCTest

final class OnboardingUITests: XCTestCase {
    func testFirstRunWarningAndKeyFlow() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let started = app.buttons["onboarding.welcome.cta"]
        XCTAssertTrue(started.waitForExistence(timeout: 12))
        let welcome = XCTAttachment(screenshot: app.screenshot()); welcome.name = "macOS-welcome"; welcome.lifetime = .keepAlways; add(welcome)
        started.click()
        let accept = app.buttons["onboarding.key.warning.accept"]
        XCTAssertTrue(accept.waitForExistence(timeout: 6))
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: accept)
        wait(for: [enabled], timeout: 6); accept.click()
        XCTAssertTrue(app.secureTextFields["key-input"].waitForExistence(timeout: 6))
        XCTAssertFalse(app.buttons["onboarding.key.cta"].isEnabled)
        let key = XCTAttachment(screenshot: app.screenshot()); key.name = "macOS-key-setup"; key.lifetime = .keepAlways; add(key)
        app.secureTextFields["key-input"].typeText("invalid")
        app.buttons["onboarding.key.cta"].click()
        XCTAssertTrue(app.staticTexts["That key was rejected. Check that you copied the complete key for this provider."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.secureTextFields["key-input"].exists)
    }
    func testSettingsNavigationAndHistory() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-settings"]; app.launch()
        XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 12))
        app.buttons["nav.settings"].click()
        XCTAssertTrue(app.buttons["settings.speech"].waitForExistence(timeout: 5)); app.buttons["settings.speech"].click()
        XCTAssertTrue(app.textFields["Model ID"].waitForExistence(timeout: 5))
        let speech = XCTAttachment(screenshot: app.screenshot()); speech.name = "macOS-speech-settings"; speech.lifetime = .keepAlways; add(speech)
        app.buttons["Back"].click()
        app.buttons["settings.cleanup"].click()
        XCTAssertTrue(app.staticTexts["Match speech provider"].waitForExistence(timeout: 5))
        let cleanup = XCTAttachment(screenshot: app.screenshot()); cleanup.name = "macOS-cleanup-settings"; cleanup.lifetime = .keepAlways; add(cleanup)
        app.buttons["nav.history"].click()
        XCTAssertTrue(app.staticTexts["No dictations yet"].waitForExistence(timeout: 5))
    }
}
