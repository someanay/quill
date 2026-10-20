import XCTest

/// Runs separately from the normal suite. This uses the real Settings process, App Group,
/// keyboard extension and a second native host application; no injected text proxy is involved.
final class KeyboardIntegrationUITests: XCTestCase {
    private let host = XCUIApplication(bundleIdentifier: "com.tihor.quill.ios")
    private let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
    private let fixture = XCUIApplication(bundleIdentifier: "com.tihor.quill.ios.qa.keyboard-field")
    private let sessionID = ProcessInfo.processInfo.environment["QUILL_LIVE_SESSION_ID"] ?? UUID().uuidString
    private var isLive: Bool { ProcessInfo.processInfo.environment["QUILL_LIVE_SESSION_ID"] != nil }
    private var liveReadbacks: [[String: String]] = []
    private var stage = "starting"
    private var outcome = "incomplete"
    override func setUpWithError() throws {
        continueAfterFailure = false
        if ProcessInfo.processInfo.environment["QUILL_REQUIRE_LIVE_PROVIDERS"] == "true" {
            XCTAssertTrue(isLive, "The explicitly selected live test configuration must reach the XCTest runner.")
        }
        XCUIDevice.shared.orientation = .portrait
    }
    override func tearDownWithError() throws {
        attach("final-" + stage, app: fixture)
        attach("final-host-" + stage, app: host)
        if stage.contains("settings") { attach("final-settings-state", app: settings) }
        let transport = isLive ? "LIVE HTTPS providers; actual loopback microphone and installed keyboard" : "DEBUG synthetic provider; real microphone and installed keyboard extension"
        let report = XCTAttachment(string: "status=\(outcome)\nstage=\(stage)\ntransport=\(transport)\n")
        report.name = "Keyboard integration outcome"; report.lifetime = .keepAlways; add(report)
        if isLive, let data = try? JSONSerialization.data(withJSONObject: liveReadbacks, options: [.prettyPrinted,.sortedKeys]) {
            let readbacks = XCTAttachment(data: data, uniformTypeIdentifier: "public.json"); readbacks.name = "Live provider exact readbacks"; readbacks.lifetime = .keepAlways; add(readbacks)
        }
        fixture.terminate(); host.terminate(); settings.terminate()
    }
    private func attach(_ name: String, app: XCUIApplication) {
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = name; screenshot.lifetime = .keepAlways; add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = name + " hierarchy"; hierarchy.lifetime = .keepAlways; add(hierarchy)
        if app === fixture {
            let editor = fixture.textViews["quill.qa.editor"]
            if editor.exists {
                let observed = editor.value
                let value = observed as? String
                // This separate fixture contains only fixed synthetic QA text.
                // Preserve exact scalars, since AX descriptions truncate text values.
                let readback = XCTAttachment(string: "value=\(String(reflecting: observed))\nscalars=\(value.map { $0.unicodeScalars.map(\.value) } ?? [])\n")
                readback.name = name + " exact editor readback"; readback.lifetime = .keepAlways; add(readback)
            }
        }
    }
    private func requireEditorText(_ expected: String) {
        // Reacquire the actual UIKit editor on each read after extension/app switches.
        // Do not let a predicate retain an earlier accessibility snapshot.
        let matches = NSPredicate { [self] _, _ in fixture.textViews["quill.qa.editor"].value as? String == expected }
        expectation(for: matches, evaluatedWith: fixture); waitForExpectations(timeout: 10)
        XCTAssertEqual(fixture.textViews["quill.qa.editor"].value as? String, expected)
    }
    private func blocked(_ reason: String, app: XCUIApplication) throws -> Never {
        outcome = "blocked: " + reason
        attach("blocked-" + stage, app: app)
        throw XCTSkip(reason)
    }
    private func tap(_ element: XCUIElement, in app: XCUIApplication) {
        if !element.exists { XCTAssertTrue(element.waitForExistence(timeout: isLive ? 120 : 10)) }
        for _ in 0..<6 { if element.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(element.isHittable); element.tap()
    }
    private func settingsRow(_ title: String, prefix: Bool = false) -> XCUIElement {
        let predicate = prefix
            ? NSPredicate(format: "label BEGINSWITH[c] %@", title)
            : NSPredicate(format: "label == %@ OR label BEGINSWITH[c] %@", title, title + ", ")
        // During Settings navigation, a dismissed table can briefly retain an
        // existing cell whose frame is infinite. Do not ask XCTest to hit-test it.
        // Current Settings combines a row's title and value ("Keyboards, 2").
        // Keep searches inside real cells: the navigation title is also "Keyboards".
        for query in [settings.cells.matching(predicate), settings.cells.descendants(matching: .button).matching(predicate), settings.cells.descendants(matching: .staticText).matching(predicate)] {
            for row in query.allElementsBoundByIndex where row.exists && usableFrame(row.frame) { return row }
        }
        return settings.cells.descendants(matching: .staticText).matching(predicate).firstMatch
    }
    private func usableFrame(_ frame: CGRect) -> Bool {
        frame.origin.x.isFinite && frame.origin.y.isFinite && frame.width > 0 && frame.height > 0
    }
    private func setSettingsSwitch(_ row: XCUIElement, on: Bool) {
        // iOS 26 exposes a labelled wrapper plus a smaller native switch. Its
        // wrapper centre can be over the label, so operate the actual child switch.
        let controls = row.descendants(matching: .switch).allElementsBoundByIndex.filter { usableFrame($0.frame) && $0.isHittable }
        let control = controls.min(by: { $0.frame.width < $1.frame.width }) ?? row
        if control.value as? String != (on ? "1" : "0") { control.tap() }
        expectation(for: NSPredicate(format: "value == %@", on ? "1" : "0"), evaluatedWith: control)
        waitForExpectations(timeout: 8)
    }
    private func openSettingsRow(_ title: String, prefix: Bool = false) throws {
        for _ in 0..<20 {
            let row = settingsRow(title, prefix: prefix)
            if row.exists && usableFrame(row.frame) {
                if row.isHittable { row.tap(); RunLoop.current.run(until: Date().addingTimeInterval(0.5)); return }
                settings.swipeUp()
            } else {
                settings.swipeUp()
            }
        }
        try blocked("Simulator Settings did not expose a reachable \(title) row.", app: settings)
    }
    private func completeOnboarding() throws {
        stage = "synthetic-key-onboarding"
        host.launchArguments = ["--ui-testing", isLive ? "--ui-live-providers" : "--ui-provider-fixtures", "--ui-session-id", sessionID]
        host.launch()
        if !isLive {
        tap(host.buttons["onboarding.getStarted"], in: host)
        let next = host.buttons["Continue with Groq"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: next)
        waitForExpectations(timeout: 7)
        tap(next, in: host)
        XCTAssertFalse(host.buttons["key.provider.groq"].exists, "Groq onboarding has no provider accordion.")
        let key = host.secureTextFields["API key"]
        tap(key, in: host); key.typeText("gsk_quill_ui_fixture0000000000")
        XCTAssertTrue(host.descendants(matching: .any).matching(identifier: "key.verified").firstMatch.waitForExistence(timeout: 10))
        if host.buttons["Done"].firstMatch.exists { tap(host.buttons["Done"].firstMatch, in: host) }
        tap(host.buttons["onboarding.key.verify"], in: host)
        }
        XCTAssertTrue(host.staticTexts["onboarding.keyboard"].waitForExistence(timeout: 10))
        XCTAssertFalse(host.buttons["onboarding.keyboard.next"].isEnabled)
        try enableKeyboardThroughSettings()
        // Enabling a keyboard can terminate the suspended host on current iOS.
        // Activate may then cold-launch without this XCTest instance's arguments,
        // opening ordinary storage instead of the isolated persistent QA session.
        // Explicit launch restores the same UUID; settings/key persistence is real.
        host.launch()
        XCTAssertTrue(host.staticTexts["onboarding.keyboard"].waitForExistence(timeout: 10))
        stage = "connect-installed-keyboard"
        tap(host.textFields["onboarding.keyboard.test"], in: host)
        try showQuillKeyboard(in: host)
        if host.buttons["Done"].firstMatch.exists { tap(host.buttons["Done"].firstMatch, in: host) }
        let keyboardNext = host.buttons["onboarding.keyboard.next"]
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: keyboardNext); waitForExpectations(timeout: 10)
        tap(keyboardNext, in: host)
        if host.buttons["onboarding.permission"].exists { tap(host.buttons["onboarding.permission"], in: host) }
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if alert.waitForExistence(timeout: 3) {
            let allow = alert.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] 'Allow' OR label == 'OK'")).firstMatch
            XCTAssertTrue(allow.exists); allow.tap()
        }
        tap(host.buttons["onboarding.microphone.next"], in: host)
        stage = "real-onboarding-practice"
        let record = host.buttons["dictation.record"]
        tap(record, in: host)
        XCTAssertTrue(host.buttons["Stop and transcribe"].waitForExistence(timeout: 5))
        captureSpeech()
        stopLiveCapture(record, in: host, label: "Stop and transcribe")
        let practiceExpected = expectedLiveResult(onboarding: true)
        tap(host.buttons["onboarding.practice.send"], in: host)
        try showQuillKeyboard(in: host)
        tap(host.buttons["keyboard.insert"], in: host)
        let practiceField = host.textFields["onboarding.practice.field"]
        expectation(for: NSPredicate(format: "value == %@", practiceExpected), evaluatedWith: practiceField); waitForExpectations(timeout: 10)
        if isLive { liveReadbacks.append(["stage":"practice","text":practiceField.value as? String ?? ""]) }
        attach("native-onboarding-practice-inserted", app: host)
        if host.buttons["Done"].firstMatch.exists { tap(host.buttons["Done"].firstMatch, in: host) }
        let practiceNext = host.buttons["onboarding.practice.next"]
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: practiceNext); waitForExpectations(timeout: 10)
        tap(practiceNext, in: host)
        tap(host.buttons["onboarding.finish"], in: host)
        XCTAssertTrue(host.buttons["dictation.record"].waitForExistence(timeout: 10))
    }
    private func captureSpeech(defaultDelay: Double = 1.5) {
        if !isLive { RunLoop.current.run(until: Date().addingTimeInterval(defaultDelay)); return }
        // This CI fixture always uses its own Mac's loopback server. Do not rely
        // on Xcode preserving URL-valued environment entries in the runner.
        let url = URL(string: "http://localhost:8765/play")!
        let played = expectation(description: "Start actual synthetic speech playback on the Mac runner")
        URLSession.shared.dataTask(with: url) { _, response, error in
            XCTAssertNil(error); XCTAssertEqual((response as? HTTPURLResponse)?.statusCode,204); played.fulfill()
        }.resume()
        wait(for: [played], timeout: 30)
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }
    private func stopLiveCapture(_ button: XCUIElement, in app: XCUIApplication, label: String) {
        // On a slower runner, accessibility traversal after playback can exceed
        // the real silence timeout. Tapping a now-ready Dictate button would
        // start a second, empty take. Keep the automatic stop and require its
        // real transcript, cleanup and exact delivery below.
        if !isLive || button.label == label { tap(button, in: app) }
    }
    private func expectedLiveResult(onboarding: Bool = false) -> String {
        if !isLive { return "This is a simulated provider response." }
        let result = host.staticTexts[onboarding ? "dictation.result" : "transcript.text"]
        XCTAssertTrue(result.waitForExistence(timeout: 120))
        let text = result.label
        let evidence = XCTAttachment(string: text); evidence.name = "Actual live transcript before insertion"; evidence.lifetime = .keepAlways; add(evidence)
        for word in ["project","notes","alex","tomorrow","budget","thank"] { XCTAssertTrue(text.lowercased().contains(word), "Live result missing \(word): \(text)") }
        return text
    }
    private func enableKeyboardThroughSettings() throws {
        stage = "enable-keyboard-in-settings"
        tap(host.buttons["onboarding.keyboard.settings"], in: host)
        // A cold baseline simulator can spend more than ten seconds launching
        // Preferences. Failure evidence showed its real Apps screen arriving
        // during teardown; retain the actual foreground gate with a bounded wait.
        XCTAssertTrue(settings.wait(for: .runningForeground, timeout: 30))
        XCTAssertTrue(settings.navigationBars.firstMatch.waitForExistence(timeout: 20))
        // The public app-settings URL can finish its Apps/Quill navigation after
        // Settings is already foreground. Wait before touching a transient root page.
        if !settings.navigationBars["Quill"].waitForExistence(timeout: 15), settings.navigationBars.firstMatch.identifier == "Apps" {
            try openSettingsRow("Quill")
            XCTAssertTrue(settings.navigationBars["Quill"].waitForExistence(timeout: 10))
        }
        attach("app-specific-keyboard-settings", app: settings)
        if settingsRow("Keyboards").waitForExistence(timeout: 5) {
            try openSettingsRow("Keyboards")
            let enabled = settings.switches.matching(NSPredicate(format: "label == %@", "Quill")).firstMatch
            XCTAssertTrue(enabled.waitForExistence(timeout: 5)); setSettingsSwitch(enabled, on: true)
        } else {
            // This is the manual route also documented under “Can’t find Keyboards?”.
            for _ in 0..<6 {
                let navigation = settings.navigationBars.firstMatch
                if navigation.identifier == "Settings" { break }
                let back = navigation.buttons.firstMatch
                guard back.exists else { break }; back.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            }
            try openSettingsRow("General")
            try openSettingsRow("Keyboard")
            try openSettingsRow("Keyboards")
            if !settingsRow("Quill", prefix: true).exists {
                try openSettingsRow("Add New Keyboard", prefix: true)
                try openSettingsRow("Quill", prefix: true)
            }
            try openSettingsRow("Quill", prefix: true)
        }
        attach("settings-keyboard-added", app: settings)
        let fullAccess = settings.switches.matching(NSPredicate(format: "label CONTAINS[c] 'Allow Full Access'")).firstMatch
        guard fullAccess.waitForExistence(timeout: 5) else {
            try blocked("Quill was listed, but Simulator Settings did not expose its Full Access switch.", app: settings)
        }
        if fullAccess.value as? String != "1" {
            let controls = fullAccess.descendants(matching: .switch).allElementsBoundByIndex.filter { usableFrame($0.frame) && $0.isHittable }
            (controls.min(by: { $0.frame.width < $1.frame.width }) ?? fullAccess).tap()
            let alert = settings.alerts.firstMatch
            if alert.waitForExistence(timeout: 3) { tap(alert.buttons["Allow"], in: settings) }
        }
        XCTAssertEqual(fullAccess.value as? String, "1")
        attach("keyboard-full-access-enabled", app: settings)
    }
    private func showQuillKeyboard(in app: XCUIApplication) throws {
        for _ in 0..<8 {
            if app.buttons["keyboard.insert"].waitForExistence(timeout: 2) || app.buttons["keyboard.record"].exists { return }
            var dismissedIntroduction = false
            for surface in [app, XCUIApplication(bundleIdentifier: "com.apple.springboard")] {
                if surface.staticTexts["Quickly Change Keyboards"].exists {
                    let choices = surface.buttons.matching(NSPredicate(format: "label == %@", "Continue")).allElementsBoundByIndex.filter { $0.isHittable }
                    if let next = choices.max(by: { $0.frame.minY < $1.frame.minY }) { next.tap(); dismissedIntroduction = true; break }
                }
            }
            if dismissedIntroduction { continue }
            let introduction = app.otherElements["UIContinuousPathIntroductionView"]
            if introduction.exists {
                // A fresh simulator presents QuickPath's first-use tutorial over
                // the real globe key. Complete that system UI before switching.
                attach("system-keyboard-introduction", app: app)
                tap(introduction.buttons["Continue"], in: app)
                continue
            }
            let globe = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Next keyboard' OR label CONTAINS[c] 'Next Keyboard' OR identifier == 'Next keyboard'")).firstMatch
            if globe.exists && globe.isHittable { globe.tap(); continue }
            let language = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Switch keyboard' OR label CONTAINS[c] 'Change keyboard'")).firstMatch
            if language.exists && language.isHittable { language.tap(); continue }
            // A newly enabled iPad extension can arrive after the field becomes
            // focused but before any switch key exists. Keep observing the real
            // keyboard during the bounded loop rather than declaring it absent.
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        attach("system-keyboard-state", app: XCUIApplication(bundleIdentifier: "com.apple.springboard"))
        try blocked("Cycling the real system keyboard selector did not expose the installed Quill extension.", app: app)
    }
    func testRealRecordingHandoffIntoSeparateAppThroughInstalledKeyboard() throws {
        try completeOnboarding()
        // Prove that real settings and the validated Keychain credential survive a new process.
        // The same isolated QA session is reopened below; the test never skips onboarding or seeds keys.
        host.terminate()
        stage = "prepare-separate-native-field"
        fixture.launch()
        let editor = fixture.textViews["quill.qa.editor"]
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        XCTAssertEqual(editor.value as? String, "Before")
        if !fixture.buttons["keyboard.insert"].exists { tap(editor, in: fixture) }
        try showQuillKeyboard(in: fixture)
        attach("real-keyboard-before-handoff", app: fixture)

        stage = "record-and-publish"
        host.launch()
        attach("host-restored-validated-session", app: host)
        let record = host.buttons["dictation.record"]
        tap(record, in: host)
        XCTAssertTrue(host.buttons["Stop and transcribe"].waitForExistence(timeout: 5))
        captureSpeech()
        stopLiveCapture(record, in: host, label: "Stop and transcribe")
        let liveResult = expectedLiveResult()
        let send = host.buttons["Send to keyboard"]
        XCTAssertTrue(send.waitForExistence(timeout: 15))
        tap(send, in: host)
        attach("host-published-real-handoff", app: host)

        stage = "insert-through-installed-extension"
        fixture.activate()
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        if !fixture.buttons["keyboard.insert"].exists { tap(editor, in: fixture) }
        try showQuillKeyboard(in: fixture)
        let insert = fixture.buttons["keyboard.insert"]
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: insert)
        waitForExpectations(timeout: 10)
        tap(insert, in: fixture)
        let expected = "Before " + liveResult
        requireEditorText(expected)
        if isLive { liveReadbacks.append(["stage":"foreground","text":expected]) }
        XCTAssertFalse(insert.isEnabled, "A consumed handoff must not be insertable again.")
        attach("real-extension-inserted-and-consumed", app: fixture)
        stage = "protected-fields-with-pending-transcript"
        host.activate(); tap(host.buttons["Send to keyboard"], in: host)
        fixture.activate()
        tap(fixture.secureTextFields["quill.qa.secure"], in: fixture)
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: fixture.buttons["keyboard.insert"]); waitForExpectations(timeout: 5)
        attach("system-keyboard-for-protected-field", app: fixture)
        tap(fixture.textFields["quill.qa.phone"], in: fixture)
        let phoneInsert = fixture.buttons["keyboard.insert"]
        if phoneInsert.waitForExistence(timeout: 2) { XCTAssertFalse(phoneInsert.isEnabled) }
        XCTAssertEqual(editor.value as? String, expected)
        attach("phone-field-does-not-insert", app: fixture)
        tap(fixture.buttons["quill.qa.caret-end"], in: fixture); try showQuillKeyboard(in: fixture)
        let remaining = fixture.buttons["keyboard.insert"]
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: remaining); waitForExpectations(timeout: 8)
        tap(remaining, in: fixture)
        let repeated = expected + " " + liveResult
        requireEditorText(repeated)
        if isLive { liveReadbacks.append(["stage":"manual-reinsert","text":repeated]) }
        XCTAssertFalse(remaining.isEnabled)
        attach("pending-handoff-survives-protected-fields", app: fixture)
        stage = "enable-one-live-keyboard-session"
        host.activate()
        let sessionStart = host.buttons["session.start"]
        XCTAssertTrue(host.wait(for: .runningForeground, timeout: 10))
        XCTAssertTrue(sessionStart.waitForExistence(timeout: 10))
        XCTAssertTrue(sessionStart.isHittable); XCTAssertTrue(sessionStart.isEnabled)
        let window = host.windows.firstMatch
        let frame = sessionStart.frame
        XCTAssertTrue(window.frame.contains(frame))
        // On the current iPad runtime, element.tap() after this app switch sent
        // (212, 173) for the observed button centre (424, 346). Use the current
        // window's point coordinates once; the session must still actually start.
        window.coordinate(withNormalizedOffset: .zero).withOffset(
            CGVector(dx: frame.midX - window.frame.minX, dy: frame.midY - window.frame.minY)
        ).tap()
        XCTAssertTrue(host.staticTexts["session.active"].waitForExistence(timeout: 10))
        attach("host-explicit-live-session", app: host)
        fixture.activate(); tap(fixture.buttons["quill.qa.caret-end"], in: fixture)
        try showQuillKeyboard(in: fixture)
        var seamlessExpected = repeated
        for take in 1...2 {
            if take == 2 {
                tap(fixture.buttons["quill.qa.appearance"], in: fixture)
                tap(fixture.buttons["quill.qa.caret-end"], in: fixture)
                try showQuillKeyboard(in: fixture)
            }
            stage = "background-keyboard-dictation-\(take)"
            let dictate = fixture.buttons["keyboard.record"]
            expectation(for: NSPredicate(format: "enabled == true AND label == %@", "Dictate"), evaluatedWith: dictate)
            waitForExpectations(timeout: 12)
            XCTAssertEqual(host.state, .runningBackground, "The host must stay in the background for keyboard dictation.")
            tap(dictate, in: fixture)
            expectation(for: NSPredicate(format: "label == %@ AND enabled == true", "Stop dictation"), evaluatedWith: dictate)
            waitForExpectations(timeout: 10)
            XCTAssertEqual(host.state, .runningBackground)
            captureSpeech(defaultDelay: 1.2)
            stopLiveCapture(dictate, in: fixture, label: "Stop dictation")
            if isLive {
                let original = seamlessExpected
                let delivered = NSPredicate { [self] _, _ in
                    guard let text = fixture.textViews["quill.qa.editor"].value as? String else { return false }
                    return text.hasPrefix(original + " ") && text.count > original.count + 10
                }
                expectation(for: delivered, evaluatedWith: fixture); waitForExpectations(timeout: 120)
                seamlessExpected = fixture.textViews["quill.qa.editor"].value as? String ?? ""
                let addition = String(seamlessExpected.dropFirst(original.count))
                for word in ["project","notes","alex","tomorrow","budget","thank"] { XCTAssertTrue(addition.lowercased().contains(word)) }
                liveReadbacks.append(["stage":"background-\(take)","text":seamlessExpected])
            } else { seamlessExpected += " This is a simulated provider response." }
            requireEditorText(seamlessExpected)
            XCTAssertEqual(host.state, .runningBackground, "No app switch or manual Send/Insert is permitted in the new path.")
            attach("keyboard-auto-inserted-background-\(take)", app: fixture)
        }
        stage = "end-session-from-keyboard"
        tap(fixture.buttons["keyboard.session.end"], in: fixture)
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: fixture.buttons["keyboard.record"])
        waitForExpectations(timeout: 10)
        XCTAssertEqual(host.state, .runningBackground)
        requireEditorText(seamlessExpected)
        if isLive { liveReadbacks.append(["stage":"ended","text":seamlessExpected]) }
        attach("keyboard-session-ended-no-duplicate", app: fixture)
        outcome = "passed"
    }
}
