import Foundation
import AVFoundation
import QuillCore

final class AudioCapture: @unchecked Sendable {
    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var pcm = Data()
    private var detector = SilenceDetector()
    private var started = Date()
    private var running = false
    private var stoppedAutomatically = false
    var onLevel: (@Sendable (Double, Double, Bool) -> Void)?
    var onAutoStop: (@Sendable () -> Void)?
    static var permitted: Bool { AVCaptureDevice.authorizationStatus(for: .audio) == .authorized }
    static func requestPermission() async -> Bool { await AVCaptureDevice.requestAccess(for: .audio) }
    func start(cap: Int) throws {
        guard Self.permitted else { throw QuillError.noMicrophone }
        let input = engine.inputNode
        let source = input.outputFormat(forBus: 0)
        guard source.sampleRate > 0, source.channelCount > 0, let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true), let converter = AVAudioConverter(from: source, to: target) else { throw QuillError.noMicrophone }
        lock.lock(); pcm = Data(); detector = SilenceDetector(); started = Date(); running = true; stoppedAutomatically = false; lock.unlock()
        input.installTap(onBus: 0, bufferSize: 2048, format: source) { [weak self] buffer, _ in
            guard let self else { return }
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * 16000 / source.sampleRate + 32)
            guard let converted = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return }
            var supplied = false, error: NSError?
            let status = converter.convert(to: converted, error: &error) { _, flag in
                if supplied { flag.pointee = .noDataNow; return nil }; supplied = true; flag.pointee = .haveData; return buffer
            }
            guard status != .error, error == nil, let samples = converted.int16ChannelData?[0], converted.frameLength > 0 else { return }
            let count = Int(converted.frameLength)
            let sum = (0..<count).reduce(0.0) { $0 + pow(Double(samples[$1]), 2) }, rms = sqrt(sum / Double(count))
            self.lock.lock()
            guard self.running else { self.lock.unlock(); return }
            let remaining = max(0, cap * 16000 * 2 - self.pcm.count)
            self.pcm.append(UnsafeBufferPointer(start: UnsafeRawPointer(samples).assumingMemoryBound(to: UInt8.self), count: min(count * 2, remaining)))
            let elapsed = Date().timeIntervalSince(self.started)
            let stop = elapsed >= Double(cap) || remaining <= count * 2 || self.detector.consume(rms: rms, elapsed: elapsed)
            let shouldNotify = stop && !self.stoppedAutomatically
            if shouldNotify { self.stoppedAutomatically = true }
            self.lock.unlock()
            self.onLevel?(min(1, rms / 8000), elapsed, elapsed >= Double(cap - 15))
            if shouldNotify { self.onAutoStop?() }
        }
        do { engine.prepare(); try engine.start() }
        catch { input.removeTap(onBus: 0); lock.lock(); running = false; lock.unlock(); throw QuillError.noMicrophone }
    }
    func stop() -> Data {
        lock.lock(); let wasRunning = running; running = false; lock.unlock()
        if wasRunning { engine.stop(); engine.inputNode.removeTap(onBus: 0) }
        lock.lock(); let audio = WAV.encode(pcm: pcm); pcm.resetBytes(in: 0..<pcm.count); pcm.removeAll(); lock.unlock()
        return audio
    }
    func cancel() { _ = stop() }
}
