import AVFoundation
import UIKit
import QuillCore

/// Real, visibly disclosed microphone input, enabled in the foreground for a bounded session.
/// Idle input buffers are discarded in the audio callback. No idle file, upload or playback.
@MainActor final class KeyboardMicrophoneSession {
    private let engine = AVAudioEngine()
    private let activity = InputActivity()
    private var hasTap = false
    private(set) var startedAt: TimeInterval = 0
    var isRunning: Bool { engine.isRunning }
    var hasReceivedInput: Bool { activity.count > 0 }
    var healthy: Bool { engine.isRunning && (activity.recent || ProcessInfo.processInfo.systemUptime - startedAt < 2) }
    func start() throws {
        guard UIApplication.shared.applicationState == .active, AudioRecorder.permission == .granted else { throw QuillError.audio }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .mixWithOthers])
        try session.setPreferredSampleRate(16_000)
        try session.setActive(true)
        if let builtIn = session.availableInputs?.first(where: { $0.portType == .builtInMic }) { try session.setPreferredInput(builtIn) }
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { throw QuillError.audio }
        let activity = activity
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { _, _ in activity.received() }
        hasTap = true
        do { engine.prepare(); try engine.start(); startedAt = ProcessInfo.processInfo.systemUptime }
        catch { input.removeTap(onBus: 0); hasTap = false; throw error }
    }
    func stop() {
        if engine.isRunning { engine.stop() }
        if hasTap { engine.inputNode.removeTap(onBus: 0); hasTap = false }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

private final class InputActivity: @unchecked Sendable {
    private let lock = NSLock()
    private var last: TimeInterval = 0
    private var buffers = 0
    var count: Int { lock.lock(); defer { lock.unlock() }; return buffers }
    var recent: Bool { lock.lock(); defer { lock.unlock() }; return ProcessInfo.processInfo.systemUptime - last < 3 }
    func received() { lock.lock(); buffers += 1; last = ProcessInfo.processInfo.systemUptime; lock.unlock() }
}
