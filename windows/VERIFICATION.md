# Approved native design — October 20, 2026 (version 2.0.3)

The approved design is implemented in native WPF across setup, workspace, history,
settings and provider forms. IBM Plex typography and Quill colours match Android;
copy describes Windows shortcuts, microphone settings and UI Automation consent.
Native caption appearance follows the selected theme. Forms use aligned open
sections, with framing reserved for editors and separate credentials.

Verification at this presentation revision:

- Full .NET suite: **362 passed, zero skipped**. A separate native foreground
  launcher exercises the Windows activation-permission handoff to the existing
  process. Normal and maximized reopening require the same process, window,
  foreground handle and preserved maximized state. No foreground assertion is skipped.
- **300 native renders passed**: 13 destinations, seven setup stages and five
  provider forms, in Light/Dark at 640×600, 1000×500, 1200×800, 1600×900,
  900×1200 and 1920×650. Original images and bounds results are in
  `dist/native-redesign/windows-final/`.
- **42 setup checks passed**, including preserved window size, live key form,
  practice editor text/selection and keycaps refreshing with the actual shortcut.
  Evidence: `dist/native-redesign/windows-final-setup/result.json`.
- All 14 controlled native insertion/clipboard checks passed. The physical
  microphone capture and lifecycle probes passed without upload or retained audio.
  Evidence: `dist/windows-native-revision/`.
- Ten installer integrity, upgrade and uninstall boundary checks passed, preserving
  the actual installation registration and unrelated files.
- The real per-user installation is version 2.0.3 under
  `%LOCALAPPDATA%\Programs\Quill`; its Start shortcut and Installed apps entry
  are verified. Close-to-tray and same-instance reopening are exercised separately
  from portable test processes. Package hashes are recorded with installation evidence.
- Catalog/default/placeholder/font checks pass. ARM64 is cross-compiled; hardware
  execution and live provider requests require environments not present here.

See `docs/native-design-review.md` for the concept comparison and corrections.
Earlier dated results below describe their own versions and are historical.

# Setup window sizing — version 2.0.2, September 9, 2026

Setup is locked to a compact monitor-scaled window, with a 600×650 logical-pixel baseline. It respects the monitor work area and Windows DPI, recenters on monitor/DPI changes, and disables resize/maximize only during setup. Welcome spacing is tightened. Opening Home restores the desktop size/resizing; reviewing setup from the desktop also restores its prior maximized state afterward.

Validation: Release build passed with zero warnings/errors. The actual fresh-process splash/Welcome regression passed. The setup-window harness checked all eight steps for constant locked dimensions, fit within the work area, and restoration of desktop dimensions/resizing, saving eight renders under `dist/windows/verification/setup-window/`. Live computer-use review confirmed the compact Welcome window and disabled maximize button. Multiple physical monitors/DPI transitions were not available for direct testing.

# Windows desktop layout — September 9, 2026

Version 2.0.1 replaces the portrait shell with desktop navigation, responsive Home/Settings groups, compact controls and a split history browser. Dictation, providers, storage and consent behavior are unchanged. Existing bundled copy is reused; no remote updates or new copy-tool schema are required.

Validation for this change:
- Release solution builds with zero warnings/errors.
- 162 native WPF renders: 14 destinations, eight onboarding steps and five provider states, in Light/Dark at 780×560, 1060×700 and 1440×900. Destination navigation is exercised through the actual sidebar button handlers. Checks cover horizontal control bounds, compact multiline history rows, complete selected transcripts and reader bounds. Evidence: `dist/windows/verification/desktop-layout/`.
- 360 of 361 .NET tests passed. The existing shortcut-reopen test successfully reopened the original window but failed its foreground-window assertion: Affinity remained foreground on this active desktop. The assertion has not been weakened or skipped. Evidence: `windows/tests/Quill.Windows.Tests/TestResults/desktop-layout-tests.trx`. This is not an all-green suite.
- All 14 controlled native editor/clipboard checks passed again: `dist/windows/verification/desktop-native-smoke.json`.
- Live Windows Home and Settings were reviewed with computer use. Review caught a stale Home shortcut label after fallback registration; the final code refreshes it when registration changes.
- Version 2.0.1 x64 was installed with the verified package installer; keys/settings were preserved. The actual installed window was closed into the tray and relaunched through computer use: original PID 5300 / HWND 133702 returned, with no second instance. Ten installer integrity/uninstall-boundary checks passed. Both x64 and ARM64 archives include checksums.
- No live provider requests or new microphone capture were needed for this presentation change. ARM64 packaging does not establish ARM hardware execution.

Earlier release evidence follows; its all-green test counts apply to September 8, not this run.

# Windows verification — September 8, 2026

The fresh Windows app was built and exercised on the local Windows x64 host with .NET SDK 10.0.400/runtime 10.0.11. This report distinguishes real native checks from mocked provider checks. No live provider credentials were used.

## Completed

- **361 automated .NET tests passed, zero skipped.** The suite includes all 195 cleanup guard vectors exported from the actual compiled Android implementation; provider request/authentication/response contracts; malformed, blocked and truncated cleanup; the four-second cleanup deadline; a stalled streamed body timeout and caller cancellation; model capability, pricing and ranking rules; Android cleanup-setting transitions and model memory; saved copy placeholder vectors; DPAPI encryption and multi-key isolation including exact custom endpoints; SQLite retention, secure deletion and privacy; and serialized dictation success, failure, cancellation and retry.
- Regression tests prove private provenance survives retry and restart; changing history off prevents new writes; optional SQLite failure and deletion failure cannot make delivered audio retryable; late microphone-stop callbacks cannot restart capture; no-field capture is refused while explicit clipboard dictation works and persists.
- **14 native controlled-editor checks passed.** These use real cross-process Windows UI Automation and Unicode SendInput against a separate test editor, preserving and restoring the clipboard. Cases cover Android append spacing, insertion in the middle, selected replacement, Hindi/emoji, password refusal, focus changes, read-only refusal, same-field caret changes, same-field text changes, clipboard fallback, owner-marker replacement, preservation of text/rich clipboard data and temporary clipboard-history exclusion. Report: `dist/windows/verification/native-smoke.json`.
- **Real Chromium and modern Notepad insertion passed.** An isolated Chrome profile loaded the local `windows/tests/fixtures/insertion.html` page. Mixed ASCII/Hindi/emoji append, textarea selection replacement, contenteditable insertion and dummy-password refusal passed. A new Notepad document passed mixed-language append and selected replacement, with clipboard preservation recorded. Existing user tabs/documents were not used; both disposable test windows were closed. Reports: `chrome-append.json`, `chrome-selection.json`, `chrome-rich.json`, `chrome-sensitive.json`, `notepad-append-fixed.json`, `notepad-selection-fixed.json` and `notepad-final-transaction.json` under `dist/windows/verification`.
- The Notepad check caught genuine supplementary Unicode packet corruption; independent Copy retrieval matched UI Automation's replacement characters. ASCII and Hindi-only packets worked. Notepad supplementary characters now use a clipboard transaction chosen before delivery, and exact field contents must still verify. Null/unrenderable formats, clipboard changes during delayed rendering, restore failures and partial Ctrl+V dispatch have focused regressions. A partial dispatch releases only Quill's unmatched keys and never resends paste. Restoration failures retry on the original STA while retaining the full prior clipboard snapshot; a newer clipboard owner ends retries, and a still-owned transport payload is cleared after 60 seconds if restoration remains unavailable. This expiry is best-effort if Windows itself prevents clipboard access.
- **Guided setup regressions passed:** 650ms debounce, stale validation cancellation, status-only authenticated requests, temporary network failure versus rejection, encrypted drafts/expansion, endpoint-separated custom drafts, save-only provider activation, preferred free model selection, and Keys>Add preserving speech/cleanup. The shared Gemini/Groq/OpenRouter accordion is used by onboarding and Speech; other providers sit in a separate disclosure. Explicit Paste is the only key setup action that reads clipboard payload.
- **54 WPF screen renders passed**, including all normal screens, onboarding and each guided provider expanded in both themes. Images and results are under `dist/windows/verification/guided-final-screens`.
- **Real local microphone capture passed:** 62 PCM callbacks produced a valid 98,862-byte 16-kHz mono 16-bit WAV in memory. No recording was retained or uploaded. Report: `dist/windows/verification/microphone-check.json`. Native UI microphone controls were also exercised during the parent review. An additional real lifecycle run observed exactly one silence-stop event at 5.56 seconds, followed by a fresh 0.76-second capture/cancel, no callbacks after either stop, and an empty repeated stop. Audio stayed in memory and was cleared; no upload occurred. Report: `dist/windows/verification/microphone-lifecycle.json`.
- **Real shortcut registration passed.** The preferred Ctrl+Win+Space chord returned Win32 error 1409 on this host with no other Quill process running. Ctrl+Alt+Space registered successfully. The app shows the actual available chord and supports changing preference. Report: `dist/windows/verification/hotkey-check.json`.
- **Native UI review:** first launch, key-required reading gate, blank-key rejection, Home, grouped Settings, themes, provider dropdown, contact settings and microphone test were exercised. Review found and fixed dark dropdown contrast, a sealed WPF style startup failure, misleading shortcut state, stale microphone callbacks and earlier layout/copy differences. Final normal and maximized guided Speech screenshots are `dist/windows/verification/final-speech-normal.jpg` and `final-speech-maximized.jpg`.
- **Font evidence:** byte-identical Android IBM Plex files are bundled. Normal and semibold typefaces resolve, and actual live-window GlyphRunDrawing inspection reports IBM Plex Sans for the wordmark/body/navigation and IBM Plex Serif Var for headings. The computer-use screenshot differed visually from WPF's own rendering capture at bold weights; both artifacts were retained. A software-rendering experiment did not resolve that discrepancy and was reverted; no system font/cache settings were changed.
- Dependency vulnerability scan reported no vulnerable packages from the configured NuGet source after updating the SQLite native bundle to 3.0.2. Full .NET, Windows Desktop, NAudio, SQLitePCLRaw and Microsoft.Data.Sqlite license notices ship with the app.
- Actual multi-process tests verify a second shortcut launch exits cleanly and reopens the original PID/window, while a separate data directory stays independent. A current-user, session and normalized data-directory event queues activation during cold startup; mutex ownership is acquired without waiting, including after an abandoned owner. No duplicate recorder is created.
- The final Welcome screen uses the Android wordmark/title/body/privacy pill, Get started and Already set up?/Open Quill footer. Native review verified the key-warning three-second reading gate, Not now returning to Welcome, and Open Quill reaching Home while leaving missing keys and insertion consent unchanged. Dictation guards remain covered. Evidence: `welcome-warning-review.json`, `welcome-aligned-light.jpg`, `key-warning-reading.jpg` and `key-warning-ready.jpg`.
- A dedicated fresh-process first-run regression now launches the real app with no seeded settings, waits beyond the splash delay, and verifies the rendered Welcome action and live process. It reproduced a startup cross-thread exception before the fix: the constructor started an asynchronous splash delay before WPF installed its synchronization context. A one-shot DispatcherTimer now keeps the transition on the owning UI thread. Earlier install round-trips seeded onboarding-complete settings and therefore missed this first-run path.
- A pristine self-contained publish was launched successfully, including native SQLite and tray initialization. This check found and fixed a single-file packaging issue: externally read copy, icon and license assets must be explicitly excluded from the executable bundle. Report: `dist/windows/verification/pristine-launch.json`.
- The parent task's installer suite passed **11 actual PowerShell 5.1 checks** for payload verification, upgrades, owned-file removal, registration/unregistration, untracked data preservation, tampering, missing/duplicate inventory entries and path traversal. Tests used a dedicated QA destination and removed their temporary registration.

The test project writes `windows/tests/Quill.Windows.Tests/TestResults/windows-tests.trx`. Native smoke and diagnostic programs are separate test executables and are not shipped as production authentication bypasses.

## Limits of the evidence

Provider networking, model responses, credits and cleanup failure paths were tested through injected HTTP fixtures. No paid/free live provider account, service quota or real spoken transcription accuracy has been verified. Native text integration was verified in a controlled WPF editor, a local Chrome page and new Notepad documents; this does not establish every browser, Office, Electron, terminal, elevated app or Windows security surface. An unlocked desktop and a real microphone were available; other PC hardware, Windows versions, screen readers and multi-monitor DPI arrangements were not exhaustively exercised.

The downloadable packages are unsigned. The current machine is x64; an ARM64 cross-publish is compiled-only and cannot establish execution on ARM hardware. Microsoft Store submission/signing are not verified. The old ignored `windows/artifacts` draft binaries remain on disk because automatic approval review rejected their deletion, including a later enumerated deletion attempt. Fresh source, build outputs and packaging do not reuse those draft binaries.

Installer and final package verification are recorded by the parent task alongside its isolated install/uninstall checks. The self-contained publish procedure reruns the automated suite before generating the app package and checksums.
