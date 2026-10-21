using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Quill.Windows;
using Xunit;
namespace Quill.Windows.Tests;
public sealed class VisualAndCatalogTests(Xunit.Abstractions.ITestOutputHelper output) {
 [Fact] public async Task NativeThemesAndBundledFontsResolveWithoutFallbackOrSealedStyleFailure() {
  var phases = new System.Collections.Concurrent.ConcurrentQueue<string>(); var clock = System.Diagnostics.Stopwatch.StartNew();
  void Phase(string phase) => phases.Enqueue($"{clock.Elapsed.TotalSeconds:F3}s: {phase}");
  var done = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
  var thread = new Thread(() => {
   try {
    Phase("creating STA Application"); var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
    Phase("loading bundled font families and first light theme"); Theme.Apply("Light");
    Phase("applying dark theme"); Theme.Apply("Dark");
    Phase("reapplying light theme"); Theme.Apply("Light");
    var sameTheme = app.Resources[typeof(Button)];
    var allocations = GC.GetAllocatedBytesForCurrentThread(); var timer = System.Diagnostics.Stopwatch.StartNew();
    for (int i = 0; i < 100; i++) Theme.Apply("Light");
    timer.Stop(); Phase($"100 unchanged theme applications: {timer.Elapsed.TotalMilliseconds:F2} ms, {GC.GetAllocatedBytesForCurrentThread()-allocations} allocated bytes");
    Assert.Same(sameTheme,app.Resources[typeof(Button)]);
    Assert.Equal("#FF234DCE",((SolidColorBrush)app.Resources["Brand"]).Color.ToString());
    foreach (var family in new[] {Theme.Sans,Theme.Serif}) foreach (var weight in new[] {FontWeights.Normal,FontWeights.SemiBold}) {
     Phase("resolving " + family.Source + " " + weight); var typeface = new Typeface(family,FontStyles.Normal,weight,FontStretches.Normal);
     Assert.True(typeface.TryGetGlyphTypeface(out var glyph),family.Source + " " + weight);
     Assert.Contains(ReferenceEquals(family,Theme.Sans) ? "IBM Plex Sans" : "IBM Plex Serif Var",glyph.FamilyNames.Values);
    }
    Phase("constructing native templates"); var combo = new ComboBox { ItemsSource = new[] {"Groq","OpenAI"},SelectedIndex = 0 }; var check = new CheckBox { Content = "Permission consent",IsChecked = true }; var panel = new StackPanel(); panel.Children.Add(combo); panel.Children.Add(check);
    Phase("measuring controls and initializing system font rendering"); panel.Measure(new Size(500,500)); panel.Arrange(new Rect(0,0,500,500)); combo.ApplyTemplate(); check.ApplyTemplate();
    Assert.NotNull(combo.Template.FindName("PART_Popup",combo)); Assert.NotNull(check.Template.FindName("Track",check));
    Phase("shutting down Application"); app.Shutdown(); Phase("Application shutdown returned"); done.SetResult();
   } catch (Exception e) { Phase("exception " + e.GetType().Name); done.SetException(e); }
  }) { IsBackground = true,Name = "Quill WPF verification" };
  thread.SetApartmentState(ApartmentState.STA); Phase("starting STA thread"); thread.Start();
  // Cold hosted Windows images may initialize the WPF/font services for the first time here.
  // Preserve every assertion and a finite bound; phases distinguish initialization from shutdown.
  try { await done.Task.WaitAsync(TimeSpan.FromSeconds(90)); }
  catch (TimeoutException e) { throw new TimeoutException("WPF verification exceeded 90 seconds. Last phase: " + phases.LastOrDefault(),e); }
  finally { foreach (var phase in phases) output.WriteLine(phase); }
 }
 [Fact] public void OpenRouterUsesActualOutputCapabilitiesAndCompletePricing() { using var json = JsonDocument.Parse("""
 {"data":[{"id":"speech-without-name-hints","architecture":{"output_modalities":["transcription"]},"pricing":{"prompt":"0","completion":"0","request":"0"}},{"id":"whisper-looking-chat-model","architecture":{"output_modalities":["text"]}},{"id":"text-cleaner","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","request":"0.01"}},{"id":"embedding-model","architecture":{"output_modalities":["text"]}},{"id":"disabled","active":false,"architecture":{"output_modalities":["transcription"]}}]}
 """); var speech = ModelCatalog.Parse(json.RootElement,Provider.OpenRouter,true); Assert.Single(speech); Assert.Equal("speech-without-name-hints",speech[0].Id); Assert.True(speech[0].Free); var cleanup = ModelCatalog.Parse(json.RootElement,Provider.OpenRouter,false); Assert.Single(cleanup); Assert.Equal("text-cleaner",cleanup[0].Id); Assert.False(cleanup[0].Free); Assert.True(cleanup[0].Paid); }
 [Fact] public void ModelRankingMatchesAndroidSuitabilityThenRecency() { var models = new[] {new ModelOption("model-2",false,false,200),new ModelOption("model-mini-preview",true,false,500),new ModelOption("model-mini",true,false,100),new ModelOption("model-3",false,false,200),new ModelOption("model-100",false,false,99)}; Assert.Equal(new[] {"model-mini","model-mini-preview","model-3","model-2","model-100"},ModelCatalog.Rank(models,true).Select(m => m.Id)); }
 [Theory][InlineData("guard-3")][InlineData("safe-model")][InlineData("llama-3")]
 public void UnknownModelsAreNotLabeledFree(string id) => Assert.False(ModelCatalog.KnownFree(Provider.Groq,id));
}
