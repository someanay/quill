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
    private const uint ModWin = 0x0008;
    private const uint ModNoRepeat = 0x4000;
    private HwndSource? _source;
    private IntPtr _handle;
    public string RegistrationFailureReason { get; private set; } = "";

    public event EventHandler? Pressed;

    public bool Register(System.Windows.Window window, string shortcut)
    {
        RegistrationFailureReason = "";
        Dispose();
        _handle = new WindowInteropHelper(window).Handle;
        _source = HwndSource.FromHwnd(_handle);
        _source.AddHook(WndProc);
        if (!TryParse(shortcut, out var modifiers, out var key))
        {
            RegistrationFailureReason = "Invalid shortcut format.";
            return false;
        }
        if (RegisterHotKey(_handle, HotkeyId, modifiers | ModNoRepeat, key)) return true;
        RegistrationFailureReason = Marshal.GetLastWin32Error() == 1409
            ? "Occupied by another application or Windows. The owner process is not exposed by the Windows hotkey API."
            : $"Windows rejected this shortcut (error {Marshal.GetLastWin32Error()}).";
        return false;
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

    public static bool TryNormalize(string value, out string normalized)
    {
        normalized = "";
        if (!TryParse(value, out var modifiers, out var key)) return false;
        var parts = new List<string>();
        if ((modifiers & ModControl) != 0) parts.Add("Ctrl");
        if ((modifiers & ModAlt) != 0) parts.Add("Alt");
        if ((modifiers & ModShift) != 0) parts.Add("Shift");
        if ((modifiers & ModWin) != 0) parts.Add("Win");
        parts.Add(KeyName(key));
        normalized = string.Join('+', parts);
        return true;
    }

    private static bool TryParse(string value, out uint modifiers, out uint key)
    {
        modifiers = 0;
        key = 0;
        foreach (var part in value.Split('+', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            if (part.Equals("Ctrl", StringComparison.OrdinalIgnoreCase)) modifiers |= ModControl;
            else if (part.Equals("Alt", StringComparison.OrdinalIgnoreCase)) modifiers |= ModAlt;
            else if (part.Equals("Shift", StringComparison.OrdinalIgnoreCase)) modifiers |= ModShift;
            else if (part.Equals("Win", StringComparison.OrdinalIgnoreCase)) modifiers |= ModWin;
            else if (part.Equals("Space", StringComparison.OrdinalIgnoreCase)) key = 0x20;
            else if (part.Length == 1 && char.IsLetterOrDigit(part[0])) key = char.ToUpperInvariant(part[0]);
            else if (part.Length > 1 && part[0] is 'F' or 'f' && int.TryParse(part[1..], out var f) && f is >= 1 and <= 24) key = (uint)(0x70 + f - 1);
            else return false;
        }
        return modifiers != 0 && key != 0;
    }

    private static string KeyName(uint key) => key switch
    {
        0x20 => "Space",
        >= 0x70 and <= 0x87 => $"F{key - 0x70 + 1}",
        _ => ((char)key).ToString(),
    };

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool RegisterHotKey(IntPtr window, int id, uint modifiers, uint virtualKey);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UnregisterHotKey(IntPtr window, int id);
}
