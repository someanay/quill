# Quill for iOS

Native iOS port of Quill's BYOK dictation pipeline. It mirrors the **Android** app's model — same
providers, same guarded cleanup, same on-device privacy posture — but delivers "dictate into any app"
the iOS-native way, because iOS does not allow Android's floating overlay or cross-app accessibility
injection.

> **Status: source-complete, compiled/validated on iOS pending.** Written on a Windows host, where
> Apple toolchains cannot run, so it has **not been compiled or run**. The platform-agnostic core is
> the same Swift used by the (also-new) macOS port and is covered by an XCTest target mirroring the
> Windows/macOS suites. The iOS-integration layer (§"Needs a Mac") needs on-device validation.

## How "dictate anywhere" maps from Android → iOS

| Android | iOS (this port) |
|---|---|
| Floating overlay bubble (`SYSTEM_ALERT_WINDOW`) | **Custom keyboard extension** with a Dictate button (the sanctioned iOS way to insert into any app) |
| AccessibilityService `ACTION_SET_TEXT` into the focused field | `UITextDocumentProxy.insertText` from the keyboard |
| Mic foreground service | In-app recording + keyboard recording (with **Allow Full Access**) |
| — | Plus an **in-app dictation screen** (record → copy/share) for use inside Quill |

Everything after capture — provider request, guarded cleanup, custom-word substitution, history — is
the **shared pipeline** (`Shared/DictationEngine.swift`), identical to Android/Windows/macOS.

## Architecture

- `Shared/` — platform-agnostic core, compiled into **both** the app and the keyboard extension (no
  separate framework, so no `public` boilerplate): models, settings/history stores, Keychain secrets,
  STT clients, guarded cleanup, key validator, `AVAudioSession` capture, and the `DictationEngine`.
  Data is shared between the two targets via an **App Group** container and (optionally) a shared
  **Keychain** group.
- `Quill/` — the SwiftUI app: Dictate / History / Settings tabs + first-run onboarding.
- `QuillKeyboard/` — the keyboard extension (`UIInputViewController`).
- `SharedTests/` — pure-logic tests mirroring the Windows/macOS suites.

Ports the fixed Windows/Android logic (guarded cleanup W-1, short-fence guard W-2, literal custom
words W-3, defensive JSON W-4/W-5, omit-null-language W-6).

## Core path

1. Add the Quill keyboard (or open the in-app Dictate tab) and tap **Dictate**.
2. `AVAudioSession` + `AVAudioEngine` capture 16 kHz mono PCM (silence auto-stop + session cap).
3. The WAV goes to the selected provider (Groq/OpenAI/OpenRouter/Gemini/custom HTTPS).
4. Optional cleanup runs, guarded by `CleanupGuard`; custom-word replacements run last.
5. The keyboard inserts the transcript via `UITextDocumentProxy`; the in-app screen offers copy/share.
6. The temp WAV is deleted; history is saved locally per the retention setting unless private mode /
   history-off prevents it.

## Security

- API keys live in the **iOS Keychain** (per provider). No account, no backend for user data.
- Audio goes only to the user's chosen provider over HTTPS and is deleted after each dictation.
- Compliance/privacy copy (mic rationale, key-required warning) is compiled into the binary.

## Build & test

Requires **Xcode 15+** and [XcodeGen](https://github.com/yonyz/XcodeGen) (`brew install xcodegen`).

```bash
cd ios
xcodegen generate
open Quill.xcodeproj
# Set your Development Team on the Quill and QuillKeyboard targets, then Run.
# Tests: Product ▸ Test (⌘U), or: xcodebuild test -scheme Quill -destination 'platform=iOS Simulator,name=iPhone 15'
```

## Needs a Mac to compile/run (configuration + platform surfaces)

- **App Group**: create `group.com.tihor.quill` in your Apple Developer account and enable it on both
  targets (already referenced in the entitlements) so app + keyboard share settings/history.
- **Shared Keychain for the keyboard**: the keyboard must read the key the app saved. Add the SAME
  `keychain-access-groups` entry to both entitlements (templates are in the `.entitlements` files) and
  set `SecretStore.accessGroup` to the team-prefixed value. Until then, the **in-app** Dictate screen
  works (app's private keychain); the keyboard shows "add a key" because it can't read it.
- **Allow Full Access**: the keyboard needs it for microphone + network (`RequestsOpenAccess` is set);
  the user enables it in Settings › General › Keyboard › Keyboards › Quill.
- **Microphone (TCC)** prompt on first record.
- **`AVAudioSession` + `AVAudioEngine`** capture/convert and the `UITextDocumentProxy` insertion want a
  real-device pass (keyboard memory limits especially — keep the extension lean).
- **Provider network from an app extension** requires Full Access; verify the request timeouts feel
  right on cellular.
