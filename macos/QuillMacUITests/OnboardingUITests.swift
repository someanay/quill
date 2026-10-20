import XCTest
import AppKit

final class OnboardingUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    override func tearDownWithError() throws {
        let tracePath = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("QuillUITests/settings-navigation.txt")
        if let trace = try? String(contentsOf: tracePath, encoding: .utf8) {
            let attachment = XCTAttachment(string: trace); attachment.name = "Settings navigation actions"; attachment.lifetime = .keepAlways; add(attachment)
        }
        let app = XCUIApplication()
        if app.state != .notRunning {
            let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
            let hierarchy = XCTAttachment(string: app.debugDescription); hierarchy.name = "macOS UI hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
            app.terminate()
        }
    }
    func testGuidedKeyRowsRetainDraftAndRememberExpansion() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", "2"]; app.launch()
        XCTAssertTrue(app.buttons["key-provider-gemini"].waitForExistence(timeout: 10))
        for provider in ["groq", "openrouter", "gemini"] {
            let row = app.buttons["key-provider-" + provider]; reveal(row, in: app); row.click()
            let field = app.secureTextFields["key-input"]; XCTAssertTrue(field.waitForExistence(timeout: 5)); reveal(field, in: app)
            if provider == "openrouter" {
                field.click(); field.typeKey("a", modifierFlags: .command); field.typeText("draft-router")
                XCTAssertFalse(app.buttons["key-save-openrouter"].isEnabled)
                app.buttons["key-reveal"].click(); XCTAssertEqual(app.textFields["key-input"].value as? String, "draft-router")
                app.buttons["key-reveal"].click()
            }
            let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-guided-key-" + provider; shot.lifetime = .keepAlways; add(shot)
        }
        let router = app.buttons["key-provider-openrouter"]; reveal(router, in: app); router.click()
        app.terminate(); app.launch()
        XCTAssertTrue(app.secureTextFields["key-input"].waitForExistence(timeout: 10))
        reveal(app.secureTextFields["key-input"], in: app); app.buttons["key-reveal"].click()
        XCTAssertEqual(app.textFields["key-input"].value as? String, "draft-router")
        XCTAssertFalse(app.buttons["key-save-openrouter"].isEnabled)
        // Restore the default expansion for the independent welcome flow.
        let gemini = app.buttons["key-provider-gemini"]; reveal(gemini, in: app); gemini.click()
    }
    func testEveryOnboardingStagePresentationInLightAndDark() throws {
        let stages = ["welcome", "warning", "key", "microphone", "accessibility", "shortcut", "ready"]
        for dark in [false, true] {
            for (index, stage) in stages.enumerated() {
                let app = XCUIApplication()
                app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", String(index)] + (dark ? ["--ui-dark"] : [])
                app.launch()
                XCTAssertTrue(app.staticTexts["onboarding-title"].waitForExistence(timeout: 10))
                if index == 0 { XCTAssertTrue(app.buttons["onboarding.welcome.cta"].exists); XCTAssertTrue(app.buttons["onboarding.welcome.open"].exists) }

                if index == 2 { XCTAssertFalse(app.buttons["key-save-gemini"].isEnabled) }
                if index == 3 { XCTAssertFalse(app.buttons["onboarding.mictest.cta"].isEnabled) }
                if index == 4 { XCTAssertFalse(app.buttons["onboarding.accessibility.enable_cta"].isEnabled) }
                let shot = XCTAttachment(screenshot: app.screenshot())
                shot.name = "macOS-onboarding-presentation-" + (dark ? "dark-" : "light-") + stage
                shot.lifetime = .keepAlways; add(shot)
                if index == 0 {
                    app.buttons["onboarding.welcome.open"].click()
                    XCTAssertTrue(app.buttons["nav.home"].waitForExistence(timeout: 5))
                    XCTAssertFalse(app.buttons["key-provider-gemini"].exists)
                }
                app.terminate()
            }
        }
    }
    private func reveal(_ row: XCUIElement, in app: XCUIApplication) {
        let scroll = app.scrollViews["settings-scroll"].exists ? app.scrollViews["settings-scroll"] : app.scrollViews["onboarding-content"]
        for _ in 0..<10 {
            let viewport = scroll.frame.insetBy(dx: 0, dy: 6)
            if row.exists && row.frame.height > 0 && viewport.contains(row.frame) { return }
            scroll.scroll(byDeltaX: 0, deltaY: row.frame.minY < viewport.minY ? 200 : -200)
        }
        XCTAssertTrue(scroll.frame.contains(row.frame), "Settings row must be inside the visible scroll viewport")
    }
    func testResizingKeySetupKeepsDraftAndReachableControls() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-onboarding-stage", "2"]; app.launch()
        let field = app.secureTextFields["key-input"]; XCTAssertTrue(field.waitForExistence(timeout: 10))
        reveal(field, in: app); field.click(); field.typeKey("a", modifierFlags: .command); field.typeText("draft-desktop")
        let window = app.windows.firstMatch
        let screen = NSScreen.main?.visibleFrame.size ?? CGSize(width: 1200, height: 900)
        for requested in [CGSize(width: 660, height: 500), CGSize(width: 1160, height: 740), CGSize(width: 760, height: 1000)] {
            let target = CGSize(width: min(requested.width, screen.width - 40), height: min(requested.height, screen.height - 60))
            let corner = window.coordinate(withNormalizedOffset: CGVector(dx: 1, dy: 1)).withOffset(CGVector(dx: -3, dy: -3))
            corner.click(forDuration: 0.1, thenDragTo: corner.withOffset(CGVector(dx: target.width - window.frame.width, dy: target.height - window.frame.height)))
            XCTAssertEqual(window.frame.width, target.width, accuracy: 25)
            XCTAssertEqual(window.frame.height, target.height, accuracy: 25)
            reveal(field, in: app); XCTAssertTrue(field.isHittable)
            app.buttons["key-reveal"].click()
            XCTAssertEqual(app.textFields["key-input"].value as? String, "draft-desktop")
            app.buttons["key-reveal"].click()
            let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-resize-\(Int(target.width))x\(Int(target.height))"; shot.lifetime = .keepAlways; add(shot)
        }
    }
    func testWorkspaceAndPopulatedHistoryAcrossWindowSizes() throws {
        for dark in [false, true] {
            let app = XCUIApplication()
            app.launchArguments = ["--ui-testing", "--ui-settings", "--ui-history-fixtures"] + (dark ? ["--ui-dark"] : [])
            app.launch()
            XCTAssertTrue(app.buttons["nav.home"].waitForExistence(timeout: 10))
            let window = app.windows.firstMatch
            let screen = NSScreen.main?.visibleFrame.size ?? CGSize(width: 1200, height: 900)
            if ProcessInfo.processInfo.environment["GITHUB_ACTIONS"] == "true" { XCTAssertGreaterThanOrEqual(screen.width, 1440, "Expanded desktop coverage requires a larger CI display") }
            for requested in [CGSize(width: 1160, height: 740), CGSize(width: 660, height: 500), CGSize(width: 760, height: 1000)] {
                let target = CGSize(width: min(requested.width, screen.width - 40), height: min(requested.height, screen.height - 60))
                let corner = window.coordinate(withNormalizedOffset: CGVector(dx: 1, dy: 1)).withOffset(CGVector(dx: -3, dy: -3))
                corner.click(forDuration: 0.1, thenDragTo: corner.withOffset(CGVector(dx: target.width - window.frame.width, dy: target.height - window.frame.height)))
                XCTAssertEqual(window.frame.width, target.width, accuracy: 25)
                XCTAssertEqual(window.frame.height, target.height, accuracy: 25)
                for page in ["home", "history"] {
                    let query = app.buttons.matching(identifier: (window.frame.width < 800 ? "compact." : "nav.") + page)
                    XCTAssertTrue(query.firstMatch.waitForExistence(timeout: 5))
                    // Native toolbars expose a container button and its actual control.
                    let navigation = try XCTUnwrap(query.allElementsBoundByIndex.first(where: { $0.isHittable }))
                    navigation.click()
                    if page == "history" {
                        XCTAssertTrue(app.descendants(matching: .any)["history-list"].waitForExistence(timeout: 5))
                        XCTAssertTrue(app.scrollViews["history-reader"].exists)
                        XCTAssertTrue(app.buttons["Copy text"].isHittable)
                    }
                    let shot = XCTAttachment(screenshot: app.screenshot())
                    shot.name = "macOS-\(page)-\(dark ? "dark" : "light")-\(Int(target.width))x\(Int(target.height))"
                    shot.lifetime = .keepAlways; add(shot)
                }
            }
            app.terminate()
        }
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
        XCTAssertFalse(app.buttons["key-save-gemini"].isEnabled)
        let key = XCTAttachment(screenshot: app.screenshot()); key.name = "macOS-key-setup"; key.lifetime = .keepAlways; add(key)
        app.secureTextFields["key-input"].click()
        app.secureTextFields["key-input"].typeText("invalid")
        XCTAssertTrue(app.staticTexts["key-format-help"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["key-save-gemini"].isEnabled)
        XCTAssertTrue(app.secureTextFields["key-input"].exists)
    }
    func testSettingsNavigationAndHistory() throws {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-settings"]; app.launch()
        XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 12))
        app.buttons["nav.settings"].click()
        XCTAssertTrue(app.buttons["settings.speech"].waitForExistence(timeout: 5)); app.buttons["settings.speech"].click()
        XCTAssertTrue(app.buttons["model-manual"].waitForExistence(timeout: 5)); app.buttons["model-manual"].click()
        XCTAssertTrue(app.textFields["model-id"].waitForExistence(timeout: 5))
        reveal(app.textFields["model-id"], in: app)
        let speech = XCTAttachment(screenshot: app.screenshot()); speech.name = "macOS-speech-settings"; speech.lifetime = .keepAlways; add(speech)
        reveal(app.buttons["settings-back"], in: app)
        app.buttons["settings-back"].click()
        XCTAssertTrue(app.staticTexts["settings.page.list"].waitForExistence(timeout: 6))
        reveal(app.buttons["settings.cleanup"], in: app)
        app.buttons["settings.cleanup"].click()
        XCTAssertTrue(app.descendants(matching: .any)["cleanup-automatic"].waitForExistence(timeout: 5))
        let cleanup = XCTAttachment(screenshot: app.screenshot()); cleanup.name = "macOS-cleanup-settings"; cleanup.lifetime = .keepAlways; add(cleanup)
        app.typeKey("w", modifierFlags: .command)
        XCTAssertFalse(app.windows["QuillSettings"].exists)
        app.buttons["nav.history"].click()
        XCTAssertTrue(app.staticTexts["No dictations yet"].waitForExistence(timeout: 5))
    }
    func testEverySettingsScreenInLightAcrossWindowSizes() throws {
        try captureEverySettingsScreen(dark: false)
    }
    func testEverySettingsScreenInDarkAcrossWindowSizes() throws {
        try captureEverySettingsScreen(dark: true)
    }
    private func captureEverySettingsScreen(dark: Bool) throws {
            let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--ui-settings"] + (dark ? ["--ui-dark"] : []); app.launch()
            XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 12)); app.buttons["nav.settings"].click()
            let window = app.windows["QuillSettings"]
            XCTAssertTrue(window.waitForExistence(timeout: 6))
            for target in [CGSize(width: 1160, height: 760), CGSize(width: 680, height: 520)] {
                let corner = window.coordinate(withNormalizedOffset: CGVector(dx: 1, dy: 1)).withOffset(CGVector(dx: -3, dy: -3))
                corner.click(forDuration: 0.1, thenDragTo: corner.withOffset(CGVector(dx: target.width - window.frame.width, dy: target.height - window.frame.height)))
                XCTAssertEqual(window.frame.width, target.width, accuracy: 25)
                let overview = XCTAttachment(screenshot: app.screenshot())
                overview.name = "macOS-\(dark ? "dark" : "light")-\(Int(target.width))x\(Int(target.height))-settings-overview"
                overview.lifetime = .keepAlways; add(overview)
                for page in ["speech", "keys", "cleanup", "style", "words", "audio", "bubble", "privacy", "advanced", "about"] {
                    let row = app.buttons["settings." + page]
                    reveal(row, in: app)
                    XCTAssertTrue(row.isHittable, "Settings row must be reachable: " + page); row.click()
                    XCTAssertTrue(app.staticTexts["settings.page." + page].waitForExistence(timeout: 6))
                    let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-\(dark ? "dark" : "light")-\(Int(target.width))x\(Int(target.height))-" + page; shot.lifetime = .keepAlways; add(shot)
                    reveal(app.buttons["settings-back"], in: app)
                    app.buttons["settings-back"].click()
                    XCTAssertTrue(app.staticTexts["settings.page.list"].waitForExistence(timeout: 4))
                }
            }
            app.terminate()
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
        app.typeKey("w", modifierFlags: .command)
        XCTAssertFalse(app.windows["QuillSettings"].exists)
        app.buttons["nav.history"].click()
        XCTAssertTrue(app.staticTexts["History is off"].waitForExistence(timeout: 5))
        let shot = XCTAttachment(screenshot: app.screenshot()); shot.name = "macOS-private-history"; shot.lifetime = .keepAlways; add(shot)
    }
}
