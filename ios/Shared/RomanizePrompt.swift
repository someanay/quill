import Foundation

// Port of windows/.../Stt/RomanizePrompt.cs — keeps code-mixed Hindi in Roman/Latin script for
// explicit-English dictation only (auto-detect and Hindi remain the user's choice).
enum RomanizePrompt {
    static let hinglish =
        "The speaker mixes English and Hindi (Hinglish) and writes Hindi words in Roman/Latin script. " +
        "Transcribe Hindi words in Roman/Latin letters, not Devanagari. Example: Aaj ka plan kya hai? Let's meet kal, theek hai."

    static func forLanguage(_ language: String?) -> String? {
        language?.caseInsensitiveCompare("en") == .orderedSame ? hinglish : nil
    }
}
