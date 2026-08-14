using Microsoft.Win32;

namespace Quill.Windows.Platform;

public static class StartupService
{
    private const string KeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
    public static void SetEnabled(bool enabled)
    {
        using var key = Registry.CurrentUser.OpenSubKey(KeyPath, writable: true);
        if (enabled)
            key?.SetValue("Quill", $"\"{Environment.ProcessPath}\" --background");
        else key?.DeleteValue("Quill", throwOnMissingValue: false);
    }
}
