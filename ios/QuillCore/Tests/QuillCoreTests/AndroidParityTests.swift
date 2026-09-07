import XCTest
@testable import QuillCore

final class AndroidParityTests: XCTestCase {
    func testEveryAndroidCleanupRegressionAndContraction() throws {
        struct Case: Decodable { let raw: String; let cleaned: String; let accepted: Bool }
        struct Fixture: Decodable { let sourceSha256: String; let cases: [Case] }
        let url = try XCTUnwrap(Bundle.module.url(forResource: "cleanup", withExtension: "json", subdirectory: "Fixtures"))
        let fixture = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: url))
        XCTAssertEqual(fixture.cases.count, 195)
        for item in fixture.cases { XCTAssertEqual(CleanupGuard.preserves(item.raw, item.cleaned), item.accepted, "\(item.raw) -> \(item.cleaned)") }
    }
    func testAndroidPlaceholderSignatures() throws {
        struct Vector: Decodable { let input: String; let tokens: [String] }
        let url = try XCTUnwrap(Bundle.module.url(forResource: "copy-placeholders", withExtension: "json", subdirectory: "Fixtures"))
        let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: url))
        for vector in vectors { XCTAssertEqual(CopyCatalog.placeholderSignature(vector.input), vector.tokens, vector.input) }
    }
    func testFormatterAcceptsWidthPrecisionAndImplicitArguments() {
        let c = CopyCatalog(strings: ["a": "%2$08.2f %1$5s", "b": "%s = %d%% done%n", "c": "%1$s %1$s", "d": "%03d", "e": "%1$.3s", "f": "%1$s %<s"])
        XCTAssertEqual(c.text("a", "Hi", "1.25"), "00001.25    Hi")
        XCTAssertEqual(c.text("b", "Work", "50"), "Work = 50% done\n")
        XCTAssertEqual(c.text("c", "$1%n"), "$1%n $1%n")
        XCTAssertEqual(c.text("d", "7"), "007")
        XCTAssertEqual(c.text("e", "Hello"), "Hel")
        XCTAssertEqual(c.text("f", "A"), "A A")
    }
}
