using System.Windows;
using System.Windows.Media;
using Quill.Windows.Core;

namespace Quill.Windows.Platform;

public static class ThemeService
{
    private static readonly IReadOnlyDictionary<string, (string Dark, string Light)> Colors =
        new Dictionary<string, (string, string)>
        {
            ["Board"] = ("#090B15", "#E6E8EF"),
            ["Paper"] = ("#101321", "#EFF1F6"),
            ["Surface"] = ("#181C2D", "#FFFFFF"),
            ["SurfaceRaised"] = ("#20253A", "#FFFFFF"),
            ["Sunken"] = ("#111523", "#F6F7FB"),
            ["Line"] = ("#292F48", "#E3E5EE"),
            ["LineStrong"] = ("#3A4261", "#D3D6E2"),
            ["Ink"] = ("#F5F3FB", "#1C1B33"),
            ["Ink2"] = ("#D6D6E2", "#4A4A5C"),
            ["Muted"] = ("#A5A8BC", "#6E6E81"),
            ["Faint"] = ("#747A94", "#9A9AAC"),
            ["Brand"] = ("#8175FF", "#4A3FD0"),
            ["BrandHover"] = ("#968DFF", "#5B50DD"),
            ["BrandInk"] = ("#B8B1FF", "#3A2EA3"),
            ["BrandSoft"] = ("#29264B", "#ECEAFB"),
            ["Signal"] = ("#F0B44B", "#E0A03A"),
            ["SignalInk"] = ("#FFD68B", "#8A5A12"),
            ["SignalSoft"] = ("#362A18", "#FBEFD6"),
            ["Okay"] = ("#55C58B", "#2E9E6B"),
            ["OkaySoft"] = ("#173326", "#E1F3EA"),
            ["Error"] = ("#FF7068", "#D6453D"),
            ["ErrorSoft"] = ("#3B2025", "#FBE7E5"),
        };

    public static void Apply(AppTheme theme)
    {
        foreach (var (key, pair) in Colors)
        {
            var color = (Color)System.Windows.Media.ColorConverter.ConvertFromString(theme == AppTheme.Dark ? pair.Dark : pair.Light);
            if (Application.Current.Resources[key] is SolidColorBrush brush && !brush.IsFrozen)
                brush.Color = color;
            else
                Application.Current.Resources[key] = new SolidColorBrush(color);
        }
    }
}
