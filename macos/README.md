# Quill for macOS

Native macOS port of Quill's BYOK dictation pipeline. It mirrors the **Windows** app one-to-one:
a system-wide shortcut starts/stops dictation in any focused text field, audio goes directly to the
user's own STT provider, an optional guarded cleanup pass tidies punctuation/casing, and the
transcript is typed into the focused field (clipboard fallback when typing is refused).

Built as a **menu-bar agent app** (`LSUIElement`) with a non-activating floating HUD — the desktop
analogue of the Windows dictation ribbon.

> **Status: source-complete, compiled/validated on macOS pending.** This port was written on a Windows
> host, where Apple toolchains cannot run, so it has **not been compiled or run**. The pure logic is a
> faithful port of the Windows app (which builds green with 45 passing tests) and is covered by an
> XCTest target that mirrors the Windows suite. The platform-integration layer (§"Needs a Mac")
> uses the correct system APIs but must be built and exercised on a Mac.

## Core path (identical to Windows)

1. Press **Alt+Q** (editable) in any ordinary text field.
2. Quill reads the system-wide focused element via the Accessibility API and refuses secure
   (password) fields. If no editable field is focused, the dictation is diverted to the clipboard.
3. `AVAudioEngine` captures and resamples to 16 kHz mono 16-bit PCM (silence auto-stop + session cap).
4. Press the shortcut again, or let silence/the cap stop it.
5. The WAV goes to the selected provider: Groq, OpenAI, OpenRouter, Gemini, or a custom
   OpenAI-compatible HTTPS endpoint.
6. Optional cleanup runs, **guarded by the same token-sequence fidelity check as Windows**
   (`CleanupGuard`), and is fail-soft.
7. Custom word replacements run last (literal replacement — no regex `$` pitfall).
8. Unicode `CGEvent` keystrokes type into the focused app; the clipboard is the fallback.
9. The temp WAV is deleted. History is retained locally per the retention setting unless private
   mode / history-off prevents the write.

## Security boundaries (same model as Windows/Android)

- API keys live in the **macOS Keychain**, isolated per provider (the DPAPI analogue).
- No account, no backend for user audio/transcripts/keys.
- Recording is user-triggered and never starts merely because Quill launched.
- Password fields are refused before recording and before insertion.
- Compliance/privacy copy (mic rationale, key-required warning) is compiled into the binary
  (`Info.plist` + Swift string constants) — never soft-coded or remotely overridable (spec §2.3).

## Build & test

Requires **Xcode 15+** on macOS 13+, and [XcodeGen](https://github.com/yonyz/XcodeGen)
(`brew install xcodegen`).

```bash
cd macos
xcodegen generate
open QuillMac.xcodeproj
# In Xcode: select the QuillMac scheme, set your Development Team under Signing & Capabilities, Run.
# Tests: Product ▸ Test (⌘U), or: xcodebuild test -scheme QuillMac -destination 'platform=macOS'
```

First run prompts for **Microphone** (TCC) and, for typing into other apps, **Accessibility**
(System Settings ▸ Privacy & Security ▸ Accessibility). Grant both.

## Layout (mirrors the Windows `src/Quill.Windows` tree)

- `QuillMac/Core` — models, settings/history stores, `AppPaths`, `DictationOrchestrator`
- `QuillMac/Audio` — `AVAudioEngine` capture + WAV lifecycle
- `QuillMac/Stt` — provider clients, response parser, key validator, romanize prompt
- `QuillMac/Cleanup` — guarded cleanup client + `CleanupGuard`
- `QuillMac/Platform` — global hotkey (Carbon), focused-field/injection (Accessibility + CGEvent),
  menu bar, login item
- `QuillMac/Security` — Keychain secret store
- `QuillMac/UI` — SwiftUI settings, setup wizard, history
- `QuillMac/App` — `@main`, app delegate, non-activating HUD panel
- `QuillMacTests` — pure-logic tests mirroring the Windows suite

## Needs a Mac to compile/run (platform-integration risks)

These are written against the correct APIs but are unverified on this Windows host:

- **Accessibility injection & focus detection** (`FocusedTextInjector`): `AXUIElementCreateSystemWide`
  + `kAXFocusedUIElementAttribute` for the focused element; `kAXSecureTextFieldSubrole` for password
  detection. Requires the runtime Accessibility grant (`AXIsProcessTrusted`). Per-app tree quirks
  (Electron/web views) need the on-device injection-matrix pass, exactly like Android/Windows.
- **Unicode typing via `CGEvent`** (`keyboardSetUnicodeString`, `.cgAnnotatedSessionEventTap`): the
  chunk size and event-tap choice should be validated across Cocoa/Electron/terminal editors; some
  editors may need per-character posting or a clipboard-paste rung.
- **`AVAudioEngine` capture + `AVAudioConverter`** to 16 kHz mono Int16: the tap buffer size,
  converter output-capacity math, and RMS windowing want a real-device check against the Windows
  NAudio behaviour (silence auto-stop timing especially).
- **Carbon `RegisterEventHotKey`** (`GlobalHotkeyService`): verify the key-code map and modifier
  translation (Alt→Option, Win/Cmd→Command) and conflict reporting.
- **`SMAppService` login item** and **notarization**: the app is intentionally **not sandboxed** (it
  must control other apps via Accessibility), so distribution is Developer ID + notarization (like
  Wispr Flow), not the Mac App Store. Hardened Runtime is enabled; the mic entitlement is set.
- **Non-activating HUD panel** focus behaviour: confirm the panel never steals key focus (so the
  synthesized keystrokes land in the underlying app).
