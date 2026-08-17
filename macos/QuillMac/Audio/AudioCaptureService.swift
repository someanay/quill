import Foundation
import AVFoundation

// 16 kHz mono 16-bit PCM capture with silence + hard-session limits — the AVAudioEngine equivalent of
// the Windows NAudio AudioCaptureService. RMS thresholds and speech/auto-stop logic match the Windows
// port exactly. Audio is accumulated in memory (bounded by the ≤600 s cap ≈ 19 MB) and written as one
// WAV on stop.
struct AudioCaptureResult { let url: URL; let duration: TimeInterval; let autoStopped: Bool; let hasSpeech: Bool }

final class AudioCaptureService {
    private let paths: AppPaths
    private let engine = AVAudioEngine()
    private var converter: AVAudioConverter?
    private let lock = NSLock()
    private var pcm = Data()
    private var started = Date()
    private var silentMs = 0
    private var speechMs = 0
    private var autoStopped = false
    private var sessionCapSeconds = Int.max
    private var silenceAutoStopSeconds = 2
    private var recording = false

    /// Raised on the audio thread when silence or the session cap ends the recording; marshal to main.
    var onAutoStop: (() -> Void)?

    init(paths: AppPaths) { self.paths = paths }
    var isRecording: Bool { lock.lock(); defer { lock.unlock() }; return recording }

    private let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16_000, channels: 1, interleaved: true)!

    func start(_ settings: AppSettings) throws {
        lock.lock(); defer { lock.unlock() }
        if recording { throw NSError(domain: "Quill", code: 1, userInfo: [NSLocalizedDescriptionKey: "A recording is already active."]) }
        pcm.removeAll(keepingCapacity: true)
        started = Date(); silentMs = 0; speechMs = 0; autoStopped = false
        sessionCapSeconds = settings.sessionCapSeconds <= 0 ? Int.max : min(600, max(15, settings.sessionCapSeconds))
        silenceAutoStopSeconds = settings.silenceAutoStopSeconds

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        converter = AVAudioConverter(from: inputFormat, to: target)
        input.installTap(onBus: 0, bufferSize: 1600, format: inputFormat) { [weak self] buffer, _ in
            self?.process(buffer)
        }
        engine.prepare()
        try engine.start()
        recording = true
    }

    func stop() -> AudioCaptureResult {
        lock.lock()
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        recording = false
        let data = pcm
        let auto = autoStopped
        let speech = speechMs >= 200
        let duration = Date().timeIntervalSince(started)
        lock.unlock()

        let url = paths.recordings.appendingPathComponent("dictation-\(Int(started.timeIntervalSince1970)).wav")
        try? WavWriter.write(pcm: data, sampleRate: 16_000, channels: 1, bitsPerSample: 16, to: url)
        return AudioCaptureResult(url: url, duration: duration, autoStopped: auto, hasSpeech: speech)
    }

    private func process(_ buffer: AVAudioPCMBuffer) {
        guard let converter else { return }
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * target.sampleRate / buffer.format.sampleRate) + 64
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return }
        var consumed = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if consumed { status.pointee = .noDataNow; return nil }
            consumed = true; status.pointee = .haveData; return buffer
        }
        guard error == nil, out.frameLength > 0, let channel = out.int16ChannelData else { return }
        let count = Int(out.frameLength)

        var shouldStop = false
        lock.lock()
        channel[0].withMemoryRebound(to: UInt8.self, capacity: count * 2) { bytes in
            pcm.append(bytes, count: count * 2)
        }
        let rms = Self.rootMeanSquare(channel[0], count: count)
        // ~100 ms accounting per buffer window, matching the Windows 100 ms BufferMilliseconds cadence.
        let windowMs = max(1, Int(Double(count) / 16_000.0 * 1000.0))
        if rms >= 0.012 { speechMs += windowMs }
        if Date().timeIntervalSince(started) >= Double(sessionCapSeconds) { shouldStop = true }
        if silenceAutoStopSeconds > 0 {
            silentMs = rms < 0.012 ? silentMs + windowMs : 0
            if silentMs >= silenceAutoStopSeconds * 1000 { shouldStop = true }
        }
        if shouldStop { autoStopped = true }
        lock.unlock()

        if shouldStop { onAutoStop?() }
    }

    static func rootMeanSquare(_ samples: UnsafeMutablePointer<Int16>, count: Int) -> Double {
        if count == 0 { return 0 }
        var sum = 0.0
        for i in 0..<count { let s = Double(samples[i]) / 32768.0; sum += s * s }
        return (sum / Double(count)).squareRoot()
    }
}

enum WavWriter {
    static func write(pcm: Data, sampleRate: Int, channels: Int, bitsPerSample: Int, to url: URL) throws {
        let byteRate = sampleRate * channels * bitsPerSample / 8
        let blockAlign = channels * bitsPerSample / 8
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        d.append(Data("RIFF".utf8)); u32(UInt32(36 + pcm.count)); d.append(Data("WAVE".utf8))
        d.append(Data("fmt ".utf8)); u32(16); u16(1); u16(UInt16(channels)); u32(UInt32(sampleRate))
        u32(UInt32(byteRate)); u16(UInt16(blockAlign)); u16(UInt16(bitsPerSample))
        d.append(Data("data".utf8)); u32(UInt32(pcm.count)); d.append(pcm)
        try d.write(to: url)
    }
}
