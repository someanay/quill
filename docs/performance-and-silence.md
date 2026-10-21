# Native responsiveness and quiet-input rejection — October 21, 2026

## Changes

Windows no longer rebuilds its styles/templates during unrelated settings edits.
Frozen brushes are reused, audio UI updates are coalesced, and the floating
control rebuilds its glyph/gradient only when its presentation changes. Its focus
timer and animations stop while hidden; the example animation pauses while its
window is minimized. The practice editor has a direct, foreground-checked native
capture path, preserving exact text/selection checks before insertion.

Page reveals last 140 ms and press feedback lasts 90–120 ms. Windows Appearance
offers Match system, Full and Reduced animations. Full is enabled on the owner's
installation; the OS accessibility preference was not changed. Apple shared
buttons/pages use restrained native transitions and honor Reduce Motion.

The Windows workspace reserves a compact status/action area. Recording no
longer pushes Stop below the viewport. Recovery actions precede a bounded,
scrollable transcript preview. The editor's live text and selection stay intact.

Mac microphone samples publish to a separate meter object: pages observing the
session are not invalidated by every sample. Floating windows are not raised or
replaced for every audio tick. Unchanged permission results no longer republish,
and the recording pulse only runs during recording. iOS/iPadOS publish one
meter sample per tick and load/analyze recorded files away from the main actor.
Unchanged iOS history refreshes no longer republish the entire list.

Android awaits its first-run preferences in a lifecycle coroutine rather than
blocking the launch thread. The onboarding gate still precedes Home composition.
Existing Compose page motion now takes 140–160 ms instead of 220–260 ms; button,
meter and bubble feedback retain the accepted design and system animation scale.

## Silence contract

Every user recording path checks finalized PCM before transcription, cleanup or
delivery: desktop dictation/clipboard/recovery, Apple voice-word capture, iOS
background keyboard takes, Android overlay/retry and Android/Windows custom-word
capture. Quiet takes produce no inserted text, clipboard content or history
entry. Audio is discarded through each platform's existing cleanup path.
Windows and Apple retain their explicit storage-error and cleanup-retry behavior.

The common policy parses PCM WAV chunks, removes DC offset per 20 ms frame,
requires sustained audible energy and rejects isolated clicks. Longer takes
also need a sustained rise over their own noise floor, rejecting steady
microphone noise and hum. Short syllables retain the absolute signal floor
because a short clip cannot supply a reliable background-noise estimate.
No vocabulary blacklist removes legitimate phrases such as “thank you.”

This is a small local signal check, not a neural speech classifier. Variable
music, background conversations and other speech-like sound can still pass.
Generative transcription prompts also request empty output for nonspeech.
Credential-validation probes are intentionally separate: their response is
never inserted or saved as a dictation.

Silence is not offered as saved audio to retry. On iOS the keyboard returns to
Dictate with the no-speech explanation, retaining the explicit microphone
session so another take does not require switching apps.

## Measurements and regressions

| Check | Evidence |
|---|---|
| 100 unchanged Windows theme applications, old Debug build | 114.10 ms; 41,503,424 allocated bytes |
| Same Debug configuration after caching | 0.01 ms; 40 allocated bytes |
| Five-minute quiet PCM, Windows Release | 7.83 ms, off the UI thread in production |
| Own practice-field capture, x64 / ARM64 CI | 1.17 / 0.97 ms on the first native regression run; exact Unicode insertion, stale caret and changed field refusals pass |
| Mac meter isolation | 100 meter publications; zero session/page publications |
| Installed Windows, settled Home with Full motion | Zero sampled CPU time over 10 seconds; this is an idle observation, not an end-to-end latency claim |

These changes keep the interface responsive while speech/cleanup requests run;
they do not remove provider processing time. The existing bounded deadlines and
cancellation protections remain. Physical device latency and acoustic tuning
still need hands-on acceptance.

Fixtures cover silence, DC offset, dither, a click, steady noise/hum, short and
soft/loud signals, malformed WAVs and actual synthesized speech. Pipeline tests
also assert zero provider calls, no insertion/clipboard/history and no retry
file after a quiet take, including recovered Windows audio. Android
custom-word tests prove silence is rejected before credentials and real speech
still reaches the credential gate.

## Validation record

Final Apple production sources are `4c6be93`; the Windows custom-word follow-up
is `719b69b`, and Android's startup follow-up is `b457211`. The `984b58d` follow-up
changes iOS recorder tests and CI audio setup, with no Apple production change.

| Platform | Run | State |
|---|---|---|
| Windows x64 + ARM64, final custom-word follow-up | [39576774587](https://github.com/someanay/quill/actions/runs/39576774587) | Passed: 394 tests per architecture; all native rendering, editor and installer gates |
| Windows signal/toolbar matrix | [39574056612](https://github.com/someanay/quill/actions/runs/39574056612) | Passed: 394 tests per architecture, native editor/installer checks, 300 pages + 60 session states, 42 setup layouts and 288 example-animation frames per architecture |
| Android, custom-word follow-up | [39576774543](https://github.com/someanay/quill/actions/runs/39576774543) | Passed; 411 tests, lint and release APK/AAB build |
| Android, startup follow-up | [39579030215](https://github.com/someanay/quill/actions/runs/39579030215) | Passed: 411 tests, lint and release APK/AAB build; local debug build also passed |
| Mac baseline/current | [Baseline](https://github.com/someanay/quill/actions/runs/39574056701), [current](https://github.com/someanay/quill/actions/runs/39574056528) | All four Mac configurations passed: 48 native tests, 13 UI tests, 41 portable-core tests, universal release packaging |
| iOS full baseline/current, recorder-fixture follow-up | [Baseline](https://github.com/someanay/quill/actions/runs/39579976461), [current](https://github.com/someanay/quill/actions/runs/39579979171) | Passed: 65 portable-core, 59 native, nine real-recorder, 16 iPhone UI and 16 iPad UI tests per runtime; entitlements and unsigned device/simulator archives |
| Mac external editors | [39574056618](https://github.com/someanay/quill/actions/runs/39574056618) | Passed all eight TextEdit/browser/secure-field jobs across four configurations |
| Installed iPhone/iPad keyboard | [39574056697](https://github.com/someanay/quill/actions/runs/39574056697) | Passed all four baseline/current iPhone/iPad journeys, including background silent takes |

The two mixed-platform Apple runs linked for Mac passed every Mac job. Their
iOS recorder-cancellation fixture failed because it recorded silence while
expecting a provider request; the complete iOS follow-up runs above supersede
those iOS results. Both full follow-ups pass iPhone/iPad UI, native and
real-recorder stages. Their final artifacts and captures have been reviewed.

The installed keyboard test now plays fixed speech through the runner's actual
BlackHole/AVAudioEngine input route. A separate silent background take must leave
the external editor unchanged, return to Dictate and keep the host backgrounded.
The provider transport is synthetic in this regression; it is not described as
a new live-provider result. Earlier actual HTTPS provider results remain in
[live-provider validation](live-provider-validation.md).

The previous empty-microphone fixture correctly failed after adding the local
guard. It was repaired by supplying actual input, without bypassing the guard.
A Mac UI test also caught a corrupted replacement arrow during editing; the
UTF-8 source was corrected and its original assertion retained.

The first Windows ARM attempt failed to produce a fresh-window diagnostic and
its external UI Automation capture timed out despite the fixture owning the
foreground HWND. An identical-source rerun on a fresh hosted desktop passed all
394 tests and all editor/layout checks. No timeout, safety assertion or test was
removed. The full iOS recorder cancellation test likewise required actual speech
input after quiet recordings stopped reaching providers; its separate real
quiet-input regression checks zero provider calls and a reusable keyboard session.

## Visual review

The accepted reference remains [the native design review](native-design-review.md):
Android's IBM Plex typography, restrained blue accent and light/dark palette,
with native navigation, controls and OS-specific copy.

These are WPF, SwiftUI and UIKit applications. WPF offscreen rendering,
XCTest screenshots and a brief Sky review of the installed Windows app provide
rendered evidence; browser/Playwright rendering would not exercise these native
surfaces. Review covers hierarchy/type, spacing, control contrast, copy/shortcut
consistency, responsive layout, reachable primary actions and animation phases.

Reviewed: all 56 Windows page/footers at 1200×800 in both themes, all 60
Windows session-state captures across six aspect ratios, and all 127 Mac captures
from the first passing final-layout run. The final signal-only Apple change
does not alter those Mac presentation sources. The current iPhone suite's 141
captures and all 80 baseline/current iPhone/iPad keyboard captures were
also reviewed, together with all 234 final iPad app captures (117 per runtime).
No new material visual defect was found in those captures. Intentional changes
from the accepted design are the compact Windows recording toolbar and motion
feedback. No above-the-fold marketing
copy, palette or typography was redesigned.

Original-size iPhone Home/history-reader and iPad quiet-keyboard images were
also inspected. All literal native copy references resolve (267 Windows,
237 Mac, 292 iOS); light/dark text contrast checks pass for 28 Windows, 24 Mac,
38 iOS and 10 keyboard foreground/background pairs.

All four final Mac ZIPs match their bundled checksums, contain both x86_64 and
arm64 Mach-O slices, and preserve executable mode 0755. Downloaded verification
ZIPs were compared with GitHub's SHA-256 digests. A local exact-key scan found
no owner plaintext keys in 5,193 source/evidence files and 9,542 ZIP entries.
The final baseline/current iOS device and simulator archives contain ARM64 app/keyboard executables,
both device families and the keyboard-service extension. The unsigned device
archive contains no XCTest bundles. Simulator artifacts intentionally include
test frameworks for verification.

## Installed app and remaining device gates

Windows 2.0.5 is installed under the normal per-user Programs directory with its
Start shortcut and Installed apps registration. After the owner quit Quill, the
final `719b69b` custom-word follow-up was applied through the CLI installer with
launch disabled. Its installed product version is
`2.0.5+719b69b26b9c2f0293181dea6322dd94c10e8d1b`. All four existing native data
files remained byte for byte unchanged. Both active provider keys compare equal
to the original owner credentials, and the original settings file is unchanged.
Verification revealed no credentials. Alt + E, insertion consent and Full motion
were preserved; Quill remains closed.

The installed package ZIP SHA-256 is
`df8a04d9b9966350b5059202b1657d13c73cf6fec7bc1bb9fd984304c34c6575`.
An earlier CLI attempt refused while Quill was running and changed no files.
No desktop input or process termination followed the owner's Escape stop.

The installed `4c6be93` quiet-take check used Alt + E and the current default
DroidCam input. It showed “No speech detected,” left the practice field empty,
created no history row and retained no retry audio. Full motion remains enabled.
The owner deferred providing a physical microphone and speaking; that voiced
Windows microphone-to-insertion acceptance test remains pending.

No Android device is connected. Android physical overlay/microphone behavior
and physical iPhone/iPad/Mac acceptance are not claimed from CI. Distribution
signing/notarization and real-device interruption/Bluetooth/background checks
remain release gates. These improvements support a more responsive beta; CI
alone does not establish public-release readiness.
