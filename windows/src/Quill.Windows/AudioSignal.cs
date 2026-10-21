using System.Buffers.Binary;
namespace Quill.Windows;

/// Reject empty/quiet PCM locally before a generative recognizer can hallucinate words.
/// This is an audible-signal floor, not a claim to distinguish speech from background TV.
public static class AudioSignal {
 public static bool HasAudibleSignal(byte[] wav) {
  var bytes = wav.AsSpan();
  if (bytes.Length < 12 || !bytes[..4].SequenceEqual("RIFF"u8) || !bytes.Slice(8,4).SequenceEqual("WAVE"u8)) throw new ProviderException("invalid_audio");
  int rate = 0, channels = 0, offset = -1, length = 0;
  for (int p = 12; p <= bytes.Length - 8;) {
   uint size = BinaryPrimitives.ReadUInt32LittleEndian(bytes.Slice(p+4,4));
   if (size > bytes.Length - p - 8) throw new ProviderException("invalid_audio");
   if (bytes.Slice(p,4).SequenceEqual("fmt "u8)) {
    if (size < 16 || BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(p+8,2)) != 1 || BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(p+22,2)) != 16) throw new ProviderException("invalid_audio");
    channels = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(p+10,2)); rate = BinaryPrimitives.ReadInt32LittleEndian(bytes.Slice(p+12,4));
   } else if (bytes.Slice(p,4).SequenceEqual("data"u8)) { offset = p+8; length = (int)size; }
   p += 8 + (int)size + (int)(size & 1);
  }
  if (rate is < 8000 or > 96000 || channels is < 1 or > 2 || offset < 0 || length % (channels*2) != 0) throw new ProviderException("invalid_audio");
  int frame = rate / 50, stride = channels*2, audible = 0, consecutive = 0, longest = 0;
  var levels = new List<double>(); double previous = 0, earlier = 0, peak = 0;
  for (int p = offset; p + frame*stride <= offset+length; p += frame*stride) {
   bool active = false; double level = 0;
   for (int channel = 0; channel < channels; channel++) {
    double sum = 0, squares = 0; int min = short.MaxValue, max = short.MinValue;
    for (int i = 0; i < frame; i++) { int sample = BinaryPrimitives.ReadInt16LittleEndian(bytes.Slice(p+i*stride+channel*2,2)); sum += sample; squares += sample*(double)sample; min = Math.Min(min,sample); max = Math.Max(max,sample); }
    // Remove DC offset; a disconnected device, tiny dither or a lone click isn't a dictation.
    double energy = Math.Max(0,squares/frame - Math.Pow(sum/frame,2));
    active |= energy >= 80*80 && max-min >= 320; level = Math.Max(level,Math.Sqrt(energy));
   }
   consecutive = active ? consecutive+1 : 0;
   longest = Math.Max(longest,consecutive);
   if (active) audible++;
   if (consecutive >= 3) peak = Math.Max(peak,Math.Min(level,Math.Min(previous,earlier)));
   levels.Add(level); earlier = previous; previous = level;
  }
  if (audible < 4 || longest < 3) return false;
  // Longer takes need a sustained rise over their own noise floor. This rejects
  // steady fans/hum/dither without a model, network request or UI-thread work.
  // Very short syllables retain the absolute floor without an unreliable noise estimate.
  if (levels.Count < 20) return true;
  levels.Sort(); return peak >= Math.Max(80,levels[(levels.Count-1)/5]*1.4);
 }
}
