using Quill.Windows.Audio;
using System.Net.Http;
using Quill.Windows.Cleanup;
using Quill.Windows.Platform;
using Quill.Windows.Security;

namespace Quill.Windows.Core;

public sealed class AppServices : IDisposable
{
    public AppServices()
    {
        Paths = new AppPaths();
        Settings = new SettingsStore(Paths);
        Secrets = new SecretStore(Paths);
        History = new HistoryStore(Paths);
        Http = new HttpClient { Timeout = TimeSpan.FromSeconds(50) };
        Audio = new AudioCaptureService(Paths);
        Injector = new FocusedTextInjector();
        Cleanup = new CleanupClient(Http);
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
    public DictationOrchestrator Dictation { get; }

    public void Dispose()
    {
        Dictation.Dispose();
        Http.Dispose();
    }
}
