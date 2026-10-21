using Quill.Windows;
using Xunit;
namespace Quill.Windows.Tests;
public sealed class AudioSignalTests {
 static byte[] Take(Func<int,short> sample,int count = 16000) => AudioRecorder.Wav(Enumerable.Range(0,count).SelectMany(i => BitConverter.GetBytes(sample(i))).ToArray());
 [Fact] public void RejectsSilenceDitherDcOffsetAndSingleClick() {
  Assert.False(AudioSignal.HasAudibleSignal(Take(_ => 0)));
  Assert.False(AudioSignal.HasAudibleSignal(Take(i => (short)(i%7-3))));
  Assert.False(AudioSignal.HasAudibleSignal(Take(_ => 3000)));
  Assert.False(AudioSignal.HasAudibleSignal(Take(i => i == 2000 ? short.MaxValue : (short)0)));
 }
 [Theory][InlineData(200,1600)][InlineData(2000,16000)][InlineData(32000,16000)]
 public void AcceptsShortSoftAndLoudSignal(int amplitude,int count) => Assert.True(AudioSignal.HasAudibleSignal(Take(i => (short)(Math.Sin(i*Math.PI*440/8000)*amplitude),count)));
 [Fact] public void RejectsTruncatedAndNonPcmWithoutCrashingOrReadingPastEnd() {
  foreach (var bytes in new[] {Array.Empty<byte>(),Take(_ => 0)[..40],Take(_ => 0)[..100]}) Assert.Throws<ProviderException>(() => AudioSignal.HasAudibleSignal(bytes));
  var wav = Take(_ => 0); wav[34] = 8; Assert.Throws<ProviderException>(() => AudioSignal.HasAudibleSignal(wav));
 }
 [Fact] public void RealSpokenFixturePasses() => Assert.True(AudioSignal.HasAudibleSignal(File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory,"speech.wav"))));
}
