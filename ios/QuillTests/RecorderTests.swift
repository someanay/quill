import XCTest
import AVFoundation
@testable import Quill

/// Run after `simctl privacy <device> grant microphone com.tihor.quill.ios` on an installed host.
/// This exercises the real native recorder; it sends no audio or credentials over the network.
@MainActor final class RecorderTests: XCTestCase {
    private func makeRecorder() throws -> AudioRecorder {
        XCTAssertEqual(AudioRecorder.permission, .granted, "Grant the simulator host's microphone permission before running RecorderTests.")
        let recorder = AudioRecorder()
        try recorder.start(capSeconds: 30, microphoneTest: true)
        return recorder
    }
    func testRealCaptureProducesFinalized16kMonoPCM() async throws {
        let recorder = try makeRecorder()
        defer { recorder.cancel() }
        try await Task.sleep(nanoseconds: 700_000_000)
        XCTAssertTrue(recorder.isRecording); XCTAssertGreaterThan(recorder.elapsed, 0)
        let file = try XCTUnwrap(recorder.stop())
        defer { try? FileManager.default.removeItem(at: file) }
        XCTAssertFalse(recorder.isRecording)
        let audio = try AVAudioFile(forReading: file)
        XCTAssertEqual(audio.fileFormat.sampleRate, 16_000)
        XCTAssertEqual(audio.fileFormat.channelCount, 1)
        XCTAssertEqual(audio.fileFormat.commonFormat, .pcmFormatInt16)
        XCTAssertGreaterThan(audio.length, 0)
        XCTAssertEqual(String(data: try Data(contentsOf: file).prefix(4), encoding: .ascii), "RIFF")
    }
    func testRealCaptureCancelDeletesPartialRecording() async throws {
        let recorder = try makeRecorder()
        let file = try XCTUnwrap(recorder.fileURL)
        try await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
        recorder.cancel()
        XCTAssertFalse(recorder.isRecording); XCTAssertNil(recorder.fileURL)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }
    func testActualRecorderSessionCapStopsThroughAdapter() async throws {
        XCTAssertEqual(AudioRecorder.permission, .granted)
        let recorder = AudioRecorder()
        var automaticFile: URL?
        recorder.onAutomaticStop = { automaticFile = recorder.stop() }
        try recorder.start(capSeconds: 1, microphoneTest: true)
        defer { recorder.cancel(); if let automaticFile { try? FileManager.default.removeItem(at: automaticFile) } }
        for _ in 0..<30 { if automaticFile != nil { break }; try await Task.sleep(nanoseconds: 100_000_000) }
        XCTAssertNotNil(automaticFile); XCTAssertFalse(recorder.isRecording)
        XCTAssertGreaterThanOrEqual(recorder.elapsed, 1)
    }
    func testInterruptionNotificationRetainsStoppedAudio() async throws {
        let recorder = try makeRecorder()
        var interruptedFile: URL?
        recorder.onInterruption = { interruptedFile = recorder.stop() }
        try await Task.sleep(nanoseconds: 150_000_000)
        NotificationCenter.default.post(name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance(), userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        for _ in 0..<20 { if interruptedFile != nil { break }; try await Task.sleep(nanoseconds: 20_000_000) }
        let file = try XCTUnwrap(interruptedFile)
        defer { recorder.cancel(); try? FileManager.default.removeItem(at: file) }
        XCTAssertFalse(recorder.isRecording); XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
    }
    func testOldInterruptionCannotStopImmediateNewRecording() async throws {
        let recorder = try makeRecorder()
        var interruptedFiles: [URL] = []
        recorder.onInterruption = { if let file = recorder.stop() { interruptedFiles.append(file) } }
        defer { recorder.cancel(); for file in interruptedFiles { try? FileManager.default.removeItem(at: file) } }
        NotificationCenter.default.post(name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance(), userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        // Restart synchronously before the main actor can run any queued notification task.
        if let old = recorder.stop() { interruptedFiles.append(old) }
        try recorder.start(capSeconds: 30, microphoneTest: true)
        let newFile = try XCTUnwrap(recorder.fileURL)
        try await Task.sleep(nanoseconds: 180_000_000)
        XCTAssertTrue(recorder.isRecording)
        XCTAssertEqual(recorder.fileURL, newFile)
        XCTAssertEqual(interruptedFiles.count, 1)
    }
}
