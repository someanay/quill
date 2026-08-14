using NAudio.Wave;
using Quill.Windows.Core;

namespace Quill.Windows.Audio;

public sealed record AudioCaptureResult(string Path, TimeSpan Duration, bool AutoStopped);

/// <summary>16 kHz mono PCM capture with silence and hard-session limits.</summary>
public sealed class AudioCaptureService(AppPaths paths) : IDisposable
{
    private readonly object _sync = new();
    private WaveInEvent? _input;
    private WaveFileWriter? _writer;
    private TaskCompletionSource<AudioCaptureResult>? _completion;
    private DateTimeOffset _started;
    private string? _path;
    private int _silentMilliseconds;
    private bool _autoStopped;
    private int _sessionCapSeconds;
    private bool _silenceAutoStop;

    public bool IsRecording => _input is not null;
    public event EventHandler? AutoStopRequested;

    public void Start(AppSettings settings)
    {
        lock (_sync)
        {
            if (_input is not null) throw new InvalidOperationException("A recording is already active.");
            _started = DateTimeOffset.UtcNow;
            _path = Path.Combine(paths.Recordings, $"dictation-{_started:yyyyMMdd-HHmmss-fff}.wav");
            _sessionCapSeconds = Math.Clamp(settings.SessionCapSeconds, 60, 300);
            _silenceAutoStop = settings.SilenceAutoStop;
            _silentMilliseconds = 0;
            _autoStopped = false;
            _completion = new(TaskCreationOptions.RunContinuationsAsynchronously);
            _input = new WaveInEvent { WaveFormat = new WaveFormat(16_000, 16, 1), BufferMilliseconds = 100 };
            _writer = new WaveFileWriter(_path, _input.WaveFormat);
            _input.DataAvailable += OnDataAvailable;
            _input.RecordingStopped += OnRecordingStopped;
            _input.StartRecording();
        }
    }

    public async Task<AudioCaptureResult> StopAsync()
    {
        Task<AudioCaptureResult> completion;
        lock (_sync)
        {
            if (_input is null || _completion is null) throw new InvalidOperationException("No recording is active.");
            completion = _completion.Task;
            _input.StopRecording();
        }
        return await completion;
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs args)
    {
        bool shouldStop = false;
        lock (_sync)
        {
            _writer?.Write(args.Buffer, 0, args.BytesRecorded);
            if ((DateTimeOffset.UtcNow - _started).TotalSeconds >= _sessionCapSeconds) shouldStop = true;
            if (_silenceAutoStop && (DateTimeOffset.UtcNow - _started).TotalSeconds >= 1.2)
            {
                var rms = RootMeanSquare(args.Buffer, args.BytesRecorded);
                _silentMilliseconds = rms < 0.012 ? _silentMilliseconds + 100 : 0;
                if (_silentMilliseconds >= 1_600) shouldStop = true;
            }
            if (shouldStop) _autoStopped = true;
        }
        if (shouldStop) AutoStopRequested?.Invoke(this, EventArgs.Empty);
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs args)
    {
        TaskCompletionSource<AudioCaptureResult>? completion;
        AudioCaptureResult? result = null;
        lock (_sync)
        {
            _writer?.Dispose();
            _writer = null;
            if (_input is not null)
            {
                _input.DataAvailable -= OnDataAvailable;
                _input.RecordingStopped -= OnRecordingStopped;
                _input.Dispose();
                _input = null;
            }
            completion = _completion;
            if (args.Exception is null && _path is not null)
                result = new AudioCaptureResult(_path, DateTimeOffset.UtcNow - _started, _autoStopped);
            _completion = null;
        }
        if (args.Exception is not null) completion?.TrySetException(args.Exception);
        else if (result is not null) completion?.TrySetResult(result);
    }

    internal static double RootMeanSquare(byte[] buffer, int length)
    {
        if (length < 2) return 0;
        double sum = 0;
        var samples = length / 2;
        for (var i = 0; i < samples; i++)
        {
            var sample = BitConverter.ToInt16(buffer, i * 2) / 32768.0;
            sum += sample * sample;
        }
        return Math.Sqrt(sum / samples);
    }

    public void Dispose()
    {
        lock (_sync) { if (_input is not null) _input.StopRecording(); }
    }
}
