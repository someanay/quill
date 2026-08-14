using System.Runtime.InteropServices;
using System.Windows.Interop;

namespace Quill.Windows.Platform;

public sealed class GlobalHotkeyService : IDisposable
{
    private const int HotkeyId = 0x5155;
    private const int WmHotkey = 0x0312;
    private const uint ModAlt = 0x0001;
    private const uint ModControl = 0x0002;
    private const uint ModShift = 0x0004;
    private const uint ModNoRepeat = 0x4000;
    private HwndSource? _source;
    private IntPtr _handle;

    public event EventHandler? Pressed;

    public bool Register(System.Windows.Window window, string shortcut)
    {
        _handle = new WindowInteropHelper(window).Handle;
        _source = HwndSource.FromHwnd(_handle);
        _source.AddHook(WndProc);
        var (modifiers, key) = Parse(shortcut);
        return RegisterHotKey(_handle, HotkeyId, modifiers | ModNoRepeat, key);
    }

    public void Dispose()
    {
        if (_handle != IntPtr.Zero) UnregisterHotKey(_handle, HotkeyId);
        _source?.RemoveHook(WndProc);
        _source = null;
        _handle = IntPtr.Zero;
    }

    private IntPtr WndProc(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (message == WmHotkey && wParam.ToInt32() == HotkeyId)
        {
            handled = true;
            Pressed?.Invoke(this, EventArgs.Empty);
        }
        return IntPtr.Zero;
    }

    private static (uint Modifiers, uint Key) Parse(string value)
    {
        uint modifiers = 0, key = 0x20;
        foreach (var part in value.Split('+', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            if (part.Equals("Ctrl", StringComparison.OrdinalIgnoreCase)) modifiers |= ModControl;
            else if (part.Equals("Alt", StringComparison.OrdinalIgnoreCase)) modifiers |= ModAlt;
            else if (part.Equals("Shift", StringComparison.OrdinalIgnoreCase)) modifiers |= ModShift;
            else if (part.Equals("Space", StringComparison.OrdinalIgnoreCase)) key = 0x20;
            else if (part.Length == 1) key = char.ToUpperInvariant(part[0]);
        }
        return (modifiers, key);
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool RegisterHotKey(IntPtr window, int id, uint modifiers, uint virtualKey);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UnregisterHotKey(IntPtr window, int id);
}
