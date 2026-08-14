using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media;
using Quill.Windows.Core;
using Quill.Windows.Platform;

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
    private WritingStyle _defaultWritingStyle = WritingStyle.Neutral;
    private string _customWordsText = "";
    private string _statusTitle = "Ready";
    private string _statusDetail = "Press Ctrl+Alt+Space to dictate.";
    private DictationState _state;
    private bool _cleanupEnabled;
    private bool _silenceAutoStop = true;
    private bool _historyEnabled = true;
    private bool _privateMode;
    private bool _startWithWindows = true;
    private bool _minimizeToTray = true;
    private int _sessionCapSeconds = 300;
    private int _historyRetentionMinutes = 60;
    private AppTheme _theme = AppTheme.Dark;
    private string _keyStatus = "Keys are encrypted for your Windows account.";

    public MainViewModel(AppServices services)
    {
        _services = services;
        _services.Dictation.StatusChanged += OnStatusChanged;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    public IReadOnlyList<SttProvider> Providers { get; } = Enum.GetValues<SttProvider>();
    public IReadOnlyList<WritingStyle> WritingStyles { get; } = Enum.GetValues<WritingStyle>();
    public IReadOnlyList<AppTheme> Themes { get; } = Enum.GetValues<AppTheme>();
    public ObservableCollection<HistoryEntry> History { get; } = [];

    public SttProvider SelectedProvider { get => _selectedProvider; set => Set(ref _selectedProvider, value); }
    public string Model { get => _model; set => Set(ref _model, value); }
    public string Language { get => _language; set => Set(ref _language, value); }
    public string CustomBaseUrl { get => _customBaseUrl; set => Set(ref _customBaseUrl, value); }
    public WritingStyle DefaultWritingStyle { get => _defaultWritingStyle; set => Set(ref _defaultWritingStyle, value); }
    public string CustomWordsText { get => _customWordsText; set => Set(ref _customWordsText, value); }
    public bool CleanupEnabled { get => _cleanupEnabled; set => Set(ref _cleanupEnabled, value); }
    public bool SilenceAutoStop { get => _silenceAutoStop; set => Set(ref _silenceAutoStop, value); }
    public bool HistoryEnabled { get => _historyEnabled; set => Set(ref _historyEnabled, value); }
    public bool PrivateMode { get => _privateMode; set => Set(ref _privateMode, value); }
    public bool StartWithWindows { get => _startWithWindows; set => Set(ref _startWithWindows, value); }
    public bool MinimizeToTray { get => _minimizeToTray; set => Set(ref _minimizeToTray, value); }
    public int SessionCapSeconds { get => _sessionCapSeconds; set { if (Set(ref _sessionCapSeconds, value)) OnPropertyChanged(nameof(SessionCapLabel)); } }
    public int HistoryRetentionMinutes { get => _historyRetentionMinutes; set => Set(ref _historyRetentionMinutes, Math.Max(0, value)); }
    public AppTheme Theme { get => _theme; set => Set(ref _theme, value); }
    public string KeyStatus { get => _keyStatus; private set => Set(ref _keyStatus, value); }
    public string StatusTitle { get => _statusTitle; private set => Set(ref _statusTitle, value); }
    public string StatusDetail { get => _statusDetail; private set => Set(ref _statusDetail, value); }
    public string Hotkey => _loaded.Hotkey;
    public string ProviderName => ProviderCatalog.All[SelectedProvider].DisplayName;
    public string CleanupHelp => SelectedProvider is SttProvider.Groq or SttProvider.OpenAI or SttProvider.OpenRouter
        ? $"Uses the same {ProviderName} key—no second provider key required."
        : "Cleanup is unavailable for this provider; transcription still works normally.";
    public string SessionCapLabel => $"{SessionCapSeconds / 60} min";
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
        Model = _loaded.Model;
        Language = _loaded.Language;
        CustomBaseUrl = _loaded.CustomBaseUrl;
        DefaultWritingStyle = _loaded.DefaultWritingStyle;
        CustomWordsText = string.Join(Environment.NewLine, _loaded.CustomWords.Select(word => $"{word.Heard} => {word.Replacement}"));
        CleanupEnabled = _loaded.CleanupEnabled;
        SilenceAutoStop = _loaded.SilenceAutoStop;
        HistoryEnabled = _loaded.HistoryEnabled;
        PrivateMode = _loaded.PrivateMode;
        StartWithWindows = _loaded.StartWithWindows;
        MinimizeToTray = _loaded.MinimizeToTray;
        SessionCapSeconds = _loaded.SessionCapSeconds;
        HistoryRetentionMinutes = _loaded.HistoryRetentionMinutes;
        Theme = _loaded.Theme;
        ThemeService.Apply(Theme);
        await ReloadHistoryAsync();
        KeyStatus = _services.Secrets.Has(SelectedProvider)
            ? $"{KeyArticle(SelectedProvider)} {ProviderName} key is stored securely."
            : $"No {ProviderName} key saved yet.";
        OnPropertyChanged(nameof(Hotkey));
        OnPropertyChanged(nameof(ProviderName));
        OnPropertyChanged(nameof(CleanupHelp));
        _initializing = false;
    }

    public async Task SelectProviderAsync(SttProvider provider)
    {
        if (SelectedProvider == provider && !_initializing) return;
        SelectedProvider = provider;
        Model = ProviderCatalog.All[provider].DefaultModel;
        KeyStatus = _services.Secrets.Has(provider)
            ? $"{KeyArticle(provider)} {ProviderCatalog.All[provider].DisplayName} key is stored securely."
            : $"No {ProviderCatalog.All[provider].DisplayName} key saved yet.";
        OnPropertyChanged(nameof(ProviderName));
        OnPropertyChanged(nameof(CleanupHelp));
        if (!_initializing)
        {
            _loaded = _loaded with { Provider = provider, Model = Model };
            await _services.Settings.SaveAsync(_loaded);
        }
    }

    public async Task SaveKeyAsync(string key)
    {
        _services.Secrets.Set(SelectedProvider, key);
        KeyStatus = string.IsNullOrWhiteSpace(key) ? "Key removed." : $"{ProviderName} key saved for this Windows account.";
        _loaded = _loaded with { Provider = SelectedProvider, Model = Model.Trim(), Language = Language.Trim(), CustomBaseUrl = CustomBaseUrl.Trim() };
        await _services.Settings.SaveAsync(_loaded);
    }

    public async Task SaveSettingsAsync()
    {
        _loaded = _loaded with
        {
            Provider = SelectedProvider,
            Model = Model.Trim(),
            Language = Language.Trim(),
            CustomBaseUrl = CustomBaseUrl.Trim(),
            DefaultWritingStyle = DefaultWritingStyle,
            CustomWords = ParseCustomWords(CustomWordsText),
            CleanupEnabled = CleanupEnabled,
            SilenceAutoStop = SilenceAutoStop,
            HistoryEnabled = HistoryEnabled,
            PrivateMode = PrivateMode,
            StartWithWindows = StartWithWindows,
            MinimizeToTray = MinimizeToTray,
            SessionCapSeconds = SessionCapSeconds,
            HistoryRetentionMinutes = HistoryRetentionMinutes,
            Theme = Theme,
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

    public async Task ClearHistoryAsync()
    {
        await _services.History.ClearAsync();
        History.Clear();
        OnPropertyChanged(nameof(TodayCount));
        OnPropertyChanged(nameof(HistoryEmptyVisibility));
        OnPropertyChanged(nameof(HistoryListVisibility));
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

    private static IReadOnlyList<WordReplacement> ParseCustomWords(string input) => input
        .Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
        .Select(line => line.Split(["=>", "="], 2, StringSplitOptions.TrimEntries))
        .Where(parts => parts.Length == 2 && parts.All(part => !string.IsNullOrWhiteSpace(part)))
        .Select(parts => new WordReplacement(parts[0], parts[1]))
        .ToArray();

    private static string KeyArticle(SttProvider provider) => provider is SttProvider.OpenAI or SttProvider.OpenRouter ? "An" : "A";

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
