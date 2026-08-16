namespace Quill.Windows.Stt;

/// <summary>
/// Keeps code-mixed Hindi in the script people commonly type with on Windows:
/// Latin/Roman letters ("aaj ka"), not Devanagari. This deliberately applies
/// only to explicit English; auto-detect and Hindi remain the user's choice.
/// </summary>
public static class RomanizePrompt
{
    public const string Hinglish =
        "The speaker mixes English and Hindi (Hinglish) and writes Hindi words in Roman/Latin script. " +
        "Transcribe Hindi words in Roman/Latin letters, not Devanagari. Example: Aaj ka plan kya hai? Let's meet kal, theek hai.";

    public static string? ForLanguage(string? language) =>
        string.Equals(language, "en", StringComparison.OrdinalIgnoreCase) ? Hinglish : null;
}
