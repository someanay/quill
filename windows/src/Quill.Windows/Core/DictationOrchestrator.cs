using Quill.Windows.Audio;
using System.Net.Http;
using Quill.Windows.Cleanup;
using Quill.Windows.Platform;
using Quill.Windows.Security;
using Quill.Windows.Stt;

namespace Quill.Windows.Core;

public sealed record DictationStatus(DictationState State, string Title, string Detail = "");

public sealed class DictationOrchestrator : IDisposable
{
    private readonly SettingsStore _settings;
    private readonly SecretStore _secrets;
    private readonly HistoryStore _history;
    private readonly AudioCaptureService _audio;
    private readonly FocusedTextInjector _injector;
    private readonly CleanupClient _cleanup;
    private readonly HttpClient _http;
    private readonly SemaphoreSlim _transition = new(1, 1);
    private FocusTarget? _target;
    private DictationState _state = DictationState.Idle;

    public DictationOrchestrator(
        SettingsStore settings,
        SecretStore secrets,
        HistoryStore history,
        AudioCaptureService audio,
        FocusedTextInjector injector,
        CleanupClient cleanup,
        HttpClient http)
    {
        _settings = settings;
        _secrets = secrets;
        _history = history;
        _audio = audio;
        _injector = injector;
        _cleanup = cleanup;
        _http = http;
        _audio.AutoStopRequested += OnAutoStopRequested;
    }

    public event EventHandler<DictationStatus>? StatusChanged;
    public DictationState State => _state;

    public async Task ToggleAsync()
    {
        if (!await _transition.WaitAsync(0)) return;
        try
        {
            if (_state == DictationState.Idle || _state == DictationState.Error) await StartAsync();
            else if (_state == DictationState.Listening) await StopAndProcessAsync();
        }
        finally { _transition.Release(); }
    }

    private async Task StartAsync()
    {
        var settings = await _settings.LoadAsync();
        var key = _secrets.Get(settings.Provider);
        if (string.IsNullOrWhiteSpace(key))
        {
            SetStatus(DictationState.Error, "API key required", $"Add a {ProviderCatalog.All[settings.Provider].DisplayName} key in Settings.");
            return;
        }
        _target = _injector.CaptureTarget();
        if (_target.IsSensitive)
        {
            SetStatus(DictationState.Error, "Protected field", "Quill never records for or types into password fields.");
            return;
        }
        try
        {
            _audio.Start(settings);
            SetStatus(DictationState.Listening, "Listening", $"Press {settings.Hotkey} again to finish.");
        }
        catch (Exception error)
        {
            SetStatus(DictationState.Error, "Microphone unavailable", error.Message);
        }
    }

    private async Task StopAndProcessAsync()
    {
        AudioCaptureResult recording;
        try
        {
            SetStatus(DictationState.Transcribing, "Transcribing", "Your audio goes directly to your chosen provider.");
            recording = await _audio.StopAsync();
        }
        catch (Exception error)
        {
            SetStatus(DictationState.Error, "Recording failed", error.Message);
            return;
        }

        try
        {
            var settings = await _settings.LoadAsync();
            var key = _secrets.Get(settings.Provider);
            if (string.IsNullOrWhiteSpace(key)) throw new TranscriptionException("The API key is missing.");
            var descriptor = ProviderCatalog.All[settings.Provider];
            var model = string.IsNullOrWhiteSpace(settings.Model) ? descriptor.DefaultModel : settings.Model;
            var client = TranscriptionClientFactory.Create(settings.Provider, key, settings.CustomBaseUrl, _http);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(45));
            var transcript = await client.TranscribeAsync(recording.Path,
                new TranscriptionOptions(model, settings.Language, CustomWordPrompt(settings)), timeout.Token);
            var text = transcript.Text;
            var cleaned = false;
            if (settings.CleanupEnabled)
            {
                var groqKey = _secrets.Get(SttProvider.Groq);
                if (!string.IsNullOrWhiteSpace(groqKey))
                {
                    SetStatus(DictationState.Cleaning, "Tidying", "Meaning and names are guarded before insertion.");
                    var style = ResolveStyle(settings, _target?.Application);
                    var candidate = await _cleanup.CleanAsync(text, groqKey, style, timeout.Token);
                    cleaned = !string.Equals(candidate, text, StringComparison.Ordinal);
                    text = candidate;
                }
            }
            text = ApplyCustomWords(text, settings.CustomWords);
            SetStatus(DictationState.Inserting, "Typing", _target?.Application ?? "Focused application");
            var result = await _injector.InsertAsync(_target ?? _injector.CaptureTarget(), text);
            if (!result.Inserted && !result.CopiedToClipboard)
                throw new InvalidOperationException(result.Error ?? "Windows did not accept the text.");
            await _history.AddAsync(new HistoryEntry(Guid.NewGuid(), DateTimeOffset.UtcNow, text,
                _target?.Application ?? "Unknown app", settings.Provider, cleaned), settings);
            SetStatus(DictationState.Idle,
                result.Inserted ? "Ready" : "Copied to clipboard",
                result.Inserted ? $"Press {settings.Hotkey} to dictate." : result.Error ?? "Paste it where you need it.");
        }
        catch (Exception error) when (error is TranscriptionException or HttpRequestException or TaskCanceledException or InvalidOperationException)
        {
            SetStatus(DictationState.Error, "Dictation stopped", error.Message);
        }
        finally
        {
            try { File.Delete(recording.Path); } catch { /* transient audio is best-effort deleted */ }
            _target = null;
        }
    }

    private async void OnAutoStopRequested(object? sender, EventArgs args)
    {
        if (!await _transition.WaitAsync(0)) return;
        try { if (_state == DictationState.Listening) await StopAndProcessAsync(); }
        finally { _transition.Release(); }
    }

    private void SetStatus(DictationState state, string title, string detail)
    {
        _state = state;
        StatusChanged?.Invoke(this, new DictationStatus(state, title, detail));
    }

    private static WritingStyle ResolveStyle(AppSettings settings, string? application) =>
        application is not null && settings.PerAppStyles.TryGetValue(application, out var style)
            ? style : settings.DefaultWritingStyle;

    private static string? CustomWordPrompt(AppSettings settings) => settings.CustomWords.Count == 0
        ? null : "Preferred spellings: " + string.Join(", ", settings.CustomWords.Select(it => it.Replacement));

    internal static string ApplyCustomWords(string text, IReadOnlyList<WordReplacement> words)
    {
        foreach (var word in words.Where(it => !string.IsNullOrWhiteSpace(it.Heard)))
            text = System.Text.RegularExpressions.Regex.Replace(text,
                $@"\b{System.Text.RegularExpressions.Regex.Escape(word.Heard)}\b", word.Replacement,
                System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        return text;
    }

    public void Dispose()
    {
        _audio.AutoStopRequested -= OnAutoStopRequested;
        _audio.Dispose();
        _transition.Dispose();
    }
}
