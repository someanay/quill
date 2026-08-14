namespace Quill.Windows.Core;

public sealed class AppPaths
{
    public AppPaths(string? root = null)
    {
        Root = root ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Quill");
        Directory.CreateDirectory(Root);
        Directory.CreateDirectory(Secrets);
        Directory.CreateDirectory(Recordings);
    }

    public string Root { get; }
    public string Settings => Path.Combine(Root, "settings.json");
    public string History => Path.Combine(Root, "history.json");
    public string Secrets => Path.Combine(Root, "secrets");
    public string Recordings => Path.Combine(Root, "recordings");
}
