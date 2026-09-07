import Foundation

public struct CopyCatalog: Sendable {
    public let strings: [String:String]
    public init(data: Data) throws {
        struct Envelope: Decodable { let schemaVersion: Int; let strings: [String:String] }
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        guard envelope.schemaVersion == 1 else { throw QuillError.storage }
        self.strings = envelope.strings
    }
    public func callAsFunction(_ key: String, _ args: String...) -> String { resolve(key, arguments: args) }
    public func resolve(_ key: String, arguments: [String] = []) -> String {
        Self.format(strings[key] ?? key, arguments: arguments)
    }
    public static func format(_ template: String, arguments: [String]) -> String {
        let regex = try! NSRegularExpression(pattern: "%([0-9]+\\$)?([-#+0,(]*)([0-9]*)(?:\\.([0-9]+))?([a-zA-Z%])")
        let source = template as NSString
        var output = "", cursor = 0, implicit = 0
        for match in regex.matches(in: template, range: NSRange(location: 0, length: source.length)) {
            output += source.substring(with: NSRange(location: cursor, length: match.range.location - cursor))
            cursor = NSMaxRange(match.range)
            func group(_ index: Int) -> String { let range = match.range(at: index); return range.location == NSNotFound ? "" : source.substring(with: range) }
            let conversion = group(5), flags = group(2), width = min(10000, Int(group(3)) ?? 0), precision = Int(group(4)).map { min(1000, $0) }
            if conversion == "%" { output += "%"; continue }
            if conversion == "n" { output += "\n"; continue }
            let explicit = group(1)
            let index: Int
            if !explicit.isEmpty { index = (Int(explicit.dropLast()) ?? 0) - 1 }
            else { index = implicit; implicit += 1 }
            guard arguments.indices.contains(index) else { output += source.substring(with: match.range); continue }
            let argument = arguments[index]
            var formatted: String
            switch conversion.lowercased() {
            case "s": formatted = precision.map { String(argument.prefix($0)) } ?? argument
            case "b": formatted = argument == "null" || argument.lowercased() == "false" ? "false" : "true"
            case "c": formatted = Int(argument).flatMap(UnicodeScalar.init).map(String.init) ?? String(argument.prefix(1))
            case "d", "o", "x":
                guard let integer = Int64(argument) else { output += argument; continue }
                let radix = conversion.lowercased() == "x" ? 16 : conversion == "o" ? 8 : 10
                let magnitude = integer.magnitude
                formatted = String(magnitude, radix: radix)
                if flags.contains(",") && radix == 10 {
                    var grouped = ""
                    for (index, char) in formatted.reversed().enumerated() { if index > 0 && index % 3 == 0 { grouped.append(",") }; grouped.append(char) }
                    formatted = String(grouped.reversed())
                }
                if flags.contains("#") && radix != 10 { formatted = (radix == 16 ? "0x" : "0") + formatted }
                if integer < 0 { formatted = flags.contains("(") ? "(" + formatted + ")" : "-" + formatted }
                else if flags.contains("+") { formatted = "+" + formatted }
            case "f", "e", "g", "a":
                guard let number = Double(argument) else { output += argument; continue }
                let format = "%" + (flags.contains("+") ? "+" : "") + (flags.contains("#") ? "#" : "") + (precision.map { ".\($0)" } ?? "") + conversion
                formatted = String(format: format, locale: Locale(identifier: "en_US_POSIX"), number)
                if number < 0 && flags.contains("(") { formatted = "(" + formatted.dropFirst() + ")" }
            default: formatted = argument
            }
            if conversion == conversion.uppercased() { formatted = formatted.uppercased() }
            if formatted.count < width {
                let fill = String(repeating: flags.contains("0") && !flags.contains("-") ? "0" : " ", count: width - formatted.count)
                if flags.contains("-") { formatted += fill }
                else if flags.contains("0") && (formatted.hasPrefix("+") || formatted.hasPrefix("-")) { formatted = String(formatted.prefix(1)) + fill + formatted.dropFirst() }
                else { formatted = fill + formatted }
            }
            output += formatted
        }
        output += source.substring(from: cursor)
        return output
    }
}
