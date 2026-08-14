using Quill.Windows.Stt;

namespace Quill.Windows.Tests;

public sealed class ProviderTests
{
    [Theory]
    [InlineData("Transcript: hello world", "hello world")]
    [InlineData("```text\nhello world\n```", "hello world")]
    [InlineData("literal words", "literal words")]
    public void GeminiWrappingIsConservativelyRemoved(string raw, string expected) =>
        Assert.Equal(expected, GeminiTranscriptionClient.StripWrapping(raw));
}
