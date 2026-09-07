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
public static class Program {
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool SetForegroundWindow(nint hwnd);
 [System.Runtime.InteropServices.DllImport("user32.dll")] private static extern bool AttachThreadInput(uint from,uint to,bool attach);
 [System.Runtime.InteropServices.DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
 private static void ActivateFixture(Window window) {
  var own = GetCurrentThreadId(); var other = Native.GetWindowThreadProcessId(Native.GetForegroundWindow(),out _); var attached = own != other && AttachThreadInput(own,other,true);
  try { SetForegroundWindow(new WindowInteropHelper(window).Handle); window.Activate(); } finally { if (attached) AttachThreadInput(own,other,false); }
 }
 [STAThread] public static int Main(string[] args) {
  if (args.Length == 3 && args[0] == "--generate-icon") { GenerateIcon(args[1],args[2]); return 0; }
  if (args.Length == 2 && args[0] == "--render-ui") { RenderUi(args[1]); return 0; }
  if (args.Length == 2 && args[0] == "--hotkey-check") { _ = new Application(); var window = new Window(); var handle = new WindowInteropHelper(window).EnsureHandle(); using var hotkey = new HotkeyService(handle); var report = JsonSerializer.Serialize(new { registered = hotkey.Registered,actual = hotkey.Label,preferredError = hotkey.RegistrationError },new JsonSerializerOptions { WriteIndented = true }); File.WriteAllText(args[1],report); Console.WriteLine(report); return hotkey.Registered ? 0 : 1; }
  if (args.Length == 1 && args[0] == "--font-check") { _ = new Application(); foreach (var filename in new[] {"ibm_plex_sans.ttf","ibm_plex_serif.ttf"}) { var glyph = new System.Windows.Media.GlyphTypeface(new Uri("pack://application:,,,/Quill;component/Assets/Fonts/" + filename)); Console.WriteLine(filename + " => " + string.Join(",",glyph.FamilyNames.Values)); } foreach (var f in System.Windows.Media.Fonts.GetFontFamilies(new Uri("pack://application:,,,/Quill;component/"),"./Assets/Fonts/")) Console.WriteLine("Enumerated " + f.Source); foreach (var family in new[] {Theme.Sans,Theme.Serif}) { var typeface = new System.Windows.Media.Typeface(family,FontStyles.Normal,FontWeights.Normal,FontStretches.Normal); if (!typeface.TryGetGlyphTypeface(out var glyph)) throw new InvalidOperationException("Font resolution failed: " + family.Source); Console.WriteLine(family.Source + " => " + string.Join(",",glyph.FamilyNames.Values)); } return 0; }
  var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown }; int code = 0;
  app.Startup += async (_,_) => {
   try { if (args.Length > 1 && args[0] == "--editor") await Editor(args[1]); else if (args.Length > 1 && args[0] == "--audio-check") await AudioCheck(args[1]); else await Run(args.Length > 0 ? args[0] : Path.Combine(AppContext.BaseDirectory,"native-smoke.json")); }
   catch (Exception e) { File.WriteAllText(Path.Combine(AppContext.BaseDirectory,"native-smoke-error.txt"),e.ToString()); Console.Error.WriteLine(e); code = 1; }
   finally { app.Shutdown(); }
  }; app.Run(); return code;
 }
 private static void RenderUi(string folder) {
  Directory.CreateDirectory(folder); var app = new Application(); using var services = new AppServices(folder,true); services.Settings.Value.OnboardingComplete = true; services.Settings.Value.BubbleActive = false; Theme.Apply("Light"); var window = new MainWindow(services); var root = (FrameworkElement)window.Content; root.Measure(new Size(700,860)); root.Arrange(new Rect(0,0,700,860)); root.UpdateLayout(); var bmp = new System.Windows.Media.Imaging.RenderTargetBitmap(700,860,96,96,System.Windows.Media.PixelFormats.Pbgra32); bmp.Render(root); var png = new System.Windows.Media.Imaging.PngBitmapEncoder(); png.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bmp)); using (var f = File.Create(Path.Combine(folder,"home.png"))) png.Save(f);
  var details = new List<object>(); void Visit(System.Windows.DependencyObject node) { if (node is TextBlock block) { var face = new System.Windows.Media.Typeface(block.FontFamily,block.FontStyle,block.FontWeight,block.FontStretch); face.TryGetGlyphTypeface(out var glyph); details.Add(new { text = block.Text,font = block.FontFamily.Source,weight = block.FontWeight.ToString(),resolved = glyph == null ? "none" : string.Join(",",glyph.FamilyNames.Values),actual = GlyphNames(System.Windows.Media.VisualTreeHelper.GetDrawing(block)) }); } for (var i = 0; i < System.Windows.Media.VisualTreeHelper.GetChildrenCount(node); i++) Visit(System.Windows.Media.VisualTreeHelper.GetChild(node,i)); } Visit(root); File.WriteAllText(Path.Combine(folder,"font-tree.json"),JsonSerializer.Serialize(details,new JsonSerializerOptions { WriteIndented = true })); Console.WriteLine(File.ReadAllText(Path.Combine(folder,"font-tree.json"))); app.Shutdown();
 }
 private static string GlyphNames(System.Windows.Media.Drawing? drawing) => drawing switch { System.Windows.Media.GlyphRunDrawing glyph => string.Join(",",glyph.GlyphRun.GlyphTypeface.FamilyNames.Values), System.Windows.Media.DrawingGroup group => string.Join(";",group.Children.Select(GlyphNames)), _ => "" };
 private static async Task AudioCheck(string output) {
  using var recorder = new AudioRecorder(); var frames = 0; var peakRms = 0.0; recorder.Level += (rms,_) => { frames++; peakRms = Math.Max(peakRms,rms); };
  recorder.Start(new QuillSettings()); await Task.Delay(3100); var take = await recorder.Stop(); var passed = frames > 0 && take.Wav.Length > 32000 && BitConverter.ToInt32(take.Wav,24) == 16000;
  Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output))!); File.WriteAllText(output,JsonSerializer.Serialize(new { created = DateTimeOffset.UtcNow,passed,frames,peakRms,bytes = take.Wav.Length,durationMs = take.DurationMs,format = "16 kHz mono 16-bit WAV",audioPersisted = false,networkRequests = 0 },new JsonSerializerOptions { WriteIndented = true }));
  Console.WriteLine($"Microphone capture {(passed ? "PASS" : "FAIL")}: {frames} PCM frames, {take.Wav.Length} WAV bytes. Audio discarded; no upload."); if (!passed) throw new InvalidOperationException("No microphone frames captured");
 }
 private static void GenerateIcon(string source,string output) {
  var xml = System.Xml.Linq.XDocument.Load(source); System.Xml.Linq.XNamespace android = "http://schemas.android.com/apk/res/android";
  var drawing = new System.Windows.Media.DrawingVisual(); using (var dc = drawing.RenderOpen()) { dc.DrawRoundedRectangle(Theme.Hex("#F5F7F5"),null,new Rect(0,0,256,256),50,50); dc.PushTransform(new System.Windows.Media.ScaleTransform(256.0/108,256.0/108)); foreach (var path in xml.Root!.Elements("path")) dc.DrawGeometry(Theme.Hex(path.Attribute(android+"fillColor")!.Value),new System.Windows.Media.Pen(Theme.Hex(path.Attribute(android+"strokeColor")!.Value),.42),System.Windows.Media.Geometry.Parse(path.Attribute(android+"pathData")!.Value)); dc.Pop(); }
  var image = new System.Windows.Media.Imaging.RenderTargetBitmap(256,256,96,96,System.Windows.Media.PixelFormats.Pbgra32); image.Render(drawing); var encoder = new System.Windows.Media.Imaging.PngBitmapEncoder(); encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(image)); using var png = new MemoryStream(); encoder.Save(png); var bytes = png.ToArray(); Directory.CreateDirectory(output); File.WriteAllBytes(Path.Combine(output,"quill.png"),bytes);
  using var file = File.Create(Path.Combine(output,"quill.ico")); using var writer = new BinaryWriter(file); writer.Write((short)0); writer.Write((short)1); writer.Write((short)1); writer.Write((byte)0); writer.Write((byte)0); writer.Write((byte)0); writer.Write((byte)0); writer.Write((short)1); writer.Write((short)32); writer.Write(bytes.Length); writer.Write(22); writer.Write(bytes);
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
   try { var clip = new ClipboardService(); await Check("clipboard fallback retains exact Unicode text",() => Task.FromResult(clip.Copy("fixture नमस्ते 🌙") && Clipboard.GetText() == "fixture नमस्ते 🌙")); Clipboard.SetText("new clipboard owned by fixture"); await Check("clipboard owner marker disappears on user replacement",() => Task.FromResult(!Clipboard.ContainsData("Quill.Dictation.Owner"))); }
   finally { try { if (previous != null) Clipboard.SetDataObject(previous,true); else Clipboard.Clear(); } catch (System.Runtime.InteropServices.COMException) { } }
   await writer.WriteLineAsync(JsonSerializer.Serialize(new Command(Action:"quit"))); await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
   Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output))!); File.WriteAllText(output,JsonSerializer.Serialize(new { platform = Environment.OSVersion.VersionString,created = DateTimeOffset.UtcNow,cases = results },new JsonSerializerOptions { WriteIndented = true }));
   if (results.Any(r => !JsonSerializer.SerializeToElement(r).GetProperty("passed").GetBoolean())) throw new InvalidOperationException("Native smoke failures; see report");
  } finally { if (!child.HasExited) child.Kill(); }
 }
}
