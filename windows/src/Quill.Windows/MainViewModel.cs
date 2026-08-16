using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media;
using Quill.Windows.Core;
using Quill.Windows.Platform;
using Quill.Windows.Stt;
using Quill.Windows.Audio;

namespace Quill.Windows;

public sealed class MainViewModel : INotifyPropertyChanged
{
    private readonly AppServices _services;
    private AppSettings _loaded = new();
    private bool _initializing;
    private SttProvider _selectedProvider;
    private string _model = "";
    private string _language = "en";
    private string _customBaseUrl = "";
    private string _inputDeviceId = InputDeviceCatalog.SystemDefaultId;
    private WritingStyle _defaultWritingStyle = WritingStyle.Neutral;
    private string _customWordsText = "";
    private string _statusTitle = "Ready";
    private string _statusDetail = $"Press {SettingsPolicy.DefaultHotkey} to dictate.";
    private DictationState _state;
    private bool _cleanupEnabled;
    private string _cleanupModel = "";
    private int _silenceAutoStopSeconds = 2;
    private bool _historyEnabled = true;
    private bool _privateMode;
    private bool _startWithWindows = true;
    private bool _minimizeToTray = true;
    private int _sessionCapSeconds = 300;
    private int _historyRetentionMinutes = 60;
    private AppTheme _theme = AppTheme.Dark;
    private string _keyStatus = "Keys are encrypted for your Windows account.";
    private bool _keyStatusIsValid;
    private string _hotkey = SettingsPolicy.DefaultHotkey;
    private string _hotkeyStatus = "Checking shortcut availability...";

    public MainViewModel(AppServices services)
    {
        _services = services;
        _services.Dictation.StatusChanged += OnStatusChanged;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    public IReadOnlyList<SttProvider> Providers { get; } = Enum.GetValues<SttProvider>();
    public IReadOnlyList<WritingStyle> WritingStyles { get; } = Enum.GetValues<WritingStyle>();
    public IReadOnlyList<AppTheme> Themes { get; } = Enum.GetValues<AppTheme>();
    public IReadOnlyList<string> LanguageChoices { get; } = ["en", "auto", "hi"];
    public IReadOnlyList<RetentionChoice> RetentionChoices { get; } = SettingsPolicy.RetentionChoices;
    public IReadOnlyList<SilenceChoice> SilenceChoices { get; } = SettingsPolicy.SilenceChoices;
    public ObservableCollection<HistoryEntry> History { get; } = [];
    public ObservableCollection<WordReplacement> CustomWords { get; } = [];
    public ObservableCollection<AudioInputDevice> InputDevices { get; } = [];

    public SttProvider SelectedProvider { get => _selectedProvider; private set => Set(ref _selectedProvider, value); }
    public string Model { get => _model; set => Set(ref _model, value); }
    public string Language { get => _language; set => Set(ref _language, value); }
    public string CustomBaseUrl { get => _customBaseUrl; set => Set(ref _customBaseUrl, value); }
    public string InputDeviceId { get => _inputDeviceId; set => Set(ref _inputDeviceId, value); }
    public WritingStyle DefaultWritingStyle { get => _defaultWritingStyle; set => Set(ref _defaultWritingStyle, value); }
    public string CustomWordsText { get => _customWordsText; set => Set(ref _customWordsText, value); }
    public bool CleanupEnabled { get => _cleanupEnabled; set => Set(ref _cleanupEnabled, value); }
    public string CleanupModel { get => _cleanupModel; set => Set(ref _cleanupModel, value); }
    public int SilenceAutoStopSeconds { get => _silenceAutoStopSeconds; set => Set(ref _silenceAutoStopSeconds, value); }
    public bool HistoryEnabled { get => _historyEnabled; set => Set(ref _historyEnabled, value); }
    public bool PrivateMode
    {
        get => _privateMode;
        set
        {
            if (!Set(ref _privateMode, value)) return;
            Set(ref _historyEnabled, !value, nameof(HistoryEnabled));
            OnPropertyChanged(nameof(HistoryRetentionEnabled));
        }
    }
    public bool HistoryRetentionEnabled => !PrivateMode;
    public bool StartWithWindows { get => _startWithWindows; set => Set(ref _startWithWindows, value); }
    public bool MinimizeToTray { get => _minimizeToTray; set => Set(ref _minimizeToTray, value); }
    public int SessionCapSeconds
    {
        get => _sessionCapSeconds;
        set
        {
            if (!Set(ref _sessionCapSeconds, value)) return;
            OnPropertyChanged(nameof(SessionCapSliderValue));
            OnPropertyChanged(nameof(SessionCapLabel));
        }
    }
    public int HistoryRetentionMinutes { get => _historyRetentionMinutes; set => Set(ref _historyRetentionMinutes, Math.Max(0, value)); }
    public AppTheme Theme { get => _theme; set => Set(ref _theme, value); }
    public string KeyStatus { get => _keyStatus; private set => Set(ref _keyStatus, value); }
    public bool KeyStatusIsValid { get => _keyStatusIsValid; private set => Set(ref _keyStatusIsValid, value); }
    public bool KeyStatusIsSaved => _services.Secrets.Has(SelectedProvider);
    public bool ProviderKeyLinkAvailable => ProviderCatalog.All[SelectedProvider].KeyConsoleUrl is not null;
    public string ProviderKeyLinkText => ProviderKeyLinkAvailable
        ? $"Open {ProviderName} key console"
        : "Use your provider dashboard";
    public string ProviderKeyLinkLabel => ProviderKeyLinkAvailable
        ? $"Get a {ProviderName} key ↗"
        : "Custom provider — use its dashboard";
    public string StatusTitle { get => _statusTitle; private set => Set(ref _statusTitle, value); }
    public string StatusDetail { get => _statusDetail; private set => Set(ref _statusDetail, value); }
    public string Hotkey { get => _hotkey; set => Set(ref _hotkey, value); }
    public string HotkeyStatus { get => _hotkeyStatus; private set => Set(ref _hotkeyStatus, value); }
    public void SetHotkeyStatus(string status) => HotkeyStatus = status;
    public IReadOnlyList<string> ModelChoices => ProviderCatalog.All[SelectedProvider].Models
        .Where(model => !IsFreeTierOnly(SelectedProvider) || !ProviderCatalog.IsPaidOnly(SelectedProvider, model))
        .ToArray();
    public bool IsCustomProvider => SelectedProvider == SttProvider.Custom;
    public bool ModelAllowsFreeText => SelectedProvider is SttProvider.OpenRouter or SttProvider.Custom;
    public IReadOnlyList<string> CleanupModelChoices => CleanupModelCatalog.For(SelectedProvider);
    public bool CleanupAvailable => CleanupModelCatalog.Supports(SelectedProvider);
    public string ProviderName => ProviderCatalog.All[SelectedProvider].DisplayName;
    public string CleanupHelp => SelectedProvider is SttProvider.Groq or SttProvider.OpenAI or SttProvider.OpenRouter
        ? $"Uses the same {ProviderName} key—no second provider key required."
        : "Cleanup is unavailable for this provider; transcription still works normally.";
    public int SessionCapSliderValue
    {
        get => SessionCapSeconds <= 0 ? SettingsPolicy.UncappedSliderValue : SessionCapSeconds;
        set
        {
            var seconds = value >= SettingsPolicy.UncappedSliderValue ? 0 : Math.Clamp(value, 15, 600);
            if (Set(ref _sessionCapSeconds, seconds, nameof(SessionCapSeconds)))
            {
                OnPropertyChanged();
                OnPropertyChanged(nameof(SessionCapLabel));
            }
        }
    }
    public string SessionCapLabel => SessionCapSeconds <= 0 ? "Uncapped" :
        SessionCapSeconds < 60 ? $"{SessionCapSeconds} sec" :
        SessionCapSeconds % 60 == 0 ? $"{SessionCapSeconds / 60} min" : $"{SessionCapSeconds / 60} min {SessionCapSeconds % 60} sec";
    public string ActionLabel => _state == DictationState.Listening ? "Finish dictation" : "Start dictation";
    public int TodayCount => History.Count(it => it.CreatedAt.LocalDateTime.Date == DateTime.Today);
    public Visibility HistoryEmptyVisibility => History.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    public Visibility HistoryListVisibility => History.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
    public System.Windows.Media.Brush StateBrush => _state switch
    {
        DictationState.Listening => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Signal"),
        DictationState.Error => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Error"),
        DictationState.Idle => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Brand"),
        _ => new SolidColorBrush(Color.FromRgb(91, 91, 214)),
    };

    public async Task InitializeAsync()
    {
        _initializing = true;
        _loaded = await _services.Settings.LoadAsync();
        SelectedProvider = _loaded.Provider;
        Model = ModelFor(_loaded.Provider, _loaded.Model);
        Language = _loaded.Language;
        CustomBaseUrl = _loaded.CustomBaseUrl;
        InputDeviceId = _loaded.InputDeviceId;
        RefreshInputDevices();
        DefaultWritingStyle = _loaded.DefaultWritingStyle;
        CustomWords.Clear();
        foreach (var word in _loaded.CustomWords) CustomWords.Add(word);
        CustomWordsText = string.Join(Environment.NewLine, _loaded.CustomWords.Select(word => $"{word.Heard} => {word.Replacement}"));
        CleanupEnabled = _loaded.CleanupEnabled;
        CleanupModel = _loaded.CleanupModel;
        SilenceAutoStopSeconds = SettingsPolicy.SilenceChoices.Any(choice => choice.Seconds == _loaded.SilenceAutoStopSeconds)
            ? _loaded.SilenceAutoStopSeconds : 2;
        PrivateMode = _loaded.PrivateMode || !_loaded.HistoryEnabled;
        StartWithWindows = _loaded.StartWithWindows;
        MinimizeToTray = _loaded.MinimizeToTray;
        SessionCapSeconds = _loaded.SessionCapSeconds;
        HistoryRetentionMinutes = _loaded.HistoryRetentionMinutes;
        Theme = _loaded.Theme;
        Hotkey = _loaded.Hotkey;
        StatusDetail = $"Press {Hotkey} to dictate.";
        ThemeService.Apply(Theme);
        await ReloadHistoryAsync();
        KeyStatus = _services.Secrets.Has(SelectedProvider)
            ? $"A {ProviderName} key is stored securely. Save it again to verify it."
            : $"No {ProviderName} key saved yet.";
        KeyStatusIsValid = false;
        OnPropertyChanged(nameof(Hotkey));
        OnPropertyChanged(nameof(ProviderName));
        OnPropertyChanged(nameof(ModelChoices));
        OnPropertyChanged(nameof(IsCustomProvider));
        OnPropertyChanged(nameof(ModelAllowsFreeText));
        OnPropertyChanged(nameof(HistoryRetentionEnabled));
        OnPropertyChanged(nameof(CleanupHelp));
        OnPropertyChanged(nameof(CleanupModelChoices));
        OnPropertyChanged(nameof(CleanupAvailable));
        OnPropertyChanged(nameof(KeyStatusIsSaved));
        OnPropertyChanged(nameof(ProviderKeyLinkAvailable));
        OnPropertyChanged(nameof(ProviderKeyLinkText));
        OnPropertyChanged(nameof(ProviderKeyLinkLabel));
        _initializing = false;
    }

    public async Task SelectProviderAsync(SttProvider provider)
    {
        if (SelectedProvider == provider && !_initializing) return;
        SelectedProvider = provider;
        var selectedModel = ProviderCatalog.All[provider].DefaultModel;
        var selectedCleanupModel = CleanupModelCatalog.DefaultFor(provider);
        Model = selectedModel;
        CleanupModel = selectedCleanupModel;
        if (!CleanupModelCatalog.Supports(provider)) CleanupEnabled = false;
        KeyStatus = _services.Secrets.Has(provider)
            ? $"A {ProviderCatalog.All[provider].DisplayName} key is stored securely. Save it again to verify it."
            : $"No {ProviderCatalog.All[provider].DisplayName} key saved yet.";
        KeyStatusIsValid = false;
        OnPropertyChanged(nameof(ProviderName));
        OnPropertyChanged(nameof(ModelChoices));
        OnPropertyChanged(nameof(IsCustomProvider));
        OnPropertyChanged(nameof(ModelAllowsFreeText));
        OnPropertyChanged(nameof(CleanupHelp));
        OnPropertyChanged(nameof(CleanupModelChoices));
        OnPropertyChanged(nameof(CleanupAvailable));
        OnPropertyChanged(nameof(KeyStatusIsSaved));
        OnPropertyChanged(nameof(ProviderKeyLinkAvailable));
        OnPropertyChanged(nameof(ProviderKeyLinkText));
        OnPropertyChanged(nameof(ProviderKeyLinkLabel));
        if (!_initializing)
        {
            // Persist the captured provider defaults, not the transient ComboBox value.
            _loaded = _loaded with { Provider = provider, Model = selectedModel, CleanupModel = selectedCleanupModel, CleanupEnabled = CleanupEnabled };
            await _services.Settings.SaveAsync(_loaded);
        }
    }

    public async Task SaveKeyAsync(string key)
    {
        if (string.IsNullOrWhiteSpace(key))
        {
            _services.Secrets.Delete(SelectedProvider);
            KeyStatus = "Key removed.";
            KeyStatusIsValid = false;
            OnPropertyChanged(nameof(KeyStatusIsSaved));
            return;
        }

        KeyStatus = $"Testing the {ProviderName} key with a silent audio check...";
        KeyStatusIsValid = false;
        var validation = await _services.KeyValidator.ValidateAsync(SelectedProvider, key, Model, CustomBaseUrl);
        switch (validation)
        {
            case KeyValidation.Rejected:
                _services.Secrets.Delete(SelectedProvider);
                KeyStatus = "Key rejected - check the provider and paste a new key.";
                break;
            case KeyValidation.Valid:
                _services.Secrets.Set(SelectedProvider, key);
                KeyStatusIsValid = true;
                KeyStatus = $"Key verified with {ProviderName}.";
                await ProbePaidAccessAsync(key);
                break;
            case KeyValidation.Unverified unverified:
                _services.Secrets.Set(SelectedProvider, key);
                KeyStatus = $"Key saved, but not verified: {unverified.Reason}.";
                break;
        }
        _loaded = _loaded with { Provider = SelectedProvider, Model = Model.Trim(), Language = Language.Trim(), CustomBaseUrl = CustomBaseUrl.Trim() };
        await _services.Settings.SaveAsync(_loaded);
        OnPropertyChanged(nameof(KeyStatusIsSaved));
        OnPropertyChanged(nameof(ModelChoices));
    }

    private async Task ProbePaidAccessAsync(string key)
    {
        var paidModel = ProviderCatalog.FirstPaidOnlyModel(SelectedProvider);
        if (paidModel is null) return;
        var available = await _services.KeyValidator.HasPaidAccessAsync(SelectedProvider, key, paidModel, CustomBaseUrl);
        if (available is null) return;
        var restrictions = new Dictionary<SttProvider, bool>(_loaded.FreeTierOnlyProviders) { [SelectedProvider] = !available.Value };
        _loaded = _loaded with { FreeTierOnlyProviders = restrictions };
        if (!available.Value && ProviderCatalog.IsPaidOnly(SelectedProvider, Model))
            Model = ProviderCatalog.All[SelectedProvider].DefaultModel;
    }

    private bool IsFreeTierOnly(SttProvider provider) => _loaded.FreeTierOnlyProviders.TryGetValue(provider, out var restricted) && restricted;

    public async Task SaveSettingsAsync()
    {
        _loaded = _loaded with
        {
            Provider = SelectedProvider,
            Model = Model.Trim(),
            Language = Language.Trim(),
            CustomBaseUrl = CustomBaseUrl.Trim(),
            InputDeviceId = InputDeviceId,
            DefaultWritingStyle = DefaultWritingStyle,
            CustomWords = CustomWords.ToArray(),
            CleanupEnabled = CleanupEnabled,
            CleanupModel = CleanupModel,
            SilenceAutoStopSeconds = SilenceAutoStopSeconds,
            HistoryEnabled = HistoryEnabled,
            PrivateMode = PrivateMode,
            StartWithWindows = StartWithWindows,
            MinimizeToTray = MinimizeToTray,
            SessionCapSeconds = SessionCapSeconds,
            HistoryRetentionMinutes = HistoryRetentionMinutes,
            Theme = Theme,
            Hotkey = Hotkey.Trim(),
        };
        await _services.Settings.SaveAsync(_loaded);
        ThemeService.Apply(Theme);
        StartupService.SetEnabled(StartWithWindows);
        StatusTitle = "Settings saved";
        StatusDetail = "The global shortcut is ready.";
        OnPropertyChanged(nameof(Hotkey));
        OnPropertyChanged(nameof(ProviderName));
    }

    public async Task ToggleAsync()
    {
        await _services.Dictation.ToggleAsync();
        if (_services.Dictation.State == DictationState.Idle) await ReloadHistoryAsync();
    }

    public async Task ToggleClipboardAsync() => await _services.Dictation.ToggleClipboardAsync();

    public void RefreshInputDevices()
    {
        var devices = InputDeviceCatalog.Enumerate();
        InputDevices.Clear();
        foreach (var device in devices) InputDevices.Add(device);
        if (!InputDevices.Any(device => device.Id.Equals(InputDeviceId, StringComparison.OrdinalIgnoreCase)))
            InputDeviceId = InputDeviceCatalog.SystemDefaultId;
    }

    public async Task ClearHistoryAsync()
    {
        await _services.History.ClearAsync();
        History.Clear();
        OnPropertyChanged(nameof(TodayCount));
        OnPropertyChanged(nameof(HistoryEmptyVisibility));
        OnPropertyChanged(nameof(HistoryListVisibility));
    }

    public void AddCustomWord(string heard, string replacement)
    {
        heard = heard.Trim();
        replacement = replacement.Trim();
        if (heard.Length == 0 || replacement.Length == 0) return;
        var existing = CustomWords.FirstOrDefault(word => word.Heard.Equals(heard, StringComparison.OrdinalIgnoreCase));
        if (existing is not null) CustomWords.Remove(existing);
        CustomWords.Add(new WordReplacement(heard, replacement));
        CustomWordsText = string.Join(Environment.NewLine, CustomWords.Select(word => $"{word.Heard} => {word.Replacement}"));
    }

    public void RemoveCustomWord(WordReplacement word)
    {
        if (!CustomWords.Remove(word)) return;
        CustomWordsText = string.Join(Environment.NewLine, CustomWords.Select(item => $"{item.Heard} => {item.Replacement}"));
    }

    private async Task ReloadHistoryAsync()
    {
        var entries = await _services.History.ReadAsync(_loaded.HistoryRetentionMinutes);
        History.Clear();
        foreach (var entry in entries) History.Add(entry);
        OnPropertyChanged(nameof(TodayCount));
        OnPropertyChanged(nameof(HistoryEmptyVisibility));
        OnPropertyChanged(nameof(HistoryListVisibility));
    }

    private static string KeyArticle(SttProvider provider) => provider is SttProvider.OpenAI or SttProvider.OpenRouter ? "An" : "A";

    private static string ModelFor(SttProvider provider, string? stored)
    {
        var descriptor = ProviderCatalog.All[provider];
        var candidate = stored?.Trim() ?? "";
        if (provider == SttProvider.Custom) return candidate;
        return descriptor.Models.Contains(candidate, StringComparer.OrdinalIgnoreCase)
            ? candidate
            : descriptor.DefaultModel;
    }

    private void OnStatusChanged(object? sender, DictationStatus status)
    {
        System.Windows.Application.Current.Dispatcher.InvokeAsync(async () =>
        {
            _state = status.State;
            StatusTitle = status.Title;
            StatusDetail = status.Detail;
            OnPropertyChanged(nameof(ActionLabel));
            OnPropertyChanged(nameof(StateBrush));
            if (status.State == DictationState.Idle) await ReloadHistoryAsync();
        });
    }

    private bool Set<T>(ref T field, T value, [CallerMemberName] string? property = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(property);
        return true;
    }

    private void OnPropertyChanged([CallerMemberName] string? property = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(property));
}
