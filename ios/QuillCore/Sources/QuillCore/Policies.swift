import Foundation

public enum Endpoint {
    public static func normalize(_ text: String) throws -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard var c = URLComponents(string: trimmed), c.scheme?.lowercased() == "https", let host = c.host, !host.isEmpty,
              c.user == nil, c.password == nil, c.query == nil, c.fragment == nil,
              !trimmed.contains("\\"), !trimmed.contains(where: { $0.isWhitespace }),
              !c.percentEncodedPath.lowercased().contains("%2e"),
              !c.path.split(separator: "/").contains(".."), !c.path.split(separator: "/").contains("."),
              c.port == nil || (1...65535).contains(c.port!) else { throw QuillError.invalidEndpoint }
        c.scheme = "https"; c.host = host.lowercased()
        while c.path.hasSuffix("/") { c.path.removeLast() }
        guard let normalized = c.url?.absoluteString else { throw QuillError.invalidEndpoint }
        return normalized
    }
}

public enum RecordingLimits {
    public static func reached(elapsed: Double, cap: Double) -> Bool { elapsed >= cap }
    public static func shouldWarn(elapsed: Double, cap: Double) -> Bool { elapsed >= cap - 15 && elapsed < cap }
}

public struct SilenceDetector: Sendable {
    private var quietSince: Double?
    public init() {}
    public mutating func feed(rms: Double, elapsed: Double) -> Bool {
        if !rms.isFinite || rms >= 600 { quietSince = nil; return false }
        if elapsed < 2 { return false }
        if quietSince == nil { quietSince = elapsed }
        return elapsed - (quietSince ?? elapsed) >= 3.5
    }
}

public enum WordSubstitutions {
    public static func apply(_ text: String, _ words: [CustomWord]) -> String {
        words.reduce(text) { value, word in
            let heard = word.heard.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !heard.isEmpty, let expression = try? NSRegularExpression(pattern: "(?<![\\p{L}\\p{N}])" + NSRegularExpression.escapedPattern(for: heard) + "(?![\\p{L}\\p{N}])", options: [.caseInsensitive]) else { return value }
            return expression.stringByReplacingMatches(in: value, range: NSRange(value.startIndex..., in: value), withTemplate: NSRegularExpression.escapedTemplate(for: word.replacement))
        }
    }
}

public enum CleanupGuard {
    private static let contractions: [String: String] = {
        let pairs = ["can't": "can not", "cannot": "can not", "won't": "will not", "shan't": "shall not", "don't": "do not", "doesn't": "does not", "didn't": "did not", "isn't": "is not", "aren't": "are not", "wasn't": "was not", "weren't": "were not", "hasn't": "has not", "haven't": "have not", "hadn't": "had not", "couldn't": "could not", "wouldn't": "would not", "shouldn't": "should not", "mustn't": "must not", "needn't": "need not", "i'm": "i am", "i've": "i have", "i'll": "i will", "i'd": "i would", "you're": "you are", "you've": "you have", "you'll": "you will", "you'd": "you would", "we're": "we are", "we've": "we have", "we'll": "we will", "we'd": "we would", "they're": "they are", "they've": "they have", "they'll": "they will", "they'd": "they would", "he's": "he is", "she's": "she is", "it's": "it is", "that's": "that is", "there's": "there is", "here's": "here is", "what's": "what is", "who's": "who is", "let's": "let us"]
        var all = pairs
        for (k, v) in pairs where k != "we'll" && k != "we'd" && k != "they'd" { all[k.replacingOccurrences(of: "'", with: "")] = v }
        return all
    }()
    private static func tokens(_ input: String) -> [String] {
        let normalized = input.lowercased().replacingOccurrences(of: "’", with: "'")
        guard let words = try? NSRegularExpression(pattern: "\\p{L}+(?:'\\p{L}+)?|\\p{N}+") else { return [] }
        return words.matches(in: normalized, range: NSRange(normalized.startIndex..., in: normalized)).flatMap { match -> [String] in
            guard let range = Range(match.range, in: normalized) else { return [] }
            let word = String(normalized[range])
            return (contractions[word] ?? word).components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        }
    }
    public static func preserves(_ raw: String, _ cleaned: String) -> Bool {
        let a = tokens(raw), b = tokens(cleaned)
        guard !a.isEmpty, !b.isEmpty else { return false }
        var budget: [String: Int] = [:]
        for token in a { budget[token, default: 0] += 1 }
        var overlap = 0
        for token in b where budget[token, default: 0] > 0 { budget[token, default: 0] -= 1; overlap += 1 }
        return Double(overlap) / Double(b.count) >= 0.7 && Double(overlap) / Double(a.count) >= 0.5
    }
}

public enum TextPipeline {
    public static func finish(raw: String, cleaned: String?, words: [CustomWord]) -> String {
        let chosen = cleaned.flatMap { CleanupGuard.preserves(raw, $0) ? $0 : nil } ?? raw
        return WordSubstitutions.apply(chosen, words)
    }
}

public enum InsertionComposer {
    public static func compose(_ text: String, before: String?, after: String?) -> String {
        guard !text.isEmpty else { return "" }
        let closing = CharacterSet(charactersIn: ",.;:!?)]}、。，！？：；")
        let opening = CharacterSet(charactersIn: "([{\n")
        var result = text
        if let last = before?.unicodeScalars.last, let first = text.unicodeScalars.first,
           !CharacterSet.whitespacesAndNewlines.contains(last), !opening.contains(last),
           !CharacterSet.whitespacesAndNewlines.contains(first), !closing.contains(first) { result = " " + result }
        if let next = after?.unicodeScalars.first, let last = text.unicodeScalars.last,
           !CharacterSet.whitespacesAndNewlines.contains(next), !closing.contains(next),
           !CharacterSet.whitespacesAndNewlines.contains(last), !opening.contains(last) { result += " " }
        return result
    }
}

public enum HistoryPolicy {
    public static func prune(_ entries: [HistoryEntry], retentionMinutes: Int, now: Date = Date()) -> [HistoryEntry] {
        Array(entries.filter { retentionMinutes == 0 || $0.date > now.addingTimeInterval(-Double(retentionMinutes) * 60) }.sorted { $0.date > $1.date }.prefix(1000))
    }
}

public enum ContactVocabulary {
    public static func extract(_ names: [String]) -> [String] {
        let regex = try! NSRegularExpression(pattern: "^\\p{L}+(?:['-]\\p{L}+)*\\.?$")
        var seen = Set<String>(), result = [String]()
        func offer(_ name: String) { if result.count < 1000 && seen.insert(name.lowercased()).inserted { result.append(name) } }
        for raw in names {
            let tokens = raw.split(whereSeparator: \.isWhitespace).map(String.init), name = tokens.joined(separator: " ")
            guard (2...48).contains(name.count), (1...4).contains(tokens.count), tokens.allSatisfy({ regex.firstMatch(in: $0, range: NSRange($0.startIndex..., in: $0)) != nil }) else { continue }
            offer(name)
            if tokens.count > 1 { for token in tokens where token.count >= 2 { offer(token) } }
            if result.count >= 1000 { break }
        }
        return result
    }
}

public struct CopyCatalog: Sendable {
    public let strings: [String: String]
    public init(strings: [String: String]) { self.strings = strings }
    public init(data: Data) throws {
        struct Envelope: Decodable { let schemaVersion: Int; let strings: [String: String] }
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        guard envelope.schemaVersion == 1 else { throw QuillError.malformedResponse }
        strings = envelope.strings
    }
    public func text(_ key: String, _ args: String...) -> String { format(key, args: args) }
    public func format(_ key: String, args: [String]) -> String {
        let value = strings[key] ?? key
        let regex = Self.placeholderExpression
        var implicitIndex = 0
        var lastIndex = 0
        var replacements: [(NSRange, String)] = []
        let nsValue = value as NSString
        for match in regex.matches(in: value, range: NSRange(location: 0, length: nsValue.length)) {
            let token = nsValue.substring(with: match.range)
            if token == "%%" { replacements.append((match.range, "%")); continue }
            if token == "%n" { replacements.append((match.range, "\n")); continue }
            let indexText = match.range(at: 1).location == NSNotFound ? "" : nsValue.substring(with: match.range(at: 1)).replacingOccurrences(of: "$", with: "")
            let flags = match.range(at: 2).location == NSNotFound ? "" : nsValue.substring(with: match.range(at: 2))
            let widthText = match.range(at: 3).location == NSNotFound ? "" : nsValue.substring(with: match.range(at: 3))
            let precisionText = match.range(at: 4).location == NSNotFound ? "" : nsValue.substring(with: match.range(at: 4))
            let kind = nsValue.substring(with: match.range(at: 5))
            let index: Int
            if flags.contains("<") { index = lastIndex }
            else if let explicit = Int(indexText) { index = explicit - 1 }
            else { index = implicitIndex; implicitIndex += 1 }
            lastIndex = index
            guard args.indices.contains(index) else { continue }
            let argument = args[index]
            let width = min(Int(widthText) ?? 0, 1000)
            let precision = min(Int(precisionText) ?? 6, 100)
            var rendered: String
            switch kind {
            case "s":
                rendered = precisionText.isEmpty ? argument : String(argument.prefix(precision))
                if width > rendered.count {
                    let spaces = String(repeating: " ", count: width - rendered.count)
                    rendered = flags.contains("-") ? rendered + spaces : spaces + rendered
                }
            case "d":
                guard let integer = Int64(argument) else { continue }
                rendered = String(integer)
                if integer >= 0 { rendered = (flags.contains("+") ? "+" : flags.contains(" ") ? " " : "") + rendered }
                rendered = Self.paddedNumber(rendered, width: width, flags: flags)
            default:
                guard let number = Double(argument), number.isFinite else { continue }
                rendered = String(format: "%.\(precision)f", number)
                if flags.contains("#") && precision == 0 { rendered += "." }
                if number >= 0 { rendered = (flags.contains("+") ? "+" : flags.contains(" ") ? " " : "") + rendered }
                rendered = Self.paddedNumber(rendered, width: width, flags: flags)
            }
            replacements.append((match.range, rendered))
        }
        let output = NSMutableString(string: value)
        for (range, replacement) in replacements.reversed() { output.replaceCharacters(in: range, with: replacement) }
        return output as String
    }
    private static func paddedNumber(_ text: String, width: Int, flags: String) -> String {
        guard width > text.count else { return text }
        let padding = String(repeating: flags.contains("0") && !flags.contains("-") ? "0" : " ", count: width - text.count)
        if flags.contains("-") { return text + padding }
        if flags.contains("0"), let first = text.first, "+- ".contains(first) { return String(first) + padding + text.dropFirst() }
        return padding + text
    }
    private static let placeholderExpression = try! NSRegularExpression(pattern: "%([0-9]+\\$)?([-#+ 0,(<]*)([0-9]*)(?:\\.([0-9]+))?([sdf])|%%|%n")
    public static func placeholderSignature(_ value: String) -> [String] {
        let ns = value as NSString
        var index = 0, last = 0, tokens = Set<String>()
        for match in placeholderExpression.matches(in: value, range: NSRange(location: 0, length: ns.length)) {
            guard match.range(at: 5).location != NSNotFound else { continue }
            let position = match.range(at: 1).location == NSNotFound ? "" : ns.substring(with: match.range(at: 1)).replacingOccurrences(of: "$", with: "")
            let flags = ns.substring(with: match.range(at: 2))
            let current: Int
            if flags.contains("<") { current = last }
            else if let explicit = Int(position) { current = explicit }
            else { index += 1; current = index }
            last = current
            tokens.insert("%\(current)$" + ns.substring(with: match.range(at: 5)))
        }
        return tokens.sorted()
    }
}
