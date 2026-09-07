using System.Windows;
using Quill.Windows;
using Xunit;
namespace Quill.Windows.Tests;
public sealed class ClipboardTransactionTests {
 private sealed class DeferredData : IDataObject {
  public Dictionary<string,object?> Values { get; } = []; public Action? BeforeGet;
  public object GetData(string format,bool autoConvert) { BeforeGet?.Invoke(); return Values.GetValueOrDefault(format)!; }
  public object GetData(string format) => GetData(format,false); public object GetData(Type format) => GetData(format.FullName!);
  public bool GetDataPresent(string format,bool autoConvert) => Values.ContainsKey(format); public bool GetDataPresent(string format) => Values.ContainsKey(format); public bool GetDataPresent(Type format) => GetDataPresent(format.FullName!);
  public string[] GetFormats(bool autoConvert) => Values.Keys.ToArray(); public string[] GetFormats() => GetFormats(false);
  public void SetData(string format,object data,bool autoConvert) => Values[format] = data; public void SetData(string format,object data) => SetData(format,data,false); public void SetData(Type format,object data) => SetData(format.FullName!,data); public void SetData(object data) => SetData(data.GetType(),data);
 }
 private sealed class TestClipboard : IClipboardAccess {
  public uint Sequence { get; set; } = 1; public IDataObject? Current { get; set; } public int Writes; public Exception? RestoreFailure; public int FailingWrites = int.MaxValue;
  public IDataObject? Read() => Current; public object? GetData(string format) => Current?.GetData(format,false);
  public void Write(IDataObject data) { Writes++; if (Writes > 1 && RestoreFailure != null && FailingWrites-- > 0) throw RestoreFailure; Current = data; Sequence++; }
  public void Clear() { Current = null; Sequence++; }
 }
 [Fact] public void UnrenderableAdvertisedClipboardFormatRefusesTransaction() { var data = new DeferredData(); data.Values[DataFormats.UnicodeText] = "original"; data.Values["delayed custom format"] = null; var clipboard = new TestClipboard { Current = data }; Assert.Null(ClipboardLease.TryCreate("new",clipboard)); Assert.Same(data,clipboard.Current); Assert.Equal(0,clipboard.Writes); }
 [Fact] public void CopyDuringDelayedRenderingCannotBeOverwritten() { var data = new DeferredData(); data.Values[DataFormats.UnicodeText] = "original"; var newer = new DataObject(DataFormats.UnicodeText,"newer"); var clipboard = new TestClipboard { Current = data }; data.BeforeGet = () => { clipboard.Current = newer; clipboard.Sequence++; }; Assert.Null(ClipboardLease.TryCreate("new",clipboard)); Assert.Same(newer,clipboard.Current); Assert.Equal(0,clipboard.Writes); }
 [Theory][InlineData("io")][InlineData("serialization")][InlineData("invalid-operation")][InlineData("not-supported")]
 public async Task RestoreFailureCannotTurnConfirmedDeliveryIntoRetry(string failure) { var clipboard = new TestClipboard { Current = new DataObject(DataFormats.UnicodeText,"original"),RestoreFailure = failure switch { "io" => new IOException(),"serialization" => new System.Runtime.Serialization.SerializationException(),"invalid-operation" => new InvalidOperationException(),_ => new NotSupportedException() } }; var waits = new List<TimeSpan>(); var lease = ClipboardLease.TryCreate("dictated",clipboard,duration => { waits.Add(duration); return Task.CompletedTask; }); Assert.NotNull(lease); lease.Dispose(); await lease.Restoration; Assert.Null(clipboard.Current); Assert.Equal(TimeSpan.FromSeconds(60),TimeSpan.FromTicks(waits.Sum(t => t.Ticks))); var writes = clipboard.Writes; lease.Dispose(); Assert.Equal(writes,clipboard.Writes); }
 [Fact] public async Task TransientRestoreFailureRetriesOriginalSnapshot() { var clipboard = new TestClipboard { Current = new DataObject(DataFormats.UnicodeText,"original"),RestoreFailure = new IOException(),FailingWrites = 1 }; var lease = ClipboardLease.TryCreate("dictated",clipboard,_ => Task.CompletedTask)!; lease.Dispose(); await lease.Restoration; Assert.Equal("original",clipboard.GetData(DataFormats.UnicodeText)); Assert.Equal(3,clipboard.Writes); }
 [Fact] public async Task RetryAndExpiryNeverOverwriteANewerClipboardOwner() { var clipboard = new TestClipboard { Current = new DataObject(DataFormats.UnicodeText,"original"),RestoreFailure = new IOException() }; var lease = ClipboardLease.TryCreate("dictated",clipboard,_ => { clipboard.Current = new DataObject(DataFormats.UnicodeText,"newer"); clipboard.Sequence++; return Task.CompletedTask; })!; lease.Dispose(); await lease.Restoration; Assert.Equal("newer",clipboard.GetData(DataFormats.UnicodeText)); Assert.Equal(2,clipboard.Writes); }
 [Theory][InlineData(0,0)][InlineData(1,1)][InlineData(2,2)][InlineData(3,1)][InlineData(4,0)]
 public void PartialPasteDispatchBalancesOnlyItsOwnKeysWithoutResendingPaste(int queued,int releases) { var batches = new List<Native.INPUT[]>(); var started = NativeInputDispatch.QueuePaste(batch => { batches.Add(batch); return batches.Count == 1 ? (uint)queued : (uint)batch.Length; }); Assert.Equal(queued > 0,started); Assert.Equal(4,batches[0].Length); if (releases == 0) Assert.Single(batches); else { Assert.Equal(2,batches.Count); Assert.Equal(releases,batches[1].Length); Assert.All(batches[1],input => Assert.Equal(2u,input.union.keyboard.flags)); Assert.Equal((ushort)0x11,batches[1][^1].union.keyboard.virtualKey); } }
}
