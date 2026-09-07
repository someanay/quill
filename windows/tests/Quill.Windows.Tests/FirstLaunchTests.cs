using System.Diagnostics;
using Xunit;
namespace Quill.Windows.Tests;
public sealed class FirstLaunchTests(Xunit.Abstractions.ITestOutputHelper output) {
 [Fact] public async Task FreshApplicationSurvivesSplashBeforeDispatcherContextExists() {
  using var folder = new TestFolder(); var start = new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"Quill.exe")) { UseShellExecute = false,CreateNoWindow = true,WindowStyle = ProcessWindowStyle.Hidden };
  start.ArgumentList.Add("--data-dir"); start.ArgumentList.Add(folder.Path);
  using var process = Process.Start(start) ?? throw new InvalidOperationException("Fresh Quill test process did not start."); var clock = Stopwatch.StartNew();
  try {
   var firstRender = Path.Combine(folder.Path,"actual-window.png");
   while (!File.Exists(firstRender) && !process.HasExited && clock.Elapsed < TimeSpan.FromSeconds(45)) await Task.Delay(100);
   output.WriteLine($"Fresh process first render after {clock.Elapsed.TotalSeconds:F3}s; exited={process.HasExited}");
   Assert.False(process.HasExited,"Fresh Quill exited before its first render."); Assert.True(File.Exists(firstRender),"Fresh Quill did not render within 45 seconds.");
   // The constructor starts before Application.Run installs its synchronization context.
   // Wait beyond the real splash delay so a continuation on the wrong thread fails this test.
   await Task.Delay(3000); process.Refresh();
   Assert.False(process.HasExited,"Fresh Quill exited during splash-to-Welcome transition."); Assert.NotEqual(IntPtr.Zero,process.MainWindowHandle);
   using var visualTree = System.Text.Json.JsonDocument.Parse(File.ReadAllText(Path.Combine(folder.Path,"actual-font-tree.json")));
   Assert.Contains(visualTree.RootElement.EnumerateArray(),item => item.GetProperty("text").GetString() == new Quill.Windows.CopyCatalog().Get("onboarding.welcome.cta"));
   Assert.False(File.Exists(Path.Combine(folder.Path,"startup-failure.txt"))); Assert.True(File.Exists(Path.Combine(folder.Path,"history.sqlite3")));
   Assert.False(File.Exists(Path.Combine(folder.Path,"settings.json")),"This regression must exercise unseeded first-run settings.");
   output.WriteLine($"Unseeded first-run process remained alive after {clock.Elapsed.TotalSeconds:F3}s.");
  } finally { if (!process.HasExited) process.Kill(); await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(10)); }
 }
}
