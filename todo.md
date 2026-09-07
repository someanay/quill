# Quill — Implementation Checklist (todo.md)

> September 7, 2026: Remote copy updates (M13) have been retired. The app uses
> bundled text only; the local copy tool edits source for the next signed APK/AAB.
> Earlier OTA milestones below are historical and no longer release requirements.


Track implementation, verification, and deviations here. Work on one milestone or sub-task at a time.

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

## M3 — Text injection (hardcoded string) — code ✅, cross-app matrix is the physical-device gate
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

## M6 — End-to-end happy path 🎯 — code ✅, live testing pending (commit `feat: end-to-end dictation pipeline (M6)`)
- [x] Wire capture → STT → injection through the bubble (`OverlayService.transcribeAndInject` → `onTranscript`): record → transcribe (Groq) → `injectText`.
- [x] States flow: RECORDING → TRANSCRIBING → IDLE (inserted) / PASTE (clipboard fallback) / ERROR. Empty transcript → "didn't catch that" (ERROR). Temp WAV deleted on success/fallback, **kept on failure** for retry (spec §4.3).
- [~] **Test:** end-to-end manual script across ≥3 apps — **yours** (needs a real Groq key + speech).
- [x] **Verify:** ✅ **Confirmed on the phone (2026-06-24):** dictation lands in WhatsApp / Gmail / Messages with no placeholder prefix, transcript accurate, latency fine on WiFi. **The v1 proof point is met.** (Pipeline also verified against the real Groq endpoint via adb: record → WAV → upload → typed errors.)

> **M6 notes:**
> - **Temporary key entry:** `TempApiKeyHolder` (in-memory, no plaintext at rest) + a key field in
>   `MainActivity`. **M7 replaces it** with Keystore-encrypted `SecretStore`.
> - **Provider hardcoded to Groq** (`whisper-large-v3-turbo`) for M6; the provider/model **settings UI is M7**.
> - **Retry UI deferred:** on failure the WAV is kept but there's no explicit Retry button yet (the
>   bubble shows ERROR). Wire an explicit Retry/"recording saved" affordance at M11 (PRD §5.7).
> - **Dev trigger for the pipeline:** `am broadcast -a com.tihor.quill.DEBUG_SET_KEY --es key '<key>'`
>   then `DEBUG_RECORD_START`/`_STOP` (debug builds).

## M7 — Secrets & settings ✅ (commit `feat: keystore secrets, settings, provider seam + Ink&Signal theme (M7)`)
> **Design foundation established first** (design_system.md): `ui/theme` (Color/QuillColors/Theme/Shape/Type — hard-set Ink&Signal scheme, no dynamic colour) + `ui/components` (Wordmark, SectionHeader, StatusPill, Banner). Bubble restyled to §9 (brand/gold/busy gradients, gold listening halo + 1.8 s pulse, 2.5 dp white ring + drop shadow, mic/spinner/copy/bang glyphs, reduced-motion aware). Home/key/settings screens are on-design (verified via screenshots, dark theme).
- [x] `SecretStore`: `KeystoreSecretStore` AES-256-GCM, non-exportable AndroidKeyStore key; ciphertext+IV in app-private files; **not** EncryptedSharedPreferences (deprecated). Excluded from Auto Backup (`data_extraction_rules.xml` + `backup_rules.xml`).
- [x] API-key entry screen (`ApiKeyActivity`): masked monospace field, **validate by a test transcription** (`KeyValidator` — tiny silent WAV → Valid / Rejected / Unverified), encryption reassurance banner.
- [x] `SettingsRepository` (DataStore): provider, model, language hint, cleanup, private mode, session cap, onboarding-complete. `SettingsActivity` on-design.
- [x] **Provider seam:** `SttProvider` (Groq default + only v1-shipping; OpenAI/OpenRouter defined) + `TranscriptionClientFactory` — a second provider slots in behind `TranscriptionClient` (OpenRouter adapter drops in later). Only Groq is selectable/shipping in v1.
- [x] **Test (write first):** `AesGcmCipherTest` (round-trip, ciphertext≠plaintext, fresh IV, wrong-key fails); `SettingsRepositoryTest` (Robolectric, defaults + round-trip).
- [x] **Verify (adb on device):** key **persists encrypted** (`strings` on the blob shows no key), **survives force-stop+restart** (decrypts → Groq 401, not "no key"), **never in logs** (grep for key = empty), backup-excluded (config); settings round-trip (test). 51 unit tests green; R8 release builds.

> **M7 notes:** Key is encrypted-at-rest immediately (the M6 in-memory `TempApiKeyHolder` is removed).
> The **insert-vs-replace toggle** (tracked P1) was *not* added — it would be a no-op until the
> insert-at-cursor logic exists; left as the tracked P1. `InkMeter` (§10) lands at M8 (mic-test).
> Dev: `adb shell am broadcast -a com.tihor.quill.DEBUG_SET_KEY --es key '<key>'`.

## M8 — Onboarding flow ✅ code/build/install, a device walkthrough pending (commit `feat: onboarding flow + Copy Catalog (M8)`)
- [x] **Reordered flow (board A)** in `OnboardingActivity` (12-step state machine): splash → welcome → key-required warning (3 s gold read-gate) → add key → verify → key error → how-it-works → setup checklist → mic → **mic-test (InkMeter renders here)** → overlay → accessibility disclosure → accessibility enable → all set. Real grants via launchers (mic `RequestPermission`, overlay/accessibility settings intents).
- [x] **Copy Catalog foundation:** `CopyKeys` + baked-in `assets/copy/strings.default.json` (complete) + `CopyRepository` (resolved `StateFlow`, `get(key,args)`, fallback) + `LocalCopy`/`copy(key)` + `QuillRoot`. **Compliance keys binary-only** — `CopyKeys.COMPLIANCE` excluded from `OVERRIDABLE` from the start; `applyOverride` physically ignores them (tested).
- [x] **CI/lint guard:** `NoComposeStringLiteralsTest` fails on hardcoded `Text("…")` in the onboarding flow. *(Scoped to the on-design onboarding now; extend to migrated screens — see follow-up.)*
- [x] Microphone test with the **InkMeter** (§10) driven by live `AudioRecorder` RMS; reduced-motion aware.
- [x] **InkMeter (§10)** + **PermissionIllustration/ProgressDots** (§8.16/§8.15) built; every screen on QuillTheme + §8 patterns.
- [x] **Test:** `CopyRepositoryTest` (completeness, fallback, compliance-not-overridable, override-ignores-compliance); literal guard. 57 unit tests green.
- [~] **Verify:** Renders on-design (Welcome = brand illustration; Key-warning = gold illustration + 3 s gold gate + disabled CTA + compliance copy — screenshotted). **Cold-install run-through to first dictation is a device walkthrough** (real mic/overlay/accessibility grants).

> **M8 notes / deferred-within-M8 (tracked):**
> - **Recovery cards for revoked permissions** → folded into **M11** (revocation detection + notification deep-link); the checklist step already shows live permission status.
> - **Compose UI instrumented tests** (advance-only-when-granted, empty-key-rejected) → follow-up; empty-key is already gated (`primaryEnabled = key.isNotBlank()`), and the manual run-through is the M8 gate.
> - **Migrate M7 screens (Home/ApiKey/Settings) to the Copy Catalog** + extend the literal guard repo-wide → follow-up (they currently use literals; the onboarding is the first fully-keyed flow).
> - The standalone `AccessibilityDisclosureActivity` (M2) still reads compliance copy from `strings.xml`; both are binary/locked — consolidate onto the catalog in the follow-up.

## M9 — Dictation history (P1, on by default)
- [x] Hand-rolled SQLite `history` table + store; list screen (Compose)
- [x] Gate all writes behind private mode; delete-all + per-item delete
- [x] **On by default; time-based retention:** keep entries within a window (`historyRetentionMinutes`,
  default 60 = 1 h); user picks 1 h / 1 d / 1 w / 30 d / Forever or turns history off in Settings →
  Privacy. Expired entries auto-delete on insert and on opening History. A 1,000-row cap backstops
  storage (matters only for Forever).
- [x] **Test (write first):** store insert/query/delete; **no rows written when private mode on**;
  retention prunes entries past the window and beyond the backstop cap
- [ ] **Verify:** entries logged when on, none when private mode on, deletion + window/backstop pruning work.

## M10 — LLM cleanup pass (P1)
- [x] `CleanupClient` (chat-completions) + toggle + ~~optional separate LLM key~~ — see deviation
- [x] System prompt: filler removal, punctuation/capitalisation, (optional) vocabulary substitution
- [x] **Test (write first):** returns raw text unchanged when disabled; cleanup failure → raw fallback (never blocks insertion); vocabulary substitution (P1.5)
- [ ] **Verify:** cleaned output when on, raw transcript when off; cleanup failure falls back to raw, never blocks insertion. *(Gemini "type my email" fidelity check folds into the Post-M13 on-device verify.)*

> **M10 deviations:**
> - **No separate cleanup key** — cleanup reuses the stored **Groq** key (`CleanupKeyPolicy`); a
>   non-Groq STT provider still runs cleanup via the Groq key if present, else skips silently → raw
>   transcript (decision recorded in the Post-M13 multi-provider notes).
> - **Transcript-fidelity guard (the real "don't obey the dictation" fix).** Prompt hardening alone
>   can't stop an LLM from occasionally obeying an imperative (`"type my email"` → `"I'm ready to type
>   your email"`). `CleanupGuard.preservesMeaning` structurally verifies the *output* — length ratio
>   0.5–2.0 **and** ≥0.6 token coverage of the raw — and on failure discards the cleanup and falls back
>   to the raw transcript (same fail-soft path as a timeout). Pure + unit-tested (`CleanupGuardTest`);
>   `CleanupClientTest` covers the divergent-response → raw path. Raw is always the safe value.

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

**M13a — device-side verified fetch + cache + 3-layer resolution: DONE (2026-06-28).** New `copy/ota/` package
(`Ed25519Verifier` via `net.i2p.crypto:eddsa` — uniform on API 29–37, no platform "Ed25519" provider;
`CopyBundleCanonical` defines the exact signed bytes; `CopyBundleVerifier`, `CopyBundleCache`,
`CopyUpdateStore`, `CopyUpdater`). Cache re-verified + applied **on every foreground** (`MainActivity.onStart`,
also `OnboardingActivity`) — a fetch that ACCEPTS a newer bundle re-applies immediately, so new copy shows
**live in the same session** (warm-open), not only on the next cold launch; `LocalResolvedCopy` makes `copy(key)`
recompose. Still flicker-free (synchronous cache read) and compliance lock untouched. Canonical-input contract is in `CopyBundleCanonical` KDoc (and the uncommitted
`docs/copy-bundle-format.md` draft for M13b). 31 unit tests; `assembleRelease` green.
*Follow-ups (non-blocking): (1) add a real RFC-8032 known-answer vector once M13b's reference signer exists,
as the cross-spec check; (2) `CopyUpdater.BASE_URL` stays a placeholder until the CDN lands in M13b.*

- [x] `CopyUpdater`: launch-time, off-main GET of `/copy/{locale}.json`; short timeout; silent failure; **never on the UI critical path** *(uses ETag/If-None-Match on `en.json` rather than a separate `.sig`; signature is an in-bundle field)*
- [x] **Ed25519 signature verify** against a public key **pinned in the binary** (`net.i2p.crypto:eddsa`, not the platform provider); reject unsigned / bad-sig / tampered bundles
- [x] On-device **schema validation** (required keys, **placeholder-arg parity**) + **monotonic-revision** rollback guard; apply **atomically** to the cache or not at all *(length caps deferred — not in M13a scope)*
- [x] **Locked-key allowlist** enforced: a remote bundle can never override the accessibility disclosure, permission rationales, privacy promises, or the BYOK key warning (spec §2.3 / §6)
- [x] **"Check for updated text"** toggle in Advanced (default on); privacy-policy line covering the read-only fetch *(toggle wired; privacy policy + terms + open-source licences now committed under `docs/`, disclosing the unconditional copy-bundle GET)*
- [ ] **CI guard:** build fails if `strings.default.json` is missing any key referenced in code (layer-1 completeness); lint guard against string literals in composables
- [ ] **Authoring tool** (separate from the app — recommend a small **Next.js page on Vercel**): edit copy by key/screen, show baked-in default read-only beside an editable override, char caps, placeholder hints, lock badges; validate; bump `revision`; **commit plaintext** to the repo
- [ ] **Publish pipeline:** a GitHub Action signs the bundle with the Ed25519 **private key (CI secret only)** and deploys `en.json` + `.sig` to the static host — private key never in the app **or** the authoring browser
- [x] **Test (write first):** offline/airplane cold-start renders the full UI from baked-in (no blank keys); tampered / invalid-sig bundle rejected; lower-or-equal `revision` ignored; a locked key inside a bundle is ignored; placeholder-parity + schema failures reject the whole bundle; toggle-off disables the fetch *(covered by `copy/ota/*Test`; offline = `CopyUpdater` failure path keeps baked-in)*
- [ ] **Verify:** with the toggle on and a newer valid bundle published, the app fetches and applies it on next launch; in airplane mode the UI is identical and unbroken from baked-in defaults.

---

---

## Post-M13 — Multi-provider STT picker ✅ code/build/tests (2026-07-02); on-device pass pending
Groq / OpenAI / OpenRouter / Gemini / Custom all selectable (Deepgram chip removed — no adapter).
- **Per-provider keys:** `SttKeyStore` — one Keystore alias per provider (`stt_key_<id>`); the legacy
  single `stt_api_key` slot migrates to the Groq alias exactly once. Switching provider swaps the key
  used; a key for provider A is never sent to provider B.
- **Clients:** OpenAI-compatible (Groq/OpenAI/Custom, unchanged) · `OpenRouterTranscriptionClient`
  (JSON + base64 `input_audio` — its STT endpoint is NOT multipart; enum's stale `openAiCompatible`
  fixed) · `GeminiTranscriptionClient` (native `generateContent`, `x-goog-api-key`, hardened
  transcribe-verbatim prompt + fence/label stripping; 400 `API_KEY_INVALID` → BadKey; empty → Parse).
  Language hint: multipart param (Whisper-style) / JSON field (OpenRouter, ISO-639-1) / folded into
  the prompt (Gemini). RomanizePrompt behaviour for Groq unchanged.
- **Cleanup stays Groq-only (decision 5):** non-Groq provider → uses the stored Groq key, else skipped
  silently (raw transcript). Status line in cleanup settings.
- **Custom provider:** base URL (https-validated) + model, both required, new DataStore keys; model
  survives switching away and back. Key-console links per provider; hidden for Custom.
- Deviations: onboarding key step is now provider-selectable too (Custom excluded there — needs the
  settings-only endpoint config; picking a provider in onboarding makes it active); `DEBUG_SET_KEY`
  gained optional `--es provider <id>`; Gemini models verified 2026-07 (`gemini-3.1-flash-lite`
  default, `gemini-3.5-flash`).
- [ ] **Verify (yours, on the Honor):** per-provider key entry/validation + dictation; Gemini
  "type my email" fidelity check; key retention across provider switches (script in the session notes).

## Tracked refinements (P1 — designed-for, not built now)

- **Bubble dismiss-to-edge + auto-return + shake-to-summon (v1.1, Wispr-Flow-style).** Drag the bubble
  very close to the bottom (charging-port) edge → it dismisses into a hide zone; it auto-returns on a
  ~10-minute timer, OR the user shakes the phone to summon it back immediately. Three sub-features,
  two of which live in the exact area M11 proved hostile on MagicOS:
  - [x] (1) **drag-to-edge dismiss + notification-summon** — **SHIPPED (2026-06-28).** Bottom dismiss
    zone (`BubbleGeometry.isInDismissZone`, threshold dp(72) off the visible bottom edge) hides the
    bubble on drag-release; the FGS + notification stay up and a "Show Quill" notification action
    summons it back (`ACTION_SHOW` → `OverlayService.showBubble`). Hidden state persists
    (`BubbleVisibilityStore`, plain SharedPreferences — synchronous read so `addBubble` restores it on
    a START_STICKY/recovery restart). New copy keys `notification.action.show` + `bubble.dismiss.toast`
    (OVERRIDABLE). Skipped the optional drag-dim hint (would fight the sensitive-field alpha); shows a
    recovery toast instead. No timer/sensor/AlarmManager/SensorManager added — the notification is the
    only way back.
  - [x] (2) **~10-min auto-return** — **SHIPPED (2026-07-27).** `AutoReturn` (pure, over the service's
    own scope) holds a `delay(10 min)`; `AutoReturn.DEFAULT_DELAY_MS` is the tuning knob. **The earlier
    "needs AlarmManager/WorkManager" note above was written before Option A and its premise no longer
    holds:** it assumed the service dies while the bubble is hidden. It doesn't — hiding keeps the FGS
    and its notification alive (only `ACTION_STOP` fully stops), so a living service can just hold a
    suspended coroutine. That means **no scheduler and no `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`**
    (Play-restricted; we dropped one such permission in M12 and won't re-add one for this). Accepted
    cost: `delay` is not doze-proof — screen-off in deep idle the return fires on the next wake, not on
    the minute. If the process is killed and recovered (M11) the timer is simply gone and the bubble
    stays hidden, with the notification as the way back — deliberately no persistence.
    **Scoped to drag-dismiss only:** flicking the bubble into the bin means "not right now" and earns a
    return; the Home master toggle off is a stated preference and must not flip itself back on (same
    respect-user-intent line M11 draws around force-stop). `ACTION_STOP` cancels the timer.
  - [x] (3) **shake-to-summon** — **SHIPPED (2026-07-27).** `ShakeDetector` is pure/Android-free like
    `BubbleGeometry`, so the tuning is unit-tested rather than guessed at: low-pass gravity removal
    (works in any orientation, so tilting registers nothing), a 16 m/s² *linear* jolt threshold
    (~1.6 g net — above walking's 3–7 and running's 10–15), **2 same-axis direction reversals** within
    900 ms (one hard bump is not a shake; a jostled bag flails across axes and restarts the sequence),
    and a 2 s cooldown so one shake summons once. Measured floor ≈ 19 m/s² peak (~1.9 g), flat across
    2–5 Hz. Registered at `SENSOR_DELAY_UI` **only while hidden**, unregistered on show and destroy.
    Tests cover walking, jogging-in-pocket, single jolt, slow tilt and stationary all NOT firing.
  - Also shipped with (2)/(3): the **bin target + dismiss animation** — `BinTargetView` in its own
    `NOT_FOCUSABLE|NOT_TOUCHABLE` overlay window, added once in `addBubble` and removed in `onDestroy`
    (never per-drag, so a mid-drag service death can't leak it); `BubbleGeometry.dismissProximity`
    fades/grows it in across a 210 dp approach band and pops it error-red inside the 72 dp zone; the
    bubble then flies into it via window-position + `scaleX/scaleY` only. **Never `alpha`** — that
    belongs to the sensitive-field dimmer (0.45) and a second consumer would fight it; and never
    `translationX/Y`, which would clip against the `WRAP_CONTENT` window. Bin icon is a vector drawable
    (`ic_bin_target.xml`), no raster. New copy keys `bubble.bin.label`, `bubble.bin.label_active`,
    `bubble.autoreturn.toast` (all OVERRIDABLE); `bubble.dismiss.toast` reworded to mention shake.
  - [ ] **Verify (yours, on the Honor):** bin fade-in timing + position vs the gesture-nav bar; the
    fly-in landing in the bin at all three bubble sizes; shake calibration in daily use (does a natural
    shake fire first try? any false-fire while walking/pocketed?); auto-return promptness under MagicOS
    doze; sensitive-field dimming surviving a drag-dismiss + summon.

  **All new copy soft-coded** via CopyKeys as OVERRIDABLE, same as everything else; the shipped keys
  flow through the OTA bundle. (Origin: user request, 2026-06-28.)

- **Insert-at-cursor when a field has genuine content.** v1 *replaces* the whole field on inject
  (M3 decision — the only safe behaviour when placeholders are indistinguishable from content, e.g.
  WhatsApp `text="Message"`, no hint metadata). This wipes a real half-typed draft. The refinement:
  preserve existing content and insert at the cursor **when the field genuinely has content** —
  needs reliable per-app emptiness detection (the accessibility tree alone doesn't give it; explore
  selection/caret signals + per-app tuning). Natural home: an **"insert vs replace" setting**
  (M7 settings surface) defaulting to replace. The pure logic still exists in git history
  (`InsertionComposer.compose`, insert-at-cursor + spacing) to restore. (Origin: M3, 2026-06-24.)

---

### Working rule
After each verification gate passes: review the diff, run the build + tests/manual script, then
`git commit` with a clean conventional message (e.g. `feat: insert text via accessibility set_text`).
One green milestone = one commit. If a later step breaks, you can roll back cheaply.


## Android release preparation — September 6, 2026

- [x] Android-only repository surface; paused ports preserved locally and removed from tracking.
- [x] Replace planning-bundle READMEs with build, setup, and contribution documentation.
- [x] Shared Android surface, typography, navigation, onboarding, and button polish.
- [x] Bubble press/state animation and recording waveform; reduced-motion handling.
- [x] Provider model discovery, ranked searchable dropdowns, cached fallback, and manual model IDs.
- [x] Persist and use the selected Groq cleanup model.
- [x] Copy tool synced against the Android app and live database: 393 keys, revision 5.
- [x] Android unit tests (390), lint (no errors), and signed APK/AAB builds.
- [ ] Physical Honor visual pass: small/large text, light/dark themes, keyboard and dropdown scrolling, bubble drag/dismiss, motion disabled.
- [ ] Signed-release dictation in three real apps; discovery with each configured provider and offline fallback.
- [ ] Complete the remaining [Play submission checks](docs/play-readiness.md).

Scope expanded at the user's request to include Android visual polish and live model discovery. This release gate remains open until the physical-device and submission checks pass.

## M13 follow-up — automatic copy refresh

- [x] Keep the existing check on every foreground/restart and add a persistent six-hour background job requiring a network.
- [x] Preserve the schedule when reopening; serialize competing fetches so the cache cannot roll back.
- [ ] Physical Honor verification: launch online/offline; run job 1301; confirm signed copy applies without reopening; reboot and confirm the schedule survives. Force-stop must suspend background checks until manual relaunch.

### M10 follow-up: independent cleanup providers
- [x] Separate cleanup provider/model from transcription; preserve legacy Groq configuration and remember models per cleanup provider.
- [x] Share style prompts and meaning-preservation checks across all five supported providers, using only the selected provider's key.
- [x] Refresh provider-specific cleanup model catalogs; sync copy tool to Android manifest revision 6.
- [ ] Complete automated and signed-build gate plus live Gemini cleanup and Gmail/WhatsApp style checks.

### Gmail and key-management follow-up
- [x] Add Android 13+ accessibility editor-connection path for WebView editors, with foreground-package and sensitive-field checks.
- [x] Reject insertion into a different app after recording; confirm editor-connection insertion and preserve clipboard fallback when unconfirmed.
- [x] Default cleanup to speech provider and recommended model, retaining explicit separate configuration and off switch.
- [x] Encrypted per-provider multiple keys, active selection, removal, legacy-key retention and non-destructive validation.
- [ ] Physical Gmail insertion/style retest with updated accessibility capabilities; WhatsApp Neutral and Casual tests.

### September 6 physical retest outcome
- [x] User confirmed Gmail Formal output after the editor-connection update; Gemini/Formal applied=true independently observed in diagnostics.
- [x] User reports all three writing styles working in their physical testing.
- [ ] Broader repeated insertion matrix, selection replacement, app switching during processing, OEM recovery and older Android coverage remain release gates.

### Expanded app style catalog
- [x] Expand offline defaults from 41 to 540 packages, preserving existing defaults and user override priority.
- [x] Record published package identities and style assignments in docs/app-style-catalog.csv; add a validating generator.
- [x] Verify every catalog entry can be overridden and reset, plus email/chat/notes classification regressions (411 unit tests pass).
- Physical insertion compatibility across the full catalog is not claimed; the device matrix above remains open.

### Accessibility disclosure audit — September 7
- [x] Record the pre-fix audit and every Android accessibility-settings route.
- [x] Route onboarding and recovery through one resource-only disclosure with Agree/Not now.
- [x] Remove disclosure keys from the copy catalog and export; protect with regression tests.
- [x] Use user-approved accurate wording for focused-field reads and local diagnostics; preserve runtime behavior.
- [x] 415 unit tests, debug lint, and signed APK/AAB build pass.
- [ ] Physical first-run, decline/Back, and revoked-service recovery checks: phone not connected.

See [the audit](ACCESSIBILITY_DISCLOSURE_AUDIT.md) for evidence and the manual script.

## Local copy workflow - September 7, 2026
- [x] Remove app foreground/background copy downloads, signature dependency and runtime overrides.
- [x] Ignore legacy caches and cancel job 1301 on upgrade.
- [x] Retain a local copy editor, preserve existing drafts, and apply validated edits to source.
- [x] Add APK/AAB release script with signing checks and explicit Play version parameters.
- [ ] Physical app upgrade check: phone not connected during this pass.
