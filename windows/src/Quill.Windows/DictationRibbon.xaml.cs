using System.Windows;
using System.Windows.Media;
using Quill.Windows.Core;

namespace Quill.Windows;

public partial class DictationRibbon : Window
{
    private CancellationTokenSource? _hideDelay;

    public DictationRibbon()
    {
        InitializeComponent();
        Loaded += (_, _) => Position();
    }

    public void Update(DictationStatus status)
    {
        Dispatcher.InvokeAsync(async () =>
        {
            _hideDelay?.Cancel();
            RibbonTitle.Text = status.Title;
            RibbonDetail.Text = status.Detail;
            SignalOrb.Background = status.State switch
            {
                DictationState.Listening => (Brush)FindResource("Signal"),
                DictationState.Error => (Brush)FindResource("Error"),
                _ => (Brush)FindResource("Brand"),
            };
            Position();
            if (!IsVisible) Show();
            if (status.State is DictationState.Idle or DictationState.Error)
            {
                _hideDelay = new CancellationTokenSource();
                try { await Task.Delay(TimeSpan.FromSeconds(3), _hideDelay.Token); Hide(); }
                catch (TaskCanceledException) { }
            }
        });
    }

    private void Position()
    {
        var area = SystemParameters.WorkArea;
        Left = area.Right - Width - 26;
        Top = area.Bottom - Height - 24;
    }
}
