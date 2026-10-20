using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;
using System.Runtime.InteropServices;
using System.Windows.Interop;
namespace Quill.Windows;
public static class Theme {
 public static bool IsDark { get; private set; }
 [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(nint hwnd,int attribute,ref int value,int size);
 public static void ApplyWindowChrome(Window window) {
  var handle = new WindowInteropHelper(window).Handle;
  if (handle == IntPtr.Zero) return;
  int dark = IsDark ? 1 : 0;
  _ = DwmSetWindowAttribute(handle,20,ref dark,sizeof(int));
 }
 private static readonly IReadOnlyList<FontFamily> BundledFonts = Fonts.GetFontFamilies(new Uri("pack://application:,,,/Quill;component/"),"./Assets/Fonts/").ToArray();
 public static FontFamily Sans { get; } = BundledFonts.Single(f => f.Source.EndsWith("#IBM Plex Sans",StringComparison.Ordinal));
 public static FontFamily Serif { get; } = BundledFonts.Single(f => f.Source.EndsWith("#IBM Plex Serif Var",StringComparison.Ordinal));
 public static SolidColorBrush Hex(string value) => new((Color)ColorConverter.ConvertFromString(value));
 public static void Apply(string preference) {
  bool dark = preference == "Dark" || preference == "Match system" && (Registry.GetValue(@"HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize", "AppsUseLightTheme", 1) as int? ?? 1) == 0;
  IsDark = dark;
  foreach (Window window in Application.Current.Windows) ApplyWindowChrome(window);
  var r = Application.Current.Resources;
  foreach (var entry in new Dictionary<string,string> { ["Paper"] = dark ? "#11141A" : "#F5F7F5", ["Surface"] = dark ? "#171B23" : "#FFFFFF", ["Sunken"] = dark ? "#1E232D" : "#EDF0ED", ["Ink"] = dark ? "#E0E7F6" : "#202522", ["Muted"] = dark ? "#B2B9C6" : "#5C665F", ["Line"] = dark ? "#353C48" : "#D9DED9", ["Brand"] = "#234DCE", ["BrandInk"] = dark ? "#B6C9FF" : "#1A3FAF", ["BrandSoft"] = dark ? "#202D47" : "#E4EBF5", ["Gold"] = dark ? "#B6C9FF" : "#234DCE", ["GoldDeep"] = dark ? "#D3DFFF" : "#1A3FAF", ["GoldSoft"] = dark ? "#202D47" : "#E4EBF5", ["Error"] = dark ? "#E8635B" : "#D6453D" }) r[entry.Key] = Hex(entry.Value);
  var text = new Style(typeof(TextBlock)); text.Setters.Add(new Setter(TextBlock.FontFamilyProperty, Sans)); text.Setters.Add(new Setter(TextBlock.ForegroundProperty, new DynamicResourceExtension("Ink"))); text.Setters.Add(new Setter(TextBlock.TextWrappingProperty, TextWrapping.Wrap)); r[typeof(TextBlock)] = text;
  var button = new Style(typeof(Button)); button.Setters.Add(new Setter(Control.FontFamilyProperty, Sans)); button.Setters.Add(new Setter(Control.FontSizeProperty, 15.0)); button.Setters.Add(new Setter(Control.FontWeightProperty, FontWeights.SemiBold)); button.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(18,9,18,9))); button.Setters.Add(new Setter(FrameworkElement.MinHeightProperty, 42.0)); button.Setters.Add(new Setter(Control.BackgroundProperty, new DynamicResourceExtension("BrandSoft"))); button.Setters.Add(new Setter(Control.ForegroundProperty, new DynamicResourceExtension("BrandInk"))); button.Setters.Add(new Setter(Control.BorderThicknessProperty, new Thickness(0))); button.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(0,4,8,4)));
  var template = new ControlTemplate(typeof(Button)); var border = new FrameworkElementFactory(typeof(Border)); border.SetValue(Border.CornerRadiusProperty, new CornerRadius(6)); border.SetBinding(Border.BackgroundProperty, new System.Windows.Data.Binding("Background") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) }); var content = new FrameworkElementFactory(typeof(ContentPresenter)); content.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Stretch); content.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center); content.SetBinding(ContentPresenter.MarginProperty, new System.Windows.Data.Binding("Padding") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) }); border.SetValue(Border.BorderThicknessProperty,new Thickness(2)); border.SetBinding(Border.BorderBrushProperty,new System.Windows.Data.Binding("BorderBrush") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) }); border.AppendChild(content); template.VisualTree = border; button.Setters.Add(new Setter(Control.TemplateProperty, template));
  button.Setters.Add(new Setter(Control.BorderBrushProperty, Brushes.Transparent)); var focus = new Trigger { Property = UIElement.IsKeyboardFocusedProperty, Value = true }; focus.Setters.Add(new Setter(Control.BorderBrushProperty,new DynamicResourceExtension("BrandInk"))); button.Triggers.Add(focus);
  var hover = new Trigger { Property = UIElement.IsMouseOverProperty, Value = true }; hover.Setters.Add(new Setter(UIElement.OpacityProperty, 0.85)); button.Triggers.Add(hover); var disabled = new Trigger { Property = UIElement.IsEnabledProperty, Value = false }; disabled.Setters.Add(new Setter(UIElement.OpacityProperty, 0.45)); button.Triggers.Add(disabled); r[typeof(Button)] = button;
  foreach (var type in new[] { typeof(TextBox), typeof(PasswordBox), typeof(ComboBox) }) { var style = new Style(type); style.Setters.Add(new Setter(Control.FontFamilyProperty, Sans)); style.Setters.Add(new Setter(Control.FontSizeProperty, 15.0)); style.Setters.Add(new Setter(Control.PaddingProperty, new Thickness(10,7,10,7))); style.Setters.Add(new Setter(FrameworkElement.MinHeightProperty, 42.0)); style.Setters.Add(new Setter(Control.BackgroundProperty, new DynamicResourceExtension("Surface"))); style.Setters.Add(new Setter(Control.ForegroundProperty, new DynamicResourceExtension("Ink"))); style.Setters.Add(new Setter(Control.BorderBrushProperty, new DynamicResourceExtension("Line"))); style.Setters.Add(new Setter(FrameworkElement.MarginProperty, new Thickness(0,4,0,10))); r[type] = style; }
  var combo = new Style(typeof(ComboBox),(Style)r[typeof(ComboBox)]);
  combo.Setters.Add(new Setter(Control.TemplateProperty,(ControlTemplate)System.Windows.Markup.XamlReader.Parse("""
<ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="ComboBox">
 <Grid>
  <Border Background="{DynamicResource Surface}" BorderBrush="{DynamicResource Line}" BorderThickness="1" CornerRadius="6"/>
  <ToggleButton x:Name="DropDownButton" Focusable="False" IsChecked="{Binding IsDropDownOpen, RelativeSource={RelativeSource TemplatedParent}, Mode=TwoWay}" ClickMode="Press" Background="Transparent" BorderThickness="0">
   <ToggleButton.Template><ControlTemplate TargetType="ToggleButton"><Border Background="Transparent"><Grid><ContentPresenter/><TextBlock Text="⌄" Foreground="{DynamicResource Ink}" HorizontalAlignment="Right" VerticalAlignment="Center" Margin="0,0,14,0"/></Grid></Border></ControlTemplate></ToggleButton.Template>
  </ToggleButton>
  <ContentPresenter x:Name="Selection" Content="{TemplateBinding SelectionBoxItem}" ContentTemplate="{TemplateBinding SelectionBoxItemTemplate}" Margin="12,10,40,10" VerticalAlignment="Center" IsHitTestVisible="False" TextElement.Foreground="{DynamicResource Ink}"/>
  <TextBox x:Name="PART_EditableTextBox" Visibility="Hidden" Margin="4,0,34,0" Background="{DynamicResource Surface}" Foreground="{DynamicResource Ink}" BorderThickness="0" MinHeight="0" IsReadOnly="{TemplateBinding IsReadOnly}"/>
  <Popup x:Name="PART_Popup" IsOpen="{TemplateBinding IsDropDownOpen}" Placement="Bottom" AllowsTransparency="True" Focusable="False" PopupAnimation="None">
   <Border MinWidth="{Binding ActualWidth, RelativeSource={RelativeSource TemplatedParent}}" MaxHeight="340" Background="{DynamicResource Surface}" BorderBrush="{DynamicResource Line}" BorderThickness="1" CornerRadius="8" Padding="4"><ScrollViewer CanContentScroll="True" VerticalScrollBarVisibility="Auto"><ItemsPresenter KeyboardNavigation.DirectionalNavigation="Contained"/></ScrollViewer></Border>
  </Popup>
 </Grid>
 <ControlTemplate.Triggers><Trigger Property="IsEditable" Value="True"><Setter TargetName="Selection" Property="Visibility" Value="Hidden"/><Setter TargetName="PART_EditableTextBox" Property="Visibility" Value="Visible"/></Trigger><Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.45"/></Trigger></ControlTemplate.Triggers>
</ControlTemplate>
""")));
  r[typeof(ComboBox)] = combo;
  var item = new Style(typeof(ComboBoxItem)); item.Setters.Add(new Setter(Control.ForegroundProperty,new DynamicResourceExtension("Ink"))); item.Setters.Add(new Setter(Control.BackgroundProperty,new DynamicResourceExtension("Surface"))); item.Setters.Add(new Setter(Control.PaddingProperty,new Thickness(10,7,10,7))); var selected = new Trigger { Property = ComboBoxItem.IsHighlightedProperty,Value = true }; selected.Setters.Add(new Setter(Control.BackgroundProperty,new DynamicResourceExtension("BrandSoft"))); selected.Setters.Add(new Setter(Control.ForegroundProperty,new DynamicResourceExtension("BrandInk"))); item.Triggers.Add(selected); r[typeof(ComboBoxItem)] = item;
  var check = new Style(typeof(CheckBox)); check.Setters.Add(new Setter(Control.FontFamilyProperty, Sans)); check.Setters.Add(new Setter(Control.ForegroundProperty, new DynamicResourceExtension("Ink"))); check.Setters.Add(new Setter(Control.FontSizeProperty,15.0)); check.Setters.Add(new Setter(Control.PaddingProperty,new Thickness(8))); check.Setters.Add(new Setter(FrameworkElement.MinHeightProperty,40.0)); check.Setters.Add(new Setter(FrameworkElement.VerticalAlignmentProperty,VerticalAlignment.Center));
  check.Setters.Add(new Setter(Control.TemplateProperty,(ControlTemplate)System.Windows.Markup.XamlReader.Parse("""
<ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="CheckBox"><Grid Background="Transparent"><Grid.ColumnDefinitions><ColumnDefinition Width="*"/><ColumnDefinition Width="Auto"/></Grid.ColumnDefinitions><ContentPresenter VerticalAlignment="Center" Margin="0,8,16,8"/><Border x:Name="Track" Grid.Column="1" Width="46" Height="27" CornerRadius="14" Background="{DynamicResource Line}" VerticalAlignment="Center"><Ellipse x:Name="Thumb" Fill="White" Width="19" Height="19" Margin="4" HorizontalAlignment="Left"/></Border></Grid><ControlTemplate.Triggers><Trigger Property="IsChecked" Value="True"><Setter TargetName="Track" Property="Background" Value="{DynamicResource Brand}"/><Setter TargetName="Thumb" Property="HorizontalAlignment" Value="Right"/></Trigger><Trigger Property="IsKeyboardFocused" Value="True"><Setter TargetName="Track" Property="BorderBrush" Value="{DynamicResource BrandInk}"/><Setter TargetName="Track" Property="BorderThickness" Value="2"/></Trigger><Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.45"/></Trigger></ControlTemplate.Triggers></ControlTemplate>
"""))); r[typeof(CheckBox)] = check;
 }
}
