using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows.Automation;
using System.Windows.Automation.Text;
using System.Windows.Interop;
using Microsoft.Win32;
namespace Quill.Windows;
public record EditingContext(string Text,int Start,int End);
public record FocusTarget(nint Window, int[] RuntimeId, string Application, bool Editable, bool Sensitive,EditingContext? Context = null);
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
   return new(hwnd, element.GetRuntimeId(), app, editable, false,editable ? ReadContext(element) : null);
  } catch (Exception e) when (e is ElementNotAvailableException or InvalidOperationException or System.ComponentModel.Win32Exception or ArgumentException or COMException) { return new(hwnd, [], "", false, true); }
 }
 private static string? ReadText(AutomationElement element) {
  if (element.Current.IsPassword) return null;
  if (element.TryGetCurrentPattern(TextPattern.Pattern,out var pattern)) return ((TextPattern)pattern).DocumentRange.GetText(200_000);
  if (element.TryGetCurrentPattern(ValuePattern.Pattern,out var value)) { var content = ((ValuePattern)value).Current.Value; return content.Length <= 200_000 ? content : null; }
  return null;
 }
 private static EditingContext? ReadContext(AutomationElement element) {
  if (element.Current.IsPassword) return null;
  var current = ReadText(element); if (current == null || current.Length >= 200_000) return null;
  int start; int end;
  if (element.TryGetCurrentPattern(TextPattern.Pattern,out var pattern)) {
   var tp = (TextPattern)pattern; var selection = tp.GetSelection(); if (selection.Length != 1) return null;
   var prefix = tp.DocumentRange.Clone(); prefix.MoveEndpointByRange(TextPatternRangeEndpoint.End,selection[0],TextPatternRangeEndpoint.Start); start = prefix.GetText(200_000).Length; end = start + selection[0].GetText(200_000).Length;
  } else {
   var hwnd = (nint)element.Current.NativeWindowHandle; var className = new System.Text.StringBuilder(128); Native.GetClassName(hwnd,className,128);
   if (!className.ToString().Contains("edit",StringComparison.OrdinalIgnoreCase) || current.Length >= 65535 || Native.SendMessageTimeout(hwnd,0x00B0,0,0,0x0002,300,out var positions) == 0) return null;
   start = (int)(positions & 0xffff); end = (int)((positions >> 16) & 0xffff);
  }
  if (start < 0 || start > end || end > current.Length) return null;
  return new(current,start,end);
 }
 public async Task<bool> Insert(FocusTarget target, string text) {
  var current = await Capture();
  if (current.Sensitive || !current.Editable || target.Sensitive || !target.Editable || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId) || target.Context == null || current.Context != target.Context || Native.ModifierPressed()) return false;
  // Unicode input preserves the native editor's selection, undo history and existing contents.
  // No ValuePattern.SetValue of an entire field, no guessed cursor, and no focus stealing.
  var context = target.Context; var expected = InsertionComposer.Insert(context.Text,context.Start,context.End,text); var payloadLength = expected.Length - context.Text.Length + context.End - context.Start; var payload = expected.Substring(context.Start,payloadLength);
  current = await Capture();
  if (current.Sensitive || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId) || current.Context != context || Native.ModifierPressed()) return false;
  // Modern Notepad's RichEdit replaces supplementary Unicode packet input with U+FFFD.
  // Choose a clipboard transaction before delivering anything; never retry partial input.
  if (NativeTextPayload.NeedsClipboard(target.Application,payload)) {
   using var clipboard = ClipboardLease.TryCreate(payload); if (clipboard == null) return false;
   current = await Capture();
   if (!clipboard.OwnsClipboard || current.Sensitive || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId) || current.Context != context || Native.ModifierPressed()) return false;
   if (Native.GetForegroundWindow() != target.Window) return false;
   if (!NativeInputDispatch.QueuePaste(keys => Native.SendInput((uint)keys.Length,keys,Marshal.SizeOf<Native.INPUT>()))) return false;
   return await Verify(target,expected);
  }
  var inputs = new List<Native.INPUT>();
  foreach (var c in payload) { inputs.Add(Native.Unicode(c,false)); inputs.Add(Native.Unicode(c,true)); }
  var batch = inputs.ToArray();
  if (inputs.Count == 0 || Native.GetForegroundWindow() != target.Window || Native.ModifierPressed() || Native.SendInput((uint)inputs.Count,batch,Marshal.SizeOf<Native.INPUT>()) != inputs.Count) return false;
  return await Verify(target,expected);
 }
 private async Task<bool> Verify(FocusTarget target,string expected) {
  for (int attempt = 0;attempt < 5;attempt++) {
   await Task.Delay(attempt == 0 ? 180 : 100); var current = await Capture();
   if (current.Sensitive || current.Window != target.Window || !current.RuntimeId.SequenceEqual(target.RuntimeId)) return false;
   if (current.Context?.Text.Replace("\r\n","\n",StringComparison.Ordinal) == expected.Replace("\r\n","\n",StringComparison.Ordinal)) return true;
  }
  return false;
 }
}
public static class NativeTextPayload {
 public static bool NeedsClipboard(string application,string text) => string.Equals(application,"Notepad",StringComparison.OrdinalIgnoreCase) && text.Any(char.IsSurrogate);
}
public static class NativeInputDispatch {
 public static bool QueuePaste(Func<Native.INPUT[],uint> send) {
  var queued = send([Native.Key(0x11,false),Native.Key(0x56,false),Native.Key(0x56,true),Native.Key(0x11,true)]);
  // Never resend a paste. If only a prefix was queued, release only our unmatched keys.
  // The caller keeps the clipboard lease alive while verifying the eventual field result.
  if (queued == 1 || queued == 3) send([Native.Key(0x11,true)]);
  else if (queued == 2) send([Native.Key(0x56,true),Native.Key(0x11,true)]);
  return queued > 0;
 }
}
public interface IClipboardAccess {
 uint Sequence { get; }
 System.Windows.IDataObject? Read();
 object? GetData(string format);
 void Write(System.Windows.IDataObject data);
 void Clear();
}
public sealed class WindowsClipboardAccess : IClipboardAccess {
 public uint Sequence => Native.GetClipboardSequenceNumber();
 public System.Windows.IDataObject? Read() => System.Windows.Clipboard.GetDataObject();
 public object? GetData(string format) => System.Windows.Clipboard.GetData(format);
 public void Write(System.Windows.IDataObject data) => System.Windows.Clipboard.SetDataObject(data,true);
 public void Clear() => System.Windows.Clipboard.Clear();
}
public sealed class ClipboardLease : IDisposable {
 private const string Marker = "Quill.Insertion.Transaction";
 private readonly string token = Guid.NewGuid().ToString("N");
 private readonly System.Windows.DataObject? previous;
 private readonly IClipboardAccess access;
 private readonly Func<TimeSpan,Task> delay;
 private readonly System.Windows.Threading.Dispatcher? dispatcher;
 public Task Restoration { get; private set; } = Task.CompletedTask;
 private bool disposed;
 private ClipboardLease(System.Windows.DataObject? previous,IClipboardAccess access,Func<TimeSpan,Task> delay) { this.previous = previous; this.access = access; this.delay = delay; dispatcher = access is WindowsClipboardAccess ? System.Windows.Threading.Dispatcher.CurrentDispatcher : null; }
 private static bool ExpectedFailure(Exception e) => e is COMException or System.Runtime.Serialization.SerializationException or System.IO.IOException or ArgumentException or InvalidOperationException or NotSupportedException;
 public bool OwnsClipboard { get { try { return access.GetData(Marker) as string == token; } catch (Exception e) when (ExpectedFailure(e)) { return false; } } }
 public static ClipboardLease? TryCreate(string text,IClipboardAccess? clipboard = null,Func<TimeSpan,Task>? retryDelay = null) {
  var access = clipboard ?? new WindowsClipboardAccess(); ClipboardLease? lease = null;
  try {
   // Materialize every advertised format before replacing the clipboard, including rich text.
   // If a source cannot render its data, leave the clipboard alone and refuse automatic paste.
   var sequence = access.Sequence; var source = access.Read(); System.Windows.DataObject? snapshot = null;
   if (source != null) { snapshot = new(); foreach (var format in source.GetFormats(false)) { var value = source.GetData(format,false); if (value == null) return null; if (value is System.IO.MemoryStream stream) value = new System.IO.MemoryStream(stream.ToArray()); else if (value is byte[] bytes) value = bytes.ToArray(); snapshot.SetData(format,value,false); } }
   lease = new ClipboardLease(snapshot,access,retryDelay ?? (duration => Task.Delay(duration))); var payload = new System.Windows.DataObject(); payload.SetText(text); payload.SetData(Marker,lease.token);
   // Windows' documented marker excludes this short-lived transport from history and sync.
   payload.SetData("ExcludeClipboardContentFromMonitorProcessing",new System.IO.MemoryStream([0]));
   if (access.Sequence != sequence) return null;
   access.Write(payload); return lease;
  } catch (Exception e) when (ExpectedFailure(e)) { lease?.Dispose(); return null; }
 }
 public void Dispose() {
  if (disposed) return; disposed = true;
  if (!TryRestore()) Restoration = RestoreLater();
 }
 private bool TryRestore() {
  try {
   if (dispatcher != null && !dispatcher.CheckAccess()) return dispatcher.Invoke(TryRestore);
   if (access.GetData(Marker) as string != token) return true;
   if (previous != null) access.Write(previous); else access.Clear(); return true;
  } catch (Exception e) when (ExpectedFailure(e) || e is OperationCanceledException) { return false; }
 }
 private async Task RestoreLater() {
  // Short bounded retries keep the complete original snapshot. A newer owner always wins.
  try {
   foreach (var milliseconds in new[] {100,400,1500}) { await delay(TimeSpan.FromMilliseconds(milliseconds)); if (TryRestore()) return; }
   await delay(TimeSpan.FromSeconds(58));
   void Expire() { if (access.GetData(Marker) as string == token) access.Clear(); }
   if (dispatcher != null && !dispatcher.CheckAccess()) dispatcher.Invoke(Expire); else Expire();
  } catch (Exception e) when (ExpectedFailure(e) || e is OperationCanceledException) { /* Clipboard contention must never affect confirmed insertion. */ }
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
 private HwndSource? source; private const int Id = 0x514c; public event Action? Pressed; public bool Registered { get; private set; } public string Label { get; private set; } = "Ctrl + Win + Space"; public int RegistrationError { get; private set; }
 public const uint Modifiers = 0x4000 | 0x0002 | 0x0008;
 public static readonly IReadOnlyList<(string Label,uint Modifiers)> Bindings = new[] { ("Ctrl + Win + Space",Modifiers),("Ctrl + Alt + Space",0x4000U | 0x0002U | 0x0001U),("Ctrl + Shift + Space",0x4000U | 0x0002U | 0x0004U) };
 public HotkeyService(nint hwnd,string preferred = "Ctrl + Win + Space") { source = HwndSource.FromHwnd(hwnd); source?.AddHook(Hook); foreach (var binding in Bindings.OrderByDescending(b => b.Label == preferred)) { if (Native.RegisterHotKey(hwnd,Id,binding.Modifiers,0x20)) { Registered = true; Label = binding.Label; break; } RegistrationError = Marshal.GetLastWin32Error(); } }
 private nint Hook(nint hwnd, int message, nint wparam, nint lparam, ref bool handled) { if (message == 0x0312 && wparam == Id) { handled = true; Pressed?.Invoke(); } return 0; }
 public void Dispose() { if (source != null) { Native.UnregisterHotKey(source.Handle, Id); source.RemoveHook(Hook); source = null; } }
}
public static class StartupService {
 private const string PathKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
 public static void Set(bool enabled) { using var key = Registry.CurrentUser.CreateSubKey(PathKey); if (enabled) key.SetValue("Quill", "\"" + Environment.ProcessPath + "\" --background"); else key.DeleteValue("Quill", false); }
}
public static class Native {
 [DllImport("user32.dll")] public static extern uint GetClipboardSequenceNumber();
 [DllImport("user32.dll")] public static extern nint GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(nint hwnd, out uint processId);
 [DllImport("user32.dll",SetLastError=true)] public static extern bool RegisterHotKey(nint hwnd, int id, uint modifiers, uint key);
 [DllImport("user32.dll")] public static extern bool UnregisterHotKey(nint hwnd, int id);
 [DllImport("user32.dll")] public static extern short GetAsyncKeyState(int key);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern int GetClassName(nint hwnd,System.Text.StringBuilder name,int count);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern nint SendMessageTimeout(nint hwnd,uint message,nuint wparam,nint lparam,uint flags,uint timeout,out nuint result);
 [DllImport("user32.dll")] public static extern uint SendInput(uint count, INPUT[] inputs, int size);
 [DllImport("user32.dll", EntryPoint="GetWindowLongPtrW")] public static extern nint GetWindowLong(nint hwnd, int index);
 [DllImport("user32.dll", EntryPoint="SetWindowLongPtrW")] public static extern nint SetWindowLong(nint hwnd, int index, nint value);
 [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public InputUnion union; }
 [StructLayout(LayoutKind.Explicit)] public struct InputUnion { [FieldOffset(0)] public KEYBDINPUT keyboard; [FieldOffset(0)] public MOUSEINPUT mouse; }
 [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort virtualKey; public ushort scan; public uint flags; public uint time; public nuint extra; }
 [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int x; public int y; public uint data; public uint flags; public uint time; public nuint extra; }
 public static INPUT Unicode(char c, bool up) => new() { type = 1, union = new() { keyboard = new() { scan = c, flags = 0x0004u | (up ? 0x0002u : 0) } } };
 public static INPUT Key(ushort key,bool up) => new() { type = 1,union = new() { keyboard = new() { virtualKey = key,flags = up ? 2u : 0u } } };
 public static bool ModifierPressed() => new[] { 0x10, 0x11, 0x12, 0x5b, 0x5c }.Any(k => (GetAsyncKeyState(k) & 0x8000) != 0);
}
