using NAudio.Wave;

namespace Quill.Windows.Audio;

/// <summary>Lists the Windows WaveIn microphones Quill can capture from.</summary>
public sealed record AudioInputDevice(string Id, string DisplayName)
{
    // Belt-and-suspenders: any control that renders the item without DisplayMemberPath (e.g. the
    // text box of an editable ComboBox) falls back to ToString(). Without this override that would
    // surface the record's default "AudioInputDevice { Id = , DisplayName = Default microphone }".
    public override string ToString() => DisplayName;
}

public static class InputDeviceCatalog
{
    public const string SystemDefaultId = "";

    public static IReadOnlyList<AudioInputDevice> Enumerate()
    {
        var devices = new List<AudioInputDevice>
        {
            new(SystemDefaultId, "Default microphone"),
        };
        for (var number = 0; number < WaveIn.DeviceCount; number++)
        {
            try
            {
                var capabilities = WaveIn.GetCapabilities(number);
                var name = string.IsNullOrWhiteSpace(capabilities.ProductName) ? "Microphone" : capabilities.ProductName.Trim();
                devices.Add(new($"wavein:{number}", $"{name}  (input {number + 1})"));
            }
            catch (Exception) { }
        }
        return devices;
    }

    public static int ResolveDeviceNumber(string? deviceId) =>
        // -1 is NAudio's WAVE_MAPPER = the current Windows default recording device, which is what a
        // blank InputDeviceId ("Default microphone") is documented to follow (Models.cs). Device 0 is
        // merely the first enumerated input, which is not the system default when several exist.
        ResolveDeviceNumber(deviceId, WaveIn.DeviceCount, defaultDeviceNumber: -1);

    internal static int ResolveDeviceNumber(string? deviceId, int deviceCount, int defaultDeviceNumber)
    {
        if (string.IsNullOrWhiteSpace(deviceId) || !deviceId.StartsWith("wavein:", StringComparison.OrdinalIgnoreCase))
            return defaultDeviceNumber;
        return int.TryParse(deviceId[7..], out var deviceNumber) && deviceNumber >= 0 && deviceNumber < deviceCount
            ? deviceNumber
            : defaultDeviceNumber;
    }
}
