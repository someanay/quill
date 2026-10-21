using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using KeyEventArgs = System.Windows.Input.KeyEventArgs;
namespace Quill.Windows;
public static class Motion {
 public static string Preference { get; set; } = "Match system";
 public static bool Enabled => Preference == "Full" || Preference != "Reduced" && SystemParameters.ClientAreaAnimation;
 private static void Feedback(object sender,bool pressed) {
  if (sender is not Button button || !button.IsEnabled) return;
  if (button.RenderTransform is not ScaleTransform transform) { if (!Enabled) return; transform = new ScaleTransform(1,1); button.RenderTransform = transform; button.RenderTransformOrigin = new(.5,.5); }
  var animation = new DoubleAnimation(pressed && Enabled ? .98 : 1,TimeSpan.FromMilliseconds(Enabled ? 90 : 0)) { EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut } };
  transform.BeginAnimation(ScaleTransform.ScaleXProperty,animation); transform.BeginAnimation(ScaleTransform.ScaleYProperty,animation);
 }
 public static void Press(object sender,MouseButtonEventArgs e) => Feedback(sender,true);
 public static void Release(object sender,MouseButtonEventArgs e) => Feedback(sender,false);
 public static void ReleaseCapture(object sender,MouseEventArgs e) => Feedback(sender,false);
 public static void KeyPress(object sender,KeyEventArgs e) { if (e.Key is Key.Space or Key.Enter) Feedback(sender,true); }
 public static void KeyRelease(object sender,KeyEventArgs e) { if (e.Key is Key.Space or Key.Enter) Feedback(sender,false); }
 public static void Reveal(UIElement element) {
  element.BeginAnimation(UIElement.OpacityProperty,null);
  if (Enabled) element.BeginAnimation(UIElement.OpacityProperty,new DoubleAnimation(0,1,TimeSpan.FromMilliseconds(140)) { FillBehavior = FillBehavior.Stop });
 }
}
