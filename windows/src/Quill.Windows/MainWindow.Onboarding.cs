using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace Quill.Windows;

public sealed partial class MainWindow {
 private static readonly string[] SetupLabels = ["welcome", "privacy", "provider", "audio", "access", "shortcut", "ready"];

 private void ShowStep(int value) {
  step = Math.Clamp(value,0,6); Clear(); page.MaxWidth = double.PositiveInfinity;
  scroll.VerticalContentAlignment = step is 0 or 6 ? VerticalAlignment.Center : VerticalAlignment.Top;
  SetDesktopNavigationVisible(false); BuildSetupNavigation(); BuildSetupCompanion();
  if (step > 0) page.Children.Add(Action(C("action.back","Back"),() => ShowStep(step - 1)));
  if (step > 0) {
   var progress = Text(C("desktop.setup.progress","Step %1$d of %2$d",step + 1,7),12,true);
   progress.Margin = new(0,12,0,18); page.Children.Add(progress);
  }
  switch (step) {
   case 0:
    Welcome(); break;
   case 1:
    Heading(C("compliance.key.required_title","Bring your own API key"),C("desktop.setup.privacy_body","Quill connects directly to your speech provider. Choose who processes your recordings before you start."));
    var privacy = Card();
    privacy.Children.Add(Text(C("onboarding.key.warning.free_tier","Some providers offer a free tier.")));
    privacy.Children.Add(Text(C("onboarding.key.warning.usage","Your provider may charge for usage.")));
    privacy.Children.Add(Text(C("compliance.privacy.promise","Audio and optional cleanup go directly to your chosen provider.")));
    var accept = Action(C("action.continue","Continue"),() => ShowStep(2),true); page.Children.Add(accept);
    var later = Action(C("action.not_now","Not now"),() => ShowStep(0)); page.Children.Add(later);
    break;
   case 2:
    Heading(C("desktop.setup.provider_title","Connect your speech provider"),C("desktop.setup.provider_body","Add and verify a key. You can change providers, models and cleanup later in Settings."));
    GuidedKeys(page,true,false,() => ShowStep(3)); break;
   case 3:
    Heading(C("desktop.setup.audio_title","Check your microphone"),C("windows.onboarding.mic_help","Start the mic test and speak. Test audio stays on this PC and is discarded."));
    var audio = Card();
    var inputs = AudioRecorder.Devices();
    Choice(audio,C("windows.audio.device","Microphone"),inputs.Select(i => i.Name),inputs.FirstOrDefault(i => i.Id == S.InputDevice)?.Name ?? inputs[0].Name,name => { var input = inputs.First(i => i.Name == name); S.InputDevice = input.Id; S.InputDeviceName = input.Id == -1 ? "" : name; app.Settings.Save(); });
    var meter = new InkMeter { Height = 100 }; audio.Children.Add(meter);
    var next = Action(C("action.continue","Continue"),() => ShowStep(4),true); next.IsEnabled = micPassed;
    audio.Children.Add(MicrophoneTestButton(meter,() => { micPassed = true; next.IsEnabled = true; }));
    audio.Children.Add(Action(C("onboarding.mic.open_settings","Open microphone settings"),() => Open("ms-settings:privacy-microphone")));
    audio.Children.Add(Text(C("windows.onboarding.mic_settings","If capture is blocked, allow desktop microphone access in Windows Settings, then return."),13,true));
    page.Children.Add(next); break;
   case 4:
    Heading(C("desktop.setup.access_title","Let your words reach your apps"),C("desktop.setup.access_body","Quill can insert your finished dictation into the text field you selected. Review and allow text insertion below."));
    var consent = Card(); consent.Children.Add(Text(C("windows.onboarding.disclosure","Quill identifies the focused editable field and inserts your dictation. Password and sensitive fields are excluded. Unsupported apps may require manual paste.")));
    var agree = Action(C("windows.onboarding.allow_insert","Allow text insertion & continue"),() => { S.InjectionConsent = true; app.Settings.Save(); ShowStep(5); },true); agree.IsEnabled = S.InjectionConsent;
    Toggle(consent,C("windows.onboarding.consent","I allow Quill to insert my dictations into supported apps"),S.InjectionConsent,on => agree.IsEnabled = on);
    page.Children.Add(agree); break;
   case 5:
    Heading(C("desktop.setup.shortcut_title","One shortcut, wherever you write"),C("desktop.setup.shortcut_body","Focus a text field. Press your shortcut to start, speak, then press it again to finish."));
    var shortcut = Card(); shortcut.Children.Add(Text(app.HotkeyLabel,30));
    shortcut.Children.Add(Text(C("desktop.setup.controls","The floating control and tray menu are available too. Closing this window keeps Quill in the tray."),14,true));
    Toggle(shortcut,C("desktop.setup.bubble_choice","Show the floating recording control"),S.BubbleActive,on => { S.BubbleActive = on; S.BubbleVisible = on; app.Settings.Save(); });
    if (app.HotkeyChecked && !app.HotkeyAvailable) shortcut.Children.Add(Text(C("windows.home.hotkey_conflict","This shortcut is in use. Use the floating control or choose another shortcut in Settings."),14,true));
    page.Children.Add(Action(C("action.continue","Continue"),() => ShowStep(6),true)); break;
   case 6:
    Heading(C("desktop.setup.ready_title","Make room for your next thought."),C("desktop.setup.ready_body","Your workspace has a practice field. Try a short dictation there, then use the same shortcut in your everyday apps."));
    page.Children.Add(Action(C("desktop.setup.practice","Open the practice workspace"),() => FinishDesktopSetup(true),true));
    page.Children.Add(Action(C("desktop.setup.finish","Go to Home"),() => FinishDesktopSetup(false))); break;
  }
  if (step == 0 && app.Isolated) Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.ContextIdle,new Action(SaveVisualDiagnostics));
 }

 private void FinishDesktopSetup(bool practice) {
  S.OnboardingComplete = true; app.Settings.Save();
  if (!app.Isolated) StartupService.Set(S.StartAtLogin);
  if (S.BubbleActive && S.BubbleVisible) app.ShowBubble();
  Navigate("home");
  if (practice) Dispatcher.BeginInvoke(new Action(() => practiceEditor?.Focus()));
 }

 private void Welcome() {
  BuildWelcomePresentation();
 }

 private void BuildSetupNavigation() {
  nav.Children.Clear();
  for (var i = 0;i < SetupLabels.Length;i++) {
   var destination = i;
   var title = C("desktop.setup.stage." + SetupLabels[i],SetupLabels[i]);
   var button = Action(title,() => ShowStep(destination));
   button.HorizontalAlignment = HorizontalAlignment.Stretch; button.HorizontalContentAlignment = HorizontalAlignment.Left;
   button.IsEnabled = i <= step; button.Margin = new(0,2,0,2); button.MinHeight = 42; button.Padding = new(12,7,8,7);
   button.Background = Brushes.Transparent; button.Opacity = 1;
   var row = new Grid(); row.ColumnDefinitions.Add(new() { Width = new GridLength(28) }); row.ColumnDefinitions.Add(new());
   var marker = new System.Windows.Shapes.Ellipse { Width = 12,Height = 12,StrokeThickness = 1,HorizontalAlignment = HorizontalAlignment.Left,VerticalAlignment = VerticalAlignment.Center };
   marker.SetResourceReference(System.Windows.Shapes.Shape.StrokeProperty,i <= step ? "BrandInk" : "Muted");
   if (i <= step) marker.SetResourceReference(System.Windows.Shapes.Shape.FillProperty,"BrandInk"); row.Children.Add(marker);
   var label = Text(title,14,i != step); label.Margin = new(0); label.FontWeight = i == step ? FontWeights.Medium : FontWeights.Normal; label.VerticalAlignment = VerticalAlignment.Center; Grid.SetColumn(label,1); row.Children.Add(label); button.Content = row;
   nav.Children.Add(button);
   if (i < SetupLabels.Length - 1) { var line = new Border { Width = 1,Height = 16,HorizontalAlignment = HorizontalAlignment.Left,Margin = new(19,0,0,0) }; line.SetResourceReference(Border.BackgroundProperty,"Line"); nav.Children.Add(line); }
  }
  liveStatus.Text = C("desktop.setup.local","Your key. Your provider. Your words.");
 }

 private void BuildSetupCompanion() {
  var content = new StackPanel { Margin = new(30),VerticalAlignment = VerticalAlignment.Center };
  var label = Text(C("desktop.setup.companion." + SetupLabels[step],"Your voice, in your workspace."),27); label.FontFamily = Theme.Serif; content.Children.Add(label);
  content.Children.Add(Text(C("desktop.setup.companion_body." + SetupLabels[step],"Use the shortcut in the field you already have open."),15,true));
  var demo = new Border { Padding = new(22),CornerRadius = new(8),Margin = new(0,28,0,20),BorderThickness = new(1) };
  demo.SetResourceReference(Border.BackgroundProperty,"Surface"); demo.SetResourceReference(Border.BorderBrushProperty,"Line");
  var lines = new StackPanel(); lines.Children.Add(Text(C("desktop.demo.label","Example dictation"),12,true));
  lines.Children.Add(Text(C("desktop.demo.spoken","Let's move our meeting to Thursday. I'll send the notes before then."),20));
  var keys = Text(app.HotkeyLabel,14); keys.Margin = new(0,24,0,0); lines.Children.Add(keys); demo.Child = lines; content.Children.Add(demo);
  setupCompanion.Child = new ScrollViewer { Content = content,VerticalScrollBarVisibility = ScrollBarVisibility.Auto,HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
 }
}
