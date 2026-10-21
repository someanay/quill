using System.Diagnostics;
using System.Globalization;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using Pen = System.Windows.Media.Pen;

namespace Quill.Windows;

/// A local illustration: no keyboard events, microphone capture, or network requests.
public sealed class MessengerDemo : FrameworkElement {
 private readonly Func<string,string> copy;
 private readonly Func<string> shortcut;
 private readonly Stopwatch clock = new();
 private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromMilliseconds(33) };
 private Window? host;
 public double? PreviewTime { get; set; }
 public MessengerDemo(Func<string,string> copy,Func<string> shortcut) {
  this.copy = copy; this.shortcut = shortcut; Height = 320; MinWidth = 220; ClipToBounds = true; IsHitTestVisible = false;
  System.Windows.Automation.AutomationProperties.SetName(this,copy("demo.accessibility"));
  timer.Tick += (_,_) => InvalidateVisual();
  Loaded += (_,_) => { host = Window.GetWindow(this); if (host != null) host.StateChanged += HostStateChanged; RefreshClock(); };
  IsVisibleChanged += (_,_) => RefreshClock();
  Unloaded += (_,_) => { if (host != null) host.StateChanged -= HostStateChanged; host = null; timer.Stop(); clock.Stop(); };
 }
 private void HostStateChanged(object? sender,EventArgs e) => RefreshClock();
 private void RefreshClock() {
  if (IsLoaded && IsVisible && host?.WindowState != WindowState.Minimized && Motion.Enabled) { clock.Start(); timer.Start(); }
  else { timer.Stop(); clock.Stop(); }
  InvalidateVisual();
 }
 protected override void OnRender(DrawingContext dc) {
  base.OnRender(dc); if (ActualWidth <= 0) return;
  var scale = Math.Min(ActualWidth / 380,ActualHeight / 320);
  dc.PushTransform(new TranslateTransform((ActualWidth - 380 * scale) / 2,0)); dc.PushTransform(new ScaleTransform(scale,scale));
  Brush B(string name) => (Brush)FindResource(name);
  void Box(double x,double y,double w,double h,Brush fill,Brush? line = null,double radius = 8) => dc.DrawRoundedRectangle(fill,line == null ? null : new Pen(line,1),new Rect(x,y,w,h),radius,radius);
  FormattedText Words(string value,double x,double y,double size,Brush ink,double width = 340) {
   var text = new FormattedText(value,CultureInfo.CurrentUICulture,System.Windows.FlowDirection.LeftToRight,new Typeface(Theme.Sans,FontStyles.Normal,FontWeights.Normal,FontStretches.Normal),size,ink,VisualTreeHelper.GetDpi(this).PixelsPerDip) { MaxTextWidth = width,LineHeight = size * 1.38 };
   dc.DrawText(text,new Point(x,y)); return text;
  }
  var time = PreviewTime ?? (Motion.Enabled ? clock.Elapsed.TotalSeconds % 12 : 9.5);
  var listening = time >= 1.6 && time < 5.5; var working = time >= 5.5 && time < 7.2; var filled = time >= 7.2;
  Box(0.5,0.5,379,319,B("Surface"),B("Line"),10);
  for (var i = 0;i < 3;i++) dc.DrawEllipse(B("Line"),null,new Point(20 + i * 12,20),3,3);
  Words(copy("demo.conversation"),202,11,12,B("Muted"),160);
  dc.DrawLine(new Pen(B("Line"),1),new Point(1,40),new Point(379,40));
  Box(18,58,62,34,B("Sunken")); Words(copy("demo.hello"),29,65,14,B("Ink"));
  Box(18,102,250,40,B("Sunken")); Words(copy("demo.question"),29,112,14,B("Ink"),230);
  Box(18,166,344,88,B("Paper"),B("Line"));
  Rect? finalGlyph = null;
  if (filled) { var reply = copy("demo.reply"); var formatted = Words(reply,30,178,15,B("Ink"),316); if (reply.Length > 0) finalGlyph = formatted.BuildHighlightGeometry(new Point(30,178),reply.Length - 1,1).Bounds; }
  // Keep both the editing caret and pointer visible throughout the loop.
  var caretX = finalGlyph?.Right + 1 ?? 30; var caretY = finalGlyph?.Top + 1 ?? 182;
  dc.DrawLine(new Pen(B("BrandInk"),1.6),new Point(caretX,caretY),new Point(caretX,caretY + 18));
  var pointer = Geometry.Parse("M 0,0 L 0,18 L 4,14 L 8,22 L 11,20 L 7,12 L 14,12 Z");
  dc.PushTransform(new TranslateTransform(326,230)); dc.DrawGeometry(B("Ink"),new Pen(B("Surface"),1.4),pointer); dc.Pop();
  if (listening || working) {
   dc.DrawEllipse(B("Brand"),new Pen(Brushes.White,2),new Point(330,143),23,23);
   if (listening) for (var i = 0;i < 5;i++) { var height = 5 + Math.Abs(Math.Sin(time * 5 + i * 1.4)) * 18; dc.DrawLine(new Pen(Brushes.White,2.5) { StartLineCap = PenLineCap.Round,EndLineCap = PenLineCap.Round },new Point(318 + i * 6,143 - height / 2),new Point(318 + i * 6,143 + height / 2)); }
   else for (var i = 0;i < 3;i++) dc.DrawEllipse(Brushes.White,null,new Point(322 + i * 8,143),2,2);
  }
  var pressed = time >= 1.1 && time < 1.7 || time >= 5.1 && time < 5.6;
  Box(18,275,181,29,pressed ? B("BrandSoft") : B("Paper"),pressed ? B("BrandInk") : B("Line"),5);
  Words(shortcut(),28,280,12,pressed ? B("BrandInk") : B("Muted"),166);
  Words(copy(filled ? "demo.inserted" : working ? "demo.transcribing" : listening ? "demo.speak" : "demo.shortcut"),213,280,12,B("BrandInk"),150);
  dc.Pop(); dc.Pop();
 }
}
