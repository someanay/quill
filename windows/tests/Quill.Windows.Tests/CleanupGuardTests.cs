using Quill.Windows.Cleanup;

namespace Quill.Windows.Tests;

public sealed class CleanupGuardTests
{
    [Fact]
    public void PunctuationAndCasingCleanupIsAccepted()
    {
        Assert.True(CleanupGuard.IsSafe(
            "hey can you send the report tomorrow morning",
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

    [Fact]
    public void AnswerInsteadOfTranscriptIsRejected() => Assert.False(CleanupGuard.IsSafe("How are you?", "Fine."));

    [Fact]
    public void AddedOrRemovedWordsAreRejected()
    {
        Assert.False(CleanupGuard.IsSafe("I need the draft today", "I need the draft."));
        Assert.False(CleanupGuard.IsSafe("I need the draft today", "I need the final draft today."));
    }
}
