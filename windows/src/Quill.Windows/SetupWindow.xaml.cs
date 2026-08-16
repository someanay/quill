using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Input;
using Quill.Windows.Core;
using Quill.Windows.Platform;
using Quill.Windows.Stt;

namespace Quill.Windows;

public partial class SetupWindow : Window
{
    private readonly AppServices _services;
    private AppSettings _settings = new();
    private int _step;

    public SetupWindow(AppServices services)
    {
        _services = services;
        InitializeComponent();
        SetupProvider.ItemsSource = Enum.GetValues<SttProvider>();
        Loaded += OnLoaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        Loaded -= OnLoaded;
        _settings = await _services.Settings.LoadAsync();
        var selectedProvider = _services.Secrets.Has(_settings.Provider)
            ? _settings.Provider
            : Enum.GetValues<SttProvider>().Select(provider => (SttProvider?)provider).FirstOrDefault(provider => provider is not null && _services.Secrets.Has(provider.Value))
              ?? _settings.Provider;
        SetupProvider.SelectedItem = selectedProvider;
        RefreshModelChoices(selectedProvider);
        SetupModel.Text = selectedProvider == _settings.Provider
            ? _settings.Model
            : ProviderCatalog.All[selectedProvider].DefaultModel;
        SetupCustomBaseUrl.Text = selectedProvider == _settings.Provider ? _settings.CustomBaseUrl : "";
        RefreshCleanupChoices(selectedProvider);
        SetupCleanup.IsChecked = selectedProvider == _settings.Provider && _settings.CleanupEnabled;
        SetupCleanupModel.SelectedItem = selectedProvider == _settings.Provider
            ? _settings.CleanupModel
            : CleanupModelCatalog.DefaultFor(selectedProvider);
        SetupShortcut.Text = _settings.Hotkey;
        SetupHistory.IsChecked = _settings.HistoryEnabled && !_settings.PrivateMode;
        SetupStartup.IsChecked = _settings.StartWithWindows;
        RefreshKeyStatus();
        ShowStep(0);
    }

    private async void Next_Click(object sender, RoutedEventArgs e)
    {
        if (_step == 1 && !await SaveProviderAsync()) return;
        if (_step == 2 && !await SaveShortcutAsync()) return;
        if (_step == 3) await SavePrivacyAsync();
        if (_step == 4)
        {
            _settings = _settings with { OnboardingComplete = true };
            await _services.Settings.SaveAsync(_settings);
            DialogResult = true;
            return;
        }
        ShowStep(_step + 1);
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        if (_step > 0) ShowStep(_step - 1);
    }

    private async Task<bool> SaveProviderAsync()
    {
        if (SetupProvider.SelectedItem is not SttProvider provider) return false;
        if (provider == SttProvider.Custom &&
            (!Uri.TryCreate(SetupCustomBaseUrl.Text.Trim(), UriKind.Absolute, out var customUri) || customUri.Scheme != Uri.UriSchemeHttps))
        {
            SetupError.Text = "Custom provider requires an absolute HTTPS endpoint.";
            return false;
        }
        var enteredKey = SetupKey.Password.Trim();
        var key = string.IsNullOrWhiteSpace(enteredKey) ? _services.Secrets.Get(provider) : enteredKey;
        if (string.IsNullOrWhiteSpace(key))
        {
            SetupError.Text = $"Add a {ProviderCatalog.All[provider].DisplayName} key to continue.";
            return false;
        }
        var model = string.IsNullOrWhiteSpace(SetupModel.Text) ? ProviderCatalog.All[provider].DefaultModel : SetupModel.Text.Trim();
        SetupError.Text = "Testing this key with a silent audio check...";
        var validation = await _services.KeyValidator.ValidateAsync(provider, key, model, SetupCustomBaseUrl.Text);
        if (validation is KeyValidation.Rejected)
        {
            SetupKeyStatusCard.Background = (Brush)FindResource("ErrorSoft");
            SetupKeyStatus.Foreground = (Brush)FindResource("Error");
            SetupKeyStatus.Text = "Key rejected - check the provider and paste a new key.";
            SetupError.Text = "";
            return false;
        }
        if (!string.IsNullOrWhiteSpace(enteredKey)) _services.Secrets.Set(provider, enteredKey);
        if (validation is KeyValidation.Unverified unverified)
        {
            SetupKeyStatusCard.Background = (Brush)FindResource("ErrorSoft");
            SetupKeyStatus.Foreground = (Brush)FindResource("Error");
            SetupKeyStatus.Text = $"Key saved, but not verified: {unverified.Reason}.";
        }
        else
        {
            SetupKeyStatusCard.Background = (Brush)FindResource("OkaySoft");
            SetupKeyStatus.Foreground = (Brush)FindResource("Okay");
            SetupKeyStatus.Text = "Key verified with a silent audio check.";
            await ProbePaidAccessAsync(provider, key, SetupCustomBaseUrl.Text);
        }
        var cleanupModel = SetupCleanupModel.SelectedItem as string ?? CleanupModelCatalog.DefaultFor(provider);
        _settings = _settings with
        {
            Provider = provider,
            Model = model,
            CustomBaseUrl = SetupCustomBaseUrl.Text.Trim(),
            CleanupEnabled = SetupCleanup.IsChecked == true && CleanupModelCatalog.Supports(provider),
            CleanupModel = cleanupModel,
        };
        await _services.Settings.SaveAsync(_settings);
        SetupKey.Clear();
        SetupError.Text = "";
        return true;
    }

    private async Task ProbePaidAccessAsync(SttProvider provider, string key, string customBaseUrl)
    {
        var paidModel = ProviderCatalog.FirstPaidOnlyModel(provider);
        if (paidModel is null) return;
        var available = await _services.KeyValidator.HasPaidAccessAsync(provider, key, paidModel, customBaseUrl);
        if (available is null) return;
        var restrictions = new Dictionary<SttProvider, bool>(_settings.FreeTierOnlyProviders) { [provider] = !available.Value };
        _settings = _settings with { FreeTierOnlyProviders = restrictions };
        if (!available.Value && ProviderCatalog.IsPaidOnly(provider, SetupModel.Text))
            SetupModel.Text = ProviderCatalog.All[provider].DefaultModel;
    }

    private async Task SavePrivacyAsync()
    {
        _settings = _settings with
        {
            HistoryEnabled = SetupHistory.IsChecked == true,
            PrivateMode = SetupHistory.IsChecked != true,
            StartWithWindows = SetupStartup.IsChecked == true,
        };
        await _services.Settings.SaveAsync(_settings);
        StartupService.SetEnabled(_settings.StartWithWindows);
    }

    private void SetupProvider_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SetupProvider.SelectedItem is not SttProvider provider) return;
        RefreshModelChoices(provider);
        SetupModel.Text = ProviderCatalog.All[provider].DefaultModel;
        SetupCustomBaseUrl.Text = provider == _settings.Provider ? _settings.CustomBaseUrl : "";
        SetupCustomBaseUrl.IsEnabled = provider == SttProvider.Custom;
        SetupCustomEndpointLabel.Foreground = (Brush)FindResource(provider == SttProvider.Custom ? "Ink" : "Muted");
        RefreshCleanupChoices(provider);
        SetupCleanup.IsChecked = false;
        RefreshKeyStatus();
    }

    private void RefreshModelChoices(SttProvider provider)
    {
        SetupModel.ItemsSource = ProviderCatalog.All[provider].Models;
        SetupModel.IsEditable = provider is SttProvider.OpenRouter or SttProvider.Custom;
        SetupCustomBaseUrl.IsEnabled = provider == SttProvider.Custom;
        SetupCustomEndpointLabel.Foreground = (Brush)FindResource(provider == SttProvider.Custom ? "Ink" : "Muted");
    }

    private void RefreshCleanupChoices(SttProvider provider)
    {
        var supported = CleanupModelCatalog.Supports(provider);
        SetupCleanup.IsEnabled = supported;
        SetupCleanupModel.IsEnabled = supported;
        SetupCleanupModel.ItemsSource = CleanupModelCatalog.For(provider);
        SetupCleanupModel.SelectedItem = CleanupModelCatalog.DefaultFor(provider);
        SetupCleanupHelp.Text = supported
            ? $"Uses the same {ProviderCatalog.All[provider].DisplayName} key. You can change this later in Settings."
            : "Cleanup is unavailable for this provider; transcription still works normally.";
    }

    private async Task<bool> SaveShortcutAsync()
    {
        if (!GlobalHotkeyService.TryNormalize(SetupShortcut.Text, out var shortcut))
        {
            SetupShortcut.Focus();
            return false;
        }
        SetupShortcut.Text = shortcut;
        _settings = _settings with { Hotkey = shortcut };
        await _services.Settings.SaveAsync(_settings);
        return true;
    }

    private void SetupShortcut_PreviewKeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        e.Handled = true;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key is Key.LeftCtrl or Key.RightCtrl or Key.LeftAlt or Key.RightAlt or
            Key.LeftShift or Key.RightShift or Key.LWin or Key.RWin) return;
        var parts = new List<string>();
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Control)) parts.Add("Ctrl");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Alt)) parts.Add("Alt");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Shift)) parts.Add("Shift");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Windows)) parts.Add("Win");
        if (parts.Count == 0) return;
        parts.Add(key == Key.Space ? "Space" : key.ToString());
        SetupShortcut.Text = string.Join('+', parts);
    }

    private void RefreshKeyStatus()
    {
        if (SetupProvider.SelectedItem is not SttProvider provider) return;
        var saved = _services.Secrets.Has(provider);
        var entered = !string.IsNullOrWhiteSpace(SetupKey.Password);
        SetupKeyStatusCard.Background = (System.Windows.Media.Brush)FindResource(saved || entered ? "OkaySoft" : "ErrorSoft");
        SetupKeyStatus.Foreground = (System.Windows.Media.Brush)FindResource(saved || entered ? "Okay" : "Error");
        SetupKeyStatus.Text = entered
            ? "Key entered — it will be encrypted and saved when you continue."
            : saved
                ? $"{(provider is SttProvider.OpenAI or SttProvider.OpenRouter ? "An" : "A")} {ProviderCatalog.All[provider].DisplayName} key is already stored. Leave this blank to keep it."
                : "No key saved yet. Add one to continue; it is encrypted with Windows DPAPI.";
        if (entered) SetupKeyStatus.Text = "Key entered - it will be encrypted and saved when you continue.";
        SetupProviderLink.Content = ProviderCatalog.All[provider].KeyConsoleUrl is null
            ? "Use your provider's dashboard"
            : $"Get a {ProviderCatalog.All[provider].DisplayName} key ↗";
        SetupProviderLink.IsEnabled = ProviderCatalog.All[provider].KeyConsoleUrl is not null;
        SetupProviderLink.Content = ProviderCatalog.All[provider].KeyConsoleUrl is null
            ? "Use your provider dashboard"
            : $"Open {ProviderCatalog.All[provider].DisplayName} key console";
    }

    private void OpenProviderConsole_Click(object sender, RoutedEventArgs e)
    {
        if (SetupProvider.SelectedItem is SttProvider provider) ProviderConsoleService.Open(provider);
    }

    private void SetupKey_PasswordChanged(object sender, RoutedEventArgs e) => RefreshKeyStatus();

    private void ShowStep(int step)
    {
        _step = Math.Clamp(step, 0, 4);
        var panels = new FrameworkElement[] { Step0, Step1, Step2, Step3, Step4 };
        var rails = new[] { RailStep0, RailStep1, RailStep2, RailStep3, RailStep4 };
        for (var index = 0; index < panels.Length; index++)
        {
            panels[index].Visibility = index == _step ? Visibility.Visible : Visibility.Collapsed;
            rails[index].Foreground = (Brush)FindResource(index == _step ? "BrandInk" : "Muted");
        }
        StepCounter.Text = $"STEP {_step + 1} OF 5";
        BackButton.Visibility = _step == 0 ? Visibility.Hidden : Visibility.Visible;
        NextButton.Content = _step == 4 ? "Open Quill" : "Continue";
        ReadyInstruction.Text = $"Focus a text field, press {SetupShortcut.Text}, and speak. The bottom-right ribbon shows when Quill is listening.";
    }
}
