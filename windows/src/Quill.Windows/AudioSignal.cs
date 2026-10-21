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
  int frame = rate / 50, stride = channels*2, audible = 0, consecutive = 0;
  for (int p = offset; p + frame*stride <= offset+length; p += frame*stride) {
   bool active = false;
   for (int channel = 0; channel < channels; channel++) {
    double sum = 0, squares = 0; int min = short.MaxValue, max = short.MinValue;
    for (int i = 0; i < frame; i++) { int sample = BinaryPrimitives.ReadInt16LittleEndian(bytes.Slice(p+i*stride+channel*2,2)); sum += sample; squares += sample*(double)sample; min = Math.Min(min,sample); max = Math.Max(max,sample); }
    // Remove DC offset; a disconnected device, tiny dither or a lone click isn't a dictation.
    active |= squares/frame - Math.Pow(sum/frame,2) >= 80*80 && max-min >= 320;
   }
   consecutive = active ? consecutive+1 : 0;
   if (active) audible++;
   if (audible >= 4 && consecutive >= 3) return true;
  }
  return false;
 }
}
