using Quill.Windows.Audio;
using System.Net.Http;
using Quill.Windows.Cleanup;
using Quill.Windows.Platform;
using Quill.Windows.Security;
using Quill.Windows.Stt;

namespace Quill.Windows.Core;

public sealed record DictationStatus(DictationState State, string Title, string Detail = "", string Toast = "", bool Clipboard = false);

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
    private bool _clipboardOnly;
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

    /// <summary>Records without a focused target and places the transcript on the clipboard.</summary>
    public async Task ToggleClipboardAsync()
    {
        if (!await _transition.WaitAsync(0)) return;
        try
        {
            if (_state == DictationState.Idle || _state == DictationState.Error)
            {
                _clipboardOnly = true;
                await StartAsync();
            }
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
            ResetPending();
            return;
        }
        _target = _clipboardOnly ? null : _injector.CaptureTarget();
        if (_target?.IsSensitive == true)
        {
            SetStatus(DictationState.Error, "Protected field", "Quill never records for or types into password fields.");
            ResetPending();
            return;
        }
        var diverted = _target is { HasEditableField: false };
        if (diverted) _clipboardOnly = true;
        try
        {
            _audio.Start(settings);
            SetStatus(DictationState.Listening,
                _clipboardOnly ? "Listening · clipboard" : "Listening",
                _clipboardOnly ? "Speak — Quill will copy the text to your clipboard." : $"Press {settings.Hotkey} again to finish.",
                diverted ? "No text field selected — this dictation will be copied to your clipboard." : "",
                _clipboardOnly);
        }
        catch (Exception error)
        {
            SetStatus(DictationState.Error, "Microphone unavailable", error.Message);
            ResetPending();
        }
    }

    /// <summary>
    /// Clear the pending target/clipboard-mode flags after a start that never reached Listening.
    /// Otherwise a failed clipboard start (e.g. no key) leaves <c>_clipboardOnly = true</c> and
    /// misroutes the *next* normal dictation to the clipboard instead of the focused field.
    /// </summary>
    private void ResetPending()
    {
        _clipboardOnly = false;
        _target = null;
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
            if (!recording.HasSpeech)
            {
                SetStatus(DictationState.Idle, "Nothing heard", "No speech was detected, so nothing was sent to your provider.");
                return;
            }
            var settings = await _settings.LoadAsync();
            var key = _secrets.Get(settings.Provider);
            if (string.IsNullOrWhiteSpace(key)) throw new TranscriptionException("The API key is missing.");
            var descriptor = ProviderCatalog.All[settings.Provider];
            var model = string.IsNullOrWhiteSpace(settings.Model) ? descriptor.DefaultModel : settings.Model;
            var client = TranscriptionClientFactory.Create(settings.Provider, key, settings.CustomBaseUrl, _http);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(45));
            var transcript = await client.TranscribeAsync(recording.Path,
                new TranscriptionOptions(model, settings.Language, TranscriptionPrompt(settings)), timeout.Token);
            var text = transcript.Text;
            var cleaned = false;
            if (settings.CleanupEnabled)
            {
                SetStatus(DictationState.Cleaning, "Tidying", "Meaning and names are guarded before insertion.");
                var style = ResolveStyle(settings, _target?.Application);
                var candidate = await _cleanup.CleanAsync(
                    text, settings.Provider, key, settings.CleanupModel, style, timeout.Token);
                cleaned = !string.Equals(candidate, text, StringComparison.Ordinal);
                text = candidate;
            }
            text = ApplyCustomWords(text, settings.CustomWords);
            SetStatus(DictationState.Inserting,
                _clipboardOnly ? "Copying" : "Typing",
                _clipboardOnly ? "Clipboard" : _target?.Application ?? "Focused application",
                clipboard: _clipboardOnly);
            var result = _clipboardOnly
                ? await CopyToClipboardAsync(text)
                : await _injector.InsertAsync(_target ?? _injector.CaptureTarget(), text);
            if (!result.Inserted && !result.CopiedToClipboard)
                throw new InvalidOperationException(result.Error ?? "Windows did not accept the text.");
            try
            {
                await _history.AddAsync(new HistoryEntry(Guid.NewGuid(), DateTimeOffset.UtcNow, text,
                    _clipboardOnly ? "Clipboard" : _target?.Application ?? "Unknown app", settings.Provider, cleaned), settings);
            }
            catch (Exception historyError) when (historyError is IOException or UnauthorizedAccessException)
            {
                // History is best-effort — a locked or unwritable store must not fail (or crash on the
                // async-void auto-stop path) a dictation whose text was already inserted above.
            }
            SetStatus(DictationState.Idle,
                result.Inserted ? "Ready" : "Copied to clipboard",
                result.Inserted ? $"Press {settings.Hotkey} to dictate." : result.Error ?? "Paste it where you need it.",
                result.Inserted ? "" : "Transcript copied — paste it with Ctrl+V.");
        }
        catch (Exception error) when (error is TranscriptionException or HttpRequestException or TaskCanceledException or InvalidOperationException or IOException or UnauthorizedAccessException)
        {
            SetStatus(DictationState.Error, "Dictation stopped", error.Message);
        }
        finally
        {
            try { File.Delete(recording.Path); } catch { /* transient audio is best-effort deleted */ }
            _target = null;
            _clipboardOnly = false;
        }
    }

    private async void OnAutoStopRequested(object? sender, EventArgs args)
    {
        if (!await _transition.WaitAsync(0)) return;
        try { if (_state == DictationState.Listening) await StopAndProcessAsync(); }
        finally { _transition.Release(); }
    }

    private void SetStatus(DictationState state, string title, string detail, string toast = "", bool clipboard = false)
    {
        _state = state;
        StatusChanged?.Invoke(this, new DictationStatus(state, title, detail, toast, clipboard));
    }

    private static async Task<InjectionResult> CopyToClipboardAsync(string text)
    {
        try
        {
            await System.Windows.Application.Current.Dispatcher.InvokeAsync(() => System.Windows.Clipboard.SetText(text));
            return new InjectionResult(false, true, "Dictation copied to your clipboard.");
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.ExternalException or InvalidOperationException)
        {
            return new InjectionResult(false, false, error.Message);
        }
    }

    private static WritingStyle ResolveStyle(AppSettings settings, string? application) =>
        application is not null && settings.PerAppStyles.TryGetValue(application, out var style)
            ? style : settings.DefaultWritingStyle;

    internal static string? TranscriptionPrompt(AppSettings settings)
    {
        var parts = new List<string>();
        var romanize = RomanizePrompt.ForLanguage(settings.Language);
        if (romanize is not null) parts.Add(romanize);
        if (settings.CustomWords.Count > 0)
            parts.Add("Preferred spellings: " + string.Join(", ", settings.CustomWords.Select(it => it.Replacement)));
        return parts.Count == 0 ? null : string.Join("\n", parts);
    }

    internal static string ApplyCustomWords(string text, IReadOnlyList<WordReplacement> words)
    {
        foreach (var word in words.Where(it => !string.IsNullOrWhiteSpace(it.Heard)))
            // MatchEvaluator (not the replacement-string overload) so the replacement is inserted
            // literally: a replacement containing '$' (e.g. "US$", "$5") would otherwise be parsed as
            // a substitution pattern ($5 = capture group 5). Mirrors Android's WordSubstitutions,
            // which uses the lambda form for the same reason.
            text = System.Text.RegularExpressions.Regex.Replace(text,
                $@"\b{System.Text.RegularExpressions.Regex.Escape(word.Heard)}\b", _ => word.Replacement,
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
