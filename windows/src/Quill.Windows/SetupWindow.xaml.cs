using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Quill.Windows.Core;
using Quill.Windows.Platform;

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
        SetupModel.Text = selectedProvider == _settings.Provider
            ? _settings.Model
            : ProviderCatalog.All[selectedProvider].DefaultModel;
        SetupHistory.IsChecked = _settings.HistoryEnabled;
        SetupPrivate.IsChecked = _settings.PrivateMode;
        SetupStartup.IsChecked = _settings.StartWithWindows;
        RefreshKeyStatus();
        ShowStep(0);
    }

    private async void Next_Click(object sender, RoutedEventArgs e)
    {
        if (_step == 1 && !await SaveProviderAsync()) return;
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
        if (!string.IsNullOrWhiteSpace(SetupKey.Password)) _services.Secrets.Set(provider, SetupKey.Password);
        if (!_services.Secrets.Has(provider))
        {
            SetupError.Text = $"Add a {ProviderCatalog.All[provider].DisplayName} key to continue.";
            return false;
        }
        var model = string.IsNullOrWhiteSpace(SetupModel.Text) ? ProviderCatalog.All[provider].DefaultModel : SetupModel.Text.Trim();
        _settings = _settings with { Provider = provider, Model = model };
        await _services.Settings.SaveAsync(_settings);
        SetupKey.Clear();
        SetupError.Text = "";
        return true;
    }

    private async Task SavePrivacyAsync()
    {
        _settings = _settings with
        {
            HistoryEnabled = SetupHistory.IsChecked == true,
            PrivateMode = SetupPrivate.IsChecked == true,
            StartWithWindows = SetupStartup.IsChecked == true,
        };
        await _services.Settings.SaveAsync(_settings);
        StartupService.SetEnabled(_settings.StartWithWindows);
    }

    private void SetupProvider_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SetupProvider.SelectedItem is not SttProvider provider) return;
        SetupModel.Text = ProviderCatalog.All[provider].DefaultModel;
        RefreshKeyStatus();
    }

    private void RefreshKeyStatus()
    {
        if (SetupProvider.SelectedItem is not SttProvider provider) return;
        SetupKeyStatus.Text = _services.Secrets.Has(provider)
            ? $"{(provider is SttProvider.OpenAI or SttProvider.OpenRouter ? "An" : "A")} {ProviderCatalog.All[provider].DisplayName} key is already stored. Leave this blank to keep it."
            : "Required. The key is encrypted with Windows DPAPI.";
    }

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
    }
}
