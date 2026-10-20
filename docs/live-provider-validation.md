# Live recording, transcription, cleanup and insertion

October 20–21, 2026. Owner-authorized M14 follow-up using the existing Gemini and
OpenRouter keys. Available live-provider verification is complete: all three
native platforms have accepted real transcription, cleanup and exact insertion.
All four selected iPhone/iPad baseline/current journeys pass. Provider outages,
rejected cleanup, raw fallback and failed fixture attempts remain recorded below.

The installed NativeV2 Windows store was empty. Both original encrypted
`Quill/secrets/{provider}.bin` keys unlock under the owning Windows account and
authenticate successfully against their providers. No API key appears in source,
normal tool output, launch arguments or evidence artifacts. Apple/Windows CI
used temporary encrypted Actions secrets, now deleted with absence verified.

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

All three native cleanup guards now reject new words and loss of spoken content
after contraction normalization. Existing overlap/duplication limits still apply.
Obvious filler sounds and the existing initial hesitant "like you know I/we..."
case remain removable. Live output that dropped a spoken "thank you" exposed
the additional omission defect. Four new negative cases protect that ending,
names, negations and amounts. Prompts explicitly keep those words. The earlier
successful-delivery marker for the omitted ending does not establish acceptable
cleanup quality. The shared corpus now has 200 cases; all 243 Windows domain
checks and both full portable Apple suites pass. Native Gemini Flash
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
retried. Cancellation during backoff prevents another send. Each live job
serializes its takes to avoid bursting the owner's accounts.

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

## Producing revisions and evidence

Application fixes are at `2be2e97ab1e1aa599aaa6ca10b1f4f2495d6fd6c`.
Subsequent commits separate live/regression concurrency and correct the iOS live
test invocation. The edited `.xctestrun` is now selected explicitly, as described
in [Apple's command-line testing documentation](https://developer.apple.com/library/archive/technotes/tn2339/_index.html).
An earlier iPad journey passed its UI assertions using ordinary synthetic HTTP;
the missing live audit correctly failed its acceptance step. It is not counted
as a live pass. A separate earlier iPhone run lost the Settings process before
keyboard setup. Original failure artifacts remain available.

The synthetic source is `tests/live/speech.wav` with SHA-256
`9b06c14fd46e7c6c5588b8243924d93483297022f1d44c704507aae2dcd1f672`.
It says: “Um please send the updated project notes to Alex tomorrow and include
the budget of forty two dollars. Thank you.” Only this generated voice and
disposable editor contents are sent to providers.

| Gate | Execution and observed result |
| --- | --- |
| Windows x64 / ARM64 regression | [39472659705](https://github.com/someanay/quill/actions/runs/39472659705): both successful; **370 tests each, zero skipped**, 14 real editor checks, 42 setup renders, 300 layout renders and 11 installer checks per architecture |
| Windows live, production insertion order | [39473217545](https://github.com/someanay/quill/actions/runs/39473217545): x64 has five accepted real transcription/cleanup/exact-insertion takes across all three provider combinations; one repeated Gemini take fails on three 503 responses. ARM64's initial refusals are subsequently traced to the runner's Windows Terminal taking focus. The overall run is failed. |
| Full Apple regression | [baseline](https://github.com/someanay/quill/actions/runs/39472663018), [current](https://github.com/someanay/quill/actions/runs/39472666468): both iOS jobs pass; Mac UI passes, with a stale native metadata-test timing assertion corrected and verified separately below |
| Mac live, final application revision | [39473221589](https://github.com/someanay/quill/actions/runs/39473221589): **18/24 accepted takes** across four configurations; macOS 26 Intel passes all six. Other failures and the corrected reader ambiguity are detailed below. |
| iPhone / iPad live, synchronized recording | [39482915832](https://github.com/someanay/quill/actions/runs/39482915832): baseline iPhone passes four takes, eight successful provider stages and six exact readbacks; baseline iPad passes two foreground takes but fails its first background take. The overall parent is failed. |
| Current-runtime live | [39486551032](https://github.com/someanay/quill/actions/runs/39486551032): iPhone's first speech request exhausts all three 503 attempts, with no inserted text. iPad returns an empty transcript during practice and shows the actual “No speech was returned” error. Both jobs fail. |

### Accepted iPhone and iPad journeys

| Device/runtime | Provider | Passing job and exact evidence |
| --- | --- | --- |
| iPhone / iOS 18.5, Intel simulator host | Gemini | [39482915832](https://github.com/someanay/quill/actions/runs/39482915832), job `118838195127`: four recordings, eight successful stages, six readbacks; three 503s recover within the production retry bound. The parent remains failed because its original iPad job failed. |
| iPad / iOS 18.5, Intel simulator host | OpenRouter | [39491013455](https://github.com/someanay/quill/actions/runs/39491013455): four recordings, eight successful stages, six readbacks; whole job passes. |
| iPhone / iOS 26.4.1, Apple Silicon simulator host | OpenRouter, explicitly selected | [39494364804](https://github.com/someanay/quill/actions/runs/39494364804): four recordings, eight successful stages, six readbacks; whole job passes. Both earlier Gemini failures remain failures. |
| iPad / iOS 26.4.1, Apple Silicon simulator host | OpenRouter | [39494163186](https://github.com/someanay/quill/actions/runs/39494163186): four recordings, eight successful stages, six readbacks; whole job passes. |

Together these are **16 actual recordings, 32 successful provider stages in 35
HTTP attempts, eight background takes and 24 exact readbacks**. Every journey
includes two keyboard-started takes with the host staying backgrounded, protected
field refusal, manual re-insertion and End without duplicate delivery. All **68
captures** from these accepted journeys are reviewed, including both keyboard
appearances. Independent local aggregation rechecks editor equality against the
actual cleanup responses. Successful current-runtime speech takes 0.538–2.940
seconds and cleanup 8.530–12.370 seconds; this is functional proof, not instant
dictation or a broad speech-accuracy benchmark.

Windows follow-up [39474331901](https://github.com/someanay/quill/actions/runs/39474331901)
records six x64 successes and four ARM64 successes, but its diagnostic wrapper
performed an additional capture before insertion. A subsequent
[production-order ARM64 run](https://github.com/someanay/quill/actions/runs/39475794417)
removes that preceding capture and records automatic insertion for two
OpenRouter-only takes and one mixed-provider take. When the terminal takes focus,
the app correctly leaves the original field unchanged and offers Paste. The
[explicit recovery check](https://github.com/someanay/quill/actions/runs/39476625478)
then confirms one Gemini take through actual user-initiated Paste and three more
automatic takes (one Gemini, two OpenRouter), with exact readback. Its later
mixed-provider attempt fails on Gemini 503 overloads. These failed overall jobs
are preserved; individual successful takes do not turn them into passing runs.
The harness reports automatic insertion and explicit recovery separately, and
never removes the product's focus/context checks.

The final-application Mac 15 Apple Silicon live run records **four accepted
recorder → transcription → cleanup → exact TextEdit insertions**, covering
Gemini, OpenRouter and the mixed profile. Its additional Gemini cleanup times
out at 25 seconds and its last mixed take receives three 503s. Raw fallback on
the timeout is useful recovery but does not count as passing cleanup.

All four Mac configurations pass **35 portable core, 43 native and 11 UI tests**
when combining the original full UI runs with the corrected focused native
[baseline](https://github.com/someanay/quill/actions/runs/39474960184) and
[current](https://github.com/someanay/quill/actions/runs/39474963529) reruns.
The initial four native failures all use a test that assumed validation finished
before the new 503 backoff ended. The corrected assertion observes its terminal
state under a bounded five-second wait. Original failed jobs remain recorded.
The final iOS baseline and current jobs each pass **59 core, 55 native, eight
actual recorder, 13 iPhone UI and 13 iPad UI tests**.

An intermittent Intel Mac reader mismatch followed a failed request that left
an earlier test document untouched. Every fixture initially had identical
starting text. Because TextEdit opens documents asynchronously, the probe could
mistake that earlier field for the new one. This is the likely explanation of
the earlier mismatch, rather than a demonstrated product insertion defect.
Each take now has a unique filename and prefix. The
[corrected reader run](https://github.com/someanay/quill/actions/runs/39480458308)
confirms five exact insertions (two Gemini, two OpenRouter, one mixed), with no
mismatch. The last take fails on Gemini overload responses. Failure diagnostics
also retain the app's completed text and the actual editor value.

The Windows regression packages are version **2.0.4**. The owner's installed
2.0.3 app remains closed and has not been replaced during these checks. Earlier
inspection confirms its genuine per-user installation, Start shortcut and
Installed apps registration; opening an installer on each launch is unnecessary.

The baseline Intel-Mac iPhone live journey completes all four recordings through
the actual native recorder, Gemini transcription and Gemini cleanup. Its HTTP
audit contains **eight successful stages in 11 attempts**: three explicit 503
overloads recover through the bounded production retries. Successful speech
calls take 5.321–10.850 seconds, and cleanup takes 6.491–12.518 seconds. Exact
readback preserves Alex, $42 and “Thank you.” The two keyboard-initiated takes
complete while the host remains backgrounded, including the dark appearance.
Manual re-insertion, secure/phone-field refusal and End without duplicate
delivery also pass. All 17 real-flow captures are reviewed in light/dark.

The earlier live iOS failures exposed two more fixture problems: the 7.745-second
speech sample outlasted a fixed 7.5-second recording wait, and evidence collection
used a pre-install data-container path. Playback now acknowledges completion
before recording stops; collection resolves the actual current container and
retains an HTTP audit even when an assertion fails. It also preserves evidence
across the deliberate host cold launch. These changes do not weaken the strict
real-provider or exact-delivery gates. Original failed evidence is retained.

The original baseline iPad's two foreground recordings have successful OpenRouter speech,
accepted LFM cleanup and exact insertion, including manual re-insertion. Its
background attempt returns empty transcripts. The fixture logs expose localhost
IPv6 connection failures before falling back to the IPv4-only playback server;
the background take can reach its real silence auto-stop before speech arrives.
An unconditional later Stop tap can then start another empty take. This is the
likely fixture cause, supported by the passing corrected baseline run. The playback server now binds
both loopback families, and the test only presses Stop while it remains the Stop
control. IPv4, IPv6 and localhost health checks pass locally. Actual playback
start/finish timings are retained, and the original strict four-pair/exact-output
collector is unchanged. Focused reruns at `fce1083` are
[baseline iPad](https://github.com/someanay/quill/actions/runs/39491013455) and
[current iPhone](https://github.com/someanay/quill/actions/runs/39491019585) and
[current iPad](https://github.com/someanay/quill/actions/runs/39491289493).
The focused baseline iPad passes: **four actual recordings, eight successful
OpenRouter stages and six exact readbacks**, including both background takes and
End without duplicate delivery. Speech takes 0.966–2.884 seconds and LFM cleanup
takes 10.333–16.248 seconds. All 17 captures are reviewed, including the light/dark
keyboard and actual destination text.
The focused current-iPhone retry also fails its first speech stage on three
explicit Gemini 503s (20.086, 5.922 and 1.299 seconds). Its playback completes
without the prior IPv6 connection refusal, and the app presents its actual
provider error. Neither failed current-iPhone attempt establishes a successful
live current-runtime journey; the baseline iPhone proof and passing current
native/recorder/UI suites are separate evidence.

The `fce1083` current iPad still returns an empty practice transcript after the IPv6 fix.
The added playback timing reveals another fixture delay: `afplay` takes 13.581
seconds to play a 7.745-second sample, exceeding initial silence detection before
the speech begins. Baseline playback takes 9.071–10.304 seconds and its corrected
journey passes. Revision `d97cd7e` replaces per-take player startup with a prepared
native `AVAudioEngine`/`AVAudioPlayerNode` output graph. Two actual playback
durations must match the sample before the simulator test can proceed; this
compile/timing gate passes on the current Mac runner. The app's actual recorder,
silence detection, HTTPS requests and exact-delivery requirements are unchanged.
Both subsequent focused runs pass:
[current iPad](https://github.com/someanay/quill/actions/runs/39494163186) and an
explicit [current iPhone/OpenRouter case](https://github.com/someanay/quill/actions/runs/39494364804).
That alternate-provider case retains both original Gemini failures. The prepared
player's timing control measures 7.795 and 7.781 seconds for the 7.745-second clip;
all eight in-journey playbacks complete in approximately eight seconds. The
successful new recordings resolve the empty-input fixture failure.

Mac run [39469873471](https://github.com/someanay/quill/actions/runs/39469873471)
at the earlier `c4ae8e5` revision already records four actual loopback-recorder,
live HTTPS cleanup and exact TextEdit readback successes on Apple Silicon macOS
15, including both OpenRouter-only and mixed-provider profiles. Its Gemini-only
and Intel failures remain failures. Current-application acceptance is separate.

The pre-omission local client gate passed 313 targeted Windows storage/domain/provider/
pipeline checks. The final local gate passes all 243 Windows domain checks,
all 59 iOS portable core tests and all 35 Mac portable core
tests. The new transport tests verify exact-body overload retry, cancellation
during backoff and a shared resource deadline. Earlier local failures from
outdated four-second assertions were corrected to test the measured new budget.
No full native or live-provider pass is inferred from these portable tests.

## Package, privacy and readiness assessment

The Windows 2.0.4 x64/ARM64 packages, all four universal Mac packages and both
baseline/current iOS simulator and unsigned-device packages are downloaded and
inspected. **27 distinct downloaded artifact ZIPs** match GitHub's published
SHA-256 digests: 18 regression/live artifacts plus nine later iOS live artifacts,
including failed attempts. Device Release packages exclude the opt-in DEBUG live
transport/bootstrap hooks. Fonts, bundled copy, usage descriptions, executable
architectures and keyboard/App Group metadata pass inspection. Release
application sources are unchanged after `2be2e97`; subsequent fixes affect
test invocation, evidence capture and fixture synchronization.

The final native-source/evidence scan checks **1,291 files and 1,235 archive
entries**, finding no plaintext value of either existing API key. Both temporary
Actions secrets are deleted, with their absence verified by a subsequent API
read. The original encrypted Windows credential files remain in place. No
credential-bearing simulator container or live test bundle is exported.

My assessment is that all three apps are worth continuing and are suitable for
further beta testing. The native navigation, spacing, typography and responsive
layouts now form a coherent Quill family, with platform-specific setup and copy.
This pass reviews 116 unchanged Mac captures in both appearances, representative
Windows compact light/dark captures and the new actual iOS keyboard flow. The
preceding complete page/appearance review remains recorded in
[Apple validation](apple-validation.md) and
[the platform experience](platform-experience.md).

Windows has the strongest local installation and physical microphone evidence.
Its live-provider CI supplies audio at the recorder interface, so it proves
provider, pipeline and actual cross-process insertion together; the separate
physical microphone test is not a combined live hardware speech test. Mac and
iOS live CI exercise their actual recorders through virtual Core Audio input.
This establishes real audio capture, HTTPS and delivery, while physical audio
quality, noise, Bluetooth routes, calls, locking, sleep and battery behavior need
actual Apple hardware. The five-minute iOS session keeps the microphone active
and visible between takes; once started in Quill, repeated keyboard dictation
does not require switching back until the session ends or is interrupted.

I would not call these release-ready yet. Real free-provider latency/availability
is the largest demonstrated everyday usability weakness. Physical Apple
acceptance, distribution signing/notarization and Store acceptance also remain.
Passing simulators and unsigned archive inspection do not establish those gates.
No unrelated Android or website source is changed by this follow-up, and the
owner's installed Windows app and original encrypted credential files remain
intact.
