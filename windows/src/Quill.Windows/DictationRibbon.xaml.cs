using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Quill.Windows.Core;

namespace Quill.Windows;

public partial class DictationRibbon : Window
{
    private CancellationTokenSource? _hideDelay;
    private CancellationTokenSource? _toastDelay;

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
            ClipboardBadge.Visibility = status.Clipboard ? Visibility.Visible : Visibility.Collapsed;
            SignalOrb.Background = status.State switch
            {
                DictationState.Listening => (Brush)FindResource("Signal"),
                DictationState.Error => (Brush)FindResource("Error"),
                _ => (Brush)FindResource("Brand"),
            };
            Animate(status.State);
            if (!string.IsNullOrEmpty(status.Toast)) ShowToast(status.Toast);
            Reposition();
            if (!IsVisible) Show();
            if (status.State is DictationState.Idle or DictationState.Error)
            {
                _hideDelay = new CancellationTokenSource();
                var linger = TimeSpan.FromSeconds(string.IsNullOrEmpty(status.Toast) ? 3 : 4.5);
                try { await Task.Delay(linger, _hideDelay.Token); Hide(); }
                catch (TaskCanceledException) { }
            }
        });
    }

    private async void ShowToast(string message)
    {
        _toastDelay?.Cancel();
        _toastDelay = new CancellationTokenSource();
        ToastText.Text = message;
        ToastPill.Visibility = Visibility.Visible;
        Reposition();
        try { await Task.Delay(TimeSpan.FromSeconds(4), _toastDelay.Token); }
        catch (TaskCanceledException) { return; }
        ToastPill.Visibility = Visibility.Collapsed;
        Reposition();
    }

    private void Reposition()
    {
        UpdateLayout();
        Position();
    }

    private void Animate(DictationState state)
    {
        var active = state is DictationState.Listening or DictationState.Transcribing or DictationState.Cleaning;
        var pulse = active ? new DoubleAnimation(1, 1.13, TimeSpan.FromMilliseconds(620)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever } : null;
        OrbScale.BeginAnimation(ScaleTransform.ScaleXProperty, pulse);
        OrbScale.BeginAnimation(ScaleTransform.ScaleYProperty, pulse);
        var meters = new[] { MeterOne, MeterTwo, MeterThree, MeterFour, MeterFive };
        for (var i = 0; i < meters.Length; i++)
        {
            var transform = (ScaleTransform)meters[i].RenderTransform;
            var wave = active ? new DoubleAnimation(.35, 1.15, TimeSpan.FromMilliseconds(430 + i * 70)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever } : null;
            transform.BeginAnimation(ScaleTransform.ScaleYProperty, wave);
        }
    }

    private void Position()
    {
        var area = SystemParameters.WorkArea;
        Left = area.Right - Width - 26;
        // SizeToContent leaves Height as NaN; ActualHeight tracks the toast expanding and collapsing.
        Top = area.Bottom - ActualHeight - 24;
    }
}
