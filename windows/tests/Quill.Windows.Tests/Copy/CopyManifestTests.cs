using System.Text;
using Quill.Windows.Copy;

namespace Quill.Windows.Tests;

/// <summary>
/// Guards <c>windows/copy-manifest.json</c> (the Copy Catalog export the copy-authoring tool
/// consumes) against drift from <c>strings.default.json</c> / <see cref="CopyKeys"/> — the C# mirror
/// of the Android app's <c>CopyManifestTest</c>. Regenerate the committed file with:
/// <code>REGEN_COPY_MANIFEST=1 dotnet test --filter CommittedManifest</code>
/// </summary>
public sealed class CopyManifestTests
{
    [Fact]
    public void CommittedManifest_MatchesGenerated()
    {
        var (strings, revision) = CopyManifestGenerator.LoadCatalog();
        var expected = CopyManifestGenerator.Render(strings, revision);
        var file = CopyManifestGenerator.ManifestPath();

        if (Environment.GetEnvironmentVariable("REGEN_COPY_MANIFEST") == "1")
        {
            File.WriteAllText(file, expected, new UTF8Encoding(false));
            return;
        }

        Assert.True(File.Exists(file),
            $"windows/copy-manifest.json is missing at {file}. Regenerate + commit it with: " +
            "REGEN_COPY_MANIFEST=1 dotnet test --filter CommittedManifest");
        Assert.Equal(Normalize(expected), Normalize(File.ReadAllText(file)));
    }

    [Fact]
    public void EveryComplianceKeyExistsInCatalog()
    {
        var (strings, _) = CopyManifestGenerator.LoadCatalog();
        foreach (var key in CopyKeys.Compliance)
            Assert.True(strings.ContainsKey(key), $"COMPLIANCE key missing from strings.default.json: {key}");
    }

    // The committed manifest may be checked out with CRLF; compare on normalized newlines.
    private static string Normalize(string s) => s.Replace("\r\n", "\n");
}
