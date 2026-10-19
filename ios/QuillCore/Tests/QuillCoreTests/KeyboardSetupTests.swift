import XCTest
@testable import QuillCore

final class KeyboardSetupTests: XCTestCase {
    func testOnlyARecentExtensionConnectionConfirmsSetup() {
        let now = Date()
        XCTAssertTrue(KeyboardSetupReceipt(connectedAt: now).isRecent(since: now.addingTimeInterval(-1), now: now))
        XCTAssertFalse(KeyboardSetupReceipt(connectedAt: now.addingTimeInterval(-601)).isRecent(since: now.addingTimeInterval(-700), now: now))
        XCTAssertFalse(KeyboardSetupReceipt(connectedAt: now.addingTimeInterval(-2)).isRecent(since: now, now: now))
        XCTAssertFalse(KeyboardSetupReceipt(connectedAt: now.addingTimeInterval(10)).isRecent(since: now, now: now))
    }
    func testTypingOrAnOlderInsertionCannotPassPractice() {
        let id = UUID(), receipt = KeyboardSetupReceipt(insertedTransferID: UUID())
        XCTAssertFalse(receipt.confirmsPractice(transferID: id, transcript: "Hello 👋🏽", field: "Hello 👋🏽"))
        XCTAssertFalse(receipt.confirmsPractice(transferID: nil, transcript: "Hello", field: "Hello"))
        let inserted = KeyboardSetupReceipt(insertedTransferID: id)
        XCTAssertFalse(inserted.confirmsPractice(transferID: id, transcript: " ", field: " "))
        XCTAssertFalse(inserted.confirmsPractice(transferID: id, transcript: "Hello 👋🏽", field: "Hello"))
        XCTAssertTrue(inserted.confirmsPractice(transferID: id, transcript: "Hello 👋🏽", field: "Before Hello 👋🏽"))
    }
}
