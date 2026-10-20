using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace Quill.Windows;

public sealed partial class MainWindow {
 private Border desktopSidebar = null!;
 private ColumnDefinition sidebarColumn = null!;
 private bool setupWindow;
 private readonly Border setupCompanion = new();
 private ColumnDefinition companionColumn = null!;
 private readonly StackPanel compactNavigation = new() { Orientation = Orientation.Horizontal, Margin = new(20,8,20,0) };
 private TextBox? practiceEditor;
 private void SetDesktopNavigationVisible(bool visible) {
  setupWindow = !visible;
  ResizeMode = ResizeMode.CanResize;
  MinWidth = 640; MinHeight = 440; MaxWidth = MaxHeight = double.PositiveInfinity;
  compactNavigation.Children.Clear();
  if (visible) {
   foreach (var item in new[] { ("home","nav.home","Home"),("history","nav.history","History"),("settings","nav.settings","Settings") }) {
    var destination = item.Item1;
    compactNavigation.Children.Add(Action(C(item.Item2,item.Item3),() => Navigate(destination)));
   }
  } else compactNavigation.Children.Add(Text("quill.",22));
  UpdateWorkspaceLayout(ActualWidth > 0 ? ActualWidth : Width);
 }
 private void UpdateWorkspaceLayout(double width) {
  var rail = width >= 900;
  desktopSidebar.Visibility = rail ? Visibility.Visible : Visibility.Collapsed;
  sidebarColumn.Width = new GridLength(rail ? 206 : 0);
  compactNavigation.Visibility = rail ? Visibility.Collapsed : Visibility.Visible;
  var companion = setupWindow && step > 0 && width >= 1200;
  companionColumn.Width = companion ? new GridLength(0.85,GridUnitType.Star) : new GridLength(0);
  setupCompanion.Visibility = companion ? Visibility.Visible : Visibility.Collapsed;
 }
 private void BuildDesktopShell() {
  var root = new Grid { UseLayoutRounding = true };
  root.SetResourceReference(System.Windows.Controls.Panel.BackgroundProperty,"Paper");
  sidebarColumn = new() { Width = new GridLength(210) }; root.ColumnDefinitions.Add(sidebarColumn);
  root.ColumnDefinitions.Add(new());
  companionColumn = new() { Width = new GridLength(0) }; root.ColumnDefinitions.Add(companionColumn);
  Content = root;
  root.SizeChanged += (_,args) => UpdateWorkspaceLayout(args.NewSize.Width);
  setupCompanion.SetResourceReference(Border.BackgroundProperty,"Paper");
  Grid.SetColumn(setupCompanion,2); root.Children.Add(setupCompanion);
  var rail = new DockPanel();
  var sidebar = desktopSidebar = new Border { Child = rail, BorderThickness = new(0,0,1,0) };
  sidebar.SetResourceReference(Border.BackgroundProperty,"Surface");
  sidebar.SetResourceReference(Border.BorderBrushProperty,"Line");
  root.Children.Add(sidebar);
  var brand = new TextBlock { FontFamily = Theme.Sans, FontWeight = FontWeights.SemiBold, FontSize = 30, Margin = new(22,20,16,16) };
  brand.Inlines.Add(new System.Windows.Documents.Run("quill"));
  var dot = new System.Windows.Documents.Run("."); dot.SetResourceReference(System.Windows.Documents.TextElement.ForegroundProperty,"BrandInk"); brand.Inlines.Add(dot);
  DockPanel.SetDock(brand,Dock.Top); rail.Children.Add(brand);
  liveStatus.FontSize = 12; liveStatus.Margin = new(20,14,16,20);
  liveStatus.SetResourceReference(TextBlock.ForegroundProperty,"Muted");
  DockPanel.SetDock(liveStatus,Dock.Bottom); rail.Children.Add(liveStatus);
  nav.Margin = new(10,0,10,12);
  rail.Children.Add(new ScrollViewer { Content = nav, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled });
  var body = new DockPanel(); Grid.SetColumn(body,1); root.Children.Add(body);
  DockPanel.SetDock(compactNavigation,Dock.Top); body.Children.Add(compactNavigation);
  notice.Margin = new(28,12,28,0); notice.SetResourceReference(TextBlock.ForegroundProperty,"Error"); notice.Visibility = Visibility.Collapsed;
  DockPanel.SetDock(notice,Dock.Top); body.Children.Add(notice);
  page.Margin = new(44,38,44,34); body.Children.Add(scroll);
 }

 private void BuildDesktopNavigation() {
  nav.Children.Clear();
  void Destination(string id,string icon,string label) {
   var selected = route == id;
   var button = Action(label,() => Navigate(id)); button.Tag = id;
   button.HorizontalAlignment = HorizontalAlignment.Stretch;
   button.Margin = new(0,1,0,1); button.Padding = new(12,6,10,6); button.MinHeight = 32;
   button.SetResourceReference(BackgroundProperty,selected ? "BrandSoft" : "Surface");
   var content = new Grid(); content.ColumnDefinitions.Add(new() { Width = new GridLength(27) }); content.ColumnDefinitions.Add(new());
   var symbol = new TextBlock { Text = icon, FontFamily = new FontFamily("Segoe Fluent Icons"), FontSize = 16, VerticalAlignment = VerticalAlignment.Center };
   symbol.SetResourceReference(TextBlock.ForegroundProperty,selected ? "BrandInk" : "Muted"); content.Children.Add(symbol);
   var text = new TextBlock { Text = label, FontSize = 14, FontWeight = selected ? FontWeights.Medium : FontWeights.Normal, VerticalAlignment = VerticalAlignment.Center };
   text.SetResourceReference(TextBlock.ForegroundProperty,selected ? "BrandInk" : "Ink"); Grid.SetColumn(text,1); content.Children.Add(text); button.Content = content;
   System.Windows.Automation.AutomationProperties.SetItemStatus(button,selected ? "Selected" : "");
   nav.Children.Add(button);
  }
  Destination("home","\uE80F",C("nav.home","Home"));
  Destination("history","\uE81C",C("nav.history","History"));
  Destination("settings","\uE713",C("nav.settings","Settings"));
  if (route is not "home" and not "history") {
   var divider = new Border { Height = 1, Margin = new(10,12,10,12) }; divider.SetResourceReference(Border.BackgroundProperty,"Line"); nav.Children.Add(divider);
   foreach (var item in new[] {
    ("speech","\uE9D9","settings.row.speech_model","Speech & model"),
    ("keys","\uE72E","settings.row.api_keys","API keys"),
    ("cleanup","\uE734","settings.row.cleanup","Clean up with AI"),
    ("style","\uE8D2","settings.row.writing_style","Writing style"),
    ("words","\uE70F","settings.row.custom_words","Custom words"),
    ("audio","\uE720","settings.row.audio","Audio & recording"),
    ("bubble","\uE8B2","settings.row.bubble","Visual preferences"),
    ("privacy","\uEA18","settings.row.privacy","Privacy and History"),
    ("advanced","\uE713","settings.row.advanced","Advanced"),
    ("about","\uE946","settings.row.about","About & legal")
   }) Destination(item.Item1,item.Item2,C(item.Item3,item.Item4));
  }
  nav.Visibility = Visibility.Visible; SetDesktopNavigationVisible(S.OnboardingComplete);
 }

 private void ArrangeSettingsOverview() {
  // Keep each heading with its group as the window crosses the two-column breakpoint.
  var items = page.Children.Cast<UIElement>().Skip(1).ToArray();
  foreach (var item in items) page.Children.Remove(item);
  var columns = new DesktopColumns(); var left = new StackPanel(); var right = new StackPanel();
  for (int i = 0; i < items.Length; i++) (i < 2 ? left : right).Children.Add(items[i]);
  columns.Children.Add(left); columns.Children.Add(right); page.Children.Add(columns);
 }

 private void BuildHistoryBrowser(IReadOnlyList<HistoryEntry> entries) {
  var browser = new Grid { Margin = new(0,12,0,0), Height = Math.Max(260,Height - 220) };
  browser.ColumnDefinitions.Add(new() { Width = new GridLength(0.42,GridUnitType.Star) });
  browser.ColumnDefinitions.Add(new() { Width = new GridLength(16) });
  browser.ColumnDefinitions.Add(new() { Width = new GridLength(0.58,GridUnitType.Star) });
  var list = new System.Windows.Controls.ListBox { BorderThickness = new(1), Padding = new(4), HorizontalContentAlignment = HorizontalAlignment.Stretch };
  list.SetResourceReference(BackgroundProperty,"Surface"); list.SetResourceReference(ForegroundProperty,"Ink"); list.SetResourceReference(System.Windows.Controls.Control.BorderBrushProperty,"Line");
  ScrollViewer.SetHorizontalScrollBarVisibility(list,ScrollBarVisibility.Disabled);
  System.Windows.Automation.AutomationProperties.SetName(list,C("nav.history","History"));
  foreach (var entry in entries) {
   var item = new ListBoxItem { Tag = entry, Padding = new(10), HorizontalContentAlignment = HorizontalAlignment.Stretch };
   var preview = new StackPanel(); var meta = Text(entry.Created.ToLocalTime().ToString("d MMM · h:mm tt") + " · " + entry.Destination,12,true); preview.Children.Add(meta);
   preview.Children.Add(new TextBlock { Text = System.Text.RegularExpressions.Regex.Replace(entry.Text,@"\s+"," ").Trim(), FontSize = 14, TextWrapping = TextWrapping.NoWrap, TextTrimming = TextTrimming.CharacterEllipsis });
   item.Content = preview; list.Items.Add(item);
  }
  var detail = new DockPanel(); var surface = new Border { Child = detail, Padding = new(18), BorderThickness = new(1), CornerRadius = new(6) };
  surface.SetResourceReference(Border.BackgroundProperty,"Surface"); surface.SetResourceReference(Border.BorderBrushProperty,"Line");
  list.SelectionChanged += (_,_) => {
   if (list.SelectedItem is not ListBoxItem { Tag: HistoryEntry entry }) return;
   detail.Children.Clear();
   var meta = Text(entry.Created.ToLocalTime().ToString("ddd, d MMM · h:mm tt") + " · " + entry.Destination,12,true); DockPanel.SetDock(meta,Dock.Top); detail.Children.Add(meta);
   var actions = new WrapPanel { Margin = new(0,12,0,0) };
   actions.Children.Add(Action(C("history.item.copy","Copy text"),() => app.Clipboard.Copy(entry.Text,false)));
   actions.Children.Add(Action(C("windows.history.delete","Delete"),() => { app.History.Delete(entry.Id); Navigate("history"); }));
   DockPanel.SetDock(actions,Dock.Bottom); detail.Children.Add(actions);
   var transcript = new TextBox { Text = entry.Text, IsReadOnly = true, TextWrapping = TextWrapping.Wrap, BorderThickness = new(0), Padding = new(0), Margin = new(0), VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
   System.Windows.Automation.AutomationProperties.SetName(transcript,C("windows.home.last","Latest dictation")); detail.Children.Add(transcript);
  };
  browser.Children.Add(list); Grid.SetColumn(surface,2); browser.Children.Add(surface); page.Children.Add(browser); list.SelectedIndex = 0;
  void ArrangeHistory() {
   var stacked = browser.ActualWidth > 0 && browser.ActualWidth < 620;
   browser.ColumnDefinitions.Clear(); browser.RowDefinitions.Clear();
   browser.ColumnDefinitions.Add(new() { Width = new GridLength(stacked ? 1 : 0.42,GridUnitType.Star) });
   if (stacked) {
    browser.RowDefinitions.Add(new() { Height = new GridLength(150) });
    browser.RowDefinitions.Add(new() { Height = new GridLength(16) });
    browser.RowDefinitions.Add(new()); Grid.SetColumn(surface,0); Grid.SetRow(surface,2);
   } else {
    browser.ColumnDefinitions.Add(new() { Width = new GridLength(16) }); browser.ColumnDefinitions.Add(new() { Width = new GridLength(0.58,GridUnitType.Star) }); Grid.SetColumn(surface,2); Grid.SetRow(surface,0);
   }
  }
  browser.SizeChanged += (_,_) => ArrangeHistory();
  SizeChangedEventHandler resize = (_,_) => browser.Height = Math.Max(260,ActualHeight - 220);
  SizeChanged += resize; pageLifetimes.Add(new PageLifetime(() => SizeChanged -= resize));
 }

 private string practiceDraft = "";
 private TextBlock? workspaceStatus;
 private StackPanel? workspaceActions;
 private InkMeter? workspaceMeter;
 private void BuildHomeWorkspace() {
  var columns = new DesktopColumns { FirstFraction = 0.65 }; var start = page.Children.Count;
  var practice = Card(C("desktop.practice.title","Practice here"));
  practiceEditor = new TextBox { Text = practiceDraft,AcceptsReturn = true,TextWrapping = TextWrapping.Wrap,MinHeight = 210,Height = Math.Clamp(Height - 320,210,500),VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
  var editor = practiceEditor; editor.TextChanged += (_,_) => practiceDraft = editor.Text;
  System.Windows.Automation.AutomationProperties.SetName(practiceEditor,C("desktop.practice.title","Try Quill here")); practice.Children.Add(practiceEditor);
  workspaceStatus = Text(app.HotkeyLabel,14,true); practice.Children.Add(workspaceStatus);
  workspaceMeter = new InkMeter { Height = 64,Visibility = Visibility.Collapsed }; practice.Children.Add(workspaceMeter);
  workspaceActions = new StackPanel(); practice.Children.Add(workspaceActions);
  var left = new StackPanel(); foreach (var item in page.Children.Cast<UIElement>().Skip(start).ToArray()) { page.Children.Remove(item); left.Children.Add(item); }
  var configuration = Card(C("desktop.workspace.configuration","Your setup"));
  Row(configuration,"\uE9D9",C("settings.row.speech_model","Speech & model"),S.Provider + " · " + S.SpeechModel,null,() => Navigate("speech"));
  Row(configuration,"\uE720",C("settings.row.audio","Audio & recording"),S.InputDeviceName.Length > 0 ? S.InputDeviceName : null,null,() => Navigate("audio"));
  Row(configuration,"\uE734",C("settings.row.cleanup","Clean up with AI"),null,S.UseCleanup ? C("value.on","On") : C("value.off","Off"),() => Navigate("cleanup"));
  Toggle(configuration,C("desktop.setup.bubble_choice","Show the floating recording control"),S.BubbleActive && S.BubbleVisible,on => { if (on) app.ShowBubble(); else app.HideBubble(); });
  Card(null,C("windows.home.tip_shortcut","Right-click for clipboard dictation. Press %1$s to start or stop.",app.HotkeyLabel));
  var recent = Card(C("desktop.workspace.recent","Recent dictations")); var entries = app.History.List(S).Take(3).ToArray();
  if (S.PrivateMode) recent.Children.Add(Text(C("windows.history.private","Private mode is on. New dictations are not saved."),13,true));
  if (entries.Length == 0) recent.Children.Add(Text(C("windows.history.empty_help","Your saved words appear here. Try a dictation to get started."),13,true));
  foreach (var entry in entries) {
   recent.Children.Add(Text(entry.Created.LocalDateTime.ToString("t") + " · " + entry.Destination,12,true));
   var preview = Text(entry.Text,14); preview.MaxHeight = 60; preview.TextTrimming = TextTrimming.CharacterEllipsis; recent.Children.Add(preview);
  }
  recent.Children.Add(Action(C("nav.history","History"),() => Navigate("history")));
  var right = new StackPanel(); foreach (var item in page.Children.Cast<UIElement>().Skip(start).ToArray()) { page.Children.Remove(item); right.Children.Add(item); }
  columns.Children.Add(left); columns.Children.Add(right); page.Children.Add(columns);
  SizeChangedEventHandler resize = (_,_) => { if (practiceEditor != null) practiceEditor.Height = Math.Clamp(ActualHeight - 320,210,500); };
  SizeChanged += resize; pageLifetimes.Add(new PageLifetime(() => SizeChanged -= resize));
 }
 private void UpdateHomeSession(bool changed) {
  if (workspaceStatus == null || workspaceMeter == null || workspaceActions == null) return;
  var state = app.Dictation.State;
  workspaceStatus.Text = state == DictationState.Recording ? C("windows.status.listening","Listening") + "  " + TimeSpan.FromMilliseconds(app.Dictation.ElapsedMs).ToString(@"m\:ss") : state == DictationState.Transcribing ? C("bubble.transcribing","Transcribing…") : app.HotkeyLabel;
  workspaceMeter.Visibility = state == DictationState.Recording ? Visibility.Visible : Visibility.Collapsed;
  workspaceMeter.Update(app.Dictation.Rms,state == DictationState.Recording);
  if (!changed) return;
  workspaceActions.Children.Clear();
  if (app.Secrets.ActiveId(S.Provider,S.CustomBaseUrl) == null || !S.InjectionConsent) {
   workspaceActions.Children.Add(Text(C("desktop.workspace.setup_needed","Finish connecting your provider and permissions to start dictating."),13,true));
   workspaceActions.Children.Add(Action(C("windows.advanced.setup","Review setup"),() => ShowStep(0)));
  }
  if (state == DictationState.Recording) workspaceActions.Children.Add(AsyncAction(C("desktop.dictation.stop","Stop dictation"),app.Dictation.StopRecording,true));
  if (state is DictationState.Paste or DictationState.Error) {
   if (app.Dictation.LastText.Length > 0) { workspaceActions.Children.Add(Text(app.Dictation.LastText)); workspaceActions.Children.Add(Action(C("history.item.copy","Copy text"),() => app.Clipboard.Copy(app.Dictation.LastText,false))); }
   if (state == DictationState.Error) workspaceActions.Children.Add(AsyncAction(C("windows.action.retry","Retry saved recording"),app.Dictation.Toggle,true));
  }
  if (state != DictationState.Idle) workspaceActions.Children.Add(AsyncAction(C("windows.action.discard","Discard"),app.Dictation.Cancel));
 }

}

/// <summary>Two readable desktop columns, stacked when the available content width is narrow.</summary>
internal sealed class DesktopColumns : System.Windows.Controls.Panel {
 public double Gap { get; set; } = 28;
 public double MinColumnWidth { get; set; } = 330;
 public double FirstFraction { get; set; } = 0.5;
 private int Count(double width) => width >= MinColumnWidth * 2 + Gap ? 2 : 1;
 protected override Size MeasureOverride(Size available) {
  int columns = Count(available.Width);
  for (var i = 0; i < Children.Count; i++) { var width = columns == 1 ? available.Width : (available.Width - Gap) * (i == 0 ? FirstFraction : 1 - FirstFraction); Children[i].Measure(new Size(width,double.PositiveInfinity)); }
  return new Size(available.Width,columns == 1 ? Children.Cast<UIElement>().Sum(c => c.DesiredSize.Height) : Children.Cast<UIElement>().Select(c => c.DesiredSize.Height).DefaultIfEmpty().Max());
 }
 protected override Size ArrangeOverride(Size final) {
  int columns = Count(final.Width); double y = 0;
  for (int i = 0; i < Children.Count; i++) { var child = Children[i]; var width = columns == 1 ? final.Width : (final.Width - Gap) * (i == 0 ? FirstFraction : 1 - FirstFraction); var x = columns == 1 || i == 0 ? 0 : (final.Width - Gap) * FirstFraction + Gap; child.Arrange(new Rect(x,y,width,child.DesiredSize.Height)); if (columns == 1) y += child.DesiredSize.Height; }
  return final;
 }
}


