using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
namespace Quill.Windows;
public static class ModelCatalog {
 public static bool KnownFree(Provider provider,string id) => provider switch { Provider.Groq => new[] {"whisper-large-v3-turbo","whisper-large-v3","llama-3.3-70b-versatile"}.Contains(id), Provider.Gemini => new[] {"gemini-3.8-flash","gemini-3.7-flash","gemini-3.6-flash","gemini-3.5-flash-lite","gemini-3.1-flash-lite","gemini-3.5-transcribe"}.Contains(id),Provider.OpenRouter => id == "openrouter/free" || id.EndsWith(":free",StringComparison.Ordinal),_ => false };
 public static List<ModelOption> Parse(JsonElement root,Provider provider,bool speechUse) {
  if (!root.TryGetProperty(provider == Provider.Gemini ? "models" : "data",out var items) || items.ValueKind != JsonValueKind.Array) throw new ProviderException("invalid_response");
  var models = new List<ModelOption>();
  foreach (var row in items.EnumerateArray()) {
   if (row.ValueKind != JsonValueKind.Object) continue;
   var id = String(row,provider == Provider.Gemini ? "name" : "id"); if (id.StartsWith("models/",StringComparison.Ordinal)) id = id[7..]; if (string.IsNullOrWhiteSpace(id) || id.Length > 250) continue;
   var lower = id.ToLowerInvariant(); var speech = new[] {"whisper","transcribe","transcription"}.Any(lower.Contains);
   bool HasOutput(string modality) => row.TryGetProperty("architecture",out var arch) && arch.ValueKind == JsonValueKind.Object && arch.TryGetProperty("output_modalities",out var outputs) && outputs.ValueKind == JsonValueKind.Array && outputs.EnumerateArray().Any(o => o.ValueKind == JsonValueKind.String && o.GetString() == modality);
   bool generate = row.TryGetProperty("supportedGenerationMethods",out var methods) && methods.ValueKind == JsonValueKind.Array && methods.EnumerateArray().Any(m => m.ValueKind == JsonValueKind.String && m.GetString() == "generateContent");
   bool compatible = !speechUse ? !speech && !new[] {"guard","safety","tts","orpheus","playai","speech","audio","embed","compound","realtime","image","live","robotics","computer-use"}.Any(lower.Contains) && (provider != Provider.Gemini || lower.StartsWith("gemini-",StringComparison.Ordinal) && generate) && (provider != Provider.OpenRouter || HasOutput("text")) : provider switch { Provider.OpenRouter => HasOutput("transcription"), Provider.Gemini => lower.StartsWith("gemini-",StringComparison.Ordinal) && generate && !new[] {"live","tts","image","robotics","computer-use","embedding"}.Any(lower.Contains),Provider.Custom => true,_ => speech && !lower.Contains("diariz") && !lower.Contains("realtime") };
   if (!compatible || row.TryGetProperty("active",out var active) && active.ValueKind == JsonValueKind.False) continue;
   bool free = KnownFree(provider,id); bool paid = provider == Provider.OpenAI || provider == Provider.Gemini && id == "gemini-3.1-pro-preview";
   if (row.TryGetProperty("pricing",out var pricing) && pricing.ValueKind == JsonValueKind.Object) { static decimal? Price(JsonElement value) => decimal.TryParse(value.ToString(),NumberStyles.Float,CultureInfo.InvariantCulture,out var number) ? number : null; free = pricing.TryGetProperty("prompt",out var prompt) && Price(prompt) == 0 && pricing.TryGetProperty("completion",out var completion) && Price(completion) == 0 && pricing.EnumerateObject().All(p => Price(p.Value) == 0); paid |= pricing.EnumerateObject().Any(p => Price(p.Value) > 0); }
   models.Add(new(id,free,paid,row.TryGetProperty("created",out var created) && long.TryParse(created.ToString(),out var value) ? value : 0,String(row,"displayName") is string name && name.Length > 0 ? name : id));
  }
  return Rank(models,speechUse);
 }
 private static string String(JsonElement row,string property) => row.TryGetProperty(property,out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";
 public static List<ModelOption> Rank(IEnumerable<ModelOption> models,bool speech) => models.DistinctBy(m => m.Id).OrderByDescending(m => m.Free).ThenBy(m => { var id = m.Id.ToLowerInvariant(); return (new[] {"preview","experimental","exp-","reasoning"}.Any(id.Contains) ? 20 : 0) + ((speech ? new[] {"turbo","mini","flash"} : new[] {"versatile","instant","instruct"}).Any(id.Contains) ? 0 : 5); }).ThenByDescending(m => m.Created).ThenByDescending(m => double.TryParse(Regex.Match(m.Id,@"\d+(?:\.\d+)?").Value,NumberStyles.Float,CultureInfo.InvariantCulture,out var version) ? version : 0).ThenBy(m => m.Id,StringComparer.Ordinal).ToList();
}
