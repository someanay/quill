import Foundation

/// A terminal marker prevents a retained file from being offered as a new transcription after
/// restart. Deletion is still attempted when marking fails (for example when storage is full).
public enum RecordingCleanup {
    public struct Outcome: Equatable {
        public let terminalCommitted: Bool
        public let audioRemoved: Bool
        public let manifestRemoved: Bool
        public var cleanupFailed: Bool { !audioRemoved || !manifestRemoved }
    }

    public static func finish(markTerminal: () throws -> Void, removeAudio: () throws -> Void,
                              removeManifest: () throws -> Void) -> Outcome {
        func attempt(_ action: () throws -> Void) -> Bool {
            do { try action(); return true } catch { return false }
        }
        let marked = attempt(markTerminal)
        let audioRemoved = attempt(removeAudio)
        // Keep a successful terminal marker while its audio remains. If marking failed, removing
        // the old manifest is another way to prevent false recovery of that take.
        let manifestRemoved = audioRemoved || !marked ? attempt(removeManifest) : false
        return Outcome(terminalCommitted: marked || audioRemoved || manifestRemoved,
                       audioRemoved: audioRemoved, manifestRemoved: manifestRemoved)
    }
}
