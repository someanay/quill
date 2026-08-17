import XCTest
// The test target compiles Shared/ directly, so the types are in this module — no import needed.

// Mirrors the Windows/macOS pure-logic suite, including the W-2 (short-fence) and W-3 ($-literal) regressions.
final class CoreTests: XCTestCase {

    func testCleanupGuardAcceptsCasingAndPunctuation() {
        XCTAssertTrue(CleanupGuard.isSafe(
            "hey can you send the report tomorrow morning",
            "Hey, can you send the report tomorrow morning?"))
    }

    func testCleanupGuardRejectsInvention() {
        XCTAssertFalse(CleanupGuard.isSafe(
            "send the report tomorrow",
            "I have sent the quarterly report to every client tomorrow morning."))
    }

    func testCleanupGuardRejectsAddedOrRemovedWords() {
        XCTAssertFalse(CleanupGuard.isSafe("I need the draft today", "I need the draft."))
        XCTAssertFalse(CleanupGuard.isSafe("I need the draft today", "I need the final draft today."))
    }

    func testCleanupGuardRejectsEmptyCandidate() {
        XCTAssertFalse(CleanupGuard.isSafe("hello", ""))
    }

    func testStripWrappingRemovesFenceAndLabel() {
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("Transcript: hello world"), "hello world")
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("```text\nhello world\n```"), "hello world")
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("literal words"), "literal words")
    }

    func testStripWrappingShortFenceDoesNotCrash() { // W-2
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("```"), "```")
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("````"), "````")
        XCTAssertEqual(GeminiTranscriptionClient.stripWrapping("`````"), "`````")
    }

    func testCustomWordsAppliedLastCaseInsensitive() {
        let text = DictationEngine.applyCustomWords(
            "call rohit about ZENO",
            [WordReplacement(heard: "rohit", replacement: "Rohit"),
             WordReplacement(heard: "zeno", replacement: "Zeno Health")])
        XCTAssertEqual(text, "call Rohit about Zeno Health")
    }

    func testCustomWordsReplacementIsLiteralDollar() { // W-3
        let text = DictationEngine.applyCustomWords(
            "that costs five bucks", [WordReplacement(heard: "bucks", replacement: "US$")])
        XCTAssertEqual(text, "that costs five US$")
    }

    func testSettingsNormalizeFallsBack() {
        var s = AppSettings()
        s.provider = .groq
        s.model = "not-a-real-model"
        s.historyRetentionMinutes = 999
        s.sessionCapSeconds = 200
        let n = SettingsPolicy.normalize(s)
        XCTAssertEqual(n.model, "whisper-large-v3-turbo")
        XCTAssertEqual(n.historyRetentionMinutes, 60)
        XCTAssertEqual(n.sessionCapSeconds, 195)
    }

    func testHistoryPruneDropsExpired() {
        let now = Date()
        let entries = [
            HistoryEntry(createdAt: now.addingTimeInterval(-30 * 60), text: "keep", application: "t", provider: .groq, wasCleaned: false),
            HistoryEntry(createdAt: now.addingTimeInterval(-61 * 60), text: "drop", application: "t", provider: .groq, wasCleaned: false),
        ]
        let pruned = HistoryStore.prune(entries, minutes: 60, now: now)
        XCTAssertEqual(pruned.count, 1)
        XCTAssertEqual(pruned.first?.text, "keep")
    }

    func testAppSettingsTolerantDecodeOfPartialJSON() {
        let json = Data(#"{"provider":"gemini","cleanupEnabled":true}"#.utf8)
        let decoded = try! JSONDecoder().decode(AppSettings.self, from: json)
        XCTAssertEqual(decoded.provider, .gemini)
        XCTAssertTrue(decoded.cleanupEnabled)
        XCTAssertEqual(decoded.sessionCapSeconds, 300)
    }
}
