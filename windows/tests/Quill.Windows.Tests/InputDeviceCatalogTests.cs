using Quill.Windows.Audio;

namespace Quill.Windows.Tests;

public sealed class InputDeviceCatalogTests
{
    [Theory]
    [InlineData(null, 3, -1, -1)]
    [InlineData("", 3, -1, -1)]
    [InlineData("wavein:1", 3, -1, 1)]
    [InlineData("wavein:3", 3, -1, -1)]
    [InlineData("unavailable", 3, -1, -1)]
    public void ResolveDeviceNumberFallsBackSafely(string? deviceId, int deviceCount, int defaultDeviceNumber, int expected)
    {
        Assert.Equal(expected, InputDeviceCatalog.ResolveDeviceNumber(deviceId, deviceCount, defaultDeviceNumber));
    }
}
