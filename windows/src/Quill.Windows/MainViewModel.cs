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
    private string _keyStatus = "Keys are encrypted for your Windows account.";

    public MainViewModel(AppServices services)
    {
        _services = services;
        _services.Dictation.StatusChanged += OnStatusChanged;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    public IReadOnlyList<SttProvider> Providers { get; } = Enum.GetValues<SttProvider>();
    public IReadOnlyList<WritingStyle> WritingStyles { get; } = Enum.GetValues<WritingStyle>();
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
    public string KeyStatus { get => _keyStatus; private set => Set(ref _keyStatus, value); }
    public string StatusTitle { get => _statusTitle; private set => Set(ref _statusTitle, value); }
    public string StatusDetail { get => _statusDetail; private set => Set(ref _statusDetail, value); }
    public string Hotkey => _loaded.Hotkey;
    public string ProviderName => ProviderCatalog.All[SelectedProvider].DisplayName;
    public string SessionCapLabel => $"{SessionCapSeconds / 60} min";
    public string ActionLabel => _state == DictationState.Listening ? "Finish dictation" : "Start dictation";
    public int TodayCount => History.Count(it => it.CreatedAt.LocalDateTime.Date == DateTime.Today);
    public System.Windows.Media.Brush StateBrush => _state switch
    {
        DictationState.Listening => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Signal"),
        DictationState.Error => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Error"),
        DictationState.Idle => (System.Windows.Media.Brush)System.Windows.Application.Current.FindResource("Brand"),
        _ => new SolidColorBrush(Color.FromRgb(91, 91, 214)),
    };

    public async Task InitializeAsync()
    {
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
        await ReloadHistoryAsync();
        KeyStatus = _services.Secrets.Has(SelectedProvider)
            ? $"A {ProviderName} key is stored securely."
            : $"No {ProviderName} key saved yet.";
        OnPropertyChanged(nameof(Hotkey));
        OnPropertyChanged(nameof(ProviderName));
    }

    public void SelectProvider(SttProvider provider)
    {
        SelectedProvider = provider;
        Model = ProviderCatalog.All[provider].DefaultModel;
        KeyStatus = _services.Secrets.Has(provider)
            ? $"A {ProviderCatalog.All[provider].DisplayName} key is stored securely."
            : $"No {ProviderCatalog.All[provider].DisplayName} key saved yet.";
        OnPropertyChanged(nameof(ProviderName));
    }

    public void SaveKey(string key)
    {
        _services.Secrets.Set(SelectedProvider, key);
        KeyStatus = string.IsNullOrWhiteSpace(key) ? "Key removed." : $"{ProviderName} key saved for this Windows account.";
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
        };
        await _services.Settings.SaveAsync(_loaded);
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
    }

    private async Task ReloadHistoryAsync()
    {
        var entries = await _services.History.ReadAsync(_loaded.HistoryRetentionMinutes);
        History.Clear();
        foreach (var entry in entries) History.Add(entry);
        OnPropertyChanged(nameof(TodayCount));
    }

    private static IReadOnlyList<WordReplacement> ParseCustomWords(string input) => input
        .Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
        .Select(line => line.Split(["=>", "="], 2, StringSplitOptions.TrimEntries))
        .Where(parts => parts.Length == 2 && parts.All(part => !string.IsNullOrWhiteSpace(part)))
        .Select(parts => new WordReplacement(parts[0], parts[1]))
        .ToArray();

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
