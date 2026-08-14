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

    protected override void OnStartup(StartupEventArgs e)
    {
        _singleInstance = new Mutex(true, @"Local\Quill.Windows.Singleton", out _ownsMutex);
        if (!_ownsMutex) { Shutdown(); return; }
        base.OnStartup(e);

        _services = new AppServices();
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
        if (e.Args.Contains("--background", StringComparer.OrdinalIgnoreCase)) _main.Hide();
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
