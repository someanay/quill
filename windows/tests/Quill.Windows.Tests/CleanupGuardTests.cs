using Quill.Windows.Cleanup;

namespace Quill.Windows.Tests;

public sealed class CleanupGuardTests
{
    [Fact]
    public void PunctuationAndFillerCleanupIsAccepted()
    {
        Assert.True(CleanupGuard.IsSafe(
            "hey um can you send the report tomorrow morning",
            "Hey, can you send the report tomorrow morning?"));
    }

    [Fact]
    public void InventedMeaningIsRejected()
    {
        Assert.False(CleanupGuard.IsSafe(
            "send the report tomorrow",
            "I have sent the quarterly report to every client tomorrow morning."));
    }

    [Fact]
    public void EmptyCandidateIsRejected() => Assert.False(CleanupGuard.IsSafe("hello", ""));
}
