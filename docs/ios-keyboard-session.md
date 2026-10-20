# iOS keyboard recording session — October 20, 2026

Status: implemented and validated. Both complete native runtime suites, all four
installed-keyboard journeys, final Light/Dark visual review and package inspection
are complete. Physical-device and distribution gates remain separate below.
This is the M14 follow-up explicitly requested by the owner. Android, Windows,
Mac and unrelated website work remain outside this change.

## Platform findings

Apple's [custom keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)
does not give keyboard extensions microphone access. The containing app owns
capture. Apple's [audio-session documentation](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/record)
supports background recording with the audio background mode and microphone
permission. That does not allow a suspended or terminated app to be silently
woken by a keyboard command.

Apple's [AudioRecordingIntent documentation](https://developer.apple.com/documentation/AppIntents/AudioRecordingIntent)
requires a Live Activity for that protocol; it does not provide a cold background
microphone escape hatch. An [Apple DTS answer](https://developer.apple.com/forums/thread/816408)
also confirms the general background-activation privacy block. Quill does not
pretend to be a phone/VoIP app to bypass it.

Wispr's [session documentation](https://docs.wisprflow.ai/articles/4841123325-Longer-dictation-sessions-%E2%80%94-now-up-to-20-minutes)
describes a configurable iOS Flow Session timer, defaulting to five minutes.
Borrow the explicitly enabled live-session model. Quill must disclose that the
microphone remains active between takes; it cannot promise hardware microphone
privacy while simultaneously keeping background capture ready.

## Implemented interaction

1. Start a five-minute keyboard session visibly in Quill after key/mic setup.
2. Return to the destination app once and choose the Quill keyboard.
3. Tap Dictate, speak, then Stop dictation. Quill records/transcribes in the
   background; the keyboard inserts once into the unchanged original field.
4. Repeat without opening Quill or pressing Send/Insert between takes.
5. End the session from Quill or the keyboard. Expiry, interruptions, removed
   input routes and protected-data locking release the microphone. Restarting
   Quill never silently resumes an old session.

The explicit foreground start keeps a real microphone input engine alive.
Idle input buffers are discarded without files, provider calls or playback.
Only requested takes enter the existing 16 kHz recorder, durable recovery,
provider, cleanup and local-history pipeline. The OS microphone indicator stays
visible. There is no silent-audio playback, private host-app discovery, responder
chain URL trick, automatic app return or microphone use in the extension.

## Safeguards and verification gates

- A short live heartbeat and bounded lease prevent stale keyboard commands.
  Commands include session/capture IDs and a timestamp, with one pending command
  and replay refusal. The live session also has a monotonic deadline.
- App Group files contain opaque command/state IDs and the existing protected
  one-use transcript. Audio, credentials and destination text remain outside the
  command protocol. Files use complete protection and are excluded from backup.
- Automatic insertion also requires the original document identifier, selection
  and surrounding context, held only in keyboard memory. Changed/hidden fields
  require explicit insertion. Secure/phone fields and missing Full Access refuse
  dictation commands. No automatic replay follows extension/process restart.
- Provider/storage failure preserves the normal retry/cleanup behavior. Private
  mode and reset end the session and prevent late delivery.
- Clearing pending keyboard text also ends the live session and cancels a take
  already reaching its provider. A late response cannot republish cleared text.
- Core tests cover lease/command/anchor invariants. Native tests cover actual
  idle input, two real recorder takes, protected file coordination and keyboard
  auto-insertion/refusal. Installed-keyboard tests require two actual background
  dictations into a separate UIKit app, exact editor readback, no host foreground
  activation, consume-once and keyboard End, on iPhone/iPad baseline/current OS.
- Full native host/keyboard tests, recorder tests, phone/tablet UI screenshots,
  Light/Dark layouts, and device/simulator Release package inspection are required.

Simulator acceptance cannot establish physical-device battery use, audio quality,
lock behavior, call/audio-route reliability, system termination, App Store review,
signing or live-provider transcription quality. Those remain physical/account gates.

## Reproducible acceptance

The producing app/test revision is `0cf609096dfa1892d03cff3804212fa80d77a9e5`
on `codex/platform-rebuild`. Follow-up documentation commits do not change it.
All three workflows run native Xcode/Simulator work on GitHub-hosted Macs:

- [Baseline app, recorder and iPad suites](https://github.com/someanay/quill/actions/runs/39429885216).
- [Current app, recorder and iPad suites](https://github.com/someanay/quill/actions/runs/39429888679).
- [Four installed-keyboard journeys](https://github.com/someanay/quill/actions/runs/39429892511).

Each runtime gate requires 56 portable core, 55 host/keyboard logic, eight actual
recorder, thirteen iPhone UI and thirteen iPad UI tests. The separate keyboard
matrix adds one complete installed-extension journey per device/runtime. A
blocked, skipped or incomplete journey is rejected rather than counted as passed.
The STT response alone is synthetic: microphone input, recorder files, saved
Keychain setup, system keyboard installation, Full Access, the App Group and
separate native editor are real. Exact editor strings and their Unicode scalar
readbacks must match after both background takes and after End.

Two observed CI setup differences were corrected without bypassing the flow:

- A cold iOS 26 Preferences launch had no accessibility main window. CI now
  launches the real Preferences app after simulator boot and before the build;
  keyboard enablement and Full Access still happen through actual Settings UI.
- Simulator storage did not expose the physical file-protection readback
  attribute. Native tests now inspect the actual atomic/complete-protection
  Foundation write options and full coordinated lifecycle; physical devices
  additionally assert the protection attribute itself.

Original outcomes, exact readbacks, manifests, screenshots and downloaded
packages are kept under the ignored `dist/ios-keyboard-session/final/` folder.

The installed-keyboard matrix passes all four complete journeys with no skipped
or blocked outcomes:

| Runtime | Device | Native journey |
| --- | --- | --- |
| iOS 18.5 / Xcode 16.4 | iPhone 16 Pro | Passed |
| iOS 18.5 / Xcode 16.4 | iPad Pro 11-inch (M4) | Passed |
| iOS 26.4.1 / Xcode 26.6 | iPhone 17 Pro | Passed |
| iOS 26.4.1 / Xcode 26.6 | iPad Pro 13-inch (M5) | Passed |

All 68 original installed-keyboard screenshots are reviewed in eight contact
sheets, including actual Light/Dark keyboards, foreground session enablement,
protected fields, both automatic background insertions and End. The aggregate
`keyboard-summary.json` verifies six exact Unicode-scalar readbacks per journey,
including unchanged final text after End; all four downloaded artifact hashes
match GitHub's digests. These editor fixtures contain only synthetic text.

## Final full-suite results

Both native jobs finish successfully, including their release archives and
artifact uploads. Counts below are per runtime: host/recorder tests execute on
the phone destination, and the separate UI pass executes on the tablet.

| Suite | iOS 18.5 | iOS 26.4.1 |
| --- | ---: | ---: |
| Portable core on the Mac runner | 56 passed | 56 passed |
| Native host/keyboard logic | 55 passed | 55 passed |
| Actual simulator recorder | 8 passed | 8 passed |
| iPhone UI walkthroughs | 13 passed | 13 passed |
| iPad UI walkthroughs | 13 passed | 13 passed |

These are 290 passing full-suite executions plus the four installed-keyboard
journeys, with zero failures or skipped tests in the required suites. The
machine-readable records are `ci-jobs.json`, `baseline-tests.json`,
`current-tests.json` and `keyboard-summary.json` under the local evidence folder.

The eight recorder tests include actual idle microphone input without an audio
file, two valid 16 kHz takes under one input session, expiry and interruption
shutdown, and a real take whose late synthetic provider response cannot deliver
after clearing. The keyboard journeys assert `.runningBackground` before,
during and after both takes, then exact unchanged text after End. Neither host
activation nor manual Send/Insert is used to make those background takes pass.

Local verification also passes 56 portable tests on Windows, Swift parsing of
all 49 iOS sources, bundled copy/font and format checks, and all 100 native text
contrast pairs. These supplement the native execution above.

## Final visual and package review

Each runtime contributes 109 named phone captures, 87 tablet captures and two
actual live-session panel renders. Together with the 68 installed-keyboard
captures, all **464 images** are reviewed in **46 contact sheets**. This includes
every settings destination, six onboarding stages, provider forms/footers,
privacy/legal/diagnostics, history, largest text and landscape in both appearances.
The 18 new keyboard state renders per runtime cover portrait/landscape ready,
recording, processing and failure, plus compact pending insertion in Light/Dark.

Session controls retain Quill's Plex fonts, cobalt and paper palettes. Copy
teaches the iOS timed microphone session and keyboard controls. Pending text
offers one primary Insert action; the inactive Dictate action is hidden. Native
scrolling keeps long forms and large text reachable. No new layout defect was
found in the final captures. The local gallery is
`dist/ios-keyboard-session/final/review-index.md`; its indexes link every original.

Both device and simulator packages match the producing checkout: 620 strings at
revision 23 in host and keyboard, exact Android font bytes, native usage
descriptions, matching bundle identities, arm64 binaries and minimum iOS 17.
Both unsigned device Release archives exclude DEBUG provider/presentation
fixtures. The simulator packages are test builds. All ten downloaded GitHub
artifacts match their upstream SHA-256 digests; detailed inspection JSON is saved
beside each inner package.

| Inner package | SHA-256 |
| --- | --- |
| Baseline unsigned device | `ac71425a33b1a6e5878bb9a37248a148aa423750156f34c6284615a3d5de94d1` |
| Baseline simulator | `139de4b9b7c2d67fc2f51780e060f41cd16cd637a0b5b855906a5919c346e9b0` |
| Current unsigned device | `b1c584b27b74ef11132002fad81e8f70b9675d1b3637b9e855015f808df85b9d` |
| Current simulator | `e65b90ec6787275cd8dc4422547e3e7fe2395280e2c8fe225c8877b7abe86881` |

This removes repeated app switching during an enabled session and makes the
iOS workflow substantially more usable. It still needs one foreground start per
session and keeps the microphone indicator active until End or expiry. Physical
audio, battery/heat, lock/call/input-route behavior, external keyboards, supported
third-party apps and live-provider quality remain device acceptance work. Signing
and Store review are account/distribution work; the unsigned device archive is
build evidence, not an installable signed release. iOS 17 is a deployment target,
not an additionally executed runtime in this matrix.
