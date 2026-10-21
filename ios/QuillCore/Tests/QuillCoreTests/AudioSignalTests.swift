import XCTest
import Foundation
@testable import QuillCore

final class AudioSignalTests: XCTestCase {
    private func wav(_ sample: (Int) -> Int16, count: Int = 16000) -> Data {
        var pcm = Data()
        for i in 0..<count { let s = UInt16(bitPattern: sample(i)); pcm.append(UInt8(s & 255)); pcm.append(UInt8(s >> 8)) }
        var header = Data("RIFF".utf8)
        func u32(_ value: Int) { for shift in stride(from: 0, to: 32, by: 8) { header.append(UInt8((value >> shift) & 255)) } }
        func u16(_ value: Int) { header.append(UInt8(value & 255)); header.append(UInt8((value >> 8) & 255)) }
        u32(36+pcm.count); header.append(Data("WAVEfmt ".utf8)); u32(16); u16(1); u16(1); u32(16000); u32(32000); u16(2); u16(16); header.append(Data("data".utf8)); u32(pcm.count)
        return header + pcm
    }
    func testRejectsQuietDitherDcAndClick() throws {
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { _ in 0 }))
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { Int16($0 % 7 - 3) }))
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { _ in 3000 }))
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { $0 == 2000 ? Int16.max : 0 }))
    }
    func testSoftShortAndLoudSignal() throws {
        for amplitude in [200.0, 2000, 32000] {
            XCTAssertTrue(try AudioSignal.hasAudibleSignal(wav({ Int16(sin(Double($0)*Double.pi*440/8000)*amplitude) }, count: 1600)))
        }
    }
    func testSteadyMicrophoneNoiseAndHumAreNotDictation() throws {
        var seed: UInt32 = 17
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { _ in
            seed = seed &* 1664525 &+ 1013904223
            return Int16(Int(seed >> 16) % 801 - 400)
        }))
        XCTAssertFalse(try AudioSignal.hasAudibleSignal(wav { Int16(sin(Double($0)*Double.pi*50/8000)*2000) }))
    }
    func testRejectsMalformedAudio() {
        for data in [Data(), Data("RIFF".utf8), wav({ _ in 0 }).prefix(100)] { XCTAssertThrowsError(try AudioSignal.hasAudibleSignal(data)) }
    }
    func testRealSpeech() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "speech", withExtension: "wav", subdirectory: "Fixtures") ?? Bundle.module.url(forResource: "speech", withExtension: "wav"))
        XCTAssertTrue(try AudioSignal.hasAudibleSignal(Data(contentsOf: url)))
    }
}

