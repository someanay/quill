using Quill.Windows.Audio;
using System.Net.Http;
using Quill.Windows.Cleanup;
using Quill.Windows.Platform;
using Quill.Windows.Security;
using Quill.Windows.Stt;

namespace Quill.Windows.Core;

public sealed class AppServices : IDisposable
{
    public AppServices(string? root = null, KeyValidator? keyValidator = null)
    {
        Paths = new AppPaths(root);
        Settings = new SettingsStore(Paths);
        Secrets = new SecretStore(Paths);
        History = new HistoryStore(Paths);
        Http = new HttpClient { Timeout = TimeSpan.FromSeconds(50) };
        Audio = new AudioCaptureService(Paths);
        Injector = new FocusedTextInjector();
        Cleanup = new CleanupClient(Http);
        KeyValidator = keyValidator ?? new KeyValidator(Http);
        Dictation = new DictationOrchestrator(Settings, Secrets, History, Audio, Injector, Cleanup, Http);
    }

    public AppPaths Paths { get; }
    public SettingsStore Settings { get; }
    public SecretStore Secrets { get; }
    public HistoryStore History { get; }
    public HttpClient Http { get; }
    public AudioCaptureService Audio { get; }
    public FocusedTextInjector Injector { get; }
    public CleanupClient Cleanup { get; }
    public KeyValidator KeyValidator { get; }
    public DictationOrchestrator Dictation { get; }

    public void Dispose()
    {
        Dictation.Dispose();
        Http.Dispose();
    }
}
