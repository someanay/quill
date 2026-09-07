import AppKit
import ApplicationServices
import Carbon
import Foundation
import QuillCore

private struct FieldCheck: Codable {
    let name: String
    let passed: Bool
    let detail: String
}

private struct FieldReport: Codable {
    var status = "running"
    var reason = ""
    var mode = ""
    var accessibilityTrusted = false
    var secureInputAtStart = false
    var system = ProcessInfo.processInfo.operatingSystemVersionString
    var adapter = "Unchanged TextInjector + SystemAccessibilityAccess"
    var checks: [FieldCheck] = []
}

private struct FieldFailure: Error, CustomStringConvertible {
    let description: String
    init(_ text: String) { description = text }
}

@main @MainActor struct FieldProbe {
    static func main() async {
        guard ProcessInfo.processInfo.environment["GITHUB_ACTIONS"] == "true",
              ProcessInfo.processInfo.environment["RUNNER_OS"] == "macOS",
              CommandLine.arguments.count == 4, ["safari", "fixture"].contains(CommandLine.arguments[1]) else {
            fputs("CI-only use: QuillFieldProbe safari|fixture EVIDENCE_DIRECTORY FIXTURE_APP_PATH\n", stderr)
            exit(64)
        }
        let output = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true).standardizedFileURL
        var report = FieldReport(); report.mode = CommandLine.arguments[1]
        do { try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true) }
        catch { fputs("Cannot create field evidence directory.\n", stderr); exit(1) }
        NSApplication.shared.setActivationPolicy(.prohibited)
        report.accessibilityTrusted = AXIsProcessTrusted()
        report.secureInputAtStart = IsSecureEventInputEnabled()
        guard report.accessibilityTrusted else {
            report.status = "blocked"; report.reason = "The actual probe process lacks Accessibility trust. No permission settings were changed."
            finish(report, output: output)
        }
        guard !report.secureInputAtStart else {
            report.status = "blocked"; report.reason = "Secure keyboard input was already active before the probe."
            finish(report, output: output)
        }
        let clipboardChange = NSPasteboard.general.changeCount
        do {
            if report.mode == "safari" { try await safari(output: output, report: &report) }
            else { try await fixture(url: URL(fileURLWithPath: CommandLine.arguments[3]), report: &report) }
            report.checks.append(FieldCheck(name: "clipboard unchanged", passed: NSPasteboard.general.changeCount == clipboardChange, detail: "Compared clipboard change count before and after direct AX tests; clipboard contents were never read."))
            report.status = report.checks.allSatisfy(\.passed) ? "passed" : "failed"
            report.reason = report.status == "passed" ? "Every real separate-process field check passed." : "At least one real field check failed; see individual results."
        } catch {
            report.status = "failed"; report.reason = String(describing: error)
        }
        finish(report, output: output)
    }

    private static func safari(output: URL, report: inout FieldReport) async throws {
        let bundle = "com.apple.Safari"
        guard NSRunningApplication.runningApplications(withBundleIdentifier: bundle).isEmpty else {
            throw FieldFailure("Safari was already running; refusing to touch existing browser state.")
        }
        guard let application = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundle) else {
            throw FieldFailure("Safari is not installed.")
        }
        var browser: NSRunningApplication?
        defer { if let browser, !browser.isTerminated { _ = browser.forceTerminate() } }
        let injector = TextInjector()
        let cases: [(String, String, String, NSRange, String, String)] = [
            ("Safari textarea append", "textarea", "Hello", NSRange(location: 5, length: 0), "world", "Hello world"),
            ("Safari textarea Unicode selected replacement", "textarea", "Hello 🌍 end", ("Hello 🌍 end" as NSString).range(of: "🌍"), "भारत 👋🏽", "Hello भारत 👋🏽 end"),
            ("Safari contenteditable", "contenteditable", "Hello", NSRange(location: 5, length: 0), "café 🌍", "Hello café 🌍")
        ]
        for (index, item) in cases.enumerated() {
            let (name, kind, before, selection, payload, expected) = item
            do {
                let page = try makePage(kind: kind, text: before, index: String(index), output: output)
                browser = try await open(document: page, application: application)
                let initial = try await focused(injector: injector, bundle: bundle, text: before)
                let native = SystemAccessibilityAccess()
                let capabilities = "selectedTextWritable=\(native.isSettable(initial.element, kAXSelectedTextAttribute)), valueWritable=\(native.isSettable(initial.element, kAXValueAttribute))"
                try setRange(selection, element: initial.element)
                let target = try await focused(injector: injector, bundle: bundle, text: before, selection: selection)
                let result = try await injector.insert(payload, target: target)
                let actual = attribute(target.element, kAXValueAttribute) as? String
                guard result == .confirmed, actual == expected else {
                    // Diagnostic only: never retry the write. WebKit may acknowledge an
                    // AX request before its separate web-content process updates readback.
                    try await Task.sleep(nanoseconds: 500_000_000)
                    let settled = attribute(target.element, kAXValueAttribute) as? String
                    throw FieldFailure("\(capabilities), result=\(result), expected=\(String(reflecting: expected)), immediate=\(String(reflecting: actual)), after500ms=\(String(reflecting: settled))")
                }
                let expectedCaret = InsertionComposer.compose(existing: before, transcript: payload, selection: selection).caret
                _ = try await focused(injector: injector, bundle: bundle, text: expected, selection: NSRange(location: expectedCaret, length: 0))
                report.checks.append(FieldCheck(name: name, passed: true, detail: "Actual Safari AX readback confirmed \(String(reflecting: expected))."))
            } catch {
                report.checks.append(FieldCheck(name: name, passed: false, detail: "\(error); \(observation())"))
            }
        }
        // Diagnose the independent full-value mechanism on a fresh document. Never
        // resend or apply a fallback to a field whose selected-text write was accepted.
        do {
            let page = try makePage(kind: "textarea", text: "Fresh", index: "full-value-diagnostic", output: output)
            browser = try await open(document: page, application: application)
            let target = try await focused(injector: injector, bundle: bundle, text: "Fresh")
            let native = SystemAccessibilityAccess()
            let writable = native.isSettable(target.element, kAXValueAttribute)
            let status = AXUIElementSetAttributeValue(target.element, kAXValueAttribute as CFString, "Fresh diagnostic ✓" as CFString)
            try await Task.sleep(nanoseconds: 500_000_000)
            let actual = attribute(target.element, kAXValueAttribute) as? String
            report.checks.append(FieldCheck(name: "Safari independent full-value diagnostic", passed: status == .success && actual == "Fresh diagnostic ✓", detail: "AXValue writable=\(writable), status=\(status.rawValue), after500ms=\(String(reflecting: actual)). This is direct diagnostic AX, not a production-adapter pass."))
        } catch {
            report.checks.append(FieldCheck(name: "Safari independent full-value diagnostic", passed: false, detail: String(describing: error)))
        }
        do {
            let page = try makePage(kind: "password", text: "synthetic-qa-only", index: "password", output: output)
            browser = try await open(document: page, application: application)
            let detail = try await secureRefused(injector: injector, bundle: bundle)
            report.checks.append(FieldCheck(name: "Safari password refusal", passed: true, detail: detail))
        } catch {
            report.checks.append(FieldCheck(name: "Safari password refusal", passed: false, detail: "\(error); \(observation())"))
        }
        if let browser, !browser.isTerminated {
            guard browser.forceTerminate() else { throw FieldFailure("Could not close the synthetic Safari process.") }
            try await wait("Safari exit and secure input cleanup") { browser.isTerminated && !IsSecureEventInputEnabled() }
        }
    }

    private static func fixture(url: URL, report: inout FieldReport) async throws {
        let bundle = "com.tihor.quill.mac.qa.fields"
        guard NSRunningApplication.runningApplications(withBundleIdentifier: bundle).isEmpty else {
            throw FieldFailure("The QA fixture was already running; refusing to reuse an existing window.")
        }
        var application: NSRunningApplication?
        defer { if let application, !application.isTerminated { _ = application.forceTerminate() } }
        let injector = TextInjector()
        application = try await open(application: url)
        let before = "Quill QA normal"
        let initial = try await focused(injector: injector, bundle: bundle, text: before)
        let selection = NSRange(location: before.utf16.count, length: 0)
        try setRange(selection, element: initial.element)
        let target = try await focused(injector: injector, bundle: bundle, text: before, selection: selection)
        let result = try await injector.insert("✓ नमस्ते 🌍", target: target)
        guard result == .confirmed, attribute(target.element, kAXValueAttribute) as? String == "Quill QA normal ✓ नमस्ते 🌍" else {
            throw FieldFailure("Real native NSTextField insertion was not confirmed.")
        }
        report.checks.append(FieldCheck(name: "native NSTextField insertion", passed: true, detail: "Production adapter confirmed actual separate-process AppKit text including Hindi and emoji."))
        if let old = application {
            guard old.forceTerminate() else { throw FieldFailure("Could not terminate normal QA fixture before secure-field test.") }
            try await wait("normal fixture exit") { old.isTerminated }
        }
        application = try await open(application: url, arguments: ["--secure"])
        let detail = try await secureRefused(injector: injector, bundle: bundle)
        report.checks.append(FieldCheck(name: "native NSSecureTextField refusal", passed: true, detail: detail))
    }

    private static func makePage(kind: String, text: String, index: String, output: URL) throws -> URL {
        let escaped = text.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
        let field: String
        switch kind {
        case "textarea": field = "<textarea id=editor autofocus aria-label='Quill QA textarea'>\(escaped)</textarea>"
        case "contenteditable": field = "<div id=editor contenteditable=true role=textbox tabindex=0 autofocus aria-label='Quill QA contenteditable'>\(escaped)</div>"
        default: field = "<input id=editor type=password autofocus aria-label='Quill QA password' value='\(escaped)'>"
        }
        let html = """
        <!doctype html><html lang=en><meta charset=utf-8><title>Quill isolated AX fixture</title>
        <style>body{font:24px sans-serif;margin:80px}textarea,div,input{font:24px sans-serif;width:620px;min-height:100px;border:2px solid #234DCE;padding:20px}</style>
        <h1>Quill synthetic field test</h1>\(field)
        <script>window.addEventListener('load',()=>document.getElementById('editor').focus());</script></html>
        """
        let page = output.appendingPathComponent("field-\(index).html")
        try Data(html.utf8).write(to: page, options: .atomic)
        return page
    }

    private static func open(document: URL? = nil, application: URL, arguments: [String] = []) async throws -> NSRunningApplication {
        let configuration = NSWorkspace.OpenConfiguration()
        configuration.activates = true; configuration.addsToRecentItems = false
        configuration.arguments = arguments + ["-ApplePersistenceIgnoreState", "YES"]
        return try await withCheckedThrowingContinuation { continuation in
            let complete: (NSRunningApplication?, Error?) -> Void = { app, error in
                if let error { continuation.resume(throwing: error) }
                else if let app { continuation.resume(returning: app) }
                else { continuation.resume(throwing: FieldFailure("Application launch returned no process.")) }
            }
            if let document { NSWorkspace.shared.open([document], withApplicationAt: application, configuration: configuration, completionHandler: complete) }
            else { NSWorkspace.shared.openApplication(at: application, configuration: configuration, completionHandler: complete) }
        }
    }

    private static func focused(injector: TextInjector, bundle: String, text: String, selection: NSRange? = nil) async throws -> FocusSnapshot {
        var found: FocusSnapshot?
        try await wait("focused synthetic \(bundle) field") {
            guard let target = try injector.snapshot(), target.bundleID == bundle, target.originalText == text else { return false }
            if let selection {
                guard target.originalRange?.location == selection.location, target.originalRange?.length == selection.length else { return false }
            }
            found = target; return true
        }
        return found!
    }

    private static func secureRefused(injector: TextInjector, bundle: String) async throws -> String {
        var detail = ""
        try await wait("actual secure field refusal") {
            guard NSWorkspace.shared.frontmostApplication?.bundleIdentifier == bundle else { return false }
            do { _ = try injector.snapshot(); return false }
            catch let error as QuillError where error == .sensitiveField {
                detail = "Production snapshot refused the real secure field before capture. \(observation())"
                return true
            }
        }
        return detail
    }

    private static func setRange(_ selection: NSRange, element: AXUIElement) throws {
        var range = CFRange(location: selection.location, length: selection.length)
        guard let value = AXValueCreate(.cfRange, &range), AXUIElementSetAttributeValue(element, kAXSelectedTextRangeAttribute as CFString, value) == .success else {
            throw FieldFailure("Native field refused the selected-text range setup.")
        }
    }

    private static func attribute(_ element: AXUIElement, _ name: String) -> CFTypeRef? {
        var value: CFTypeRef?
        return AXUIElementCopyAttributeValue(element, name as CFString, &value) == .success ? value : nil
    }

    private static func observation() -> String {
        let system = SystemAccessibilityAccess()
        guard let app = system.frontmostApplication(), let element = system.focusedElement(process: app.process) else {
            return "No focused accessible element. secureInput=\(IsSecureEventInputEnabled())"
        }
        // Never read the secure field's value, including for failure diagnostics.
        let role = attribute(element, kAXRoleAttribute) as? String ?? "none"
        let subrole = attribute(element, kAXSubroleAttribute) as? String ?? "none"
        return "bundle=\(app.bundleID ?? "none"), role=\(role), subrole=\(subrole), secureInput=\(IsSecureEventInputEnabled())"
    }

    private static func wait(_ description: String, condition: () throws -> Bool) async throws {
        let deadline = Date().addingTimeInterval(10)
        while Date() < deadline {
            if try condition() { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        throw FieldFailure("Timed out waiting for \(description).")
    }

    private static func finish(_ report: FieldReport, output: URL) -> Never {
        do {
            let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(report)
            try data.write(to: output.appendingPathComponent("\(report.mode)-report.json"), options: .atomic)
            print(String(decoding: data, as: UTF8.self))
        } catch { fputs("Could not preserve real field evidence.\n", stderr); exit(1) }
        exit(report.status == "passed" ? 0 : report.status == "blocked" ? 78 : 1)
    }
}
