import AVFoundation
import Combine
import QuillCore

@MainActor final class AudioRecorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published private(set) var isRecording = false
    @Published private(set) var amplitude: Double = 0
    @Published private(set) var elapsed: Double = 0
    @Published private(set) var warning = false
    var onAutomaticStop: (() -> Void)?
    var onInterruption: (() -> Void)?
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var detector = SilenceDetector()
    private var startedAt: TimeInterval = 0
    private var cap: Double = 300
    private var testMode = false
    private var interruptionObserver: NSObjectProtocol?
    private var routeObserver: NSObjectProtocol?
    var fileURL: URL? { recorder?.url }
    static var permission: AVAudioApplication.recordPermission { AVAudioApplication.shared.recordPermission }
    static func requestPermission() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }
    override init() {
        super.init()
        interruptionObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            guard let value = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt, value == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in guard let self, self.isRecording else { return }; self.onInterruption?() }
        }
        routeObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            guard let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt, reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
            Task { @MainActor in guard let self, self.isRecording else { return }; self.onInterruption?() }
        }
    }
    deinit {
        timer?.invalidate()
        if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) }
        if let routeObserver { NotificationCenter.default.removeObserver(routeObserver) }
    }
    func start(capSeconds: Int, microphoneTest: Bool = false) throws {
        guard !isRecording, Self.permission == .granted else { throw QuillError.audio }
        try AppFiles.prepare()
        let session = AVAudioSession.sharedInstance()
        let path = AppFiles.audio.appendingPathComponent(UUID().uuidString + ".wav")
        var attemptedRecorder: AVAudioRecorder?
        do {
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker])
            try session.setPreferredSampleRate(16_000)
            try session.setActive(true)
            // Match Android handset-microphone capture; the hardware input can run at another rate,
            // but AVAudioRecorder converts the written PCM stream to provider-compatible 16 kHz mono.
            if let builtIn = session.availableInputs?.first(where: { $0.portType == .builtInMic }) { try session.setPreferredInput(builtIn) }
            let audio = try AVAudioRecorder(url: path, settings: [AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 16_000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false])
            attemptedRecorder = audio
            audio.delegate = self; audio.isMeteringEnabled = true
            guard audio.prepareToRecord(), audio.record() else { throw QuillError.audio }
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUnlessOpen], ofItemAtPath: path.path)
            recorder = audio; detector = SilenceDetector(); elapsed = 0; amplitude = 0; warning = false
            cap = Double(capSeconds); testMode = microphoneTest; startedAt = ProcessInfo.processInfo.systemUptime; isRecording = true
            timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in Task { @MainActor in self?.tick() } }
        } catch {
            attemptedRecorder?.stop()
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            try? FileManager.default.removeItem(at: path)
            throw QuillError.audio
        }
    }
    private func tick() {
        guard isRecording, let recorder else { return }
        recorder.updateMeters()
        elapsed = ProcessInfo.processInfo.systemUptime - startedAt
        let normalized = pow(10, Double(recorder.averagePower(forChannel: 0)) / 20)
        amplitude = amplitude * 0.65 + min(1, normalized * 6) * 0.35
        warning = RecordingLimits.shouldWarn(elapsed: elapsed, cap: cap)
        if !recorder.isRecording { onInterruption?(); return }
        if RecordingLimits.reached(elapsed: elapsed, cap: cap) || (!testMode && detector.feed(rms: normalized * 32767, elapsed: elapsed)) {
            onAutomaticStop?()
        }
    }
    @discardableResult func stop() -> URL? {
        let url = recorder?.url
        timer?.invalidate(); timer = nil; recorder?.stop(); recorder = nil
        isRecording = false; amplitude = 0; warning = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return url
    }
    func cancel() { if let url = stop() { try? FileManager.default.removeItem(at: url) } }
    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        let failedFile = recorder.url
        Task { @MainActor [weak self] in
            guard let self, self.isRecording, self.fileURL == failedFile else { return }
            self.onInterruption?()
        }
    }
}
