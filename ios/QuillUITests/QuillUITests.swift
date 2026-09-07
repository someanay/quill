import XCTest

final class QuillUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    override func tearDownWithError() throws {
        let app = XCUIApplication()
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription); hierarchy.name = "UI hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        app.terminate()
    }
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 10))
        for _ in 0..<5 { if element.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(element.isHittable); element.tap()
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        // Capture the physical screen directly. Observed app-element landscape captures have
        // black padding despite valid window bounds; this avoids an app-rectangle crop.
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
    private func settingsTab(_ app: XCUIApplication) -> XCUIElement {
        let phoneTab = app.tabBars.buttons["Settings"]
        if phoneTab.exists { return phoneTab }
        // iPad's native floating tab strip exposes nested buttons, not a TabBar.
        // Both observed nodes have the same frame; firstMatch avoids ambiguity.
        return app.buttons.matching(identifier: "gearshape").matching(NSPredicate(format: "label == %@", "Settings")).firstMatch
    }
    private func waitForLandscape(_ app: XCUIApplication) {
        var last = CGRect.zero, stable = 0
        for _ in 0..<40 {
            let frame = app.windows.firstMatch.frame
            stable = frame.width > frame.height && frame == last ? stable + 1 : 0
            if stable >= 4 { return }
            last = frame
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail("The app did not settle into a stable landscape window.")
    }
    private func revealLandscapeControl(_ control: XCUIElement, in app: XCUIApplication) {
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<12 {
            let window = app.windows.firstMatch.frame
            let top = max(scroll.frame.minY, app.navigationBars.firstMatch.frame.maxY) + 12
            let bottom = min(scroll.frame.maxY, app.tabBars.firstMatch.exists ? app.tabBars.firstMatch.frame.minY : window.maxY) - 12
            let frame = control.frame
            if control.isHittable && frame.minY >= top && frame.maxY <= bottom { return }
            // A full swipe can skip a 32-point segmented control in a short landscape viewport.
            // Move toward its measured location, in bounded increments, including upward recovery.
            let distance = min(100, max(-100, (top + bottom) / 2 - frame.midY))
            let start = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: distance)))
        }
        XCTFail("Landscape control could not be scrolled fully into view: \(control.frame)")
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
        XCTAssertTrue(app.staticTexts["You'll need your own API key"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Quill has no account and runs no server. Your audio and transcripts stay on this device except for the direct, encrypted call to the provider you chose."].exists)
        capture(app, "ios-required-key-warning")
        let next = app.buttons["Help me get & add a key"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        let predicate = NSPredicate(format: "enabled == true")
        expectation(for: predicate, evaluatedWith: next); waitForExpectations(timeout: 6)
        next.tap()
        XCTAssertTrue(app.secureTextFields["API key"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["key.provider.gemini"].isSelected)
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-onboarding-key"; shot.lifetime = .keepAlways; add(shot)
    }
    func testHomeSettingsPrivacyAndKeyboardHelp() throws {
        let app = openHome()
        settingsTab(app).tap()
        tap(app.buttons["settings.speech"], in: app)
        let language = app.staticTexts["speech.language.section"]
        for _ in 0..<8 { if language.exists && language.isHittable { break }; app.swipeUp() }
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
        XCTAssertTrue(app.buttons["key.provider.gemini"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["key.provider.gemini"].isSelected)
        tap(app.buttons["key.provider.groq"], in: app)
        XCTAssertTrue(app.buttons["key.provider.groq"].isSelected, "The whole provider row, including its center, must expand the requested provider.")
        let key = app.secureTextFields["API key"]
        tap(key, in: app); key.typeText("gsk_quill_ui_fixture0000000000")
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "key.verified").firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["onboarding.key.verify"].isEnabled)
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
        for _ in 0..<5 { if app.buttons["dictation.record"].isHittable { break }; app.swipeUp() }
        XCTAssertTrue(app.buttons["dictation.record"].isHittable)
        capture(app, "ios-large-text-home")
        let today = app.staticTexts["home.today"]
        for _ in 0..<5 { if today.exists && today.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(today.exists); XCTAssertTrue(today.isHittable)
        for identifier in ["home.today.count", "home.today.last_used"] {
            let row = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
            for _ in 0..<5 { if row.exists && row.isHittable { break }; app.swipeUp() }
            XCTAssertTrue(row.exists); XCTAssertTrue(row.isHittable)
        }
        capture(app, "ios-large-text-home-today")
        settingsTab(app).tap()
        tap(app.buttons["settings.visual"], in: app)
        tap(app.buttons["Dark"], in: app); capture(app, "ios-large-text-dark")
        XCTAssertTrue(app.buttons["Match system"].exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        waitForLandscape(app)
        XCTAssertTrue(app.staticTexts["screen.visual"].waitForExistence(timeout: 5))
        let window = app.windows.firstMatch.frame, heading = app.staticTexts["screen.visual"].frame
        XCTAssertGreaterThan(window.width, window.height)
        XCTAssertGreaterThanOrEqual(heading.minX, window.minX); XCTAssertLessThanOrEqual(heading.maxX, window.maxX)
        let medium = app.buttons["Medium"]
        revealLandscapeControl(medium, in: app)
        XCTAssertTrue(medium.isHittable); XCTAssertTrue(window.contains(medium.frame))
        capture(app, "ios-large-text-landscape")
    }
}
