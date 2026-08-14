using Quill.Windows.Core;
using Quill.Windows.Security;

namespace Quill.Windows.Tests;

public sealed class PersistenceTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "quill-windows-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public async Task SettingsRoundTrip()
    {
        var store = new SettingsStore(new AppPaths(_root));
        var expected = new AppSettings { Provider = SttProvider.Gemini, Model = "gemini-3.6-flash", PrivateMode = true, SessionCapSeconds = 120 };
        await store.SaveAsync(expected);
        var actual = await store.LoadAsync();
        Assert.Equal(expected.Provider, actual.Provider);
        Assert.Equal(expected.Model, actual.Model);
        Assert.True(actual.PrivateMode);
        Assert.Equal(120, actual.SessionCapSeconds);
    }

    [Fact]
    public void DpapiSecretRoundTripIsUserScoped()
    {
        var store = new SecretStore(new AppPaths(_root));
        store.Set(SttProvider.Groq, "gsk_test_secret");
        Assert.Equal("gsk_test_secret", store.Get(SttProvider.Groq));
        store.Delete(SttProvider.Groq);
        Assert.Null(store.Get(SttProvider.Groq));
    }

    public void Dispose()
    {
        if (Directory.Exists(_root)) Directory.Delete(_root, recursive: true);
    }
}
