using System.Windows;
using Quill.Windows.Core;
using Quill.Windows.Stt;

namespace Quill.Windows;

public partial class VocabularyWindow : Window
{
    private readonly AppServices _services;
    private bool _recording;

    public VocabularyWindow(AppServices services)
    {
        _services = services;
        InitializeComponent();
    }

    public string HeardWord => HeardField.Text.Trim();
    public string Replacement => ReplacementField.Text.Trim();

    private async void RecordButton_Click(object sender, RoutedEventArgs e)
    {
        if (_recording)
        {
            await StopRecordingAsync();
            return;
        }

        try
        {
            var settings = await _services.Settings.LoadAsync();
            var key = _services.Secrets.Get(settings.Provider);
            if (string.IsNullOrWhiteSpace(key))
            {
                RecordStatus.Text = $"Add a {ProviderCatalog.All[settings.Provider].DisplayName} key before recording.";
                return;
            }
            _services.Audio.Start(settings);
            _recording = true;
            RecordButton.Content = "■";
            RecordStatus.Text = "Listening… click the mic again when you finish.";
        }
        catch (Exception error)
        {
            RecordStatus.Text = error.Message;
        }
    }

    private async Task StopRecordingAsync()
    {
        if (!_recording) return;
        _recording = false;
        RecordButton.Content = "🎙";
        try
        {
            var settings = await _services.Settings.LoadAsync();
            var key = _services.Secrets.Get(settings.Provider);
            if (string.IsNullOrWhiteSpace(key)) return;
            var recording = await _services.Audio.StopAsync();
            var model = string.IsNullOrWhiteSpace(settings.Model)
                ? ProviderCatalog.All[settings.Provider].DefaultModel : settings.Model;
            var client = TranscriptionClientFactory.Create(settings.Provider, key, settings.CustomBaseUrl, _services.Http);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(30));
            var transcript = await client.TranscribeAsync(recording.Path,
                new TranscriptionOptions(model, settings.Language), timeout.Token);
            HeardField.Text = transcript.Text.Trim();
            RecordStatus.Text = "Captured. You can edit the heard word before saving.";
            try { File.Delete(recording.Path); } catch { }
        }
        catch (Exception error)
        {
            RecordStatus.Text = error.Message;
        }
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        if (HeardWord.Length == 0 || Replacement.Length == 0)
        {
            RecordStatus.Text = "Add both the heard word and its replacement.";
            return;
        }
        DialogResult = true;
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Close();

    protected override void OnClosed(EventArgs e)
    {
        if (_recording) _ = StopRecordingSilentlyAsync();
        base.OnClosed(e);
    }

    private async Task StopRecordingSilentlyAsync()
    {
        try { await _services.Audio.StopAsync(); } catch { }
        _recording = false;
    }
}
