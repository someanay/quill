using Quill.Windows.Core;
using Quill.Windows.Stt;

namespace Quill.Windows.Tests;

public sealed class RomanizePromptTests
{
    [Theory]
    [InlineData("en")]
    [InlineData("EN")]
    public void EnglishUsesHinglishRomanizationGuidance(string language) =>
        Assert.Contains("Roman/Latin", RomanizePrompt.ForLanguage(language));

    [Theory]
    [InlineData("hi")]
    [InlineData("auto")]
    [InlineData(null)]
    public void OtherLanguageChoicesDoNotForceRomanization(string? language) =>
        Assert.Null(RomanizePrompt.ForLanguage(language));

    [Fact]
    public void CustomVocabularyAndHinglishGuidanceAreComposed()
    {
        var settings = new AppSettings { Language = "en", CustomWords = [new WordReplacement("quill", "Quill")] };
        var prompt = DictationOrchestrator.TranscriptionPrompt(settings);
        Assert.Contains("Roman/Latin", prompt);
        Assert.Contains("Preferred spellings: Quill", prompt);
    }
}
