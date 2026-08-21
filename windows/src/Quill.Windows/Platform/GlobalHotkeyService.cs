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
    private const int HoldMilliseconds = 400; // held at least this long ⇒ long-press (clipboard mode)
    private HwndSource? _source;
    private IntPtr _handle;
    private uint _key;
    private System.Windows.Threading.DispatcherTimer? _holdTimer;
    private int _pressTick;
    private bool _longFired;
    public string RegistrationFailureReason { get; private set; } = "";

    /// <summary>A quick tap of the shortcut (pressed and released before the hold threshold).</summary>
    public event EventHandler? Pressed;

    /// <summary>The shortcut held past <see cref="HoldMilliseconds"/> — Quill's "dictate to clipboard" gesture.</summary>
    public event EventHandler? LongPressed;

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
        if (RegisterHotKey(_handle, HotkeyId, modifiers | ModNoRepeat, key)) { _key = key; return true; }
        RegistrationFailureReason = Marshal.GetLastWin32Error() == 1409
            ? "Occupied by another application or Windows. The owner process is not exposed by the Windows hotkey API."
            : $"Windows rejected this shortcut (error {Marshal.GetLastWin32Error()}).";
        return false;
    }

    public void Dispose()
    {
        _holdTimer?.Stop();
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
            BeginHoldDetection();
        }
        return IntPtr.Zero;
    }

    // WM_HOTKEY (with MOD_NOREPEAT) fires once on the initial press and reveals nothing about the
    // release, so a "hold" is invisible from it alone. After the press we poll the one configured
    // key's physical state: released before the threshold ⇒ a tap (Pressed); still down at the
    // threshold ⇒ a long-press (LongPressed). This preserves the privacy-preserving model — Quill
    // reads only its own shortcut key, never a global low-level keyboard hook over every keystroke.
    private void BeginHoldDetection()
    {
        _longFired = false;
        _pressTick = Environment.TickCount;
        _holdTimer ??= CreateHoldTimer();
        _holdTimer.Stop();
        _holdTimer.Start();
    }

    private System.Windows.Threading.DispatcherTimer CreateHoldTimer()
    {
        var timer = new System.Windows.Threading.DispatcherTimer(System.Windows.Threading.DispatcherPriority.Input)
        {
            Interval = TimeSpan.FromMilliseconds(25),
        };
        timer.Tick += (_, _) => HoldTick();
        return timer;
    }

    private void HoldTick()
    {
        var down = (GetAsyncKeyState((int)_key) & 0x8000) != 0;
        var elapsed = Environment.TickCount - _pressTick;
        if (!_longFired && down && elapsed >= HoldMilliseconds)
        {
            _longFired = true;
            _holdTimer?.Stop();
            LongPressed?.Invoke(this, EventArgs.Empty);
        }
        else if (!down)
        {
            _holdTimer?.Stop();
            if (!_longFired) Pressed?.Invoke(this, EventArgs.Empty);
        }
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

    [DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int virtualKey);
}
