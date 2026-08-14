using System.Threading;
using System.Windows;
using Quill.Windows.Core;
using Quill.Windows.Platform;

namespace Quill.Windows;

public partial class App : System.Windows.Application
{
    private Mutex? _singleInstance;
    private bool _ownsMutex;
    private AppServices? _services;
    private TrayIconService? _tray;
    private DictationRibbon? _ribbon;
    private MainWindow? _main;

    protected override async void OnStartup(StartupEventArgs e)
    {
        _singleInstance = new Mutex(true, @"Local\Quill.Windows.Singleton", out _ownsMutex);
        if (!_ownsMutex) { Shutdown(); return; }
        base.OnStartup(e);

        _services = new AppServices();
        var settings = await _services.Settings.LoadAsync();
        ThemeService.Apply(settings.Theme);
        var background = e.Args.Contains("--background", StringComparer.OrdinalIgnoreCase);
        var skipOnboarding = false;
#if DEBUG
        skipOnboarding = e.Args.Contains("--skip-onboarding", StringComparer.OrdinalIgnoreCase);
#endif
        if (!background && !skipOnboarding && !settings.OnboardingComplete)
            new SetupWindow(_services).ShowDialog();

        _main = new MainWindow(_services);
        _ribbon = new DictationRibbon();
        _services.Dictation.StatusChanged += (_, status) => _ribbon.Update(status);
        _tray = new TrayIconService(
            () => Dispatcher.Invoke(_main.ShowFromTray),
            () => Dispatcher.InvokeAsync(_main.ViewModel.ToggleAsync).Task.Unwrap(),
            ExitApplication);

        _services.Dictation.StatusChanged += (_, status) => _tray.SetStatus($"Quill — {status.Title}");
        // Even tray-start needs an HWND: RegisterHotKey delivers WM_HOTKEY to that native handle.
        _main.Show();
        if (background) _main.Hide();
    }

    private void ExitApplication()
    {
        Dispatcher.Invoke(() =>
        {
            _main?.ForceClose();
            _ribbon?.Close();
            Shutdown();
        });
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _tray?.Dispose();
        _services?.Dispose();
        if (_ownsMutex) _singleInstance?.ReleaseMutex();
        _singleInstance?.Dispose();
        base.OnExit(e);
    }
}
