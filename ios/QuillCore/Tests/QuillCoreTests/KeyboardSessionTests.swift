import XCTest
@testable import QuillCore

final class KeyboardSessionTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 2_000_000)
    func testLeaseRequiresRecentHeartbeatAndUnexpiredExplicitSession() {
        var session = KeyboardRecordingSession(now: now, duration: 300)
        XCTAssertTrue(session.isLive(now: now))
        XCTAssertFalse(session.isLive(now: now.addingTimeInterval(4)))
        session.updatedAt = now.addingTimeInterval(299)
        XCTAssertTrue(session.isLive(now: now.addingTimeInterval(299)))
        XCTAssertFalse(session.isLive(now: now.addingTimeInterval(301)))
        XCTAssertFalse(session.isLive(now: now.addingTimeInterval(-10)))
        session.phase = .closed
        XCTAssertFalse(session.isLive(now: now.addingTimeInterval(299)))
    }
    func testCommandsAreBoundToSessionCaptureAndShortLifetime() {
        var session = KeyboardRecordingSession(now: now, duration: 300)
        let start = KeyboardSessionCommand(sessionID: session.id, kind: .start, now: now)
        XCTAssertTrue(start.isValid(for: session, now: now))
        XCTAssertFalse(start.isValid(for: session, now: now.addingTimeInterval(11)))
        XCTAssertFalse(KeyboardSessionCommand(sessionID: UUID(), kind: .start, now: now).isValid(for: session, now: now))
        session.phase = .recording; session.captureID = start.id
        XCTAssertFalse(start.isValid(for: session, now: now))
        XCTAssertFalse(KeyboardSessionCommand(sessionID: session.id, kind: .stop, captureID: UUID(), now: now).isValid(for: session, now: now))
        XCTAssertTrue(KeyboardSessionCommand(sessionID: session.id, kind: .stop, captureID: start.id, now: now).isValid(for: session, now: now))
        session.phase = .processing
        XCTAssertFalse(KeyboardSessionCommand(sessionID: session.id, kind: .start, now: now).isValid(for: session, now: now))
        XCTAssertTrue(KeyboardSessionCommand(sessionID: session.id, kind: .end, now: now).isValid(for: session, now: now))
    }
    func testAutomaticInsertionRequiresTheSameUneditedDocument() {
        let document = UUID()
        let anchor = KeyboardInsertionAnchor(documentID: document, before: "Hello", after: "!", selection: "")
        XCTAssertTrue(anchor.matches(documentID: document, before: "Hello", after: "!", selection: ""))
        XCTAssertFalse(anchor.matches(documentID: UUID(), before: "Hello", after: "!", selection: ""))
        XCTAssertFalse(anchor.matches(documentID: document, before: "Edited", after: "!", selection: ""))
        XCTAssertFalse(anchor.matches(documentID: document, before: "Hello", after: "!", selection: "selected"))
        XCTAssertFalse(KeyboardInsertionAnchor(documentID: document, before: nil, after: nil, selection: nil).matches(documentID: document, before: nil, after: nil, selection: nil))
    }
    func testSharedProtocolContainsNoAudioCredentialsOrDestinationText() throws {
        let session = KeyboardRecordingSession(now: now, duration: 300)
        let command = KeyboardSessionCommand(sessionID: session.id, kind: .start, now: now)
        let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(command)) as! [String: Any]
        XCTAssertEqual(Set(encoded.keys), ["id", "sessionID", "kind", "createdAt"])
        XCTAssertLessThan(try JSONEncoder().encode(session).count, 1024)
    }
    func testSessionDurationIsAlwaysBoundedAndFailureRequiresExplicitRecovery() {
        XCTAssertEqual(KeyboardRecordingSession(now: now, duration: 100_000).expiresAt.timeIntervalSince(now), 900)
        var session = KeyboardRecordingSession(now: now, duration: 300)
        session.phase = .failed; session.captureID = UUID()
        XCTAssertFalse(KeyboardSessionCommand(sessionID: session.id, kind: .start, now: now).isValid(for: session, now: now))
        XCTAssertTrue(KeyboardSessionCommand(sessionID: session.id, kind: .retry, captureID: session.captureID, now: now).isValid(for: session, now: now))
        XCTAssertTrue(KeyboardSessionCommand(sessionID: session.id, kind: .discard, captureID: session.captureID, now: now).isValid(for: session, now: now))
    }
}
