# Quill for macOS

This is a native Mac dictation app sharing Quill’s provider, privacy and brand foundations. It uses SwiftUI/AppKit, AVAudioEngine, macOS Accessibility, Keychain and SQLite. QuillCore contains the provider contracts and pure policies and is also testable on Windows. No user-data server, copy server, analytics SDK or third-party runtime is involved.

macOS 13 or later is required. Both Apple Silicon and Intel are supported by the project. Start/stop dictation with the floating bubble or **Command–Option–Space**. The bubble does not activate Quill or take keyboard focus. A long press dictates to the clipboard. The menu bar provides show/hide, cancel and quit controls.

## Desktop experience revision — October 20, 2026

The new source uses a resizable desktop window, sidebar navigation and a practice
workspace. Home adapts between parallel and stacked panes. History has a selectable
list and a full transcript reader. Settings opens in a separate resizable window,
also available with Command-comma, with persistent categories at larger widths.
The inactive contacts candidate importer is hidden from navigation.

Onboarding teaches native microphone/Accessibility access and Command–Option–Space.
It has seven desktop stages with contextual content at wide sizes; compact and
short windows scroll without replacing the live provider-key form. The floating
control is optional. There is no Android overlay-permission or battery-setup step.

Native build, logic, UI, editor-insertion and package verification now run on
GitHub Actions. See [current Apple validation](../docs/apple-validation.md) for
the exact tested revisions, screenshot findings and remaining hardware checks,
and [the platform experience contract](../docs/platform-experience.md) for scope.

## Build and run

On a Mac with Xcode selected and XcodeGen installed:

```sh
cd macos
swift test --package-path QuillCore
xcodegen generate
xcodebuild -project QuillMac.xcodeproj -scheme QuillMac \
  -configuration Debug -destination 'platform=macOS' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
open build/Build/Products/Debug/Quill.app
```

Grant microphone access when onboarding asks, then enable Quill in **System Settings → Privacy & Security → Accessibility**. The Accessibility screen includes an explicit disclosure and consent checkbox. Contacts access is requested only when Import is selected. Login restoration uses Apple's `SMAppService`; allow the login item in System Settings if macOS requests approval.

Run native and UI tests:

```sh
xcodebuild -project QuillMac.xcodeproj -scheme QuillMac \
  -destination 'platform=macOS' -derivedDataPath build \
  -resultBundlePath build/QuillMacTests.xcresult CODE_SIGNING_ALLOWED=NO test
```

The UI test launch option uses a separate application-support directory and Keychain service. Debug-only presentation fixtures render every onboarding stage in light and dark and allow settings navigation without inventing a valid key, granting permissions or bypassing the provider client. Onboarding presentation captures keep the microphone inactive and show the real disabled permission/key gates. They verify rendering, not successful completion of setup. Release builds do not recognize either presentation shortcut.

## Android behavior carried across

- Desktop onboarding: welcome, own-key warning, provider connection, microphone test, Accessibility disclosure/enable, shortcut practice and completion.
- Shared Android guided key setup: Gemini/Groq/OpenRouter expandable rows, numbered provider instructions, explicit Paste/reveal, 650 ms automatic metadata validation, stale-request cancellation and verified-only Save. Unfinished drafts remain in Keychain and the expanded provider is remembered separately. Speech settings prefill saved keys and activate the chosen speech provider; adding an extra key preserves speech and cleanup selections. OpenAI/custom setup remains available separately; custom endpoints require an explicit check before any draft key is sent.
- Five speech providers: Groq, OpenAI, OpenRouter, Gemini and custom HTTPS OpenAI-compatible endpoints. Correct multipart, OpenRouter `input_audio`, and Gemini `generateContent` request types are implemented. Verification calls provider metadata endpoints without uploading audio or consuming model tokens.
- Multiple separately stored key slots per provider, active selection, re-test and removal. Custom keys are bound to the exact validated base URL, so changing endpoints cannot forward an existing key to a different server.
- Live provider model catalogs, bounded pagination, compatible-modality filtering, free-tier ranking, local cache and offline defaults. Saved selections remain available.
- Independent cleanup provider/model, automatic matching only where a known free option exists, four-second fail-soft cleanup, per-app Formal/Neutral/Casual style, original transcript on rejected or failed cleanup, custom substitutions last.
- Current Android capture format: 16 kHz mono signed 16-bit PCM WAV. Android's current encoder is a WAV pass-through; its older FLAC design is not described as implemented here. Same 600-RMS silence floor, two-second startup grace, 3.5-second quiet window, 300-second maximum, 15-second warning, retry and cancellation.
- Pending recordings and undelivered transcripts survive app restart in an AES-GCM encrypted recovery file; its random key stays in Keychain. Sleep, microphone revocation and quit stop capture and preserve recovery. Relaunch never records or inserts automatically. Before delivery, retryable audio must be durably replaced by the completed transcript; storage failure retains recovery and prevents insertion or Copy. A recovered transcript is offered for manual Copy. If explicit Discard cannot remove recovery data, it reports the failure and blocks new capture until deletion succeeds.
- History on by default, one-hour retention, 1 day/1 week/30 days/forever options, 1,000-entry cap, deletion and private mode. History uses SQLite with secure deletion enabled.
- Local custom words with eight-second raw voice capture, case-insensitive replacement of duplicate heard forms, trimming without length truncation; contact-name import, filtering and clear. As in Android's current phase-one implementation, imported contacts are **not uploaded** or added to provider prompts.
- Current Android cobalt palette, lowercase `quill.` wordmark, bundled IBM Plex Sans/Serif, dark/light/system theme, white-ring bubble, RMS recording strokes, reduced-motion behavior and bubble size ratios.
- Drag-to-bottom shows Android's approaching trash target and hides the bubble for ten minutes using its visible-bottom threshold; hiding does not discard a dictation. Explicit Home/menu hiding cancels automatic return. Login/wake restoration never resumes recording.
- Opt-in diagnostic capture uses an allowlist of version/OS/stack addresses. Arbitrary exception reasons, recordings, transcripts, clipboard contents and keys are excluded. Reports are reviewed locally and exported only by a deliberate Save action. A previous unclean exit is identified as an unexpected termination, not automatically labeled a crash.
- All UI text is bundled, with no remote text updates. The Quill Copy Tool edits `QuillMac/Resources/copy/strings.default.json` and `copy-manifest.json` for the next release. `scripts/bootstrap_copy.py` is only for intentional rebaselining, because it overwrites local copy edits.

## Insertion and platform boundaries

Accessibility captures the focused element, application, existing text and selection at recording start. It refuses password/secure fields, then rechecks focus and content before insertion. The source chooses a supported route before writing: writable selected text in native editors, and writable whole-value updates in Safari, whose selected-text setter is acknowledged but ignored. It does not rewrite a Safari rich field if its value is not writable. Once a text write is attempted it is never resent, including when the API reports failure: a delayed mutation may still have been submitted.

A successful API return is not proof: the source polls the expected text for at most 750 ms while checking permission, secure input and the original process/element. Whole-value insertion waits for the updated value before placing and confirming the intended caret, preserving unrelated selection changes. Cancellation, suspension and Copy invalidate pending confirmation so a late result cannot revive an old dictation. Suspension retains manual recovery; starting again cannot silently discard an undelivered transcript. Unconfirmed delivery asks the user to inspect the field. Explicit paste also retains the transcript because a posted keyboard event cannot prove delivery. User-initiated Copy does not expire; the temporary paste fallback clipboard payload expires after 60 seconds only if Quill still owns the clipboard.

macOS Accessibility differs from Android's service: application support varies, some rich editors hide text or reject updates, and secure input can disable dictation. Menu bar/login integration replaces Android's foreground-service notification and OEM battery settings. There is no macOS equivalent of Android's overlay permission. These are native platform substitutions, not claims of identical OS APIs.

The native history gate deliberately preserves the privacy choice at recording start as well as completion: a take begun in private mode stays out of history after retries, even if private mode is subsequently switched off. This is stricter than Android's current retry-time settings snapshot. Turning history off while a request runs also prevents saving that take.

## Verification record

The following dated snapshots document earlier integration work. For the current
redesign, use [Apple validation](../docs/apple-validation.md): the authored suite
contains 34 portable core, 43 native and eleven UI tests, with all settings/setup
screens in both appearances, compact/expanded/tall windows, and separate real
TextEdit/Safari/AppKit probes on Intel and Apple Silicon runners. Historical
counts and pending statements below do not override that current report.

- **Passed on Windows, Swift 6.3.3:** 34 QuillCore tests, including all 195 authoritative Android cleanup vectors evaluated by the Android JVM implementation. Coverage includes default/settings round trips, contact filtering, Unicode selection/insertion, history pruning, WAV headers, silence timing, custom URL rejection, key metadata requests, all provider request shapes, typed provider errors, a real four-second drip-fed cleanup deadline, Gemini response safety states, cancellation of unfinished streams above 4 MiB, 20-page catalog bounds, custom-word upsert, copy formatting and visible-bottom bubble-dismiss geometry.
- **Passed:** Swift parser check over every native source file, catalog/manifest generation and deterministic app-icon generation from Android artwork.
- **Passed on macOS CI (snapshot `63977e0`, run `34166907074`):** 34 core tests, 29 native tests and all six UI walkthroughs. Native coverage includes real AVFoundation stereo-to-mono resampling, Keychain/SQLite/settings, encrypted recovery and tamper rejection, microphone revocation, raw vocabulary capture, private retry, stale audio-route callbacks and nine insertion-adapter mutation/refusal/readback cases. Five guided-key regressions verify debounce/stale responses, Keychain draft persistence, verified-only save, preserving speech settings when adding another key and custom-endpoint changes.
- **Visually inspected from the same passing Mac run:** guided Gemini/Groq/OpenRouter setup, draft/expanded-row restoration after reopening, Speech model controls, the Keys empty state, every settings screen in dark mode, and all 18 onboarding presentations across light and dark. Existing walkthroughs also verify first-run warning/key gates, settings/history navigation and custom-word save/remove plus private history. Silent microphone capture permits continuing once the microphone successfully starts; detected speech remains a truthful indicator rather than an extra setup requirement. These presentation fixtures do not establish real permission or provider success.
- **Universal Release built and packaged:** the same run produced `Quill.app` with both `arm64` and `x86_64` architectures. The package contains the current Android font bytes, requires macOS 13 or later and excludes debug onboarding shortcuts. It is unsigned and has not been notarized.
- **Passed against real TextEdit on macOS 15.7.9 (snapshot `7bd67ec`, [run `34168019571`](https://github.com/someanay/quill/actions/runs/34168019571)):** all 11 checks passed with actual Accessibility trust enabled and secure input inactive. Seven AX insertions confirmed empty/end/middle/zero caret behavior, selected replacement and Hindi/emoji text. Three real selection/content/application changes were refused without modifying TextEdit, and direct insertion left the clipboard unchanged. The separate QA process used the runner's existing effective trust; no permission was fabricated or changed, and installed Quill still requires the user's Accessibility grant.
- **Observed in real Safari/AppKit integration (snapshot `bc2a86b`, [run `34168471685`](https://github.com/someanay/quill/actions/runs/34168471685), artifact `10034950943`):** all three native fixture checks passed: actual `NSTextField` Unicode insertion, `NSSecureTextField` refusal and unchanged clipboard. Safari also correctly refused its real password field (`AXSecureTextField`, secure input active) and left the clipboard unchanged. Its three ordinary textarea/contenteditable direct-insertion cases failed because accepted selected-text writes did not change the field. A second run (`564ac71`, `34168762768`) confirmed that the text was still unchanged after 500 ms. The transcripts remained unconfirmed and recoverable; no success was falsely reported.
- **September source corrections:** Safari selects a writable whole-value strategy before sending any write, with bounded asynchronous text/caret confirmation. Fourteen additional native regressions cover strategy selection, delayed confirmation/failure, focus/security/cancellation, caret placement, suspend/restart recovery, Copy during confirmation, durable audio retirement and failed Discard; the native suite contains 43 tests. September's billing restriction prevented their execution then. October's desktop experience supersedes the earlier welcome design, and [current Apple validation](../docs/apple-validation.md) records the resumed native execution and package evidence.

- **Requires a physical Mac and user provider accounts:** real microphone fidelity, insertion across the wider editor matrix, secure fields, provider quota/access behavior, sleep/wake, login restore, clipboard fallback ownership changes and long-running recording/bubble behavior. Adapter tests and the TextEdit probe do not establish these remaining results.

### Real TextEdit integration probe

`QA/TextEditProbe.swift` is a separate command-line target, `QuillAXProbe`, that compiles the unchanged production `TextInjector` and `SystemAccessibilityAccess` sources. It is not linked into `Quill.app`. On an isolated GitHub macOS runner, run `bash macos/scripts/test-textedit.sh` from the repository root. The probe refuses to run on a normal local machine or touch an existing TextEdit process.

The probe records the actual `AXIsProcessTrusted()` and secure-input values before touching the editor. If permitted, it opens only synthetic TextEdit files and checks seven caret/selection/Unicode insertions by AX readback, three changed-selection/content/application refusals and unchanged clipboard ownership. It does not edit TCC databases, request microphone access, supply a fake Accessibility adapter or override a production permission gate. GitHub's [Mac image configuration](https://github.com/actions/runner-images/blob/main/images/macos/scripts/build/configure-tccdb-macos.sh) preauthorizes some runner processes; whether this particular executable has effective trust is measured at runtime. Apple's [trust API](https://developer.apple.com/documentation/applicationservices/1459186-axisprocesstrustedwithoptions) reports actual process trust, and requesting a prompt would not grant access synchronously.

Evidence is written to `macos/TextEditEvidence/textedit-report.json`, `probe.log` and `build.log`. Exit **0** means the real integration checks passed, **78** means a recorded platform permission or pre-existing desktop-state blocker, and every other nonzero exit is a build/test/evidence failure. A blocked probe does not establish successful insertion. The completed run above produced artifact `macos-textedit-verification` (`10034796722`), whose JSON records `accessibilityTrusted: true`, `secureInputActive: false`, `status: passed` and all 11 successful checks. These are additional integration checks, separate from the 34 core, 29 native and six UI tests.

### Browser and secure-field probes

`bash macos/scripts/test-fields.sh` builds the separate `QuillFieldProbe` and `QuillAXFixture` QA targets on an isolated GitHub macOS runner. It tests Safari using local synthetic HTML textarea/contenteditable/password pages, then tests real AppKit `NSTextField` insertion and `NSSecureTextField` refusal in a separate fixture process. The fixture contains only fixed QA text, disables saved state and is not included in Quill's app or scheme. The probe never reads the password value, including failure diagnostics. It checks actual Accessibility trust, compiles the unchanged production adapter and leaves the clipboard untouched.

The browser and native fixture each produce their own JSON and log in `macos/FieldEvidence`; build output is `build.log`. Exit **0** requires both reports to pass, **78** represents actual permission blocking, and other nonzero exits indicate a real build/test failure. Existing browser or fixture processes cause refusal to touch that state. The observed first-run failures are historical; all four corrected Safari/AppKit configurations and four TextEdit configurations pass in the October report. The additional fresh-page AXValue diagnostic is explicitly labeled as direct API investigation, not a production-adapter pass.

The Safari correction is informed by [WebKit's empty selected-text implementation](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/accessibility/AccessibilityObject.h) and [its native input/textarea value setter](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/accessibility/AccessibilityRenderObject.cpp), together with the real failed selected-text experiments. Source inspection supports the strategy choice; the corrected runtime behavior is verified in [the final Apple matrix](../docs/apple-validation.md).

## Manual release gate

1. Fresh setup in light/dark/large-text modes; the own-key explanation is explicit without a reading timer, empty/malformed keys are refused, metadata/network failures retain typed input, microphone denial and Accessibility revocation provide recovery.
2. Test dictation at empty caret, middle caret, end caret and nonempty selection in TextEdit, Notes, Mail, Safari, Chrome, Slack and a terminal editor. Change application and edit field contents while transcription is pending: no text may be sent to the wrong field and the transcript must remain recoverable.
3. Focus password and secure-entry fields. Bubble and hotkey dictation must refuse capture. Deny Accessibility while a request is pending; verify recovery without lost transcript.
4. Record with natural pauses, then sustained silence. Test the 60/120/180/240/300-second caps, cancel during recording/STT/cleanup, provider rejection/offline retry, headphone disconnect and sleep while recording.
5. Switch every speech and cleanup provider, each active key slot, explicit/automatic cleanup and Formal/Neutral/Casual styles. Spoken commands must stay text; failing cleanup must preserve the transcript. Check custom URL/key scope rejection.
6. Test retention cutoff, private mode, local contacts, clear-data, Keychain persistence after app restart, clipboard user changes, light/dark bubble contrast, drag-hide ten-minute return and explicit-hide persistence.
7. Build a signed hardened runtime app, verify its signature, notarize with the maintainer's Developer ID credentials, staple, and test first launch under Gatekeeper. This repository does not contain signing credentials or claim an unsigned app is notarized.
