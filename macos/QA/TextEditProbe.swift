import AppKit
import ApplicationServices
import Carbon
import Foundation
import QuillCore

// A separate CI executable compiles the production adapter verbatim. It has no fake
// AccessibilityAccess, permission override, dictation key or entry point in Quill.app.
private struct ProbeCheck: Codable {
    let name: String
    let passed: Bool
    let detail: String
}

private struct ProbeReport: Codable {
    var status = "running"
    var reason = ""
    var system = ProcessInfo.processInfo.operatingSystemVersionString
    var accessibilityTrusted = false
    var secureInputActive = false
    var process = ProcessInfo.processInfo.processIdentifier
    var adapter = "Unchanged TextInjector + SystemAccessibilityAccess"
    var editor = "com.apple.TextEdit"
    var checks: [ProbeCheck] = []
}

private struct ProbeFailure: Error, CustomStringConvertible {
    let description: String
    init(_ message: String) { description = message }
}

@main @MainActor struct TextEditProbe {
    static func main() async {
        guard ProcessInfo.processInfo.environment["GITHUB_ACTIONS"] == "true",
              ProcessInfo.processInfo.environment["RUNNER_OS"] == "macOS",
              CommandLine.arguments.count == 2 else {
            fputs("This disposable-document probe runs only on an isolated GitHub macOS runner. Supply its evidence directory.\n", stderr)
            exit(64)
        }
        let output = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true).standardizedFileURL
        var report = ProbeReport()
        do { try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true) }
        catch { fputs("Cannot create probe evidence directory.\n", stderr); exit(1) }

        NSApplication.shared.setActivationPolicy(.prohibited)
        report.accessibilityTrusted = AXIsProcessTrusted()
        report.secureInputActive = IsSecureEventInputEnabled()
        let injector = TextInjector()
        if !report.accessibilityTrusted {
            do {
                _ = try injector.snapshot()
                report.checks.append(ProbeCheck(name: "untrusted client refusal", passed: false, detail: "Production snapshot unexpectedly allowed an untrusted client."))
                report.status = "failed"; report.reason = "Production Accessibility permission gate failed."
            } catch {
                let refused = (error as? QuillError) == .noAccessibility
                report.checks.append(ProbeCheck(name: "untrusted client refusal", passed: refused, detail: String(describing: error)))
                report.status = refused ? "blocked" : "failed"
                report.reason = "AXIsProcessTrusted returned false for the real CI probe process. No TCC settings were modified and no insertion was attempted."
            }
            finish(report, output: output)
        }
        if report.secureInputActive {
            report.status = "blocked"; report.reason = "macOS reports secure keyboard input active before testing."
            finish(report, output: output)
        }
        guard NSRunningApplication.runningApplications(withBundleIdentifier: "com.apple.TextEdit").isEmpty else {
            report.status = "blocked"; report.reason = "TextEdit was already running; the probe refuses to touch pre-existing documents."
            finish(report, output: output)
        }
        var editor: NSRunningApplication?
        var phase = "starting TextEdit"
        let clipboardChange = NSPasteboard.general.changeCount
        do {
            guard let editorURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.apple.TextEdit") else {
                throw ProbeFailure("TextEdit is not installed on the runner.")
            }
            let cases: [(String, String, NSRange, String, String)] = [
                ("empty field", "", NSRange(location: 0, length: 0), "Hello world", "Hello world"),
                ("append separator", "Hello", NSRange(location: 5, length: 0), "world", "Hello world"),
                ("middle caret", "Hello world", NSRange(location: 5, length: 0), " brave", "Hello brave world"),
                ("selected replacement", "Hello world", NSRange(location: 6, length: 5), "there", "Hello there"),
                ("zero caret preserves existing text", "existing", NSRange(location: 0, length: 0), "new ", "new existing"),
                ("Unicode append", "नमस्ते", NSRange(location: "नमस्ते".utf16.count, length: 0), "🌍 café", "नमस्ते 🌍 café"),
                ("Unicode selected replacement", "Hello 🌍 end", ("Hello 🌍 end" as NSString).range(of: "🌍"), "भारत 👋🏽", "Hello भारत 👋🏽 end")
            ]
            for (index, item) in cases.enumerated() {
                let (name, before, selection, payload, expected) = item
                let document = output.appendingPathComponent("textedit-case-\(index).txt")
                try Data(before.utf8).write(to: document, options: .atomic)
                editor = try await open(document: document, editorURL: editorURL)
                phase = name + ": initial field"
                let initial = try await focused(injector: injector, editor: editor!, expected: before, document: document)
                phase = name + ": requested selection"
                try setSelection(selection, element: initial.element)
                let target = try await focused(injector: injector, editor: editor!, expected: before, selection: selection, document: document, prepareSelection: true)
                phase = name + ": insertion confirmation"
                let result = try await injector.insert(payload, target: target)
                let actual = attribute(target.element, kAXValueAttribute) as? String
                guard result == .confirmed, actual == expected else {
                    throw ProbeFailure("\(name): result=\(result), expected=\(String(reflecting: expected)), actual=\(String(reflecting: actual))")
                }
                report.checks.append(ProbeCheck(name: name, passed: true, detail: "Real TextEdit AX readback confirmed \(String(reflecting: actual!))."))
                print("PASS: \(name)")
            }

            // Simulate an edit made while transcription is pending using the real editor.
            let target = try await focused(injector: injector, editor: editor!, expected: "Hello भारत 👋🏽 end")
            try setSelection(NSRange(location: 0, length: 0), element: target.element)
            let beforeSelectionTest = attribute(target.element, kAXValueAttribute) as? String
            try await expectFocusRefusal(injector: injector, target: target)
            guard attribute(target.element, kAXValueAttribute) as? String == beforeSelectionTest else {
                throw ProbeFailure("Changed-selection refusal mutated TextEdit.")
            }
            report.checks.append(ProbeCheck(name: "selection drift refusal", passed: true, detail: "Changed the real TextEdit selection after capture; Quill refused without writing."))

            let textTarget = try await focused(injector: injector, editor: editor!, expected: beforeSelectionTest!)
            guard AXUIElementSetAttributeValue(textTarget.element, kAXSelectedTextAttribute as CFString, "manual edit " as CFString) == .success else {
                throw ProbeFailure("TextEdit refused the setup edit for the content-drift case.")
            }
            let manuallyEdited = attribute(textTarget.element, kAXValueAttribute) as? String
            guard manuallyEdited != textTarget.originalText else { throw ProbeFailure("TextEdit did not apply the setup edit.") }
            if let originalRange = textTarget.originalRange {
                try setSelection(NSRange(location: originalRange.location, length: originalRange.length), element: textTarget.element)
            }
            try await expectFocusRefusal(injector: injector, target: textTarget)
            guard attribute(textTarget.element, kAXValueAttribute) as? String == manuallyEdited else {
                throw ProbeFailure("Content-drift refusal changed the edited text.")
            }
            report.checks.append(ProbeCheck(name: "content drift refusal", passed: true, detail: "Changed actual TextEdit contents after capture; Quill preserved the manual edit."))

            let focusTarget = try await focused(injector: injector, editor: editor!, expected: manuallyEdited!)
            guard let finder = NSRunningApplication.runningApplications(withBundleIdentifier: "com.apple.finder").first,
                  finder.activate(options: [.activateIgnoringOtherApps]) else { throw ProbeFailure("Could not focus Finder for the cross-process focus-change test.") }
            try await waitUntil("Finder foreground") { NSWorkspace.shared.frontmostApplication?.bundleIdentifier == "com.apple.finder" }
            try await expectFocusRefusal(injector: injector, target: focusTarget)
            guard attribute(focusTarget.element, kAXValueAttribute) as? String == manuallyEdited else {
                throw ProbeFailure("Application-focus refusal changed TextEdit.")
            }
            report.checks.append(ProbeCheck(name: "application focus drift refusal", passed: true, detail: "Activated the real Finder process after capture; TextEdit was unchanged."))
            guard NSPasteboard.general.changeCount == clipboardChange else {
                throw ProbeFailure("Direct Accessibility insertion unexpectedly changed the clipboard.")
            }
            report.checks.append(ProbeCheck(name: "clipboard unchanged", passed: true, detail: "The clipboard change count stayed identical across all direct insertion and refusal cases."))
            report.status = "passed"
            report.reason = "Seven insertions and three stale-focus/content refusals exercised unchanged production code against the separate TextEdit process; clipboard unchanged."
        } catch {
            report.status = "failed"; report.reason = phase + ": " + String(describing: error)
            report.checks.append(ProbeCheck(name: "integration execution", passed: false, detail: report.reason))
            failureScreenshot(output: output)
        }
        // TextEdit was absent before this isolated probe, and only these synthetic files
        // were opened. Force quit avoids an unsaved-document dialog hanging CI cleanup.
        if let editor, !editor.isTerminated { _ = editor.forceTerminate() }
        finish(report, output: output)
    }

    private static func open(document: URL, editorURL: URL) async throws -> NSRunningApplication {
        let configuration = NSWorkspace.OpenConfiguration()
        configuration.activates = true
        configuration.addsToRecentItems = false
        configuration.arguments = ["-ApplePersistenceIgnoreState", "YES"]
        // Cold launch and document opening are separate native operations. On
        // current Intel runners, the combined request can launch TextEdit without
        // opening its first document. This only opens the owned synthetic file.
        if NSRunningApplication.runningApplications(withBundleIdentifier: "com.apple.TextEdit").isEmpty {
            let application: NSRunningApplication = try await withCheckedThrowingContinuation { continuation in
                NSWorkspace.shared.openApplication(at: editorURL, configuration: configuration) { app, error in
                    if let error { continuation.resume(throwing: error) }
                    else if let app { continuation.resume(returning: app) }
                    else { continuation.resume(throwing: ProbeFailure("TextEdit launch returned neither app nor error.")) }
                }
            }
            _ = application.activate(options: [.activateAllWindows, .activateIgnoringOtherApps])
        }
        return try await withCheckedThrowingContinuation { continuation in
            NSWorkspace.shared.open([document], withApplicationAt: editorURL, configuration: configuration) { app, error in
                if let error { continuation.resume(throwing: error) }
                else if let app { continuation.resume(returning: app) }
                else { continuation.resume(throwing: ProbeFailure("TextEdit launch returned neither app nor error.")) }
            }
        }
    }

    private static func focused(injector: TextInjector, editor: NSRunningApplication, expected: String, selection: NSRange? = nil, document: URL? = nil, prepareSelection: Bool = false) async throws -> FocusSnapshot {
        var captured: FocusSnapshot?
        var last = "no snapshot"
        var selectionReadySince: Date?
        do { try await waitUntil("TextEdit focused text and selection") {
            guard let snapshot = try injector.snapshot() else {
                let application = AXUIElementCreateApplication(editor.processIdentifier)
                let windows = attribute(application, kAXWindowsAttribute) as? [AXUIElement] ?? []
                last = "no editable focused snapshot; TextEdit windows=\(windows.count)"
                return false
            }
            last = "bundle=\(snapshot.bundleID ?? "none"), textLength=\(snapshot.originalText?.utf16.count ?? -1), range=\(String(describing: snapshot.originalRange)), expectedLength=\(expected.utf16.count), expectedRange=\(String(describing: selection))"
            guard snapshot.process == editor.processIdentifier,
                  snapshot.bundleID == "com.apple.TextEdit", snapshot.originalText == expected else { return false }
            if let document {
                // Opening a document completes before its window necessarily becomes
                // focused. Two adjacent cases intentionally share the same text; an
                // old window must not satisfy readiness for the next synthetic file.
                let application = AXUIElementCreateApplication(editor.processIdentifier)
                guard let focusedWindow = attribute(application, kAXFocusedWindowAttribute), CFGetTypeID(focusedWindow) == AXUIElementGetTypeID() else { return false }
                let window = focusedWindow as! AXUIElement
                let title = attribute(window, kAXTitleAttribute) as? String ?? ""
                let location = (attribute(window, kAXDocumentAttribute) as? String).flatMap(URL.init(string:))
                let sameFile = location?.standardizedFileURL == document.standardizedFileURL
                last += ", expectedDocument=\(document.lastPathComponent), focusedTitle=\(title)"
                guard sameFile || title == document.lastPathComponent || title.hasPrefix(document.lastPathComponent + " —") else { return false }
            }
            if let selection {
                guard snapshot.originalRange?.location == selection.location, snapshot.originalRange?.length == selection.length else {
                    selectionReadySince = nil
                    // A newly opened TextEdit document can finish restoring its
                    // caret after AX first exposes the text. Establish only the
                    // owned synthetic document's test selection before capture.
                    // Production insertion and its confirmation are never retried.
                    if prepareSelection { try setSelection(selection, element: snapshot.element) }
                    return false
                }
                if prepareSelection {
                    if selectionReadySince == nil { selectionReadySince = Date() }
                    guard Date().timeIntervalSince(selectionReadySince!) >= 0.2 else { return false }
                }
            }
            captured = snapshot
            return true
        } } catch { throw ProbeFailure("\(error) Last observation: \(last)") }
        return captured!
    }

    private static func waitUntil(_ description: String, condition: () throws -> Bool) async throws {
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if try condition() { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        throw ProbeFailure("Timed out waiting for \(description). Frontmost bundle: \(NSWorkspace.shared.frontmostApplication?.bundleIdentifier ?? "none").")
    }

    private static func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        var value: CFTypeRef?
        return AXUIElementCopyAttributeValue(element, name as CFString, &value) == .success ? value : nil
    }

    private static func setSelection(_ selection: NSRange, element: AXUIElement) throws {
        var range = CFRange(location: selection.location, length: selection.length)
        guard let value = AXValueCreate(.cfRange, &range),
              AXUIElementSetAttributeValue(element, kAXSelectedTextRangeAttribute as CFString, value) == .success else {
            throw ProbeFailure("TextEdit refused the test selection.")
        }
    }

    private static func expectFocusRefusal(injector: TextInjector, target: FocusSnapshot) async throws {
        do {
            _ = try await injector.insert("MUST NOT INSERT", target: target)
            throw ProbeFailure("A stale target was not refused.")
        } catch let error as QuillError where error == .focusChanged { return }
    }

    private static func failureScreenshot(output: URL) {
        let capture = Process(); capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture"); capture.arguments = ["-x", output.appendingPathComponent("failure-at-check.png").path]
        do { try capture.run(); capture.waitUntilExit() } catch { print("Failure screenshot unavailable: \(error)") }
    }
    private static func finish(_ report: ProbeReport, output: URL) -> Never {
        do {
            let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(report)
            try data.write(to: output.appendingPathComponent("textedit-report.json"), options: .atomic)
            print(String(decoding: data, as: UTF8.self))
        } catch { fputs("Could not write TextEdit probe evidence.\n", stderr); exit(1) }
        exit(report.status == "passed" ? 0 : report.status == "blocked" ? 78 : 1)
    }
}
