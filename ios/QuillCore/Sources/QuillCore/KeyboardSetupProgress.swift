import Foundation

/// Setup uses a recent extension receipt, never the enabled-keyboard preferences or typed text
/// alone. The receipt contains a timestamp and an opaque transfer ID, not destination text.
public struct KeyboardSetupReceipt: Codable, Equatable, Sendable {
    public let connectedAt: Date
    public let insertedTransferID: UUID?
    public init(connectedAt: Date = Date(), insertedTransferID: UUID? = nil) {
        self.connectedAt = connectedAt; self.insertedTransferID = insertedTransferID
    }
    public func isRecent(since: Date, now: Date = Date()) -> Bool {
        connectedAt >= since && connectedAt <= now.addingTimeInterval(5) && now.timeIntervalSince(connectedAt) <= 600
    }
    public func confirmsPractice(transferID: UUID?, transcript: String, field: String) -> Bool {
        guard let transferID, insertedTransferID == transferID,
              !transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        return field.contains(transcript)
    }
}
