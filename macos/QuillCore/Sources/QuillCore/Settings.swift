import Foundation

public enum Provider: String, Codable, CaseIterable, Identifiable, Sendable {
    case groq, openai, openrouter, gemini, custom
    public var id: String { rawValue }
    public var name: String { ["groq":"Groq", "openai":"OpenAI", "openrouter":"OpenRouter", "gemini":"Gemini", "custom":"Custom"][rawValue]! }
    public var baseURL: String {
        switch self {
        case .groq: return "https://api.groq.com/openai/v1"
        case .openai: return "https://api.openai.com/v1"
        case .openrouter: return "https://openrouter.ai/api/v1"
        case .gemini: return "https://generativelanguage.googleapis.com"
        case .custom: return ""
        }
    }
    public var speechModels: [String] {
        switch self {
        case .groq: return ["whisper-large-v3-turbo", "whisper-large-v3"]
        case .openai: return ["whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"]
        case .openrouter: return ["openai/whisper-large-v3-turbo", "openai/gpt-4o-mini-transcribe", "openai/gpt-4o-transcribe"]
        case .gemini: return ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"]
        case .custom: return []
        }
    }
    public var defaultSpeech: String { speechModels.first ?? "" }
    public var defaultCleanup: String {
        switch self {
        case .groq: return "llama-3.3-70b-versatile"
        case .openai: return "gpt-4o-mini"
        case .openrouter: return "openrouter/free"
        case .gemini: return "gemini-3.5-flash-lite"
        case .custom: return ""
        }
    }
    public var freeCleanup: Bool { self == .groq || self == .gemini || self == .openrouter }
    public var console: URL? {
        URL(string: ["groq":"https://console.groq.com/keys", "openai":"https://platform.openai.com/api-keys", "openrouter":"https://openrouter.ai/keys", "gemini":"https://aistudio.google.com/apikey"][rawValue] ?? "")
    }
}

public enum WritingStyle: String, Codable, CaseIterable, Identifiable, Sendable {
    case formal, neutral, casual
    public var id: String { rawValue }
}
public struct CustomWord: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var heard: String
    public var replacement: String
    public init(id: UUID = UUID(), heard: String, replacement: String) { self.id = id; self.heard = heard; self.replacement = replacement }
}
public struct KeySlot: Codable, Identifiable, Equatable, Sendable {
    public var id: UUID
    public var provider: Provider
    public var label: String
    public var status: String
    public var customOrigin: String?
    public init(id: UUID = UUID(), provider: Provider, label: String, status: String, customOrigin: String? = nil) {
        self.id = id; self.provider = provider; self.label = label; self.status = status; self.customOrigin = customOrigin
    }
}
public struct QuillSettings: Codable, Equatable, Sendable {
    public var provider: Provider = .groq
    public var speechModel = Provider.groq.defaultSpeech
    public var customBaseURL = ""
    public var customModel = ""
    public var language = "en"
    public var cleanupEnabled = true
    public var cleanupAutomatic = true
    public var cleanupProvider: Provider = .groq
    public var cleanupModel = Provider.groq.defaultCleanup
    public var cleanupModelSelections: [String:String] = [:]
    public var writingStyle: WritingStyle = .neutral
    public var styleOverrides: [String:WritingStyle] = [:]
    public var privateMode = false
    public var historyEnabled = true
    public var historyRetentionMinutes = 60
    public var sessionCapSeconds = 300
    public var onboardingComplete = false
    public var accessibilityEnabledOnce = false
    public var bubbleSize = "Medium"
    public var appTheme = "Match system"
    public var bootRestore = true
    public var bubbleActive = false
    public var bubbleVisible = true
    public var crashReportingEnabled = false
    public var freeTierProviders: Set<String> = []
    public var activeKeys: [String:UUID] = [:]
    public var words: [CustomWord] = []
    public var contactNames: [String] = []
    public init() {}
    public var effectiveCleanupProvider: Provider { cleanupAutomatic ? provider : cleanupProvider }
    public var effectiveCleanupModel: String { cleanupAutomatic ? provider.defaultCleanup : cleanupModel }
    public var shouldClean: Bool { cleanupEnabled && (!cleanupAutomatic || provider.freeCleanup) }
    public var shouldSaveHistory: Bool { historyEnabled && !privateMode }
    public var bubbleDiameter: Double { bubbleSize == "Small" ? 48 : bubbleSize == "Large" ? 64 : 56 }
    public mutating func selectProvider(_ next: Provider) { provider = next; speechModel = next == .custom ? customModel : next.defaultSpeech }
    public mutating func selectCleanupProvider(_ next: Provider) {
        cleanupModelSelections[cleanupProvider.rawValue] = cleanupModel
        cleanupAutomatic = false; cleanupProvider = next
        cleanupModel = cleanupModelSelections[next.rawValue] ?? next.defaultCleanup
    }
    public mutating func setCleanupEnabled(_ enabled: Bool) {
        cleanupEnabled = enabled
        if enabled && cleanupAutomatic && !provider.freeCleanup { cleanupAutomatic = false; cleanupProvider = provider; cleanupModel = provider.defaultCleanup }
    }
    public func style(for bundleID: String?) -> WritingStyle {
        guard let id = bundleID else { return writingStyle }
        if let override = styleOverrides[id] { return override }
        if ["com.apple.mail", "com.microsoft.Outlook", "com.microsoft.Word", "com.apple.iWork.Pages", "com.linear", "com.notion.id", "com.tinyspeck.slackmacgap", "com.microsoft.teams2", "com.microsoft.teams"].contains(id) { return .formal }
        if ["com.hnc.Discord"].contains(id) { return .casual }
        if ["com.apple.MobileSMS", "net.whatsapp.WhatsApp", "org.telegram.desktop", "org.whispersystems.signal-desktop", "com.apple.Notes", "com.apple.Safari", "com.google.Chrome", "org.mozilla.firefox", "com.microsoft.edgemac"].contains(id) { return .neutral }
        return writingStyle
    }
    public mutating func normalize() {
        sessionCapSeconds = min(300, max(30, sessionCapSeconds))
        if ![60,1440,10080,43200,0].contains(historyRetentionMinutes) { historyRetentionMinutes = 60 }
        if !["Small","Medium","Large"].contains(bubbleSize) { bubbleSize = "Medium" }
        if !["Match system","Light","Dark"].contains(appTheme) { appTheme = "Match system" }
        if speechModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && provider != .custom { speechModel = provider.defaultSpeech }
    }
}

public enum Endpoint {
    public static func validated(_ value: String) throws -> URL {
        guard let parts = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines)), parts.scheme == "https", let host = parts.host, !host.isEmpty, parts.user == nil, parts.password == nil, parts.query == nil, parts.fragment == nil, let url = parts.url else { throw QuillError.invalidEndpoint }
        return url
    }
}
public enum QuillError: Error, Equatable, Sendable {
    case badKey, quota, rateLimit, network, timeout, emptyTranscript, invalidEndpoint, invalidModel, responseTooLarge, provider(Int), noMicrophone, noAccessibility, sensitiveField, focusChanged, cancelled, storage
    public var copyKey: String {
        switch self {
        case .badKey: return "mac.error.bad_key"
        case .quota: return "mac.error.quota"
        case .rateLimit: return "mac.error.rate_limit"
        case .network: return "mac.error.network"
        case .timeout: return "mac.error.timeout"
        case .invalidEndpoint: return "mac.error.endpoint"
        case .invalidModel: return "mac.error.model"
        case .noMicrophone: return "mac.error.microphone"
        case .noAccessibility: return "mac.error.accessibility"
        case .sensitiveField: return "mac.error.sensitive"
        case .focusChanged: return "mac.error.focus_changed"
        case .storage: return "mac.error.storage"
        case .cancelled: return "mac.status.cancelled"
        default: return "mac.error.provider"
        }
    }
}
