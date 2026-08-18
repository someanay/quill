using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;
using Quill.Windows.Copy;

namespace Quill.Windows.Tests;

/// <summary>
/// Generates <c>windows/copy-manifest.json</c> — the machine-readable export of the Windows Copy
/// Catalog for the external copy-authoring tool (quill-copy-tool), mirroring the Android app's
/// <c>CopyManifest</c>. Lives in the test assembly on purpose: tooling metadata, never shipped with
/// the app. Deterministic — keys sorted by ordinal/UTF-8 byte order (all keys are ASCII, so the two
/// orders coincide), 2-space indent, LF line endings, trailing newline. Placeholder tokens come from
/// the shared <see cref="Placeholders"/> parser so the copy tool can prove its extractor agrees.
/// </summary>
internal static class CopyManifestGenerator
{
    // Same inputs as the Android generator's TEST_VECTORS; expected tokens are always computed by
    // Placeholders (never hand-written), so the vectors document the parser's real behaviour.
    public static readonly string[] TestVectors =
    {
        "", "No placeholders here", "%s", "%d", "%1$s", "%2$.2f",
        "%1$s and %1$s again", "%s = %d", "%2$s then %1$d", "%05.2f",
        "100%%", "50% off", "%1$d items", "line one%nline two", "%d%% done",
    };

    private static readonly JsonSerializerOptions Pretty = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping, // emit · — … ① literally, like Android
    };

    public static (Dictionary<string, string> Strings, int Revision) LoadCatalog()
    {
        var root = JsonNode.Parse(File.ReadAllText(AssetPath()))
                   ?? throw new InvalidOperationException("strings.default.json parsed to null");
        var revision = root["revision"]!.GetValue<int>();
        var strings = new Dictionary<string, string>();
        foreach (var kv in root["strings"]!.AsObject())
            strings[kv.Key] = kv.Value!.GetValue<string>();
        return (strings, revision);
    }

    public static string Render(IReadOnlyDictionary<string, string> strings, int revision)
    {
        var keys = new JsonObject();
        foreach (var key in strings.Keys.OrderBy(k => k, StringComparer.Ordinal))
        {
            var value = strings[key];
            keys[key] = new JsonObject
            {
                ["default"] = value,
                ["placeholders"] = ToArray(Placeholders.Tokens(value)),
                ["overridable"] = !CopyKeys.Compliance.Contains(key),
                ["optionalBlank"] = CopyKeys.OptionalBlank.Contains(key),
            };
        }

        var vectors = new JsonArray();
        foreach (var input in TestVectors)
            vectors.Add(new JsonObject
            {
                ["input"] = input,
                ["tokens"] = ToArray(Placeholders.Tokens(input)),
            });

        var manifest = new JsonObject
        {
            ["revision"] = revision,
            ["keys"] = keys,
            ["placeholderTestVectors"] = vectors,
        };

        var json = manifest.ToJsonString(Pretty).Replace("\r\n", "\n");
        return json.EndsWith('\n') ? json : json + "\n";
    }

    private static JsonArray ToArray(IEnumerable<string> items)
    {
        var arr = new JsonArray();
        foreach (var s in items) arr.Add(s);
        return arr;
    }

    public static string ManifestPath() => Path.Combine(WindowsDir(), "copy-manifest.json");

    private static string AssetPath() =>
        Path.Combine(WindowsDir(), "src", "Quill.Windows", "Assets", "copy", "strings.default.json");

    // Walk up from the test bin directory to the windows/ dir, identified by the catalog asset.
    private static string WindowsDir()
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir != null; dir = dir.Parent)
        {
            var probe = Path.Combine(dir.FullName, "src", "Quill.Windows", "Assets", "copy", "strings.default.json");
            if (File.Exists(probe)) return dir.FullName;
        }
        throw new InvalidOperationException(
            "Could not locate the windows/ directory (src/Quill.Windows/Assets/copy/strings.default.json) " +
            $"from {AppContext.BaseDirectory}");
    }
}
