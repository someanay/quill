# Quill for Windows

Native Windows port of Quill's BYOK dictation pipeline. It keeps the Android app's **Ink & Signal**
design and privacy model, but replaces the floating bubble with a system-wide shortcut and a
non-activating dictation ribbon.

The first normal launch opens a five-step setup guide covering the product tour, provider/key,
shortcut, privacy choices, and first dictation. Dark mode is the default; light mode is available in
Settings and applies on the next launch. Home/setup use separate 16:9 desktop demo carousels; see
`docs/carousel-content-spec.md` for the asset folders and encoding contract.

## Current core path

1. Press **Alt+Q** (the editable default) in any ordinary text field.
2. Quill captures the focused window and refuses fields exposed as passwords by Windows UI Automation.
   If no editable text field is focused (desktop, a button, a read-only view), the dictation is
   diverted to the clipboard instead of typing, and the ribbon shows a toast saying so.
3. NAudio records 16 kHz, 16-bit mono PCM to a temporary WAV.
4. Press the shortcut again, wait for silence auto-stop, or reach the configured 15-second-step cap
   (up to 10 minutes, or uncapped).
5. The WAV goes directly to the selected BYOK provider: Groq, OpenAI, OpenRouter, Gemini, or a custom
   OpenAI-compatible HTTPS endpoint.
6. Optional provider-specific cleanup uses the model selected during setup or in Settings; it is
   fail-soft and guarded by the same token-overlap policy as Android.
7. Custom word replacements run last.
8. Unicode `SendInput` types into the captured window. If Windows integrity isolation blocks it, the
   transcript is copied to the clipboard.
9. The temporary WAV is deleted. History is retained locally for one hour by default, unless private
   mode or history-off prevents the write.

## Security boundaries

- API keys are isolated per provider and encrypted with Windows DPAPI for the current user.
- Quill has no account and no backend for user audio, transcripts, or keys.
- Recording is user-triggered and never starts merely because Quill launches.
- Password fields are refused before recording and again before insertion.
- The app runs unelevated. Windows intentionally prevents it from injecting into elevated apps; the
  clipboard fallback handles that case without asking Quill to run as administrator.
- Compliance promises are compiled into the application; this port does not implement remote copy.

## Build and test

Requires the .NET 10 SDK on Windows 10/11.

```powershell
dotnet restore windows/Quill.Windows.slnx
dotnet build windows/Quill.Windows.slnx --configuration Debug
dotnet test windows/Quill.Windows.slnx --configuration Debug
dotnet run --project windows/src/Quill.Windows/Quill.Windows.csproj
```

DPAPI tests need a normal interactive user profile; highly restricted sandboxes can report that the
profile is unavailable even though the same test succeeds for the signed-in user.

## Installable build

Create a self-contained Windows installer (no separate .NET runtime required):

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\installer\Publish-Quill.ps1
```

The generated `windows/artifacts/Quill-win-x64-Setup.exe` installs per-user under
`%LOCALAPPDATA%\Programs\Quill`, creates a Start Menu shortcut, registers an uninstall entry, and
launches the existing first-run setup wizard. The local artifact is unsigned; production releases
should use a trusted code-signing certificate.

## Layout

- `src/Quill.Windows/Audio` — microphone capture, WAV lifecycle, silence/session limits
- `src/Quill.Windows/Stt` — provider contracts and response/error mapping
- `src/Quill.Windows/Cleanup` — optional cleanup and structural guard
- `src/Quill.Windows/Core` — settings, history, models, and end-to-end orchestration
- `src/Quill.Windows/Platform` — global hotkey, focused-field safety, insertion, tray/startup
- `src/Quill.Windows/SetupWindow.*` — guided first-run and repeatable setup wizard
- `src/Quill.Windows/Security` — per-provider DPAPI secret storage
- `tests/Quill.Windows.Tests` — pure pipeline and persistence tests

## Still requiring physical Windows verification

- Real microphone capture and silence auto-stop across the user's audio devices
- Injection matrix: Notepad, Office, Chromium, Firefox, Electron, UWP/WinUI, and elevated apps
- Every live provider with real user keys, quota states, and long recordings
- Multi-monitor/DPI placement of the dictation ribbon
- Startup registration after publishing to a stable install path
- Branded `.ico`, code signing, and Windows accessibility review

The tray icon currently uses a system placeholder. It must be replaced with final Quill artwork before
distribution.
