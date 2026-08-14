using System.Text.RegularExpressions;

namespace Quill.Windows.Cleanup;

/// <summary>Fail-soft structural guard: cleaned text must preserve most source tokens.</summary>
public static partial class CleanupGuard
{
    public const double MinimumPrecision = 0.70;
    public const double MinimumRecall = 0.50;

    public static bool IsSafe(string original, string candidate)
    {
        if (string.IsNullOrWhiteSpace(candidate)) return false;
        var source = Tokens(original);
        var output = Tokens(candidate);
        if (source.Count == 0 || output.Count == 0) return false;

        var remaining = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        foreach (var token in source) remaining[token] = remaining.GetValueOrDefault(token) + 1;
        var overlap = 0;
        foreach (var token in output)
        {
            if (remaining.GetValueOrDefault(token) <= 0) continue;
            overlap++;
            remaining[token]--;
        }
        return overlap / (double)output.Count >= MinimumPrecision
               && overlap / (double)source.Count >= MinimumRecall;
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
