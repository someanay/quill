using System.Diagnostics;
using Quill.Windows.Core;

namespace Quill.Windows.Platform;

public static class ProviderConsoleService
{
    public static bool Open(SttProvider provider)
    {
        var url = ProviderCatalog.All[provider].KeyConsoleUrl;
        if (string.IsNullOrWhiteSpace(url)) return false;
        // ShellExecute throws Win32Exception when there is no default browser association; the sync
        // click handler doesn't catch and there is no DispatcherUnhandledException handler, so an
        // unwrapped throw crashes the app. Best-effort, mirroring the rest of the platform layer.
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }
}
