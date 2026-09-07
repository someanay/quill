using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows.Automation;
using System.Windows.Interop;
using Microsoft.Win32;
namespace Quill.Windows;
public record FocusTarget(nint Window, int[] RuntimeId, string Application, bool Editable, bool Sensitive);
public interface ITextInjector { Task<FocusTarget> Capture(); Task<bool> Insert(FocusTarget target, string text); Task<bool> CanType(); }
public sealed class TextInjector : ITextInjector {
 private static readonly SemaphoreSlim UiaSlot = new(1,1);
 private static async Task<T> Bounded<T>(Func<T> work, T fallback) {
  if (!await UiaSlot.WaitAsync(0)) return fallback;
  var task = Task.Run(() => { try { return work(); } finally { UiaSlot.Release(); } });
  try { return await task.WaitAsync(TimeSpan.FromMilliseconds(900)); } catch (Exception e) when (e is TimeoutException or ElementNotAvailableException or InvalidOperationException or COMException) { return fallback; }
 }
 public async Task<bool> CanType() { var target = await Capture(); return target.Editable && !target.Sensitive; }
 public Task<FocusTarget> Capture() => Bounded(CaptureCore,new FocusTarget(0,[],"",false,true));
 private FocusTarget CaptureCore() {
  var hwnd = Native.GetForegroundWindow();
  try {
   var element = AutomationElement.FocusedElement; if (element == null) return new(hwnd, [], "", false, false);
   // Password status is queried before names, values, selection or surrounding text.
   if (element.Current.IsPassword) return new(hwnd, [], "", false, true);
   Native.GetWindowThreadProcessId(hwnd, out var pid); using var process = Process.GetProcessById((int)pid); var app = process.ProcessName;
   if (new[] { "keepass", "1password", "bitwarden", "credentialuibroker", "logonui", "consent" }.Any(s => app.Contains(s, StringComparison.OrdinalIgnoreCase))) return new(hwnd, [], app, false, true);
   var name = element.Current.Name + " " + element.Current.AutomationId;
   if (System.Text.RegularExpressions.Regex.IsMatch(name, @"password|passcode|\bpin\b|\bcvv\b|\bcvc\b|credit.?card|card.?number|social.?security", System.Text.RegularExpressions.RegexOptions.IgnoreCase)) return new(hwnd, [], app, false, true);
   bool editable = element.Current.IsEnabled && element.Current.IsKeyboardFocusable && (element.Current.ControlType == ControlType.Edit || element.Current.ControlType == ControlType.Document);
   if (element.TryGetCurrentPattern(ValuePattern.Pattern, out var value) && ((ValuePattern)value).Current.IsReadOnly) editable = false;
   return new(hwnd, element.GetRuntimeId(), app, editable, false);
  } catch (Exception e) when (e is ElementNotAvailableException or InvalidOperationException or System.ComponentModel.Win32Exception or ArgumentException or COMException) { return new(hwnd, [], "", false, true); }
 }
 private static string? ReadFocusedText() {
  var element = AutomationElement.FocusedElement; if (element == null || element.Current.IsPassword) return null;
  if (element.TryGetCurrentPattern(TextPattern.Pattern,out var pattern)) return ((TextPattern)pattern).DocumentRange.GetText(200_000);
  if (element.TryGetCurrentPattern(ValuePattern.Pattern,out var value)) { var content = ((ValuePattern)value).Current.Value; return content.Length <= 200_000 ? content : null; }
  return null;
 }
 public async Task<bool> Insert(FocusTarget target, string text) {
  var current = await Capture();
  if (current.Sensitive || !current.Editable || target.Sensitive || !target.Editable || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId) || Native.ModifierPressed()) return false;
  // Unicode input preserves the native editor's selection, undo history and existing contents.
  // No ValuePattern.SetValue of an entire field, no guessed cursor, and no focus stealing.
  var before = await Bounded(ReadFocusedText,null);
  if (before == null) return false;
  current = await Capture();
  if (current.Sensitive || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId) || Native.ModifierPressed()) return false;
  var inputs = new List<Native.INPUT>();
  foreach (var c in text) { inputs.Add(Native.Unicode(c, false)); inputs.Add(Native.Unicode(c, true)); }
  if (inputs.Count == 0 || Native.SendInput((uint)inputs.Count,inputs.ToArray(),Marshal.SizeOf<Native.INPUT>()) != inputs.Count) return false;
  await Task.Delay(180);
  current = await Capture(); if (current.Sensitive || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId)) return false;
  var after = await Bounded(ReadFocusedText,null);
  return after != null && after != before && after.Replace("\r\n","\n",StringComparison.Ordinal).Contains(text.Replace("\r\n","\n",StringComparison.Ordinal),StringComparison.Ordinal);
 }
}
public interface IClipboardService { bool Copy(string text,bool transient = true); Task<bool> Paste(string text,ITextInjector injector); }
public sealed class ClipboardService : IClipboardService {
 private const string MarkerFormat = "Quill.Dictation.Owner";
 private string? owned;
 public bool Copy(string text, bool transient = true) {
  try { owned = Guid.NewGuid().ToString("N"); var data = new System.Windows.DataObject(); data.SetText(text); data.SetData(MarkerFormat, owned); System.Windows.Clipboard.SetDataObject(data, true); if (transient) _ = ClearLater(owned); return true; }
  catch (COMException) { return false; }
 }
 private async Task ClearLater(string token) { await Task.Delay(60_000); if (Application.Current == null) return; await Application.Current.Dispatcher.InvokeAsync(() => { try { if (System.Windows.Clipboard.GetData(MarkerFormat) as string == token) System.Windows.Clipboard.Clear(); } catch (COMException) { } }); }
 public async Task<bool> Paste(string text,ITextInjector injector) { var target = await injector.Capture(); if (!target.Editable || target.Sensitive || Native.ModifierPressed()) return false; return await injector.Insert(target,text); }
}
public sealed class HotkeyService : IDisposable {
 private HwndSource? source; private const int Id = 0x514c; public event Action? Pressed; public bool Registered { get; private set; }
 public HotkeyService(nint hwnd) { source = HwndSource.FromHwnd(hwnd); source?.AddHook(Hook); Registered = Native.RegisterHotKey(hwnd, Id, 0x4000 | 0x0002 | 0x0004, 0x20); }
 private nint Hook(nint hwnd, int message, nint wparam, nint lparam, ref bool handled) { if (message == 0x0312 && wparam == Id) { handled = true; Pressed?.Invoke(); } return 0; }
 public void Dispose() { if (source != null) { Native.UnregisterHotKey(source.Handle, Id); source.RemoveHook(Hook); source = null; } }
}
public static class StartupService {
 private const string PathKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
 public static void Set(bool enabled) { using var key = Registry.CurrentUser.CreateSubKey(PathKey); if (enabled) key.SetValue("Quill", "\"" + Environment.ProcessPath + "\" --background"); else key.DeleteValue("Quill", false); }
}
public static class Native {
 [DllImport("user32.dll")] public static extern nint GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(nint hwnd, out uint processId);
 [DllImport("user32.dll")] public static extern bool RegisterHotKey(nint hwnd, int id, uint modifiers, uint key);
 [DllImport("user32.dll")] public static extern bool UnregisterHotKey(nint hwnd, int id);
 [DllImport("user32.dll")] public static extern short GetAsyncKeyState(int key);
 [DllImport("user32.dll")] public static extern uint SendInput(uint count, INPUT[] inputs, int size);
 [DllImport("user32.dll", EntryPoint="GetWindowLongPtrW")] public static extern nint GetWindowLong(nint hwnd, int index);
 [DllImport("user32.dll", EntryPoint="SetWindowLongPtrW")] public static extern nint SetWindowLong(nint hwnd, int index, nint value);
 [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public InputUnion union; }
 [StructLayout(LayoutKind.Explicit)] public struct InputUnion { [FieldOffset(0)] public KEYBDINPUT keyboard; [FieldOffset(0)] public MOUSEINPUT mouse; }
 [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort virtualKey; public ushort scan; public uint flags; public uint time; public nuint extra; }
 [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int x; public int y; public uint data; public uint flags; public uint time; public nuint extra; }
 public static INPUT Unicode(char c, bool up) => new() { type = 1, union = new() { keyboard = new() { scan = c, flags = 0x0004u | (up ? 0x0002u : 0) } } };
 public static bool ModifierPressed() => new[] { 0x10, 0x11, 0x12, 0x5b, 0x5c }.Any(k => (GetAsyncKeyState(k) & 0x8000) != 0);
}
