# Quill for iOS

Native SwiftUI app and UIKit keyboard extension, rebuilt from the current Android app. iOS 17 or later is required. The code uses the Android cobalt palette, the actual IBM Plex Sans and Serif font files, the `quill.` wordmark, recording bubble and ink meter, the three Home / History / Settings tabs, and the complete bundled Android copy catalog with accurate iOS-specific additions.

## Build and test

On a Mac with Xcode and XcodeGen installed:

```sh
cd ios
xcodegen generate
swift test --package-path QuillCore
xcodebuild test -project Quill.xcodeproj -scheme Quill \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -resultBundlePath ../dist/ios/Quill.xcresult \
  -skip-testing:QuillTests/RecorderTests \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual
```

Use an installed simulator name from `xcrun simctl list devices available`. `Quill` is the shared scheme; it builds the host and embedded `QuillKeyboard`, and runs `QuillTests` and `QuillUITests`. The generated project is reproducible from `project.yml`. The root Apple CI workflow chooses an available simulator automatically and preserves the result bundle, screenshots, logs and simulator app.

After that pass installs the host, grant its microphone permission with `xcrun simctl privacy <simulator-UUID> grant microphone com.tihor.quill.ios`, then run `xcodebuild test-without-building` with the same project/scheme/destination and `-only-testing:QuillTests/RecorderTests`. The five recorder tests use the real native recorder to check finalized 16 kHz mono PCM audio, cancellation deletion, the session cap, interruption-adapter recovery and interruption followed by immediate restart. They never upload audio. The CI workflow performs both passes.

For a physical iPhone, choose your development team in both targets, register `com.tihor.quill.ios` and `com.tihor.quill.ios.keyboard`, and enable the `group.com.tihor.quill.ios` App Group for both. Keep the Keychain access groups separate; the keyboard has no key-sharing entitlement. Build and run the `Quill` scheme on the phone. A device IPA or App Store release requires Apple's signing credentials and provisioning; a simulator app cannot be installed on an iPhone.

The portable `QuillCore` package also builds and tests with Swift 6.3.3 on Windows. It has no UIKit, AVFoundation, Security or SQLite dependency. Its tests include all 195 actual Android cleanup decisions, Android formatter-signature vectors, endpoint validation, provider request/authentication shapes, error mapping, Gemini response hardening, custom words, contacts filtering, insertion spacing, retention and keyboard handoff lifetime. The source fixture must match `tests/parity/cleanup.json` in the repository root.

`QuillTests` tests actual SQLite storage and deletion, the bundle/fonts, Keychain isolation, failed-recording recovery, protected file coordination, the real keyboard controller with an injected text proxy, and the host pipeline with synthetic provider responses. Keyboard tests include selected-text replacement, Unicode preservation, expired transfers, consume once, secure/unavailable fields and revoked Full Access. Pipeline tests cover cleanup failure, cancellation, private retry, voice vocabulary and failed history storage. These use isolated files and synthetic keys in the real Keychain; no production validation is bypassed. Simulator builds use ad hoc signing so Keychain and App Group entitlements remain present.

`QuillUITests` uses a separate local app-data directory and navigates every onboarding step and all settings categories, including dark mode, larger text and landscape. Its complete-setup test uses an explicitly selected synthetic provider transport compiled only in DEBUG and activated only by both `--ui-testing` and `--ui-provider-fixtures`; real request construction, response validation, Keychain writes and native microphone permission still execute. The fixture recognizes only a fixed synthetic Groq key. Release builds omit this transport entirely, and no chargeable provider calls are made by these tests. Screenshots and accessibility hierarchies are attached to every UI test; the keyboard controller also attaches compact-width light/dark renders.

Simulator file tests assert the actual atomic and complete-protection options passed to Foundation and execute the full handoff lifecycle. The simulator filesystem does not expose the physical device's protection attribute in the observed run; the additional attribute assertion executes on physical devices. Apple's [file-protection documentation](https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files) describes the hardware/passcode protection boundary. Simulator tests do not establish device-lock encryption, microphone quality, physical keyboard transfer compatibility or App Store readiness.

## Architecture and data flow

`QuillCore` owns provider configuration, HTTPS endpoint validation, outbound request/response handling, cleanup divergence guards, deterministic vocabulary, recording policy, history policy, insertion spacing and copy formatting. `AppModel` coordinates the native seams with a snapshot of the provider/model/style/words selected when recording begins. Changing settings during a request cannot redirect that recording or credential to a different endpoint.

The host records 16 kHz mono 16-bit PCM WAV through `AVAudioRecorder`. Its meter feeds the same PCM-equivalent threshold used by Android's silence detector: 600 RMS, a two-second startup grace and a 3.5-second quiet window. The session cap defaults to five minutes and offers the same one-, two-, three-, four- and five-minute options as Android. The built-in microphone is selected explicitly. Audio interruptions and revoked permission expose recovery instead of continuing invisibly. A microphone test never sends audio to any provider.

Record → provider STT → optional independent cleanup (four-second timeout) → structural guard → custom-word substitution → result/history. The guard accepts exactly the same golden cases as Android, including contraction-normalization quirks. Custom words run last, and cleanup failure preserves the raw transcript. Groq, OpenAI, OpenRouter, Gemini and custom OpenAI-compatible speech providers are supported. Cleanup is selected independently; automatic cleanup is enabled only for the same known free-policy providers as Android. Provider catalogs refresh directly, with bundled and manual model choices available offline.

Provider calls use ephemeral HTTPS sessions without cookies, disk response caches or saved credentials. Redirects are refused. Groq/OpenAI/custom STT use multipart audio; OpenRouter uses its JSON `input_audio` endpoint; Gemini uses `generateContent` and excludes thought parts and incomplete outputs. Provider errors are mapped to local messages; raw replies and keys do not enter diagnostics.

Keys are stored in this device's private Keychain with `WhenUnlockedThisDeviceOnly` and synchronization disabled. Multiple named keys are supported for each provider. Custom keys are bound to the exact normalized HTTPS endpoint verified at save time. Only non-secret labels, IDs and a four-character suffix are written to the protected key index. The keyboard has no access to any key, recording, full history or contact import.

History uses SQLite, secure deletion and DELETE journaling. Default retention is one hour; the same day/week/month/forever options and 1,000-entry cap apply. App data and recordings are protected and excluded from backups. Private mode skips new transcript history and hides an in-memory result on backgrounding; it does not erase earlier saved history. In both modes failed recordings remain in protected local storage, including across app recreation, until Retry succeeds or the user explicitly cancels. Successful recordings and microphone tests are deleted. Orphan audio is purged on launch.

Contact import stores only filtered candidate names locally and sends no contact data to providers, matching Android's current candidate-only vocabulary phase. Optional MetricKit reporting stores only numeric crash/hang/exception counts; it discards raw payloads. Reports contain a fixed whitelist and leave the phone only when the user reviews and chooses Share.

## Keyboard workflow and platform boundaries

1. Record and transcribe in the Quill app.
2. Tap **Send to keyboard**. This writes only that transcript to the App Group with a 60-second lifetime.
3. Return to the target app, switch to Quill using the globe key, and tap **Insert dictation**.

The keyboard consumes and deletes the pending transcript before insertion. Cross-process file coordination prevents a tap from deleting a newer replacement. Full Access is required for this shared-container consume/delete; ordinary typing, space, shift, numbers, delete, Return and the next-keyboard key work without it. The extension makes no network requests and never records. Selected text is replaced on the explicit insertion tap, as with the system keyboard and Android. A separator is added only when appending to existing non-whitespace text; middle insertion adds no extra spaces. The native proxy knows real field content, so iOS preserves content at the very start instead of using Android's accessibility-placeholder workaround. An explicit Copy action remains available in the host; that user-owned clipboard copy does not expire and is not synchronized to other devices.

Some Android capabilities have no equivalent public iOS API:

| Android behavior | iOS behavior |
|---|---|
| Floating bubble above any app | Bubble inside Quill; host-app recording plus keyboard transfer |
| Microphone started from overlay | User starts microphone in Quill; an active audio session may continue in background |
| Accessibility insertion into other apps | Native keyboard text proxy or explicit Copy |
| Automatic destination-app writing styles | User selects the writing style before recording; destination identity is unavailable |
| System-overlay visibility, drag/snap, shake and OEM battery exceptions | No corresponding cross-app overlay or OEM permission; iOS controls background lifecycle |
| Current Android WAV pass-through upload | Native lossless PCM WAV upload; the older Android FLAC plan is still deferred |
| Restore bubble after reboot | User reopens Quill; no hidden background microphone start |

Apple documents that custom keyboards cannot use the microphone, secure fields use the system keyboard, host apps may disallow custom keyboards, and keyboards can draw only within their own view. See [Apple's Custom Keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html). Apple's current [open-access documentation](https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard) describes shared-container restrictions. [Audio-session recording documentation](https://developer.apple.com/documentation/avfaudio/avaudiosession/category-swift.struct/record) covers continuation of a user-started recording in background. The onboarding explains these differences; it does not promise an Android overlay on iOS or automatic app switching.

## Copy and assets

`Quill/Resources/copy/strings.default.json` is the sole bundled catalog; `copy-manifest.json` contains editable local source metadata. The app and keyboard have no remote-copy loader. `scripts/sync-android-assets.py` copies Android catalog updates, exact font bytes and test fixtures while preserving existing iOS-only local wording. Use `--refresh-defaults` only when intentionally replacing that wording with the checked-in script defaults. It also maintains the same formatting signatures and test vectors as Android. The root copy tool edits these files for the next app release.

The App Icon is rendered directly from Android's current adaptive-icon paths, without substituting a different logo. `scripts/build-icon.cjs` reproduces the opaque 1024-pixel icon using `sharp`. Microphone and Contacts system permission text is included in the host's `Info.plist`; review it with the bundled privacy copy before release.

## Physical-device acceptance matrix

Do not mark the following passed based only on simulator or pure tests:

- Fresh install: complete warning/key verification, microphone grant and microphone test; deny each permission and recover from Settings; set up the keyboard with Full Access off/on.
- Record short and five-minute speech, silence, long mid-sentence pauses and a noisy room; verify RMS feedback, cap warning, cancel, incoming call, screen lock and app switching.
- Validate every speech provider with its own authorized test key; invalid/revoked keys, offline network, exhausted quota, wrong model, cancellation and provider timeout. Do the same for all independent cleanup combinations.
- Fail a transcription in private and normal mode; terminate and reopen Quill; verify Retry preserves the captured audio and Cancel deletes it. Successful private dictations must not enter SQLite history.
- Type into Notes, Messages, Safari textareas and contenteditable, Mail, Gmail and supported third-party apps. Insert at start/middle/end, before punctuation, and next to Unicode. Confirm existing text stays intact.
- Use secure/password and phone-number fields and an app that refuses third-party keyboards; verify iOS's system keyboard behavior and the host's explicit Copy route.
- Test handoff expiry, single-use consumption, app/keyboard process restart, repeated publish, selected text, Full Access revocation and device lock. Pending text must never become available to another app except through the user-selected keyboard insertion.
- Verify history retention boundaries, delete/clear, private mode, key switching/deletion, custom endpoint binding, contact import/clear, voice custom-word capture and the reviewed diagnostic report.
- Check every screen on a small iPhone and iPad, light/dark, largest Dynamic Type, VoiceOver, Reduce Motion, portrait/landscape, and keyboard switching.

App Store submission additionally requires real-device evidence, signed distribution artifacts, a hosted product-specific privacy-policy URL, accurate privacy labels and an Apple developer account. These are not replaced by a successful compiler or simulator run.
