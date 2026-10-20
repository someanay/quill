import XCTest
import Foundation
import AVFoundation
import QuillCore
@testable import Quill

private actor DelayedSessionProvider: HTTPTransport {
    private(set) var started = false
    func send(_ request: URLRequest) async throws -> HTTPResult {
        started = true
        // Deliberately finish after cancellation to exercise the delivery generation guard.
        return try await Task.detached {
            try await Task.sleep(nanoseconds: 1_000_000_000)
            return HTTPResult(data: Data(#"{"text":"This cancelled take must never be delivered."}"#.utf8), status: 200)
        }.value
    }
}

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
    func testLiveInputSessionDiscardsIdleAudioAndSupportsTwoRealTakes() async throws {
        try AppFiles.prepare()
        let before = Set(try FileManager.default.contentsOfDirectory(atPath: AppFiles.audio.path))
        let session = KeyboardMicrophoneSession()
        try session.start()
        let recorder = AudioRecorder(); recorder.keepsAudioSessionActive = true
        defer { recorder.cancel(); session.stop() }
        try await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertTrue(session.isRunning); XCTAssertTrue(session.hasReceivedInput)
        XCTAssertEqual(Set(try FileManager.default.contentsOfDirectory(atPath: AppFiles.audio.path)), before, "Idle input must never create an audio file.")
        for _ in 0..<2 {
            try recorder.start(capSeconds: 30, microphoneTest: true)
            try await Task.sleep(nanoseconds: 400_000_000)
            let file = try XCTUnwrap(recorder.stop())
            let audio = try AVAudioFile(forReading: file)
            XCTAssertGreaterThan(audio.length, 0); XCTAssertEqual(audio.fileFormat.sampleRate, 16_000)
            try FileManager.default.removeItem(at: file)
            XCTAssertTrue(session.healthy, "Finishing one take must preserve the live input session.")
        }
        session.stop(); XCTAssertFalse(session.isRunning)
    }
    func testRealSessionExpiresAndAnInterruptionDoesNotRestartIt() async throws {
        let model = AppModel()
        model.cancel(); model.settings = QuillSettings(); model.settings.selectProvider(.groq)
        try model.vault.save("session-fixture-" + UUID().uuidString, provider: .groq, endpoint: Provider.groq.baseURL, label: "Session recorder test")
        let key = try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL))
        defer { model.keyboardSession.end(cancelCapture: true); try? model.vault.delete(key) }
        await model.keyboardSession.enable(duration: 1)
        XCTAssertTrue(model.keyboardSession.isActive)
        for _ in 0..<30 {
            if model.keyboardSession.state?.phase == .closed { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertEqual(model.keyboardSession.state?.phase, .closed)
        XCTAssertFalse(model.recorder.keepsAudioSessionActive)
        await model.keyboardSession.enable(duration: 30)
        XCTAssertTrue(model.keyboardSession.isActive)
        NotificationCenter.default.post(name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance(), userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        try await Task.sleep(nanoseconds: 400_000_000)
        XCTAssertEqual(model.keyboardSession.state?.phase, .closed)
        XCTAssertFalse(model.recorder.keepsAudioSessionActive)
    }
    func testClearingKeyboardTextCancelsAnInFlightRealTakeWithoutLateDelivery() async throws {
        let provider = DelayedSessionProvider()
        let model = AppModel(providerAPI: ProviderAPI(transport: provider))
        model.cancel(); model.settings = QuillSettings(); model.settings.selectProvider(.groq); model.settings.cleanupEnabled = false
        try model.vault.save("session-clear-" + UUID().uuidString, provider: .groq, endpoint: Provider.groq.baseURL, label: "Session cancellation test")
        let key = try XCTUnwrap(model.vault.active(.groq, endpoint: Provider.groq.baseURL))
        defer { model.keyboardSession.end(cancelCapture: true); try? model.vault.delete(key); try? KeyboardTransfer.clear() }
        try KeyboardTransfer.clear()
        await model.keyboardSession.enable(duration: 30)
        for _ in 0..<30 {
            if model.keyboardSession.ready { break }; try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(model.keyboardSession.ready)
        let session = try XCTUnwrap(model.keyboardSession.state)
        try KeyboardSessionBridge.send(KeyboardSessionCommand(sessionID: session.id, kind: .start))
        for _ in 0..<30 {
            if model.phase == .recording { break }; try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertEqual(model.phase, .recording)
        try await Task.sleep(nanoseconds: 250_000_000)
        model.stopAndTranscribe(); XCTAssertEqual(model.phase, .transcribing)
        for _ in 0..<30 {
            if await provider.started { break }; try await Task.sleep(nanoseconds: 10_000_000)
        }
        let reachedProvider = await provider.started
        XCTAssertTrue(reachedProvider, "The real recorded WAV must reach the delayed transport before clearing.")
        model.clearTransfer()
        try await Task.sleep(nanoseconds: 1_200_000_000)
        XCTAssertEqual(model.keyboardSession.state?.phase, .closed)
        XCTAssertFalse(model.recorder.isRecording); XCTAssertFalse(model.recorder.keepsAudioSessionActive)
        XCTAssertEqual(model.result, ""); XCTAssertNil(KeyboardTransfer.peek()); XCTAssertEqual(model.phase, .idle)
    }
}
