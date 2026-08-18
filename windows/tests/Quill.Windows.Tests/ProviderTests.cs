using Quill.Windows.Stt;
using Quill.Windows.Core;
using System.Net;
using System.Text;

namespace Quill.Windows.Tests;

public sealed class ProviderTests
{
    [Fact]
    public void ShippedProvidersExposeKeyConsoleLinksAndOpenRouterCatalog()
    {
        Assert.All(new[] { SttProvider.Groq, SttProvider.OpenAI, SttProvider.OpenRouter, SttProvider.Gemini },
            provider => Assert.False(string.IsNullOrWhiteSpace(ProviderCatalog.All[provider].KeyConsoleUrl)));
        Assert.Null(ProviderCatalog.All[SttProvider.Custom].KeyConsoleUrl);
        Assert.Contains("openai/whisper-large-v3-turbo", ProviderCatalog.All[SttProvider.OpenRouter].Models);
        Assert.Contains("mistralai/voxtral-small-24b-2507", ProviderCatalog.All[SttProvider.OpenRouter].Models);
    }

    [Theory]
    [InlineData("Transcript: hello world", "hello world")]
    [InlineData("```text\nhello world\n```", "hello world")]
    [InlineData("literal words", "literal words")]
    // Degenerate short fences must not throw (regression: text[3..^3] on length < 6 crashed).
    [InlineData("```", "```")]
    [InlineData("````", "````")]
    [InlineData("`````", "`````")]
    public void GeminiWrappingIsConservativelyRemoved(string raw, string expected) =>
        Assert.Equal(expected, GeminiTranscriptionClient.StripWrapping(raw));

    [Fact]
    public async Task OpenRouterParsesCurrentTranscriptionResponse()
    {
        using var response = new HttpResponseMessage(HttpStatusCode.OK)
        { Content = new StringContent("{\"text\":\"hello from OpenRouter\",\"usage\":{\"seconds\":1.2}}", Encoding.UTF8, "application/json") };
        var transcript = await ResponseParser.ParseAsync(response, CancellationToken.None);
        Assert.Equal("hello from OpenRouter", transcript.Text);
    }

    [Fact]
    public async Task PlainTextSuccessIsAcceptedInsteadOfReportedAsUnreadable()
    {
        using var response = new HttpResponseMessage(HttpStatusCode.OK)
        { Content = new StringContent("hello from a compatible provider", Encoding.UTF8, "text/plain") };
        var transcript = await ResponseParser.ParseAsync(response, CancellationToken.None);
        Assert.Equal("hello from a compatible provider", transcript.Text);
    }

    [Fact]
    public async Task ProviderBadRequestSurfacesUsefulMessage()
    {
        using var response = new HttpResponseMessage(HttpStatusCode.BadRequest)
        { Content = new StringContent("{\"error\":{\"message\":\"Model does not support transcription\"}}", Encoding.UTF8, "application/json") };
        var error = await Assert.ThrowsAsync<TranscriptionException>(() => ResponseParser.ParseAsync(response, CancellationToken.None));
        Assert.Equal("Model does not support transcription", error.Message);
    }

    [Fact]
    public async Task EmptySuccessfulResponseIsFlaggedAsEmptyTranscript()
    {
        using var response = new HttpResponseMessage(HttpStatusCode.OK)
        { Content = new StringContent("{\"text\":\"\"}", Encoding.UTF8, "application/json") };
        var error = await Assert.ThrowsAsync<TranscriptionException>(() => ResponseParser.ParseAsync(response, CancellationToken.None));
        Assert.True(error.EmptyTranscript);
    }
}
