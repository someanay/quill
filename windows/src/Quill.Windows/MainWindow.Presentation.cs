using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace Quill.Windows;

public sealed partial class MainWindow {
 private readonly List<Action> shortcutPresentations = [];
 private FrameworkElement ShortcutKeys(double size = 18) {
  var keys = new WrapPanel { Margin = new(0,6,0,8) };
  System.Windows.Automation.AutomationProperties.SetAutomationId(keys,"quill-shortcut-keys");
  void Render() {
  keys.Children.Clear();
  var labels = app.HotkeyLabel.Split('+',StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
  for (var i = 0; i < labels.Length; i++) {
   if (i > 0) { var plus = Text("+",size,true); plus.Margin = new(10,12,10,0); keys.Children.Add(plus); }
   var text = Text(labels[i],size); text.Margin = new(0); text.FontWeight = FontWeights.Medium;
   var key = new Border { Child = text,Padding = new(18,12,18,12),CornerRadius = new(7),BorderThickness = new(1),MinWidth = 58,HorizontalAlignment = HorizontalAlignment.Left,Margin = new(0,0,0,4) };
   key.SetResourceReference(Border.BackgroundProperty,"Surface"); key.SetResourceReference(Border.BorderBrushProperty,"Line");
   keys.Children.Add(key);
  }
  }
  Render(); shortcutPresentations.Add(Render); pageLifetimes.Add(new PageLifetime(() => shortcutPresentations.Remove(Render)));
  return keys;
 }
 private FrameworkElement DictationExample() {
  var panel = new StackPanel { Margin = new(0,10,0,0) };
  var label = Text(C("desktop.demo.label","Example dictation"),13,true); label.Margin = new(0,0,0,12); panel.Children.Add(label);
  panel.Children.Add(new MessengerDemo(key => C(key,key),() => app.HotkeyLabel));
  return panel;
 }
 private void BuildWelcomePresentation() {
  var content = new StackPanel();
  var host = new Grid(); host.Children.Add(content); content.VerticalAlignment = VerticalAlignment.Center;
  var title = Text(C("desktop.setup.welcome_title","Your voice, wherever you write.").Replace(", ",",\n"),64); title.FontFamily = Theme.Serif; title.FontWeight = FontWeights.SemiBold; title.LineHeight = 76; title.Margin = new(0,0,0,32); title.MaxWidth = 900; title.HorizontalAlignment = HorizontalAlignment.Left; content.Children.Add(title);
  var introduction = new StackPanel();
  var body = Text(C("desktop.setup.welcome_body","Click a text field. Press your shortcut. Speak, then press again to insert your words."),20,true); body.LineHeight = 30; body.MaxWidth = 490; body.HorizontalAlignment = HorizontalAlignment.Left; body.Margin = new(0,0,0,28); introduction.Children.Add(body);
  introduction.Children.Add(ShortcutKeys(22));
  var hint = Text(C("desktop.setup.shortcut_caption","Start and stop dictation"),14,true); hint.Margin = new(0,12,0,24); introduction.Children.Add(hint);
  var columns = new DesktopColumns { Gap = 36,MinColumnWidth = 360 }; columns.Children.Add(introduction); columns.Children.Add(DictationExample()); content.Children.Add(columns);
  var actions = new WrapPanel { Margin = new(0,20,0,20) };
  var start = Action(C("onboarding.welcome.cta","Set up Quill"),() => ShowStep(1),true); start.MinWidth = 156; start.MinHeight = 46; start.Padding = new(24,10,24,10); start.Margin = new(0,0,16,8); actions.Children.Add(start);
  var open = Action(C("onboarding.welcome.open","Open workspace"),() => { S.OnboardingComplete = true; app.Settings.Save(); Navigate("home"); }); open.MinHeight = 46; open.Background = Brushes.Transparent; open.Margin = new(0,0,0,8); actions.Children.Add(open); introduction.Children.Add(actions);
  introduction.Children.Add(Text(C("desktop.setup.privacy_line","Your provider key. A direct connection. No Quill account."),13,true));
  void ApplyResponsive(double width,double height) {
   var compact = height < 640;
   title.FontSize = width < 900 || compact ? 34 : 64; title.LineHeight = width < 900 || compact ? 44 : 76; title.Margin = new(0,0,0,compact ? 24 : 32);
   body.FontSize = compact ? 16 : 20; body.LineHeight = compact ? 24 : 30; body.Margin = new(0,0,0,compact ? 20 : 28);
   hint.Margin = new(0,compact ? 8 : 12,0,compact ? 12 : 24); actions.Margin = new(0,compact ? 12 : 20,0,compact ? 12 : 20);
   host.MinHeight = Math.Max(0,height - 130);
  }
  SizeChangedEventHandler responsive = (_,_) => ApplyResponsive(ActualWidth,ActualHeight);
  SizeChanged += responsive; pageLifetimes.Add(new PageLifetime(() => SizeChanged -= responsive));
  ApplyResponsive(Width,Height); page.Children.Add(host);
 }
}
