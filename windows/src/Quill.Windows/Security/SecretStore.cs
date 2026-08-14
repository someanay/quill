using System.Security.Cryptography;
using System.Text;
using Quill.Windows.Core;

namespace Quill.Windows.Security;

/// <summary>User-scoped DPAPI storage. Ciphertext is useful only to this Windows account.</summary>
public sealed class SecretStore(AppPaths paths)
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("Quill.Windows.BYOK.v1");

    public void Set(SttProvider provider, string value)
    {
        var path = PathFor(provider);
        if (string.IsNullOrWhiteSpace(value)) { Delete(provider); return; }
        File.WriteAllBytes(path, ProtectedData.Protect(
            Encoding.UTF8.GetBytes(value.Trim()), Entropy, DataProtectionScope.CurrentUser));
    }

    public string? Get(SttProvider provider)
    {
        try
        {
            var path = PathFor(provider);
            return File.Exists(path) ? Encoding.UTF8.GetString(ProtectedData.Unprotect(
                File.ReadAllBytes(path), Entropy, DataProtectionScope.CurrentUser)) : null;
        }
        catch (CryptographicException) { return null; }
    }

    public bool Has(SttProvider provider) => !string.IsNullOrWhiteSpace(Get(provider));
    public void Delete(SttProvider provider) { var path = PathFor(provider); if (File.Exists(path)) File.Delete(path); }
    private string PathFor(SttProvider provider) => Path.Combine(paths.Secrets, $"{provider.ToString().ToLowerInvariant()}.bin");
}
