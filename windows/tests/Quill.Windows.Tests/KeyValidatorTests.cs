using Quill.Windows.Core;
using Quill.Windows.Stt;

namespace Quill.Windows.Tests;

public sealed class KeyValidatorTests
{
    [Fact]
    public async Task ValidKeyUsesTheSelectedProviderAndAMinimalWav()
    {
        SttProvider? providerUsed = null;
        var validator = new KeyValidator((provider, _, _) => new FakeClient(() => providerUsed = provider, null));

        var result = await validator.ValidateAsync(SttProvider.OpenRouter, "sk-test", "openai/whisper-large-v3-turbo", "");

        Assert.IsType<KeyValidation.Valid>(result);
        Assert.Equal(SttProvider.OpenRouter, providerUsed);
    }

    [Fact]
    public async Task RejectedKeyIsReportedSeparately()
    {
        var validator = new KeyValidator((_, _, _) => new FakeClient(null,
            new TranscriptionException("The API key was rejected.")));

        var result = await validator.ValidateAsync(SttProvider.Groq, "bad", "whisper-large-v3-turbo", "");

        Assert.IsType<KeyValidation.Rejected>(result);
    }

    [Fact]
    public async Task PaidProbeOnlyReturnsFalseForConfirmedQuotaDenial()
    {
        var validator = new KeyValidator((_, _, _) => new FakeClient(null,
            new TranscriptionException("The provider rate limit or quota was reached.", true)));

        var result = await validator.HasPaidAccessAsync(SttProvider.Gemini, "key", "gemini-3.1-pro-preview", "");

        Assert.False(result);
    }

    private sealed class FakeClient(Action? onCalled, Exception? error) : ITranscriptionClient
    {
        public Task<Transcript> TranscribeAsync(string audioPath, TranscriptionOptions options, CancellationToken cancellation)
        {
            Assert.True(File.Exists(audioPath));
            Assert.True(new FileInfo(audioPath).Length > 44);
            onCalled?.Invoke();
            return error is null ? Task.FromResult(new Transcript("")) : Task.FromException<Transcript>(error);
        }
    }
}
