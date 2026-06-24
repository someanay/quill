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

## M2 — Accessibility service skeleton ✅ (commit `feat: accessibility service skeleton (M2)`)
- [x] `accessibility_service_config.xml` per `spec.md` §5 (no `isAccessibilityTool`; `typeViewFocused|typeWindowStateChanged`, `flagDefault|flagRetrieveInteractiveWindows`, `feedbackGeneric`, `canRetrieveWindowContent`, description)
- [x] Prominent-disclosure + affirmative-consent screen **before** enabling (`AccessibilityDisclosureActivity`); copy is **binary-only** compliance text in `strings.xml` (hard rule)
- [x] Enable flow (`ACTION_ACCESSIBILITY_SETTINGS`) + enabled-state detection (`AccessibilityUtil.isServiceEnabled` via `Settings.Secure`; `MainActivity` re-checks on resume)
- [x] On focus change, log focused editable node's package/class + sensitive-field flag (`QuillAccessibilityService`)
- [x] **Test (write first):** `SensitiveFieldClassifierTest` (6 cases, green) — password flag + text/number password inputType variations → sensitive; ordinary text/email/number/phone → not
- [~] **Verify:** Confirmed on the Honor device via adb — service **connects** (`Quill.A11y: Service connected.`), enabled-detection works, and focusing Chrome's native omnibox logs `pkg=com.android.chrome class=EditText inputType=0xa0001 sensitive=false`. **Native** editable resolution works.

> **M2 notes:**
> - **Web inputs don't resolve yet** — focusing a Chrome *web* password field surfaced only a wrapper
>   `FrameLayout`, not the editable node. That's the exact WebView complexity spec §5 calls out as an
>   **M3** task ("resolve the *true* editable node — do not trust the focused node"). M2 deliberately
>   does naive `findFocus(FOCUS_INPUT)`/`event.source` logging only.
> - **Live password-field → sensitive** is proven by `SensitiveFieldClassifierTest` (unit) now and is
>   re-checked physically at M3 ("password field untouched"). The live native-password demo over adb
>   was skipped (couldn't cleanly launch a native password field headlessly).
> - Enable the service for dev via:
>   `adb shell settings put secure enabled_accessibility_services com.tihor.quill/com.tihor.quill.accessibility.QuillAccessibilityService`
>   then `adb shell settings put secure accessibility_enabled 1` (append, don't clobber existing services).

## M3 — Text injection (hardcoded string) — code ✅, cross-app matrix is YOUR physical gate
- [x] `TextInjector` / `AccessibilityTextInjector`: resolve the true editable node, read text+selection → `InsertionComposer.compose` → `ACTION_SET_TEXT` → `ACTION_SET_SELECTION` (caret repositioned). Ignores hint text so it never appends to a placeholder.
- [x] **Node resolution made SAFE** (`EditableNodeResolver`): focus node → **isFocused** editable descendant → editable ancestor. Deliberately does **not** pick "first editable descendant" — that wandered to the wrong field in a multi-input web form (a real bug caught on-device). Focus is also tracked from focus **events** (`event.source`), which is more reliable than `findFocus(FOCUS_INPUT)` for WebViews.
- [~] **Dump real nodes per matrix app + tune** — this is the on-device tuning (`spec.md` §5D) that belongs to your physical matrix run; the resolver is a safe starting point.
- [x] Clipboard fallback (`AccessibilityTextInjector`): sets the clip with `EXTRA_IS_SENSITIVE`, **restores the prior clip only after a confirmed paste** (re-reads the node), never a silent timed restore. Wired to the bubble PASTE state (tap-to-paste). *Full confirm/restore couldn't be exercised headlessly — part of your matrix run.*
- [x] Sensitive-field guard: refuses to inject when the focused field is sensitive (`SensitiveFieldClassifier`). Reliable for **native** password fields (isPassword / password inputType). **Limitation:** Chrome **web** password inputs may report `isPassword=false` at the a11y layer (seen on-device) — web banking can't be detected this way; native banking apps (the real P0-5 case) are detected.
- [x] **Test (write first):** `InsertionComposerTest` (10 cases, green) — empty/append/no-double-space/mid-insert/replace-selection/unset-caret/spacing.
- [~] **Verify:** Confirmed via adb (debug broadcast trigger) — `ACTION_SET_TEXT` inserts "hello world" with correct caret into Chrome's **native omnibox** AND a **WebView** text field; resolver targets the focused field (no wandering). **≥3 real apps incl. fallback + "banking field untouched" is your physical matrix** (`testing_plan.md` §3) — the inherently-manual M3 gate.

> **M3 notes:**
> - **Bubble tap now injects** (M1 state-cycling replaced): a tap inserts "hello world" into the
>   focused field via the accessibility service; PASTE-state tap performs the clipboard paste. (Real
>   transcript replaces the hardcoded string at M4–M6.) **Bubble taps can't be driven by adb** (overlay
>   isn't focusable) — so injection was verified via a debug broadcast and the bubble path is part of
>   your physical test.
> - **Dev trigger:** `adb shell "am broadcast -a com.tihor.quill.DEBUG_INJECT --es text 'hello world'"`
>   (debug builds only; the receiver is registered by the a11y service). `DEBUG_PASTE` triggers paste.
>   **Quote the whole `am` line for the device shell** or the space in the text becomes a `pkg=` arg.
> - **Compose / Samsung / banking** node behaviour is unknown until you run the §3 matrix on the phone.
>
> **Hint-prefix bug → resolved by a deliberate semantics decision (2026-06-24).** On-device the
> WhatsApp field reports `text="Message"`, `hintText=null`, `isShowingHintText=false` — **no
> placeholder metadata at all**, so a placeholder is indistinguishable from real content and no
> hint-detection can work. **DECISION: v1 REPLACES the whole field with the transcript** (overrides
> the optimistic PRD §5.2 "insert at cursor, preserve content", which assumed readable real content).
> `InsertionComposer.composeForInjection` ignores existing text → `ACTION_SET_TEXT` writes exactly the
> transcript on any app. **Trade-off (owned):** dictating into a field with real half-typed content
> overwrites it; preserve-content is a deferred **P1** (needs reliable per-app emptiness detection; the
> natural home for an "insert vs replace" **setting**, M7+). Covered by `InsertionComposerTest`
> (replace: placeholder/real both → transcript) + `HintNodeInjectionTest` (Robolectric, the **real**
> node text="Message"/hintText=null/flag=false → exactly the transcript). Debug log adds `sel=…`.
> **Re-check note:** synthetic `adb input tap` does **not** fire the `TYPE_VIEW_FOCUSED` events real
> finger taps do, so native-field injection can't be driven headlessly — **re-run the matrix by hand**.
>
> **M3 hand-test PASSED (2026-06-24, Honor):** native WhatsApp/Gmail/Messages inject at the cursor ✅;
> Chrome paste-fallback worked (P state → tap → paste → prior clipboard restored) ✅; native
> banking/password refused/untouched ✅; bubble gestures ✅. (Matrix filled in `testing_plan.md` §3.)
>
> **Web-password safety rule (closed, by request — not deferred):** Chrome reports web password
> inputs as `isPassword=false` with plain `TYPE_CLASS_TEXT`, indistinguishable from a web text field.
> So `SensitiveFieldClassifier` now treats **any browser-context editable field whose type isn't
> *positively* confirmed non-secret (email/url/phone/date) as sensitive → refuse**. Unit-tested
> (`SensitiveFieldClassifierTest`, browser cases) and verified on-device (Chrome omnibox now
> `RefusedSensitive`). Trade-off: also disables dictation into the URL bar + generic web text fields
> (safety over convenience, as requested). Softening option noted in `testing_plan.md` §3.

## M4 — Audio capture in a microphone foreground service ✅ (commit `feat: audio capture in mic FGS (M4)`)
> Honor whitelist DONE (user, 2026-06-24).
- [x] `OverlayService` FGS adds `type=microphone` while recording (manifest `specialUse|microphone`); drops the mic type on stop. Notification updates ("Quill is listening").
- [x] `AudioRecorder(input = handset default)` — **`AudioRecord`** (VOICE_RECOGNITION) 16 kHz mono PCM → WAV in cache; routes input through the `AudioRoutingManager` seam (`HandsetAudioRoutingManager`). **FLAC deferred** behind the `AudioEncoder` seam (`WavPassthroughEncoder`) — see note.
- [x] Start recording from bubble tap (background-start permitted via the visible overlay). Tap toggles record/stop; live level feeds the bubble (`BubbleView.level`).
- [x] Mic runtime-permission handling (`hasMicPermission` → ERROR state if missing); session cap (300 s) + pre-limit warning (`RecordingLimits`).
- [x] **Silence auto-stop (v1):** RMS per buffer → `SilenceDetector` (fixed threshold, conservative quiet window + startup grace). Unit-tested; live tuning is a manual step.
- [~] **Minimal keep-alive:** sticky FGS ✅ (`START_STICKY`). Battery-exemption **prompt** + auto-restart-on-foreground folded into M8/M11 (the Honor whitelist is done manually for dev).
- [x] **Test (write first):** `WavHeaderTest` (16 kHz/mono/16-bit), `SilenceDetectorTest`, `RecordingLimitsTest` — all green.
- [x] **Verify (adb on Honor):** record → **valid WAV** (header dumped: `RIFF/WAVE`, PCM, mono, 0x3e80=16000 Hz, 16-bit; 4.2 s = 134400 PCM bytes); screen on ✅; revoking mic mid-session → **no crash** (OS kills+restarts process, sticky FGS recovers) ✅. *(Bubble-tap start + on-screen level meter + live silence-stop are part of your physical pass; driven here via `DEBUG_RECORD_START/STOP`.)*

> **M4 deviations:**
> - **FLAC encoder deferred** (NDK not provisioned; native CMake build on AGP 9.2.1 is high-risk).
>   v1 records WAV; `AudioEncoder`/`WavPassthroughEncoder` seam is in place so libFLAC drops in with
>   no caller change. **Groq/OpenAI accept WAV**, so M5/M6 work without it. Revisit before release
>   (upload-size optimization). This is the NDK/FLAC item originally slated for M4.
> - **Dev triggers:** `adb shell am broadcast -a com.tihor.quill.DEBUG_RECORD_START` / `_STOP`
>   (debug builds). Grant mic: `adb shell pm grant com.tihor.quill android.permission.RECORD_AUDIO`.

## M5 — STT integration (BYOK) ✅ (commit `feat: STT transcription client (M5)`)
- [x] `TranscriptionClient` interface (streaming-friendly: doesn't assume req/resp) + `OpenAiCompatibleTranscriptionClient` (base URL + model; covers OpenAI **and** Groq). Groq defaults baked (`whisper-large-v3-turbo`).
- [x] Multipart WAV upload (OkHttp); parse via kotlinx.serialization; **typed errors** (`TranscriptionError`: BadKey 401/403, RateLimited 429, Provider 5xx/other, Network, Parse). Key read lazily via `apiKey()` (never stored on the client / logged).
- [x] **R8 shift-left:** kotlinx.serialization models; **`assembleRelease` with R8 on now passes** (`minifyReleaseWithR8`). Enabled via AGP 9 gradual-R8 (`optimization.enable=true` + `android.r8.gradual.support=true`).
- [x] **Test (write first):** `TranscriptionClientTest` (MockWebServer, 6 cases) — success+multipart/auth, 401→BadKey, 429→RateLimited, 500→Provider, malformed→Parse, no-server→Network.
- [~] **Verify:** unit tests green on debug ✅; R8 release build green ✅. **Live "real key returns text"** rolls into M6 (needs your API key) — that's the M6 stop.

> **M5 notes:** Added `unitTests.isReturnDefaultValues = true` so `android.util.Log` in the client
> doesn't throw in plain JVM tests. The live minified-parse check happens when the release APK is
> exercised (M12); R8 retains the serializers (build proves it).

## M6 — End-to-end happy path 🎯 — code ✅, YOUR live test pending (commit `feat: end-to-end dictation pipeline (M6)`)
- [x] Wire capture → STT → injection through the bubble (`OverlayService.transcribeAndInject` → `onTranscript`): record → transcribe (Groq) → `injectText`.
- [x] States flow: RECORDING → TRANSCRIBING → IDLE (inserted) / PASTE (clipboard fallback) / ERROR. Empty transcript → "didn't catch that" (ERROR). Temp WAV deleted on success/fallback, **kept on failure** for retry (spec §4.3).
- [~] **Test:** end-to-end manual script across ≥3 apps — **yours** (needs a real Groq key + speech).
- [~] **Verify:** Pipeline verified on-device against the **real Groq endpoint** (record → WAV → multipart upload → 401 → typed `BadKey`/ERROR; WAV kept). **Live success path (speak → text lands) is your test** with your key. *This is the v1 proof point.*

> **M6 notes:**
> - **Temporary key entry:** `TempApiKeyHolder` (in-memory, no plaintext at rest) + a key field in
>   `MainActivity`. **M7 replaces it** with Keystore-encrypted `SecretStore`.
> - **Provider hardcoded to Groq** (`whisper-large-v3-turbo`) for M6; the provider/model **settings UI is M7**.
> - **Retry UI deferred:** on failure the WAV is kept but there's no explicit Retry button yet (the
>   bubble shows ERROR). Wire an explicit Retry/"recording saved" affordance at M11 (PRD §5.7).
> - **Dev trigger for the pipeline:** `am broadcast -a com.tihor.quill.DEBUG_SET_KEY --es key '<key>'`
>   then `DEBUG_RECORD_START`/`_STOP` (debug builds).

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
