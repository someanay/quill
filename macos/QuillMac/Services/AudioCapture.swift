import Foundation
import AVFoundation
import QuillCore

protocol AudioRecording: AnyObject {
    var onLevel: (@Sendable (UUID, Double, Double, Bool) -> Void)? { get set }
    var onAutoStop: (@Sendable (UUID) -> Void)? { get set }
    func start(cap: Int) throws -> UUID
    func stop() -> Data
    func cancel()
}

final class AudioCapture: AudioRecording, @unchecked Sendable {
    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var pcm = Data()
    private var detector = SilenceDetector()
    private var started = Date()
    private var running = false
    private var stoppedAutomatically = false
    private var captureID = UUID()
    var onLevel: (@Sendable (UUID, Double, Double, Bool) -> Void)?
    var onAutoStop: (@Sendable (UUID) -> Void)?
    static var permitted: Bool { AVCaptureDevice.authorizationStatus(for: .audio) == .authorized }
    static func requestPermission() async -> Bool { await AVCaptureDevice.requestAccess(for: .audio) }
    static func convert(_ buffer: AVAudioPCMBuffer, using converter: AVAudioConverter) -> AVAudioPCMBuffer? {
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * converter.outputFormat.sampleRate / buffer.format.sampleRate + 32)
        guard let converted = AVAudioPCMBuffer(pcmFormat: converter.outputFormat, frameCapacity: capacity) else { return nil }
        var supplied = false, error: NSError?
        let status = converter.convert(to: converted, error: &error) { _, flag in
            if supplied { flag.pointee = .noDataNow; return nil }; supplied = true; flag.pointee = .haveData; return buffer
        }
        guard status != .error, error == nil, converted.frameLength > 0 else { return nil }
        return converted
    }
    func start(cap: Int) throws -> UUID {
        guard Self.permitted else { throw QuillError.noMicrophone }
        let input = engine.inputNode
        let source = input.outputFormat(forBus: 0)
        guard source.sampleRate > 0, source.channelCount > 0, let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true), let converter = AVAudioConverter(from: source, to: target) else { throw QuillError.noMicrophone }
        let id = UUID()
        lock.lock(); captureID = id; pcm = Data(); detector = SilenceDetector(); started = Date(); running = true; stoppedAutomatically = false; lock.unlock()
        input.installTap(onBus: 0, bufferSize: 2048, format: source) { [weak self] buffer, _ in
            guard let self else { return }
            guard let converted = Self.convert(buffer, using: converter), let samples = converted.int16ChannelData?[0] else { return }
            let count = Int(converted.frameLength)
            let sum = (0..<count).reduce(0.0) { $0 + pow(Double(samples[$1]), 2) }, rms = sqrt(sum / Double(count))
            self.lock.lock()
            guard self.running && self.captureID == id else { self.lock.unlock(); return }
            let remaining = max(0, cap * 16000 * 2 - self.pcm.count)
            self.pcm.append(UnsafeBufferPointer(start: UnsafeRawPointer(samples).assumingMemoryBound(to: UInt8.self), count: min(count * 2, remaining)))
            let elapsed = Date().timeIntervalSince(self.started)
            let stop = elapsed >= Double(cap) || remaining <= count * 2 || self.detector.consume(rms: rms, elapsed: elapsed)
            let shouldNotify = stop && !self.stoppedAutomatically
            if shouldNotify { self.stoppedAutomatically = true }
            self.lock.unlock()
            self.onLevel?(id, min(1, rms / 8000), elapsed, elapsed >= Double(cap - 15))
            if shouldNotify { self.onAutoStop?(id) }
        }
        do { engine.prepare(); try engine.start() }
        catch { input.removeTap(onBus: 0); lock.lock(); running = false; lock.unlock(); throw QuillError.noMicrophone }
        return id
    }
    func stop() -> Data {
        lock.lock(); let wasRunning = running; running = false; captureID = UUID(); lock.unlock()
        if wasRunning { engine.stop(); engine.inputNode.removeTap(onBus: 0) }
        lock.lock(); let audio = WAV.encode(pcm: pcm); pcm.resetBytes(in: 0..<pcm.count); pcm.removeAll(); lock.unlock()
        return audio
    }
    func cancel() { _ = stop() }
}
