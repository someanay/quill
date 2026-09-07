using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.Data.Sqlite;
namespace Quill.Windows;

public static class JsonFiles {
 public static readonly JsonSerializerOptions Options = new() { WriteIndented = true, PropertyNameCaseInsensitive = true };
 public static T Read<T>(string path, Func<T> fallback) {
  if (!File.Exists(path)) return fallback();
  try { return JsonSerializer.Deserialize<T>(File.ReadAllText(path), Options) ?? fallback(); }
  catch (JsonException) { File.Copy(path, path + ".invalid-" + DateTime.UtcNow.ToString("yyyyMMddHHmmss"), true); return fallback(); }
 }
 public static void Write<T>(string path, T value) {
  Directory.CreateDirectory(Path.GetDirectoryName(path)!);
  var temporary = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
  try { File.WriteAllText(temporary, JsonSerializer.Serialize(value, Options), new UTF8Encoding(false)); File.Move(temporary, path, true); }
  finally { if (File.Exists(temporary)) File.Delete(temporary); }
 }
}
public sealed class SettingsStore {
 private readonly string path;
 public QuillSettings Value { get; private set; }
 public event Action? Changed;
 public SettingsStore(string folder) { path = Path.Combine(folder, "settings.json"); Value = JsonFiles.Read(path, () => new QuillSettings()); Value.Validate(); }
 public void Save() { Value.Validate(); JsonFiles.Write(path, Value); Changed?.Invoke(); }
 public void SetProvider(Provider provider) { Value.Provider = provider; Value.SpeechModel = provider == Provider.Custom ? Value.CustomModel : Providers.Get(provider).SpeechModel; Save(); }
 public void SetCleanupAutomatic(bool automatic) { if (!automatic && Value.CleanupAutomatic) { Value.CleanupProvider = Value.Provider; Value.CleanupModel = Providers.Get(Value.Provider).CleanupModel; } Value.CleanupAutomatic = automatic; if (automatic) Value.CleanupEnabled = true; Save(); }
 public void SetCleanupEnabled(bool enabled) { Value.CleanupEnabled = enabled; if (enabled && Value.CleanupAutomatic && !Providers.HasFreeCleanup(Value.Provider)) { Value.CleanupAutomatic = false; Value.CleanupProvider = Value.Provider; Value.CleanupModel = Providers.Get(Value.Provider).CleanupModel; } Save(); }
 public void SetCleanupProvider(Provider provider) { Value.CleanupAutomatic = false; Value.CleanupModels[Value.CleanupProvider] = Value.CleanupModel; Value.CleanupProvider = provider; Value.CleanupModel = Value.CleanupModels.GetValueOrDefault(provider) ?? Providers.Get(provider).CleanupModel; Save(); }
 public void SetCleanupModel(string model) { if (Value.CleanupAutomatic) Value.CleanupProvider = Value.Provider; Value.CleanupAutomatic = false; Value.CleanupModel = model.Trim(); Save(); }
 public void Reset(bool all = false) { var complete = Value.OnboardingComplete; var consent = Value.InjectionConsent; Value = new() { OnboardingComplete = !all && complete, InjectionConsent = !all && consent }; Save(); }
}
public record SavedKey(string Id,string Label,string Ciphertext,string LastFour,DateTimeOffset Created,string? Endpoint = null);
public sealed class KeyRing { public List<SavedKey> Keys { get; set; } = []; public string? ActiveId { get; set; } public Dictionary<string,string> ActiveByEndpoint { get; set; } = []; }
public sealed class SecretStore {
 private readonly string path;
 private readonly object gate = new();
 private Dictionary<Provider, KeyRing> rings;
 private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("Quill.Windows.v2/provider-keys");
 public SecretStore(string folder) { path = Path.Combine(folder, "keys.dpapi.json"); rings = JsonFiles.Read(path, () => new Dictionary<Provider, KeyRing>()); }
 private static string? Scope(Provider provider,string? endpoint) { if (provider != Provider.Custom) return null; try { return Providers.ValidateEndpoint(endpoint ?? ""); } catch (ArgumentException) { return null; } }
 public IReadOnlyList<SavedKey> List(Provider provider,string? endpoint = null) { lock (gate) { var scope = Scope(provider,endpoint); return rings.GetValueOrDefault(provider)?.Keys.Where(k => provider != Provider.Custom || scope != null && k.Endpoint == scope).ToArray() ?? []; } }
 public string? ActiveId(Provider provider,string? endpoint = null) { lock (gate) { var ring = rings.GetValueOrDefault(provider); var scope = Scope(provider,endpoint); return provider == Provider.Custom ? scope == null ? null : ring?.ActiveByEndpoint.GetValueOrDefault(scope) : ring?.ActiveId; } }
 public string? Read(Provider provider,string? endpoint = null) {
  lock (gate) {
   var id = ActiveId(provider,endpoint); var key = List(provider,endpoint).FirstOrDefault(k => k.Id == id); if (key == null) return null;
   byte[] clear = ProtectedData.Unprotect(Convert.FromBase64String(key.Ciphertext), Entropy, DataProtectionScope.CurrentUser);
   try { return Encoding.UTF8.GetString(clear); } finally { CryptographicOperations.ZeroMemory(clear); }
  }
 }
 public void Add(Provider provider,string value,string label,string? endpoint = null) {
  value = value.Trim(); if (value.Length < (provider == Provider.Custom ? 1 : 6) || value.Any(char.IsWhiteSpace)) throw new ArgumentException("Paste the complete key without spaces.");
  var scope = Scope(provider,endpoint); if (provider == Provider.Custom && scope == null) throw new ArgumentException("A custom key must be verified for an HTTPS endpoint.");
  lock (gate) {
   var clear = Encoding.UTF8.GetBytes(value); string cipher;
   try { cipher = Convert.ToBase64String(ProtectedData.Protect(clear, Entropy, DataProtectionScope.CurrentUser)); } finally { CryptographicOperations.ZeroMemory(clear); }
   var ring = rings.GetValueOrDefault(provider) ?? new KeyRing(); var id = Guid.NewGuid().ToString("N");
   ring.Keys.Add(new(id,string.IsNullOrWhiteSpace(label) ? $"Key {ring.Keys.Count + 1}" : label.Trim(),cipher,value.Length > 4 ? value[^4..] : "",DateTimeOffset.UtcNow,scope)); if (scope != null) ring.ActiveByEndpoint[scope] = id; else ring.ActiveId = id; rings[provider] = ring; JsonFiles.Write(path,rings);
  }
 }
 public void Activate(Provider provider,string id,string? endpoint = null) { lock (gate) { var ring = rings[provider]; if (!List(provider,endpoint).Any(k => k.Id == id)) throw new ArgumentException("Unknown key for this endpoint."); var scope = Scope(provider,endpoint); if (scope != null) ring.ActiveByEndpoint[scope] = id; else ring.ActiveId = id; JsonFiles.Write(path,rings); } }
 public void Delete(Provider provider,string id) { lock (gate) { if (!rings.TryGetValue(provider,out var ring)) return; var removed = ring.Keys.FirstOrDefault(k => k.Id == id); ring.Keys.RemoveAll(k => k.Id == id); if (removed?.Endpoint is string scope && ring.ActiveByEndpoint.GetValueOrDefault(scope) == id) { var next = ring.Keys.FirstOrDefault(k => k.Endpoint == scope); if (next != null) ring.ActiveByEndpoint[scope] = next.Id; else ring.ActiveByEndpoint.Remove(scope); } if (ring.ActiveId == id) ring.ActiveId = ring.Keys.FirstOrDefault()?.Id; JsonFiles.Write(path,rings); } }
 public void Clear() { lock (gate) { rings.Clear(); if (File.Exists(path)) File.Delete(path); new KeySetupDraftStore(Path.GetDirectoryName(path)!).ClearAll(); } }
}
public sealed class HistoryStore {
 private readonly string connectionString;
 private readonly object gate = new();
 public HistoryStore(string folder) {
  Directory.CreateDirectory(folder); connectionString = new SqliteConnectionStringBuilder { DataSource = Path.Combine(folder, "history.sqlite3"), Pooling = false }.ToString();
  using var db = Open(); using var command = db.CreateCommand(); command.CommandText = "PRAGMA journal_mode=DELETE; PRAGMA secure_delete=ON; CREATE TABLE IF NOT EXISTS transcripts(id INTEGER PRIMARY KEY, created INTEGER NOT NULL, text TEXT NOT NULL, destination TEXT NOT NULL, duration INTEGER NOT NULL, status TEXT NOT NULL, provider TEXT NOT NULL); CREATE INDEX IF NOT EXISTS ix_created ON transcripts(created);"; command.ExecuteNonQuery();
 }
 private SqliteConnection Open() { var db = new SqliteConnection(connectionString); db.Open(); using var command = db.CreateCommand(); command.CommandText = "PRAGMA secure_delete=ON;"; command.ExecuteNonQuery(); return db; }
 public void Add(string text, string destination, long duration, string status, string provider, QuillSettings settings, DateTimeOffset? now = null) {
  if (settings.PrivateMode || !settings.HistoryEnabled || string.IsNullOrWhiteSpace(text)) return;
  lock (gate) {
   using var db = Open(); using var command = db.CreateCommand(); command.CommandText = "INSERT INTO transcripts(created,text,destination,duration,status,provider) VALUES($created,$text,$dest,$duration,$status,$provider)";
   command.Parameters.AddWithValue("$created", (now ?? DateTimeOffset.UtcNow).ToUnixTimeMilliseconds()); command.Parameters.AddWithValue("$text", text); command.Parameters.AddWithValue("$dest", destination); command.Parameters.AddWithValue("$duration", duration); command.Parameters.AddWithValue("$status", status); command.Parameters.AddWithValue("$provider", provider); command.ExecuteNonQuery();
   Prune(settings, now);
  }
 }
 public void Prune(QuillSettings settings, DateTimeOffset? now = null) {
  lock (gate) {
   using var db = Open(); using var command = db.CreateCommand();
   command.CommandText = "DELETE FROM transcripts WHERE ($cutoff > 0 AND created < $cutoff) OR id NOT IN (SELECT id FROM transcripts ORDER BY created DESC,id DESC LIMIT 1000)";
   command.Parameters.AddWithValue("$cutoff", settings.HistoryRetentionMinutes > 0 ? (now ?? DateTimeOffset.UtcNow).AddMinutes(-settings.HistoryRetentionMinutes).ToUnixTimeMilliseconds() : 0); command.ExecuteNonQuery();
  }
 }
 public List<HistoryEntry> List(QuillSettings settings) {
  lock (gate) { Prune(settings); using var db = Open(); using var command = db.CreateCommand(); command.CommandText = "SELECT id,created,text,destination,duration,status,provider FROM transcripts ORDER BY created DESC,id DESC"; using var reader = command.ExecuteReader(); var entries = new List<HistoryEntry>(); while (reader.Read()) entries.Add(new(reader.GetInt64(0), DateTimeOffset.FromUnixTimeMilliseconds(reader.GetInt64(1)), reader.GetString(2), reader.GetString(3), reader.GetInt64(4), reader.GetString(5), reader.GetString(6))); return entries; }
 }
 public void Delete(long id) { lock (gate) { using var db = Open(); using var command = db.CreateCommand(); command.CommandText = "DELETE FROM transcripts WHERE id=$id"; command.Parameters.AddWithValue("$id", id); command.ExecuteNonQuery(); } }
 public void Clear() { lock (gate) { using var db = Open(); using var command = db.CreateCommand(); command.CommandText = "DELETE FROM transcripts; VACUUM;"; command.ExecuteNonQuery(); } }
}
public sealed class CopyCatalog {
 private readonly Dictionary<string, string> strings;
 public CopyCatalog(string? path = null) { using var json = JsonDocument.Parse(File.ReadAllText(path ?? Path.Combine(AppContext.BaseDirectory, "Assets", "copy", "strings.default.json"))); strings = json.RootElement.GetProperty("strings").EnumerateObject().ToDictionary(p => p.Name, p => p.Value.GetString() ?? ""); }
 public string Get(string key, params object[] args) {
  var value = strings.GetValueOrDefault(key, key);
  return CopyFormatter.Format(value,args);
 }
 public IReadOnlyDictionary<string,string> All => strings;
}
public static class CopyFormatter {
 private static readonly System.Text.RegularExpressions.Regex Spec = new(@"%(?:(\d+)\$)?([-#+0,(]*)(\d*)(?:\.(\d+))?([a-zA-Z%])");
 public static string[] Signature(string template) {
  var implicitIndex = 0; var found = new Dictionary<int,char>();
  foreach (System.Text.RegularExpressions.Match match in Spec.Matches(template)) { var conversion = match.Groups[5].Value[0]; if (conversion is '%' or 'n') continue; var index = match.Groups[1].Success ? int.Parse(match.Groups[1].Value) : ++implicitIndex; found.TryAdd(index,conversion); }
  return found.OrderBy(p => p.Key).Select(p => $"%{p.Key}${p.Value}").ToArray();
 }
 public static string Format(string template, params object[] args) {
  var implicitIndex = 0;
  return Spec.Replace(template,m => {
   var conversion = m.Groups[5].Value[0]; if (conversion == '%') return "%"; if (conversion == 'n') return Environment.NewLine;
   var index = m.Groups[1].Success ? int.Parse(m.Groups[1].Value) - 1 : implicitIndex++; if (index < 0 || index >= args.Length) return m.Value;
   var argument = args[index]; var precision = m.Groups[4].Success ? Math.Min(99,int.Parse(m.Groups[4].Value)) : (int?)null;
   string value;
   try {
    value = char.ToLowerInvariant(conversion) switch { 'd' => Convert.ToInt64(argument).ToString(m.Groups[2].Value.Contains(',') ? "N0" : "D",System.Globalization.CultureInfo.InvariantCulture), 'f' => Convert.ToDouble(argument).ToString("F" + (precision ?? 6),System.Globalization.CultureInfo.InvariantCulture), 'e' => Convert.ToDouble(argument).ToString("e" + (precision ?? 6),System.Globalization.CultureInfo.InvariantCulture), 'g' => Convert.ToDouble(argument).ToString("G" + (precision ?? 6),System.Globalization.CultureInfo.InvariantCulture), 'x' => Convert.ToInt64(argument).ToString("x",System.Globalization.CultureInfo.InvariantCulture), 'b' => argument is bool b ? b ? "true" : "false" : "true", 'c' => Convert.ToChar(argument).ToString(), _ => argument?.ToString() ?? "null" };
   } catch (Exception e) when (e is FormatException or InvalidCastException or OverflowException) { return m.Value; }
   if (char.ToLowerInvariant(conversion) == 's' && precision.HasValue && value.Length > precision) value = value[..precision.Value];
   if (char.IsUpper(conversion)) value = value.ToUpperInvariant();
   if (m.Groups[2].Value.Contains('+') && char.ToLowerInvariant(conversion) is 'd' or 'f' or 'e' or 'g' && !value.StartsWith('-')) value = "+" + value;
   if (m.Groups[3].Value.Length > 0 && int.TryParse(m.Groups[3].Value,out var width)) { width = Math.Min(width,4096); value = m.Groups[2].Value.Contains('-') ? value.PadRight(width) : value.PadLeft(width,m.Groups[2].Value.Contains('0') ? '0' : ' '); }
   return value;
  });
 }
}
