using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using Quill.Windows.Core;
using Quill.Windows.Platform;

namespace Quill.Windows;

public partial class MainWindow : Window
{
    private readonly GlobalHotkeyService _hotkey = new();
    private readonly AppServices _services;
    private bool _allowClose;
    private string _registeredHotkey = SettingsPolicy.DefaultHotkey;

    public MainWindow(AppServices services)
    {
        _services = services;
        InitializeComponent();
        Icon = BrandIconFactory.CreateImageSource();
        ViewModel = new MainViewModel(services);
        DataContext = ViewModel;
        Loaded += OnLoaded;
        Closing += OnClosing;
        _hotkey.Pressed += async (_, _) => await ViewModel.ToggleAsync();
    }

    public MainViewModel ViewModel { get; }

    private async void OnLoaded(object sender, RoutedEventArgs args)
    {
        Loaded -= OnLoaded;
        await ViewModel.InitializeAsync();
        if (!_hotkey.Register(this, ViewModel.Hotkey))
            ViewModel.SetHotkeyStatus($"Occupied — {_hotkey.RegistrationFailureReason}");
        else
        {
            _registeredHotkey = ViewModel.Hotkey;
            ViewModel.SetHotkeyStatus("Available — Quill is listening for this shortcut.");
        }
    }

    public void ShowFromTray()
    {
        Show();
        WindowState = WindowState.Normal;
        Activate();
    }

    public void ForceClose()
    {
        _allowClose = true;
        _hotkey.Dispose();
        Close();
    }

    private void OnClosing(object? sender, CancelEventArgs args)
    {
        if (_allowClose || !ViewModel.MinimizeToTray) return;
        args.Cancel = true;
        Hide();
    }

    private void Home_Click(object sender, RoutedEventArgs e) => Pages.SelectedIndex = 0;
    private void History_Click(object sender, RoutedEventArgs e) => Pages.SelectedIndex = 1;
    private void Settings_Click(object sender, RoutedEventArgs e) => Pages.SelectedIndex = 2;
    private void OpenSettings_Click(object sender, RoutedEventArgs e) => Pages.SelectedIndex = 2;
    private void ChangeHotkey_Click(object sender, RoutedEventArgs e)
    {
        Pages.SelectedIndex = 2;
        var capture = new ShortcutCaptureWindow { Owner = this };
        if (capture.ShowDialog() != true || string.IsNullOrWhiteSpace(capture.CapturedHotkey)) return;
        ViewModel.Hotkey = capture.CapturedHotkey;
        ViewModel.SetHotkeyStatus("New shortcut selected - click Save settings to apply it.");
    }
    private async void ToggleDictation_Click(object sender, RoutedEventArgs e) => await ViewModel.ToggleAsync();
    private async void ToggleClipboardDictation_Click(object sender, RoutedEventArgs e) => await ViewModel.ToggleClipboardAsync();
    private void RefreshInputDevices_Click(object sender, RoutedEventArgs e) => ViewModel.RefreshInputDevices();
    private async void ClearHistory_Click(object sender, RoutedEventArgs e) => await ViewModel.ClearHistoryAsync();
    private async void SaveKey_Click(object sender, RoutedEventArgs e) { await ViewModel.SaveKeyAsync(ApiKeyField.Password); ApiKeyField.Clear(); }
    private void OpenProviderConsole_Click(object sender, RoutedEventArgs e) =>
        ProviderConsoleService.Open(ViewModel.SelectedProvider);
    private async void SaveSettings_Click(object sender, RoutedEventArgs e)
    {
        if (!GlobalHotkeyService.TryNormalize(ViewModel.Hotkey, out var normalized))
        {
            MessageBox.Show("Choose a shortcut with at least one modifier and one key, for example Alt+Q.",
                "Shortcut not valid", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        ViewModel.Hotkey = normalized;
        if (!_hotkey.Register(this, normalized))
        {
            var failureReason = _hotkey.RegistrationFailureReason;
            _hotkey.Register(this, _registeredHotkey);
            ViewModel.Hotkey = _registeredHotkey;
            ViewModel.SetHotkeyStatus($"Occupied — {failureReason}");
            return;
        }
        _registeredHotkey = normalized;
        ViewModel.SetHotkeyStatus("Available — Quill is listening for this shortcut.");
        await ViewModel.SaveSettingsAsync();
    }

    private async void ProviderPicker_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ProviderPicker.SelectedItem is not SttProvider provider) return;
        try
        {
            await ViewModel.SelectProviderAsync(provider);
        }
        catch (Exception)
        {
            MessageBox.Show("The provider could not be switched. Your previous settings are still available.",
                "Provider switch failed", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private async void RunSetup_Click(object sender, RoutedEventArgs e)
    {
        var previousShortcut = _registeredHotkey;
        if (new SetupWindow(_services) { Owner = this }.ShowDialog() != true) return;
        await ViewModel.InitializeAsync();
        if (_hotkey.Register(this, ViewModel.Hotkey))
        {
            _registeredHotkey = ViewModel.Hotkey;
            return;
        }

        _hotkey.Register(this, previousShortcut);
        _registeredHotkey = previousShortcut;
        ViewModel.Hotkey = previousShortcut;
        await ViewModel.SaveSettingsAsync();
        MessageBox.Show($"{ViewModel.Hotkey} remains active because the shortcut chosen in setup is already in use.",
            "Shortcut unavailable", MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    private void AddCustomWord_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new VocabularyWindow(_services) { Owner = this };
        if (dialog.ShowDialog() == true) ViewModel.AddCustomWord(dialog.HeardWord, dialog.Replacement);
    }

    private void RemoveCustomWord_Click(object sender, RoutedEventArgs e)
    {
        if (sender is System.Windows.Controls.Button { Tag: WordReplacement word }) ViewModel.RemoveCustomWord(word);
    }

}
