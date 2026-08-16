using System.Drawing;
using Forms = System.Windows.Forms;

namespace Quill.Windows.Platform;

public sealed class TrayIconService : IDisposable
{
    private readonly Forms.NotifyIcon _icon;

    public TrayIconService(Action show, Func<Task> toggle, Action exit)
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Open Quill", null, (_, _) => show());
        menu.Items.Add("Start / stop dictation", null, async (_, _) => await toggle());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("Exit", null, (_, _) => exit());
        _icon = new Forms.NotifyIcon
        {
            Icon = BrandIconFactory.CreateIcon(),
            Text = "Quill — ready",
            Visible = true,
            ContextMenuStrip = menu,
        };
        _icon.DoubleClick += (_, _) => show();
    }

    public void SetStatus(string value) => _icon.Text = value.Length <= 63 ? value : value[..63];
    public void Dispose() { _icon.Visible = false; _icon.Dispose(); }
}
