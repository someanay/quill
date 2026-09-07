import XCTest
@testable import QuillCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

final class QuillCoreTests: XCTestCase {
    func testSettingsDefaultAndAutomaticCleanup() throws {
        var settings = QuillSettings()
        XCTAssertEqual(settings.provider, .groq); XCTAssertEqual(settings.speechModel, "whisper-large-v3-turbo")
        XCTAssertTrue(settings.shouldClean); XCTAssertTrue(settings.shouldSaveHistory)
        XCTAssertEqual(settings.historyRetentionMinutes, 60)
        settings.selectProvider(.openai); XCTAssertFalse(settings.shouldClean)
        settings.setCleanupEnabled(true); XCTAssertTrue(settings.shouldClean); XCTAssertFalse(settings.cleanupAutomatic)
        settings.selectCleanupProvider(.gemini); settings.cleanupModel = "custom-gemini"
        settings.selectCleanupProvider(.groq); settings.selectCleanupProvider(.gemini); XCTAssertEqual(settings.cleanupModel, "custom-gemini")
        settings.privateMode = true; XCTAssertFalse(settings.shouldSaveHistory)
        XCTAssertEqual(try JSONDecoder().decode(QuillSettings.self, from: JSONEncoder().encode(settings)), settings)
    }
    func testSettingsRejectOutOfRangeValues() {
        var settings = QuillSettings(); settings.sessionCapSeconds = 600; settings.historyRetentionMinutes = -1; settings.bubbleSize = "invalid"
        settings.normalize(); XCTAssertEqual(settings.sessionCapSeconds, 300); XCTAssertEqual(settings.historyRetentionMinutes, 60); XCTAssertEqual(settings.bubbleDiameter, 56)
    }
    func testSettingsUpgradeRetainsKnownValuesWhenNewFieldsAreAbsent() throws {
        let old = Data("{\"provider\":\"gemini\",\"privateMode\":true,\"historyRetentionMinutes\":1440,\"unknownFutureField\":123}".utf8)
        let settings = try JSONDecoder().decode(QuillSettings.self, from: old)
        XCTAssertEqual(settings.provider, .gemini); XCTAssertEqual(settings.speechModel, Provider.gemini.defaultSpeech)
        XCTAssertTrue(settings.privateMode); XCTAssertEqual(settings.historyRetentionMinutes, 1440); XCTAssertEqual(settings.sessionCapSeconds, 300)
    }
    func testEndpointsRejectCredentialExfiltrationShapes() throws {
        for url in ["http://localhost", "https://user:password@example.com", "https://example.com?a=b", "https://example.com/#part", "file:///tmp/a", "https://"] { XCTAssertThrowsError(try Endpoint.validated(url)) }
        XCTAssertEqual(try Endpoint.validated("https://example.com/v1").host, "example.com")
    }
    func testSilenceGraceAndReset() {
        var detector = SilenceDetector()
        XCTAssertFalse(detector.consume(rms: 0, elapsed: 0)); XCTAssertFalse(detector.consume(rms: 0, elapsed: 2))
        XCTAssertFalse(detector.consume(rms: 0, elapsed: 5.4)); XCTAssertTrue(detector.consume(rms: 0, elapsed: 5.5))
        XCTAssertFalse(detector.consume(rms: 650, elapsed: 6)); XCTAssertFalse(detector.consume(rms: 0, elapsed: 8)); XCTAssertFalse(detector.consume(rms: 0, elapsed: 10))
        XCTAssertTrue(detector.consume(rms: 0, elapsed: 11.5))
    }
    func testWavHeaderAndPayload() {
        let pcm = Data(repeating: 0, count: 12800), wav = WAV.encode(pcm: pcm)
        XCTAssertEqual(wav.count, 12844); XCTAssertEqual(String(data: wav.prefix(4), encoding: .utf8), "RIFF")
        XCTAssertEqual(Array(wav[24..<28]), [0x80,0x3e,0,0]); XCTAssertEqual(Array(wav[34..<36]), [16,0])
        XCTAssertEqual(wav.suffix(pcm.count), pcm)
    }
    func testCleanupGuardContractsAndRejectsAnswers() {
        XCTAssertTrue(CleanupGuard.preservesMeaning(raw: "i dont know", cleaned: "I do not know."))
        XCTAssertTrue(CleanupGuard.preservesMeaning(raw: "um uh meet me at five", cleaned: "Meet me at five."))
        XCTAssertFalse(CleanupGuard.preservesMeaning(raw: "type my email", cleaned: "I am ready to type your email."))
        XCTAssertFalse(CleanupGuard.preservesMeaning(raw: "meet me at five", cleaned: "Meet me at five. Meet me at five."))
        XCTAssertFalse(CleanupGuard.preservesMeaning(raw: "one two three four five six seven eight nine ten", cleaned: "one two three"))
    }
    func testVocabularyUnicodeBoundariesAndLiteralReplacement() {
        let words = [CustomWord(heard: "cat", replacement: "$5\\pet"), CustomWord(heard: "tihor", replacement: "Tihor")]
        XCTAssertEqual(WordSubstitutions.apply("CAT category tihor", words: words), "$5\\pet category Tihor")
        XCTAssertEqual(WordSubstitutions.apply("अना अनाय", words: [CustomWord(heard: "अना", replacement: "Anay")]), "Anay अनाय")
    }
    func testInsertionPreservesUnicodeAndSelections() {
        let a = InsertionComposer.compose(existing: "Hello 🌎", transcript: "again", selection: NSRange(location: 8, length: 0))
        XCTAssertEqual(a.text, "Hello 🌎 again"); XCTAssertEqual(a.caret, 14)
        XCTAssertEqual(InsertionComposer.compose(existing: "Hello world", transcript: "there", selection: NSRange(location: 6, length: 5)).text, "Hello there")
        XCTAssertEqual(InsertionComposer.compose(existing: "world", transcript: "Hello ", selection: NSRange(location: 0, length: 0)).text, "Hello world")
    }
    func testHistoryRetentionAndCap() {
        let now = Date(timeIntervalSince1970: 100000)
        let rows = (0..<1100).map { HistoryEntry(text: String($0), created: now.addingTimeInterval(-Double($0) * 60), provider: .groq, appName: "TextEdit", duration: 1) }
        XCTAssertEqual(HistoryPolicy.prune(rows, retentionMinutes: 60, now: now).count, 60)
        XCTAssertEqual(HistoryPolicy.prune(rows, retentionMinutes: 0, now: now).count, 1000)
    }
    func testProviderBodiesAndHeaderIsolation() throws {
        let wav = WAV.encode(pcm: Data(repeating: 0, count: 20))
        for provider in Provider.allCases {
            let request = try RequestBuilder.transcribe(audio: wav, options: SpeechRequest(provider: provider, model: provider == .custom ? "my-model" : provider.defaultSpeech, baseURL: "https://custom.example/v1"), key: "test-key", boundary: "test-boundary")
            XCTAssertFalse(request.url!.absoluteString.contains("test-key"))
            XCTAssertEqual(request.httpMethod, "POST")
            if provider == .gemini {
                XCTAssertNil(request.value(forHTTPHeaderField: "Authorization")); XCTAssertEqual(request.value(forHTTPHeaderField: "x-goog-api-key"), "test-key")
                XCTAssertTrue(request.url!.absoluteString.contains(":generateContent"))
            } else { XCTAssertNil(request.value(forHTTPHeaderField: "x-goog-api-key")); XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer test-key") }
            if provider == .openrouter {
                let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String:Any]
                XCTAssertNotNil(body["input_audio"]); XCTAssertNil(body["messages"])
                XCTAssertNil(body["prompt"])
            }
        }
    }
    func testTranscriptionParsingAndErrorMapping() throws {
        XCTAssertEqual(try RequestBuilder.parseTranscript(Data("{\"text\":\"Hello.\"}".utf8), provider: .groq), "Hello.")
        XCTAssertThrowsError(try RequestBuilder.parseTranscript(Data("{}".utf8), provider: .openai))
        let data = Data("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"Transcript: Hello\"}]}}]}".utf8)
        XCTAssertEqual(try RequestBuilder.parseTranscript(data, provider: .gemini), "Hello")
        XCTAssertEqual(RequestBuilder.error(status: 400, data: Data("API_KEY_INVALID".utf8), provider: .gemini), .badKey)
        XCTAssertEqual(RequestBuilder.error(status: 429, data: Data(), provider: .gemini), .quota)
        XCTAssertEqual(RequestBuilder.error(status: 429, data: Data(), provider: .groq), .rateLimit)
    }
    func testCatalogFiltersWrongModalities() throws {
        let json = Data("{\"data\":[{\"id\":\"a\",\"architecture\":{\"output_modalities\":[\"text\"]}},{\"id\":\"b\",\"architecture\":{\"output_modalities\":[\"transcription\"]}}]}".utf8)
        XCTAssertEqual(try ModelCatalog.parse(data: json, provider: .openrouter, use: .dictation).models.map(\.id), ["b"])
        XCTAssertEqual(try ModelCatalog.parse(data: json, provider: .openrouter, use: .cleanup).models.map(\.id), ["a"])
    }
    func testCopyPreservesPositionalArgumentsAndUnknownKeys() throws {
        let copy = try CopyCatalog(data: Data("{\"schemaVersion\":1,\"strings\":{\"greeting\":\"%2$s: %1$d%%\"}}".utf8))
        XCTAssertEqual(copy("greeting", "25", "Done"), "Done: 25%")
        XCTAssertEqual(copy("unknown"), "unknown")
    }
    func testPerAppStyleOverrideOrder() {
        var s = QuillSettings(); XCTAssertEqual(s.style(for: "com.apple.mail"), .formal)
        s.styleOverrides["com.apple.mail"] = .casual; XCTAssertEqual(s.style(for: "com.apple.mail"), .casual)
        s.writingStyle = .formal; XCTAssertEqual(s.style(for: "unknown"), .formal)
        XCTAssertEqual(s.style(for: "net.whatsapp.WhatsApp"), .neutral)
        XCTAssertEqual(s.style(for: "com.tinyspeck.slackmacgap"), .formal)
        XCTAssertEqual(s.style(for: "com.hnc.Discord"), .casual)
        XCTAssertEqual(s.style(for: "com.apple.Safari"), .neutral)
    }
    func testCopyFormatterCompatibleWidthPrecisionImplicitAndRepeated() {
        XCTAssertEqual(CopyCatalog.format("%1$05d items, %2$.3s, %1$d%% %n%s", arguments: ["12", "Quill"]), "00012 items, Qui, 12% \n12")
        XCTAssertEqual(CopyCatalog.format("%-6s! %.2f %+,d", arguments: ["Hi", "1.256", "12345"]), "Hi    ! 1.26 +12,345")
        XCTAssertEqual(CopyCatalog.format("%1$S %2$04d", arguments: ["quill", "-2"]), "QUILL -002")
        XCTAssertEqual(CopyCatalog.format("50% off", arguments: []), "50% off")
    }
    func testAndroidCleanupGoldenCorpus() throws {
        struct Corpus: Decodable { struct Row: Decodable { let raw: String; let cleaned: String; let accepted: Bool }; let cases: [Row] }
        let url = Bundle.module.url(forResource: "cleanup", withExtension: "json")!
        let corpus = try JSONDecoder().decode(Corpus.self, from: Data(contentsOf: url))
        XCTAssertGreaterThan(corpus.cases.count, 190)
        for row in corpus.cases { XCTAssertEqual(CleanupGuard.preservesMeaning(raw: row.raw, cleaned: row.cleaned), row.accepted, "\(row.raw) → \(row.cleaned)") }
    }
    func testContactsVocabularyStaysLocalAndRejectsNonNames() {
        XCTAssertEqual(ContactVocabulary.extract(["Anay Tihor", "anay tihor", "alice@example.com", "+91 12345", "Anne-Marie O'Brien", "😀 Friend", "Dr. Anay"]), ["Anay Tihor", "Anay", "Tihor", "Anne-Marie O'Brien", "Anne-Marie", "O'Brien", "Dr. Anay", "Dr."])
    }
}
