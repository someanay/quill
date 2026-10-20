using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using Quill.Windows;
namespace Quill.NativeSmoke;
public record Command(string Field = "normal",string Text = "",int Start = 0,int Length = 0,bool ReadOnly = false,string Action = "prepare");
public record Reply(string Text,bool SensitiveUnchanged,long Handle);
public record ExternalInsertion(string App,string Expected,string Insert,string Output,bool ExpectSensitive = false,bool CopyDiagnostic = false,bool SurrogateDiagnostic = false);
public static class Program {
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool SetForegroundWindow(nint hwnd);
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool AttachThreadInput(uint from,uint to,bool attach);
 [System.Runtime.InteropServices.DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool SetCursorPos(int x,int y);
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern nint WindowFromPoint(NativePoint point);
 [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)] private struct NativePoint { public int X; public int Y; }
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern nint GetThreadDesktop(uint thread);
 [System.Runtime.InteropServices.DllImport("user32.dll",SetLastError=true)] private static extern nint OpenInputDesktop(uint flags,bool inherit,uint access);
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool CloseDesktop(nint desktop);
 [System.Runtime.InteropServices.DllImport("user32.dll",CharSet = System.Runtime.InteropServices.CharSet.Unicode)] private static extern bool GetUserObjectInformation(nint handle,int index,System.Text.StringBuilder value,int bytes,out int needed);
 private static string RunnerDesktopState(nint atPoint) {
  if (Environment.GetEnvironmentVariable("GITHUB_ACTIONS") != "true") return "local controlled fixture";
  string Name(nint desktop) { var value = new System.Text.StringBuilder(256); return desktop != 0 && GetUserObjectInformation(desktop,2,value,512,out _) ? value.ToString() : "unavailable"; }
  string Window(nint handle) { Native.GetWindowThreadProcessId(handle,out var id); var type = new System.Text.StringBuilder(256); Native.GetClassName(handle,type,type.Capacity); string process; try { using var p = Process.GetProcessById((int)id); process = p.ProcessName; } catch { process = "unavailable"; } return $"{handle}/{type}/{process}"; }
  var input = OpenInputDesktop(0,false,1); var error = System.Runtime.InteropServices.Marshal.GetLastWin32Error();
  try { return $"threadDesktop={Name(GetThreadDesktop(GetCurrentThreadId()))}, inputDesktop={Name(input)}, inputError={error}, foreground={Window(Native.GetForegroundWindow())}, atPoint={Window(atPoint)}, interactive={Environment.UserInteractive}, session={Process.GetCurrentProcess().SessionId}"; }
  finally { if (input != 0) CloseDesktop(input); }
 }
 private static void ActivateFixture(Window window) {
  var own = GetCurrentThreadId(); var other = Native.GetWindowThreadProcessId(Native.GetForegroundWindow(),out _); var attached = own != other && AttachThreadInput(own,other,true);
  try { SetForegroundWindow(new WindowInteropHelper(window).Handle); window.Activate(); } finally { if (attached) AttachThreadInput(own,other,false); }
 }
 [STAThread] public static int Main(string[] args) {
  if (args.Length == 3 && args[0] == "--generate-icon") { GenerateIcon(args[1],args[2]); return 0; }
  if (args.Length == 2 && args[0] == "--render-ui") { RenderUi(args[1]); return 0; }
  if (args.Length == 2 && args[0] == "--render-all-ui") { RenderAllUi(args[1]); return 0; }
  if (args.Length == 2 && args[0] == "--verify-setup-window") { VerifySetupWindow(args[1]); return 0; }
  if (args.Length == 2 && args[0] == "--hotkey-check") { _ = new Application(); var window = new Window(); var handle = new WindowInteropHelper(window).EnsureHandle(); using var hotkey = new HotkeyService(handle); var report = JsonSerializer.Serialize(new { registered = hotkey.Registered,actual = hotkey.Label,preferredError = hotkey.RegistrationError },new JsonSerializerOptions { WriteIndented = true }); File.WriteAllText(args[1],report); Console.WriteLine(report); return hotkey.Registered ? 0 : 1; }
  if (args.Length == 1 && args[0] == "--font-check") { _ = new Application(); foreach (var filename in new[] {"ibm_plex_sans.ttf","ibm_plex_serif.ttf"}) { var glyph = new System.Windows.Media.GlyphTypeface(new Uri("pack://application:,,,/Quill;component/Assets/Fonts/" + filename)); Console.WriteLine(filename + " => " + string.Join(",",glyph.FamilyNames.Values)); } foreach (var f in System.Windows.Media.Fonts.GetFontFamilies(new Uri("pack://application:,,,/Quill;component/"),"./Assets/Fonts/")) Console.WriteLine("Enumerated " + f.Source); foreach (var family in new[] {Theme.Sans,Theme.Serif}) { var typeface = new System.Windows.Media.Typeface(family,FontStyles.Normal,FontWeights.Normal,FontStretches.Normal); if (!typeface.TryGetGlyphTypeface(out var glyph)) throw new InvalidOperationException("Font resolution failed: " + family.Source); Console.WriteLine(family.Source + " => " + string.Join(",",glyph.FamilyNames.Values)); } return 0; }
  var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown }; int code = 0;
  // Start after the dispatcher installs its synchronization context. Pipe connections
  // can complete asynchronously on ARM, so starting directly inside Startup allowed
  // later window operations to resume on a worker thread.
  app.Startup += (_,_) => app.Dispatcher.BeginInvoke(new Action(async () => {
   try { if (args.Length == 4 && args[0] == "--activation-launcher") await ActivationLauncher(args[1],args[2],args[3]); else if (args.Length > 1 && args[0] == "--editor") await Editor(args[1]); else if (args.Length > 1 && args[0] == "--audio-check") await AudioCheck(args[1]); else if (args.Length > 1 && args[0] == "--audio-lifecycle-check") await AudioLifecycleCheck(args[1]); else if (args.Length > 1 && args[0] == "--external-insert") await ExternalInsert(args[1]); else await Run(args.Length > 0 ? args[0] : Path.Combine(AppContext.BaseDirectory,"native-smoke.json")); }
   catch (Exception e) { File.WriteAllText(Path.Combine(AppContext.BaseDirectory,"native-smoke-error.txt"),e.ToString()); Console.Error.WriteLine(e); code = 1; }
   finally { app.Shutdown(); }
  })); app.Run(); return code;
 }
 private static async Task ExternalInsert(string path) {
  var job = JsonSerializer.Deserialize<ExternalInsertion>(File.ReadAllText(path))!; if (!job.Expected.StartsWith("Quill ",StringComparison.Ordinal) || !new[] {"Notepad","chrome","brave","msedge"}.Contains(job.App,StringComparer.OrdinalIgnoreCase)) throw new InvalidOperationException("Only specifically named test fixtures are permitted.");
  var foreground = Native.GetForegroundWindow(); Native.GetWindowThreadProcessId(foreground,out var processId); using var testProcess = Process.GetProcessById((int)processId); if (!string.Equals(testProcess.ProcessName,job.App,StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("Test app does not own foreground; no input sent.");
  var injector = new TextInjector(); var target = await injector.Capture(); if (target.Window != foreground || !string.Equals(target.Application,job.App,StringComparison.OrdinalIgnoreCase) && !(job.ExpectSensitive && target.Sensitive)) throw new InvalidOperationException("Test app does not own foreground; no input sent.");
  if (job.ExpectSensitive) { var denied = target.Sensitive && !await injector.Insert(target,job.Insert); File.WriteAllText(job.Output,JsonSerializer.Serialize(new { app = job.App,sensitive = target.Sensitive,denied })); if (!denied) throw new InvalidOperationException("Sensitive test field was not refused."); return; }
  if (target.Context?.Text.TrimEnd('\r','\n') != job.Expected && !(job.CopyDiagnostic && target.Context?.Text.StartsWith("Quill ASCII fixture",StringComparison.Ordinal) == true)) { File.WriteAllText(job.Output,JsonSerializer.Serialize(new { app = job.App,editable = target.Editable,sensitive = target.Sensitive,contextAvailable = target.Context != null,capturedLength = target.Context?.Text.Length })); throw new InvalidOperationException("Controlled test text did not match; no input sent."); }
  if (job.CopyDiagnostic) {
   var previous = Clipboard.GetDataObject();
   try {
    var keys = new[] {Key(0x11,false),Key(0x41,false),Key(0x41,true),Key(0x11,true),Key(0x11,false),Key(0x43,false),Key(0x43,true),Key(0x11,true)};
    Native.SendInput((uint)keys.Length,keys,System.Runtime.InteropServices.Marshal.SizeOf<Native.INPUT>()); await Task.Delay(350);
    var copied = Clipboard.GetText(); var diagnosticReport = new { app = job.App,uiAutomation = target.Context.Text,copied,copiedMatchesUia = copied == target.Context.Text };
    File.WriteAllText(job.Output,JsonSerializer.Serialize(diagnosticReport,new JsonSerializerOptions { WriteIndented = true })); Console.WriteLine(JsonSerializer.Serialize(diagnosticReport));
   } finally { if (previous != null) Clipboard.SetDataObject(previous,true); else Clipboard.Clear(); }
   return;
  }
  if (job.SurrogateDiagnostic) {
   var inputs = new List<Native.INPUT>(); foreach (var rune in job.Insert.EnumerateRunes()) { var chars = rune.ToString(); foreach (var ch in chars) inputs.Add(Native.Unicode(ch,false)); foreach (var ch in chars) inputs.Add(Native.Unicode(ch,true)); }
   Native.SendInput((uint)inputs.Count,inputs.ToArray(),System.Runtime.InteropServices.Marshal.SizeOf<Native.INPUT>()); await Task.Delay(350); var actual = await injector.Capture(); var diagnosticReport = new { app = job.App,after = actual.Context?.Text }; File.WriteAllText(job.Output,JsonSerializer.Serialize(diagnosticReport)); Console.WriteLine(JsonSerializer.Serialize(diagnosticReport)); return;
  }
  var clipboardBefore = Clipboard.ContainsText() ? Clipboard.GetText() : null; var accepted = await injector.Insert(target,job.Insert); var after = await injector.Capture(); var clipboardPreserved = clipboardBefore == (Clipboard.ContainsText() ? Clipboard.GetText() : null); var report = new { app = job.App,accepted,clipboardPreserved,before = target.Context.Text,selection = new[] {target.Context.Start,target.Context.End},after = after.Window == target.Window ? after.Context?.Text : null }; File.WriteAllText(job.Output,JsonSerializer.Serialize(report,new JsonSerializerOptions { WriteIndented = true })); Console.WriteLine(JsonSerializer.Serialize(report));
 }
 private static Native.INPUT Key(ushort key,bool up) => new() { type = 1,union = new() { keyboard = new() { virtualKey = key,flags = up ? 2u : 0u } } };
 private static void VerifySetupWindow(string folder) {
  Directory.CreateDirectory(folder); var application = new Application();
  using var services = new AppServices(Path.Combine(folder,"data-" + Guid.NewGuid().ToString("N")),true);
  Theme.Apply("Dark"); var window = new MainWindow(services);
  var flags = System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic;
  typeof(MainWindow).GetMethod("ShowStep",flags)!.Invoke(window,[0]);
  services.Settings.Value.DictationShortcut = "Ctrl + Alt + Space"; window.RefreshStatus();
  var welcome = (FrameworkElement)window.Content; welcome.Measure(new Size(1200,800)); welcome.Arrange(new Rect(0,0,1200,800)); welcome.UpdateLayout();
  IEnumerable<DependencyObject> Walk(DependencyObject node) { yield return node; for (var i = 0;i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node);i++) foreach (var child in Walk(System.Windows.Media.VisualTreeHelper.GetChild(node,i))) yield return child; }
  var keycaps = Walk(welcome).OfType<WrapPanel>().Single(panel => System.Windows.Automation.AutomationProperties.GetAutomationId(panel) == "quill-shortcut-keys");
  var displayedShortcut = string.Join(" + ",Walk(keycaps).OfType<TextBlock>().Select(block => block.Text).Where(text => text != "+"));
  if (displayedShortcut != services.HotkeyLabel) throw new InvalidOperationException("Welcome keycaps did not follow the active shortcut.");
  services.Settings.Value.DictationShortcut = "Ctrl + Win + Space"; window.RefreshStatus();
  int rendered = 0;
  foreach (var viewport in new[] {new Size(640,600),new Size(1000,500),new Size(1200,800),new Size(1600,900),new Size(900,1200),new Size(1920,650)}) {
   window.Width = viewport.Width; window.Height = viewport.Height;
   for (int step = 0; step < 7; step++) {
    typeof(MainWindow).GetMethod("ShowStep",flags)!.Invoke(window,[step]);
    if (window.ResizeMode != ResizeMode.CanResize || !double.IsPositiveInfinity(window.MaxWidth)) throw new InvalidOperationException("Setup must remain resizable.");
    if (window.Width != viewport.Width || window.Height != viewport.Height) throw new InvalidOperationException("Setup changed the user's window size.");
    var root = (FrameworkElement)window.Content; root.Measure(viewport); root.Arrange(new Rect(new Point(),viewport)); root.UpdateLayout();
    VerifyDesktopLayout(window,"setup",viewport); rendered++;
   }
  }
  // Resizing must retain the same live key form, rather than disposing its validation/draft session.
  typeof(MainWindow).GetMethod("ShowStep",flags)!.Invoke(window,[2]);
  var page = (StackPanel)typeof(MainWindow).GetField("page",flags)!.GetValue(window)!;
  var form = page.Children.Cast<UIElement>().ToArray();
  var content = (FrameworkElement)window.Content; content.Measure(new Size(1600,900)); content.Arrange(new Rect(0,0,1600,900)); content.UpdateLayout();
  content.Measure(new Size(640,600)); content.Arrange(new Rect(0,0,640,600)); content.UpdateLayout();
  if (!form.SequenceEqual(page.Children.Cast<UIElement>())) throw new InvalidOperationException("Resize replaced the key form.");
  services.Settings.Value.OnboardingComplete = true; typeof(MainWindow).GetMethod("Navigate",flags)!.Invoke(window,["home"]);
  if (window.ResizeMode != ResizeMode.CanResize || window.Width != 1920 || window.Height != 650) throw new InvalidOperationException("Home must retain window size and resizing.");
  var editorField = typeof(MainWindow).GetField("practiceEditor",flags)!;
  var editor = (TextBox)editorField.GetValue(window)!; editor.Text = "An existing thought"; editor.Select(3,5);
  var stateProperty = typeof(DictationController).GetProperty("State")!;
  foreach (var state in new[] {DictationState.Recording,DictationState.Transcribing,DictationState.Idle}) {
   stateProperty.SetValue(services.Dictation,state); window.RefreshStatus();
   if (!ReferenceEquals(editor,editorField.GetValue(window)) || editor.Text != "An existing thought" || editor.SelectionStart != 3 || editor.SelectionLength != 5) throw new InvalidOperationException("Dictation status replaced the practice field or its selection.");
  }
  File.WriteAllText(Path.Combine(folder,"result.json"),JsonSerializer.Serialize(new { passed = true,rendered,steps = 7,viewports = 6,keyFormRetained = true,practiceEditorRetained = true,shortcutKeycapsRefresh = true }));
  Console.WriteLine($"PASS {rendered} setup layouts, resizable window, retained key form/practice editor, and Home size preservation."); application.Shutdown(); application.Dispatcher.InvokeShutdown();
 }
 private static void RenderUi(string folder) {
  Directory.CreateDirectory(folder); var app = new Application(); using var services = new AppServices(folder,true); services.Settings.Value.OnboardingComplete = true; services.Settings.Value.BubbleActive = false; Theme.Apply("Light"); var window = new MainWindow(services); var root = (FrameworkElement)window.Content; root.Measure(new Size(1060,700)); root.Arrange(new Rect(0,0,1060,700)); root.UpdateLayout(); var bmp = new System.Windows.Media.Imaging.RenderTargetBitmap(1060,700,96,96,System.Windows.Media.PixelFormats.Pbgra32); bmp.Render(root); var png = new System.Windows.Media.Imaging.PngBitmapEncoder(); png.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bmp)); using (var f = File.Create(Path.Combine(folder,"home.png"))) png.Save(f);
  var details = new List<object>(); void Visit(System.Windows.DependencyObject node) { if (node is TextBlock block) { var face = new System.Windows.Media.Typeface(block.FontFamily,block.FontStyle,block.FontWeight,block.FontStretch); face.TryGetGlyphTypeface(out var glyph); details.Add(new { text = block.Text,font = block.FontFamily.Source,weight = block.FontWeight.ToString(),resolved = glyph == null ? "none" : string.Join(",",glyph.FamilyNames.Values),actual = GlyphNames(System.Windows.Media.VisualTreeHelper.GetDrawing(block)) }); } for (var i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node); i++) Visit(System.Windows.Media.VisualTreeHelper.GetChild(node,i)); } Visit(root); File.WriteAllText(Path.Combine(folder,"font-tree.json"),JsonSerializer.Serialize(details,new JsonSerializerOptions { WriteIndented = true })); Console.WriteLine(File.ReadAllText(Path.Combine(folder,"font-tree.json"))); app.Shutdown();
 }
 private static void RenderAllUi(string folder) {
  Directory.CreateDirectory(folder); var app = new Application(); using var services = new AppServices(Path.Combine(folder,"sessions",Guid.NewGuid().ToString("N")),true); services.Settings.Value.OnboardingComplete = true; Theme.Apply("Light"); var window = new MainWindow(services); services.History.Add("Please send the revised proposal by Thursday. I will review it before our meeting.","notepad",12000,"inserted","Groq",services.Settings.Value); services.History.Add(string.Join("\n\n",Enumerable.Repeat("A long transcript remains readable while the list of dictations stays in view. This fixture checks selection, wrapping and scrolling in the desktop history browser.",12)),"word",48000,"inserted","Groq",services.Settings.Value); var results = new List<object>(); var flags = System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic;
  foreach (var viewport in new[] { new Size(640,600), new Size(1000,500),new Size(1200,800), new Size(1600,900), new Size(900,1200), new Size(1920,650) })
  foreach (var theme in new[] { "Light","Dark" }) {
   window.Width = viewport.Width; window.Height = viewport.Height; services.Settings.Value.Theme = theme; Theme.Apply(theme);
   typeof(MainWindow).GetMethod("Navigate",flags)!.Invoke(window,["home"]);
   foreach (var route in new[] { "home","history","settings","speech","keys","cleanup","style","words","audio","privacy","bubble","advanced","about" }) { var navigation = (StackPanel)typeof(MainWindow).GetField("nav",flags)!.GetValue(window)!; var destination = navigation.Children.OfType<Button>().Single(button => Equals(button.Tag,route)); destination.RaiseEvent(new RoutedEventArgs(Button.ClickEvent)); if (!Equals(typeof(MainWindow).GetField("route",flags)!.GetValue(window),route)) throw new InvalidOperationException("Navigation failed: " + route); var root = (FrameworkElement)window.Content; root.Measure(viewport); root.Arrange(new Rect(new Point(),viewport)); root.UpdateLayout(); VerifyDesktopLayout(window,route,viewport); var bitmap = new System.Windows.Media.Imaging.RenderTargetBitmap((int)viewport.Width,(int)viewport.Height,96,96,System.Windows.Media.PixelFormats.Pbgra32); bitmap.Render(root); var png = new System.Windows.Media.Imaging.PngBitmapEncoder(); png.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap)); using var file = File.Create(Path.Combine(folder,viewport.Width + "x" + viewport.Height + "-" + theme + "-" + route + ".png")); png.Save(file); results.Add(new { viewport,theme,route,passed = true }); }
   void SaveScreen(string name) { var root = (System.Windows.Controls.Panel)window.Content; root.SetResourceReference(System.Windows.Controls.Panel.BackgroundProperty,"Paper"); root.Measure(viewport); root.Arrange(new Rect(new Point(),viewport)); root.UpdateLayout(); var bitmap = new System.Windows.Media.Imaging.RenderTargetBitmap((int)viewport.Width,(int)viewport.Height,96,96,System.Windows.Media.PixelFormats.Pbgra32); bitmap.Render(root); var png = new System.Windows.Media.Imaging.PngBitmapEncoder(); png.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap)); using var file = File.Create(Path.Combine(folder,viewport.Width + "x" + viewport.Height + "-" + theme + "-" + name + ".png")); png.Save(file); }
   for (var step = 0;step < 7;step++) { typeof(MainWindow).GetMethod("ShowStep",flags)!.Invoke(window,[step]); SaveScreen("onboarding-" + step); VerifyDesktopLayout(window,"setup",viewport); results.Add(new { viewport,theme,step,passed = true }); }
   foreach (var provider in Enum.GetValues<Provider>()) { new KeySetupDraftStore(services.Folder).Expand(provider.ToString()); typeof(MainWindow).GetMethod("ShowStep",flags)!.Invoke(window,[2]); SaveScreen("guided-" + provider); VerifyDesktopLayout(window,"setup",viewport); results.Add(new { viewport,theme,provider,passed = true }); }
  }
  File.WriteAllText(Path.Combine(folder,"render-results.json"),JsonSerializer.Serialize(results,new JsonSerializerOptions { WriteIndented = true })); Console.WriteLine("PASS " + results.Count + " complete native WPF screen renders"); app.Shutdown(); app.Dispatcher.InvokeShutdown();
 }
 private static void VerifyDesktopLayout(MainWindow window,string route,Size viewport) {
  IEnumerable<DependencyObject> Descendants(DependencyObject parent) {
   for (int i = 0;i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(parent);i++) {
    var child = System.Windows.Media.VisualTreeHelper.GetChild(parent,i); yield return child;
    foreach (var descendant in Descendants(child)) yield return descendant;
   }
  }
  var root = (FrameworkElement)window.Content;
  var controls = Descendants(root).ToArray();
  if (route == "history") {
   var list = controls.OfType<ListBox>().Single();
   if (list.Items.Cast<ListBoxItem>().Any(item => item.ActualHeight > 100)) throw new InvalidOperationException("A multiline transcript expanded the history list row.");
   foreach (ListBoxItem item in list.Items) {
    list.SelectedItem = item; root.UpdateLayout();
    var transcript = Descendants(root).OfType<TextBox>().Single(box => box.IsReadOnly);
    if (transcript.Text != ((HistoryEntry)item.Tag).Text) throw new InvalidOperationException("History selection did not show the complete selected transcript.");
    var position = transcript.TranslatePoint(new Point(),root);
    if (position.X + transcript.ActualWidth > viewport.Width + 1 || position.Y + transcript.ActualHeight > viewport.Height + 1) throw new InvalidOperationException("History reader extends outside the window.");
   }
   list.SelectedIndex = 0; root.UpdateLayout();
  }
  bool Rendered(FrameworkElement control) {
   if (control.ActualWidth <= 0 || control.ActualHeight <= 0) return false;
   for (DependencyObject? node = control; node != null; node = System.Windows.Media.VisualTreeHelper.GetParent(node))
    if (node is UIElement element && element.Visibility != Visibility.Visible) return false;
   return true;
  }
  foreach (var button in controls.OfType<Control>().Where(Rendered)) {
   var point = button.TranslatePoint(new Point(),root);
   if (point.X < -1 || point.X + button.ActualWidth > viewport.Width + 1) throw new InvalidOperationException("A button extends horizontally outside " + route);
  }
 }
 private static string GlyphNames(System.Windows.Media.Drawing? drawing) => drawing switch { System.Windows.Media.GlyphRunDrawing glyph => string.Join(",",glyph.GlyphRun.GlyphTypeface.FamilyNames.Values), System.Windows.Media.DrawingGroup group => string.Join(";",group.Children.Select(GlyphNames)), _ => "" };
 private static async Task AudioCheck(string output) {
  using var recorder = new AudioRecorder(); var frames = 0; var peakRms = 0.0; recorder.Level += (rms,_) => { frames++; peakRms = Math.Max(peakRms,rms); };
  recorder.Start(new QuillSettings()); await Task.Delay(3100); var take = await recorder.Stop(); var passed = frames > 0 && take.Wav.Length > 32000 && BitConverter.ToInt32(take.Wav,24) == 16000;
  Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output))!); File.WriteAllText(output,JsonSerializer.Serialize(new { created = DateTimeOffset.UtcNow,passed,frames,peakRms,bytes = take.Wav.Length,durationMs = take.DurationMs,format = "16 kHz mono 16-bit WAV",audioPersisted = false,networkRequests = 0 },new JsonSerializerOptions { WriteIndented = true }));
  Console.WriteLine($"Microphone capture {(passed ? "PASS" : "FAIL")}: {frames} PCM frames, {take.Wav.Length} WAV bytes. Audio discarded; no upload."); if (!passed) throw new InvalidOperationException("No microphone frames captured");
 }
 private static async Task AudioLifecycleCheck(string output) {
  using var recorder = new AudioRecorder(); var frames = 0; var autoStops = 0; var peakRms = 0.0; var signal = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
  recorder.Level += (rms,_) => { Interlocked.Increment(ref frames); peakRms = Math.Max(peakRms,rms); };
  recorder.AutoStop += () => { Interlocked.Increment(ref autoStops); signal.TrySetResult(); };
  recorder.Start(new QuillSettings()); var silenceObserved = await Task.WhenAny(signal.Task,Task.Delay(8000)) == signal.Task; var take = await recorder.Stop(); var stoppedFrames = frames; await Task.Delay(250); var stoppedCleanly = !recorder.Recording && frames == stoppedFrames;
  recorder.Start(new QuillSettings()); await Task.Delay(750); var cancelled = await recorder.Stop(); var cancelledFrames = frames; await Task.Delay(250); var cancelledCleanly = !recorder.Recording && frames == cancelledFrames; var secondStop = await recorder.Stop();
  var passed = stoppedFrames > 0 && take.Wav.Length > 32000 && stoppedCleanly && cancelledCleanly && cancelled.Wav.Length > 3200 && secondStop.Wav.Length == 44 && autoStops <= 1;
  var report = new { created = DateTimeOffset.UtcNow,passed,frames,peakRms,silenceAutoStopObserved = silenceObserved,autoStopEvents = autoStops,firstDurationMs = take.DurationMs,firstBytes = take.Wav.Length,stoppedCleanly,cancelledCleanly,cancelledDurationMs = cancelled.DurationMs,repeatedStopEmpty = secondStop.Wav.Length == 44,audioPersisted = false,networkRequests = 0 };
  Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output))!); File.WriteAllText(output,JsonSerializer.Serialize(report,new JsonSerializerOptions { WriteIndented = true })); Array.Clear(take.Wav); Array.Clear(cancelled.Wav);
  Console.WriteLine(JsonSerializer.Serialize(report)); if (!passed) throw new InvalidOperationException("Microphone lifecycle check failed; see report.");
 }
 private static void GenerateIcon(string source,string output) {
  var xml = System.Xml.Linq.XDocument.Load(source); System.Xml.Linq.XNamespace android = "http://schemas.android.com/apk/res/android";
  var drawing = new System.Windows.Media.DrawingVisual(); using (var dc = drawing.RenderOpen()) { dc.DrawRoundedRectangle(Theme.Hex("#F5F7F5"),null,new Rect(0,0,256,256),50,50); dc.PushTransform(new System.Windows.Media.ScaleTransform(256.0/108,256.0/108)); foreach (var path in xml.Root!.Elements("path")) dc.DrawGeometry(Theme.Hex(path.Attribute(android+"fillColor")!.Value),new System.Windows.Media.Pen(Theme.Hex(path.Attribute(android+"strokeColor")!.Value),.42),System.Windows.Media.Geometry.Parse(path.Attribute(android+"pathData")!.Value)); dc.Pop(); }
  var image = new System.Windows.Media.Imaging.RenderTargetBitmap(256,256,96,96,System.Windows.Media.PixelFormats.Pbgra32); image.Render(drawing); var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder(); encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(image)); using var png = new MemoryStream(); encoder.Save(png); var bytes = png.ToArray(); Directory.CreateDirectory(output); File.WriteAllBytes(Path.Combine(output,"quill.png"),bytes);
  using var file = File.Create(Path.Combine(output,"quill.ico")); using var writer = new BinaryWriter(file); writer.Write((short)0); writer.Write((short)1); writer.Write((short)1); writer.Write((byte)0); writer.Write((byte)0); writer.Write((byte)0); writer.Write((byte)0); writer.Write((short)1); writer.Write((short)32); writer.Write(bytes.Length); writer.Write(22); writer.Write(bytes);
 }
 private static async Task ActivationLauncher(string pipeName,string executable,string folder) {
  if (!string.Equals(Path.GetFullPath(executable),Path.Combine(AppContext.BaseDirectory,"Quill.exe"),StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException("The launcher fixture can only open its adjacent Quill test build.");
  var window = new Window { Title = "Quill shortcut launcher fixture",Width = 400,Height = 180,WindowStartupLocation = WindowStartupLocation.CenterScreen,Topmost = true,Content = new TextBlock { Text = "Controlled foreground shortcut launcher",Margin = new(24) } }; window.Show();
  using var pipe = new NamedPipeClientStream(".",pipeName,PipeDirection.InOut,PipeOptions.Asynchronous); await pipe.ConnectAsync(10_000); using var reader = new StreamReader(pipe); using var writer = new StreamWriter(pipe) { AutoFlush = true };
  while (await reader.ReadLineAsync() is string command) {
   if (command == "quit") break;
   if (command != "launch") throw new InvalidOperationException("Unexpected launcher command.");
   ActivateFixture(window); var handle = new WindowInteropHelper(window).Handle;
   await System.Windows.Threading.Dispatcher.Yield(System.Windows.Threading.DispatcherPriority.ContextIdle);
   // A native click on the owned fixture models selecting a shortcut even on
   // runners that do not initially give programmatically shown windows focus.
   var point = window.PointToScreen(new Point(100,80));
   var atPoint = WindowFromPoint(new NativePoint { X = (int)point.X,Y = (int)point.Y });
   Native.GetWindowThreadProcessId(atPoint,out var atPointProcess);
   if (atPointProcess != Environment.ProcessId) throw new InvalidOperationException("The controlled launcher is occluded; no input was sent. " + RunnerDesktopState(atPoint));
   if (!SetCursorPos((int)point.X,(int)point.Y)) throw new InvalidOperationException("Could not position the controlled fixture click.");
   var click = new[] { new Native.INPUT { type = 0,union = new() { mouse = new() { flags = 0x0002 } } },new Native.INPUT { type = 0,union = new() { mouse = new() { flags = 0x0004 } } } };
   if (Native.SendInput((uint)click.Length,click,System.Runtime.InteropServices.Marshal.SizeOf<Native.INPUT>()) != click.Length) throw new InvalidOperationException("The controlled fixture click was not delivered.");
   for (var attempt = 0; Native.GetForegroundWindow() != handle && attempt < 30; attempt++) await Task.Delay(100);
   if (Native.GetForegroundWindow() != handle) throw new InvalidOperationException($"The controlled shortcut launcher did not own foreground. interactive={Environment.UserInteractive}, session={Process.GetCurrentProcess().SessionId}, visible={window.IsVisible}, expected={handle}, actual={Native.GetForegroundWindow()}");
   // Deliver a harmless key to this fixture before invoking its shortcut. Merely
   // attaching input queues to show a test window does not model the input that
   // gives a real Start-menu launcher permission to activate another process.
   var input = new[] { Key(0x87,false),Key(0x87,true) }; // F24, handled only by this fixture.
   if (Native.SendInput((uint)input.Length,input,System.Runtime.InteropServices.Marshal.SizeOf<Native.INPUT>()) != input.Length) throw new InvalidOperationException("The controlled launcher did not receive its activation input.");
   await System.Windows.Threading.Dispatcher.Yield(System.Windows.Threading.DispatcherPriority.Background);
   if (Native.GetForegroundWindow() != handle) throw new InvalidOperationException("Foreground changed before the controlled shortcut launch.");
   var start = new ProcessStartInfo(executable) { UseShellExecute = true,WindowStyle = ProcessWindowStyle.Normal }; start.ArgumentList.Add("--data-dir"); start.ArgumentList.Add(folder);
   using var child = Process.Start(start) ?? throw new InvalidOperationException("Shortcut child did not start."); await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(15));
   await writer.WriteLineAsync(JsonSerializer.Serialize(new { foregroundLauncher = true,exitCode = child.ExitCode,launcherHandle = handle.ToInt64() }));
  }
  window.Close();
 }
 private static async Task Editor(string pipeName) {
  var normal = new TextBox { MinHeight = 60, Margin = new(12), AcceptsReturn = true }; var alternate = new TextBox { MinHeight = 60,Margin = new(12) }; var sensitive = new PasswordBox { MinHeight = 50,Margin = new(12),Password = "fixture-secret" };
  System.Windows.Automation.AutomationProperties.SetName(normal,"Controlled normal editor"); System.Windows.Automation.AutomationProperties.SetName(alternate,"Controlled alternate editor"); System.Windows.Automation.AutomationProperties.SetName(sensitive,"Controlled password editor");
  var panel = new StackPanel(); panel.Children.Add(new TextBlock { Text = "Quill native integration fixture — test content only",Margin = new(12) }); panel.Children.Add(normal); panel.Children.Add(alternate); panel.Children.Add(sensitive);
  var window = new Window { Title = "Quill native integration fixture",Width = 560,Height = 340,Content = panel,WindowStartupLocation = WindowStartupLocation.CenterScreen };
  window.Show(); using var pipe = new NamedPipeClientStream(".",pipeName,PipeDirection.InOut,PipeOptions.Asynchronous); await pipe.ConnectAsync(10_000); using var reader = new StreamReader(pipe); using var writer = new StreamWriter(pipe) { AutoFlush = true };
  while (await reader.ReadLineAsync() is string line) {
   var command = JsonSerializer.Deserialize<Command>(line)!; if (command.Action == "quit") break;
   if (command.Action == "prepare") { ActivateFixture(window); if (command.Field == "password") sensitive.Focus(); else { var field = command.Field == "alternate" ? alternate : normal; field.Text = command.Text; field.IsReadOnly = command.ReadOnly; field.Select(command.Start,command.Length); field.Focus(); } await Task.Delay(250); }
   await writer.WriteLineAsync(JsonSerializer.Serialize(new Reply(normal.Text,sensitive.Password == "fixture-secret",new WindowInteropHelper(window).Handle.ToInt64())));
  }
  window.Close();
 }
 private static async Task Run(string output) {
  var pipeName = "Quill.NativeSmoke." + Guid.NewGuid().ToString("N"); using var pipe = new NamedPipeServerStream(pipeName,PipeDirection.InOut,1,PipeTransmissionMode.Byte,PipeOptions.Asynchronous);
  using var child = Process.Start(new ProcessStartInfo(Environment.ProcessPath!,"--editor " + pipeName) { UseShellExecute = false,CreateNoWindow = true }) ?? throw new InvalidOperationException("Fixture launch failed");
  var results = new List<object>();
  try {
   await pipe.WaitForConnectionAsync().WaitAsync(TimeSpan.FromSeconds(15)); using var reader = new StreamReader(pipe); using var writer = new StreamWriter(pipe) { AutoFlush = true };
   async Task<Reply> Send(Command command) { await writer.WriteLineAsync(JsonSerializer.Serialize(command)); var line = await reader.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)); return JsonSerializer.Deserialize<Reply>(line!)!; }
   var injector = new TextInjector();
   async Task Check(string name,Func<Task<bool>> check) { var passed = await check(); results.Add(new { name,passed }); Console.WriteLine((passed ? "PASS " : "FAIL ") + name); }
   await Check("append insertion preserves text and adds Android separator",async () => { var prepared = await Send(new(Text:"hello",Start:5)); var target = await injector.Capture(); for (int i = 0;i < 5 && target.Application != "Quill.NativeSmoke";i++) { await Task.Delay(500); target = await injector.Capture(); } if (target.Application != "Quill.NativeSmoke") throw new InvalidOperationException($"Fixture did not own focus; no input sent. Expected hwnd {prepared.Handle}; foreground {Native.GetForegroundWindow()}; capture {target.Window}; sensitive {target.Sensitive}; editable {target.Editable}"); var accepted = await injector.Insert(target,"Quill"); var result = await Send(new(Action:"read")); return accepted && result.Text == "hello Quill"; });
   await Check("middle insertion adds no Android separator",async () => { await Send(new(Text:"hello world",Start:5)); return await injector.Insert(await injector.Capture(),"Quill") && (await Send(new(Action:"read"))).Text == "helloQuill world"; });
   await Check("selection replacement preserves surrounding text",async () => { await Send(new(Text:"before replace after",Start:7,Length:7)); var target = await injector.Capture(); var accepted = await injector.Insert(target,"Quill"); return accepted && (await Send(new(Action:"read"))).Text == "before Quill after"; });
   await Check("Unicode insertion preserves multilingual text",async () => { await Send(new(Text:"hello ",Start:6)); var accepted = await injector.Insert(await injector.Capture(),"नमस्ते 🌙"); return accepted && (await Send(new(Action:"read"))).Text == "hello नमस्ते 🌙"; });
   await Check("password blocked before reading or writing",async () => { await Send(new(Field:"password")); var target = await injector.Capture(); var accepted = await injector.Insert(target,"never"); return target.Sensitive && !accepted && (await Send(new(Action:"read"))).SensitiveUnchanged; });
   await Check("focus changes prevent insertion into another field",async () => { await Send(new(Text:"original",Start:8)); var target = await injector.Capture(); await Send(new(Field:"alternate",Text:"other")); var accepted = await injector.Insert(target,"never"); return !accepted && (await Send(new(Action:"read"))).Text == "original"; });
   await Check("read-only field rejected",async () => { await Send(new(Text:"read only",ReadOnly:true)); var target = await injector.Capture(); return !target.Editable && !await injector.Insert(target,"never") && (await Send(new(Action:"read"))).Text == "read only"; });
   await Check("same-field caret movement prevents stale insertion",async () => { await Send(new(Text:"original",Start:8)); var target = await injector.Capture(); await Send(new(Text:"original",Start:2)); return !await injector.Insert(target,"never") && (await Send(new(Action:"read"))).Text == "original"; });
   await Check("same-field text edits prevent stale insertion",async () => { await Send(new(Text:"original",Start:8)); var target = await injector.Capture(); await Send(new(Text:"edited words",Start:8)); return !await injector.Insert(target,"never") && (await Send(new(Action:"read"))).Text == "edited words"; });
   // Clipboard ownership is checked using test-only sentinels, restoring pre-existing data afterward.
   var previous = Clipboard.GetDataObject();
   try { var clip = new ClipboardService(); await Check("clipboard fallback retains exact Unicode text",() => Task.FromResult(clip.Copy("fixture नमस्ते 🌙") && Clipboard.GetText() == "fixture नमस्ते 🌙")); Clipboard.SetText("new clipboard owned by fixture"); await Check("clipboard owner marker disappears on user replacement",() => Task.FromResult(!Clipboard.ContainsData("Quill.Dictation.Owner")));
    await Check("paste transaction restores original text and rich data",() => { var original = new DataObject(); original.SetText("fixture original clipboard"); original.SetData(DataFormats.Rtf,"{\\rtf1 fixture rich}",false); Clipboard.SetDataObject(original,true); using (var lease = ClipboardLease.TryCreate("fixture नमस्ते 🌙")) { if (lease == null || !lease.OwnsClipboard || Clipboard.GetText() != "fixture नमस्ते 🌙") return Task.FromResult(false); } return Task.FromResult(Clipboard.GetText() == "fixture original clipboard" && Clipboard.GetData(DataFormats.Rtf) as string == "{\\rtf1 fixture rich}"); });
    await Check("paste transaction excludes temporary text from Windows history",() => { using var lease = ClipboardLease.TryCreate("fixture hidden from clipboard history"); return Task.FromResult(lease != null && Clipboard.ContainsData("ExcludeClipboardContentFromMonitorProcessing")); });
    await Check("paste transaction preserves a newer clipboard owner",() => { using (var lease = ClipboardLease.TryCreate("fixture temporary clipboard")) { if (lease == null) return Task.FromResult(false); Clipboard.SetText("fixture newer clipboard"); } return Task.FromResult(Clipboard.GetText() == "fixture newer clipboard"); });
   }
   finally { try { if (previous != null) Clipboard.SetDataObject(previous,true); else Clipboard.Clear(); } catch (System.Runtime.InteropServices.COMException) { } }
   await writer.WriteLineAsync(JsonSerializer.Serialize(new Command(Action:"quit"))); await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
   Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output))!); File.WriteAllText(output,JsonSerializer.Serialize(new { platform = Environment.OSVersion.VersionString,created = DateTimeOffset.UtcNow,cases = results },new JsonSerializerOptions { WriteIndented = true }));
   if (results.Any(r => !JsonSerializer.SerializeToElement(r).GetProperty("passed").GetBoolean())) throw new InvalidOperationException("Native smoke failures; see report");
  } finally { if (!child.HasExited) child.Kill(); }
 }
}
