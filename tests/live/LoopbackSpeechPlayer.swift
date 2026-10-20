import AVFoundation
import Foundation

// Disposable Mac CI stimulus only. Keep the output graph running between takes
// so starting afplay cannot consume the app's real initial-silence timeout.
let arguments = CommandLine.arguments
guard arguments.count >= 2 else { fatalError("Expected synthetic speech WAV") }
let file = try AVAudioFile(forReading: URL(fileURLWithPath: arguments[1]))
guard file.length > 0, file.length < 1_600_000,
      let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)) else {
    fatalError("Invalid stimulus")
}
try file.read(into: buffer)
let engine = AVAudioEngine()
let player = AVAudioPlayerNode()
engine.attach(player)
engine.connect(player, to: engine.mainMixerNode, format: buffer.format)
engine.prepare()
try engine.start()
player.play()
Thread.sleep(forTimeInterval: 2)

func play() -> Double {
    let started = ProcessInfo.processInfo.systemUptime
    let finished = DispatchSemaphore(value: 0)
    player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { _ in finished.signal() }
    guard finished.wait(timeout: .now() + 25) == .success else { fatalError("Stimulus playback did not complete") }
    return ProcessInfo.processInfo.systemUptime - started
}
func reply(_ text: String) { FileHandle.standardOutput.write(Data((text + "\n").utf8)) }
if arguments.contains("--self-check") {
    let duration = Double(buffer.frameLength) / buffer.format.sampleRate
    let first = play(), second = play()
    guard abs(first - duration) < 2, abs(second - duration) < 2 else { fatalError("Playback differs from stimulus duration") }
    reply("PASS: persistent playback; sample=\(duration), first=\(first), second=\(second)")
} else {
    reply("ready")
    while let command = readLine() {
        guard command == "play" else { fatalError("Unknown stimulus command") }
        let elapsed = play()
        reply("done \(elapsed)")
    }
}
player.stop()
engine.stop()
