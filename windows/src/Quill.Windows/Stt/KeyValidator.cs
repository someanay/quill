using Quill.Windows.Core;

namespace Quill.Windows.Stt;

/// <summary>Result of a real, provider-scoped BYOK validation request.</summary>
public abstract record KeyValidation
{
    public sealed record Valid : KeyValidation;
    public sealed record Rejected : KeyValidation;
    public sealed record Unverified(string Reason) : KeyValidation;
}

/// <summary>
/// Checks a key using a 400 ms silent WAV sent only to the selected provider. This proves the
/// same request shape Quill uses for dictation without ever sending speech as part of validation.
/// </summary>
public sealed class KeyValidator
{
    private readonly Func<SttProvider, string, string, ITranscriptionClient> _clientFactory;

    public KeyValidator(HttpClient http)
        : this((provider, key, customBaseUrl) => TranscriptionClientFactory.Create(provider, key, customBaseUrl, http)) { }

    public KeyValidator(Func<SttProvider, string, string, ITranscriptionClient> clientFactory) =>
        _clientFactory = clientFactory;

    public async Task<KeyValidation> ValidateAsync(
        SttProvider provider, string key, string model, string customBaseUrl, CancellationToken cancellation = default)
    {
        if (string.IsNullOrWhiteSpace(key)) return new KeyValidation.Unverified("no key entered");
        if (string.IsNullOrWhiteSpace(model)) return new KeyValidation.Unverified("no model selected");
        if (provider == SttProvider.Custom && !IsHttpsUrl(customBaseUrl))
            return new KeyValidation.Unverified("custom endpoint not configured");

        var wav = CreateSilentWav();
        try
        {
            await _clientFactory(provider, key.Trim(), customBaseUrl.Trim())
                .TranscribeAsync(wav, new TranscriptionOptions(model.Trim(), null), cancellation);
            return new KeyValidation.Valid();
        }
        catch (TranscriptionException error) when (error.Message.Contains("API key was rejected", StringComparison.OrdinalIgnoreCase))
        {
            return new KeyValidation.Rejected();
        }
        catch (OperationCanceledException) when (!cancellation.IsCancellationRequested)
        {
            return new KeyValidation.Unverified("timed out");
        }
        catch (Exception error)
        {
            return new KeyValidation.Unverified(ConciseReason(error));
        }
        finally
        {
            try { File.Delete(wav); } catch (IOException) { }
        }
    }

    /// <summary>Returns false only for a confirmed quota/credit denial; null remains honest about inconclusive results.</summary>
    public async Task<bool?> HasPaidAccessAsync(
        SttProvider provider, string key, string model, string customBaseUrl, CancellationToken cancellation = default)
    {
        var wav = CreateSilentWav();
        try
        {
            await _clientFactory(provider, key.Trim(), customBaseUrl.Trim())
                .TranscribeAsync(wav, new TranscriptionOptions(model.Trim(), null), cancellation);
            return true;
        }
        catch (TranscriptionException error) when (
            error.Message.Contains("quota", StringComparison.OrdinalIgnoreCase) ||
            error.Message.Contains("no available credit", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }
        catch { return null; }
        finally
        {
            try { File.Delete(wav); } catch (IOException) { }
        }
    }

    private static bool IsHttpsUrl(string value) => Uri.TryCreate(value, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps;

    private static string CreateSilentWav()
    {
        const int sampleRate = 16_000;
        const int durationMilliseconds = 400;
        var dataBytes = sampleRate * sizeof(short) * durationMilliseconds / 1_000;
        var path = Path.Combine(Path.GetTempPath(), $"quill-keytest-{Guid.NewGuid():N}.wav");
        using var stream = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None);
        using var writer = new BinaryWriter(stream);
        writer.Write("RIFF"u8.ToArray());
        writer.Write(36 + dataBytes);
        writer.Write("WAVEfmt "u8.ToArray());
        writer.Write(16);
        writer.Write((short)1);
        writer.Write((short)1);
        writer.Write(sampleRate);
        writer.Write(sampleRate * sizeof(short));
        writer.Write((short)sizeof(short));
        writer.Write((short)16);
        writer.Write("data"u8.ToArray());
        writer.Write(dataBytes);
        writer.Write(new byte[dataBytes]);
        return path;
    }

    private static string ConciseReason(Exception error) => error switch
    {
        HttpRequestException => "no network connection",
        TranscriptionException transcription => transcription.Message,
        _ => "could not verify right now",
    };
}
