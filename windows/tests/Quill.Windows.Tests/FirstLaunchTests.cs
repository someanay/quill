using System.Diagnostics;
using Xunit;
using System.IO.Pipes;
using System.Text.Json;
using System.Runtime.InteropServices;
namespace Quill.Windows.Tests;
public sealed class FirstLaunchTests(Xunit.Abstractions.ITestOutputHelper output) {
 [Fact] public void ActivationBeforeListenerIsQueuedAndNormalizedToTheSameDirectory() {
  using var folder = new TestFolder(); using var primary = new Quill.Windows.SingleInstanceActivation(folder.Path); Assert.True(primary.IsPrimary);
  Exception? error = null; var thread = new Thread(() => { try { using var secondary = new Quill.Windows.SingleInstanceActivation(folder.Path.ToUpperInvariant() + Path.DirectorySeparatorChar); Assert.False(secondary.IsPrimary); secondary.RequestActivation(); } catch (Exception e) { error = e; } }) { IsBackground = true }; thread.Start(); Assert.True(thread.Join(TimeSpan.FromSeconds(3))); Assert.Null(error);
  using var activated = new ManualResetEventSlim(); primary.Listen(activated.Set); Assert.True(activated.Wait(TimeSpan.FromSeconds(3)),"A launch during primary startup must stay queued until the listener exists.");
 }
 private static Process Launch(string folder) { var start = new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"Quill.exe")) { UseShellExecute = false,CreateNoWindow = true,WindowStyle = ProcessWindowStyle.Hidden }; start.ArgumentList.Add("--data-dir"); start.ArgumentList.Add(folder); return Process.Start(start) ?? throw new InvalidOperationException("Quill test process did not start."); }
 private static async Task WaitUntil(Func<bool> condition,string failure) { var timer = Stopwatch.StartNew(); while (!condition() && timer.Elapsed < TimeSpan.FromSeconds(45)) await Task.Delay(100); Assert.True(condition(),failure); }
 [DllImport("user32.dll")] private static extern bool ShowWindow(nint hwnd,int command);
 [DllImport("user32.dll")] private static extern bool IsZoomed(nint hwnd);
 [Theory]
 [InlineData(false)]
 [InlineData(true)]
 public async Task ShortcutReopensSameInstanceAndDataDirectoriesStayIndependent(bool maximized) {
  using var folder = new TestFolder(); using var independentFolder = new TestFolder(); using var first = Launch(folder.Path); Process? launcher = null; Process? independent = null;
  var pipeName = "Quill.ActivationTest." + Guid.NewGuid().ToString("N"); using var pipe = new NamedPipeServerStream(pipeName,PipeDirection.InOut,1,PipeTransmissionMode.Byte,PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
  try {
   await WaitUntil(() => File.Exists(Path.Combine(folder.Path,"actual-window.png")) || first.HasExited,"Primary launch did not render."); Assert.False(first.HasExited);
   first.Refresh(); var firstHandle = first.MainWindowHandle; Assert.NotEqual(IntPtr.Zero,firstHandle); if (maximized) { ShowWindow(firstHandle,3); await WaitUntil(() => IsZoomed(firstHandle),"The test window did not maximize."); } Assert.True(first.CloseMainWindow());
   await WaitUntil(() => { first.Refresh(); return first.MainWindowHandle == IntPtr.Zero; },"Close should hide the tray instance.");
   var launch = new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"Quill.NativeSmoke.exe")) { UseShellExecute = false,CreateNoWindow = true,WindowStyle = ProcessWindowStyle.Hidden };
   foreach (var argument in new[] {"--activation-launcher",pipeName,Path.Combine(AppContext.BaseDirectory,"Quill.exe"),folder.Path}) launch.ArgumentList.Add(argument);
   launcher = Process.Start(launch) ?? throw new InvalidOperationException("Launcher fixture did not start.");
   await pipe.WaitForConnectionAsync().WaitAsync(TimeSpan.FromSeconds(15)); using var reader = new StreamReader(pipe,leaveOpen: true); using var writer = new StreamWriter(pipe,leaveOpen: true) { AutoFlush = true };
   await writer.WriteLineAsync("launch"); var reply = await reader.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(20)); using var launched = JsonDocument.Parse(reply!);
   Assert.True(launched.RootElement.GetProperty("foregroundLauncher").GetBoolean()); Assert.Equal(0,launched.RootElement.GetProperty("exitCode").GetInt32());
   using var request = JsonDocument.Parse(File.ReadAllText(Path.Combine(folder.Path,"activation-request.json"))); Assert.True(request.RootElement.GetProperty("foregroundPermissionGranted").GetBoolean(),"The foreground shortcut must grant activation to the existing instance.");
   await WaitUntil(() => { first.Refresh(); return first.MainWindowHandle != IntPtr.Zero; },"Second shortcut launch did not reopen the existing window."); Assert.Equal(firstHandle,first.MainWindowHandle); Assert.False(first.HasExited);
   // The queued dispatcher request makes the HWND visible before Activate runs. Observe the
   // completed activation, rather than racing the middle of the same ShowMain callback.
   await WaitUntil(() => Quill.Windows.Native.GetForegroundWindow() == firstHandle,"The reopened window did not become foreground.");
   Assert.Equal(firstHandle,Quill.Windows.Native.GetForegroundWindow()); Assert.Equal(maximized,IsZoomed(firstHandle));
   await writer.WriteLineAsync("quit"); await launcher.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)); Assert.Equal(0,launcher.ExitCode);
   independent = Launch(independentFolder.Path); await WaitUntil(() => File.Exists(Path.Combine(independentFolder.Path,"actual-window.png")) || independent.HasExited,"Independent directory did not launch."); Assert.False(independent.HasExited); Assert.NotEqual(first.Id,independent.Id);
   output.WriteLine($"Foreground launcher {launcher.Id} completed; original PID {first.Id}/HWND {firstHandle} reopened. Separate data directory PID {independent.Id} remained independent.");
  } finally { foreach (var process in new[] {launcher,independent,first}) if (process != null) { if (!process.HasExited) process.Kill(); await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)); if (!ReferenceEquals(process,first)) process.Dispose(); } }
 }
 [Fact] public async Task FreshApplicationRendersDesktopWelcomeWithUnseededSettings() {
  using var folder = new TestFolder(); var start = new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"Quill.exe")) { UseShellExecute = false,CreateNoWindow = true,WindowStyle = ProcessWindowStyle.Hidden };
  start.ArgumentList.Add("--data-dir"); start.ArgumentList.Add(folder.Path);
  using var process = Process.Start(start) ?? throw new InvalidOperationException("Fresh Quill test process did not start."); var clock = Stopwatch.StartNew();
  try {
   var firstRender = Path.Combine(folder.Path,"actual-window.png");
   while (!File.Exists(firstRender) && !process.HasExited && clock.Elapsed < TimeSpan.FromSeconds(45)) await Task.Delay(100);
   output.WriteLine($"Fresh process first render after {clock.Elapsed.TotalSeconds:F3}s; exited={process.HasExited}");
   Assert.False(process.HasExited,"Fresh Quill exited before its first render."); Assert.True(File.Exists(firstRender),"Fresh Quill did not render within 45 seconds.");
   // Remain alive after the initial render without seeding settings or credentials.
   await Task.Delay(3000); process.Refresh();
   Assert.False(process.HasExited,"Fresh Quill exited after rendering Welcome."); Assert.NotEqual(IntPtr.Zero,process.MainWindowHandle);
   using var visualTree = System.Text.Json.JsonDocument.Parse(File.ReadAllText(Path.Combine(folder.Path,"actual-font-tree.json")));
   Assert.Contains(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString() == new Quill.Windows.CopyCatalog().Get("onboarding.welcome.cta"));
   Assert.Contains(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString() == new Quill.Windows.CopyCatalog().Get("onboarding.welcome.open"));
   Assert.Contains(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString() == new Quill.Windows.CopyCatalog().Get("desktop.setup.privacy_line"));
   Assert.Contains(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString() == new Quill.Windows.CopyCatalog().Get("desktop.setup.welcome_body"));
   Assert.DoesNotContain(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString()?.Contains("Tap it",StringComparison.OrdinalIgnoreCase) == true);
   Assert.False(File.Exists(Path.Combine(folder.Path,"startup-failure.txt"))); Assert.True(File.Exists(Path.Combine(folder.Path,"history.sqlite3")));
   Assert.False(File.Exists(Path.Combine(folder.Path,"settings.json")),"This regression must exercise unseeded first-run settings.");
   output.WriteLine($"Unseeded first-run process remained alive after {clock.Elapsed.TotalSeconds:F3}s.");
  } finally { if (!process.HasExited) process.Kill(); await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)); }
 }
}
