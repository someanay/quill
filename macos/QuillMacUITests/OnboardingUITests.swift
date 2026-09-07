import XCTest

final class OnboardingUITests: XCTestCase {
    func testFirstRunWarningAndKeyFlow() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let started = app.buttons["onboarding.welcome.cta"]
        XCTAssertTrue(started.waitForExistence(timeout: 12))
        let welcome = XCTAttachment(screenshot: app.screenshot()); welcome.name = "macOS-welcome"; welcome.lifetime = .keepAlways; add(welcome)
        started.click()
        let accept = app.buttons["onboarding.key.warning.accept"]
        XCTAssertTrue(accept.waitForExistence(timeout: 6)); XCTAssertTrue(accept.isEnabled); accept.click()
        XCTAssertTrue(app.secureTextFields["key-input"].waitForExistence(timeout: 6))
        XCTAssertFalse(app.buttons["onboarding.key.cta"].isEnabled)
        let key = XCTAttachment(screenshot: app.screenshot()); key.name = "macOS-key-setup"; key.lifetime = .keepAlways; add(key)
        app.secureTextFields["key-input"].typeText("invalid")
        app.buttons["onboarding.key.cta"].click()
        XCTAssertTrue(app.staticTexts["That key was rejected. Check that you copied the complete key for this provider."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.secureTextFields["key-input"].exists)
    }
}
