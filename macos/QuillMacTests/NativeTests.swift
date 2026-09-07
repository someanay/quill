import XCTest
import QuillCore
@testable import QuillMac

final class NativeTests: XCTestCase {
    func testBundledCopyHasOnboardingAndMacPermissionText() {
        XCTAssertNotEqual(Copy.text("onboarding.welcome.cta"), "onboarding.welcome.cta")
        XCTAssertNotEqual(Copy.text("mac.accessibility.disclosure"), "mac.accessibility.disclosure")
        XCTAssertTrue(Copy.catalog.strings.count > 300)
    }
    func testFontsArePackaged() {
        XCTAssertNotNil(Bundle.main.url(forResource: "ibm_plex_sans", withExtension: "ttf", subdirectory: "fonts"))
        XCTAssertNotNil(Bundle.main.url(forResource: "ibm_plex_serif", withExtension: "ttf", subdirectory: "fonts"))
    }
    func testNoRemoteCopyURL() { XCTAssertFalse(Copy.catalog.strings.keys.contains("copy.remote_url")) }
    @MainActor func testDiagnosticsOnlyContainsAllowlistedState() {
        let report = Diagnostics.report()
        XCTAssertTrue(report.contains("OS:")); XCTAssertTrue(report.contains("Architecture:"))
        XCTAssertFalse(report.contains(NSHomeDirectory())); XCTAssertFalse(report.contains("API key:")); XCTAssertFalse(report.contains("transcript:"))
    }
}
