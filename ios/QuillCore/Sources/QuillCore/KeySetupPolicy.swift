import Foundation

public enum KeySetupPolicy {
    public static let guidedProviders: [Provider] = [.gemini, .groq, .openrouter]
    public static func plausible(_ provider: Provider, _ value: String) -> Bool {
        let key = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let prefix: Bool
        switch provider {
        case .gemini: prefix = key.hasPrefix("AIza") || key.hasPrefix("AQ.")
        case .groq: prefix = key.hasPrefix("gsk_")
        case .openrouter: prefix = key.hasPrefix("sk-or-")
        default: return false
        }
        return prefix && (20...4096).contains(key.utf16.count) && !key.contains("...") && key.allSatisfy { $0.isLetter || $0.isNumber || "-_.".contains($0) }
    }
    public static func mask(_ provider: Provider) -> String {
        switch provider {
        case .gemini: return "AIza•••••• / AQ.••••••"
        case .groq: return "gsk_••••••••••••••••"
        case .openrouter: return "sk-or-v1-••••••••••••"
        default: return "••••••••••••••••••••"
        }
    }
    public static func titleKey(_ provider: Provider) -> String {
        switch provider { case .gemini: return "onboarding.key.easy"; case .groq: return "onboarding.key.more_steps"; default: return "onboarding.key.free_options" }
    }
}
