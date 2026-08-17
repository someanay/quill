using System.Threading;
using System.Diagnostics;
using System.Runtime.InteropServices;
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
        if (!_ownsMutex)
        {
            // Restore an existing tray/background window. If the old process has no
            // top-level window (for example, it is still booting), continue startup
            // so the new launch still produces a usable window.
            var existing = FindWindowForProcess("Quill.Windows");
            if (existing != IntPtr.Zero)
            {
                ShowWindow(existing, 9);
                SetForegroundWindow(existing);
                Shutdown();
                return;
            }
        }
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

        // Marshal to the UI thread: on silence/session auto-stop, StatusChanged is raised on NAudio's
        // capture thread, and NotifyIcon.Text is a WinForms control mutation with thread affinity (the
        // ribbon and view-model subscribers already marshal; this one must too).
        _services.Dictation.StatusChanged += (_, status) =>
            Dispatcher.InvokeAsync(() => _tray?.SetStatus($"Quill — {status.Title}"));
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

    private static IntPtr FindWindowForProcess(string processName)
    {
        foreach (var process in Process.GetProcessesByName(processName))
        {
            var processId = process.Id;
            IntPtr found = IntPtr.Zero;
            EnumWindows((handle, _) =>
            {
                GetWindowThreadProcessId(handle, out var ownerId);
                if (ownerId == processId && IsWindowVisible(handle))
                {
                    found = handle;
                    return false;
                }
                return true;
            }, IntPtr.Zero);
            process.Dispose();
            if (found != IntPtr.Zero) return found;
        }
        return IntPtr.Zero;
    }

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr extraData);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int command);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

}
