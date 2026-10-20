# Live recording, transcription, cleanup and insertion

October 20, 2026. Owner-authorized M14 follow-up using the existing Gemini and
OpenRouter keys. Status: native live-provider acceptance is in progress.

The installed NativeV2 Windows store was empty. Both original encrypted
`Quill/secrets/{provider}.bin` keys unlock under the owning Windows account and
authenticate successfully against their providers. No API key appears in source,
normal tool output, launch arguments or evidence artifacts. Apple/Windows CI
uses temporary encrypted Actions secrets, to be removed after the last jobs.

The first real Windows requests establish successful transcription on both
accounts. They also expose actual failures absent from synthetic HTTP tests:

- Gemini returned one 503 overload, followed by successful transcripts. Some
  transcription requests are slow. Cleanup exceeded its existing four-second
  deadline; this is recorded as a failed cleanup, not a success via raw fallback.
- The OpenRouter free router returned both a rewrite adding an unspoken word
  (`email`) and an unrelated safety-classifier answer. It also timed out. The
  latter answer was already rejected; the former exposed a permissive overlap
  guard that needed correction.
- The rebuilt Windows app did not import the older installation's saved keys.
  A non-destructive, once-only migration now preserves existing new-store keys,
  leaves the old encrypted files intact and does not resurrect keys after Clear.

All three native cleanup guards now reject new words after their existing
contraction normalization. Existing overlap/duplication limits still apply.
The observed added-word response is a shared regression fixture. Targeted local
Windows storage/domain tests pass 254 checks with no skips. Native Gemini Flash
requests now ask for documented minimal reasoning to avoid unnecessary latency;
the wall-clock deadline and safe fallback remain in place. Actual cleanup on the
existing Gemini account took 13.266 seconds in a separate latency diagnostic,
so the original four-second budget prevented completion even for a valid reply.
The three apps now allow a bounded 25 seconds for optional cleanup. A subsequent
native-endpoint diagnostic took 18.237 seconds, and a real Mac compatibility call
also exceeded 15 seconds. These are provider latency observations, not claims of
instant dictation. Cancellation
still stops immediately; a timeout or rejected response retains the raw words.

Explicit Gemini 503 overload responses receive at most two retries of the exact
same request, under the original shared deadline with one-/two-second backoff.
No provider/model is silently changed, and no authentication/quota failure is
retried. Cancellation during backoff prevents another send. The live matrix
serializes cases within each platform to avoid bursting the owner's accounts.

The free router is replaced as the automatic OpenRouter cleanup default by
`liquid/lfm-2.5-2.6b:free`, a general text model in the actual current free catalog.
The real production client accepts its cleanup control after 13.622 seconds;
both tested Gemma free alternatives return 429 and are not selected. Explicit
saved manual model selections remain unchanged. The router's random selection
is documented by [OpenRouter](https://github.com/OpenRouterTeam/docs/blob/main/guides/routing/routers/free-router.mdx).

The manual-only `live-providers.yml` workflow exercises live HTTPS providers and
exact output readback from disposable editor fields. Windows CI supplies a
synthetic WAV at the recorder interface, then uses the real DictationController,
ProviderClient, encrypted store and cross-process TextInjector. Apple CI routes
the same synthetic speech through a Core Audio loopback device into the actual
native recorder. macOS uses the real Dictation and TextEdit; iOS uses the host,
real Keychain, installed keyboard and a separate UIKit editor, including repeated
dictation with the host remaining in the background. No HTTP result or insertion
is mocked. Virtual audio input is distinct from physical microphone fidelity.

Final producing revisions, jobs, results, fixes and remaining device/provider
limitations will be recorded here after the live runs finish.

The final local client gate passes 313 targeted Windows storage/domain/provider/
pipeline checks, all 59 iOS portable core tests and all 35 Mac portable core
tests. The new transport tests verify exact-body overload retry, cancellation
during backoff and a shared resource deadline. Earlier local failures from
outdated four-second assertions were corrected to test the measured new budget.
No full native or live-provider pass is inferred from these portable tests.
