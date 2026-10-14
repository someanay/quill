# Quill — System Specification (spec.md)

Android architecture, storage, provider integration, and release constraints. Historical decisions are retained below; current release checks are in [release readiness](docs/play-readiness.md).

The September 8 native rebuild uses the current Android source as its reference. Shared boundaries remain recorder → provider → optional cleanup → insertion/handoff, with separate secrets, settings/history/vocabulary and bundled copy repositories. Windows implements OS adapters with WPF/UI Automation/DPAPI; macOS uses SwiftUI/AppKit/Accessibility/Keychain; iOS uses AVFoundation/Keychain and an explicit custom-keyboard handoff. Current contracts, unavoidable platform substitutions and exact validation limits are in [the native rebuild record](docs/platform-rebuild.md). No platform downloads remote copy.

---

## 1. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language | **Kotlin** | — |
| UI | **Jetpack Compose** (Activities for screens; overlay drawn via `WindowManager`, optionally hosting a `ComposeView`) | Four screens only: onboarding, key entry, settings, history. |
| Min / target SDK | **minSdk 29 (Android 10)**, **targetSdk = latest stable (35 / Android 15, or 36 if available)** | Targeting Android 12+ triggers the Play accessibility declaration. Targeting 14+ triggers mandatory FGS types. Verify the 16 KB page-size requirement only if you add native libs. |
| Async | **Coroutines + Flow** | — |
| DI | **Hilt** (manual DI is acceptable given the small surface) | — |
| Networking | **Retrofit + OkHttp** (or Ktor) · **kotlinx.serialization** for JSON | Multipart upload for audio; standard HTTPS. Compile-time JSON → minimal R8 keep-rules. |
| Persistence | **DataStore** (settings) · **Room** (history) · **Android Keystore** (secrets) | See §4. |
| Audio | **`AudioRecord` → 16 kHz mono PCM → WAV** — decided; see §2.2 | Raw PCM is required by the live level meter and silence auto-stop. **Encode to FLAC before upload** (v1). |
| Build | **Gradle (Kotlin DSL)** + version catalog | — |
| Testing | JUnit, Robolectric, Compose UI test, Espresso, **MockWebServer** | See `testing_plan.md`. |
| Backend / hosting | **None for user data** | BYOK; no accounts, no user-data backend in v1. The only hosted artifact is a static, signed **copy bundle** fetched read-only (§2.3). |
| Copy / remote config | **Self-hosted static signed JSON** (Copy Catalog) + **Ed25519** verify | Soft-coded UI strings; offline-first baked-in defaults; optional over-the-air updates; see §2.3. |

**Recommendation that simplifies provider support:** ship an **OpenAI-compatible** transcription
client. OpenAI's `/v1/audio/transcriptions` and Groq's `/openai/v1/audio/transcriptions` share the
same multipart request shape, so a single client with a swappable base URL + model covers both.
Deepgram/AssemblyAI can be added later behind the same interface.

---

## 2. Architecture decisions

The two decisions everything else hangs on. Recorded here with the options considered and the
consequences, so the reasoning survives.

### 2.1 Text injection — AccessibilityService + overlay bubble *(Accepted)*

**Accessibility setup guidance (October 4, 2026):** after the standalone disclosure's
Agree action, a bundled silent video accompanies Accessibility Settings in native
Android PiP. This uses the existing disclosure Activity, adds no overlay/FGS permission,
closes when Quill's service is enabled, and falls back to normal Settings when PiP is
unavailable. Every new request still requires disclosure and consent. See
[the guide's session behavior and physical checks](docs/accessibility-video-guide.md).

**Decision:** insert dictated text via an **`AccessibilityService`** that writes into the focused
field (`ACTION_SET_TEXT`, clipboard + `ACTION_PASTE` fallback), with a **`SYSTEM_ALERT_WINDOW`
overlay** drawing the floating bubble. **Rejected alternative:** a custom keyboard (IME).

- **Option A (chosen):** overlay + accessibility. This is exactly how Wispr Flow works on Android —
  it requires microphone, accessibility, and "display over other apps", detects the focused field
  through the accessibility service, and falls back to a clipboard Paste when insertion fails.
- **Option B (rejected):** an IME has a sanctioned `InputConnection` to commit text — no
  accessibility, no overlay, no Play declaration — but the mic lives **on the keyboard** and only
  works where the user has selected it. There is no free-floating bubble over arbitrary apps.

**Drivers:** the floating-bubble "dictate anywhere" UX is the product and Option B structurally
can't deliver it; public Play distribution raises Option A's cost but doesn't rule it out (Wispr
ships it).

**Consequences (accepted, with mitigations):**
- The overlay doubles as the thing that **legally lets the mic FGS start from the background** (§5).
- Play review risk — see §8.
- OEM reliability tax — see §7.
- Non-uniform cross-app insertion (WebViews/Compose) — covered by the clipboard fallback + the
  matrix in `testing_plan.md` §3.

**If Play blocks Option A:** ship an **IME-based v1** to get on the store, add the bubble later. The
`TextInjector` interface is designed so an IME-backed injector slots in without rewriting the
pipeline.

### 2.2 Audio capture — `AudioRecord`, not `MediaRecorder` *(Accepted)*

**Decision:** capture raw **16 kHz mono PCM via `AudioRecord`**, write a WAV in cache, and
encode to FLAC before upload. **`MediaRecorder` rejected.**

For a dead-simple batch v1, `MediaRecorder` (straight to compressed m4a) is less code. The decision
turns on commitments made elsewhere:
- **The PRD already requires a live level meter** (PRD §5 decision 5). A real amplitude meter needs
  raw PCM frames; `MediaRecorder.getMaxAmplitude()` is coarse and laggy. `AudioRecord` gives
  per-buffer amplitude for free.
- **The VAD / streaming path (P2)** consumes PCM as it arrives; starting on `MediaRecorder` forces a
  later rewrite.
- **Deterministic format** for STT (16 kHz mono), independent of the device encoder.

**Trade-off (owned):** more code (WAV header + buffering — unit-test the header) and larger raw
files (~9–10 MB / 5 min). **Mitigation:** **v1 always encodes to FLAC before upload** (lossless,
~half the WAV size); an **m4a-on-slow-network fallback is P1**, not v1. The 5-minute session cap
bounds the worst case. Capture is raw PCM; any compression is an explicit pre-upload step we
control, not opaque recorder output.

**FLAC encoder (previously unspecified — now pinned):** Android has no one-call PCM→`.flac` path.
`MediaCodec`'s FLAC encoder (`audio/flac`) emits encoded data that still needs wrapping in a FLAC
stream container, and `MediaMuxer` exposes **no** FLAC output format. So v1 **bundles a small native
FLAC encoder (libFLAC via the NDK/JNI)** and adds it to the M0 dependency set (M0 + M4 below). Both
default providers (OpenAI and Groq) accept FLAC on `audio/transcriptions`, so there is no
server-side concern; re-verify against each provider's live docs before shipping a new provider.

**Audio source (v1): the handset mic — but built behind a seam.** Because the trigger is an
on-screen bubble the user taps, the phone is in hand and its mic is both available and higher quality
than Bluetooth SCO. v1 captures from the handset only. **However, the audio-source seam ships in
v1:** `AudioRecorder` takes a selectable input device (handset by default) and the
`AudioRoutingManager` interface (§3.2) exists, so **Bluetooth capture drops in later with no
rework**. The BT *implementation* (selection UI + routing) is **P1**, and when built must use
**`setCommunicationDevice()` + `AudioDeviceCallback`** (the modern API; `startBluetoothSco()` was
deprecated in Android 12), needs `BLUETOOTH_CONNECT`, and warrants its own OEM test pass.

**Silence auto-stop (v1).** Tap-to-toggle means a user can forget to stop; with only the 5-minute
cap, a 4-second reply could upload ~5 minutes of silence (wasted wait, money, rate limit). Since raw
PCM frames already feed the live level meter, a cheap RMS check auto-stops after a sustained quiet
window. **Tune conservatively** — a tight threshold cuts people off when they pause mid-thought — so
this is a *safety net beside* the cap, not the precise endpointing of full VAD (still P2).
**Known limitation (v1):** the threshold is a **fixed absolute RMS**, so in a high-noise environment
(busy café, traffic) the level may never drop below it and the auto-stop won't fire — the 5-minute
session cap is the backstop there. A relative/adaptive noise-floor estimate is the upgrade path
(tracked toward VAD, P2); v1 deliberately keeps the simple fixed threshold.

---

### 2.3 Build-time copy catalog (accepted September 7, 2026)

The app reads interface text exclusively from its packaged assets. There is no
remote copy host, network updater, signature verifier, cache application, or
scheduled copy fetch. Existing installations cancel legacy job 1301 and ignore
previously downloaded copy. Only a new APK delivered through an app update changes
wording. Direct calls to user-selected AI providers remain unchanged.

The companion Copy Tool is a local-only authoring application. It reads the Android
checkout, stores drafts/backups on the maintainer's PC, validates placeholders and
locked keys, then applies edits to the source catalog and generated manifest.
Concurrent or stale drafts are rejected. It has no cloud database dependency.

Accessibility disclosure lives only in Android string resources. Other compliance
keys are locked in the copy editor and require a reviewed source change. All copy,
including ordinary editable text, becomes fixed in the installed app binary.

Use scripts/build-android-release.ps1 to generate the export, test, lint, build,
and verify signed APK/AAB artifacts. Google Play upload remains a separate release
step; version codes must increase for each new upload.

---
## 3. Components ("API & core functions")

We own no HTTP *endpoints*; the only hosted artifact is a static, signed copy bundle (§2.3). The "API
surface" is (a) Android system components and (b) the internal service interfaces. The **outbound**
network calls are: the user's chosen providers (audio + optional cleanup), and a read-only GET of the
copy bundle (§2.3).

### 3.1 Android system components

- **`OverlayService`** — foreground service, `foregroundServiceType="microphone"`. Owns the bubble
  window (via `WindowManager`), the `AudioRecorder`, and the recording lifecycle. Posts the
  required persistent notification. This service is what makes a mic-FGS start legal "from the
  background" (see §5).
- **`QuillAccessibilityService`** — `AccessibilityService`. Tracks the currently focused editable
  node (and its app package/field type), decides bubble visibility, and performs insertion. **Not**
  declared `isAccessibilityTool` (we don't qualify) → prominent disclosure required.
- **Bubble UI** — a draggable `WindowManager` overlay view showing idle / recording / transcribing
  / paste-fallback / error states. **It must stay legible over any background** (a dark fill
  disappears over a dark-mode app): use an adaptive/contrasting fill plus a stroke or drop shadow,
  and pick state colours that read against both light and dark surfaces.
- **Activities (Compose):** `OnboardingActivity`, `ApiKeyActivity`, `SettingsActivity`,
  `HistoryActivity`.
- **`BootReceiver`** — restarts the bubble/service after reboot (auto-start, mirroring Wispr),
  guarded by permission state.

### 3.2 Internal service interfaces

```text
interface TranscriptionClient {
    suspend fun transcribe(audio: WavFile, opts: TranscriptionOptions): Result<Transcript>
}
// v1 impl: OpenAiCompatibleTranscriptionClient(baseUrl, model)  // covers OpenAI + Groq

interface CleanupClient {                 // P1
    suspend fun clean(text: String, opts: CleanupOptions): Result<String>
}

interface TextInjector {
    fun insert(node: AccessibilityNodeInfo, text: String): InsertResult  // SET_TEXT, else PASTE fallback
}

interface SecretStore {                   // Keystore-backed
    fun put(alias: String, secret: String)
    fun get(alias: String): String?
    fun clear(alias: String)
}

class SettingsRepository(dataStore)        // provider, model, language, cleanup, privateMode
class HistoryRepository(roomDao)           // P1, gated by privateMode
class AudioRecorder(input: AudioDeviceInfo? = null)  // null = handset default; exposes RMS for level meter + silence auto-stop; encodes WAV→FLAC before upload

interface AudioRoutingManager {            // SEAM ships in v1 (handset only); BT selection/impl is P1
    fun availableInputs(): List<AudioDeviceInfo>
    fun selectInput(device: AudioDeviceInfo): Boolean   // setCommunicationDevice(); not startBluetoothSco()
    fun clearSelection()
}
```

### 3.3 Outbound provider calls (the only network I/O)

- **Transcription:** `POST {baseUrl}/audio/transcriptions` — multipart: `file=<wav>`, `model`,
  optional `language`, optional `prompt`. Auth: `Authorization: Bearer <user key>`. Response:
  transcript text. (OpenAI / Groq compatible.)
- **Cleanup (P1):** `POST {baseUrl}/chat/completions` — a system prompt instructing
  filler-removal + punctuation/formatting (+ optional vocabulary substitutions), user message =
  raw transcript. Returns cleaned text.

> **Provider sanity check:** Anthropic/Claude has **no speech-to-text API** — a Claude key can only
> power the **cleanup** pass above, never transcription. Valid STT providers are OpenAI, Groq,
> Deepgram, AssemblyAI, etc. Don't offer an "Anthropic key" in the STT picker.

Both use the user's own key. Keys are read from `SecretStore` into memory only for the duration of
the call and cleared after; never logged; never sent anywhere except the provider.

---

## 4. Data model / schema

No server database. Three local stores:

### 4.1 Settings — DataStore (Preferences)
`provider` (enum: OPENAI, GROQ, …; **default GROQ**), `sttModel`, `cleanupEnabled` (bool, **default
true**), `cleanupModel`, `languageHint` (nullable; auto-detect), `privateMode` (bool, **default
false**), `bubbleEnabled` (bool), `sessionCapSeconds` (default 300), `historyEnabled` (bool,
**default true**), `historyRetentionMinutes` (default 60 = 1 h; 0 = keep forever, count-cap only),
`pendingCrashReport` (bool; drives the next-launch prompt), `onboardingComplete` (bool).

### 4.2 Secrets — Android Keystore + encrypted blob
Not a "table." An AES-256-GCM key lives in the Keystore (non-exportable); the API key ciphertext +
IV are stored locally (DataStore or an encrypted file). Aliases: `stt_api_key`, `llm_api_key`
(P1). **Excluded from Auto Backup** (see §6).

### 4.3 History — local SQLite (P1, on by default; time-based retention, default 1 h)

`transcripts`:

| field | type | notes |
|---|---|---|
| id | Long (PK, autogen) | |
| createdAt | Long (epoch ms) | |
| text | String | the inserted text |
| destPackage | String? | e.g. `com.whatsapp` |
| durationMs | Long | recording length |
| charCount | Int | |
| status | enum(SUCCESS, FALLBACK_PASTE, FAILED) | |
| provider | String | which STT provider produced it |

`dictionary_entries` (P1.5): `id` (PK), `phrase`, `replacement`, `enabled`.

**Privacy rule:** when `privateMode = true`, nothing is written to `transcripts`. **Private mode
governs the transcript row, not audio retention** — audio temp files are *always* transient
regardless of the mode.

**Temp-audio lifecycle (both modes):** a captured WAV/FLAC temp file is **deleted immediately after
a *successful* transcription** (transcript returned and inserted/handed off). On **failure**
(network/airplane mode/provider error) the temp file is **kept** so the PRD's "recording saved +
Retry" path can re-upload without re-recording; it is deleted once a Retry **succeeds** or the user
**discards** the pending dictation. This holds in private mode too: a failed dictation may retain its
audio until Retry resolves or is discarded — the privacy guarantee is "no persistent transcript and
no audio left after the dictation completes," not "no bytes ever touch cache." Temp files live in
app-private cache (not user-visible storage).

**Retention:** the log is bounded — keep the most recent **~1,000 entries** and prune the oldest;
per-item delete and clear-all are both available. No time-based expiry in v1.

### 4.4 Copy catalog

The sole runtime source is assets/copy/strings.default.json. copy-manifest.json is
build-time authoring metadata. Legacy cache files are never loaded.

## 5. Permissions & the foreground-service model

### Manifest permissions
- `INTERNET`
- `RECORD_AUDIO` *(runtime/dangerous)*
- `SYSTEM_ALERT_WINDOW` *(special; granted via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`)*
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE` *(Android 14+)*
- `POST_NOTIFICATIONS` *(runtime; Android 13+)*
- `RECEIVE_BOOT_COMPLETED` *(auto-start)*
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` *(to prompt the OEM battery exemption)*
- The accessibility service is gated by the system `BIND_ACCESSIBILITY_SERVICE` permission in its
  service declaration.
- **Deliberately NOT requested:** `QUERY_ALL_PACKAGES` — the accessibility service already reports
  the foreground package; this permission is restricted and would add review burden for no benefit.

### The non-obvious constraint
On Android 14+ a microphone foreground service **cannot be started while the app is in the
background** *unless* the app holds `SYSTEM_ALERT_WINDOW` **and** has a visible overlay window. The
floating bubble is therefore **load-bearing for the permission model**, not just UX: it is what
lets `OverlayService` legally start the recorder when the user is in someone else's app. Implement
the bubble before the recorder (see `todo.md` ordering).

### Accessibility service config (`accessibility_service_config.xml`)
- `canRetrieveWindowContent="true"`
- events: `typeViewFocused`, `typeWindowStateChanged` (track focus + foreground app)
- `accessibilityFlags`: `flagDefault | flagRetrieveInteractiveWindows`
- `feedbackType="feedbackGeneric"`
- a `description` string stating the purpose in plain language
- **no** `android:isAccessibilityTool="true"`

### Node resolution & insertion (the M3 blueprint)

Grabbing the focused node and firing
`ACTION_SET_TEXT` — fails on Compose, WebViews, and any field with existing text. The algorithm:

**A. Resolve the *true* editable node — do not trust the focused node.** The node holding
accessibility focus is often a semantic wrapper, not the editable field.
- Search for the target as the node whose `actionList` contains **`ACTION_SET_TEXT`** (equivalently
  `isEditable` / a TextView-derived class) and that has input focus. Look in this order: the focused
  node itself → its descendants → nearest editable ancestor.
- **Compose:** the `SetText` action lives on the editable *semantics* node, not the focus wrapper —
  target that (query unmerged semantics). SET_TEXT generally works once you hit the right node;
  verify against your Compose version, as behaviour has shifted across releases.
- **WebView/Chrome:** web inputs frequently expose **no** SET_TEXT — detect this (no SET_TEXT in the
  action list) and drop straight to the fallback ladder rather than retrying.

**B. Insert at the cursor — `ACTION_SET_TEXT` replaces the *whole* field; there is no insert action.**
1. Read existing `node.text` and the caret via `textSelectionStart` / `textSelectionEnd` (treat as
   end-of-text if unset).
2. Compose the full new string: `before + transcript (+ spacing rules) + after`.
3. `performAction(ACTION_SET_TEXT, ARGUMENT_SET_TEXT_CHARSEQUENCE = fullString)`.
4. Reposition the caret with `performAction(ACTION_SET_SELECTION, start=end=newCaret)` — SET_TEXT
   often resets it. *This read-compose-set-reposition sequence is what prevents the overwrite bug.*
5. Skip entirely if the node is a password/sensitive field (disable dictation there).

**C. Fallback ladder — each rung is taken only when the one above fails:**
1. SET_TEXT on the resolved editable node.
2. **Clipboard paste**, user-initiated via the bubble's **"Tap to Paste"** state (not silent): save
   the user's prior clip → set the transcript with `ClipDescription.EXTRA_IS_SENSITIVE` →
   `ACTION_PASTE` → **confirm the paste landed** (re-read the target node's text/selection and verify
   the transcript is present) → **only then restore the prior clip**. If confirmation fails or times
   out, **do not restore** — leave the transcript on the clipboard so the user can paste manually.
   (Restoring blindly here is exactly the race that pastes the *previous* clip instead of the
   transcript.) *(We only **write** the clipboard, so we don't fire Android 12's "X read your
   clipboard" toast, which is for cross-app **reads**; the host app's paste may show its own one-time
   toast, which the explicit Tap-to-Paste makes expected.)*
3. **Manual Tap-to-Paste** (the user pastes when they choose). Quill can't reliably observe a manual
   paste, so there is **no silent timed auto-restore** — it would race the user's paste and clobber
   it. The saved prior clip is instead offered back via an explicit, short-lived **"restore previous
   clipboard"** affordance; if unused, Quill leaves the transcript in place rather than overwrite a
   paste that may still be in flight.
4. **Architectural escape hatch:** the IME-based injector (§2.1). Where SET_TEXT *and* clipboard both
   fail (some WebViews / canvas editors), an accessibility service genuinely cannot write — the IME
   is the only reliable path, which is why the `TextInjector` interface keeps that door open.

**D. The blueprint is a starting algorithm, not gospel — validate empirically.** You cannot know the
exact tree per app blind. For each app in the §3 matrix, **dump the real nodes** (Accessibility
Scanner / `adb shell uiautomator dump` / Layout Inspector) on the Honor device and tune the
resolver. Per-toolkit expectation: native `EditText` → SET_TEXT works; Compose → SET_TEXT on the
editable semantics node; WebView/custom editors → expect the clipboard or IME rung. The cross-app
matrix in `testing_plan.md` §3 is the gate that proves it.

---

## 6. Authentication & security

**User identity:** none in v1. There is no account and no login. The only "credential" is the
user's own provider API key; "identity" is effectively the device plus that key. No PII is
collected by us.

**Where secrets live:** API keys are encrypted with an Android Keystore key
(`KeyGenParameterSpec`, AES-256-GCM, no padding, non-exportable). Ciphertext + IV are stored
locally. Keys are decrypted into memory only for the duration of a provider call and cleared
afterwards. They are never logged and never transmitted anywhere except the chosen provider.

> **Note:** the old `EncryptedSharedPreferences` (`androidx.security:security-crypto`) was
> **deprecated in April 2025**. Do **not** build on it for new code. Use Keystore + Tink, or the
> community fork, behind the `SecretStore` interface. Mark this in `todo.md` M7.

**Backups:** all encrypted secrets/files are **excluded** from Auto Backup via
`data_extraction_rules.xml` / `android:fullBackupContent` — on restore the Keystore key won't
exist and the ciphertext would be undecryptable garbage.

**Network:** HTTPS only; default certificate validation; no traffic proxied through any server we
run.

**Copy integrity.** Copy is packaged in the signed app. No remote copy endpoint or
override parser is present. The local editor validates source edits before building.

**Accessibility data handling (privacy promise + Play expectation):** the service reads focused-
field text only to position/insert dictation, and explicitly skips password/banking fields. This
is stated in the in-app prominent disclosure and the Play data-safety form. With private mode on,
no audio or transcript is persisted.

**Session cap:** recording is hard-capped (default 5 minutes, mirroring Wispr) with a warning
before the limit, to bound battery use and avoid a runaway mic FGS.

**Crash reporting (opt-in, no backend).** There is no analytics SDK and no automatic crash upload.
An uncaught-exception handler writes a **local, scrubbed** report — stack trace, app version, device
model, Android/MagicOS version — and **never** transcript text, audio, clipboard contents, or API
keys. On the **next launch after a crash** (`pendingCrashReport`), a one-time dialog offers to send
it; the report is **user-reviewable before anything leaves the device**, and sending uses the
**system share sheet / email draft** — so there is still no server we operate. Declining discards it.
This is the only telemetry of any kind, and it is strictly opt-in per crash. *(If aggregate crash
analytics are ever wanted, a consent-gated Sentry is the documented P1 alternative — it adds a
third-party SDK, so it's deliberately out of v1.)*

---

## 7. Reliability / OEM hardening (first-class, not polish)

Wispr Flow's changelog is dominated by Samsung/Xiaomi/OnePlus/Oppo/Vivo/Realme/Huawei/Motorola
silently killing the service or revoking accessibility. Budget for it:
- Prompt for battery-optimisation exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and, best-
  effort, deep-link to OEM auto-start settings.
- **Detect overlay/accessibility revocation — realistically.** A revoked accessibility service is
  *dead* and cannot observe its own revocation, and MagicOS suppresses the very background work
  (`WorkManager`/`AlarmManager`) that would otherwise catch it. So detection is **primarily an
  on-app-foreground re-check** (compare expected state against `Settings.Secure` /
  `AccessibilityManager` each time Quill is opened), backed by a **best-effort periodic check where
  the OEM permits it**. On revocation detected, post a high-priority notification deep-linking to the
  exact settings screen. **Do not assume real-time detection** — on aggressive OEMs the alert may
  only surface on next foreground, and the testing-plan criteria are written to match.
- Auto-restart the bubble on app foreground and after boot.
- Keep the bubble visible and re-grant-able if microphone permission is pulled mid-session (never
  crash).

### Primary test device: Honor Magic 6 Pro (MagicOS 8/9, Android 14/15)
The developer's daily driver and main test device — and close to the **worst case** for this app.
MagicOS forcibly closes background apps and is known to silently revoke accessibility permission.
- **Whitelist the dev device manually before M4** (App launch → Manual → enable auto-launch /
  secondary launch / run-in-background; Battery → Protected/unrestricted), or nothing survives long
  enough to develop against. Exact steps in `testing_plan.md`.
- **Pull a *minimal* keep-alive forward to ~M4** (battery-exemption prompt + sticky FGS +
  auto-restart) — full hardening stays at M11, but on Honor the service dies during normal dev
  without it.
- Use an OEM battery-settings helper library (`betterypermissionhelper` / `AppKillerManager`-style)
  to deep-link straight to Honor's whitelist screens.
- **Good news:** the global Magic 6 Pro ships with full Google Play, so the public-Play path is
  testable on it directly.

---

## 8. Play Store policy & permissions risk

The biggest external risk is **Google Play review**, not the code: Quill uses the
`AccessibilityService` API for a non-accessibility (productivity) purpose and ships publicly.

**Why it's sensitive.** Play permits the Accessibility API broadly, but only genuine disability
tools may claim `isAccessibilityTool="true"` and skip disclosure/consent. Every other use —
including Quill — must complete a Play Console accessibility declaration and implement an in-app
prominent disclosure + affirmative consent before the API is used. Policy also says apps must **use
a more narrowly scoped API when one exists** — and for "insert text," the narrower API is an IME, so
a reviewer can push back. Two more constraints: Google sends **periodic re-confirmation reminders**;
and **Android 16 Advanced Protection Mode** blocks/revokes permissions for apps not classified as
accessibility tools (confirm final behaviour near launch).

**Our stance.** Do **not** claim `isAccessibilityTool` (Quill isn't a disability aid). Use the
disclosure + declaration route (Wispr's posture). **Preempt the "narrower API" objection** in the
declaration and demo video: state that the core function is dictation into the *currently focused
field across any app without switching keyboards*, which an IME cannot provide; Wispr on Play is
existence proof the use is permitted.

**Submission artifacts (M12):**
- [ ] In-app prominent disclosure + affirmative consent **before** enabling the service; states what
      is accessed (focused-field text, to insert dictation) and what is **not** (passwords/banking).
- [ ] Play Console accessibility declaration.
- [ ] Demo video showing the disclosure + core purpose.
- [ ] Foreground-service-type declaration (App Content page) — `microphone`.
- [ ] Data-safety form matching reality: audio → the user's **own** provider over HTTPS; transcripts
      local + only if private mode off; **nothing collected by us**. Mismatches are a common
      rejection cause.

**Permission justification (keep handy for review):**

| Permission | Why | Risk |
|---|---|---|
| `RECORD_AUDIO` | Capture dictation | Standard |
| `SYSTEM_ALERT_WINDOW` | Bubble; also legalises background mic-FGS start | Standard for overlay apps |
| `FOREGROUND_SERVICE(_MICROPHONE)` | Record while in another app | Declare FGS type |
| `AccessibilityService` (BIND) | Detect focused field + insert | **Main review risk** — declaration + disclosure + video |
| `POST_NOTIFICATIONS` | FGS + revocation alerts | Low |
| `RECEIVE_BOOT_COMPLETED` | Restore bubble after reboot | Low |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Survive OEM battery managers | Use standard prompt |
| *(avoided)* `QUERY_ALL_PACKAGES` | — | Deliberately not requested |

**Contingency.** If the declaration is rejected twice, execute the IME fallback from §2.1.

*Policy specifics reflect Google Play Console help / Android docs as of mid-2026; re-verify against
the live pages before submitting.*

---

## 9. Architecture diagram (textual)

```text
[User taps bubble]
   │  (bubble = SYSTEM_ALERT_WINDOW overlay; its visibility lets the mic FGS start)
   ▼
OverlayService (FGS type=microphone, persistent notification)
   └─ AudioRecorder → 16 kHz mono PCM → WAV (temp, cache) → encode to FLAC before upload
   ▼
TranscriptionClient  ──HTTPS──▶  [User's STT provider]   (BYOK key from Keystore)
   ▼  transcript
CleanupClient (P1)   ──HTTPS──▶  [User's LLM provider]   (optional)
   ▼  final text
TextInjector → QuillAccessibilityService
   ├─ ACTION_SET_TEXT into focused node           (happy path)
   └─ clipboard + ACTION_PASTE / manual Paste     (fallback)

Local only: DataStore (settings) · Room (history, opt-in) · Keystore (secrets)

CopyUpdater (launch, off-main, optional)
   └─ GET  ──HTTPS──▶  [static host]  /copy/en.json (+ .sig)   → Ed25519 verify → schema → cache
      (offline / airplane / rejected bundle → baked-in assets/copy/strings.default.json)

No account. No user data leaves the device. Outbound calls: the provider call(s) above, plus a
read-only GET of the public, signed copy bundle.
```

## Independent cleanup providers (September 6, 2026)

Cleanup is opt-in and has its own provider and model selection: Groq, OpenAI, OpenRouter, Gemini or a custom OpenAI-compatible server. Switching transcription does not change cleanup. Existing installations retain their Groq cleanup selection; models are remembered per cleanup provider. Keys remain provider-specific. Custom transcription and cleanup intentionally share the configured custom endpoint and key; the settings screen explains this before saving an endpoint.

All three writing styles use the same cleanup prompt and meaning-preservation guard. Gemini text cleanup uses Google's documented OpenAI-compatible endpoint. OpenRouter uses an explicit model and latency-prioritized provider routing, without automatic cross-model or cross-provider-key fallback. The four-second cleanup deadline, HTTP/parse/empty/incomplete-output failures and rejected edits all preserve the raw transcript. Model catalogs filter for text-capable candidates where provider metadata is available; custom IDs remain user-configurable. Catalog membership is not proof that an account can invoke a model.

Sources: https://ai.google.dev/gemini-api/docs/openai and https://openrouter.ai/docs/guides/routing/provider-selection.

## Reliability follow-up: Gmail editor connection
Gmail's compose body on the Honor exposes a focused WebView with no editable descendants; the accessibility service is enabled and bound. Android 13+ accessibility input-method support now provides a second detection/insertion path using the active editor connection. The editor package must match the foreground application, its input type must indicate an editor, and password input types remain blocked. Insertion is checked against surrounding text; unconfirmed insertion preserves a clipboard fallback. Changing the foreground app during transcription does not redirect insertion to that app. Older Android retains the node-based and clipboard paths.

Cleanup now defaults on and follows the speech provider's active key and recommended model. Explicitly disabling cleanup is preserved; selecting a separate cleanup provider/model disables automatic matching. Custom servers require manual text-model selection. Multiple keys can be saved per provider, with one explicitly active key shared by that provider's transcription and cleanup. Adding/rejecting a candidate key no longer overwrites or removes a working key. Existing encrypted keys are retained; key rings are stored encrypted and excluded from backup.

Reference: https://developer.android.com/reference/android/accessibilityservice/InputMethod
