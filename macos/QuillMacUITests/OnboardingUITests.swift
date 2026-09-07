import XCTest

final class OnboardingUITests: XCTestCase {
    func testEveryOnboardingStagePresentationInLightAndDark() throws {
        let stages = ["splash", "welcome", "warning", "key", "checklist", "microphone", "bubble", "accessibility", "ready"]
        for dark in [false, true] {
            for (index, stage) in stages.enumerated() {
                let app = XCUIApplication()
                app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", String(index)] + (dark ? ["--ui-dark"] : [])
                app.launch()
                XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 10))
                if index == 3 { XCTAssertFalse(app.buttons["onboarding.key.cta"].isEnabled) }
                if index == 5 { XCTAssertFalse(app.buttons["onboarding.mictest.cta"].isEnabled) }
                if index == 7 { XCTAssertFalse(app.buttons["onboarding.accessibility.enable_cta"].isEnabled) }
                let shot = XCTAttachment(screenshot: app.screenshot())
                shot.name = "macOS-onboarding-presentation-" + (dark ? "dark-" : "light-") + stage
                shot.lifetime = .keepAlways; add(shot)
                app.terminate()
            }
        }
    }
    private func reveal(_ row: XCUIElement, in app: XCUIApplication) {
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<10 {
            let viewport = scroll.frame.insetBy(dx: 0, dy: 6)
            if row.exists && row.frame.height > 0 && viewport.contains(row.frame) { return }
            scroll.scroll(byDeltaX: 0, deltaY: -200)
        }
        XCTAssertTrue(scroll.frame.contains(row.frame), "Settings row must be inside the visible scroll viewport")
    }
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
        app.secureTextFields["key-input"].click()
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
        XCTAssertTrue(app.textFields["model-id"].waitForExistence(timeout: 5))
        let speech = XCTAttachment(screenshot: app.screenshot()); speech.name = "macOS-speech-settings"; speech.lifetime = .keepAlways; add(speech)
        app.buttons["settings-back"].click()
        app.buttons["settings.cleanup"].click()
        XCTAssertTrue(app.descendants(matching: .any)["cleanup-automatic"].waitForExistence(timeout: 5))
        let cleanup = XCTAttachment(screenshot: app.screenshot()); cleanup.name = "macOS-cleanup-settings"; cleanup.lifetime = .keepAlways; add(cleanup)
        app.buttons["nav.history"].click()
        XCTAssertTrue(app.staticTexts["No dictations yet"].waitForExistence(timeout: 5))
    }
    func testEverySettingsScreenInDarkAppearance() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-settings", "--ui-dark"]; app.launch()
        XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 12)); app.buttons["nav.settings"].click()
        for page in ["speech", "keys", "cleanup", "style", "words", "vocab", "audio", "bubble", "privacy", "advanced", "about"] {
            let row = app.buttons["settings." + page]
            reveal(row, in: app)
            XCTAssertTrue(row.isHittable, "Settings row must be reachable: " + page); row.click()
            XCTAssertTrue(app.staticTexts["settings.page." + page].waitForExistence(timeout: 6))
            let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-dark-" + page; shot.lifetime = .keepAlways; add(shot)
            app.buttons["settings-back"].click()
            XCTAssertTrue(app.staticTexts["settings.page.list"].waitForExistence(timeout: 4))
        }
    }
    func testCustomWordEditingAndPrivateHistoryGate() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-settings"]; app.launch()
        XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 12)); app.buttons["nav.settings"].click()
        app.buttons["settings.words"].click()
        let heard = app.textFields["word-heard"], replacement = app.textFields["word-replacement"]
        XCTAssertTrue(heard.waitForExistence(timeout: 5)); heard.click(); heard.typeText("tihor")
        replacement.click(); replacement.typeText("Quill")
        app.buttons["settings.words.save"].click()
        XCTAssertTrue(app.staticTexts["tihor"].waitForExistence(timeout: 5)); XCTAssertTrue(app.staticTexts["→ Quill"].exists)
        app.buttons["word-delete"].firstMatch.click(); XCTAssertFalse(app.staticTexts["tihor"].exists)
        app.buttons["settings-back"].click()
        let privacy = app.buttons["settings.privacy"]
        reveal(privacy, in: app)
        XCTAssertTrue(privacy.isHittable); privacy.click()
        let toggle = app.descendants(matching: .any)["privacy-private-mode"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5)); toggle.click()
        app.buttons["nav.history"].click()
        XCTAssertTrue(app.staticTexts["History is off"].waitForExistence(timeout: 5))
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-private-history"; shot.lifetime = .keepAlways; add(shot)
    }
}
