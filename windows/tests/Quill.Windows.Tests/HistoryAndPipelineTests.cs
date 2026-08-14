using Quill.Windows.Core;

namespace Quill.Windows.Tests;

public sealed class HistoryAndPipelineTests
{
    [Fact]
    public void RetentionPrunesExpiredEntries()
    {
        var now = DateTimeOffset.UtcNow;
        var entries = new[]
        {
            Entry(now.AddMinutes(-30), "keep"),
            Entry(now.AddMinutes(-61), "drop"),
        };
        var result = HistoryStore.Prune(entries, 60, now);
        Assert.Single(result);
        Assert.Equal("keep", result[0].Text);
    }

    [Fact]
    public void CustomWordsAreAppliedLastAndCaseInsensitively()
    {
        var text = DictationOrchestrator.ApplyCustomWords("call rohit about ZENO",
            [new("rohit", "Rohit"), new("zeno", "Zeno Health")]);
        Assert.Equal("call Rohit about Zeno Health", text);
    }

    private static HistoryEntry Entry(DateTimeOffset at, string text) =>
        new(Guid.NewGuid(), at, text, "test", SttProvider.Groq, false);
}
