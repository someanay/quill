import XCTest
@testable import QuillCore

final class PolicyTests: XCTestCase {
    func testAndroidDefaults() {
        let s = QuillSettings()
        XCTAssertEqual(s.provider, .groq)
        XCTAssertEqual(s.model, "whisper-large-v3-turbo")
        XCTAssertEqual(s.language, "en")
        XCTAssertEqual(s.sessionCapSeconds, 300)
        XCTAssertEqual(s.retentionMinutes, 60)
        XCTAssertTrue(s.historyEnabled)
        XCTAssertFalse(s.privateMode)
        XCTAssertEqual(s.effectiveCleanupProvider, .groq)
    }
    func testAutomaticCleanupOnlyFreePolicy() {
        for provider in Provider.allCases {
            var s = QuillSettings(); s.provider = provider
            XCTAssertEqual(s.effectiveCleanupEnabled, [.groq, .gemini, .openrouter].contains(provider))
            s.cleanupAutomatic = false; s.cleanupProvider = .openai
            XCTAssertTrue(s.effectiveCleanupEnabled)
            XCTAssertEqual(s.effectiveCleanupProvider, .openai)
        }
    }
    func testRejectUnsafeEndpoint() {
        for value in ["http://example.com/v1", "https://user:pass@example.com/v1", "https://example.com/v1?q=x", "https://example.com/v1#x", "file:///tmp/data", "https://example.com/../keys", "https://example.com/%2e%2e/key"] {
            XCTAssertThrowsError(try Endpoint.normalize(value), value)
        }
        XCTAssertEqual(try Endpoint.normalize(" https://EXAMPLE.com/v1/ "), "https://example.com/v1")
    }
    func testCleanupPreservesUserWords() {
        XCTAssertTrue(CleanupGuard.preserves("um uh don't send it", "Do not send it."))
        XCTAssertTrue(CleanupGuard.preserves("I do not think so", "i dont think so"))
        XCTAssertFalse(CleanupGuard.preserves("what time is it", "It's currently 3pm."))
        XCTAssertFalse(CleanupGuard.preserves("type my email", "I'm ready to type your email"))
        XCTAssertFalse(CleanupGuard.preserves("Meet me at five", "Meet me at five. Meet me at five."))
        XCTAssertFalse(CleanupGuard.preserves("", ""))
    }
    func testVocabularyLiteralUnicodeAndWholeWords() {
        let words = [CustomWord(heard: "cat", replacement: "$1\\pet"), CustomWord(heard: "tihor", replacement: "Tihor")]
        XCTAssertEqual(WordSubstitutions.apply("Category cat CAT tihor", words), "Category $1\\pet $1\\pet Tihor")
        XCTAssertEqual(WordSubstitutions.apply("छोटा cat बड़ा", words), "छोटा $1\\pet बड़ा")
        XCTAssertEqual(WordSubstitutions.apply("A.B aXb", [CustomWord(heard: "a.b", replacement: "ok")]), "ok aXb")
    }
    func testCleanupThenWordsLast() {
        XCTAssertEqual(TextPipeline.finish(raw: "quill um", cleaned: "Quill.", words: [CustomWord(heard: "quill", replacement: "QUILL")]), "QUILL.")
        XCTAssertEqual(TextPipeline.finish(raw: "type my email", cleaned: "I will write your email", words: []), "type my email")
    }
    func testAddingExistingWordReplacesCaseInsensitiveEntryWithoutTruncation() {
        let initial = [CustomWord(heard: "Quill", replacement: "old"), CustomWord(heard: "cat", replacement: "Cat")]
        let replacement = String(repeating: "x", count: 250)
        let updated = WordSubstitutions.upsert(initial, heard: " quill ", replacement: " " + replacement + " ")
        XCTAssertEqual(updated.map(\.heard), ["cat", "quill"])
        XCTAssertEqual(updated.last?.replacement, replacement)
        XCTAssertEqual(WordSubstitutions.upsert(updated, heard: " ", replacement: "ignored"), updated)
    }
    func testSilenceAndCap() {
        var detector = SilenceDetector()
        XCTAssertFalse(detector.feed(rms: 0, elapsed: 1))
        XCTAssertFalse(detector.feed(rms: 0, elapsed: 2))
        XCTAssertFalse(detector.feed(rms: 0, elapsed: 5.49))
        XCTAssertTrue(detector.feed(rms: 0, elapsed: 5.5))
        XCTAssertFalse(detector.feed(rms: 600, elapsed: 5.6))
        XCTAssertFalse(detector.feed(rms: 0, elapsed: 6))
        XCTAssertFalse(detector.feed(rms: 0, elapsed: 9.4))
        XCTAssertTrue(RecordingLimits.shouldWarn(elapsed: 285, cap: 300))
        XCTAssertFalse(RecordingLimits.shouldWarn(elapsed: 300, cap: 300))
        XCTAssertTrue(RecordingLimits.reached(elapsed: 300, cap: 300))
    }
    func testSpacingPreservesPunctuationAndExistingText() {
        XCTAssertEqual(InsertionComposer.compose("hello", before: "Say", after: "world"), "hello")
        XCTAssertEqual(InsertionComposer.compose("hello", before: "Say", after: ""), " hello")
        XCTAssertEqual(InsertionComposer.compose("hello", before: "Say", after: "", replacingSelection: true), "hello")
        XCTAssertEqual(InsertionComposer.compose("hello", before: "Say ", after: ","), "hello")
        XCTAssertEqual(InsertionComposer.compose("hello", before: "(", after: ")"), "hello")
        XCTAssertEqual(InsertionComposer.compose("", before: "a", after: "b"), "")
    }
    func testRetentionAndPrivateGates() {
        let now = Date(timeIntervalSince1970: 10_000)
        let old = HistoryEntry(text: "old", provider: .groq, date: now.addingTimeInterval(-3601))
        let fresh = HistoryEntry(text: "new", provider: .groq, date: now)
        XCTAssertEqual(HistoryPolicy.prune([old, fresh], retentionMinutes: 60, now: now).map(\.text), ["new"])
        XCTAssertEqual(HistoryPolicy.prune([old, fresh], retentionMinutes: 0, now: now).count, 2)
        var s = QuillSettings(); s.privateMode = true
        XCTAssertFalse(s.shouldSaveHistory)
    }
    func testHandoffTTLAndValidation() {
        let now = Date(timeIntervalSince1970: 50)
        let handoff = KeyboardHandoff(text: "hello", now: now)
        XCTAssertTrue(handoff.isValid(now: now.addingTimeInterval(59)))
        XCTAssertFalse(handoff.isValid(now: now.addingTimeInterval(60)))
        XCTAssertFalse(handoff.isValid(now: now.addingTimeInterval(-1)))
        XCTAssertFalse(KeyboardHandoff(text: " ", now: now).isValid(now: now))
    }
    func testContactsRemainLocalCandidateNames() {
        let names = ContactVocabulary.extract(["Anne-Marie O'Brien", "anne-marie o'brien", "123", "a@example.com", "😀 Bob", "A B", "Dr. Singh"])
        XCTAssertTrue(names.contains("Anne-Marie O'Brien"))
        XCTAssertTrue(names.contains("O'Brien"))
        XCTAssertFalse(names.contains("123"))
        XCTAssertFalse(names.contains("A"))
        XCTAssertEqual(names.filter { $0.lowercased() == "anne-marie o'brien" }.count, 1)
    }
    func testCopyPlaceholders() {
        let catalog = CopyCatalog(strings: ["count": "%1$d of %2$d done", "x": "Hi %1$s", "escaped": "100%%"])
        XCTAssertEqual(catalog.text("count", "1", "3"), "1 of 3 done")
        XCTAssertEqual(catalog.text("x", "$1"), "Hi $1")
        XCTAssertEqual(catalog.text("escaped"), "100%")
    }
}
