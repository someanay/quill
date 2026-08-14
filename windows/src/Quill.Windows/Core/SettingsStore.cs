using System.Text.Json;

namespace Quill.Windows.Core;

public sealed class SettingsStore(AppPaths paths)
{
    private readonly SemaphoreSlim _gate = new(1, 1);

    public async Task<AppSettings> LoadAsync()
    {
        await _gate.WaitAsync();
        try
        {
            if (!File.Exists(paths.Settings)) return new AppSettings();
            await using var stream = File.OpenRead(paths.Settings);
            return await JsonSerializer.DeserializeAsync<AppSettings>(stream, JsonDefaults.Options)
                   ?? new AppSettings();
        }
        catch (JsonException)
        {
            return new AppSettings();
        }
        finally { _gate.Release(); }
    }

    public async Task SaveAsync(AppSettings settings)
    {
        await _gate.WaitAsync();
        try
        {
            var temporary = paths.Settings + ".tmp";
            await using (var stream = File.Create(temporary))
                await JsonSerializer.SerializeAsync(stream, settings, JsonDefaults.Options);
            File.Move(temporary, paths.Settings, true);
        }
        finally { _gate.Release(); }
    }
}
