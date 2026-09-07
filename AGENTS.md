# AGENTS.md — Quill

Quill is a **BYOK voice-dictation app for Android**: a floating bubble dictates into any text
field in any app; audio is transcribed by the **user's own** STT provider key, optionally cleaned
up by an LLM, and inserted into the focused field. Modelled on Wispr Flow.

**The five planning docs are the source of truth. Read them before changing anything:**
`README.md` (overview + how to work), `prd.md` (what/why, scope), `spec.md` (architecture,
permissions, security, Play risk), `todo.md` (the milestone state machine / task queue),
`testing_plan.md` (test shells + manual scripts + injection matrix).

The hard 80% of this app is **system integration**, not UI: reliable overlay, legally starting a
mic foreground service from the background, writing into focused fields across inconsistent apps,
surviving aggressive OEM battery managers, and passing Play review for sensitive permissions.

---

## Build / install / logcat commands

Run from the project root. `adb` is **not on PATH** — use the full path (alias below) or call it
from PowerShell.

```powershell
# adb (Windows)
$adb = "C:\Users\anayt\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# Build
./gradlew assembleDebug            # compile a debug APK (the fast feedback loop)
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew lint                     # Android lint

# Deploy to the physically connected phone
./gradlew installDebug             # build + install to the connected device

# Logs
& $adb logcat                      # all logcat
& $adb logcat -s Quill:* AndroidRuntime:E   # Quill tag + crashes only

# Launch / screenshot
& $adb shell am start -n com.tihor.quill/.MainActivity
& $adb exec-out screencap -p > shot.png
```

**Target device:** a physically connected phone — currently **HONOR BVL-N49, Android 16 (API 36)**,
serial `A5CS024313005887`. This is the aggressive-OEM daily driver and close to the worst case for
this app (MagicOS kills background services and silently revokes accessibility). **Test on the real
device, never an emulator** — emulators handle overlays, the accessibility service, and the mic
poorly and won't reproduce MagicOS's background-killing.

**Granting permissions for dev via adb (don't tap through Settings):**
```powershell
& $adb shell appops set com.tihor.quill SYSTEM_ALERT_WINDOW allow   # overlay
& $adb shell pm grant com.tihor.quill android.permission.RECORD_AUDIO
& $adb shell pm grant com.tihor.quill android.permission.POST_NOTIFICATIONS
# Accessibility service must be enabled via Settings.Secure (see M2):
& $adb shell settings put secure enabled_accessibility_services com.tihor.quill/com.tihor.quill.accessibility.QuillAccessibilityService
& $adb shell settings put secure accessibility_enabled 1

# Trigger text injection without the bubble (debug builds only — M3). Quote the WHOLE am line so
# the space in the text isn't parsed as a pkg= arg:
& $adb shell "am broadcast -a com.tihor.quill.DEBUG_INJECT --es text 'hello world'"
```

**Honor/MagicOS dev gotchas (learned on this device — save yourself the chase):**
- **Whitelist Quill before trusting any background behaviour.** MagicOS hides background-app
  *overlays* (the bubble shows over our own app but `isOnScreen=false` over others) and kills
  background services until Quill is added to **App launch management** (Auto-launch / Secondary
  launch / Run in background) + Battery **Unrestricted**. This is a physical Settings step (HONOR
  PowerGenie) — **not** settable via adb; `dumpsys deviceidle whitelist +` and `RUN_IN_BACKGROUND`
  appops do **not** override it. Verify overlay visibility with
  `adb shell "dumpsys window windows | grep -A40 'u0 com.tihor.quill}:'"` → look at `isOnScreen=`.
- **adb-injected `input tap`/`swipe` do not reach overlay windows.** They follow input focus, so
  they hit the Activity beneath a `FLAG_NOT_FOCUSABLE` overlay, never the bubble. Bubble gestures
  must be tested with a real finger; cover the math with unit tests (`BubbleGeometry`) instead.
- **Screenshots:** capture on-device then pull — `adb shell screencap -p /sdcard/x.png` +
  `adb pull`. Piping `adb exec-out screencap -p > x.png` through **PowerShell** corrupts the binary
  (UTF-16/BOM); use the Bash tool or the pull approach.
- The device secure-locks; visual checks need it unlocked. `adb shell svc power stayon true` keeps
  the screen on while charging.

---

## Toolchain (this project is on a bleeding-edge, future-dated stack)

- **AGP 9.2.1**, **Gradle 9.4.1**, **Kotlin 2.2.10** (app), **compileSdk 37**, minSdk 29,
  targetSdk 36, Java 11.
- AGP 9 has **built-in Kotlin** — the Kotlin compiler runs without separately applying the Kotlin
  Gradle plugin. Compose/serialization/KSP plugins still layer on top; mind version-matching to
  Kotlin **2.2.10**.
- The wizard pinned `compileSdk = release(36)`, but the future-dated AndroidX libs (`core` 1.19.0)
  require **compileSdk 37** — already bumped. `android-37` was auto-resolved by AGP.
- **Heads-up vs the planning docs:** the docs call this "an empty Jetpack Compose project," but the
  wizard actually produced a **View-based** project (appcompat/Material, no Compose, no
  `MainActivity`). Compose was added in M0. Verify versions empirically (the stack is past the model
  knowledge cutoff) — read build errors, don't guess.

---

## Milestone order (riskiest first — from todo.md)

Sequenced to **prove the riskiest things first** (overlay → accessibility injection → background
mic capture) before STT, UI, and polish. **M1–M6 = a working end-to-end happy path = the real proof
point.** Do **one milestone (often one sub-task) at a time**; never "build the whole app."

- **M0** — Project scaffold (deps + structure). *(NDK/FLAC deferred to M4.)*
- **M1** — Overlay bubble (**before** the recorder — the visible overlay is what legally lets the
  mic FGS start from the background; spec §5).
- **M2** — Accessibility service skeleton (disclosure/consent, focus logging, sensitive-field flag).
- **M3** — Text injection (resolve the *true* editable node → SET_TEXT → clipboard fallback ladder).
- **M4** — Audio capture in a mic foreground service (`AudioRecord` → WAV → **FLAC via NDK/JNI**;
  silence auto-stop; minimal keep-alive pulled forward for Honor). Whitelist Quill on Honor first.
- **M5** — STT integration (BYOK, OpenAI-compatible; run a `minifyEnabled true` build here).
- **M6** — 🎯 End-to-end happy path (capture → STT → injection). The v1 proof point.
- **M7** — Secrets & settings (Keystore AES-256-GCM; **not** the deprecated EncryptedSharedPreferences).
- **M8** — Onboarding + the **Copy Catalog** foundation (soft-coded copy, baked-in defaults).
- **M9** — Dictation history (local SQLite, P1, **on by default** with time-based retention —
  default 1 h, user-configurable or off; gated by private mode).
- **M10** — LLM cleanup pass (P1).
- **M11** — OEM/reliability hardening + opt-in crash reporting.
- **M12** — Play release readiness (declaration, demo video, data-safety, R8).
- **M13** — Soft-coded copy: OTA delivery + signing/authoring pipeline.

---

## Working rule

1. **Load the docs**, then **scope to exactly one milestone** from `todo.md`.
2. **Test-first where todo.md says "write first"** — author the failing/`TODO` test shells before
   the implementation (unit-testable logic per `testing_plan.md`). Overlay/accessibility/mic
   behaviour is verified by the **manual scripts on the real device**, not unit tests.
3. **Verification gate:** build + tests + the milestone's manual script must all pass before moving on.
4. **One green milestone = one commit** with a Conventional Commit message
   (`feat:` / `fix:` / `chore:` / `test:` / `docs:` / `refactor:`), e.g.
   `feat: insert text via accessibility set_text`. Keep diffs reviewable so rollback is cheap.
5. After the gate: **check the box in `todo.md`** and note any deviation.

If a milestone is too big for one pass, split into sub-tasks and run the loop per sub-task.

---

## Copy authoring: local source edits, app releases only

User-approved policy, September 7, 2026: all copy-tool catalog strings, including
permission rationales, privacy promises, and key-required warnings, may be edited
locally. Preserve required text and formatting placeholders. Review compliance copy
against actual behavior before releasing the rebuilt APK/AAB.

No remote copy updates are allowed. Installed apps resolve bundled text only;
changes reach users through a new app release. Accessibility disclosure remains in
Android XML resources and must remain accurate and present in the release binary.
The former compliance editing locks and OTA allowlist requirements are retired.
