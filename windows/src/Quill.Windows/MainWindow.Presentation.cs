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
  var words = Text(C("desktop.demo.spoken","Let’s move our meeting to Thursday. I’ll send the notes before then."),21); words.Margin = new(0); words.LineHeight = 32;
  var surface = new Border { Child = words,Padding = new(24),CornerRadius = new(7),BorderThickness = new(1),MinHeight = 180 };
  surface.SetResourceReference(Border.BackgroundProperty,"Surface"); surface.SetResourceReference(Border.BorderBrushProperty,"Line"); panel.Children.Add(surface);
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
  var actions = new WrapPanel { Margin = new(0,36,0,28) };
  var start = Action(C("onboarding.welcome.cta","Set up Quill"),() => ShowStep(1),true); start.MinWidth = 156; start.MinHeight = 46; start.Padding = new(24,10,24,10); start.Margin = new(0,0,16,8); actions.Children.Add(start);
  var open = Action(C("onboarding.welcome.open","Open workspace"),() => { S.OnboardingComplete = true; app.Settings.Save(); Navigate("home"); }); open.MinHeight = 46; open.Background = Brushes.Transparent; open.Margin = new(0,0,0,8); actions.Children.Add(open); content.Children.Add(actions);
  content.Children.Add(Text(C("desktop.setup.privacy_line","Your provider key. A direct connection. No Quill account."),13,true));
  SizeChangedEventHandler responsive = (_,_) => { title.FontSize = ActualWidth < 900 ? 34 : 64; title.LineHeight = ActualWidth < 900 ? 44 : 76; host.MinHeight = Math.Max(0,ActualHeight - 130); };
  SizeChanged += responsive; pageLifetimes.Add(new PageLifetime(() => SizeChanged -= responsive));
  title.FontSize = Width < 900 ? 34 : 64; title.LineHeight = Width < 900 ? 44 : 76;
  host.MinHeight = Math.Max(0,Height - 130); page.Children.Add(host);
 }
}
