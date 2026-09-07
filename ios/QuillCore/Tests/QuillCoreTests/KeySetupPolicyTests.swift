import XCTest
@testable import QuillCore

final class KeySetupPolicyTests: XCTestCase {
    func testAndroidProviderOrderAndPlausibility() {
        XCTAssertEqual(KeySetupPolicy.guidedProviders, [.gemini, .groq, .openrouter])
        for (provider, prefix) in [(Provider.gemini, "AIza"), (.gemini, "AQ."), (.groq, "gsk_"), (.openrouter, "sk-or-")] {
            XCTAssertTrue(KeySetupPolicy.plausible(provider, " \(prefix)abcdefghijklmnop0123456789\n"))
            for suffix in ["short", "abcdefghijklmnop...", "abc defghijklmnop01234", "abcdefghijklmnop/01234"] {
                XCTAssertFalse(KeySetupPolicy.plausible(provider, prefix + suffix))
            }
            XCTAssertFalse(KeySetupPolicy.plausible(provider, prefix + String(repeating: "a", count: 4096)))
        }
        XCTAssertFalse(KeySetupPolicy.plausible(.openai, "sk-abcdefghijklmnop0123456789"))
        XCTAssertFalse(KeySetupPolicy.plausible(.custom, "abcdefghijklmnop0123456789"))
        XCTAssertFalse(KeySetupPolicy.plausible(.groq, "AIzaabcdefghijklmnop0123456789"))
    }
}
