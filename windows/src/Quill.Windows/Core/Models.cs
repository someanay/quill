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
    public bool CleanupEnabled { get; init; }
    public bool HistoryEnabled { get; init; } = true;
    public bool PrivateMode { get; init; }
    public int HistoryRetentionMinutes { get; init; } = 60;
    public int SessionCapSeconds { get; init; } = 300;
    public bool SilenceAutoStop { get; init; } = true;
    public WritingStyle DefaultWritingStyle { get; init; } = WritingStyle.Neutral;
    public string Hotkey { get; init; } = "Ctrl+Alt+Space";
    public bool StartWithWindows { get; init; } = true;
    public bool MinimizeToTray { get; init; } = true;
    public IReadOnlyList<WordReplacement> CustomWords { get; init; } = [];
    public IReadOnlyDictionary<string, WritingStyle> PerAppStyles { get; init; } =
        new Dictionary<string, WritingStyle>(StringComparer.OrdinalIgnoreCase);
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
    IReadOnlyList<string> Models);

public static class ProviderCatalog
{
    public static readonly IReadOnlyDictionary<SttProvider, ProviderDescriptor> All =
        new Dictionary<SttProvider, ProviderDescriptor>
        {
            [SttProvider.Groq] = new(SttProvider.Groq, "Groq", "https://api.groq.com/openai/v1",
                "whisper-large-v3-turbo", ["whisper-large-v3-turbo", "whisper-large-v3"]),
            [SttProvider.OpenAI] = new(SttProvider.OpenAI, "OpenAI", "https://api.openai.com/v1",
                "gpt-4o-mini-transcribe", ["gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1"]),
            [SttProvider.OpenRouter] = new(SttProvider.OpenRouter, "OpenRouter", "https://openrouter.ai/api/v1",
                "openai/whisper-large-v3-turbo",
                ["openai/whisper-large-v3-turbo", "openai/whisper-large-v3", "openai/gpt-4o-mini-transcribe"]),
            [SttProvider.Gemini] = new(SttProvider.Gemini, "Gemini", "https://generativelanguage.googleapis.com",
                "gemini-3.1-flash-lite", ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash"]),
            [SttProvider.Custom] = new(SttProvider.Custom, "Custom", "", "", []),
        };
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
