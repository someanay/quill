using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.IO.MemoryMappedFiles;
namespace Quill.Windows;
public sealed class SingleInstanceActivation : IDisposable {
 private readonly EventWaitHandle requested;
 private readonly Mutex mutex;
 private readonly string ownerName;
 private readonly MemoryMappedFile? owner;
 private RegisteredWaitHandle? listener;
 private int disposed;
 public bool IsPrimary { get; }
 internal int ActivationError { get; private set; }
 internal nint ActivationForeground { get; private set; }
 public SingleInstanceActivation(string folder) {
  using var identity = WindowsIdentity.GetCurrent();
  var scope = (identity.User?.Value ?? identity.Name) + "\n" + Path.TrimEndingDirectorySeparator(Path.GetFullPath(folder)).ToUpperInvariant();
  var name = "Local\\Quill.NativeV2." + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(scope)))[..24];
  ownerName = name + ".Owner";
  // Create the event before claiming the mutex. A second launch during cold startup can
  // signal it immediately; the request remains queued until the primary starts listening.
  requested = new EventWaitHandle(false,EventResetMode.AutoReset,name + ".Activate");
  mutex = new Mutex(false,name);
  try { IsPrimary = mutex.WaitOne(0); } catch (AbandonedMutexException) { IsPrimary = true; }
  if (IsPrimary) {
   owner = MemoryMappedFile.CreateOrOpen(ownerName,sizeof(int),MemoryMappedFileAccess.ReadWrite);
   using var view = owner.CreateViewAccessor(0,sizeof(int),MemoryMappedFileAccess.Write); view.Write(0,Environment.ProcessId); view.Flush();
  }
 }
 public bool RequestActivation() {
  var granted = false;
  // A foreground shortcut launcher can transfer its activation permission to this
  // specific existing instance. No global foreground permission is granted.
  for (var attempt = 0;attempt < 25;attempt++) {
   try {
    using var shared = MemoryMappedFile.OpenExisting(ownerName,MemoryMappedFileRights.Read);
    using var view = shared.CreateViewAccessor(0,sizeof(int),MemoryMappedFileAccess.Read);
    var processId = view.ReadInt32(0);
    if (processId > 0) { ActivationForeground = Native.GetForegroundWindow(); granted = Native.AllowSetForegroundWindow((uint)processId); ActivationError = granted ? 0 : System.Runtime.InteropServices.Marshal.GetLastWin32Error(); break; }
   } catch (FileNotFoundException) { /* The primary can still be claiming its startup resources. */ }
   catch (UnauthorizedAccessException) { break; }
   Thread.Sleep(10);
  }
  requested.Set(); return granted;
 }
 public void Listen(Action activate) {
  if (!IsPrimary || listener != null) throw new InvalidOperationException("Only the primary instance may listen once.");
  listener = ThreadPool.RegisterWaitForSingleObject(requested,(_,_) => { if (Volatile.Read(ref disposed) == 0) activate(); },null,Timeout.Infinite,false);
 }
 public void Dispose() {
  if (Interlocked.Exchange(ref disposed,1) != 0) return;
  listener?.Unregister(null); owner?.Dispose(); if (IsPrimary) mutex.ReleaseMutex(); mutex.Dispose(); requested.Dispose();
 }
}
