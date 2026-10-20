import Foundation

/// A live foreground-enabled microphone lease. This is never an instruction to wake an app.
public struct KeyboardRecordingSession: Codable, Equatable, Sendable {
    public enum Phase: String, Codable, Sendable { case ready, recording, processing, failed, closed }
    public let id: UUID
    public let expiresAt: Date
    public var updatedAt: Date
    public var phase: Phase = .ready
    public var captureID: UUID?
    public var transferID: UUID?
    public var acknowledgedCommandID: UUID?
    public var errorKey: String?
    public init(now: Date = Date(), duration: TimeInterval = 300) {
        id = UUID(); updatedAt = now; expiresAt = now.addingTimeInterval(min(900, max(1, duration)))
    }
    public func isLive(now: Date = Date()) -> Bool {
        phase != .closed && now < expiresAt && updatedAt <= now.addingTimeInterval(1) && now.timeIntervalSince(updatedAt) <= 3
    }
}

public struct KeyboardSessionCommand: Codable, Equatable, Sendable {
    public enum Kind: String, Codable, Sendable { case start, stop, retry, discard, end }
    public let id: UUID
    public let sessionID: UUID
    public let kind: Kind
    public let captureID: UUID?
    public let createdAt: Date
    public init(sessionID: UUID, kind: Kind, captureID: UUID? = nil, now: Date = Date()) {
        id = UUID(); self.sessionID = sessionID; self.kind = kind; self.captureID = captureID; createdAt = now
    }
    public func isValid(for session: KeyboardRecordingSession, now: Date = Date()) -> Bool {
        guard session.id == sessionID, session.isLive(now: now), createdAt <= now.addingTimeInterval(1), now.timeIntervalSince(createdAt) <= 10 else { return false }
        switch kind {
        case .start: return session.phase == .ready && session.transferID == nil
        case .stop: return session.phase == .recording && captureID == session.captureID
        case .retry, .discard: return session.phase == .failed && captureID == session.captureID
        case .end: return true
        }
    }
}

/// Kept only in the keyboard process. Never encoded, shared, logged or sent to a provider.
public struct KeyboardInsertionAnchor: Equatable, Sendable {
    let documentID: UUID
    let before: String?
    let after: String?
    let selection: String?
    public init(documentID: UUID, before: String?, after: String?, selection: String?) {
        self.documentID = documentID; self.before = before; self.after = after; self.selection = selection
    }
    public func matches(documentID: UUID, before: String?, after: String?, selection: String?) -> Bool {
        (self.before != nil || self.after != nil) && self.documentID == documentID && self.before == before && self.after == after && self.selection == selection
    }
}
