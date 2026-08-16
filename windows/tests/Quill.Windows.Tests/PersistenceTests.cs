using Quill.Windows.Core;
using Quill.Windows.Security;
using Quill.Windows.Stt;

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

    [Fact]
    public async Task SavingKeyAlsoPersistsTheSelectedProvider()
    {
        using var services = new AppServices(_root, new KeyValidator((_, _, _) => new SuccessfulClient()));
        var viewModel = new MainViewModel(services);

        await viewModel.SelectProviderAsync(SttProvider.OpenRouter);
        await viewModel.SaveKeyAsync("sk-or-test-secret");

        var stored = await services.Settings.LoadAsync();
        Assert.Equal(SttProvider.OpenRouter, stored.Provider);
        Assert.Equal("openai/whisper-large-v3-turbo", stored.Model);
        Assert.True(services.Secrets.Has(SttProvider.OpenRouter));
        Assert.False(services.Secrets.Has(SttProvider.Groq));
    }

    [Fact]
    public async Task SwitchingFromOpenRouterToGeminiPersistsAValidModel()
    {
        using var services = new AppServices(_root);
        var viewModel = new MainViewModel(services);

        await viewModel.SelectProviderAsync(SttProvider.OpenRouter);
        await viewModel.SelectProviderAsync(SttProvider.Gemini);

        var stored = await services.Settings.LoadAsync();
        Assert.Equal(SttProvider.Gemini, stored.Provider);
        Assert.Equal(ProviderCatalog.All[SttProvider.Gemini].DefaultModel, stored.Model);
    }

    [Fact]
    public void SettingsPolicyRepairsCrossProviderModelAndLegacyDefaults()
    {
        var normalized = SettingsPolicy.Normalize(new AppSettings
        {
            Provider = SttProvider.Gemini,
            Model = "openai/whisper-large-v3-turbo",
            Hotkey = SettingsPolicy.LegacyDefaultHotkey,
            SessionCapSeconds = 607,
            HistoryRetentionMinutes = 999,
        });

        Assert.Equal(ProviderCatalog.All[SttProvider.Gemini].DefaultModel, normalized.Model);
        Assert.Equal(SettingsPolicy.DefaultHotkey, normalized.Hotkey);
        Assert.Equal(600, normalized.SessionCapSeconds);
        Assert.Equal(60, normalized.HistoryRetentionMinutes);
        Assert.Equal("1 hour", SettingsPolicy.RetentionChoices.Single(choice => choice.Minutes == 60).ToString());
    }

    [Fact]
    public void SettingsPolicyHandlesTransientNullModelDuringProviderSwitch()
    {
        var normalized = SettingsPolicy.Normalize(new AppSettings
        {
            Provider = SttProvider.Gemini,
            Model = null!,
            CleanupModel = null!,
        });

        Assert.Equal(ProviderCatalog.All[SttProvider.Gemini].DefaultModel, normalized.Model);
        Assert.Equal("", normalized.CleanupModel);
    }

    [Fact]
    public void SettingsPolicyKeepsAValidProviderSpecificCleanupModel()
    {
        var normalized = SettingsPolicy.Normalize(new AppSettings
        {
            Provider = SttProvider.OpenRouter,
            CleanupEnabled = true,
            CleanupModel = "openai/gpt-4.1-mini",
        });

        Assert.True(normalized.CleanupEnabled);
        Assert.Equal("openai/gpt-4.1-mini", normalized.CleanupModel);
    }

    [Fact]
    public void SettingsPolicyDisablesCleanupForUnsupportedProviders()
    {
        var normalized = SettingsPolicy.Normalize(new AppSettings
        {
            Provider = SttProvider.Gemini,
            CleanupEnabled = true,
            CleanupModel = "openai/gpt-4o-mini",
        });

        Assert.False(normalized.CleanupEnabled);
        Assert.Empty(normalized.CleanupModel);
    }

    public void Dispose()
    {
        if (Directory.Exists(_root)) Directory.Delete(_root, recursive: true);
    }

    private sealed class SuccessfulClient : ITranscriptionClient
    {
        public Task<Transcript> TranscribeAsync(string audioPath, TranscriptionOptions options, CancellationToken cancellation) =>
            Task.FromResult(new Transcript(""));
    }
}
