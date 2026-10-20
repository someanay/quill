using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Shapes;
using System.Windows.Threading;
namespace Quill.Windows;
public sealed class InkMeter : FrameworkElement {
 public double Rms { get; set; } public bool Recording { get; set; } public int Bars { get; set; } = 13;
 private readonly double[] history = new double[20]; private int index;
 public void Update(double rms, bool recording) { Rms = rms; Recording = recording; history[index++ % history.Length] = Math.Min(1, Math.Sqrt(rms / 12000)); InvalidateVisual(); }
 protected override void OnRender(DrawingContext dc) {
  base.OnRender(dc); double width = Math.Min(5, ActualWidth / (Bars * 1.7)); double gap = width * 0.75; double start = (ActualWidth - Bars * (width + gap)) / 2;
  for (int i = 0; i < Bars; i++) { var amplitude = history[(index - Bars + i + history.Length * 10) % history.Length]; var h = Recording ? Math.Max(5, amplitude * ActualHeight * 0.9) : 5; if (!SystemParameters.ClientAreaAnimation) h = Recording ? Math.Min(h, ActualHeight / 3) : 5; dc.DrawRoundedRectangle(Theme.Hex(Recording ? "#234DCE" : "#CBD2CC"), null, new Rect(start + i * (width + gap), (ActualHeight - h) / 2, width,h),3,3); }
 }
}
public sealed class BubbleWindow : Window {
 private readonly AppServices services; private readonly Border circle; private readonly Border halo; private readonly Grid glyph; private readonly TextBlock status; private readonly InkMeter meter; private readonly Border pill; private readonly Button cancel;
 private Point down; private Point windowDown; private bool dragging; private bool pressed; private bool longPressed; private CancellationTokenSource? longPress; private readonly DispatcherTimer focusTimer;
 public BubbleWindow(AppServices services) {
  this.services = services; Width = 258; Height = 110; WindowStyle = WindowStyle.None; ResizeMode = ResizeMode.NoResize; AllowsTransparency = true; Background = Brushes.Transparent; Topmost = true; ShowInTaskbar = false; ShowActivated = false;
  var grid = new Grid(); Content = grid;
  var caption = new StackPanel(); status = new TextBlock { FontSize = 12, TextAlignment = TextAlignment.Center }; status.SetResourceReference(TextBlock.ForegroundProperty, "GoldDeep"); meter = new InkMeter { Height = 25, Width = 60, Bars = 6 }; caption.Children.Add(meter); caption.Children.Add(status);
  pill = new Border { Child = caption, CornerRadius = new(14), Padding = new Thickness(12,8,12,8), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center, Width = 155, BorderThickness = new Thickness(1) }; pill.SetResourceReference(Border.BackgroundProperty, "Surface"); pill.SetResourceReference(Border.BorderBrushProperty, "Line"); grid.Children.Add(pill);
  halo = new Border { Width = 82, Height = 82, CornerRadius = new(100), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center, Margin = new(0,0,8,0), Background = Theme.Hex("#38234DCE") }; grid.Children.Add(halo);
  glyph = new Grid { Width = 28, Height = 30 };
  circle = new Border { Width = 56, Height = 56, CornerRadius = new(100), BorderBrush = Theme.Hex("#EBFFFFFF"), BorderThickness = new Thickness(2.5), Child = glyph, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center, Margin = new(0,0,21,0), Cursor = System.Windows.Input.Cursors.Hand, Effect = new DropShadowEffect { Color = Color.FromRgb(28,27,51), BlurRadius = 24, ShadowDepth = 6, Opacity = .34 } };
  System.Windows.Automation.AutomationProperties.SetName(circle, services.C("bubble.idle", "Start dictation")); grid.Children.Add(circle);
  cancel = new Button { Content = "×", Width = 32, Height = 32, MinHeight = 32, Padding = new(0), FontSize = 23, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Margin = new(130,0,0,0) }; System.Windows.Automation.AutomationProperties.SetName(cancel, services.C("action.cancel", "Cancel")); cancel.Click += async (_,_) => await services.Dictation.Cancel(); grid.Children.Add(cancel);
  circle.MouseLeftButtonDown += Down; circle.MouseMove += Move; circle.MouseLeftButtonUp += Up;
  circle.LostMouseCapture += (_,_) => { pressed = false; longPress?.Cancel(); }; IsVisibleChanged += (_,_) => { if (!IsVisible) { pressed = false; longPress?.Cancel(); } };
  var menu = new ContextMenu(); AddMenu(menu,services.C("windows.tray.open","Open Quill"),services.ShowMain); AddMenu(menu,services.C("windows.bubble.clipboard","Dictate to clipboard"),async () => { await Task.Delay(200); await services.Dictation.DictateToClipboard(); }); AddMenu(menu,services.C("windows.tray.hide","Hide bubble"),services.HideBubble); AddMenu(menu,services.C("windows.bubble.discard","Discard dictation"),async () => await services.Dictation.Cancel()); circle.ContextMenu = menu;
  SourceInitialized += (_, _) => { var hwnd = new WindowInteropHelper(this).Handle; Native.SetWindowLong(hwnd, -20, Native.GetWindowLong(hwnd, -20) | 0x08000000 | 0x00000080); Position(); };
  focusTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(1200) }; focusTimer.Tick += async (_,_) => { if (!services.Settings.Value.PinBubble && services.Dictation.State == DictationState.Idle) circle.Opacity = await services.Injector.CanType() ? 1 : 0.25; else circle.Opacity = 1; }; focusTimer.Start(); Closed += (_,_) => focusTimer.Stop(); Refresh();
 }
 private static void AddMenu(ContextMenu menu, string title, Action action) { var item = new MenuItem { Header = title }; item.Click += (_,_) => action(); menu.Items.Add(item); }
 private void Position() {
  var work = SystemParameters.WorkArea; Left = services.Settings.Value.BubbleX < 0 ? work.Right - Width : services.Settings.Value.BubbleX; Top = services.Settings.Value.BubbleY < 0 ? work.Top + work.Height / 3 : services.Settings.Value.BubbleY; Clamp(false);
 }
 private Point CursorPoint() { var cursor = System.Windows.Forms.Cursor.Position; var dpi = VisualTreeHelper.GetDpi(this); return new(cursor.X / dpi.DpiScaleX, cursor.Y / dpi.DpiScaleY); }
 private async void Down(object sender, MouseButtonEventArgs e) { down = CursorPoint(); windowDown = new(Left,Top); dragging = false; pressed = true; longPressed = false; circle.CaptureMouse(); longPress?.Cancel(); longPress?.Dispose(); longPress = new(); var token = longPress.Token; e.Handled = true; try { await Task.Delay(500,token); if (pressed && !dragging && services.Dictation.State == DictationState.Idle) { longPressed = true; await services.Dictation.DictateToClipboard(); } } catch (OperationCanceledException) { } }
 private void Move(object sender, MouseEventArgs e) { if (!pressed) return; var p = CursorPoint(); var delta = p - down; if (delta.Length > 6) { longPress?.Cancel(); dragging = true; } if (dragging) { Left = windowDown.X + delta.X; Top = windowDown.Y + delta.Y; } }
 private async void Up(object sender, MouseButtonEventArgs e) {
  if (!pressed) return; pressed = false; longPress?.Cancel(); circle.ReleaseMouseCapture();
  if (dragging) { var work = SystemParameters.WorkArea; if (Top + Height > work.Bottom - 28) { services.HideBubble(); services.ScheduleAutoReturn(); } else Clamp(true); }
  else if (!longPressed) await services.Dictation.Toggle(); e.Handled = true;
 }
 private void Clamp(bool save) {
  var cursor = System.Windows.Forms.Cursor.Position; var area = System.Windows.Forms.Screen.FromPoint(cursor).WorkingArea; var dpi = VisualTreeHelper.GetDpi(this); var left = area.Left / dpi.DpiScaleX; var right = area.Right / dpi.DpiScaleX; var top = area.Top / dpi.DpiScaleY; var bottom = area.Bottom / dpi.DpiScaleY;
  Left = Left + Width / 2 < (left + right) / 2 ? left - 158 : right - Width; Top = Math.Clamp(Top, top, Math.Max(top, bottom - Height));
  if (save) { services.Settings.Value.BubbleX = Left; services.Settings.Value.BubbleY = Top; services.Settings.Save(); }
 }
 public void Refresh() {
  var s = services.Dictation.State; bool recording = s == DictationState.Recording; var size = services.Settings.Value.BubbleSize switch { "Small" => 48, "Large" => 68, _ => 56 }; circle.Width = size; circle.Height = size;
  var first = s == DictationState.Error ? "#D6453D" : s == DictationState.Transcribing ? "#3E5579" : "#234DCE"; var second = s == DictationState.Error ? "#E8635B" : s == DictationState.Transcribing ? "#526F9E" : "#244FD8";
  circle.Background = new LinearGradientBrush(Theme.Hex(first).Color, Theme.Hex(second).Color, 45); halo.Visibility = recording ? Visibility.Visible : Visibility.Collapsed;
  if (recording && SystemParameters.ClientAreaAnimation && halo.HasAnimatedProperties == false) halo.BeginAnimation(OpacityProperty, new DoubleAnimation(.25,.8,TimeSpan.FromSeconds(.9)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever }); else if (!recording) halo.BeginAnimation(OpacityProperty, null);
  glyph.Children.Clear();
  if (s is DictationState.Paste or DictationState.Error or DictationState.Transcribing) glyph.Children.Add(new TextBlock { Text = s == DictationState.Paste ? "▣" : s == DictationState.Error ? "!" : "⋯", Foreground = Brushes.White, FontSize = 26, FontWeight = FontWeights.Bold, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center });
  else { glyph.Children.Add(new Rectangle { Width = 10, Height = 17, RadiusX = 5, RadiusY = 5, Fill = Brushes.White, VerticalAlignment = VerticalAlignment.Top, Margin = new(0,1,0,0) }); glyph.Children.Add(new System.Windows.Shapes.Path { Data = Geometry.Parse("M5,13 C5,27 23,27 23,13 M14,24 L14,29 M8,29 L20,29"), Stroke = Brushes.White, StrokeThickness = 2.4 }); }
  status.Text = recording ? TimeSpan.FromMilliseconds(services.Dictation.ElapsedMs).ToString(@"m\:ss") : s == DictationState.Transcribing ? services.C("bubble.transcribing", "Transcribing…") : s == DictationState.Paste ? services.C("windows.bubble.tap_paste", "Click to paste") : services.C("windows.bubble.retry", "Click to retry");
  pill.Visibility = s == DictationState.Idle ? Visibility.Collapsed : Visibility.Visible; meter.Visibility = recording ? Visibility.Visible : Visibility.Collapsed; cancel.Visibility = s == DictationState.Idle ? Visibility.Collapsed : Visibility.Visible; meter.Update(services.Dictation.Rms, recording);
  System.Windows.Automation.AutomationProperties.SetName(circle, recording ? services.C("bubble.recording", "Listening. Click to finish") : status.Text);
 }
}
