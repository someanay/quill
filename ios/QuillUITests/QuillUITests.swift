import XCTest

final class QuillUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    override func tearDownWithError() throws {
        let app = XCUIApplication()
        if app.state != .notRunning {
            let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
            let hierarchy = XCTAttachment(string: app.debugDescription); hierarchy.name = "UI hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        }
        app.terminate()
    }
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<12 {
            if element.waitForExistence(timeout: 1) && element.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(element.exists)
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
        if app.buttons["nav.settings"].exists { return app.buttons["nav.settings"] }
        // iPad's native floating tab strip exposes nested buttons, not a TabBar.
        // Both observed nodes have the same frame; firstMatch avoids ambiguity.
        return app.buttons.matching(identifier: "gearshape").matching(NSPredicate(format: "label == %@", "Settings")).firstMatch
    }
    private func backToSettings(_ app: XCUIApplication) {
        // iPad 18 exposes Show Sidebar before Back in the same navigation bar.
        // Select the real native Back label rather than the first toolbar control.
        let back = app.navigationBars.buttons.matching(NSPredicate(format: "identifier != %@ AND (label == %@ OR label == %@)", "ToggleSidebar", "Settings", "Back")).firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 5)); XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(app.buttons["settings.speech"].waitForExistence(timeout: 5))
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
        tap(app.buttons["Open workspace"], in: app)
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 10))
        if !settingsTab(app).exists, app.buttons["ToggleSidebar"].exists {
            tap(app.buttons["ToggleSidebar"], in: app)
        }
        XCTAssertTrue(settingsTab(app).waitForExistence(timeout: 10))
        return app
    }
    func testWelcomeAndKeyWarning() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        let start = app.buttons["onboarding.getStarted"]
        XCTAssertTrue(start.waitForExistence(timeout: 10)); start.tap()
        XCTAssertTrue(app.staticTexts["You'll need your own API key"].waitForExistence(timeout: 5))
        let privacyPromise = "Quill has no account and runs no server. Your audio and transcripts stay on this device except for the direct, encrypted call to the provider you chose."
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label == %@", privacyPromise)).firstMatch.exists)
        capture(app, "ios-required-key-warning")
        let next = app.buttons["Continue with Groq"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        let predicate = NSPredicate(format: "enabled == true")
        expectation(for: predicate, evaluatedWith: next); waitForExpectations(timeout: 6)
        next.tap()
        XCTAssertTrue(app.secureTextFields["API key"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["key.provider.gemini"].exists)
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-onboarding-key"; shot.lifetime = .keepAlways; add(shot)
    }
    func testHomeSettingsPrivacyAndKeyboardHelp() throws {
        let app = openHome()
        settingsTab(app).tap()
        tap(app.buttons["settings.speech"], in: app)
        let language = app.staticTexts["speech.language.section"]
        for _ in 0..<8 { if language.exists && language.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(language.waitForExistence(timeout: 5))
        backToSettings(app)
        tap(app.buttons["settings.privacy"], in: app)
        XCTAssertTrue(app.switches["Private mode"].waitForExistence(timeout: 5))
        app.switches["Private mode"].tap()
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "ios-privacy"; shot.lifetime = .keepAlways; add(shot)
    }
    func testGroqSetupSettingsRouteRetainsDraftInBothAppearances() throws {
        for dark in [false, true] {
            let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", "2"] + (dark ? ["--ui-dark"] : []); app.launch()
            let key = app.secureTextFields["key.candidate"]; XCTAssertTrue(key.waitForExistence(timeout: 10))
            XCTAssertFalse(app.buttons["key.provider.gemini"].exists); XCTAssertFalse(app.buttons["key.provider.groq"].exists)
            tap(key, in: app); key.typeText("draft-groq")
            if app.buttons["Done"].firstMatch.exists { tap(app.buttons["Done"].firstMatch, in: app) }
            capture(app, "ios-groq-onboarding-\(dark ? "dark" : "light")")
            tap(app.buttons["onboarding.other-providers"], in: app)
            XCTAssertTrue(app.staticTexts["screen.keys"].waitForExistence(timeout: 10))
            tap(app.buttons["keys.add"], in: app)
            XCTAssertTrue(app.buttons["key.provider.gemini"].waitForExistence(timeout: 5))
            capture(app, "ios-setup-provider-settings-\(dark ? "dark" : "light")")
            tap(app.buttons["onboarding.resume"], in: app)
            XCTAssertTrue(key.waitForExistence(timeout: 5)); XCTAssertEqual(key.value as? String, "••••••••••", "The Groq draft remains in the secure form.")
            XCTAssertFalse(app.buttons["key.provider.gemini"].exists); app.terminate()
        }
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
        let accept = app.buttons["Continue with Groq"]
        XCTAssertTrue(accept.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: accept); waitForExpectations(timeout: 6)
        tap(accept, in: app)
        XCTAssertTrue(app.secureTextFields["API key"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["key.provider.gemini"].exists)
        XCTAssertFalse(app.buttons["key.provider.groq"].exists, "Onboarding must not contain a provider accordion.")
        let key = app.secureTextFields["API key"]
        tap(key, in: app); key.typeText("gsk_quill_ui_fixture0000000000")
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "key.verified").firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["onboarding.key.verify"].isEnabled)
        if app.buttons["Done"].firstMatch.exists { tap(app.buttons["Done"].firstMatch, in: app) }
        tap(app.buttons["onboarding.key.verify"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.keyboard"].waitForExistence(timeout: 10)); capture(app, "ios-setup-keyboard")
        XCTAssertFalse(app.buttons["onboarding.keyboard.next"].isEnabled)
        tap(app.buttons["onboarding.keyboard.later"], in: app)
        if app.buttons["onboarding.permission"].exists { tap(app.buttons["onboarding.permission"], in: app) }
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let permission = springboard.alerts.firstMatch
        if permission.waitForExistence(timeout: 3) {
            let allow = permission.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] 'Allow' OR label == 'OK'")).firstMatch
            XCTAssertTrue(allow.exists); allow.tap()
        }
        XCTAssertTrue(app.staticTexts["onboarding.microphone"].waitForExistence(timeout: 10)); capture(app, "ios-setup-microphone")
        tap(app.buttons["onboarding.microphone.next"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.practice"].waitForExistence(timeout: 5)); capture(app, "ios-setup-practice")
        XCTAssertFalse(app.buttons["onboarding.practice.next"].isEnabled)
        tap(app.buttons["onboarding.practice.later"], in: app)
        XCTAssertTrue(app.staticTexts["onboarding.ready"].waitForExistence(timeout: 5)); capture(app, "ios-setup-ready")
        tap(app.buttons["onboarding.finish"], in: app)
        XCTAssertTrue(app.buttons["dictation.record"].waitForExistence(timeout: 10))
    }
    func testEverySettingsDetailScreen() throws {
        for dark in [false, true] {
            let app = openHome(arguments: dark ? ["--ui-dark"] : []); settingsTab(app).tap()
            for section in ["speech", "keys", "cleanup", "style", "words", "audio", "visual", "keyboard", "privacy", "advanced", "about"] {
                tap(app.buttons["settings." + section], in: app)
                XCTAssertTrue(app.staticTexts["screen." + section].waitForExistence(timeout: 5), section)
                capture(app, "ios-settings-\(dark ? "dark" : "light")-" + section)
                backToSettings(app)
            }
            app.terminate()
        }
    }
    func testLastSettingsActionCanScrollAboveNativeNavigation() throws {
        let app = openHome(); tap(settingsTab(app), in: app)
        tap(app.buttons["settings.advanced"], in: app)
        let action = app.buttons["settings.clear-data"]
        for _ in 0..<12 {
            let bottom = app.tabBars.firstMatch.exists ? app.tabBars.firstMatch.frame.minY : app.windows.firstMatch.frame.maxY
            if action.exists && action.isHittable && action.frame.maxY < bottom - 8 { break }
            app.swipeUp()
        }
        XCTAssertTrue(action.exists); XCTAssertTrue(action.isHittable)
        let bottom = app.tabBars.firstMatch.exists ? app.tabBars.firstMatch.frame.minY : app.windows.firstMatch.frame.maxY
        XCTAssertLessThan(action.frame.maxY, bottom - 8, "The final action must scroll clear of native navigation.")
        capture(app, "ios-settings-footer-reachable")
    }
    func testEveryNativeSetupStageInLightAndDark() throws {
        for dark in [false, true] {
            for stage in 1...6 {
                let app = XCUIApplication()
                app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", String(stage)] + (dark ? ["--ui-dark"] : [])
                app.launch()
                let identifier = ["welcome", "provider", "keyboard", "microphone", "practice", "ready"][stage - 1]
                XCTAssertTrue(app.staticTexts["onboarding." + identifier].waitForExistence(timeout: 10))
                capture(app, "ios-native-setup-\(stage)-\(dark ? "dark" : "light")")
                app.terminate()
            }
        }
    }
    func testAllProviderFormsInLight() throws { try captureAllProviderForms(dark: false) }
    func testAllProviderFormsInDark() throws { try captureAllProviderForms(dark: true) }
    private func revealFormControl(_ element: XCUIElement, in app: XCUIApplication) {
        let scroll = app.scrollViews.firstMatch
        for _ in 0..<24 {
            let navigation = app.navigationBars.firstMatch
            let top = max(scroll.frame.minY, navigation.exists ? navigation.frame.maxY : scroll.frame.minY) + 12
            let bottom = min(scroll.frame.maxY, app.tabBars.firstMatch.exists ? app.tabBars.firstMatch.frame.minY : scroll.frame.maxY) - 12
            if element.exists && element.isHittable && element.frame.minY >= top && element.frame.maxY <= bottom { return }
            if element.exists && element.frame.origin.y.isFinite {
                let distance = min(180, max(-180, (top + bottom) / 2 - element.frame.midY))
                let start = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: distance)))
            } else { scroll.swipeUp() }
        }
        XCTAssertTrue(element.exists); XCTAssertTrue(element.isHittable)
    }
    private func captureAllProviderForms(dark: Bool) throws {
        XCUIDevice.shared.orientation = .portrait
        let app = openHome(arguments: dark ? ["--ui-dark"] : [])
        tap(settingsTab(app), in: app); tap(app.buttons["settings.keys"], in: app); tap(app.buttons["keys.add"], in: app)
        for provider in ["gemini", "groq", "openrouter"] {
            let row = app.buttons["key.provider." + provider]
            revealFormControl(row, in: app); if !row.isSelected { row.tap() }
            XCTAssertTrue(row.isSelected)
            XCTAssertTrue(app.secureTextFields["key.candidate"].waitForExistence(timeout: 5))
            capture(app, "ios-key-form-\(dark ? "dark" : "light")-" + provider)
            let save = app.buttons["onboarding.key.verify"]
            revealFormControl(save, in: app); XCTAssertFalse(save.isEnabled)
            capture(app, "ios-key-form-footer-\(dark ? "dark" : "light")-" + provider)
        }
        let other = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "OpenAI or a custom provider")).firstMatch
        revealFormControl(other, in: app); other.tap()
        for provider in ["openai", "custom"] {
            let row = app.buttons["key.provider." + provider]
            revealFormControl(row, in: app); if !row.isSelected { row.tap() }
            let expanded = expectation(for: NSPredicate(format: "selected == true"), evaluatedWith: row)
            wait(for: [expanded], timeout: 5)
            capture(app, "ios-key-form-\(dark ? "dark" : "light")-" + provider)
            let save = try XCTUnwrap(app.buttons.matching(identifier: "onboarding.key.verify").allElementsBoundByIndex.last)
            revealFormControl(save, in: app); XCTAssertFalse(save.isEnabled)
            capture(app, "ios-key-form-footer-\(dark ? "dark" : "light")-" + provider)
            if provider == "openai" {
                let guide = app.buttons["key.guide"]
                revealFormControl(guide, in: app); guide.tap()
                let title = app.staticTexts["key.guide.title"]
                XCTAssertTrue(title.waitForExistence(timeout: 5))
                capture(app, "ios-provider-guide-\(dark ? "dark" : "light")")
                tap(app.buttons["key.guide.done"], in: app)
                let dismissed = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: title)
                wait(for: [dismissed], timeout: 5)
            }
        }
    }
    func testSecondaryScreensAndSheetsInLight() throws { try captureSecondaryScreens(dark: false) }
    func testSecondaryScreensAndSheetsInDark() throws { try captureSecondaryScreens(dark: true) }
    private func captureSecondaryScreens(dark: Bool) throws {
        XCUIDevice.shared.orientation = .portrait
        let arguments = dark ? ["--ui-dark"] : []
        let welcome = XCUIApplication(); welcome.launchArguments = ["--ui-testing"] + arguments; welcome.launch()
        tap(welcome.buttons["onboarding.getStarted"], in: welcome)
        XCTAssertTrue(welcome.buttons["Continue with Groq"].waitForExistence(timeout: 5))
        capture(welcome, "ios-key-disclosure-\(dark ? "dark" : "light")"); welcome.terminate()
        let words = openHome(arguments: arguments); tap(settingsTab(words), in: words); tap(words.buttons["settings.words"], in: words)
        tap(words.buttons["Add a word"], in: words)
        XCTAssertTrue(words.staticTexts["Add a custom word"].waitForExistence(timeout: 5))
        capture(words, "ios-word-sheet-\(dark ? "dark" : "light")"); words.terminate()
        for title in ["Privacy details", "Font licenses"] {
            let app = openHome(arguments: arguments); tap(settingsTab(app), in: app); tap(app.buttons["settings.about"], in: app)
            tap(app.buttons[title], in: app)
            XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: 5))
            if title == "Font licenses" { XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "SIL OPEN FONT LICENSE")).firstMatch.exists) }
            capture(app, "ios-legal-\(dark ? "dark" : "light")-" + title); app.terminate()
        }
        let diagnostics = openHome(arguments: arguments); tap(settingsTab(diagnostics), in: diagnostics); tap(diagnostics.buttons["settings.privacy"], in: diagnostics)
        tap(diagnostics.buttons["Review diagnostic report"], in: diagnostics)
        XCTAssertTrue(diagnostics.staticTexts["Review diagnostic report"].waitForExistence(timeout: 5))
        capture(diagnostics, "ios-diagnostics-\(dark ? "dark" : "light")")
    }
    func testPopulatedHistoryInBothOrientationsAndThemes() throws {
        for dark in [false, true] {
            let app = openHome(arguments: ["--ui-history-fixtures"] + (dark ? ["--ui-dark"] : []))
            capture(app, "ios-workspace-\(dark ? "dark" : "light")")
            XCUIDevice.shared.orientation = .landscapeLeft
            waitForLandscape(app)
            let record = app.buttons["dictation.record"]
            XCTAssertTrue(record.waitForExistence(timeout: 5))
            if app.buttons["nav.home"].exists && app.windows.firstMatch.frame.width >= 1100 {
                let today = app.staticTexts["home.today"]
                XCTAssertTrue(today.exists)
                XCTAssertLessThanOrEqual(record.frame.maxX, today.frame.minX, "Wide iPad Home must give the recorder and supporting context separate panes.")
            }
            capture(app, "ios-workspace-landscape-\(dark ? "dark" : "light")")
            XCUIDevice.shared.orientation = .portrait
            expectation(for: NSPredicate { _, _ in app.windows.firstMatch.frame.width < app.windows.firstMatch.frame.height }, evaluatedWith: app)
            waitForExpectations(timeout: 5)
            let history = app.tabBars.buttons["History"].exists ? app.tabBars.buttons["History"] : app.buttons["nav.history"]
            tap(history, in: app)
            let entry = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "history.entry.")).firstMatch
            tap(entry, in: app)
            XCTAssertTrue(app.staticTexts["transcript.text"].waitForExistence(timeout: 5))
            capture(app, "ios-history-reader-\(dark ? "dark" : "light")")
            if app.navigationBars.buttons["Done"].exists { app.navigationBars.buttons["Done"].tap() }
            XCUIDevice.shared.orientation = .landscapeLeft
            waitForLandscape(app)
            tap(entry, in: app)
            XCTAssertTrue(app.staticTexts["transcript.text"].waitForExistence(timeout: 5))
            capture(app, "ios-history-landscape-\(dark ? "dark" : "light")")
            XCUIDevice.shared.orientation = .portrait
            app.terminate()
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
        let countRow = app.descendants(matching: .any).matching(identifier: "home.today.count").firstMatch
        let lastRow = app.buttons["home.today.last_used"]
        XCTAssertLessThanOrEqual(today.frame.maxY, countRow.frame.minY, "Large text must not overlap the statistics heading.")
        XCTAssertLessThanOrEqual(countRow.frame.maxY, lastRow.frame.minY, "Large text must not overlap adjacent statistics rows.")
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
