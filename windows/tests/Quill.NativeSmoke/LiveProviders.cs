using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Quill.Windows;

namespace Quill.NativeSmoke;

// Opt-in integration checks only. HTTP requests use the unchanged production client
// and real provider endpoints. Recorded synthetic speech is the CI input fixture.
public static partial class Program {
 private const string CleanupControl = "um please send the updated project notes to maya tomorrow uh and include the budget of forty two dollars thank you";
 private sealed record LiveHttp(string Host,string Path,int Status,long Milliseconds,string? Output,string? Finish);
 private sealed class LiveAudit : DelegatingHandler {
  public List<LiveHttp> Requests { get; } = [];
  public LiveAudit() : base(new HttpClientHandler { AllowAutoRedirect = false }) { }
  protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellation) {
   if (request.RequestUri?.Host is not ("generativelanguage.googleapis.com" or "openrouter.ai")) throw new InvalidOperationException("Unexpected live-provider host.");
   var watch = Stopwatch.StartNew();
   try {
    var response = await base.SendAsync(request,cancellation); string? output = null,finish = null;
    if (response.IsSuccessStatusCode) {
     using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellation)); var root = json.RootElement;
     if (root.TryGetProperty("choices",out var choices)) { output = choices[0].GetProperty("message").GetProperty("content").GetString(); finish = choices[0].GetProperty("finish_reason").GetString(); }
     else if (root.TryGetProperty("candidates",out var candidates)) { var candidate = candidates[0]; finish = candidate.GetProperty("finishReason").GetString(); output = string.Join("",candidate.GetProperty("content").GetProperty("parts").EnumerateArray().Where(p => p.TryGetProperty("text",out _) && (!p.TryGetProperty("thought",out var thought) || thought.ValueKind != JsonValueKind.True)).Select(p => p.GetProperty("text").GetString())); }
     else if (root.TryGetProperty("text",out var text)) output = text.GetString();
    }
    Requests.Add(new(request.RequestUri.Host,request.RequestUri.AbsolutePath,(int)response.StatusCode,watch.ElapsedMilliseconds,output,finish)); return response;
   } catch (OperationCanceledException) { Requests.Add(new(request.RequestUri.Host,request.RequestUri.AbsolutePath,0,watch.ElapsedMilliseconds,null,"cancelled-or-timeout")); throw; }
  }
 }
 private static string LiveKey(Provider provider) {
  var configured = Environment.GetEnvironmentVariable(provider == Provider.Gemini ? "QUILL_LIVE_GEMINI_KEY" : "QUILL_LIVE_OPENROUTER_KEY");
  if (!string.IsNullOrWhiteSpace(configured)) return configured;
  if (Environment.GetEnvironmentVariable("GITHUB_ACTIONS") == "true") throw new InvalidOperationException("Live key missing.");
  var file = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Quill","secrets",provider.ToString().ToLowerInvariant() + ".bin");
  var clear = ProtectedData.Unprotect(File.ReadAllBytes(file),Encoding.UTF8.GetBytes("Quill.Windows.BYOK.v1"),DataProtectionScope.CurrentUser);
  try { return Encoding.UTF8.GetString(clear); } finally { CryptographicOperations.ZeroMemory(clear); }
 }
 private static QuillSettings LiveSettings(Provider speech,Provider cleanup) => new() { Provider = speech,SpeechModel = Providers.Get(speech).SpeechModel,CleanupProvider = cleanup,CleanupModel = Providers.Get(cleanup).CleanupModel,CleanupAutomatic = false,CleanupEnabled = true,OnboardingComplete = true,InjectionConsent = true,PrivateMode = true,HistoryEnabled = false,Language = "en" };
 private static void RequireSpeech(string text) {
  var lower = text.ToLowerInvariant(); foreach (var anchor in new[] {"project","notes","alex","tomorrow","budget","thank"}) if (!lower.Contains(anchor,StringComparison.Ordinal)) throw new InvalidOperationException("Live transcription missed a speech anchor.");
  if (!System.Text.RegularExpressions.Regex.IsMatch(lower,@"\b42\b") && !(lower.Contains("forty",StringComparison.Ordinal) && lower.Contains("two",StringComparison.Ordinal))) throw new InvalidOperationException("Live transcription lost the spoken amount.");
 }
 private static void RequireCleanup(LiveAudit audit,string raw,string clean) {
  var response = audit.Requests.LastOrDefault();
  if (response is not { Status:200,Finish:"stop",Output:not null } || response.Path != "/api/v1/chat/completions" && !response.Path.EndsWith("/openai/chat/completions",StringComparison.Ordinal) || response.Output.Trim() != clean || !CleanupGuard.PreservesMeaning(raw,clean)) throw new InvalidOperationException("Cleanup failed, timed out, was rejected or silently fell back.");
 }
 private static async Task<int> LiveRequests(string wavPath,string output) {
  var results = new List<object>(); var failed = false; var wav = File.ReadAllBytes(wavPath);
  foreach (var provider in new[] {Provider.Gemini,Provider.OpenRouter}) {
   var audit = new LiveAudit(); using var client = new ProviderClient(audit);
   try {
    var settings = LiveSettings(provider,provider); var key = LiveKey(provider);
    var raw = await client.Transcribe(wav,settings,key,CancellationToken.None); RequireSpeech(raw);
    var clean = await client.Cleanup(raw,settings,key,"Quill.NativeSmoke",CancellationToken.None); RequireCleanup(audit,raw,clean);
    var control = await client.Cleanup(CleanupControl,settings,key,"Quill.NativeSmoke",CancellationToken.None); RequireCleanup(audit,CleanupControl,control);
    if (control == CleanupControl || control.Split(' ',StringSplitOptions.RemoveEmptyEntries).Any(x => x is "um" or "uh")) throw new InvalidOperationException("Cleanup control did not remove fillers and format the text.");
    results.Add(new { provider,passed = true,raw,clean,control,requests = audit.Requests });
   } catch (Exception error) { failed = true; results.Add(new { provider,passed = false,error = error is ProviderException p ? p.Code : error is InvalidOperationException ? error.Message : error.GetType().Name,requests = audit.Requests }); }
  }
  File.WriteAllText(output,JsonSerializer.Serialize(new { passed = !failed,audioSource = "Recorded synthetic speech fixture; unchanged production HTTP clients",audioSHA256 = Convert.ToHexString(SHA256.HashData(wav)),results },JsonFiles.Options));
  Console.WriteLine(failed ? "FAIL: inspect safe live-provider report." : "PASS: both real transcription and cleanup providers, including cleanup controls."); return failed ? 1 : 0;
 }
 private static async Task<int> LiveCleanupModels(string output) {
  var results = new List<object>();
  foreach (var model in new[] {"liquid/lfm-2.5-2.6b:free","google/gemma-4-26b-a4b-it:free","google/gemma-4-31b-it:free"}) {
   var audit = new LiveAudit(); using var client = new ProviderClient(audit); var settings = LiveSettings(Provider.OpenRouter,Provider.OpenRouter); settings.CleanupModel = model;
   try { var clean = await client.Cleanup(CleanupControl,settings,LiveKey(Provider.OpenRouter),"Quill.NativeSmoke",CancellationToken.None); RequireCleanup(audit,CleanupControl,clean); results.Add(new { model,passed = true,clean,requests = audit.Requests }); }
   catch (Exception e) { results.Add(new { model,passed = false,error = e is InvalidOperationException ? e.Message : e.GetType().Name,requests = audit.Requests }); }
  }
  File.WriteAllText(output,JsonSerializer.Serialize(results,JsonFiles.Options)); return 0;
 }
 private sealed class RecordedSpeechInput(byte[] wav) : IAudioRecorder {
  public event Action<double,long>? Level { add { } remove { } }
  public event Action? AutoStop { add { } remove { } }
  public bool Recording { get; private set; }
  public void Start(QuillSettings settings,bool microphoneTest = false) => Recording = true;
  public Task<AudioTake> Stop() { Recording = false; return Task.FromResult(new AudioTake(wav,(wav.Length - 44) * 1000L / 32000)); }
  public void Dispose() { Recording = false; }
 }
 private sealed class LiveInsertionAudit : ITextInjector {
  private readonly TextInjector native = new();
  public List<object> Attempts { get; } = [];
  public Task<FocusTarget> Capture() => native.Capture();
  public Task<bool> CanType() => native.CanType();
  public async Task<bool> Insert(FocusTarget target,string text) {
   var inserted = await native.Insert(target,text);
   // Observe after the production attempt; an extra preceding Capture could
   // warm UI Automation and conceal the cold-path timing we need to verify.
   var current = await native.Capture();
   var after = new { sensitive=current.Sensitive,editable=current.Editable,sameWindow=current.Window == target.Window,sameElement=current.RuntimeId.SequenceEqual(target.RuntimeId),sameOriginalContext=current.Context == target.Context,modifierPressed=Native.ModifierPressed(),application=current.Application,hasTargetContext=target.Context != null };
   Attempts.Add(new { inserted,after }); return inserted;
  }
 }
 private static async Task LivePipeline(string wavPath,string output) {
  if (Environment.GetEnvironmentVariable("GITHUB_ACTIONS") != "true") throw new InvalidOperationException("Editor automation is restricted to an isolated CI runner.");
  var pipeName = "Quill.LiveProvider." + Guid.NewGuid().ToString("N"); using var pipe = new NamedPipeServerStream(pipeName,PipeDirection.InOut,1,PipeTransmissionMode.Byte,PipeOptions.Asynchronous);
  using var child = Process.Start(new ProcessStartInfo(Environment.ProcessPath!,"--editor " + pipeName) { UseShellExecute = false,CreateNoWindow = true }) ?? throw new InvalidOperationException("Fixture launch failed.");
  var results = new List<object>(); var failed = false; LiveAudit? currentAudit = null; var wav = File.ReadAllBytes(wavPath); var dataRoot = Path.Combine(Path.GetTempPath(),"Quill.LiveProvider",Guid.NewGuid().ToString("N")); Directory.CreateDirectory(dataRoot);
  try {
   await pipe.WaitForConnectionAsync().WaitAsync(TimeSpan.FromSeconds(15)); using var reader = new StreamReader(pipe); using var writer = new StreamWriter(pipe) { AutoFlush = true };
   async Task<Reply> Send(Command command) { await writer.WriteLineAsync(JsonSerializer.Serialize(command)); return JsonSerializer.Deserialize<Reply>((await reader.ReadLineAsync().WaitAsync(TimeSpan.FromSeconds(10)))!)!; }
   foreach (var profile in new[] {(Provider.Gemini,Provider.Gemini),(Provider.OpenRouter,Provider.OpenRouter),(Provider.Gemini,Provider.OpenRouter)}) {
    var folder = Path.Combine(dataRoot,Guid.NewGuid().ToString("N")); Directory.CreateDirectory(folder); var settings = new SettingsStore(folder); var live = LiveSettings(profile.Item1,profile.Item2);
    settings.Value.Provider = live.Provider; settings.Value.SpeechModel = live.SpeechModel; settings.Value.CleanupProvider = live.CleanupProvider; settings.Value.CleanupModel = live.CleanupModel; settings.Value.CleanupAutomatic = false; settings.Value.OnboardingComplete = true; settings.Value.InjectionConsent = true; settings.Value.PrivateMode = true; settings.Value.HistoryEnabled = false;
    var secrets = new SecretStore(folder); foreach (var p in new[] {Provider.Gemini,Provider.OpenRouter}) secrets.Add(p,LiveKey(p),"Temporary live QA");
    var insertion = new LiveInsertionAudit();
    var audit = currentAudit = new LiveAudit(); using var controller = new DictationController(settings,secrets,new HistoryStore(folder),new ProviderClient(audit),new RecordedSpeechInput(wav),insertion,new ClipboardService(),folder);
    try {
     for (var take = 1;take <= 2;take++) {
      const string prefix = "Quill live fixture: "; var prepared = await Send(new(Text:prefix,Start:prefix.Length));
      for (var attempt = 0;attempt < 10 && Native.GetForegroundWindow() != (nint)prepared.Handle;attempt++) { await Task.Delay(300); prepared = await Send(new(Text:prefix,Start:prefix.Length)); }
      if (Native.GetForegroundWindow() != (nint)prepared.Handle) throw new InvalidOperationException("Disposable editor does not own focus.");
      var before = audit.Requests.Count; await controller.Toggle(); if (controller.State != DictationState.Recording) throw new InvalidOperationException("Recording phase did not start: " + controller.Error);
      await controller.StopRecording(); var requests = audit.Requests.Skip(before).ToArray(); var actual = (await Send(new(Action:"read"))).Text;
      var successful = requests.Where(x => x.Status == 200).ToArray();
      if (controller.State != DictationState.Idle || successful.Length != 2 || requests.Any(x => x.Status is not (200 or 503))) throw new InvalidOperationException("Production pipeline did not finish both live requests and insertion: " + controller.Error);
      RequireSpeech(controller.LastText);
      var raw = successful[0].Output ?? throw new InvalidOperationException("Missing live transcription response."); RequireCleanup(audit,raw,controller.LastText);
      if (actual != prefix + controller.LastText || File.Exists(Path.Combine(folder,"pending-audio.dpapi"))) throw new InvalidOperationException("Exact editor readback or audio retirement failed.");
      results.Add(new { speech = profile.Item1,cleanup = profile.Item2,take,passed = true,raw,clean = controller.LastText,editor = actual,requests });
     }
    } catch (Exception error) {
     failed = true; var observed = await Send(new(Action:"read")); results.Add(new { speech=profile.Item1,cleanup=profile.Item2,passed=false,state=controller.State.ToString(),editor=observed.Text,insertion=insertion.Attempts,error=error is ProviderException p ? p.Code : error is InvalidOperationException ? error.Message : error.GetType().Name,requests=audit.Requests }); await controller.Cancel();
    } finally { secrets.Clear(); }
   }
   await writer.WriteLineAsync(JsonSerializer.Serialize(new Command(Action:"quit"))); await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
   File.WriteAllText(output,JsonSerializer.Serialize(new { passed = !failed,audioSource = "Prerecorded synthetic speech at the IAudioRecorder boundary; no mocked HTTP or insertion",audioSHA256 = Convert.ToHexString(SHA256.HashData(wav)),results },JsonFiles.Options));
   if (failed) throw new InvalidOperationException("One or more live profiles failed; all available profiles were attempted.");
  } catch (Exception error) {
   File.WriteAllText(output,JsonSerializer.Serialize(new { passed = false,error = error is ProviderException p ? p.Code : error is InvalidOperationException ? error.Message : error.GetType().Name,results,requests = currentAudit?.Requests },JsonFiles.Options)); throw;
  } finally { if (!child.HasExited) child.Kill(); Directory.Delete(dataRoot,true); }
 }
}
