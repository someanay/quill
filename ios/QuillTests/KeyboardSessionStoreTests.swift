import XCTest
import QuillCore
@testable import Quill

final class KeyboardSessionStoreTests: XCTestCase {
    private var directory: URL!
    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }
    override func tearDownWithError() throws { try? FileManager.default.removeItem(at: directory) }
    func testCommandsAreProtectedAtomicAndConsumedOnce() throws {
        var writes: [Data.WritingOptions] = []
        let store = CoordinatedKeyboardSessionStore(directory: directory!) { data, url, options in
            writes.append(options); try data.write(to: url, options: options)
        }
        let state = KeyboardRecordingSession(); try store.publish(state)
        let command = KeyboardSessionCommand(sessionID: state.id, kind: .start)
        try store.send(command)
        XCTAssertThrowsError(try store.send(KeyboardSessionCommand(sessionID: state.id, kind: .start)))
        XCTAssertEqual(try store.takeCommand(), command); XCTAssertNil(try store.takeCommand())
        XCTAssertEqual(writes.count, 2)
        XCTAssertTrue(writes.allSatisfy { $0.contains(.atomic) && $0.contains(.completeFileProtection) })
        #if !targetEnvironment(simulator)
        for name in ["state.json"] {
            let attributes = try FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent(name).path)
            XCTAssertEqual(attributes[.protectionKey] as? FileProtectionType, .complete)
        }
        #endif
        XCTAssertEqual(try directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }
    func testRestartClosedAndExpiredLeasesRejectOldKeyboardCommands() throws {
        let store = CoordinatedKeyboardSessionStore(directory: directory!)
        let old = KeyboardRecordingSession(); try store.publish(old)
        try store.publish(KeyboardRecordingSession())
        XCTAssertThrowsError(try store.send(KeyboardSessionCommand(sessionID: old.id, kind: .start)))
        var closed = KeyboardRecordingSession(); closed.phase = .closed; try store.publish(closed)
        XCTAssertThrowsError(try store.send(KeyboardSessionCommand(sessionID: closed.id, kind: .start)))
        let expired = KeyboardRecordingSession(now: Date().addingTimeInterval(-30), duration: 1); try store.publish(expired)
        XCTAssertThrowsError(try store.send(KeyboardSessionCommand(sessionID: expired.id, kind: .start)))
        XCTAssertNil(try store.takeCommand())
    }
    func testMalformedAndOversizedStateCannotEnableTheKeyboard() throws {
        let store = CoordinatedKeyboardSessionStore(directory: directory!)
        try store.publish(KeyboardRecordingSession())
        let target = directory.appendingPathComponent("state.json")
        try Data("{}".utf8).write(to: target); XCTAssertNil(store.state())
        try Data(repeating: 65, count: 8192).write(to: target); XCTAssertNil(store.state())
    }
}
