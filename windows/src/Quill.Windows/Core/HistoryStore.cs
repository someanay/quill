using System.Text.Json;

namespace Quill.Windows.Core;

public sealed class HistoryStore(AppPaths paths)
{
    private const int MaximumEntries = 1_000;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public async Task<IReadOnlyList<HistoryEntry>> ReadAsync(int retentionMinutes)
    {
        await _gate.WaitAsync();
        try
        {
            var entries = await ReadUnsafeAsync();
            var pruned = Prune(entries, retentionMinutes, DateTimeOffset.UtcNow);
            if (pruned.Count != entries.Count) await WriteUnsafeAsync(pruned);
            return pruned;
        }
        finally { _gate.Release(); }
    }

    public async Task AddAsync(HistoryEntry entry, AppSettings settings)
    {
        if (!settings.HistoryEnabled || settings.PrivateMode) return;
        await _gate.WaitAsync();
        try
        {
            var entries = await ReadUnsafeAsync();
            entries.Insert(0, entry);
            await WriteUnsafeAsync(Prune(entries, settings.HistoryRetentionMinutes, DateTimeOffset.UtcNow));
        }
        finally { _gate.Release(); }
    }

    public async Task ClearAsync()
    {
        await _gate.WaitAsync();
        try { if (File.Exists(paths.History)) File.Delete(paths.History); }
        finally { _gate.Release(); }
    }

    internal static List<HistoryEntry> Prune(IEnumerable<HistoryEntry> entries, int minutes, DateTimeOffset now)
    {
        var cutoff = minutes <= 0 ? DateTimeOffset.MinValue : now.AddMinutes(-minutes);
        return entries.Where(it => it.CreatedAt >= cutoff)
            .OrderByDescending(it => it.CreatedAt).Take(MaximumEntries).ToList();
    }

    private async Task<List<HistoryEntry>> ReadUnsafeAsync()
    {
        if (!File.Exists(paths.History)) return [];
        try
        {
            await using var stream = File.OpenRead(paths.History);
            return await JsonSerializer.DeserializeAsync<List<HistoryEntry>>(stream, JsonDefaults.Options) ?? [];
        }
        catch (JsonException) { return []; }
    }

    private async Task WriteUnsafeAsync(List<HistoryEntry> entries)
    {
        var temporary = paths.History + ".tmp";
        await using (var stream = File.Create(temporary))
            await JsonSerializer.SerializeAsync(stream, entries, JsonDefaults.Options);
        File.Move(temporary, paths.History, true);
    }
}
