using System.Diagnostics;
using Quill.Windows.Core;

namespace Quill.Windows.Platform;

public static class ProviderConsoleService
{
    public static bool Open(SttProvider provider)
    {
        var url = ProviderCatalog.All[provider].KeyConsoleUrl;
        if (string.IsNullOrWhiteSpace(url)) return false;
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        return true;
    }
}
