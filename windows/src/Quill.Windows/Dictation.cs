using System.Security.Cryptography;
using System.Text.Json;
namespace Quill.Windows;
public enum DictationState { Idle, Recording, Transcribing, Paste, Error }
public sealed class DictationController : IDisposable {
 private readonly SettingsStore settings; private readonly SecretStore secrets; private readonly HistoryStore history; private readonly ProviderClient provider; private readonly IAudioRecorder recorder; private readonly ITextInjector injector; private readonly IClipboardService clipboard;
 private readonly SemaphoreSlim gate = new(1,1); private CancellationTokenSource? cancellation; private AudioTake? pending; private QuillSettings? sessionSettings; private FocusTarget? target; private readonly string pendingPath;
 public DictationState State { get; private set; }
 public string LastText { get; private set; } = "";
 public string Error { get; private set; } = "";
 public long ElapsedMs { get; private set; }
 public double Rms { get; private set; }
 public event Action? Changed;
 public DictationController(SettingsStore settings, SecretStore secrets, HistoryStore history, ProviderClient provider, IAudioRecorder recorder, ITextInjector injector, IClipboardService clipboard, string folder) {
  this.settings = settings; this.secrets = secrets; this.history = history; this.provider = provider; this.recorder = recorder; this.injector = injector; this.clipboard = clipboard; pendingPath = Path.Combine(folder, "pending-audio.dpapi");
  recorder.Level += (rms, elapsed) => { Rms = rms; ElapsedMs = elapsed; Changed?.Invoke(); };
  if (File.Exists(pendingPath)) { State = DictationState.Error; Error = "recording_recovered"; }
 }
 private void Set(DictationState state, string error = "") { State = state; Error = error; Changed?.Invoke(); }
 public async Task Toggle() {
  if (!await gate.WaitAsync(0)) return;
  try {
   if (State == DictationState.Recording) { pending = await recorder.Stop(); await TranscribePending(); }
   else if (State == DictationState.Paste) { if (await clipboard.Paste(LastText,injector)) Set(DictationState.Idle); else Set(DictationState.Paste,"focus_to_paste"); }
   else if (State == DictationState.Error) await RetryInternal();
   else if (State == DictationState.Idle) await Start();
  } catch (OperationCanceledException) { ClearPending(); Set(DictationState.Idle); }
  catch (Exception e) { SavePending(); Set(DictationState.Error, Code(e)); }
  finally { gate.Release(); }
 }
 private async Task Start() {
  if (!settings.Value.OnboardingComplete || !settings.Value.InjectionConsent) throw new ProviderException("setup_required");
  target = await injector.Capture(); if (target.Sensitive) throw new ProviderException("sensitive_field");
  sessionSettings = JsonSerializer.Deserialize<QuillSettings>(JsonSerializer.Serialize(settings.Value))!;
  if (string.IsNullOrWhiteSpace(secrets.Read(sessionSettings.Provider))) throw new ProviderException("key_missing");
  cancellation?.Dispose(); cancellation = new(); LastText = ""; ElapsedMs = 0; Rms = 0;
  recorder.Start(sessionSettings); Set(DictationState.Recording);
 }
 private async Task RetryInternal() {
  if (pending == null && File.Exists(pendingPath)) {
   var bytes = ProtectedData.Unprotect(File.ReadAllBytes(pendingPath), null, DataProtectionScope.CurrentUser);
   pending = new(bytes, Math.Max(0, bytes.Length - 44) * 1000L / 32000); target = null;
  }
  if (pending == null) { Set(DictationState.Idle); return; }
  sessionSettings = JsonSerializer.Deserialize<QuillSettings>(JsonSerializer.Serialize(settings.Value))!;
  cancellation?.Dispose(); cancellation = new(); await TranscribePending();
 }
 private async Task TranscribePending() {
  var take = pending ?? throw new InvalidOperationException(); var s = sessionSettings ?? settings.Value; var token = cancellation?.Token ?? CancellationToken.None;
  if (take.Wav.Length < 3244) { ClearPending(); throw new ProviderException("empty_recording"); }
  Set(DictationState.Transcribing);
  var raw = await provider.Transcribe(take.Wav, s, secrets.Read(s.Provider) ?? "", token); token.ThrowIfCancellationRequested();
  var text = raw;
  if (s.UseCleanup) { var key = secrets.Read(s.EffectiveCleanupProvider); if (!string.IsNullOrWhiteSpace(key)) text = await provider.Cleanup(raw, s, key, target?.Application ?? "", token); }
  token.ThrowIfCancellationRequested(); text = Words.Apply(text, s.CustomWords); LastText = text;
  var inserted = target != null && await injector.Insert(target,text);
  if (!inserted) clipboard.Copy(text);
  // A session that began private stays private; toggling private during a request also prevents saving.
  s.PrivateMode |= settings.Value.PrivateMode;
  history.Add(text, target?.Application ?? "", take.DurationMs, inserted ? "SUCCESS" : "FALLBACK_PASTE", s.Provider.ToString(), s);
  ClearPending(); Set(inserted ? DictationState.Idle : DictationState.Paste);
 }
 public async Task Cancel() {
  cancellation?.Cancel(); await gate.WaitAsync();
  try { if (recorder.Recording) await recorder.Stop(); ClearPending(); LastText = ""; Set(DictationState.Idle); }
  finally { gate.Release(); }
 }
 private void SavePending() { if (pending == null) return; try { var encrypted = ProtectedData.Protect(pending.Wav, null, DataProtectionScope.CurrentUser); File.WriteAllBytes(pendingPath, encrypted); } catch (IOException) { /* Retain the take in memory if storage is unavailable. */ } }
 private void ClearPending() { pending = null; if (File.Exists(pendingPath)) File.Delete(pendingPath); }
 public static string Code(Exception e) => e is ProviderException p ? p.Code : e is CryptographicException ? "key_unavailable" : e is IOException ? "storage_error" : e is ArgumentException ? "invalid_configuration" : "unexpected_error";
 public void Dispose() { cancellation?.Cancel(); cancellation?.Dispose(); recorder.Dispose(); provider.Dispose(); gate.Dispose(); }
}
