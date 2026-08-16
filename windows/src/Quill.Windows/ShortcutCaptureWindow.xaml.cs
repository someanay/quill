using System.Windows;
using System.Windows.Input;
using Quill.Windows.Platform;

namespace Quill.Windows;

public partial class ShortcutCaptureWindow : Window
{
    public ShortcutCaptureWindow() => InitializeComponent();
    public string? CapturedHotkey { get; private set; }

    private void Window_PreviewKeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        e.Handled = true;
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        if (key == Key.Escape) { DialogResult = false; return; }
        if (key is Key.LeftCtrl or Key.RightCtrl or Key.LeftAlt or Key.RightAlt or
            Key.LeftShift or Key.RightShift or Key.LWin or Key.RWin) return;

        var parts = new List<string>();
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Control)) parts.Add("Ctrl");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Alt)) parts.Add("Alt");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Shift)) parts.Add("Shift");
        if (Keyboard.Modifiers.HasFlag(ModifierKeys.Windows)) parts.Add("Win");
        if (parts.Count == 0)
        {
            CaptureHint.Text = "Add Ctrl, Alt, Shift, or Win to the key.";
            return;
        }

        parts.Add(key == Key.Space ? "Space" : key.ToString());
        if (!GlobalHotkeyService.TryNormalize(string.Join('+', parts), out var shortcut))
        {
            CaptureHint.Text = "That combination is not supported. Try another.";
            return;
        }
        CapturedHotkey = shortcut;
        ShortcutPreview.Text = shortcut;
        CaptureHint.Text = "Review it, then choose Use this shortcut — or Retry.";
        AcceptButton.IsEnabled = true;
    }

    private void Retry_Click(object sender, RoutedEventArgs e)
    {
        CapturedHotkey = null;
        ShortcutPreview.Text = "Listening…";
        CaptureHint.Text = "Press a modifier plus a key. Esc cancels.";
        AcceptButton.IsEnabled = false;
    }
    private void Accept_Click(object sender, RoutedEventArgs e) => DialogResult = true;
    private void Cancel_Click(object sender, RoutedEventArgs e) => DialogResult = false;
}
