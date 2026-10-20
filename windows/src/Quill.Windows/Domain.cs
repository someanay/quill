using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
namespace Quill.Windows;
[JsonConverter(typeof(JsonStringEnumConverter))] public enum Provider { Groq, OpenAI, OpenRouter, Gemini, Custom }
[JsonConverter(typeof(JsonStringEnumConverter))] public enum WritingStyle { Neutral, Formal, Casual }
public record ProviderInfo(string BaseUrl, string SpeechModel, string CleanupModel, string? ConsoleUrl, string[] Models);
public static class Providers {
 public static ProviderInfo Get(Provider p) => p switch {
  Provider.Groq => new("https://api.groq.com/openai/v1", "whisper-large-v3-turbo", "llama-3.3-70b-versatile", "https://console.groq.com/keys", ["whisper-large-v3-turbo", "whisper-large-v3"]),
  Provider.OpenAI => new("https://api.openai.com/v1", "whisper-1", "gpt-4o-mini", "https://platform.openai.com/api-keys", ["whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"]),
  Provider.OpenRouter => new("https://openrouter.ai/api/v1", "openai/whisper-large-v3-turbo", "liquid/lfm-2.5-2.6b:free", "https://openrouter.ai/keys", ["openai/whisper-large-v3-turbo", "openai/gpt-4o-mini-transcribe", "openai/gpt-4o-transcribe"]),
  Provider.Gemini => new("https://generativelanguage.googleapis.com", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "https://aistudio.google.com/apikey", ["gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"]),
  _ => new("", "", "", null, []) };
 public static string BaseUrl(Provider p, string custom) => ValidateEndpoint(p == Provider.Custom ? custom : Get(p).BaseUrl);
 public static string ValidateEndpoint(string value) {
  if (!Uri.TryCreate(value.Trim(), UriKind.Absolute, out var url) || url.Scheme != "https" || string.IsNullOrWhiteSpace(url.Host) || url.UserInfo.Length > 0 || url.Query.Length > 0 || url.Fragment.Length > 0) throw new ArgumentException("Use an HTTPS base URL without a username, password, query, or fragment.");
  return url.AbsoluteUri.TrimEnd('/'); }
 public static bool HasFreeCleanup(Provider p) => p is Provider.Groq or Provider.OpenRouter or Provider.Gemini;
}
public sealed class QuillSettings {
 public string DictationShortcut { get; set; } = "Ctrl + Win + Space";
 public Provider Provider { get; set; } = Provider.Groq;
 public string SpeechModel { get; set; } = "whisper-large-v3-turbo";
 public string CustomBaseUrl { get; set; } = "";
 public string CustomModel { get; set; } = "";
 public string Language { get; set; } = "en";
 public bool CleanupEnabled { get; set; } = true;
 public bool CleanupAutomatic { get; set; } = true;
 public Provider CleanupProvider { get; set; } = Provider.Groq;
 public string CleanupModel { get; set; } = "llama-3.3-70b-versatile";
 public Dictionary<Provider,string> CleanupModels { get; set; } = [];
 public WritingStyle DefaultStyle { get; set; }
 public Dictionary<string, WritingStyle> AppStyles { get; set; } = new(StringComparer.OrdinalIgnoreCase);
 public bool PrivateMode { get; set; }
 public bool HistoryEnabled { get; set; } = true;
 public int HistoryRetentionMinutes { get; set; } = 60;
 public int SessionCapSeconds { get; set; } = 300;
 public bool OnboardingComplete { get; set; }
 public bool InjectionConsent { get; set; }
 public string BubbleSize { get; set; } = "Medium";
 public string Theme { get; set; } = "Match system";
 public bool StartAtLogin { get; set; } = true;
 public bool BubbleActive { get; set; }
 public bool BubbleVisible { get; set; } = true;
 public bool CrashReportingEnabled { get; set; }
 public int InputDevice { get; set; } = -1;
 public string InputDeviceName { get; set; } = "";
 public double BubbleX { get; set; } = -1;
 public double BubbleY { get; set; } = -1;
 public bool HoldToTalk { get; set; }
 public bool PinBubble { get; set; } = true;
 public List<CustomWord> CustomWords { get; set; } = [];
 public List<string> ContactNames { get; set; } = [];
 public bool ContactsEnabled { get; set; }
 public HashSet<Provider> FreeTierProviders { get; set; } = [];
 [JsonIgnore] public Provider EffectiveCleanupProvider => CleanupAutomatic ? Provider : CleanupProvider;
 [JsonIgnore] public string EffectiveCleanupModel => CleanupAutomatic ? Providers.Get(Provider).CleanupModel : CleanupModel;
 [JsonIgnore] public bool UseCleanup => CleanupEnabled && (!CleanupAutomatic || Providers.HasFreeCleanup(Provider));
 public WritingStyle StyleFor(string? app) {
  if (app != null && AppStyles.TryGetValue(app, out var style)) return style;
  var id = app?.ToLowerInvariant() ?? "";
  if (new[] { "outlook", "olk", "winword", "thunderbird", "notion", "slack", "teams", "ms-teams" }.Contains(id)) return WritingStyle.Formal;
  if (id == "discord") return WritingStyle.Casual;
  if (new[] { "whatsapp", "telegram", "signal", "messages", "chrome", "msedge", "firefox", "brave", "opera", "notepad", "onenote" }.Contains(id)) return WritingStyle.Neutral;
  return DefaultStyle; }
 public void Validate() {
  SessionCapSeconds = Math.Clamp(SessionCapSeconds, 60, 300);
  if (!new[] { 0, 60, 1440, 10080, 43200 }.Contains(HistoryRetentionMinutes)) HistoryRetentionMinutes = 60;
  if (string.IsNullOrWhiteSpace(SpeechModel) && Provider != Provider.Custom) SpeechModel = Providers.Get(Provider).SpeechModel;
  CustomWords ??= []; ContactNames ??= []; AppStyles ??= new(); FreeTierProviders ??= []; CleanupModels ??= []; }
}
public static class SessionPresentation {
 public static bool ShowFloatingControl(bool enabled,bool visible,DictationState state) => enabled && visible || state != DictationState.Idle;
}
public record CustomWord(string Heard, string Replacement);
public record HistoryEntry(long Id, DateTimeOffset Created, string Text, string Destination, long DurationMs, string Status, string Provider);
public static class Words {
 public static void Upsert(IList<CustomWord> words,string heard,string replacement) { heard = heard.Trim(); replacement = replacement.Trim(); if (heard.Length == 0 || replacement.Length == 0) return; for (int i = words.Count - 1;i >= 0;i--) if (string.Equals(words[i].Heard,heard,StringComparison.OrdinalIgnoreCase)) words.RemoveAt(i); words.Add(new(heard,replacement)); }
 public static string Apply(string text, IEnumerable<CustomWord> words) {
  foreach (var word in words.Where(w => !string.IsNullOrWhiteSpace(w.Heard))) text = Regex.Replace(text, @"(?<![\p{L}\p{N}])" + Regex.Escape(word.Heard.Trim()) + @"(?![\p{L}\p{N}])", _ => word.Replacement, RegexOptions.IgnoreCase);
  return text; }
}
public sealed class SilenceDetector {
 private long quietSince = -1;
 public bool Observe(double rms, long elapsedMs) {
  if (rms >= 600) { quietSince = -1; return false; }
  if (elapsedMs < 2000) return false;
  if (quietSince < 0) quietSince = elapsedMs;
  return elapsedMs - quietSince >= 3500; }
}
public static class InsertionComposer {
 public static string Insert(string current, int start, int end, string text) {
  start = Math.Clamp(start,0,current.Length); end = Math.Clamp(end,0,current.Length); if (start > end) (start,end) = (end,start);
  var prefix = start == current.Length && end == start && start > 0 && text.Length > 0 && !char.IsWhiteSpace(current[start - 1]) && !char.IsWhiteSpace(text[0]) ? " " : "";
  // Windows UIA supplies an actual caret; preserve content at position zero instead of applying
  // Android's placeholder workaround (which intentionally replaces that Android field).
  return current[..start] + prefix + text + current[end..]; }
}
