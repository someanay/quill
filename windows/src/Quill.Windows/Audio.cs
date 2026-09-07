using System.Diagnostics;
using NAudio.Wave;
namespace Quill.Windows;
public record InputDevice(int Id, string Name);
public record AudioTake(byte[] Wav, long DurationMs);
public interface IAudioRecorder : IDisposable {
 event Action<double,long>? Level;
 event Action? AutoStop;
 bool Recording { get; }
 void Start(QuillSettings settings, bool microphoneTest = false);
 Task<AudioTake> Stop();
}
public sealed class AudioRecorder : IAudioRecorder {
 private WaveInEvent? input; private MemoryStream? pcm; private TaskCompletionSource<AudioTake>? stopped; private Stopwatch? clock; private readonly object gate = new(); private Exception? captureError;
 public event Action<double,long>? Level; public event Action? AutoStop;
 public bool Recording => input != null;
 public static List<InputDevice> Devices() { var devices = new List<InputDevice> { new(-1, "Windows default microphone") }; for (var i = 0; i < WaveIn.DeviceCount; i++) devices.Add(new(i, WaveIn.GetCapabilities(i).ProductName)); return devices; }
 public void Start(QuillSettings settings, bool microphoneTest = false) {
  if (Recording) throw new InvalidOperationException("Microphone already active.");
  var devices = Devices(); var selected = settings.InputDeviceName.Length > 0 ? devices.FirstOrDefault(d => d.Name == settings.InputDeviceName)?.Id ?? -1 : settings.InputDevice;
  if (selected >= WaveIn.DeviceCount || selected < -1) selected = -1;
  if (WaveIn.DeviceCount == 0) throw new ProviderException("microphone_missing");
  pcm = new(); stopped = new(TaskCreationOptions.RunContinuationsAsynchronously); captureError = null; clock = Stopwatch.StartNew(); var silence = new SilenceDetector(); var stopRequested = 0;
  input = new WaveInEvent { DeviceNumber = selected, WaveFormat = new WaveFormat(16000, 16, 1), BufferMilliseconds = 50, NumberOfBuffers = 3 };
  input.DataAvailable += (_, e) => {
   double sum = 0; for (var i = 0; i + 1 < e.BytesRecorded; i += 2) { var sample = (short)(e.Buffer[i] | e.Buffer[i + 1] << 8); sum += (double)sample * sample; }
   var rms = Math.Sqrt(sum / Math.Max(1, e.BytesRecorded / 2)); long elapsed = clock.ElapsedMilliseconds;
   lock (gate) if (pcm != null && !microphoneTest && pcm.Length + e.BytesRecorded <= 9_600_000) pcm.Write(e.Buffer, 0, e.BytesRecorded);
   Level?.Invoke(rms, elapsed);
   if ((elapsed >= (microphoneTest ? 30_000 : settings.SessionCapSeconds * 1000L) || !microphoneTest && silence.Observe(rms, elapsed)) && Interlocked.Exchange(ref stopRequested, 1) == 0) AutoStop?.Invoke();
  };
  input.RecordingStopped += (_, e) => {
   clock.Stop(); byte[] bytes; lock (gate) bytes = pcm?.ToArray() ?? []; captureError = e.Exception;
   if (captureError != null && bytes.Length < 3200) stopped.TrySetException(new ProviderException("microphone_error")); else stopped.TrySetResult(new(Wav(bytes),clock.ElapsedMilliseconds));
   if (captureError != null) AutoStop?.Invoke();
  };
  try { input.StartRecording(); } catch { input.Dispose(); input = null; pcm.Dispose(); pcm = null; throw new ProviderException("microphone_denied"); }
 }
 public async Task<AudioTake> Stop() {
  var current = input; var completion = stopped; if (current == null || completion == null) return new(Wav([]), 0);
  try { current.StopRecording(); return await completion.Task.WaitAsync(TimeSpan.FromSeconds(5)); }
  finally { current.Dispose(); input = null; lock (gate) { pcm?.Dispose(); pcm = null; } }
 }
 public static byte[] Wav(byte[] pcm) {
  using var output = new MemoryStream(); using var w = new BinaryWriter(output); w.Write("RIFF"u8); w.Write(36 + pcm.Length); w.Write("WAVEfmt "u8); w.Write(16); w.Write((short)1); w.Write((short)1); w.Write(16000); w.Write(32000); w.Write((short)2); w.Write((short)16); w.Write("data"u8); w.Write(pcm.Length); w.Write(pcm); return output.ToArray();
 }
 public void Dispose() { input?.Dispose(); input = null; lock (gate) { pcm?.Dispose(); pcm = null; } }
}
