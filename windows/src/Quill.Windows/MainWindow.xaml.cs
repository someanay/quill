using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using Quill.Windows.Core;
using Quill.Windows.Platform;

namespace Quill.Windows;

public partial class MainWindow : Window
{
    private readonly GlobalHotkeyService _hotkey = new();
    private bool _allowClose;

    public MainWindow(AppServices services)
    {
        InitializeComponent();
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
            MessageBox.Show($"{ViewModel.Hotkey} is already used by another application.", "Quill shortcut unavailable",
                MessageBoxButton.OK, MessageBoxImage.Warning);
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
    private async void ToggleDictation_Click(object sender, RoutedEventArgs e) => await ViewModel.ToggleAsync();
    private async void ClearHistory_Click(object sender, RoutedEventArgs e) => await ViewModel.ClearHistoryAsync();
    private void SaveKey_Click(object sender, RoutedEventArgs e) { ViewModel.SaveKey(ApiKeyField.Password); ApiKeyField.Clear(); }
    private async void SaveSettings_Click(object sender, RoutedEventArgs e) => await ViewModel.SaveSettingsAsync();

    private void ProviderPicker_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ProviderPicker.SelectedItem is SttProvider provider) ViewModel.SelectProvider(provider);
    }
}
