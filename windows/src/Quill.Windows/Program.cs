using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Threading;
namespace Quill.Windows;
public static class Program {
 [STAThread] public static int Main(string[] args) {
  var dataArg = Array.IndexOf(args, "--data-dir");
  var folder = dataArg >= 0 && dataArg + 1 < args.Length ? Path.GetFullPath(args[dataArg + 1]) : Environment.GetEnvironmentVariable("QUILL_DATA_DIR") ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Quill", "NativeV2");
  Directory.CreateDirectory(folder); bool isolated = dataArg >= 0 || Environment.GetEnvironmentVariable("QUILL_DATA_DIR") != null;
  var mutexName = "Local\\Quill.NativeV2." + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(folder)))[..16];
  using var mutex = new Mutex(true, mutexName, out var created);
  if (!created) return 0;
  var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
  AppServices? services = null;
  try {
   services = new AppServices(folder, isolated); Theme.Apply(services.Settings.Value.Theme);
   var window = new MainWindow(services); app.MainWindow = window; services.MainWindow = window;
   services.InitializeShell(window);
   app.DispatcherUnhandledException += (_, e) => { services.RecordCrash(e.Exception); e.Handled = true; services.Notice("unexpected_error"); };
   AppDomain.CurrentDomain.UnhandledException += (_, e) => { if (e.ExceptionObject is Exception error) services.RecordCrash(error); };
   if (!args.Contains("--background") || !services.Settings.Value.OnboardingComplete) window.Show();
   if (services.Settings.Value.OnboardingComplete && services.Settings.Value.BubbleActive && services.Settings.Value.BubbleVisible) services.ShowBubble();
   app.Run(); return 0;
  } catch (Exception e) { services?.RecordCrash(e); MessageBox.Show("Quill could not start. Check that its files are intact and your local storage is available.", "Quill"); return 1; }
  finally { services?.Dispose(); }
 }
}
public sealed class AppServices : IDisposable {
 public string Folder { get; } public bool Isolated { get; }
 public CopyCatalog Copy { get; } = new(); public SettingsStore Settings { get; } public SecretStore Secrets { get; } public HistoryStore History { get; }
 public ProviderClient Provider { get; } = new(); public ClipboardService Clipboard { get; } = new(); public ITextInjector Injector { get; } = new TextInjector();
 public AudioRecorder Recorder { get; } = new(); public DictationController Dictation { get; }
 public MainWindow? MainWindow { get; set; } public BubbleWindow? Bubble { get; private set; }
 private System.Windows.Forms.NotifyIcon? tray; private HotkeyService? hotkey; private readonly DispatcherTimer prune; private CancellationTokenSource? autoReturn; public bool HotkeyAvailable => hotkey?.Registered == true;
 public AppServices(string folder, bool isolated) {
  Folder = folder; Isolated = isolated; Settings = new(folder); Secrets = new(folder); History = new(folder);
  Dictation = new(Settings, Secrets, History, Provider, Recorder, Injector, Clipboard, folder);
  Recorder.AutoStop += () => Application.Current.Dispatcher.BeginInvoke(async () => await Dictation.Toggle());
  Dictation.Changed += () => Application.Current.Dispatcher.BeginInvoke(() => { MainWindow?.RefreshStatus(); Bubble?.Refresh(); });
  Settings.Changed += () => { Theme.Apply(Settings.Value.Theme); Bubble?.Refresh(); History.Prune(Settings.Value); };
  prune = new DispatcherTimer { Interval = TimeSpan.FromMinutes(1) }; prune.Tick += (_, _) => { try { History.Prune(Settings.Value); } catch (IOException) { } }; prune.Start();
 }
 public string C(string key, string fallback, params object[] args) => Copy.All.ContainsKey(key) ? Copy.Get(key, args) : fallback;
 public void InitializeShell(MainWindow window) {
  var handle = new WindowInteropHelper(window).EnsureHandle(); hotkey = new(handle); hotkey.Pressed += () => Application.Current.Dispatcher.BeginInvoke(async () => { await Task.Delay(130); await Dictation.Toggle(); });
  tray = new System.Windows.Forms.NotifyIcon { Text = "Quill", Icon = MakeIcon(), Visible = true };
  var menu = new System.Windows.Forms.ContextMenuStrip();
  menu.Items.Add(C("windows.tray.open", "Open Quill"), null, (_, _) => ShowMain());
  menu.Items.Add(C("windows.tray.dictate", "Start / stop dictation"), null, async (_, _) => await Dictation.Toggle());
  menu.Items.Add(C("home.show_bubble", "Show bubble"), null, (_, _) => ShowBubble());
  menu.Items.Add(C("windows.tray.hide", "Hide bubble"), null, (_, _) => HideBubble());
  menu.Items.Add(C("windows.tray.quit", "Quit Quill"), null, async (_, _) => { await Dictation.Cancel(); Application.Current.Shutdown(); });
  tray.ContextMenuStrip = menu; tray.DoubleClick += (_, _) => ShowMain();
 }
 private static System.Drawing.Icon MakeIcon() {
  using var bitmap = new System.Drawing.Bitmap(32,32); using var g = System.Drawing.Graphics.FromImage(bitmap); g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias; g.Clear(System.Drawing.Color.Transparent);
  using var fill = new System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(74,63,208)); g.FillEllipse(fill, 1,1,30,30); using var pen = new System.Drawing.Pen(System.Drawing.Color.White, 2); g.DrawLine(pen, 11,23,23,7); g.DrawLine(pen, 11,23,12,15); g.DrawLine(pen, 11,23,19,20);
  var handle = bitmap.GetHicon(); try { return (System.Drawing.Icon)System.Drawing.Icon.FromHandle(handle).Clone(); } finally { DestroyIcon(handle); }
 }
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool DestroyIcon(nint handle);
 public void ShowMain() { if (MainWindow == null) return; MainWindow.Show(); MainWindow.WindowState = WindowState.Normal; MainWindow.Activate(); }
 public void ShowBubble(bool persist = true) { if (persist) { Settings.Value.BubbleActive = true; Settings.Value.BubbleVisible = true; Settings.Save(); } Bubble ??= new(this); Bubble.Show(); Bubble.Refresh(); MainWindow?.RefreshStatus(); }
 public void HideBubble() { autoReturn?.Cancel(); Settings.Value.BubbleVisible = false; Settings.Save(); Bubble?.Hide(); MainWindow?.RefreshStatus(); }
 public async void ScheduleAutoReturn() { autoReturn?.Cancel(); autoReturn?.Dispose(); autoReturn = new(); try { await Task.Delay(TimeSpan.FromMinutes(10),autoReturn.Token); if (Settings.Value.BubbleActive) ShowBubble(); } catch (OperationCanceledException) { } }
 public void ResetBubblePosition() { Bubble?.Close(); Bubble = null; if (Settings.Value.BubbleVisible && Settings.Value.BubbleActive) ShowBubble(); }
 public void Notice(string code) { MainWindow?.SetNotice(ErrorText(code)); }
 public string ErrorText(string code) => C("windows.error." + code, code switch {
  "key_missing" => "Add an active key for the selected provider in API keys.", "bad_key" => "That key was rejected. Check you copied it fully, then replace it in API keys.", "quota" => "Your provider's quota or credits are exhausted. Check your provider account, then retry.", "rate_limit" => "Your provider is receiving too many requests. Wait a moment, then retry.", "network" => "Couldn't reach your provider. Check your connection, then retry. Your recording is saved.", "timeout" => "Your provider took too long. Retry the saved recording.", "empty_transcript" => "Didn't catch that. Try speaking again.", "empty_recording" => "That recording was too short. Try speaking again.", "microphone_missing" => "Connect a microphone, then try again.", "microphone_denied" => "Allow desktop microphone access in Windows Settings, then try again.", "microphone_error" => "The microphone stopped unexpectedly. Reconnect it and try again.", "sensitive_field" => "Dictation is off in password and sensitive fields.", "setup_required" => "Complete setup and allow text insertion before dictating.", "focus_to_paste" => "Click an editable text field, then tap the bubble to paste.", "recording_recovered" => "A previous recording is saved. Retry it or discard it.", "model_missing" => "Choose a model in Speech & model.", "invalid_configuration" => "Check the HTTPS server URL and model in Speech & model.", "key_unavailable" => "Windows couldn't unlock the saved key. Add it again in API keys.", "storage_error" => "Local storage is unavailable. Your current recording remains in memory.", "redirect_blocked" => "The provider redirected the request. Check the configured endpoint.", "invalid_response" => "The provider returned an unreadable response. Retry or choose another model.", _ => "Something interrupted the dictation. Retry the recording or discard it." });
 public void RecordCrash(Exception exception) {
  if (!Settings.Value.CrashReportingEnabled) return;
  try { File.WriteAllText(Path.Combine(Folder, "crash-report.txt"), $"Quill Windows 2.0.0\nOS: {Environment.OSVersion.Version}\nTime: {DateTimeOffset.UtcNow:O}\nError type: {exception.GetType().FullName}\n" + string.Join('\n', new StackTrace(exception, false).GetFrames().Select(f => f.GetMethod()).Where(m => m?.DeclaringType?.Namespace?.StartsWith("Quill", StringComparison.Ordinal) == true).Select(m => m!.DeclaringType!.FullName + "." + m.Name))); } catch (IOException) { }
 }
 public void Dispose() { autoReturn?.Cancel(); autoReturn?.Dispose(); prune.Stop(); hotkey?.Dispose(); tray?.Dispose(); Bubble?.Close(); Dictation.Dispose(); }
}
