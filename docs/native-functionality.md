# Native functionality verification — October 20, 2026

Status: the available functional matrix is complete and passing for all three
requested platforms. The completed iOS recording-session implementation and its
separate results are in [the session report](ios-keyboard-session.md). Physical
device, live-provider and distribution gates remain explicitly identified below.

The owner explicitly extended the task to functionally verify Windows, macOS and
iOS/iPadOS in addition to the design work. This pass exercises the existing
production implementations; unrelated website work remains outside its scope.
Fresh Windows/Mac runs use `abaac971db8439410ff44c354fdb4fa8de077cf7` on
`codex/platform-rebuild`. Its application sources match the completed iOS
producing revision `0cf6090`; the intervening commit changes documentation only.

## Execution matrix

| Platform | Fresh execution |
| --- | --- |
| Windows x64 and ARM64 | [Native tests, editor/clipboard checks, setup state and installer integrity](https://github.com/someanay/quill/actions/runs/39439128409) |
| Mac, Intel and Apple Silicon, macOS 15 | [Core/native/UI tests and release packages](https://github.com/someanay/quill/actions/runs/39439135466) |
| Mac, Intel and Apple Silicon, macOS 26 | [Core/native/UI tests and release packages](https://github.com/someanay/quill/actions/runs/39439142443) |
| Mac, all four configurations | [Production TextEdit, Safari and AppKit insertion/refusal checks](https://github.com/someanay/quill/actions/runs/39439147058) |
| iPhone and iPad, iOS 18.5 and 26.4.1 | Completed full native and installed-keyboard gates at `0cf6090`; see the [exact runs, counts and package hashes](ios-keyboard-session.md) |

## Functional boundaries

- Recording and lifecycle: real Windows microphone capture, silence auto-stop,
  clean stop/restart and empty repeated stop; actual iOS simulator input, repeated
  takes, timed background sessions, expiry/interruption and cancellation. Mac
  tests exercise native AVFoundation conversion and capture-adapter lifecycle;
  physical Mac microphone fidelity remains a hardware check.
- Pipeline and errors: provider request/authentication shapes, cleanup deadline
  and rejection, stale/cancelled responses, retries using the same take, private
  provenance through restart, storage failures and preventing duplicate delivery.
  HTTP fixtures verify implementation behavior; they do not establish live
  provider quality, billing or account eligibility.
- Real insertion: Windows cross-process native editor, Unicode, selection,
  focus/content/caret drift, secure/read-only refusal and clipboard ownership;
  Mac TextEdit/Safari/AppKit with measured Accessibility trust and exact readback;
  installed iOS keyboard into a separate UIKit app, protected fields and
  consume-once. Only disposable editors and synthetic text are used.
- State and controls: saved keys/settings/history, consent gates, reset/deletion,
  custom words, retained setup drafts, Windows activation and same-instance
  reopen, actual shortcut registration and safe installer/upgrade/uninstall.
  Mac global shortcut ergonomics, login and real sleep/wake remain physical
  acceptance; the test-mode UI deliberately avoids installing a global hotkey.

## Local Windows checks

The real recorder passes a 3.147-second capture: 62 frames, 98,862 WAV bytes,
16 kHz mono 16-bit PCM. The lifecycle probe passes silence auto-stop (one event),
clean stop, a second take, clean cancellation and an empty repeated stop. Both
reports record `audioPersisted=false` and `networkRequests=0`.

Actual shortcut registration also passes. Windows reports the preferred
Ctrl+Win+Space already reserved (error 1409), then successfully registers the
documented Ctrl+Alt+Space fallback. This checks registration without synthesizing
keys or changing the installed app's settings.

Original local evidence is under the ignored `dist/native-functionality/` folder.

The actual installed application is also verified without launching it: version
2.0.3 under `%LOCALAPPDATA%\Programs\Quill`, its Installed apps registration and
Start shortcut target, and executable SHA-256
`4a98a41a25ea552127d60ed2e32d05eb701dd211a5198ef33143fba955795f16`.
The application remains closed. This confirms a per-user installation that opens
from Start after the one-time installer step.

## Completed Windows and real-editor results

Both fresh Windows architectures pass all 362 tests with zero skipped, including
normal and maximized same-process/window reopening. Each also passes 14 actual
cross-process editor/clipboard cases, 42 retained setup/state checks, 300 native
layout renders and 11 installer integrity/registration/uninstall checks. Original
TRX counters and JSON outcomes are inspected and aggregated in the local
`final/functional-editor-summary.json`.

All eight Mac editor jobs pass, using the unchanged production accessibility
adapter and measured effective Accessibility trust. Each of four configurations
passes 11 TextEdit checks, five production Safari checks and three native AppKit
checks. That is **76 production-adapter editor/safety checks**. Four additional
direct-AX Safari diagnostic checks also pass; these are explicitly separate from
production-adapter insertion results.

TextEdit checks empty/end/middle/zero-caret insertion, selected replacement,
Hindi/emoji, selection/content/application drift and clipboard preservation.
Safari confirms textarea append, Unicode replacement, contenteditable and real
password refusal. AppKit confirms a native text field, secure-field refusal and
unchanged clipboard. Only isolated synthetic documents/fields are used.

The baseline editor runners report macOS 15.7.9. Current Apple Silicon reports
26.6.2; current Intel reports 26.6.1. These are actual runtime results, not claims
of execution on every older supported version.

Source comparisons confirm Windows application/asset/installer code is unchanged
from reviewed revision `7f22dff`, Mac application/core code from `928cdcb`, and
iOS application/keyboard/core code from `0cf6090`. This is recorded in
`source-equivalence.json`. No additional application change was required by this
functional pass.

## Full Mac suites and packages

All four full Mac jobs complete successfully, including native tests, UI
walkthroughs and universal Release builds. Each configuration passes **34 core,
43 native and 11 UI tests**, with zero failures or skipped required tests. The
packaging procedure additionally repeats the 34 core tests successfully on each
runner. Conditional iOS-only workflow steps are intentionally inapplicable to
Mac jobs. Exact job outcomes and suite excerpts are retained in `final/ci-jobs.json`.

The native suite covers provider/key setup, dictation/error recovery, private
recording provenance, cancellation, delayed accessibility confirmation,
secure/focus drift refusal and no duplicate insertion after restart. UI tests
cover the native setup/workspace/settings/history controls, compact and expanded
windows, Light/Dark, retained provider drafts and Settings lifecycle. The fresh
full matrix supplements the separate real-editor results above.

All four downloaded Mac release artifacts match GitHub's SHA-256 digests and
the producing source. Each inner package contains both arm64 and x86_64 binaries,
568 bundled strings at revision 23, matching native permission descriptions and
the exact Android font bytes. Release-only inspection confirms that DEBUG
fixtures are absent. They target macOS 13; only the runtimes named above were
actually executed. These unsigned/unnotarized packages remain build evidence.

| Universal package producer | Inner ZIP SHA-256 |
| --- | --- |
| macOS 15 Apple Silicon | `af35b5b320becd3baa742e89fe69030f3533a7af8bef0f0a16abff3bc7460cbe` |
| macOS 15 Intel | `59b8c4e069d245553448866cd1bf7fcb5e7c107cfab56fec28ead626f4a583ff` |
| macOS 26 Apple Silicon | `53eb89aab9720d9b865fa7bbb775104860e0edca57c5a72309caad544ef6db0e` |
| macOS 26 Intel | `b0f1716a46b2657d026aa04cbd8edcf04eff0007cf6b7360c022c5496b7338e4` |

The four package inspections and four outer-artifact digest records are saved in
`final/mac-package-inspections.json` and `final/mac-package-digests.json`. The two
Windows test artifacts and eight Mac editor artifacts also match GitHub's
digests, as recorded in `final/editor-artifact-digests.json`. Fresh Windows
package-build and installer gates pass; the duplicate Windows package archives
were not downloaded again because the installed executable and unchanged source
were already verified.

## iPhone and iPad functionality

The unchanged iOS sources already pass the complete iOS 18.5 and 26.4.1 gates:
56 core, 55 native host/keyboard, eight actual simulator recorder, 13 iPhone UI
and 13 iPad UI tests per runtime. Four installed-keyboard journeys additionally
pass on phone/tablet across both runtimes: **294 passing executions** in total,
with zero failures or skipped required tests. All 464 final captures were reviewed,
including both appearances. All four inner packages were inspected and all ten
downloaded artifacts match GitHub digests. Exact runs and hashes are in
[the session report](ios-keyboard-session.md).

Two real simulator takes per keyboard journey start and stop from the installed
keyboard while the host remains in the background; exact inserted text is read
back from a separate app. The journeys also verify setup, Full Access, protected
fields, manual fallback, second dictation and unchanged text after End. No host
activation or manual Send/Insert is used for those background dictations.

## Readiness assessment and remaining acceptance

Windows has the strongest physical evidence: its installed package, local
microphone lifecycle and shortcut registration are verified, in addition to the
fresh x64/ARM64 CI gates. The native Mac and iOS apps have credible functional
evidence beyond screenshots: real Mac editor readbacks and real installed iOS
keyboard/background-session journeys pass. The earlier complete visual review
remains applicable to the unchanged Windows/Mac presentation sources; the new
iOS session controls received their own complete Light/Dark review.

I would proceed to controlled device testing, rather than call the Apple apps
ready for public distribution. Physical Mac microphone/audio routes, permission
denial and Settings return, global shortcut behavior, login and sleep/wake still
need acceptance. Physical iPhone/iPad audio, locked-device protection, calls,
input-route changes, external keyboards and the five-minute session's battery
and heat cost still need acceptance. Simulator assertions exercise lifecycle
policy and actual audio APIs but cannot establish these device properties.
Wider third-party editor compatibility and real speech/cleanup-provider quality
also remain account/device checks. Signing, notarization and App Store review
remain distribution gates. No claim here implies exhaustive coverage of every
OS version, editor, hardware configuration or provider account.
