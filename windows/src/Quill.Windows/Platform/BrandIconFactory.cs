using Drawing = System.Drawing;
using D2D = System.Drawing.Drawing2D;
using DrawingImaging = System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Windows.Interop;
using WpfImageSource = System.Windows.Media.ImageSource;
using System.Windows.Media.Imaging;

namespace Quill.Windows.Platform;

/// <summary>
/// Creates the Quill mark for WPF chrome and the notification tray. The mark is the same
/// feather as the Android launcher icon (<c>res/drawable/ic_launcher_foreground.xml</c>):
/// a clean minimalist feather, white on the brand ink-indigo gradient.
/// </summary>
public static class BrandIconFactory
{
    public static Drawing.Icon CreateIcon()
    {
        using var bitmap = new Drawing.Bitmap(32, 32, DrawingImaging.PixelFormat.Format32bppArgb);
        using var graphics = Drawing.Graphics.FromImage(bitmap);
        graphics.SmoothingMode = D2D.SmoothingMode.AntiAlias;
        graphics.Clear(Drawing.Color.Transparent);

        // Brand ink-indigo gradient (design_system.md §2.4) — matches the Android background.
        using var background = new D2D.LinearGradientBrush(
            new Drawing.Rectangle(1, 1, 30, 30),
            Drawing.Color.FromArgb(74, 63, 208),   // #4A3FD0
            Drawing.Color.FromArgb(106, 91, 234),  // #6A5BEA
            45f);
        graphics.FillRoundedRectangle(background, new Drawing.Rectangle(1, 1, 30, 30), 8);

        // Feather mark — white, round caps/joins (same style as the Android vector stroke).
        using var pen = new Drawing.Pen(Drawing.Color.White, 1.6f)
        {
            StartCap = D2D.LineCap.Round,
            EndCap = D2D.LineCap.Round,
            LineJoin = D2D.LineJoin.Round,
        };
        using var path = BuildFeatherPath();
        graphics.DrawPath(pen, path);

        var handle = bitmap.GetHicon();
        try
        {
            using var source = Drawing.Icon.FromHandle(handle);
            return (Drawing.Icon)source.Clone();
        }
        finally { DestroyIcon(handle); }
    }

    public static WpfImageSource CreateImageSource()
    {
        using var icon = CreateIcon();
        var source = Imaging.CreateBitmapSourceFromHIcon(icon.Handle, System.Windows.Int32Rect.Empty,
            BitmapSizeOptions.FromEmptyOptions());
        source.Freeze();
        return source;
    }

    /// <summary>
    /// The Quill feather, drawn in a 24×24 space then scaled into the 32×32 icon.
    /// Geometry matches the Android vector: vane <c>M20.24 12.24 a6 6 0 0 0 -8.49 -8.49 L5 10.5
    /// V19 h8.5 z</c> · spine <c>M16 8 L2 22</c> · barb <c>M17.5 15 H9</c>.
    /// </summary>
    private static D2D.GraphicsPath BuildFeatherPath()
    {
        const float scale = 0.85f;
        const float cx = 12f, cy = 12f;
        static float X(float x) => 16f + scale * (x - cx);
        static float Y(float y) => 16f + scale * (y - cy);

        var path = new D2D.GraphicsPath();

        // Vane body (closed figure).
        path.StartFigure();
        // Semicircular arc centred (16,8), r=6: from 45° CCW by −180° to (11.75,3.75).
        float r = 6f * scale;
        path.AddArc(X(16f) - r, Y(8f) - r, r * 2f, r * 2f, 45f, -180f);
        path.AddLine(X(11.75f), Y(3.75f), X(5f), Y(10.5f));
        path.AddLine(X(5f), Y(10.5f), X(5f), Y(19f));
        path.AddLine(X(5f), Y(19f), X(13.5f), Y(19f));
        path.CloseFigure();

        // Spine.
        path.StartFigure();
        path.AddLine(X(16f), Y(8f), X(2f), Y(22f));

        // Barb.
        path.StartFigure();
        path.AddLine(X(17.5f), Y(15f), X(9f), Y(15f));

        return path;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyIcon(IntPtr handle);
}

internal static class GraphicsExtensions
{
    public static void FillRoundedRectangle(this Drawing.Graphics graphics, Drawing.Brush brush, Drawing.Rectangle bounds, int radius)
    {
        using var path = new D2D.GraphicsPath();
        var diameter = radius * 2;
        path.AddArc(bounds.X, bounds.Y, diameter, diameter, 180, 90);
        path.AddArc(bounds.Right - diameter, bounds.Y, diameter, diameter, 270, 90);
        path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
        path.AddArc(bounds.X, bounds.Bottom - diameter, diameter, diameter, 90, 90);
        path.CloseFigure();
        graphics.FillPath(brush, path);
    }
}
