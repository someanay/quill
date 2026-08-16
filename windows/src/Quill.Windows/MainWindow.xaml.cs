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
    private bool _capturingHotkey;
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
        RepairLegacyEncoding(this);
        await ViewModel.InitializeAsync();
        if (!_hotkey.Register(this, ViewModel.Hotkey))
            ViewModel.SetHotkeyStatus($"Occupied — {_hotkey.RegistrationFailureReason}");
        else
        {
            _registeredHotkey = ViewModel.Hotkey;
            ViewModel.SetHotkeyStatus("Available — Quill is listening for this shortcut.");
        }
    }

    // Older generated XAML contained a UTF-8/Windows-1252 conversion. Normalize those
    // visible strings at load so existing installs do not show replacement glyphs.
    private static void RepairLegacyEncoding(DependencyObject root)
    {
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(root); i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            switch (child)
            {
                case System.Windows.Controls.Button button when button.Content is string content:
                    if (content.Contains("Home", StringComparison.Ordinal)) button.Content = "Home";
                    else if (content.Contains("History", StringComparison.Ordinal)) button.Content = "History";
                    else if (content.Contains("Settings", StringComparison.Ordinal)) button.Content = "Settings";
                    else if (content.Contains("Add word", StringComparison.Ordinal)) button.Content = "+  Add word";
                    else if (button.ToolTip is "Remove word") button.Content = "×";
                    break;
                case TextBlock textBlock when textBlock.Text.Contains('â') || textBlock.Text.Contains('Ã') || textBlock.Text.Contains('ï'):
                    if (textBlock.Text.Contains("Your key", StringComparison.Ordinal)) textBlock.Text = "Your key · direct connection";
                    else if (textBlock.Text.Contains("Complete a dictation", StringComparison.Ordinal))
                        textBlock.Text = "Complete a dictation to build a local, time-limited history - or turn history off in Settings.";
                    else if (textBlock.Text.Length < 12) textBlock.Text = "•";
                    else if (textBlock.Text.Contains("CreatedAt", StringComparison.Ordinal) || textBlock.Text.Contains("Application", StringComparison.Ordinal))
                        textBlock.Text = " · ";
                    break;
            }
            RepairLegacyEncoding(child);
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

    private void Window_PreviewKeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        if (!_capturingHotkey) return;
        e.Handled = true;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key == Key.Escape)
        {
            _capturingHotkey = false;
            SidebarShortcutButton.Content = "Change shortcut";
            SettingsShortcutButton.Content = "Change shortcut";
            ViewModel.SetHotkeyStatus("Shortcut change cancelled.");
            return;
        }
        if (key is Key.LeftCtrl or Key.RightCtrl or Key.LeftAlt or Key.RightAlt or
            Key.LeftShift or Key.RightShift or Key.LWin or Key.RWin) return;
        var parts = new List<string>();
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Control)) parts.Add("Ctrl");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Alt)) parts.Add("Alt");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Shift)) parts.Add("Shift");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Windows)) parts.Add("Win");
        var keyName = key == Key.Space ? "Space" : key.ToString();
        if (parts.Count == 0 || keyName.Length == 0)
        {
            ViewModel.SetHotkeyStatus("Choose at least one modifier and one key.");
            return;
        }
        parts.Add(keyName);
        var candidate = string.Join('+', parts);
        if (!GlobalHotkeyService.TryNormalize(candidate, out var normalized))
        {
            ViewModel.SetHotkeyStatus("That key combination is not supported.");
            return;
        }
        _capturingHotkey = false;
        SidebarShortcutButton.Content = "Change shortcut";
        SettingsShortcutButton.Content = "Change shortcut";
        ViewModel.Hotkey = normalized;
        ViewModel.SetHotkeyStatus("New shortcut selected — click Save settings to apply it.");
        ViewModel.SetHotkeyStatus("New shortcut selected - click Save settings to apply it.");
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
