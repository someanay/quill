import Foundation

/// Local PCM signal floor. Background speech still requires recognizer judgement.
public enum AudioSignal {
    public enum Failure: Error { case invalidWAV }
    public static func hasAudibleSignal(_ data: Data) throws -> Bool {
        try data.withUnsafeBytes { raw in
            let b = raw.bindMemory(to: UInt8.self)
            func tag(_ p: Int, _ value: String) -> Bool { p + 4 <= b.count && Array(b[p..<p+4]) == Array(value.utf8) }
            func u16(_ p: Int) -> Int { Int(b[p]) | Int(b[p+1]) << 8 }
            func u32(_ p: Int) -> Int { u16(p) | u16(p+2) << 16 }
            guard b.count >= 12, tag(0,"RIFF"), tag(8,"WAVE") else { throw Failure.invalidWAV }
            var rate = 0, channels = 0, offset = -1, length = 0, p = 12
            while p <= b.count - 8 {
                let size = u32(p+4)
                guard size <= b.count - p - 8 else { throw Failure.invalidWAV }
                if tag(p,"fmt ") {
                    guard size >= 16, u16(p+8) == 1, u16(p+22) == 16 else { throw Failure.invalidWAV }
                    channels = u16(p+10); rate = u32(p+12)
                } else if tag(p,"data") { offset = p+8; length = size }
                p += 8 + size + (size & 1)
            }
            guard (8000...96000).contains(rate), (1...2).contains(channels), offset >= 0, length % (channels*2) == 0 else { throw Failure.invalidWAV }
            let frame = rate/50, stride = channels*2
            var audible = 0, consecutive = 0, longest = 0
            var levels: [Double] = [], previous = 0.0, earlier = 0.0, peak = 0.0
            p = offset
            while p + frame*stride <= offset+length {
                var active = false, level = 0.0
                for channel in 0..<channels {
                    var sum = 0.0, squares = 0.0, low = 32767, high = -32768
                    for i in 0..<frame {
                        let s = Int(Int16(bitPattern: UInt16(u16(p+i*stride+channel*2))))
                        sum += Double(s); squares += Double(s)*Double(s); low = min(low,s); high = max(high,s)
                    }
                    let mean = sum/Double(frame)
                    let energy = max(0,squares/Double(frame) - mean*mean)
                    active = active || (energy >= 80*80 && high-low >= 320)
                    level = max(level,sqrt(energy))
                }
                consecutive = active ? consecutive+1 : 0
                longest = max(longest,consecutive)
                if active { audible += 1 }
                if consecutive >= 3 { peak = max(peak,min(level,min(previous,earlier))) }
                levels.append(level); earlier = previous; previous = level
                p += frame*stride
            }
            guard audible >= 4, longest >= 3 else { return false }
            if levels.count < 20 { return true }
            levels.sort()
            return peak >= max(80,levels[(levels.count-1)/5]*1.4)
        }
    }
}

