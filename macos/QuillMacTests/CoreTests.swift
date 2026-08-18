import XCTest
@testable import QuillMac

// Mirrors windows/tests/Quill.Windows.Tests for the pure logic shared by the ports, including the
// overnight W-2 (short-fence) and W-3 ($-literal) regressions.
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
        let text = DictationOrchestrator.applyCustomWords(
            "call rohit about ZENO",
            [WordReplacement(heard: "rohit", replacement: "Rohit"),
             WordReplacement(heard: "zeno", replacement: "Zeno Health")])
        XCTAssertEqual(text, "call Rohit about Zeno Health")
    }

    func testCustomWordsReplacementIsLiteralDollar() { // W-3
        let text = DictationOrchestrator.applyCustomWords(
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
        XCTAssertEqual(n.sessionCapSeconds, 195) // 200 → nearest 15
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
        XCTAssertEqual(decoded.sessionCapSeconds, 300) // missing key → default
        XCTAssertEqual(decoded.historyRetentionMinutes, 60)
    }

    // MARK: - Expanded coverage (mirrors more of the Windows xUnit suite)

    func testRomanizeOnlyForExplicitEnglish() {
        XCTAssertEqual(RomanizePrompt.forLanguage("en"), RomanizePrompt.hinglish)
        XCTAssertEqual(RomanizePrompt.forLanguage("EN"), RomanizePrompt.hinglish) // case-insensitive
        XCTAssertNil(RomanizePrompt.forLanguage("hi"))
        XCTAssertNil(RomanizePrompt.forLanguage("auto"))
        XCTAssertNil(RomanizePrompt.forLanguage(nil))
    }

    func testNormalizeDisablesCleanupForUnsupportedProvider() {
        var s = AppSettings(); s.provider = .gemini; s.cleanupEnabled = true
        XCTAssertFalse(SettingsPolicy.normalize(s).cleanupEnabled) // Gemini has no cleanup catalog
    }

    func testNormalizeRepairsLegacyHotkeyAndCrossProviderModel() {
        var s = AppSettings()
        s.provider = .groq
        s.model = "gpt-4o-transcribe"   // an OpenAI model, invalid for Groq
        s.hotkey = "Ctrl+Alt+Space"     // the legacy default
        let n = SettingsPolicy.normalize(s)
        XCTAssertEqual(n.model, "whisper-large-v3-turbo")
        XCTAssertEqual(n.hotkey, "Alt+Q")
    }

    func testNormalizeSessionCapBounds() {
        func cap(_ v: Int) -> Int { var s = AppSettings(); s.sessionCapSeconds = v; return SettingsPolicy.normalize(s).sessionCapSeconds }
        XCTAssertEqual(cap(0), 0)       // uncapped
        XCTAssertEqual(cap(5), 15)      // min floor
        XCTAssertEqual(cap(200), 195)   // nearest 15
        XCTAssertEqual(cap(700), 600)   // max ceiling
    }

    func testNormalizeKeepsValidProviderSpecificCleanupModel() {
        var s = AppSettings(); s.provider = .groq; s.cleanupEnabled = true; s.cleanupModel = "llama-3.1-8b-instant"
        let n = SettingsPolicy.normalize(s)
        XCTAssertTrue(n.cleanupEnabled)
        XCTAssertEqual(n.cleanupModel, "llama-3.1-8b-instant")
    }

    func testProviderCatalogCoversEveryProvider() {
        for p in SttProvider.allCases { XCTAssertNotNil(ProviderCatalog.all[p], "missing descriptor for \(p)") }
        for p in SttProvider.allCases where p != .custom {
            let d = ProviderCatalog.all[p]!
            XCTAssertFalse(d.models.isEmpty, "\(p) should ship models")
            XCTAssertNotNil(d.keyConsoleURL, "\(p) should expose a key console link")
        }
        XCTAssertTrue(ProviderCatalog.all[.custom]!.models.isEmpty)
    }

    func testGeminiExtractHandlesMalformedResponsesWithoutCrash() { // W-4 defensive navigation
        XCTAssertEqual(GeminiTranscriptionClient.extractGeminiText(Data("{}".utf8)), "")
        XCTAssertEqual(GeminiTranscriptionClient.extractGeminiText(Data(#"{"candidates":[]}"#.utf8)), "")
        XCTAssertEqual(GeminiTranscriptionClient.extractGeminiText(Data(#"{"candidates":[{}]}"#.utf8)), "")
        let ok = Data(#"{"candidates":[{"content":{"parts":[{"text":"hello world"}]}}]}"#.utf8)
        XCTAssertEqual(GeminiTranscriptionClient.extractGeminiText(ok), "hello world")
    }

    func testCreateSilentWavIsWellFormed() {
        let url = KeyValidator.createSilentWav()
        defer { try? FileManager.default.removeItem(at: url) }
        let data = try! Data(contentsOf: url)
        XCTAssertEqual(data.count, 44 + 16_000 * 2 * 400 / 1000) // 44-byte header + 400ms @ 16k mono 16-bit
        XCTAssertEqual(String(decoding: data[0..<4], as: UTF8.self), "RIFF")
        XCTAssertEqual(String(decoding: data[8..<12], as: UTF8.self), "WAVE")
    }

    func testKeyValidatorShortCircuitsOnEmptyInput() async {
        let v = KeyValidator(session: .shared)
        if case .unverified = await v.validate(provider: .groq, key: "", model: "m", customBaseURL: "") {} else { XCTFail("empty key → unverified") }
        if case .unverified = await v.validate(provider: .groq, key: "k", model: "", customBaseURL: "") {} else { XCTFail("empty model → unverified") }
        if case .unverified = await v.validate(provider: .custom, key: "k", model: "m", customBaseURL: "not-a-url") {} else { XCTFail("bad custom URL → unverified") }
    }

    func testAppSettingsRoundTripPreservesFields() {
        var s = AppSettings()
        s.provider = .openRouter; s.model = "openai/whisper-large-v3-turbo"; s.cleanupEnabled = true
        s.historyRetentionMinutes = 1440
        s.customWords = [WordReplacement(heard: "zeno", replacement: "Zeno Health")]
        s.perAppStyles = ["Slack": .casual]; s.freeTierOnlyProviders = ["gemini": true]
        let data = try! JSONEncoder().encode(s)
        let back = try! JSONDecoder().decode(AppSettings.self, from: data)
        XCTAssertEqual(back, s)
    }

    func testResponseParserAcceptsKnownSuccessShapes() throws {
        func http(_ status: Int, _ contentType: String? = nil) -> HTTPURLResponse {
            HTTPURLResponse(url: URL(string: "https://api.test/v1/audio/transcriptions")!, statusCode: status,
                            httpVersion: nil, headerFields: contentType.map { ["Content-Type": $0] })!
        }
        XCTAssertEqual(try ResponseParser.parse(data: Data(#"{"text":"hello"}"#.utf8), response: http(200)).text, "hello")
        XCTAssertEqual(try ResponseParser.parse(data: Data(#"{"choices":[{"message":{"content":"hi"}}]}"#.utf8), response: http(200)).text, "hi")
        XCTAssertEqual(try ResponseParser.parse(data: Data("plain words".utf8), response: http(200, "text/plain; charset=utf-8")).text, "plain words")
    }

    func testResponseParserMapsAuthErrorToRejectedKey() {
        let resp = HTTPURLResponse(url: URL(string: "https://api.test/x")!, statusCode: 401, httpVersion: nil, headerFields: nil)!
        XCTAssertThrowsError(try ResponseParser.parse(data: Data(#"{"error":{"message":"bad"}}"#.utf8), response: resp)) { error in
            XCTAssertNotNil((error as? TranscriptionException)?.message.range(of: "API key was rejected", options: .caseInsensitive))
        }
    }

    func testCleanupGuardNormalizesContractions() {
        XCTAssertTrue(CleanupGuard.isSafe("i am going home", "I'm going home"))
        XCTAssertTrue(CleanupGuard.isSafe("do not do that", "don't do that"))
    }
}
