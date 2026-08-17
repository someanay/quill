import Foundation

// Port of windows/.../Cleanup/CleanupGuard.cs. Fail closed: cleanup may adjust casing and punctuation,
// never spoken words. Exact token-sequence equality after contraction normalization (deliberately
// stricter than Android's token-overlap guard, matching the Windows cleanup prompt's "punctuation and
// casing only" scope — see the audit report's A-GUARD note).
enum CleanupGuard {
    static func isSafe(_ original: String, _ candidate: String) -> Bool {
        if candidate.trimmed.isEmpty { return false }
        let source = tokens(original)
        let output = tokens(candidate)
        if source.isEmpty || output.isEmpty { return false }
        return source == output
    }

    private static func tokens(_ value: String) -> [String] {
        normalizeContractions(value).lowercased()
            .split { !($0.isLetter || $0.isNumber) }
            .map(String.init)
    }

    private static func normalizeContractions(_ value: String) -> String {
        var v = value.replacingOccurrences(of: "\u{2019}", with: "'")
        let map = [("'s", " is"), ("n't", " not"), ("'re", " are"),
                   ("'ve", " have"), ("'ll", " will"), ("'d", " would"), ("'m", " am")]
        for (from, to) in map { v = v.replacingOccurrences(of: from, with: to, options: .caseInsensitive) }
        return v
    }
}
