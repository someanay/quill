import AVFoundation
import Combine
import UIKit
import QuillCore

@MainActor final class KeyboardSessionController: ObservableObject {
    @Published private(set) var state: KeyboardRecordingSession?
    @Published private(set) var ready = false
    @Published private(set) var lastTransferID: UUID?
    private weak var model: AppModel?
    private var microphone: KeyboardMicrophoneSession?
    private var timer: Timer?
    private var observers: [NSObjectProtocol] = []
    private var handled: Set<UUID> = []
    private var ownsCapture = false
    private var delivering = false
    private var starting = false
    private var deadlineUptime: TimeInterval = 0
    private var store: CoordinatedKeyboardSessionStore? { KeyboardSessionBridge.store }
    var isActive: Bool { state?.isLive() == true }
    var remainingSeconds: Int { max(0, Int(state?.expiresAt.timeIntervalSinceNow ?? 0)) }
    func attach(_ model: AppModel) {
        self.model = model
        // A process restart never restores a microphone lease or replays an old command.
        if var stale = store?.state() { stale.phase = .closed; stale.updatedAt = Date(); try? store?.publish(stale) }
        store?.clearCommand()
        for name in [AVAudioSession.interruptionNotification, AVAudioSession.routeChangeNotification, UIApplication.protectedDataWillBecomeUnavailableNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] note in
                MainActor.assumeIsolated {
                    if note.name == AVAudioSession.interruptionNotification,
                       note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt != AVAudioSession.InterruptionType.began.rawValue { return }
                    if note.name == AVAudioSession.routeChangeNotification,
                       note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt != AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { return }
                    self?.interrupt()
                }
            })
        }
    }
    deinit { timer?.invalidate(); for observer in observers { NotificationCenter.default.removeObserver(observer) } }
    func enable(duration: TimeInterval = 300) async {
        guard let model, !isActive, !model.busy, !model.canRetry, !model.cleanupRequired,
              UIApplication.shared.applicationState == .active, UIApplication.shared.isProtectedDataAvailable else { return }
        if !model.microphoneGranted { await model.requestMicrophone() }
        guard !isActive, !model.busy, !model.canRetry, !model.cleanupRequired else { return }
        guard model.microphoneGranted, model.hasActiveKey, !model.busy, !model.canRetry else {
            model.errorKey = model.hasActiveKey ? "ios.mic.blocked" : QuillError.noKey.copyKey; return
        }
        model.stopMicTest()
        if microphone != nil { end() }
        let microphone = KeyboardMicrophoneSession()
        do {
            try microphone.start()
            guard let store else { throw QuillError.storage }
            let state = KeyboardRecordingSession(duration: duration)
            try store.publish(state); store.clearCommand()
            self.microphone = microphone; self.state = state; ready = false; handled = []; ownsCapture = false
            deadlineUptime = ProcessInfo.processInfo.systemUptime + state.expiresAt.timeIntervalSinceNow
            model.recorder.keepsAudioSessionActive = true
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.tick() }
            }
        } catch { microphone.stop(); model.report(error) }
    }
    func end(cancelCapture: Bool = false) {
        guard let model else { return }
        timer?.invalidate(); timer = nil
        if ownsCapture {
            if cancelCapture { model.cancel(); ownsCapture = false }
            else if model.phase == .recording { model.stopAndTranscribe() }
        }
        model.recorder.keepsAudioSessionActive = false
        microphone?.stop(); microphone = nil; ready = false
        if var state { state.phase = .closed; state.updatedAt = Date(); self.state = state; try? store?.publish(state) }
        store?.clearCommand()
        // A deliberate end may still finish the take under the normal bounded background task.
        // Its result remains available for explicit insertion even after the mic lease closes.
        if ownsCapture && model.busy {
            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in Task { @MainActor in self?.finishAfterEnd() } }
        } else { ownsCapture = false }
    }
    private func finishAfterEnd() {
        guard let model, !model.busy else { return }
        if model.phase == .ready && !model.result.isEmpty { _ = model.sendToKeyboard(model.result) }
        ownsCapture = false; timer?.invalidate(); timer = nil
    }
    private func interrupt() {
        guard microphone != nil else { return }
        if ownsCapture, model?.phase == .recording { model?.interruptKeyboardRecording() }
        end()
    }
    private func tick() {
        guard var state, state.phase != .closed, let model, let microphone else { return }
        guard Date() < state.expiresAt, ProcessInfo.processInfo.systemUptime < deadlineUptime else { end(); return }
        guard microphone.healthy, AudioRecorder.permission == .granted, UIApplication.shared.isProtectedDataAvailable else { interrupt(); return }
        ready = microphone.hasReceivedInput
        state.updatedAt = Date()
        if ownsCapture && !starting && !delivering {
            switch model.phase {
            case .recording: state.phase = .recording
            case .transcribing, .cleaning: state.phase = .processing
            case .ready:
                if state.transferID == nil && !model.result.isEmpty {
                    delivering = true
                    state.transferID = model.sendToKeyboard(model.result)
                    lastTransferID = state.transferID
                    delivering = false
                    if state.transferID == nil { state.phase = .failed; state.errorKey = QuillError.storage.copyKey }
                    else { state.phase = .ready }
                }
            case .failed:
                state.errorKey = model.errorKey ?? QuillError.audio.copyKey
                if model.errorKey == QuillError.noSpeech.copyKey && !model.canRetry && !model.cleanupRequired {
                    // Silence is terminal, not retryable. Keep the microphone lease ready for
                    // the next explicit take without an app switch or a meaningless Retry.
                    ownsCapture = false; state.captureID = nil; state.transferID = nil; state.phase = .ready
                } else { state.phase = .failed }
            case .idle: ownsCapture = false; state.captureID = nil; state.phase = .ready
            }
        }
        if let transfer = state.transferID, KeyboardTransfer.peek()?.id != transfer {
            state.transferID = nil; state.captureID = nil; ownsCapture = false
        }
        self.state = state
        do {
            try store?.publish(state)
            if let command = try store?.takeCommand(), !handled.contains(command.id), command.isValid(for: state) {
                handled.insert(command.id)
                if handled.count > 128 { handled = [command.id] }
                state.acknowledgedCommandID = command.id; self.state = state
                handle(command)
            }
        } catch { model.report(error); end() }
    }
    private func handle(_ command: KeyboardSessionCommand) {
        guard let model, var state else { return }
        switch command.kind {
        case .start:
            guard ready, !model.busy, !model.canRetry, !model.cleanupRequired else { return }
            ownsCapture = true; starting = true; state.captureID = command.id; state.transferID = nil; state.errorKey = nil; state.phase = .recording
            self.state = state; try? store?.publish(state)
            let sessionID = state.id
            lastTransferID = nil
            Task {
                guard self.state?.id == sessionID, self.state?.phase != .closed else { starting = false; ownsCapture = false; return }
                await model.start(capSeconds: max(1, min(model.settings.sessionCapSeconds, remainingSeconds)))
                starting = false
                if model.phase != .recording { self.state?.phase = .failed; self.state?.errorKey = model.errorKey ?? QuillError.audio.copyKey }
            }
        case .stop: model.stopAndTranscribe()
        case .retry:
            if model.cleanupRequired { model.retryRecordingCleanup() }
            else if model.canRetry { model.retry() }
            else if !model.result.isEmpty { state.transferID = model.sendToKeyboard(model.result); state.phase = state.transferID == nil ? .failed : .ready; self.state = state }
        case .discard: model.cancel(); ownsCapture = false; state.captureID = nil; state.transferID = nil; state.phase = .ready; self.state = state
        case .end: end()
        }
    }
}
