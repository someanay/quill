using System.Text.RegularExpressions;

namespace Quill.Windows.Tests;

/// <summary>
/// C# port of the Quill copy placeholder parser. Must agree token-for-token with the Android app's
/// <c>Placeholders</c> (app/.../copy/ota/Placeholders.kt) and the copy-authoring tool's extractor
/// (quill-copy-tool src/lib/placeholders.ts / publish_gate.py). Two rules are kept in lock-step
/// across all three: the space is NOT in the flag class (literal "50% off" must not read a phantom
/// "% o" token), and <c>%%</c> / <c>%n</c> consume no argument, so they are ignored.
/// </summary>
internal static class Placeholders
{
    // %[argument_index$][flags][width][.precision]conversion — index (group 1) + conversion (group 2).
    private static readonly Regex Spec =
        new(@"%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])", RegexOptions.Compiled);

    /// <summary>argument-index -> conversion char, in ascending index order.</summary>
    public static SortedDictionary<int, char> Signature(string template)
    {
        var sig = new SortedDictionary<int, char>();
        var implicitIndex = 0;
        foreach (Match m in Spec.Matches(template))
        {
            var conv = m.Groups[2].Value[0];
            if (conv is '%' or 'n') continue; // consume no argument
            var explicitIndex = m.Groups[1].Value;
            var index = explicitIndex.Length > 0 ? int.Parse(explicitIndex) : ++implicitIndex;
            if (!sig.ContainsKey(index)) sig[index] = conv; // first wins, like Kotlin putIfAbsent
        }
        return sig;
    }

    /// <summary>Canonical "%&lt;index&gt;$&lt;conv&gt;" tokens, sorted by argument index.</summary>
    public static List<string> Tokens(string template)
        => Signature(template).Select(kv => $"%{kv.Key}${kv.Value}").ToList();
}
