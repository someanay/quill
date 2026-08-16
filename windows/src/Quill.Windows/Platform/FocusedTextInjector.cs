using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Threading;

namespace Quill.Windows.Platform;

public sealed record FocusTarget(IntPtr Window, string Application, bool IsSensitive, bool HasEditableField = true);

public sealed record InjectionResult(bool Inserted, bool CopiedToClipboard, string? Error = null);

public sealed class FocusedTextInjector
{
    public FocusTarget CaptureTarget()
    {
        var window = GetForegroundWindow();
        var application = "Unknown app";
        GetWindowThreadProcessId(window, out var processId);
        try { application = Process.GetProcessById((int)processId).ProcessName; } catch { /* best effort */ }

        var sensitive = false;
        // Assume editable when UI Automation cannot answer — blind SendInput was the old behavior.
        var editable = true;
        try
        {
            var focused = AutomationElement.FocusedElement;
            sensitive = focused is not null && focused.Current.IsPassword;
            editable = focused is not null && IsEditable(focused);
        }
        catch (ElementNotAvailableException) { }
        catch (InvalidOperationException) { }
        return new FocusTarget(window, application, sensitive, editable);
    }

    private static bool IsEditable(AutomationElement focused)
    {
        var control = focused.Current.ControlType;
        if (control == ControlType.Edit || control == ControlType.Document) return true;
        if (focused.TryGetCurrentPattern(ValuePattern.Pattern, out var value))
            return !((ValuePattern)value).Current.IsReadOnly;
        // Terminals and rich editors expose Text without a Value pattern; only a focused
        // element with neither pattern (button, list item, bare desktop) has no caret.
        return focused.TryGetCurrentPattern(TextPattern.Pattern, out _);
    }

    public async Task<InjectionResult> InsertAsync(FocusTarget target, string text)
    {
        if (target.IsSensitive) return new(false, false, "Quill never types into password fields.");
        if (string.IsNullOrEmpty(text)) return new(false, false, "There is no text to insert.");
        if (target.Window != IntPtr.Zero) SetForegroundWindow(target.Window);
        await Task.Delay(80);
        var inputs = UnicodeInputs(text);
        if (SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>()) == inputs.Length)
            return new(true, false);

        // UIPI can block input into elevated apps. Keep a useful fallback on the clipboard.
        try
        {
            // Auto-stop is raised by NAudio's capture thread. WPF's clipboard requires STA,
            // so marshal this fallback even though SendInput itself is thread-safe.
            await Application.Current.Dispatcher.InvokeAsync(() => Clipboard.SetText(text));
            return new(false, true, "Windows blocked direct typing; the transcript was copied.");
        }
        catch (ExternalException error) { return new(false, false, error.Message); }
    }

    private static Input[] UnicodeInputs(string text)
    {
        var result = new Input[text.Length * 2];
        for (var i = 0; i < text.Length; i++)
        {
            result[i * 2] = Keyboard(text[i], 0x0004);
            result[i * 2 + 1] = Keyboard(text[i], 0x0004 | 0x0002);
        }
        return result;
    }

    private static Input Keyboard(char character, uint flags) => new()
    {
        Type = 1,
        Data = new InputUnion { Keyboard = new KeyboardInput { Scan = character, Flags = flags } },
    };

    [StructLayout(LayoutKind.Sequential)]
    private struct Input { public uint Type; public InputUnion Data; }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public KeyboardInput Keyboard;
        [FieldOffset(0)] public MouseInput Mouse;
        [FieldOffset(0)] public HardwareInput Hardware;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    { public ushort VirtualKey; public ushort Scan; public uint Flags; public uint Time; public IntPtr ExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    { public int X; public int Y; public uint MouseData; public uint Flags; public uint Time; public IntPtr ExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct HardwareInput { public uint Message; public ushort ParamLow; public ushort ParamHigh; }

    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] [return: MarshalAs(UnmanagedType.Bool)] private static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint count, Input[] inputs, int size);
}
