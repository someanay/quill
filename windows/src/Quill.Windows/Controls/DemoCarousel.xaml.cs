using System.IO;
using System.Windows;
using System.Windows.Controls;

namespace Quill.Windows.Controls;

public partial class DemoCarousel : System.Windows.Controls.UserControl
{
    private readonly List<string> _clips = [];
    private int _index;

    public static readonly DependencyProperty AssetFolderProperty = DependencyProperty.Register(
        nameof(AssetFolder), typeof(string), typeof(DemoCarousel), new PropertyMetadata("Home"));

    public string AssetFolder
    {
        get => (string)GetValue(AssetFolderProperty);
        set => SetValue(AssetFolderProperty, value);
    }

    public DemoCarousel()
    {
        InitializeComponent();
        Loaded += OnLoaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        _clips.Clear();
        var directory = Path.Combine(AppContext.BaseDirectory, "Assets", "Carousel", AssetFolder);
        if (Directory.Exists(directory))
            _clips.AddRange(Directory.EnumerateFiles(directory, "*.mp4").OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase).Take(6));
        EmptyState.Visibility = _clips.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        Controls.Visibility = _clips.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
        if (_clips.Count > 0) Play(0);
    }

    private void Play(int index)
    {
        if (_clips.Count == 0) return;
        _index = (index + _clips.Count) % _clips.Count;
        Player.Source = new Uri(_clips[_index], UriKind.Absolute);
        Player.Position = TimeSpan.Zero;
        Player.Play();
        ClipTitle.Text = Path.GetFileNameWithoutExtension(_clips[_index]);
        Position.Text = $"{_index + 1} / {_clips.Count}";
    }

    private void Previous_Click(object sender, RoutedEventArgs e) => Play(_index - 1);
    private void Next_Click(object sender, RoutedEventArgs e) => Play(_index + 1);
    private void Player_MediaEnded(object sender, RoutedEventArgs e) { Player.Position = TimeSpan.Zero; Player.Play(); }
    private void OnUnloaded(object sender, RoutedEventArgs e) => Player.Stop();
}
