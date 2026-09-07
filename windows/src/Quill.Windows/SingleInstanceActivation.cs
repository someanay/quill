using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
namespace Quill.Windows;
public sealed class SingleInstanceActivation : IDisposable {
 private readonly EventWaitHandle requested;
 private readonly Mutex mutex;
 private RegisteredWaitHandle? listener;
 private int disposed;
 public bool IsPrimary { get; }
 public SingleInstanceActivation(string folder) {
  using var identity = WindowsIdentity.GetCurrent();
  var scope = (identity.User?.Value ?? identity.Name) + "\n" + Path.TrimEndingDirectorySeparator(Path.GetFullPath(folder)).ToUpperInvariant();
  var name = "Local\\Quill.NativeV2." + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(scope)))[..24];
  // Create the event before claiming the mutex. A second launch during cold startup can
  // signal it immediately; the request remains queued until the primary starts listening.
  requested = new EventWaitHandle(false,EventResetMode.AutoReset,name + ".Activate");
  mutex = new Mutex(false,name);
  try { IsPrimary = mutex.WaitOne(0); } catch (AbandonedMutexException) { IsPrimary = true; }
 }
 public void RequestActivation() => requested.Set();
 public void Listen(Action activate) {
  if (!IsPrimary || listener != null) throw new InvalidOperationException("Only the primary instance may listen once.");
  listener = ThreadPool.RegisterWaitForSingleObject(requested,(_,_) => { if (Volatile.Read(ref disposed) == 0) activate(); },null,Timeout.Infinite,false);
 }
 public void Dispose() {
  if (Interlocked.Exchange(ref disposed,1) != 0) return;
  listener?.Unregister(null); if (IsPrimary) mutex.ReleaseMutex(); mutex.Dispose(); requested.Dispose();
 }
}
