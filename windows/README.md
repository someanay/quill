# Quill for Windows

Fresh native Windows implementation of the current Android app. Requires Windows 10 or 11 on an x64 PC, a microphone, and the user's provider key. No Quill account or server.

## Install

Extract `Quill-Windows-2.0.0-win-x64.zip`, then run `Quill-Setup.cmd`. The installer verifies the packaged app's checksums and installs for your Windows user without administrator permission. Alternatively, run `app/Quill.exe` directly. The self-contained package includes .NET; no runtime download is required.

The installer registers Quill under Windows Installed apps. Uninstall removes only files owned by that installation and preserves settings, keys, history and unrelated files. The separately compiled `win-arm64` download targets ARM Windows PCs; it has not been executed on ARM hardware.

This build is unsigned. Windows may show its normal unknown-publisher prompt. The source and SHA-256 checksum are provided for review; no signing identity is fabricated.

## Use

Complete the same onboarding sequence as Android: 1.2-second splash, welcome, key-required notice (3-second reading gate), provider/key validation, setup checklist, microphone test, floating bubble, affirmative text-insertion disclosure, and all set. Windows desktop microphone settings replace Android's permission dialogs. The desktop overlay needs no separate permission grant.

Click a supported field, tap the bubble, speak, and tap again. A long press starts clipboard dictation. The shortcut shown in the header also starts/stops. Quill prefers **Ctrl + Win + Space**, falling back to **Ctrl + Alt + Space** or **Ctrl + Shift + Space** when Windows or another app reserves it. Choose a preference in Advanced. Drag the bubble to an edge; drag it to the bottom to hide for ten minutes. The Home switch hides it until explicitly shown again. Right-click supports clipboard dictation and discard. Tray actions restore the app or bubble.

The guided key form starts with Gemini, followed by Groq and OpenRouter, with the same numbered instructions as Android. OpenAI/custom providers are in a separate disclosure. Drafts are encrypted; validation starts automatically after typing pauses, and Save requires a verified key. Opening a row does not change the active speech provider. Speech setup activates the saved provider; adding a key under API keys preserves speech and cleanup choices.

Provider/model/language and saved free-model filters, independently configurable cleanup with model memory per provider, active multi-key storage, writing styles, custom words with 8-second voice capture, local contact-name import, microphone selection/test, 60–300-second session cap, private mode, history retention, bubble size, theme, startup and scrubbed local diagnostics are under Settings.

## Privacy and safety

Keys are encrypted with Windows DPAPI for the signed-in user and isolated by provider. Custom-server keys are additionally bound to the exact normalized HTTPS base URL, including its path. Changing the endpoint requires a separately validated key. Secrets, transcripts and provider responses never enter logs. HTTP redirects are rejected.

History uses local SQLite with secure deletion, defaults to 1 hour, supports 1 day/1 week/30 days/forever and a 1,000-row cap. Private sessions never save history, including when private mode is switched during a request. Temporary failed recordings are DPAPI encrypted for retry and removed after success or discard. Imported contact candidate names remain local and are not sent to providers. Only optional, scrubbed local crash reports are retained; exporting is an explicit user action.

UI Automation excludes passwords, sensitive field labels and credential apps before reading contents. Insertion preserves native undo and selection, verifies the exact expected field contents, and falls back to recoverable text when confirmation fails. The source target, text and selection must still match the start of the dictation. Modern Notepad receives supplementary Unicode through a temporary clipboard transaction because its Unicode keyboard packets corrupt surrogate pairs; the previous clipboard is restored only while Quill owns it, and the temporary item is excluded from Windows clipboard history/sync. Secure desktop/elevated/uncooperative apps may require manual clipboard paste. Automatic clipboard fallback expires after 60 seconds only while Quill still owns the clipboard; explicit History Copy and clipboard dictation remain until the user changes them.

## Architecture

`Domain` contains settings/defaults and pure policies; `Storage` handles atomic settings, DPAPI keys, SQLite history and bundled copy; `Audio` captures 16-kHz mono 16-bit WAV plus RMS; `Providers`/`ModelCatalog` implement the five direct provider APIs; `Dictation` serializes capture/transcription/cleanup/insertion/retry; `Native` implements bounded UI Automation, verified SendInput, clipboard ownership and global shortcut; WPF screens and bubble use actual Android colors and byte-identical IBM Plex fonts.

Current Android itself uploads WAV (its FLAC optimization remains deferred); Windows matches that behavior. Windows uses an actual UIA selection at position zero, preserving preceding/succeeding content instead of Android's placeholder workaround that replaces an entire field at caret zero. Per-app styles map Android's corresponding app defaults to native executable identities; Android-only package names have no executable equivalent. Windows additionally preserves a private recording's privacy flag across failures, retries and process recovery; current Android reevaluates settings on retry. Turning history off while a request is in flight also suppresses its history entry. Retired delivered recordings are never offered for retry if optional history or file cleanup fails.

Normal data folder: `%LOCALAPPDATA%\Quill\NativeV2`. Earlier draft app data is not modified or migrated silently. For isolated verification, use `Quill.exe --data-dir C:\path\to\isolated-data` or `QUILL_DATA_DIR`; this suppresses startup registry changes and enables isolated startup errors, shortcut registration results, and an initial screenshot/font diagnostic of Quill's own window. Use disposable test data for this mode. Normal app launches do not write those diagnostics.

## Build and verify

Install the .NET 10 SDK. From the repository root:

```powershell
dotnet build windows/Quill.Windows.slnx -c Release
dotnet test windows/Quill.Windows.slnx -c Release
./windows/installer/Publish-Quill.ps1
```

`Publish-Quill.ps1 -RunNativeSmoke` additionally tests actual UI Automation and SendInput against a separate controlled editor process. It needs an unlocked interactive desktop and temporarily focuses only that fixture. Tests refuse to type if another app owns focus. The harness restores clipboard data afterward. It is a separate test executable; production builds contain no authentication bypass or fake provider mode.

The local Copy Tool edits `Assets/copy/strings.default.json` and `windows/copy-manifest.json`; all 423 current Android keys and exact formatting signatures are retained, with Windows copy additions. No remote copy is fetched. Run `python windows/scripts/sync-copy.py` only when importing new Android keys or newly authored Windows defaults; existing Windows catalog edits are preserved.

See `VERIFICATION.md` for actual test evidence and remaining external verification requirements.
