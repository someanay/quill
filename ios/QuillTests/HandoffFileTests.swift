import XCTest
import Foundation
import QuillCore

final class HandoffFileTests: XCTestCase {
    private func isolatedStore() throws -> CoordinatedHandoffStore {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("handoff-test-" + UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: folder) }
        return CoordinatedHandoffStore(url: folder.appendingPathComponent("pending.json"))
    }
    func testCoordinatedFileRoundtripIsProtectedAndSingleUse() throws {
        let store = try isolatedStore()
        try store.publish("Don't change my words 👋🏽")
        let handoff = try XCTUnwrap(store.peek())
        let attributes = try FileManager.default.attributesOfItem(atPath: store.url.path)
        XCTAssertEqual(attributes[.protectionKey] as? FileProtectionType, .complete)
        XCTAssertEqual(try store.consume(handoff.id), "Don't change my words 👋🏽")
        XCTAssertNil(store.peek()); XCTAssertThrowsError(try store.consume(handoff.id))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.url.path))
    }
    func testStaleTapCannotConsumeNewerPublication() throws {
        let store = try isolatedStore()
        try store.publish("first")
        let first = try XCTUnwrap(store.peek())
        try store.publish("second")
        let second = try XCTUnwrap(store.peek())
        XCTAssertThrowsError(try store.consume(first.id))
        XCTAssertEqual(store.peek()?.id, second.id)
        XCTAssertEqual(try store.consume(second.id), "second")
    }
    func testExpiredAndMalformedFilesArePrunedWithoutInsertion() throws {
        let store = try isolatedStore()
        let expired = KeyboardHandoff(text: "expired", now: Date().addingTimeInterval(-61))
        try JSONEncoder().encode(expired).write(to: store.url, options: .atomic)
        XCTAssertNil(store.peek()); XCTAssertThrowsError(try store.consume(expired.id))
        store.prune(); XCTAssertFalse(FileManager.default.fileExists(atPath: store.url.path))
        try Data("broken JSON".utf8).write(to: store.url, options: .atomic)
        XCTAssertNil(store.peek()); store.prune()
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.url.path))
        try store.publish("valid"); store.prune()
        XCTAssertEqual(store.peek()?.text, "valid")
        try store.clear(); XCTAssertNil(store.peek()); try store.clear()
    }
}
