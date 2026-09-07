import Foundation

public enum Provider: String, Codable, CaseIterable, Identifiable, Sendable {
    case groq, openai, openrouter, gemini, custom
    public var id: String { rawValue }
    public var title: String { ["groq": "Groq", "openai": "OpenAI", "openrouter": "OpenRouter", "gemini": "Gemini", "custom": "Custom"][rawValue]! }
    public var baseURL: String {
        switch self {
        case .groq: return "https://api.groq.com/openai/v1"
        case .openai: return "https://api.openai.com/v1"
        case .openrouter: return "https://openrouter.ai/api/v1"
        case .gemini: return "https://generativelanguage.googleapis.com"
        case .custom: return ""
        }
    }
    public var defaultModel: String { speechModels.first ?? "" }
    public var speechModels: [String] {
        switch self {
        case .groq: return ["whisper-large-v3-turbo", "whisper-large-v3"]
        case .openai: return ["whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"]
        case .openrouter: return ["openai/whisper-large-v3-turbo", "openai/gpt-4o-mini-transcribe", "openai/gpt-4o-transcribe"]
        case .gemini: return ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"]
        case .custom: return []
        }
    }
    public var defaultCleanupModel: String {
        switch self {
        case .groq: return "llama-3.3-70b-versatile"
        case .openai: return "gpt-4o-mini"
        case .openrouter: return "openrouter/free"
        case .gemini: return "gemini-3.5-flash-lite"
        case .custom: return ""
        }
    }
    public var hasKnownFreeCleanup: Bool { [.groq, .openrouter, .gemini].contains(self) }
    public var consoleURL: URL? {
        URL(string: [.groq: "https://console.groq.com/keys", .openai: "https://platform.openai.com/api-keys", .openrouter: "https://openrouter.ai/keys", .gemini: "https://aistudio.google.com/apikey"][self] ?? "")
    }
}

public enum WritingStyle: String, Codable, CaseIterable, Identifiable, Sendable {
    case formal, neutral, casual
    public var id: String { rawValue }
    public var title: String { rawValue.capitalized }
    public var directive: String {
        let bounds = " This restyling may ONLY change capitalization, sentence-final punctuation, comma and clause punctuation, contraction form, and line or paragraph breaks. Never substitute, add, remove content words, reorder, summarize, paraphrase, add emoji or greetings, or answer the text. Preserving what the user said always outranks style."
        switch self {
        case .neutral: return ""
        case .formal: return " WRITING STYLE: Full sentence case, terminal punctuation, complete clauses, no ellipses, contractions expanded." + bounds
        case .casual: return " WRITING STYLE: Relaxed capitalization, terminal punctuation optional, sparing commas, contractions preferred." + bounds
        }
    }
}

public struct QuillSettings: Codable, Equatable, Sendable {
    public var provider: Provider = .groq
    public var model = Provider.groq.defaultModel
    public var customBaseURL = ""
    public var customModel = ""
    public var language = "en"
    public var cleanupEnabled = true
    public var cleanupAutomatic = true
    public var cleanupProvider: Provider = .groq
    public var cleanupModel = Provider.groq.defaultCleanupModel
    // Optional for backwards-compatible decoding of an existing settings file.
    private var rememberedCleanupModels: [String: String]?
    public var writingStyle: WritingStyle = .neutral
    public var privateMode = false
    public var historyEnabled = true
    public var retentionMinutes = 60
    public var sessionCapSeconds = 300
    public var onboardingComplete = false
    public var onboardingStep = 0
    public var bubbleSize = "Medium"
    public var theme = "Match system"
    public var keyboardSetupAcknowledged = false
    public var crashReportingEnabled = false
    public init() {}
    public var shouldSaveHistory: Bool { historyEnabled && !privateMode }
    public var effectiveCleanupProvider: Provider { cleanupAutomatic ? provider : cleanupProvider }
    public var effectiveCleanupModel: String { cleanupAutomatic ? provider.defaultCleanupModel : cleanupModel }
    public var effectiveCleanupEnabled: Bool { cleanupEnabled && (!cleanupAutomatic || provider.hasKnownFreeCleanup) }
    public mutating func selectProvider(_ value: Provider) {
        provider = value; model = value == .custom ? customModel : value.defaultModel
    }
    public mutating func setCleanupModel(_ value: String) {
        if cleanupAutomatic { cleanupProvider = provider }
        cleanupAutomatic = false
        cleanupModel = value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    public mutating func setCleanupAutomatic(_ automatic: Bool) {
        if !automatic && cleanupAutomatic {
            cleanupProvider = provider; cleanupModel = provider.defaultCleanupModel
        }
        cleanupAutomatic = automatic
        if automatic { cleanupEnabled = true }
    }
    public mutating func selectCleanupProvider(_ value: Provider) {
        cleanupAutomatic = false
        var remembered = rememberedCleanupModels ?? [:]
        remembered[cleanupProvider.rawValue] = cleanupModel
        cleanupProvider = value
        cleanupModel = remembered[value.rawValue] ?? value.defaultCleanupModel
        rememberedCleanupModels = remembered
    }
    public mutating func setCleanupEnabled(_ enabled: Bool) {
        cleanupEnabled = enabled
        if enabled && cleanupAutomatic && !provider.hasKnownFreeCleanup {
            cleanupAutomatic = false
            cleanupProvider = provider; cleanupModel = provider.defaultCleanupModel
        }
    }
    public mutating func sanitize() {
        sessionCapSeconds = min(300, max(60, sessionCapSeconds))
        if ![0, 60, 1440, 10080, 43200].contains(retentionMinutes) { retentionMinutes = 60 }
        if !["Match system", "Light", "Dark"].contains(theme) { theme = "Match system" }
        if !["Small", "Medium", "Large"].contains(bubbleSize) { bubbleSize = "Medium" }
        onboardingStep = min(7, max(0, onboardingStep))
        if language.isEmpty { language = "en" }
    }
}

public struct CustomWord: Codable, Equatable, Identifiable, Sendable {
    public var id: UUID
    public var heard: String
    public var replacement: String
    public init(heard: String, replacement: String, id: UUID = UUID()) { self.id = id; self.heard = heard; self.replacement = replacement }
}

public struct HistoryEntry: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var text: String
    public var provider: Provider
    public var date: Date
    public var destination: String
    public var status: String
    public init(text: String, provider: Provider, date: Date = Date(), destination: String = "", status: String = "ready", id: UUID = UUID()) {
        self.id = id; self.text = text; self.provider = provider; self.date = date; self.destination = destination; self.status = status
    }
}

public struct KeyboardHandoff: Codable, Equatable, Sendable {
    public var id: UUID
    public var text: String
    public var createdAt: Date
    public var expiresAt: Date
    public init(text: String, now: Date = Date()) {
        id = UUID(); self.text = text; createdAt = now; expiresAt = now.addingTimeInterval(60)
    }
    public func isValid(now: Date = Date()) -> Bool {
        now >= createdAt && now < expiresAt && expiresAt.timeIntervalSince(createdAt) <= 60 && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && text.utf8.count <= 200_000
    }
}

public struct ModelOption: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var isFree: Bool
    public var isPaid: Bool
    public var created: Double
    public init(_ id: String, name: String? = nil, isFree: Bool = false, isPaid: Bool = false, created: Double = 0) {
        self.id = id; self.name = name ?? id; self.isFree = isFree; self.isPaid = isPaid; self.created = created
    }
}

public enum QuillError: Error, Equatable, LocalizedError, Sendable {
    case invalidEndpoint, invalidKey, noKey, noSpeech, network, quota, rateLimit, provider(Int), malformedResponse, audio, storage, interrupted, expired, cancelled
    public var copyKey: String {
        switch self {
        case .invalidEndpoint: return "ios.error.endpoint"
        case .invalidKey: return "key.error.invalid"
        case .noKey: return "ios.error.no_key"
        case .noSpeech: return "ios.error.no_speech"
        case .network: return "key.error.network"
        case .quota: return "stt.quota.toast"
        case .rateLimit: return "ios.error.rate_limit"
        case .provider: return "ios.error.provider"
        case .malformedResponse: return "ios.error.response"
        case .audio: return "ios.error.audio"
        case .storage: return "ios.error.storage"
        case .interrupted: return "ios.error.interrupted"
        case .expired: return "ios.error.expired"
        case .cancelled: return "ios.error.cancelled"
        }
    }
    public var errorDescription: String? { copyKey }
}
