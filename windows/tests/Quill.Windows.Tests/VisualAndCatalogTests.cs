using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Quill.Windows;
using Xunit;
namespace Quill.Windows.Tests;
public sealed class VisualAndCatalogTests {
 [Fact] public async Task NativeThemesAndBundledFontsResolveWithoutFallbackOrSealedStyleFailure() {
  var done = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously); var thread = new Thread(() => { try { var app = new Application(); Theme.Apply("Light"); Theme.Apply("Dark"); Theme.Apply("Light"); Assert.Equal("#FF234DCE",((SolidColorBrush)app.Resources["Brand"]).Color.ToString()); foreach (var family in new[] {Theme.Sans,Theme.Serif}) foreach (var weight in new[] {FontWeights.Normal,FontWeights.SemiBold}) { var typeface = new Typeface(family,FontStyles.Normal,weight,FontStretches.Normal); Assert.True(typeface.TryGetGlyphTypeface(out var glyph),family.Source + " " + weight); Assert.Contains(glyph.FamilyNames.Values,n => n.StartsWith("IBM Plex",StringComparison.Ordinal)); } var combo = new ComboBox { ItemsSource = new[] {"Groq","OpenAI"},SelectedIndex = 0 }; var check = new CheckBox { Content = "Permission consent",IsChecked = true }; var panel = new StackPanel(); panel.Children.Add(combo); panel.Children.Add(check); panel.Measure(new Size(500,500)); panel.Arrange(new Rect(0,0,500,500)); combo.ApplyTemplate(); check.ApplyTemplate(); Assert.NotNull(combo.Template.FindName("PART_Popup",combo)); Assert.NotNull(check.Template.FindName("Track",check)); app.Shutdown(); done.SetResult(); } catch (Exception e) { done.SetException(e); } }); thread.SetApartmentState(ApartmentState.STA); thread.Start(); await done.Task.WaitAsync(TimeSpan.FromSeconds(15));
 }
 [Fact] public void OpenRouterUsesActualOutputCapabilitiesAndCompletePricing() { using var json = JsonDocument.Parse("""
 {"data":[{"id":"speech-without-name-hints","architecture":{"output_modalities":["transcription"]},"pricing":{"prompt":"0","completion":"0","request":"0"}},{"id":"whisper-looking-chat-model","architecture":{"output_modalities":["text"]}},{"id":"text-cleaner","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","request":"0.01"}},{"id":"embedding-model","architecture":{"output_modalities":["text"]}},{"id":"disabled","active":false,"architecture":{"output_modalities":["transcription"]}}]}
 """); var speech = ModelCatalog.Parse(json.RootElement,Provider.OpenRouter,true); Assert.Single(speech); Assert.Equal("speech-without-name-hints",speech[0].Id); Assert.True(speech[0].Free); var cleanup = ModelCatalog.Parse(json.RootElement,Provider.OpenRouter,false); Assert.Single(cleanup); Assert.Equal("text-cleaner",cleanup[0].Id); Assert.False(cleanup[0].Free); Assert.True(cleanup[0].Paid); }
 [Fact] public void ModelRankingMatchesAndroidSuitabilityThenRecency() { var models = new[] {new ModelOption("model-2",false,false,200),new ModelOption("model-mini-preview",true,false,500),new ModelOption("model-mini",true,false,100),new ModelOption("model-3",false,false,200),new ModelOption("model-100",false,false,99)}; Assert.Equal(new[] {"model-mini","model-mini-preview","model-3","model-2","model-100"},ModelCatalog.Rank(models,true).Select(m => m.Id)); }
 [Theory][InlineData("guard-3")][InlineData("safe-model")][InlineData("llama-3")]
 public void UnknownModelsAreNotLabeledFree(string id) => Assert.False(ModelCatalog.KnownFree(Provider.Groq,id));
}
