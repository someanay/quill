using System.Text.RegularExpressions;

namespace Quill.Windows.Cleanup;

/// <summary>Fail closed: cleanup can adjust casing and punctuation, never spoken words.</summary>
public static partial class CleanupGuard
{
    public static bool IsSafe(string original, string candidate)
    {
        if (string.IsNullOrWhiteSpace(candidate)) return false;
        var source = Tokens(original);
        var output = Tokens(candidate);
        if (source.Count == 0 || output.Count == 0) return false;

        return source.SequenceEqual(output, StringComparer.OrdinalIgnoreCase);
    }

    private static List<string> Tokens(string value) => TokenRegex().Matches(NormalizeContractions(value))
        .Select(match => match.Value.ToLowerInvariant()).ToList();

    private static string NormalizeContractions(string value) => value
        .Replace("’", "'").Replace("'s", " is", StringComparison.OrdinalIgnoreCase)
        .Replace("n't", " not", StringComparison.OrdinalIgnoreCase)
        .Replace("'re", " are", StringComparison.OrdinalIgnoreCase)
        .Replace("'ve", " have", StringComparison.OrdinalIgnoreCase)
        .Replace("'ll", " will", StringComparison.OrdinalIgnoreCase)
        .Replace("'d", " would", StringComparison.OrdinalIgnoreCase)
        .Replace("'m", " am", StringComparison.OrdinalIgnoreCase);

    [GeneratedRegex(@"[\p{L}\p{N}]+", RegexOptions.CultureInvariant)]
    private static partial Regex TokenRegex();
}
