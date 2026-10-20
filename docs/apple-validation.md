# Apple validation — October 20, 2026

This is the completed native-redesign baseline. The subsequent iOS recording
interaction and its expanded acceptance are recorded separately in
[the keyboard-session report](ios-keyboard-session.md); the iOS counts and
manual-switching assessment below describe the named earlier revisions.

Status: all final jobs, packages and visual-review gates are complete. All four
Mac configurations, both iPhone/iPad runtime suites, all four installed-keyboard
journeys and all eight Mac real-editor jobs are green.
This report supersedes older counts and pending statements in the READMEs.

Git write access and pushes to `codex/apple-native-validation` are verified.
These are native Xcode builds on GitHub-hosted Macs, driven from Windows.

## Final execution matrix

| Component | Source | Complete gate |
| --- | --- | --- |
| Mac app | 928cdcb | 34 core, 43 native and 11 UI tests on macOS 15/26, Intel and Apple Silicon; universal Release packages |
| iPhone/iPad app | d3691f3 | 51 core, 47 native, 5 actual recorder, 13 iPhone UI and 13 iPad UI tests on each baseline/current runtime; device and simulator packages |
| Installed iOS keyboard | 6107bee | Four real Settings/Full Access, microphone, restart, practice-receipt, separate-app insertion and protected-field journeys |
| Mac real editors | 74022a2 | Eight successful jobs: 11 TextEdit checks and 6 Safari + 3 AppKit checks on each of four Mac configurations |

Mac runs: [baseline 39411608294](https://github.com/someanay/quill/actions/runs/39411608294),
[current 39411610962](https://github.com/someanay/quill/actions/runs/39411610962).
The current Intel job passed all tests and built its release, but its first two
artifact uploads timed out in GitHub's CreateArtifact service. Its later package
upload succeeded. The package was verified and retained locally before retrying
only that job; no test or assertion was changed for this infrastructure failure.
The retry passed all 43 native and eleven UI tests again, uploaded its reports
and all 109 screenshots, and packaged its universal Release successfully.

App runs: [baseline 39412493862](https://github.com/someanay/quill/actions/runs/39412493862),
[current 39412500028](https://github.com/someanay/quill/actions/runs/39412500028).
Baseline uses Xcode 16.4 / iOS 18.5; current uses Xcode 26.6 / iOS 26.4.1.
Minimum deployment targets are macOS 13 and iOS 17; that does not establish
execution on those older OS versions.

The manual workflow selector runs a complete native platform. Ordinary pushes
and pull requests keep the full matrices. All six selector outputs were checked;
actual job lists confirm the intended architectures and complete device suites.
Unchanged suites continue when a platform-specific correction is rerun.

## Real system integration

All eight [Mac editor jobs](https://github.com/someanay/quill/actions/runs/39403741480)
pass. All twelve JSON reports say `status=passed`, with 80 true checks in total.
Reports are retained in `dist/native-redesign/mac-editors-final/`.
They cover actual TextEdit, Safari and a separate AppKit fixture: caret/selection,
Hindi/emoji insertion, changed-target refusal, password refusal and clipboard
preservation. The Safari report labels its additional direct AXValue diagnostic
separately; it is not counted as a production-adapter insertion.

These use the production insertion adapter and actual runtime Accessibility
trust. They refuse pre-existing editor sessions and own only synthetic pages and
fields. They never alter TCC databases or read secure-field contents. A blocked
probe fails acceptance. The fixture must become the actual foreground PID before
probing; earlier failure evidence correctly showed Finder still had focus.

All four [installed-keyboard jobs](https://github.com/someanay/quill/actions/runs/39409012205)
pass, with zero blocked or skipped acceptance outcomes. Each original report says
`status=passed`; Unicode-scalar editor readback matches the exact expected text.
All 52 original screenshots and reports are retained under
`dist/native-redesign/610-keyboard/`, with a checked aggregate in
`dist/native-redesign/keyboard-final-summary.json`.

The keyboard journey uses actual Settings enablement/Full Access, native
microphone capture, Keychain, protected App Group transfer, host restart and an
installed extension inside a separate UIKit editor. The editor has no Quill
imports, shared storage or network. The STT response alone is a fixed DEBUG
fixture, excluded from the device Release. Pending transcripts survive refusal
in protected/phone fields and can then be inserted once into the normal editor.
The later app edits affect advanced-provider touch targets and device wording;
the tested recording, guided-key setup, receipts and transfer code are unchanged.

## Corrections found through execution and visual review

- Mac windows use explicit column widths and stable scrolling; decorative borders
  ignore input. Compact windows retain reachable controls and key drafts.
- Mac disclosure rows have a full clickable area and preserve native disclosure
  accessibility semantics. UI tests require actual expanded/collapsed state.
  Sheet closure uses a bounded state assertion, rather than testing mid-animation.
- Endpoint, model and custom-word fields keep visible labels after entry.
- iOS advanced-provider rows now have a real full-width 48-point touch target;
  their previous transparent centre missed taps. All five forms and the provider
  guide are captured in both appearances.
- iOS rows/cards retain their ideal height at the largest text size. Tests require
  adjacent statistics rows not to overlap and the final action to scroll above
  native navigation. iPad falls back to readable tabs at accessibility sizes.
- Keyboard connection and practice need recent matching receipts; typed text alone
  cannot unlock setup. A cold Settings launch gets a bounded foreground wait.
  Relaunches preserve the same isolated session UUID and actual stored key.
- Real editor readback reacquires the field after process switches. TextEdit setup
  waits for the intended synthetic document and stable caret, without retrying
  production writes or weakening confirmation.
- Intel macOS 26's disposable runner has a bounded IconServices health recovery
  for its [reported runner-image defect](https://github.com/actions/runner-images/issues/14751).
  It does not change Quill, TCC, microphone access or signing. AppKit generic file
  icons are allowed; Quill's own icons and all assertions remain intact.
- Privacy copy describes local failed-recording recovery and temporary keyboard
  context accurately. iPad wording says device, and microphone permission text
  distinguishes local tests from uploaded dictations. Copy ships only in releases.

## Visual and package inspection

All 109 final current-Mac named captures were reviewed in ten sheets at
`dist/native-redesign/928-final/mac-gallery/`. They cover seven setup stages,
every settings category at two sizes, five key forms and footers, expanded legal
details, diagnostics, resized drafts, workspace and full history in both themes.
The preceding baseline 109-image set was also reviewed in ten sheets; the final
field-label refinement is included in the current set and native baseline tests.
All 109 current Intel captures were also reviewed in ten sheets at
`dist/native-redesign/928-intel-retry/review/`. The baseline iOS 18.5 set has 91
named phone and 87 named tablet images, all reviewed in sixteen sheets at
`dist/native-redesign/d369-baseline/`. They cover six-stage setup, every settings
destination, provider forms/footers/guide, legal and diagnostic views, custom
words, largest text, landscape workspace/history and the actual UIKit keyboard.
The separate current iOS 26.4.1 set also has 91 phone and 87 tablet captures;
all were reviewed in sixteen sheets at `dist/native-redesign/d369-current/`.
The 356 final iOS captures cover both runtimes and both appearances. No blocking
visual defect remains in the reviewed surfaces. The local screenshot gallery is
`dist/native-redesign/review-index.md`.

The current universal Mac package is inspected independently. ZIP SHA-256:
`a8a6ffe3e8a13f18f3ae3d118da053a3cb12d5983bd6159b47494c2fe7c84f27`.
It has x86_64 + arm64 Mach-O binaries, macOS 13 minimum, all 568 revision-23
strings, exact Android font bytes, matching native permission descriptions and
no DEBUG presentation/provider fixtures. Inspection is recorded in
`dist/native-redesign/928-final/mac-package-inspection.json`.

Use `python scripts/inspect-apple-package.py <zip>` from the producing source to
check targets, host/keyboard identities, catalogs, fonts, permission descriptions
and Release fixture exclusion. These checks do not establish signing or physical
runtime acceptance. Both baseline iOS packages pass: revision 21 / 603 strings,
matching host/keyboard identities and permissions, exact Android fonts and a
device Release with no DEBUG fixtures. SHA-256 values:

- Device: `1b77fe83d838f28f1bbd4439780bdeccf54e979ece6114290fc607825c0467db`.
- Simulator: `0357b57c1a31f2d9901fb6febab54b775acd706e55faaecd6c1b5075ff8ed6ad`.
- Current Intel universal Mac retry: `166fa1f3f521ca201a86482d61617b4e2c8b66ca506a7049933f9dc9a6f2b340`.

Both current iOS packages independently pass the same checks. Current device
SHA-256: `ade86bb53f8f662f7b47ad7a66177ec56c636c5ccfe620e085c82857a22bde54`;
current simulator: `d65637ad87438ebb020d8455a4734e923948430f99e5a54dface2fdb9fc71dd6`.
Inspection JSON is retained beside each downloaded package. The independent
checkout comparison matches all native source, test and workflow files. The
strengthened package inspector (exercised against the final packages) and local
copy-authoring utility are synchronized separately; they do not change the tested
application binaries. `dist/native-redesign/final-checkout-comparison.json` and
`final-actions-summary.json` retain the exact comparison and all green job IDs.

## Honest readiness limits

Keep the native foundations: their integration work is worth retaining. The
presentation is coherent and platform-specific. Mac still needs physical audio,
permission-denial/Settings-return, global-shortcut, login, sleep/wake and broader
editor acceptance. iPhone/iPad need physical audio/lock/interruption, keyboard
access, protected storage, split-view/rotation, external-keyboard and live-provider
acceptance. The explicit record → send → return → insert loop still has more
friction than a keyboard-initiated recording session; onboarding describes the
implemented loop accurately.

Physical Apple devices, distribution identities and live provider accounts are
not available here. Ad hoc/unsigned CI outputs are not App Store or Gatekeeper
release approval. See the product assessment in `platform-experience.md` and the
Windows installation/results in `../windows/VERIFICATION.md`.
