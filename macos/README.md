# Quill for macOS

This is a fresh native implementation of the current Android app, replacing the old macOS draft. It uses SwiftUI/AppKit, AVAudioEngine, macOS Accessibility, Keychain and SQLite. QuillCore contains the provider contracts and pure policies and is also testable on Windows. No user-data server, copy server, analytics SDK or third-party runtime is involved.

macOS 13 or later is required. Both Apple Silicon and Intel are supported by the project. Start/stop dictation with the floating bubble or **Command–Option–Space**. The bubble does not activate Quill or take keyboard focus. A long press dictates to the clipboard. The menu bar provides show/hide, cancel and quit controls.

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

The UI test launch option uses a separate application-support directory and Keychain service. The Debug-only settings fixture changes initial navigation without inventing a valid key, granting permissions or bypassing the provider client. Release builds do not recognize that settings shortcut.

## Android behavior carried across

- The same onboarding sequence: welcome, three-second BYOK warning, key entry and verification, setup checklist, live microphone test, bubble explanation, Accessibility disclosure/enable, ready screen.
- Five speech providers: Groq, OpenAI, OpenRouter, Gemini and custom HTTPS OpenAI-compatible endpoints. Correct multipart, OpenRouter `input_audio`, and Gemini `generateContent` request types are implemented. Verification calls provider metadata endpoints without uploading audio or consuming model tokens.
- Multiple separately stored key slots per provider, active selection, re-test and removal. Custom keys are bound to the exact validated base URL, so changing endpoints cannot forward an existing key to a different server.
- Live provider model catalogs, bounded pagination, compatible-modality filtering, free-tier ranking, local cache and offline defaults. Saved selections remain available.
- Independent cleanup provider/model, automatic matching only where a known free option exists, four-second fail-soft cleanup, per-app Formal/Neutral/Casual style, original transcript on rejected or failed cleanup, custom substitutions last.
- Current Android capture format: 16 kHz mono signed 16-bit PCM WAV. Android's current encoder is a WAV pass-through; its older FLAC design is not described as implemented here. Same 600-RMS silence floor, two-second startup grace, 3.5-second quiet window, 300-second maximum, 15-second warning, retry and cancellation.
- History on by default, one-hour retention, 1 day/1 week/30 days/forever options, 1,000-entry cap, deletion and private mode. History uses SQLite with secure deletion enabled.
- Local custom words with voice capture; contact-name import, filtering and clear. As in Android's current phase-one implementation, imported contacts are **not uploaded** or added to provider prompts.
- Current Android cobalt palette, lowercase `quill.` wordmark, bundled IBM Plex Sans/Serif, dark/light/system theme, white-ring bubble, RMS recording strokes, reduced-motion behavior and bubble size ratios.
- Drag-to-bottom hides the bubble for ten minutes; explicit Home/menu hiding cancels automatic return. Login/wake restoration never resumes recording.
- Opt-in diagnostic capture uses an allowlist of version/OS/stack addresses. Arbitrary exception reasons, recordings, transcripts, clipboard contents and keys are excluded. Reports are reviewed locally and exported only by a deliberate Save action. A previous unclean exit is identified as an unexpected termination, not automatically labeled a crash.
- All UI text is bundled, with no remote text updates. The Quill Copy Tool edits `QuillMac/Resources/copy/strings.default.json` and `copy-manifest.json` for the next release. `scripts/bootstrap_copy.py` is only for intentional rebaselining, because it overwrites local copy edits.

## Insertion and platform boundaries

Accessibility captures the focused element, application, existing text and selection at recording start. It refuses password/secure fields, then rechecks focus and content before insertion. It first writes the selected text, then tries a full-value update where that attribute is writable. A successful API return is not treated as proof: the app reads back the expected text. Unconfirmed delivery retains the transcript and tells the user to inspect the field. Explicit paste also retains the transcript because a posted keyboard event cannot prove delivery. User-initiated Copy does not expire; the temporary paste fallback clipboard payload expires after 60 seconds only if Quill still owns the clipboard.

macOS Accessibility differs from Android's service: application support varies, some rich editors hide text or reject updates, and secure input can disable dictation. Menu bar/login integration replaces Android's foreground-service notification and OEM battery settings. There is no macOS equivalent of Android's overlay permission. These are native platform substitutions, not claims of identical OS APIs.

## Verification record

- **Passed on Windows, Swift 6.3.3:** 26 QuillCore tests, including all 195 authoritative Android cleanup vectors evaluated by the Android JVM implementation. Coverage includes default/settings round trips, contact filtering, Unicode selection/insertion, history pruning, WAV headers, silence timing, custom URL rejection, key metadata requests, all provider request shapes, typed provider errors, cleanup cancellation/fail-soft behavior, Gemini response safety states, catalog pagination and copy formatting.
- **Passed:** Swift parser check over every native source file, catalog/manifest generation and deterministic app-icon generation from Android artwork.
- **Pending at this writing:** native Xcode compile/test results and XCUITest screenshots are being collected by the parent platform-rebuild task. Update this record from its CI artifacts before declaring a release ready.
- **Requires a physical Mac and user provider accounts:** real microphone fidelity, multi-app Accessibility insertion, secure fields, provider quota/access behavior, sleep/wake, login restore, clipboard ownership changes and long-running recording/bubble behavior. Unit tests and a successful compile do not establish these results.

## Manual release gate

1. Fresh setup in light/dark/large-text modes; warning cannot be acknowledged before three seconds, empty/malformed keys are refused, metadata/network failures retain typed input, microphone denial and Accessibility revocation provide recovery.
2. Test dictation at empty caret, middle caret, end caret and nonempty selection in TextEdit, Notes, Mail, Safari, Chrome, Slack and a terminal editor. Change application and edit field contents while transcription is pending: no text may be sent to the wrong field and the transcript must remain recoverable.
3. Focus password and secure-entry fields. Bubble and hotkey dictation must refuse capture. Deny Accessibility while a request is pending; verify recovery without lost transcript.
4. Record with natural pauses, then sustained silence. Test 30/300-second caps, cancel during recording/STT/cleanup, provider rejection/offline retry, headphone disconnect and sleep while recording.
5. Switch every speech and cleanup provider, each active key slot, explicit/automatic cleanup and Formal/Neutral/Casual styles. Spoken commands must stay text; failing cleanup must preserve the transcript. Check custom URL/key scope rejection.
6. Test retention cutoff, private mode, local contacts, clear-data, Keychain persistence after app restart, clipboard user changes, light/dark bubble contrast, drag-hide ten-minute return and explicit-hide persistence.
7. Build a signed hardened runtime app, verify its signature, notarize with the maintainer's Developer ID credentials, staple, and test first launch under Gatekeeper. This repository does not contain signing credentials or claim an unsigned app is notarized.
