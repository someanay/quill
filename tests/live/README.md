# Live provider acceptance

Run only by explicit owner request. The `live-providers.yml` workflow is manual;
fork and pull-request jobs never receive provider keys. Use short synthetic
speech and disposable editor fields. Never log authorization headers, serialize
keys into launch arguments or upload containers, credentials or XCTest bundles.

The owner authorized reuse of the existing Gemini and OpenRouter accounts.
Temporary encrypted Actions secrets are removed after the final jobs finish.
Every successful take must contain an actual 200 transcription response and an
actual accepted 200 cleanup response, followed by exact separate-editor readback.
A raw fallback, timeout, blocked permission or failed provider is not a passing
cleanup result. Preserve those outcomes before retrying.

Windows CI uses a finalized synthetic WAV at the recorder interface and the real
dictation controller, HTTP clients and cross-process UI Automation adapter. Apple
runners additionally route that speech through a real Core Audio loopback input
into the native recorder. This is a virtual audio device, not physical microphone
or handset fidelity acceptance. No HTTP response or insertion is mocked.

`speech.wav` is locally synthesized English, 16 kHz mono signed 16-bit PCM. It is
a test resource only, never included in any production application target.

Dispatch the existing Windows, macOS native-integration or iOS installed-keyboard
workflow on the tested branch with `live_providers=true`. Optional `live_runner`
and iOS `live_device`/`live_provider` inputs focus a follow-up without repeating
unrelated jobs. An explicit alternate-provider case never replaces a failed
provider's evidence. Ordinary push/PR
regressions keep synthetic HTTP and never consume the live credentials.

iOS must select the prepared `.xctestrun` explicitly, wait for loopback playback
to finish before stopping capture and resolve its actual app container after
testing. Its independent collector requires four real transcription/cleanup
pairs and six exact readbacks, including two keyboard-started takes while the
host stays in the background. Failed assertions still preserve the safe HTTP
audit. Windows distinguishes automatic insertion from explicit Paste recovery
after a foreground change; Mac takes use unique document names and prefixes.
The Mac stimulus uses a prepared native audio output engine: CI verifies two
actual playback durations before launching the simulator journey. Keep the
app's real recorder and silence detector unchanged; per-take player startup can
otherwise outlast initial silence detection on slower hosted machines.

Preserve each failed attempt before retrying. Inspect the screenshots and
provider timings, check artifact hashes, scan exported evidence for credential
values and remove the two temporary Actions secrets after the final live job.
The exact results and remaining hardware gates are in
[live-provider validation](../../docs/live-provider-validation.md).
