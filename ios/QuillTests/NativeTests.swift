import XCTest
import UIKit
@testable import Quill
import QuillCore

@MainActor final class NativeTests: XCTestCase {
    func testSQLiteRoundtripDeleteAndRetention() throws {
        let store = try HistoryStore()
        let entry = HistoryEntry(text: "I'm testing SQL 'quotes'; DROP TABLE history;", provider: .groq)
        try store.save(entry)
        XCTAssertTrue(try store.entries(retentionMinutes: 60).contains { $0.id == entry.id && $0.text == entry.text })
        try store.delete(entry.id)
        XCTAssertFalse(try store.entries(retentionMinutes: 60).contains { $0.id == entry.id })
        let old = HistoryEntry(text: "expired", provider: .openai, date: Date().addingTimeInterval(-3700))
        try store.save(old)
        XCTAssertFalse(try store.entries(retentionMinutes: 60).contains { $0.id == old.id })
    }
    func testCatalogAndFontsBundled() throws {
        let bundle = Bundle(for: AppModel.self)
        let url = bundle.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? bundle.url(forResource: "strings.default", withExtension: "json")
        let catalog = try CopyCatalog(data: Data(contentsOf: XCTUnwrap(url)))
        XCTAssertEqual(catalog.text("nav.home"), "Home")
        XCTAssertNotEqual(catalog.text("ios.keyboard.full_access_body"), "ios.keyboard.full_access_body")
        XCTAssertNotNil(bundle.url(forResource: "ibm_plex_sans", withExtension: "ttf"))
        XCTAssertNotNil(bundle.url(forResource: "ibm_plex_serif", withExtension: "ttf"))
        XCTAssertNotNil(UIFont(name: "IBMPlexSans", size: 16))
        XCTAssertNotNil(UIFont(name: "IBMPlexSerifVar-Regular", size: 24))
    }
    func testKeychainRoundtripAndExactEndpointIsolation() throws {
        let vault = KeyVault()
        let endpoint = "https://example.invalid/" + UUID().uuidString
        let secret = "test-only-" + UUID().uuidString
        try vault.save(secret, provider: .custom, endpoint: endpoint, label: "Test")
        let record = try XCTUnwrap(vault.active(.custom, endpoint: endpoint))
        defer { try? vault.delete(record) }
        XCTAssertEqual(try vault.read(record), secret)
        XCTAssertNil(vault.active(.groq, endpoint: endpoint))
        XCTAssertNil(vault.active(.custom, endpoint: endpoint + "/different"))
        let index = try String(contentsOf: AppFiles.root.appendingPathComponent("key-index.json"))
        XCTAssertFalse(index.contains(secret))
        try vault.delete(record)
        XCTAssertThrowsError(try vault.read(record))
    }
    func testPrivateRetrySurvivesAppRecreationAndCancelRemovesIt() throws {
        try AppFiles.prepare()
        var settings = QuillSettings(); settings.privateMode = true
        let name = UUID().uuidString + ".wav"
        let url = AppFiles.audio.appendingPathComponent(name)
        try Data("RIFF".utf8).write(to: url, options: [.atomic, .completeFileProtection])
        try AppFiles.write(PendingRecording(filename: name, settings: settings, words: [], voiceSample: false), name: "pending-recording.json")
        let model = AppModel()
        XCTAssertTrue(model.canRetry)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        model.cancel()
        XCTAssertFalse(model.canRetry)
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: AppFiles.root.appendingPathComponent("pending-recording.json").path))
    }
    func testPendingRecordingRejectsTraversal() {
        let pending = PendingRecording(filename: "../settings.json", settings: QuillSettings(), words: [], voiceSample: false)
        XCTAssertNil(pending.url)
    }
}
