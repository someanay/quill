import Foundation

// Faithful port of windows/src/Quill.Windows/Core/Models.cs. Same enums, defaults, provider catalog,
// and normalization policy so the macOS and Windows apps behave identically.

enum DictationState: String, Codable { case idle, listening, transcribing, cleaning, inserting, error }

enum SttProvider: String, Codable, CaseIterable {
    case groq, openAI, openRouter, gemini, custom
    var displayName: String { ProviderCatalog.all[self]!.displayName }
}

enum WritingStyle: String, Codable, CaseIterable { case formal, neutral, casual }

enum AppTheme: String, Codable { case dark, light }

struct WordReplacement: Codable, Equatable, Identifiable {
    var heard: String
    var replacement: String
    var id: String { heard + "\u{0}" + replacement }
}

struct AppSettings: Codable, Equatable {
    var onboardingComplete = false
    var theme: AppTheme = .dark
    var provider: SttProvider = .groq
    var model = "whisper-large-v3-turbo"
    var language = "en"
    var customBaseURL = ""
    /// Empty follows the system default input device (mirrors the Windows blank-device contract).
    var inputDeviceUID = ""
    var cleanupEnabled = false
    var cleanupModel = "llama-3.3-70b-versatile"
    var historyEnabled = true
    var privateMode = false
    var historyRetentionMinutes = 60
    var sessionCapSeconds = 300
    var silenceAutoStopSeconds = 2
    var defaultWritingStyle: WritingStyle = .neutral
    var hotkey = "Alt+Q"
    var startAtLogin = true
    var customWords: [WordReplacement] = []
    var perAppStyles: [String: WritingStyle] = [:]
    /// Providers (keyed by raw id) whose stored key was confirmed to lack their paid-only STT models.
    /// String keys (not [SttProvider:…]) so JSONEncoder emits a clean object, not a k/v array.
    var freeTierOnlyProviders: [String: Bool] = [:]

    // Tolerant decode: a partial/older settings file falls back to defaults per field (like the
    // Windows JsonSerializer with PropertyNameCaseInsensitive + defaults).
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func b(_ k: CodingKeys, _ d: Bool) -> Bool { (try? c.decodeIfPresent(Bool.self, forKey: k)) ?? nil ?? d }
        func s(_ k: CodingKeys, _ d: String) -> String { (try? c.decodeIfPresent(String.self, forKey: k)) ?? nil ?? d }
        func i(_ k: CodingKeys, _ d: Int) -> Int { (try? c.decodeIfPresent(Int.self, forKey: k)) ?? nil ?? d }
        onboardingComplete = b(.onboardingComplete, false)
        theme = (try? c.decodeIfPresent(AppTheme.self, forKey: .theme)) ?? nil ?? .dark
        provider = (try? c.decodeIfPresent(SttProvider.self, forKey: .provider)) ?? nil ?? .groq
        model = s(.model, "whisper-large-v3-turbo")
        language = s(.language, "en")
        customBaseURL = s(.customBaseURL, "")
        inputDeviceUID = s(.inputDeviceUID, "")
        cleanupEnabled = b(.cleanupEnabled, false)
        cleanupModel = s(.cleanupModel, "llama-3.3-70b-versatile")
        historyEnabled = b(.historyEnabled, true)
        privateMode = b(.privateMode, false)
        historyRetentionMinutes = i(.historyRetentionMinutes, 60)
        sessionCapSeconds = i(.sessionCapSeconds, 300)
        silenceAutoStopSeconds = i(.silenceAutoStopSeconds, 2)
        defaultWritingStyle = (try? c.decodeIfPresent(WritingStyle.self, forKey: .defaultWritingStyle)) ?? nil ?? .neutral
        hotkey = s(.hotkey, "Alt+Q")
        startAtLogin = b(.startAtLogin, true)
        customWords = (try? c.decodeIfPresent([WordReplacement].self, forKey: .customWords)) ?? nil ?? []
        perAppStyles = (try? c.decodeIfPresent([String: WritingStyle].self, forKey: .perAppStyles)) ?? nil ?? [:]
        freeTierOnlyProviders = (try? c.decodeIfPresent([String: Bool].self, forKey: .freeTierOnlyProviders)) ?? nil ?? [:]
    }
}

struct HistoryEntry: Codable, Identifiable, Equatable {
    var id = UUID()
    var createdAt = Date()
    var text: String
    var application: String
    var provider: SttProvider
    var wasCleaned: Bool
}

struct Transcript { let text: String }

struct TranscriptionOptions {
    let model: String
    let language: String?
    let prompt: String?
    init(model: String, language: String? = nil, prompt: String? = nil) {
        self.model = model; self.language = language; self.prompt = prompt
    }
}

struct ProviderDescriptor {
    let id: SttProvider
    let displayName: String
    let baseURL: String
    let defaultModel: String
    let models: [String]
    let keyConsoleURL: String?
    let keyConsoleFree: Bool
    init(_ id: SttProvider, _ displayName: String, _ baseURL: String, _ defaultModel: String,
         _ models: [String], keyConsoleURL: String? = nil, keyConsoleFree: Bool = false) {
        self.id = id; self.displayName = displayName; self.baseURL = baseURL
        self.defaultModel = defaultModel; self.models = models
        self.keyConsoleURL = keyConsoleURL; self.keyConsoleFree = keyConsoleFree
    }
}

enum ProviderCatalog {
    static let all: [SttProvider: ProviderDescriptor] = [
        .groq: ProviderDescriptor(.groq, "Groq", "https://api.groq.com/openai/v1",
            "whisper-large-v3-turbo", ["whisper-large-v3-turbo", "whisper-large-v3"],
            keyConsoleURL: "https://console.groq.com/keys"),
        .openAI: ProviderDescriptor(.openAI, "OpenAI", "https://api.openai.com/v1",
            "gpt-4o-mini-transcribe", ["whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"],
            keyConsoleURL: "https://platform.openai.com/api-keys"),
        .openRouter: ProviderDescriptor(.openRouter, "OpenRouter", "https://openrouter.ai/api/v1",
            "openai/whisper-large-v3-turbo",
            ["openai/whisper-large-v3-turbo", "mistralai/voxtral-small-24b-2507", "openai/gpt-audio-mini",
             "openai/gpt-audio", "google/gemini-3.5-flash", "google/gemini-3.1-flash-lite"],
            keyConsoleURL: "https://openrouter.ai/keys"),
        .gemini: ProviderDescriptor(.gemini, "Gemini", "https://generativelanguage.googleapis.com",
            "gemini-3.1-flash-lite",
            ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"],
            keyConsoleURL: "https://aistudio.google.com/apikey", keyConsoleFree: true),
        .custom: ProviderDescriptor(.custom, "Custom", "", "", []),
    ]

    static func isPaidOnly(_ provider: SttProvider, _ model: String) -> Bool {
        provider == .gemini && model.caseInsensitiveCompare("gemini-3.1-pro-preview") == .orderedSame
    }
    static func firstPaidOnlyModel(_ provider: SttProvider) -> String? {
        provider == .gemini ? "gemini-3.1-pro-preview" : nil
    }
}

enum CleanupModelCatalog {
    static let models: [SttProvider: [String]] = [
        .groq: ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
        .openAI: ["gpt-4o-mini", "gpt-4.1-mini"],
        .openRouter: ["openai/gpt-4o-mini", "openai/gpt-4.1-mini"],
    ]
    static func supports(_ p: SttProvider) -> Bool { models[p] != nil }
    static func forProvider(_ p: SttProvider) -> [String] { models[p] ?? [] }
    static func defaultFor(_ p: SttProvider) -> String { forProvider(p).first ?? "" }
}

struct RetentionChoice: Hashable { let minutes: Int; let label: String }
struct SilenceChoice: Hashable { let seconds: Int; let label: String }

enum SettingsPolicy {
    static let defaultHotkey = "Alt+Q"
    static let legacyDefaultHotkey = "Ctrl+Alt+Space"

    static let retentionChoices = [
        RetentionChoice(minutes: 10, label: "10 minutes"),
        RetentionChoice(minutes: 60, label: "1 hour"),
        RetentionChoice(minutes: 24 * 60, label: "1 day"),
        RetentionChoice(minutes: 7 * 24 * 60, label: "7 days"),
        RetentionChoice(minutes: 30 * 24 * 60, label: "30 days"),
        RetentionChoice(minutes: 0, label: "Forever"),
    ]
    static let silenceChoices = [
        SilenceChoice(seconds: 0, label: "Never"), SilenceChoice(seconds: 1, label: "1 second"),
        SilenceChoice(seconds: 2, label: "2 seconds"), SilenceChoice(seconds: 5, label: "5 seconds"),
        SilenceChoice(seconds: 10, label: "10 seconds"), SilenceChoice(seconds: 15, label: "15 seconds"),
        SilenceChoice(seconds: 30, label: "30 seconds"),
    ]

    static func normalize(_ s: AppSettings) -> AppSettings {
        var out = s
        let descriptor = ProviderCatalog.all[s.provider]!
        var model = s.model.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.provider != .custom &&
            (model.isEmpty || !descriptor.models.contains(where: { $0.caseInsensitiveCompare(model) == .orderedSame })) {
            model = descriptor.defaultModel
        }
        if s.freeTierOnlyProviders[s.provider.rawValue] == true && ProviderCatalog.isPaidOnly(s.provider, model) {
            model = descriptor.defaultModel
        }
        out.model = model
        out.inputDeviceUID = s.inputDeviceUID.trimmingCharacters(in: .whitespacesAndNewlines)

        let hotkeyTrimmed = s.hotkey.trimmingCharacters(in: .whitespacesAndNewlines)
        out.hotkey = (hotkeyTrimmed.isEmpty || hotkeyTrimmed.caseInsensitiveCompare(legacyDefaultHotkey) == .orderedSame)
            ? defaultHotkey : hotkeyTrimmed

        out.sessionCapSeconds = s.sessionCapSeconds <= 0
            ? 0 : min(600, max(15, Int((Double(s.sessionCapSeconds) / 15.0).rounded()) * 15))
        out.historyRetentionMinutes = retentionChoices.contains(where: { $0.minutes == s.historyRetentionMinutes })
            ? s.historyRetentionMinutes : 60
        out.silenceAutoStopSeconds = silenceChoices.contains(where: { $0.seconds == s.silenceAutoStopSeconds })
            ? s.silenceAutoStopSeconds : 2

        let cleanupModels = CleanupModelCatalog.forProvider(s.provider)
        let stored = s.cleanupModel.trimmingCharacters(in: .whitespacesAndNewlines)
        out.cleanupModel = cleanupModels.contains(where: { $0.caseInsensitiveCompare(stored) == .orderedSame })
            ? stored : CleanupModelCatalog.defaultFor(s.provider)
        out.cleanupEnabled = s.cleanupEnabled && CleanupModelCatalog.supports(s.provider)
        return out
    }
}
