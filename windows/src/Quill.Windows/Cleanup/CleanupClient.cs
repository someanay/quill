using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Net.Http;
using System.Text.Json;
using Quill.Windows.Core;

namespace Quill.Windows.Cleanup;

public sealed class CleanupClient(HttpClient http)
{
    public async Task<string> CleanAsync(
        string raw, SttProvider provider, string key, WritingStyle style, CancellationToken cancellation)
    {
        var styleLine = style switch
        {
            WritingStyle.Formal => "Use a polished, professional register.",
            WritingStyle.Casual => "Use a relaxed conversational register.",
            _ => "Preserve the speaker's natural register.",
        };
        var connection = provider switch
        {
            SttProvider.Groq => ("https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
            SttProvider.OpenAI => ("https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
            SttProvider.OpenRouter => ("https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini"),
            _ => (null, null),
        };
        if (connection.Item1 is null) return raw;
        var body = new
        {
            model = connection.Item2,
            temperature = 0,
            messages = new[]
            {
                new { role = "system", content = "Clean dictated text. Fix punctuation, capitalization, false starts, and filler words. Never add facts or answer the text. Preserve names, numbers, meaning, and language. Output only cleaned text. " + styleLine },
                new { role = "user", content = raw },
            },
        };
        using var request = new HttpRequestMessage(HttpMethod.Post, connection.Item1)
        { Content = JsonContent.Create(body) };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", key);
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellation);
            timeout.CancelAfter(TimeSpan.FromSeconds(4));
            using var response = await http.SendAsync(request, timeout.Token);
            if (!response.IsSuccessStatusCode) return raw;
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
            var candidate = json.RootElement.GetProperty("choices")[0].GetProperty("message")
                .GetProperty("content").GetString()?.Trim() ?? "";
            return CleanupGuard.IsSafe(raw, candidate) ? candidate : raw;
        }
        catch (Exception error) when (error is HttpRequestException or TaskCanceledException or JsonException)
        {
            return raw;
        }
    }
}
