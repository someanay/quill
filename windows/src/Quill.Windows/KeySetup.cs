using System.Security.Cryptography;
using System.Text;
namespace Quill.Windows;

public sealed class KeySetupDraftStore {
 private sealed class State { public string? Expanded { get; set; } public Dictionary<string,string> Drafts { get; set; } = []; }
 private readonly string path; private readonly State state;
 private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("Quill.Windows.v2/key-setup-drafts");
 public KeySetupDraftStore(string folder) { path = Path.Combine(folder,"key-setup.dpapi.json"); state = JsonFiles.Read(path,() => new State()); }
 public string Expanded(Provider initial = Provider.Gemini) => state.Expanded ?? initial.ToString();
 public void Expand(string provider) { state.Expanded = provider; JsonFiles.Write(path,state); }
 private static string Scope(Provider provider,string endpoint) => provider + (provider == Provider.Custom ? ":" + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(Providers.ValidateEndpoint(endpoint)))) : "");
 public string Read(Provider provider,string endpoint = "") {
  if (!state.Drafts.TryGetValue(Scope(provider,endpoint),out var encrypted)) return "";
  var clear = ProtectedData.Unprotect(Convert.FromBase64String(encrypted),Entropy,DataProtectionScope.CurrentUser);
  try { return Encoding.UTF8.GetString(clear); } finally { CryptographicOperations.ZeroMemory(clear); }
 }
 public void Write(Provider provider,string value,string endpoint = "") {
  if (value.Length > 4096) throw new ArgumentException("Key draft exceeds limit.");
  var scope = Scope(provider,endpoint);
  if (value.Length == 0) state.Drafts.Remove(scope);
  else { var clear = Encoding.UTF8.GetBytes(value); try { state.Drafts[scope] = Convert.ToBase64String(ProtectedData.Protect(clear,Entropy,DataProtectionScope.CurrentUser)); } finally { CryptographicOperations.ZeroMemory(clear); } }
  JsonFiles.Write(path,state);
 }
 public void ClearAll() { state.Drafts.Clear(); state.Expanded = null; JsonFiles.Write(path,state); }
}
public enum KeyCheckState { Empty, Incomplete, Waiting, Checking, Verified, Rejected, Unverified, StorageError }
public sealed class KeySetupSession : IDisposable {
 public static readonly Provider[] GuidedProviders = [Provider.Gemini,Provider.Groq,Provider.OpenRouter];
 private readonly Provider provider; private readonly KeySetupDraftStore drafts; private readonly Func<Provider,string,string,CancellationToken,Task> validate; private readonly TimeSpan debounce;
 private CancellationTokenSource? pending; private long revision; private bool disposed;
 public string Value { get; private set; } = ""; public string Endpoint { get; private set; } = ""; public KeyCheckState State { get; private set; } public bool Saving { get; private set; }
 public bool CanSave => !disposed && !Saving && State == KeyCheckState.Verified;
 public event Action? Changed;
 public KeySetupSession(Provider provider,KeySetupDraftStore drafts,Func<Provider,string,string,CancellationToken,Task> validate,TimeSpan? debounce = null) { this.provider = provider; this.drafts = drafts; this.validate = validate; this.debounce = debounce ?? TimeSpan.FromMilliseconds(650); }
 public static string Mask(Provider provider) => provider switch { Provider.Gemini => "AIza / AQ.",Provider.Groq => "gsk_",Provider.OpenRouter => "sk-or-",Provider.OpenAI => "sk-",_ => "your server's credential" };
 public static bool Plausible(Provider provider,string input,string endpoint = "") {
  var key = input.Trim(); if (key.Length > 4096 || key.Length == 0 || key.Any(char.IsWhiteSpace) || key.Contains("...",StringComparison.Ordinal)) return false;
  if (provider == Provider.Custom) { try { Providers.ValidateEndpoint(endpoint); return true; } catch (ArgumentException) { return false; } }
  var prefix = provider switch { Provider.Gemini => key.StartsWith("AIza",StringComparison.Ordinal) || key.StartsWith("AQ.",StringComparison.Ordinal),Provider.Groq => key.StartsWith("gsk_",StringComparison.Ordinal),Provider.OpenRouter => key.StartsWith("sk-or-",StringComparison.Ordinal),Provider.OpenAI => key.StartsWith("sk-",StringComparison.Ordinal),_ => false };
  return prefix && key.Length >= 20 && key.All(c => char.IsLetterOrDigit(c) || c is '-' or '_' or '.');
 }
 public async Task Update(string value,string endpoint = "") {
  if (disposed || Saving || value.Length > 4096) return;
  pending?.Cancel(); pending?.Dispose(); pending = new(); var token = pending.Token; var ownRevision = ++revision; Value = value; Endpoint = endpoint;
  State = value.Length == 0 ? KeyCheckState.Empty : Plausible(provider,value,endpoint) ? KeyCheckState.Waiting : KeyCheckState.Incomplete;
  try { drafts.Write(provider,value,endpoint); } catch (Exception e) when (e is IOException or UnauthorizedAccessException or CryptographicException or ArgumentException) { State = KeyCheckState.StorageError; Changed?.Invoke(); return; }
  Changed?.Invoke(); if (State != KeyCheckState.Waiting) return;
  try {
   await Task.Delay(debounce,token); if (disposed || ownRevision != revision) return;
   State = KeyCheckState.Checking; Changed?.Invoke(); await validate(provider,value.Trim(),endpoint,token); token.ThrowIfCancellationRequested();
   if (!disposed && ownRevision == revision) State = KeyCheckState.Verified;
  } catch (OperationCanceledException) { return; }
  catch (ProviderException e) { if (ownRevision == revision) State = e.Code == "bad_key" || e.Status is 400 or 401 or 403 ? KeyCheckState.Rejected : KeyCheckState.Unverified; }
  catch (Exception) { if (ownRevision == revision) State = KeyCheckState.Unverified; }
  if (!disposed && ownRevision == revision) Changed?.Invoke();
 }
 public async Task<bool> Save(Func<string,CancellationToken,Task> save) {
  if (!CanSave) return false; Saving = true; Changed?.Invoke(); var token = pending?.Token ?? CancellationToken.None;
  try { await save(Value.Trim(),token); token.ThrowIfCancellationRequested(); drafts.Write(provider,"",Endpoint); return true; }
  catch (OperationCanceledException) { return false; }
  catch (Exception) { State = KeyCheckState.StorageError; return false; }
  finally { Saving = false; if (!disposed) Changed?.Invoke(); }
 }
 public void Dispose() { if (disposed) return; disposed = true; ++revision; pending?.Cancel(); pending?.Dispose(); pending = null; Value = ""; Changed = null; }
}
public static class KeySetupSaving {
 public static async Task Save(Provider provider,string candidate,string endpoint,string customModel,string label,bool activateSpeech,SecretStore secrets,SettingsStore settings,Func<CancellationToken,Task<List<ModelOption>>> models,CancellationToken cancellation) {
  var model = provider == Provider.Custom ? customModel.Trim() : Providers.Get(provider).SpeechModel;
  if (activateSpeech && model.Length == 0) throw new ProviderException("model_missing");
  if (activateSpeech && provider != Provider.Custom) { try { model = ModelCatalog.Rank(await models(cancellation),true).FirstOrDefault(m => m.Free)?.Id ?? model; } catch (OperationCanceledException) { throw; } catch (Exception) { } }
  cancellation.ThrowIfCancellationRequested(); secrets.Add(provider,candidate,label,endpoint);
  if (activateSpeech) { settings.Value.Provider = provider; settings.Value.SpeechModel = model; if (provider == Provider.Custom) { settings.Value.CustomBaseUrl = Providers.ValidateEndpoint(endpoint); settings.Value.CustomModel = customModel.Trim(); } settings.Save(); }
 }
}
