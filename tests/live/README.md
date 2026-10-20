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
