using System.Net;
using System.Text;
using Quill.Windows.Cleanup;
using Quill.Windows.Core;
using Quill.Windows.Stt;

namespace Quill.Windows.Tests;

/// <summary>
/// Regression tests for the overnight fix pass (2026-08-18): the cleanup fidelity guard must be
/// enforced (W-1), the cleanup parse must be fail-soft on malformed bodies (W-5), and Gemini must not
/// crash on a 200 response with no candidates (W-4).
/// </summary>
public sealed class PipelineRegressionTests
{
    private sealed class StubHandler(string json, HttpStatusCode status = HttpStatusCode.OK) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(status)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json"),
            });
    }

    private static Task<string> Clean(string raw, string json, HttpStatusCode status = HttpStatusCode.OK) =>
        new CleanupClient(new HttpClient(new StubHandler(json, status)))
            .CleanAsync(raw, SttProvider.Groq, "key", "llama-3.3-70b-versatile", WritingStyle.Neutral, CancellationToken.None);

    [Fact] // W-1: a punctuation/casing-only tidy keeps the same tokens → accepted.
    public async Task Cleanup_keepsCandidate_whenOnlyCasingAndPunctuationChange()
    {
        var result = await Clean("send the report", "{\"choices\":[{\"message\":{\"content\":\"Send the report.\"}}]}");
        Assert.Equal("Send the report.", result);
    }

    [Fact] // W-1: the guard must reject an obeyed/expanded dictation and fall back to raw.
    public async Task Cleanup_fallsBackToRaw_whenModelObeysOrDiverges()
    {
        var result = await Clean("send the report", "{\"choices\":[{\"message\":{\"content\":\"I have sent the report to everyone, as requested.\"}}]}");
        Assert.Equal("send the report", result);
    }

    [Fact] // W-5: an unexpected-shape 200 must fail soft to raw, not throw.
    public async Task Cleanup_fallsBackToRaw_onMalformedSuccessBody()
    {
        Assert.Equal("send the report", await Clean("send the report", "{\"choices\":[]}"));
        Assert.Equal("send the report", await Clean("send the report", "{\"unexpected\":true}"));
    }

    [Fact] // W-5: an HTTP error must fail soft to raw.
    public async Task Cleanup_fallsBackToRaw_onHttpError()
    {
        var result = await Clean("send the report", "{\"error\":\"nope\"}", HttpStatusCode.InternalServerError);
        Assert.Equal("send the report", result);
    }

    [Fact] // W-4: a 200 with no candidates (safety block / empty) → controlled error, not a crash.
    public async Task Gemini_emptyCandidates_throwsTranscriptionException_notIndexCrash()
    {
        var wav = Path.GetTempFileName();
        await File.WriteAllBytesAsync(wav, [0, 1, 2, 3]);
        try
        {
            var client = new GeminiTranscriptionClient(new HttpClient(new StubHandler("{\"candidates\":[]}")), "key", "https://example.com");
            await Assert.ThrowsAsync<TranscriptionException>(() =>
                client.TranscribeAsync(wav, new TranscriptionOptions("gemini-3.1-flash-lite", null, null), CancellationToken.None));
        }
        finally { File.Delete(wav); }
    }

    [Fact] // W-4: a 200 whose candidate carries no parts (finishReason=SAFETY) also stays controlled.
    public async Task Gemini_candidateWithoutParts_throwsTranscriptionException()
    {
        var wav = Path.GetTempFileName();
        await File.WriteAllBytesAsync(wav, [0, 1, 2, 3]);
        try
        {
            var client = new GeminiTranscriptionClient(
                new HttpClient(new StubHandler("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}")), "key", "https://example.com");
            await Assert.ThrowsAsync<TranscriptionException>(() =>
                client.TranscribeAsync(wav, new TranscriptionOptions("gemini-3.1-flash-lite", null, null), CancellationToken.None));
        }
        finally { File.Delete(wav); }
    }
}
