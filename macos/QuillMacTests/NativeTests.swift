import XCTest
import AVFoundation
import QuillCore
@testable import QuillMac

final class NativeTests: XCTestCase {
    private func isolatedDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("QuillNativeTests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return directory
    }
    func testBundledCopyHasOnboardingAndMacPermissionText() {
        XCTAssertNotEqual(Copy.text("onboarding.welcome.cta"), "onboarding.welcome.cta")
        XCTAssertNotEqual(Copy.text("mac.accessibility.disclosure"), "mac.accessibility.disclosure")
        XCTAssertTrue(Copy.catalog.strings.count > 300)
    }
    func testFontsArePackaged() {
        XCTAssertNotNil(Bundle.main.url(forResource: "ibm_plex_sans", withExtension: "ttf", subdirectory: "fonts"))
        XCTAssertNotNil(Bundle.main.url(forResource: "ibm_plex_serif", withExtension: "ttf", subdirectory: "fonts"))
    }
    func testNoRemoteCopyURL() { XCTAssertFalse(Copy.catalog.strings.keys.contains("copy.remote_url")) }
    func testNativeAudioConverterResamplesStereoToProviderPCM() throws {
        let inputFormat = try XCTUnwrap(AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 48000, channels: 2, interleaved: false))
        let outputFormat = try XCTUnwrap(AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true))
        let converter = try XCTUnwrap(AVAudioConverter(from: inputFormat, to: outputFormat))
        let input = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: inputFormat, frameCapacity: 48000)); input.frameLength = 48000
        let channels = try XCTUnwrap(input.floatChannelData)
        for index in 0..<48000 {
            let sample = Float(sin(Double(index) * 2 * .pi * 440 / 48000) * 0.25)
            channels[0][index] = sample; channels[1][index] = sample
        }
        let output = try XCTUnwrap(AudioCapture.convert(input, using: converter))
        XCTAssertEqual(output.format.sampleRate, 16000); XCTAssertEqual(output.format.channelCount, 1)
        XCTAssertGreaterThan(output.frameLength, 15000); XCTAssertLessThanOrEqual(output.frameLength, 16032)
        let samples = try XCTUnwrap(output.int16ChannelData?[0])
        let rms = sqrt((0..<Int(output.frameLength)).reduce(0.0) { $0 + pow(Double(samples[$1]), 2) } / Double(output.frameLength))
        XCTAssertGreaterThan(rms, 5000); XCTAssertLessThan(rms, 6500)
        let pcm = Data(bytes: samples, count: Int(output.frameLength) * 2)
        XCTAssertEqual(WAV.encode(pcm: pcm).count, pcm.count + 44)
    }
    @MainActor func testDiagnosticsOnlyContainsAllowlistedState() {
        let report = Diagnostics.report()
        XCTAssertTrue(report.contains("OS:")); XCTAssertTrue(report.contains("Architecture:"))
        XCTAssertFalse(report.contains(NSHomeDirectory())); XCTAssertFalse(report.contains("API key:")); XCTAssertFalse(report.contains("transcript:"))
    }
    @MainActor func testKeychainProviderIsolationAndCustomEndpointBinding() throws {
        let directory = try isolatedDirectory()
        let keys = KeychainStore(directory: directory, namespace: "com.tihor.quill.native-test." + UUID().uuidString)
        let first = try keys.save(provider: .groq, label: "test", secret: "first-test-secret", status: "valid", customURL: "")
        let second = try keys.save(provider: .gemini, label: "test", secret: "second-test-secret", status: "valid", customURL: "")
        let custom = try keys.save(provider: .custom, label: "test", secret: "custom-test-secret", status: "valid", customURL: "https://first.example/v1")
        defer { try? keys.clear() }
        var settings = QuillSettings(); settings.activeKeys[Provider.groq.rawValue] = first.id; settings.activeKeys[Provider.gemini.rawValue] = second.id
        XCTAssertEqual(keys.active(.groq, settings: settings), "first-test-secret")
        XCTAssertEqual(keys.active(.gemini, settings: settings), "second-test-secret")
        XCTAssertEqual(keys.read(custom, customURL: "https://first.example/v1"), "custom-test-secret")
        XCTAssertNil(keys.read(custom, customURL: "https://second.example/v1"))
        XCTAssertFalse(try String(contentsOf: directory.appendingPathComponent("key-slots.json")).contains("first-test-secret"))
    }
    @MainActor func testSQLiteRetentionPrivateModeAndDelete() throws {
        let directory = try isolatedDirectory()
        let store = HistoryStore(directory: directory)
        var settings = QuillSettings()
        store.insert(HistoryEntry(text: "expired", created: Date().addingTimeInterval(-4000), provider: .groq, appName: "test", duration: 1), settings: settings)
        XCTAssertTrue(store.entries.isEmpty)
        let fresh = HistoryEntry(text: "saved", provider: .gemini, appName: "test", duration: 2)
        store.insert(fresh, settings: settings); XCTAssertEqual(store.entries.map(\.text), ["saved"])
        settings.privateMode = true; store.insert(HistoryEntry(text: "private", provider: .groq, appName: "test", duration: 1), settings: settings)
        XCTAssertEqual(store.entries.map(\.text), ["saved"])
        store.delete(fresh.id, settings: settings); XCTAssertTrue(store.entries.isEmpty); XCTAssertFalse(store.storageFailed)
    }
    @MainActor func testSettingsPersistAndResetPreservesKeysAndOnboarding() throws {
        let directory = try isolatedDirectory()
        let store = SettingsStore(directory: directory)
        let key = UUID(); store.value.onboardingComplete = true; store.value.accessibilityConsent = true; store.value.activeKeys["groq"] = key; store.value.sessionCapSeconds = 90
        let reopened = SettingsStore(directory: directory)
        XCTAssertEqual(reopened.value.sessionCapSeconds, 90)
        reopened.reset(); XCTAssertEqual(reopened.value.sessionCapSeconds, 300); XCTAssertTrue(reopened.value.onboardingComplete); XCTAssertTrue(reopened.value.accessibilityConsent); XCTAssertEqual(reopened.value.activeKeys["groq"], key)
    }
}
