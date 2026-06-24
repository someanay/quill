# Quill — Implementation Checklist (todo.md)

This is the project's **state machine** and Claude Code's task queue. **Update it as you go** —
check boxes, add notes, record deviations. Feed Claude Code **one milestone (often one sub-task)
at a time**; never "build the whole app."

Milestones are ordered to **prove the riskiest things first**: overlay → accessibility injection →
background mic capture, *before* investing in STT, UI, and polish. M1–M6 = a working end-to-end
happy path; that is the real proof point.

**Testing is a task, not an afterthought.** Every milestone carries an explicit **Test** checkbox.
Where the logic is unit-testable, **write the test shells first** (failing/`TODO`) before the
implementation, per `testing_plan.md`. Each milestone then ends with a **verification gate** — do
not proceed until build + tests + the manual script all pass, then commit.

---

## M0 — Project scaffold ✅ (commit `chore: project scaffold (M0)`)
- [x] Create Android Studio project (Kotlin, Compose, **`applicationId com.tihor.quill`**, minSdk 29, targetSdk 36)
- [x] Gradle Kotlin DSL + version catalog; add Coroutines, Retrofit/OkHttp, **kotlinx.serialization**, DataStore — see deviations below
- [x] Package structure (`overlay/`, `accessibility/`, `audio/`, `stt/`, `secrets/`, `settings/`, `history/`, `ui/`) — empty pkgs hold a `.gitkeep`
- [x] Base `AndroidManifest.xml` (permissions per `spec.md` §5), empty launcher Activity (`MainActivity`, Compose)
- [ ] ~~Lint/format config; GitHub Actions build-on-PR~~ — deferred (optional; no remote/CI set up yet)
- [x] **Test (harness):** wired JUnit + Robolectric + MockWebServer + Compose-test deps; two trivial passing tests (`SmokeTest`, `RobolectricSmokeTest`)
- [x] **Verify:** `clean assembleDebug` + `testDebugUnitTest` green; `installDebug` ok; `MainActivity` launches top-most. *(Visual peek at the blank screen pending a device unlock — phone is secure-locked; build/install/launch confirmed via gradle + `dumpsys window`.)*

> **M0 deviations (recorded per the working rule):**
> - **The wizard project was View-based, not Compose** (no `MainActivity`, appcompat/Material theme). Added Jetpack Compose (BOM `2026.06.00`, activity-compose 1.13.0, Material 3) + a minimal `QuillTheme`; switched the XML window theme to a platform parent so we don't pull in the Material *View* library.
> - **compileSdk 36 → 37**: the future-dated `androidx.core 1.19.0` requires compileSdk 37 (AGP auto-resolved `android-37`). targetSdk stays 36.
> - **Toolchain is bleeding-edge / future-dated**: AGP 9.2.1, Gradle 9.4.1, Kotlin 2.2.10 with AGP's **built-in Kotlin**. Library versions were resolved empirically (build errors, not guesses): coroutines 1.11.0, serialization-json 1.11.0, retrofit 3.0.0, okhttp 5.4.0, datastore 1.3.0-alpha09 (stable lags this stack).
> - **Hilt deferred → manual DI.** `spec.md` §1 explicitly allows manual DI ("acceptable given the small surface"). Avoids KSP version-matching fragility against AGP-9-built-in-Kotlin and honours the "early builds simple" instruction. Revisit only if DI grows unwieldy.
> - **Room deferred to M9.** Room (KSP) is only needed for history (P1, M9); wiring it now buys nothing and adds an annotation processor to every early build.
> - **NDK / libFLAC encoder deferred to M4** (per the explicit instruction to keep early builds simple). FLAC encoding is a pre-upload step needed first at M4/M5.

## M1 — Overlay bubble (do this before the recorder) — code/build/install ✅, visual verify pending unlock
- [x] Overlay-permission request flow (`ACTION_MANAGE_OVERLAY_PERMISSION`) + state check (`OverlayPermission`; `MainActivity` requests + re-checks on return)
- [x] Draggable bubble via `WindowManager`, appears over other apps; dismiss via long-press (`OverlayService` + `BubbleView`). *Snooze stubbed into dismiss for M1.*
- [x] Bubble visual states stubbed: idle / recording / transcribing / paste / error (`BubbleState`; tap cycles them; contrast-safe per spec §3.1 — drop shadow + white ring + state-coloured fill)
- [x] **Test:** drag/snap/tap geometry isolated into pure `BubbleGeometry` + unit-tested (`BubbleGeometryTest`, 8 cases, green). Overlay rendering itself is the manual script (`testing_plan.md` §2).
- [~] **Verify:** Confirmed via adb on the Honor device — bubble **renders** (contrast-safe slate disc + white ring, screenshotted), **snaps to the right edge** on show (x = screenWidth − bubbleWidth), permission flow works, and the **overlay window persists across backgrounding** once it's an FGS (`dumpsys window`: same window id before/after opening Settings). **Still needs hands-on finger testing** (see below) — folded into the M3 physical-test stop.

> **M1 notes & device findings (Honor BVL-N49 / MagicOS, Android 16):**
> - **Pulled the FGS forward from M4 to M1.** A plain *started* service's overlay was trimmed by
>   MagicOS the instant another app came forward — failing M1's "float over other apps". Fixed by
>   running `OverlayService` as a foreground service now (`specialUse` type + low-priority
>   notification; flips to `microphone` at M4 when it owns the recorder). Window now survives
>   backgrounding.
> - **MagicOS hides background-app overlays until Quill is whitelisted.** Even as an FGS, the overlay
>   shows over our *own* app but `isOnScreen=false` over other apps until Quill is added to MagicOS
>   **App launch management** (Auto-launch / Secondary launch / Run in background) + Battery
>   **Unrestricted**. This is the documented Honor setup (`testing_plan.md` §2) the plan schedules
>   before M4; it's needed at M1 here. Not settable via adb (HONOR PowerGenie, not a standard appop);
>   doze-whitelist + RUN_IN_BACKGROUND appops did **not** override it.
> - **adb-injected taps/swipes can't drive the bubble.** `input tap/swipe` follow *input focus*, so
>   they land on MainActivity beneath the `FLAG_NOT_FOCUSABLE` overlay, never the bubble. Real finger
>   touches hit-test to the top window. So tap-cycle / drag / long-press are verified by the
>   unit-tested geometry (`BubbleGeometryTest`) + **physical** testing, not adb.
> - Overlay permission granted for dev via `adb shell appops set com.tihor.quill SYSTEM_ALERT_WINDOW allow`.

## M2 — Accessibility service skeleton
- [ ] `accessibility_service_config.xml` per `spec.md` §4 (no `isAccessibilityTool`)
- [ ] Prominent-disclosure + affirmative-consent screen **before** sending the user to enable it
- [ ] Enable flow (`ACTION_ACCESSIBILITY_SETTINGS`) + enabled-state detection
- [ ] On focus change, log focused editable node's package/class + sensitive-field flag
- [ ] **Test (write first):** unit-test the sensitive-field classifier (node props → `isSensitive`); manual focus-logging script for real apps
- [ ] **Verify:** focusing a field in WhatsApp/Gmail logs the node + package; password field flagged sensitive.

## M3 — Text injection (hardcoded string)
- [ ] `TextInjector`: **resolve the true editable node** (SET_TEXT-capable / `isEditable`, not the focus wrapper — see `spec.md` §5 blueprint), then read text + selection → compose → `ACTION_SET_TEXT` → `ACTION_SET_SELECTION`
- [ ] **Dump real nodes** (Accessibility Scanner / `uiautomator dump`) for each matrix app on the Honor device and tune the resolver before declaring M3 done
- [ ] Clipboard fallback as a **user-initiated "Tap to Paste"** state; set `EXTRA_IS_SENSITIVE`; **restore the prior clip only after a *confirmed* paste** (re-read the node's text/selection to verify the transcript landed). On a **manual** paste, offer a short-lived **"restore previous clipboard"** affordance instead — **never a silent timed restore** (it races the user's paste; see `spec.md` §5 ladder)
- [ ] Sensitive-field guard: refuse to write password/banking fields
- [ ] **Test (write first):** unit-test the insert-at-cursor composition (existing text + caret + transcript → expected string, incl. spacing rules); the cross-app matrix in `testing_plan.md` §3 is the real gate
- [ ] **Verify:** tapping the bubble inserts "hello world" into the focused field in **≥3 apps** (incl. Chrome/WebView); fallback path proven where SET_TEXT is rejected; password field untouched.

## M4 — Audio capture in a microphone foreground service
> **Before starting M4, manually whitelist Quill on your Honor dev device** (App launch → Manual →
> enable all three; Battery → Protected/unrestricted). See `testing_plan.md`. Without this, MagicOS
> kills the service mid-development and you'll chase ghosts.
- [ ] `OverlayService` as FGS `type=microphone` + persistent notification
- [ ] `AudioRecorder(input = handset default)` — **`AudioRecord`** 16 kHz mono PCM → WAV in cache, then **encode to FLAC** (**libFLAC via NDK/JNI** — Android has no one-call PCM→`.flac` and `MediaMuxer` exposes no FLAC output; spec §2.2) before upload; route input through the `AudioRoutingManager` seam so BT drops in later (decision: `spec.md` §2.2)
- [ ] Start recording from bubble tap (background-start permitted via the visible overlay)
- [ ] Mic runtime-permission handling; session cap (default 300 s) with pre-limit warning
- [ ] **Silence auto-stop (v1):** compute RMS from the capture frames; auto-stop after a conservative quiet window (tune so mid-sentence pauses don't cut off) — guards against forgotten-stop uploads
- [ ] **Minimal keep-alive (pulled forward from M11 because Honor needs it now):** sticky FGS +
      battery-optimisation-exemption prompt + auto-restart on app foreground
- [ ] **Test (write first):** unit-test the WAV header (16 kHz / mono / 16-bit) and the session-cap timer; run the Honor survival script in `testing_plan.md`
- [ ] **Verify:** tap → records → valid WAV saved; works with screen on; revoking mic mid-session does **not** crash; **service survives backgrounding on the Honor device**.

## M5 — STT integration (BYOK)
- [ ] `TranscriptionClient` interface + `OpenAiCompatibleTranscriptionClient` (base URL + model)
- [ ] Multipart WAV upload; parse transcript; typed error model (network / 401 bad-key / provider)
- [ ] **R8 shift-left:** use **kotlinx.serialization** for the transcription JSON models (compile-time → minimal keep-rules) plus any `-keep` the serializers need; **run a `minifyEnabled true` build now** so serialization breakage surfaces here, not at M12
- [ ] **Test (write first):** `TranscriptionClientTest` shells with MockWebServer — success / 401 bad-key / 5xx — authored **before** the client; include the parse test on a minified build
- [ ] **Verify:** unit tests green on debug **and** minified build; manual: a real key returns text for recorded audio.

## M6 — End-to-end happy path 🎯
- [ ] Wire capture → STT → injection through the bubble
- [ ] States flow correctly: recording → transcribing → inserted (or fallback)
- [ ] **Test:** the end-to-end manual script across ≥3 apps (this one is inherently manual)
- [ ] **Verify:** speak → text appears in the focused field across ≥3 apps. *This is the v1 proof point.*

## M7 — Secrets & settings
- [ ] `SecretStore`: Keystore AES-256-GCM; encrypt key blob; exclude from Auto Backup
- [ ] API-key entry screen (validate by a test transcription)
- [ ] `SettingsRepository` (DataStore): provider, model, language hint, private mode, session cap
- [ ] **Test (write first):** `SecretStore` round-trip (encrypt→store→retrieve == original; ciphertext ≠ raw); `SettingsRepository` persistence; backup-exclusion check
- [ ] **Verify:** key persists encrypted, survives restart, excluded from backup, never in logs; settings round-trip.

## M8 — Onboarding flow
- [ ] Sequential permission steps (mic → overlay → accessibility-with-disclosure), plain-language copy
- [ ] **Soft-coded copy foundation (used by every screen):** strings read by key via the **Copy Catalog** — `CopyRepository` + baked-in `assets/copy/strings.default.json` + a Compose `LocalCopy`; **no string literals in composables** (spec §2.3). Compliance strings (accessibility disclosure, permission rationales, privacy promises, BYOK key warning) live **only** in the binary and are excluded from any future remote override.
- [ ] Microphone test (level meter); deep links into the right Settings screens
- [ ] Recovery cards for any revoked permission
- [ ] **Test:** Compose UI tests (advances only when each permission granted; key-field validation rejects empty); manual cold-install run-through
- [ ] **Verify:** a cold install runs end-to-end to first successful dictation without external help.

## M9 — Dictation history (P1, opt-in)
- [ ] Room `transcripts` table + DAO; list screen (Compose)
- [ ] Gate all writes behind private mode; delete-all + per-item delete
- [ ] **Retention cap:** keep most recent ~1,000 entries, prune oldest (`historyCap`)
- [ ] **Test (write first):** Room DAO insert/query/delete; **no rows written when private mode on**; retention prunes oldest beyond the cap
- [ ] **Verify:** entries logged when on, none when private mode on, deletion + pruning work.

## M10 — LLM cleanup pass (P1)
- [ ] `CleanupClient` (chat-completions) + toggle + optional separate LLM key
- [ ] System prompt: filler removal, punctuation/capitalisation, (optional) vocabulary substitution
- [ ] **Test (write first):** returns raw text unchanged when disabled; cleanup failure → raw fallback (never blocks insertion); vocabulary substitution (P1.5)
- [ ] **Verify:** cleaned output when on, raw transcript when off; cleanup failure falls back to raw, never blocks insertion.

## M11 — OEM / reliability hardening
- [ ] Battery-optimisation exemption prompt; best-effort OEM auto-start deep links via a helper lib
      (e.g. `betterypermissionhelper` / `AppKillerManager`) that targets **Honor/MagicOS** explicitly
- [ ] Overlay/accessibility revocation detection — **on-app-foreground re-check** (a revoked service can't observe its own revocation), plus a **best-effort periodic check where the OEM permits**; high-priority notification deep-linking to the exact setting (`spec.md` §7). **No real-time guarantee on aggressive OEMs — alert may only surface on next foreground.**
- [ ] Auto-restart bubble on app foreground + after boot (`BootReceiver`)
- [ ] Empty/loading/error states finalised
- [ ] **Opt-in crash reporting:** uncaught-exception handler writes a **scrubbed** local report (no transcript/audio/clipboard/keys); next-launch dialog offers review + send via the share sheet; decline discards (spec §6)
- [ ] **Test:** run the OEM/permission + Honor/MagicOS survival & revocation scripts in `testing_plan.md`; force a crash → relaunch → prompt appears → report is scrubbed → send/decline both work
- [ ] **Verify:** against those scripts; service recovers after force-stop.

## M12 — Play release readiness
- [ ] Accessibility declaration (Play Console) + demo video + in-app disclosure copy finalised
- [ ] Foreground-service-type declaration (App Content); data-safety form **(declare the read-only copy-bundle GET — no user data sent; see §2.3 privacy delta)**
- [ ] R8/ProGuard rules; release signing; internal testing track
- [ ] **Test:** R8/minify build runs the full app without reflection/serialization breakage; data-safety form matches actual behaviour; full regression on stock + Honor
- [ ] **Verify:** pre-launch report passes; declaration submitted. See `spec.md` §8 for the full checklist.

## M13 — Soft-coded copy: OTA updates + authoring pipeline
*The foundation (`CopyRepository`, baked-in `strings.default.json`, Compose `LocalCopy`) lands in M8; this milestone adds **remote delivery**. If OTA ships in v1, its data-safety + privacy-policy items must be settled alongside M12; otherwise OTA is a clean **1.1 fast-follow** with soft-coding already in place.*
- [ ] `CopyUpdater`: launch-time, off-main GET of `/copy/{locale}.json` (+ `.sig`); short timeout; silent failure; **never on the UI critical path**
- [ ] **Ed25519 signature verify** against a public key **pinned in the binary** (Tink or `java.security`); reject unsigned / bad-sig / tampered bundles
- [ ] On-device **schema validation** (required keys, length caps, **placeholder-arg parity**) + **monotonic-revision** rollback guard; apply **atomically** to the cache or not at all
- [ ] **Locked-key allowlist** enforced: a remote bundle can never override the accessibility disclosure, permission rationales, privacy promises, or the BYOK key warning (spec §2.3 / §6)
- [ ] **"Check for updated text"** toggle in Advanced (default on); privacy-policy line covering the read-only fetch
- [ ] **CI guard:** build fails if `strings.default.json` is missing any key referenced in code (layer-1 completeness); lint guard against string literals in composables
- [ ] **Authoring tool** (separate from the app — recommend a small **Next.js page on Vercel**): edit copy by key/screen, show baked-in default read-only beside an editable override, char caps, placeholder hints, lock badges; validate; bump `revision`; **commit plaintext** to the repo
- [ ] **Publish pipeline:** a GitHub Action signs the bundle with the Ed25519 **private key (CI secret only)** and deploys `en.json` + `.sig` to the static host — private key never in the app **or** the authoring browser
- [ ] **Test (write first):** offline/airplane cold-start renders the full UI from baked-in (no blank keys); tampered / invalid-sig bundle rejected; lower-or-equal `revision` ignored; a locked key inside a bundle is ignored; placeholder-parity + schema failures reject the whole bundle; toggle-off disables the fetch
- [ ] **Verify:** with the toggle on and a newer valid bundle published, the app fetches and applies it on next launch; in airplane mode the UI is identical and unbroken from baked-in defaults.

---

### Working rule
After each verification gate passes: review the diff, run the build + tests/manual script, then
`git commit` with a clean conventional message (e.g. `feat: insert text via accessibility set_text`).
One green milestone = one commit. If a later step breaks, you can roll back cheaply.
