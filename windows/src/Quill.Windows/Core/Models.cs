using System.Text.Json.Serialization;

namespace Quill.Windows.Core;

public enum DictationState { Idle, Listening, Transcribing, Cleaning, Inserting, Error }

public enum SttProvider { Groq, OpenAI, OpenRouter, Gemini, Custom }

public enum WritingStyle { Formal, Neutral, Casual }

public enum AppTheme { Dark, Light }

public sealed record AppSettings
{
    public bool OnboardingComplete { get; init; }
    public AppTheme Theme { get; init; } = AppTheme.Dark;
    public SttProvider Provider { get; init; } = SttProvider.Groq;
    public string Model { get; init; } = "whisper-large-v3-turbo";
    public string Language { get; init; } = "en";
    public string CustomBaseUrl { get; init; } = "";
    /// <summary>NAudio WaveIn device identifier; blank intentionally follows the Windows default.</summary>
    public string InputDeviceId { get; init; } = "";
    public bool CleanupEnabled { get; init; }
    public string CleanupModel { get; init; } = "llama-3.3-70b-versatile";
    public bool HistoryEnabled { get; init; } = true;
    public bool PrivateMode { get; init; }
    public int HistoryRetentionMinutes { get; init; } = 60;
    public int SessionCapSeconds { get; init; } = 300;
    public int SilenceAutoStopSeconds { get; init; } = 2;
    public WritingStyle DefaultWritingStyle { get; init; } = WritingStyle.Neutral;
    public string Hotkey { get; init; } = "Alt+Q";
    public bool StartWithWindows { get; init; } = true;
    public bool MinimizeToTray { get; init; } = true;
    public IReadOnlyList<WordReplacement> CustomWords { get; init; } = [];
    public IReadOnlyDictionary<string, WritingStyle> PerAppStyles { get; init; } =
        new Dictionary<string, WritingStyle>(StringComparer.OrdinalIgnoreCase);
    /// <summary>Providers whose stored key was confirmed to lack their paid-only STT models.</summary>
    public IReadOnlyDictionary<SttProvider, bool> FreeTierOnlyProviders { get; init; } =
        new Dictionary<SttProvider, bool>();
}

public sealed record WordReplacement(string Heard, string Replacement);

public sealed record HistoryEntry(
    Guid Id,
    DateTimeOffset CreatedAt,
    string Text,
    string Application,
    SttProvider Provider,
    bool WasCleaned);

public sealed record Transcript(string Text);

public sealed record TranscriptionOptions(string Model, string? Language, string? Prompt = null);

public sealed record ProviderDescriptor(
    SttProvider Id,
    string DisplayName,
    string BaseUrl,
    string DefaultModel,
    IReadOnlyList<string> Models,
    string? KeyConsoleUrl = null,
    bool KeyConsoleFree = false);

public sealed record RetentionChoice(int Minutes, string Label)
{
    public override string ToString() => Label;
}

public sealed record SilenceChoice(int Seconds, string Label)
{
    public override string ToString() => Label;
}

public static class ProviderCatalog
{
    public static readonly IReadOnlyDictionary<SttProvider, ProviderDescriptor> All =
        new Dictionary<SttProvider, ProviderDescriptor>
        {
            [SttProvider.Groq] = new(SttProvider.Groq, "Groq", "https://api.groq.com/openai/v1",
                "whisper-large-v3-turbo", ["whisper-large-v3-turbo", "whisper-large-v3"],
                "https://console.groq.com/keys"),
            [SttProvider.OpenAI] = new(SttProvider.OpenAI, "OpenAI", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", ["whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"],
                "https://platform.openai.com/api-keys"),
            [SttProvider.OpenRouter] = new(SttProvider.OpenRouter, "OpenRouter", "https://openrouter.ai/api/v1",
                "openai/whisper-large-v3-turbo",
                ["openai/whisper-large-v3-turbo", "mistralai/voxtral-small-24b-2507", "openai/gpt-audio-mini", "openai/gpt-audio", "google/gemini-3.5-flash", "google/gemini-3.1-flash-lite", "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "xiaomi/mimo-v2.5"],
                "https://openrouter.ai/keys"),
            [SttProvider.Gemini] = new(SttProvider.Gemini, "Gemini", "https://generativelanguage.googleapis.com",
                "gemini-3.1-flash-lite", ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"],
                "https://aistudio.google.com/apikey", true),
            [SttProvider.Custom] = new(SttProvider.Custom, "Custom", "", "", []),
        };

    public static bool IsPaidOnly(SttProvider provider, string model) =>
        provider == SttProvider.Gemini && model.Equals("gemini-3.1-pro-preview", StringComparison.OrdinalIgnoreCase);

    public static string? FirstPaidOnlyModel(SttProvider provider) => provider == SttProvider.Gemini
        ? "gemini-3.1-pro-preview"
        : null;
}

public static class CleanupModelCatalog
{
    public static readonly IReadOnlyDictionary<SttProvider, IReadOnlyList<string>> Models =
        new Dictionary<SttProvider, IReadOnlyList<string>>
        {
            [SttProvider.Groq] = ["llama-3.3-70b-versatile", "llama-3.1-8b-instant"],
            [SttProvider.OpenAI] = ["gpt-4o-mini", "gpt-4.1-mini"],
            [SttProvider.OpenRouter] = ["openai/gpt-4o-mini", "openai/gpt-4.1-mini"],
        };

    public static bool Supports(SttProvider provider) => Models.ContainsKey(provider);
    public static IReadOnlyList<string> For(SttProvider provider) =>
        Models.TryGetValue(provider, out var models) ? models : [];
    public static string DefaultFor(SttProvider provider) => For(provider).FirstOrDefault() ?? "";
}

public static class SettingsPolicy
{
    public const string DefaultHotkey = "Alt+Q";
    public const string LegacyDefaultHotkey = "Ctrl+Alt+Space";
    // Keep one empty snap interval after 10 minutes so Uncapped is easy to select.
    public const int UncappedSliderValue = 630;

    public static readonly IReadOnlyList<RetentionChoice> RetentionChoices =
    [
        new(10, "10 minutes"),
        new(60, "1 hour"),
        new(24 * 60, "1 day"),
        new(7 * 24 * 60, "7 days"),
        new(30 * 24 * 60, "30 days"),
        new(0, "Forever"),
    ];

    public static readonly IReadOnlyList<SilenceChoice> SilenceChoices =
    [
        new(0, "Never"),
        new(1, "1 second"),
        new(2, "2 seconds"),
        new(5, "5 seconds"),
        new(10, "10 seconds"),
        new(15, "15 seconds"),
        new(30, "30 seconds"),
    ];

    public static AppSettings Normalize(AppSettings settings)
    {
        var descriptor = ProviderCatalog.All[settings.Provider];
        // ComboBox rebinding can briefly produce null while a provider changes.
        var model = settings.Model?.Trim() ?? "";
        var freeTierOnly = settings.FreeTierOnlyProviders ?? new Dictionary<SttProvider, bool>();
        if (settings.Provider != SttProvider.Custom &&
            (model.Length == 0 || !descriptor.Models.Contains(model, StringComparer.OrdinalIgnoreCase)))
            model = descriptor.DefaultModel;
        if (freeTierOnly.TryGetValue(settings.Provider, out var restricted) && restricted &&
            ProviderCatalog.IsPaidOnly(settings.Provider, model))
            model = descriptor.DefaultModel;

        var hotkey = string.IsNullOrWhiteSpace(settings.Hotkey) ||
                     settings.Hotkey.Equals(LegacyDefaultHotkey, StringComparison.OrdinalIgnoreCase)
            ? DefaultHotkey
            : settings.Hotkey.Trim();
        var sessionCap = settings.SessionCapSeconds <= 0
            ? 0
            : Math.Clamp((int)Math.Round(settings.SessionCapSeconds / 15d) * 15, 15, 600);
        var retention = RetentionChoices.Any(choice => choice.Minutes == settings.HistoryRetentionMinutes)
            ? settings.HistoryRetentionMinutes
            : 60;
        var cleanupModels = CleanupModelCatalog.For(settings.Provider);
        var storedCleanupModel = settings.CleanupModel?.Trim() ?? "";
        var cleanupModel = cleanupModels.Contains(storedCleanupModel, StringComparer.OrdinalIgnoreCase)
            ? storedCleanupModel
            : CleanupModelCatalog.DefaultFor(settings.Provider);
        var cleanupEnabled = settings.CleanupEnabled && CleanupModelCatalog.Supports(settings.Provider);
        var silence = SilenceChoices.Any(choice => choice.Seconds == settings.SilenceAutoStopSeconds)
            ? settings.SilenceAutoStopSeconds
            : 2;

        return settings with
        {
            Model = model,
            InputDeviceId = settings.InputDeviceId?.Trim() ?? "",
            CleanupModel = cleanupModel,
            CleanupEnabled = cleanupEnabled,
            Hotkey = hotkey,
            SessionCapSeconds = sessionCap,
            HistoryRetentionMinutes = retention,
            SilenceAutoStopSeconds = silence,
            FreeTierOnlyProviders = freeTierOnly,
        };
    }
}

public static class JsonDefaults
{
    public static readonly System.Text.Json.JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true,
        Converters = { new JsonStringEnumConverter() },
    };
}
