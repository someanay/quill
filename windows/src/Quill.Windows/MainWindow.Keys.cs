using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Clipboard = System.Windows.Clipboard;
namespace Quill.Windows;
public sealed partial class MainWindow {
 private void GuidedKeys(StackPanel destination,bool activateSpeech,bool prefill,Action saved) {
  var drafts = new KeySetupDraftStore(app.Folder); var initial = Provider.Gemini;
  destination.Children.Add(Text(C("onboarding.key.choose","Choose any 1 of the following"),17));
  var host = new StackPanel(); var border = new Border { Child = host,CornerRadius = new(8),BorderThickness = new(1),Margin = new(0,8,0,8) }; border.SetResourceReference(Border.BorderBrushProperty,"Line"); border.SetResourceReference(Border.BackgroundProperty,"Surface"); destination.Children.Add(border);
  var sessions = new List<KeySetupSession>(); var timers = new List<PageLifetime>();
  void Render() {
   foreach (var session in sessions) { session.Dispose(); pageLifetimes.Remove(session); } sessions.Clear(); foreach (var timer in timers) { timer.Dispose(); pageLifetimes.Remove(timer); } timers.Clear(); host.Children.Clear();
   var otherHost = new StackPanel();
   foreach (var provider in KeySetupSession.GuidedProviders.Concat(new[] {Provider.OpenAI,Provider.Custom})) {
    if (provider == Provider.OpenAI) host.Children.Add(new Expander { Header = Text(C("windows.keys.other","Other providers: OpenAI and custom"),13,true),Content = otherHost,IsExpanded = drafts.Expanded(initial) is "OpenAI" or "Custom",Margin = new(16,8,16,8) });
    var rowHost = provider is Provider.OpenAI or Provider.Custom ? otherHost : host;
    var expanded = drafts.Expanded(initial) == provider.ToString();
    if (rowHost.Children.Count > 0) { var divider = new Border { Height = 1 }; divider.SetResourceReference(Border.BackgroundProperty,"Line"); rowHost.Children.Add(divider); }
    var title = provider switch { Provider.Gemini => C("onboarding.key.easy","Google Gemini (Big Free Usage, Easy Process)"),Provider.Groq => C("onboarding.key.more_steps","Groq (Big Free Usage, Longer Process)"),Provider.OpenRouter => C("onboarding.key.free_options","OpenRouter (Free options, Longer Process)"),_ => provider.ToString() };
    var header = Action(title,() => { drafts.Expand(expanded ? "" : provider.ToString()); Render(); }); header.Background = Brushes.Transparent; header.HorizontalContentAlignment = HorizontalAlignment.Stretch; header.Content = new TextBlock { Text = title + (expanded ? "  ⌃" : "  ⌄"),FontSize = 14,TextWrapping = TextWrapping.Wrap }; header.Margin = new(0); header.Padding = new(16); System.Windows.Automation.AutomationProperties.SetHelpText(header,C(expanded ? "onboarding.key.expanded" : "onboarding.key.collapsed",expanded ? "Expanded" : "Collapsed")); rowHost.Children.Add(header);
    if (!expanded) continue;
    var body = new StackPanel { Margin = new(16,0,16,16) }; rowHost.Children.Add(body);
    var guideKey = provider switch { Provider.Gemini => "onboarding.key.guide.gemini_steps",Provider.Groq => "onboarding.key.guide.groq_steps",Provider.OpenRouter => "onboarding.key.guide.openrouter_steps",Provider.OpenAI => "onboarding.key.guide.openai_steps",_ => "" };
    if (guideKey.Length > 0) { var lines = C(guideKey,"").Split('\n').Where(l => !string.IsNullOrWhiteSpace(l)).ToArray(); for (int i = 0;i < lines.Length;i++) { var line = lines[i]; var caption = (provider == Provider.OpenAI ? "" : (i + 1) + ") ") + line; var url = System.Text.RegularExpressions.Regex.Match(line,@"https://[^\s]+"); if (url.Success) { var link = Action(caption,() => Open(url.Value)); var linkText = new TextBlock { Text = caption,FontSize = 13,TextAlignment = TextAlignment.Left,HorizontalAlignment = HorizontalAlignment.Left,TextWrapping = TextWrapping.Wrap }; linkText.SetResourceReference(TextBlock.ForegroundProperty,"BrandInk"); link.Content = linkText; link.Background = Brushes.Transparent; link.HorizontalContentAlignment = HorizontalAlignment.Left; link.Margin = new(0); link.Padding = new(0,4,0,4); body.Children.Add(link); } else body.Children.Add(Text(caption,13)); } }
    string endpoint = S.CustomBaseUrl; TextBox? endpointBox = null; TextBox? customModel = null;
    if (provider == Provider.Custom) { endpointBox = Field(body,C("windows.speech.url","HTTPS base URL"),endpoint); customModel = Field(body,C("windows.speech.custom_model","Speech model ID"),S.CustomModel); }
    var session = new KeySetupSession(provider,drafts,app.Provider.ValidateSetupKey); sessions.Add(session); pageLifetimes.Add(session);
    var inputGrid = new Grid(); inputGrid.ColumnDefinitions.Add(new() { Width = new GridLength(1,GridUnitType.Star) }); inputGrid.ColumnDefinitions.Add(new() { Width = GridLength.Auto }); inputGrid.ColumnDefinitions.Add(new() { Width = GridLength.Auto });
    var secret = new PasswordBox { MaxLength = 4096 }; var revealed = new TextBox { MaxLength = 4096,Visibility = Visibility.Collapsed }; System.Windows.Automation.AutomationProperties.SetName(secret,C("onboarding.key.field_label","API key")); System.Windows.Automation.AutomationProperties.SetName(revealed,C("onboarding.key.field_label","API key")); System.Windows.Automation.AutomationProperties.SetAutomationId(revealed,"quill-password-key"); inputGrid.Children.Add(secret); inputGrid.Children.Add(revealed);
    var paste = Action(C("onboarding.key.paste","Paste"),() => { try { if (Clipboard.ContainsText()) { var text = Clipboard.GetText(); if (text.Length <= 4096) secret.Password = text; } } catch (System.Runtime.InteropServices.COMException) { SetNotice(C("onboarding.key.unverified","Clipboard is busy. Try again.")); } }); Grid.SetColumn(paste,1); inputGrid.Children.Add(paste);
    bool reveal = false; Button? show = null; show = Action(C("a11y.show_key","Show key"),() => { reveal = !reveal; revealed.Text = secret.Password; revealed.Visibility = reveal ? Visibility.Visible : Visibility.Collapsed; secret.Visibility = reveal ? Visibility.Collapsed : Visibility.Visible; var label = C(reveal ? "a11y.hide_key" : "a11y.show_key",reveal ? "Hide key" : "Show key"); show!.Content = new TextBlock { Text = label }; System.Windows.Automation.AutomationProperties.SetName(show,label); }); Grid.SetColumn(show,2); inputGrid.Children.Add(show); body.Children.Add(inputGrid);
    var label = Field(body,C("windows.keys.label","Key label (optional)")); var state = Text("",13,true); body.Children.Add(state);
    var retry = Action(C("onboarding.key.retry","Check again"),() => _ = session.Update(secret.Password,endpoint)); body.Children.Add(retry);
    Button? save = null; save = Action(C("onboarding.key.save_provider","Save %1$s API Key Securely",title.Split(" (")[0]),async () => {
     var successful = await session.Save((candidate,token) => KeySetupSaving.Save(provider,candidate,endpoint,customModel?.Text ?? S.CustomModel,label.Text,activateSpeech,app.Secrets,app.Settings,t => app.Provider.Models(provider,candidate,endpoint,true,t),token)); if (successful) saved();
    },true); body.Children.Add(save);
    void Refresh() {
     host.IsEnabled = !session.Saving; secret.IsEnabled = revealed.IsEnabled = show.IsEnabled = header.IsEnabled = !session.Saving; try { paste.IsEnabled = !session.Saving && Clipboard.ContainsText(); } catch (System.Runtime.InteropServices.COMException) { paste.IsEnabled = false; } label.IsEnabled = !session.Saving; if (endpointBox != null) endpointBox.IsEnabled = !session.Saving; if (customModel != null) customModel.IsEnabled = !session.Saving;
     save.IsEnabled = session.CanSave; retry.Visibility = session.State is KeyCheckState.Unverified or KeyCheckState.StorageError ? Visibility.Visible : Visibility.Collapsed; retry.IsEnabled = !session.Saving;
     state.Text = session.State switch { KeyCheckState.Empty => "",KeyCheckState.Incomplete => C("onboarding.key.format_help","Enter a complete key starting with %1$s.",KeySetupSession.Mask(provider)),KeyCheckState.Waiting => "",KeyCheckState.Checking => C("onboarding.key.verifying","Checking your key…"),KeyCheckState.Verified => "✓ " + C("onboarding.key.verified","Key verified"),KeyCheckState.Rejected => C("key.error.invalid","That key was rejected. Check you copied it fully."),KeyCheckState.StorageError => C("onboarding.key.storage_error","We could not save your key securely. Keep this screen open and try again."),_ => C("onboarding.key.unverified","We could not reach the provider. Your draft is kept securely.") };
     state.SetResourceReference(TextBlock.ForegroundProperty,session.State == KeyCheckState.Verified ? "BrandInk" : session.State is KeyCheckState.Rejected or KeyCheckState.StorageError ? "Error" : "Muted");
    }
    session.Changed += Refresh; var clipboardTimer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(750) }; clipboardTimer.Tick += (_,_) => { try { paste.IsEnabled = !session.Saving && Clipboard.ContainsText(); } catch (System.Runtime.InteropServices.COMException) { paste.IsEnabled = false; } }; clipboardTimer.Start(); var timerLifetime = new PageLifetime(() => clipboardTimer.Stop()); pageLifetimes.Add(timerLifetime); timers.Add(timerLifetime); bool syncing = false;
    secret.PasswordChanged += (_,_) => { if (syncing) return; syncing = true; revealed.Text = secret.Password; syncing = false; _ = session.Update(secret.Password,endpoint); };
    revealed.TextChanged += (_,_) => { if (syncing) return; secret.Password = revealed.Text; };
    if (endpointBox != null) endpointBox.TextChanged += (_,_) => { endpoint = endpointBox.Text.Trim(); try { secret.Password = drafts.Read(provider,endpoint); } catch (ArgumentException) { secret.Password = ""; } _ = session.Update(secret.Password,endpoint); };
    string value; try { value = drafts.Read(provider,endpoint); if (value.Length == 0 && prefill) value = app.Secrets.Read(provider,endpoint) ?? ""; } catch (ArgumentException) { value = ""; } catch (Exception) { value = ""; SetNotice(C("onboarding.key.storage_error","We could not load your key securely.")); }
    if (value.Length > 0) secret.Password = value; else Refresh();
    body.Children.Add(Text(C(provider == Provider.OpenRouter ? "onboarding.key.router_note" : "onboarding.key.free_note","Free-tier limits apply. Checking your key does not send audio."),12,true));
   }
  }
  Render(); destination.Children.Add(Text(C("windows.keys.secure","Keys are encrypted using your Windows account. They stay on this PC and are sent only to their selected provider. A rejected new key leaves your saved keys intact."),12,true));
 }
 private sealed class PageLifetime(Action end) : IDisposable { public void Dispose() => end(); }
}
