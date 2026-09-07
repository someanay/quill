import XCTest
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
    }
}
