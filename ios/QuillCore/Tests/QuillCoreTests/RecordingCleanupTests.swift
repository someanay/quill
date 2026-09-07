import XCTest
@testable import QuillCore

final class RecordingCleanupTests: XCTestCase {
    private enum Failure: Error { case denied }
    func testTerminalMarkerPrecedesAllDeletion() {
        var calls: [String] = []
        let result = RecordingCleanup.finish(markTerminal: { calls.append("terminal") },
            removeAudio: { calls.append("audio") }, removeManifest: { calls.append("manifest") })
        XCTAssertEqual(calls, ["terminal", "audio", "manifest"])
        XCTAssertTrue(result.terminalCommitted); XCTAssertFalse(result.cleanupFailed)
    }
    func testFailedAudioDeletionRetainsTerminalMarker() {
        var removedManifest = false
        let result = RecordingCleanup.finish(markTerminal: {}, removeAudio: { throw Failure.denied },
            removeManifest: { removedManifest = true })
        XCTAssertTrue(result.terminalCommitted); XCTAssertFalse(result.audioRemoved)
        XCTAssertTrue(result.cleanupFailed); XCTAssertFalse(removedManifest)
    }
    func testFailedMarkerStillAllowsSuccessfulDeletion() {
        let result = RecordingCleanup.finish(markTerminal: { throw Failure.denied }, removeAudio: {}, removeManifest: {})
        XCTAssertTrue(result.terminalCommitted); XCTAssertFalse(result.cleanupFailed)
    }
    func testAllStorageFailuresCannotCommitCompletion() {
        let result = RecordingCleanup.finish(markTerminal: { throw Failure.denied },
            removeAudio: { throw Failure.denied }, removeManifest: { throw Failure.denied })
        XCTAssertFalse(result.terminalCommitted); XCTAssertFalse(result.audioRemoved)
        XCTAssertTrue(result.cleanupFailed)
    }
    func testManifestRemovalInvalidatesTakeEvenIfMarkerAndAudioFail() {
        let result = RecordingCleanup.finish(markTerminal: { throw Failure.denied },
            removeAudio: { throw Failure.denied }, removeManifest: {})
        XCTAssertTrue(result.terminalCommitted); XCTAssertFalse(result.audioRemoved)
        XCTAssertTrue(result.manifestRemoved); XCTAssertTrue(result.cleanupFailed)
    }
    func testRemovedAudioCannotResurrectDespiteFailedMarkerAndManifest() {
        let result = RecordingCleanup.finish(markTerminal: { throw Failure.denied },
            removeAudio: {}, removeManifest: { throw Failure.denied })
        XCTAssertTrue(result.terminalCommitted); XCTAssertTrue(result.audioRemoved)
        XCTAssertTrue(result.cleanupFailed)
    }
}
