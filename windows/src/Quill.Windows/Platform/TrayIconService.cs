using System.Drawing;
using Forms = System.Windows.Forms;

namespace Quill.Windows.Platform;

public sealed class TrayIconService : IDisposable
{
    private readonly Forms.NotifyIcon _icon;
    private readonly Forms.ContextMenuStrip _menu;

    public TrayIconService(Action show, Func<Task> toggle, Action exit)
    {
        _menu = new Forms.ContextMenuStrip();
        _menu.Items.Add("Open Quill", null, (_, _) => show());
        _menu.Items.Add("Start / stop dictation", null, async (_, _) => await toggle());
        _menu.Items.Add(new Forms.ToolStripSeparator());
        _menu.Items.Add("Exit", null, (_, _) => exit());
        _icon = new Forms.NotifyIcon
        {
            Icon = BrandIconFactory.CreateIcon(),
            Text = "Quill — ready",
            Visible = true,
            ContextMenuStrip = _menu,
        };
        _icon.DoubleClick += (_, _) => show();
    }

    public void SetStatus(string value) => _icon.Text = value.Length <= 63 ? value : value[..63];

    public void Dispose()
    {
        // NotifyIcon.Dispose() releases neither the assigned Icon (a real HICON from
        // BrandIconFactory) nor the ContextMenuStrip — dispose both explicitly to avoid leaking a
        // GDI handle and the menu's tool-strip items.
        var icon = _icon.Icon;
        _icon.Visible = false;
        _icon.Dispose();
        icon?.Dispose();
        _menu.Dispose();
    }
}
