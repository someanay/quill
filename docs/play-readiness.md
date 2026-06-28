# Quill — Play Release Readiness (M12)

**DRAFT / working audit.** _Generated 2026-06-28 against `com.tihor.quill` v1.0 (versionCode 1)._
Read-only audit of the app vs. Play submission requirements, plus the **single submission checklist**
(Part 5) at the end. Companion drafts: `privacy-policy.md`, `terms.md`, `accessibility-declaration.md`,
`store-listing.md`.

---

## 0. Build / signing status (verified this pass)

| Item | Status |
|------|--------|
| `./gradlew testDebugUnitTest` | ✅ pass |
| `./gradlew assembleRelease` (signed APK) | ✅ `app/build/outputs/apk/release/app-release.apk` |
| `./gradlew bundleRelease` (signed AAB) | ✅ `app/build/outputs/bundle/release/app-release.aab` |
| APK signature | ✅ v2 scheme verified, 1 signer (cert SHA-256 `141cbc34…7a8f5253`; DN `L=Mumbai, ST=…, C=IN`) |
| AAB signature | ✅ `jarsigner -verify` → "jar verified" |
| R8 / minify | ✅ `minifyReleaseWithR8` ran, **no missing-class / stripped-class warnings** on serialization/network paths |
| Signing source | `local.properties` (`QUILL_*`), read via safe loader; omitted gracefully if absent; never logged |

**Notes:** Only v2 signature present (no v3/v4). Not a blocker — with **Play App Signing**, the upload
cert only authenticates the upload; Play re-signs for distribution. Optionally enable v3/v4 later.

---

## 1. AccessibilityService — ⚠️ #1 REJECTION RISK

- Declared **`isAccessibilityTool=false`** (productivity, not assistive) → requires prominent
  disclosure + consent **and** a Console justification + demo video.
- In-app prominent disclosure + affirmative consent: **DONE** (`AccessibilityDisclosureActivity.kt`).
- Console justification text + demo-video shot-list: **drafted in `docs/accessibility-declaration.md`.**
- Exactly what it does (focused-field detect + `ACTION_SET_TEXT` insert, refuses password fields, no
  backend) is documented there with code citations.
- **Why it's the top risk:** non-assistive accessibility use is heavily scrutinised; a vague
  justification or a demo that doesn't show the disclosure-before-enable flow is the common rejection.
  → film the demo exactly per the shot list.

## 2. Foreground service: `specialUse|microphone`

- Manifest: `<service … foregroundServiceType="specialUse|microphone">` with
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "floating_dictation_bubble_overlay"`. _Manifest verified._
- Runtime starts the FGS as **specialUse** for the idle bubble and **adds the microphone type only
  while recording**. _Code: `overlay/OverlayService.kt` setForeground()._
- **Play (Android 14+) requires an FGS-type justification** at submission:
  - **microphone:** "Records the user's dictation while they are in another app; recording is
    user-initiated via the bubble and bounded (≤300s session cap)."
  - **specialUse subtype:** "Hosts the always-available floating dictation bubble overlay that the user
    taps to start dictation system-wide; there is no other FGS type that fits an on-demand overlay
    launcher." _Console asks for this exact subtype string + rationale._
- **Risk:** medium. specialUse FGS gets manual review; the justification above must match the manifest
  subtype string verbatim.

## 3. Sensitive / special permissions — per-permission justification

| Permission | Why (user-facing) | Play form answer | Risk |
|------------|-------------------|------------------|------|
| `RECORD_AUDIO` (dangerous) | Capture dictation | Core function; user-initiated; audio deleted after transcription, sent only to user's chosen provider | Low |
| `SYSTEM_ALERT_WINDOW` (special) | The floating bubble; also legalises the background mic-FGS start | Overlay is the app's primary UI; standard for overlay launchers | Low–Med |
| ~~`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`~~ | Keep the bubble alive on OEMs that kill background apps | **REMOVED (M12 fix)** — now deep-links to the battery-optimisation settings list only | ✅ resolved |
| `RECEIVE_BOOT_COMPLETED` | Restore the bubble after reboot | Auto-start to resume user-enabled dictation; no auto-launch of UI | Low |
| `POST_NOTIFICATIONS` | FGS + "couldn't insert / re-enable" alerts | Standard | Low |
| `FOREGROUND_SERVICE(_MICROPHONE/_SPECIAL_USE)` | Record while in another app | See §2 | Med |
| `INTERNET` | Send audio/text to the user's provider | Standard | Low |
| `QUERY_ALL_PACKAGES` | **not requested** (deliberately) | — | — |

> **⚠️ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is a Play-policy minefield.** Google only permits the
> direct "ignore battery optimisations" request (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) for a
> short list of acceptable use cases. A BYOK dictation app may **not** qualify, and shipping it can
> trigger rejection. **Recommended (a small behavior change for a later pass, NOT done here per the
> "no behavior change" rule):** drop the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission + the direct
> dialog, and keep only the **non-restricted** deep-link to the battery-optimisation settings list
> (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) — our `BatteryOptimization.settingsIntent()` already
> exists as that fallback. Decision is yours; flagged as a CODE-todo.

## 4. Data Safety form — COMPLETE DRAFT (map each answer to code)

**Overall posture:** Quill has **no backend that receives user data**. Keys are encrypted on-device;
audio/text go only to the user's chosen provider.

**Data collection & sharing**

| Data type | Collected? | Shared? | Notes / code |
|-----------|-----------|---------|--------------|
| Audio (voice) | Collected (transient, on-device) | **Shared with the user's chosen STT provider** (not us) | Temp file deleted post-transcription. `overlay/OverlayService.kt`, `stt/OpenAiCompatibleTranscriptionClient.kt` |
| Transcribed text / "other user content" | Collected (optional local history) | Shared with user's LLM provider **only if clean-up is enabled** | Local SQLite, retention default 1h, private-mode off-switch. `history/HistoryStore.kt`, `cleanup/CleanupClient.kt` |
| App API key (a credential the user supplies) | Stored on-device (encrypted) | **Not shared with us**; sent only as auth to the user's provider | AES-256-GCM Keystore. `secrets/SecretStore.kt` |
| Custom-word vocabulary | Collected (on-device) | Not shared | `settings/CustomWordsStore.kt` |
| Crash logs (diagnostics) | **Optional, off by default**, on-device | Only if the user manually shares via their own share sheet | `crash/CrashReporter.kt` |
| Personal identifiers / contacts / location / financial info | **Not collected** | — | — |
| Analytics / advertising | **None** (no SDK) | — | — |

**Required Data Safety answers (draft):**
- *Does your app collect or share any required user data types?* **Yes** (audio; optionally text) —
  but **not collected by us**; processed on-device and sent to the **user-configured third-party
  provider**. Declare audio as "collected" + "shared" (shared = sent off the device to the provider).
- *Is all data encrypted in transit?* **Yes** (HTTPS to provider). _Code: OkHttp HTTPS clients._
- *Can users request data deletion?* **Yes** — in-app history delete/clear + "Clear all data & keys".
  _Code: `settings/AppDataReset.kt`._
- *Is data processed ephemerally?* Audio: **yes** (deleted after transcription). History: stored per
  user retention setting (default 1h).
- *Is any data sold?* **No.**
- *Is data collection optional?* History/clean-up/crash: **optional**. Audio capture: core function,
  user-initiated.
- *Committed to Play Families policy / target audience?* **[YOURS: set audience; Quill is not for
  children.]**

> **Truthfulness caveat:** Play's Data Safety has no "sent to a third party the user chose, not to the
> developer" nuance — audio leaving the device counts as **shared**. Declare it as shared and explain
> in the listing/privacy policy that the destination is the **user's own provider account**. Do NOT
> claim "no data shared" — that would be false given audio is transmitted.

## 5. Target API / 64-bit / minSdk — compliance

| Requirement | Quill | Verdict |
|-------------|-------|---------|
| Target API recency (Play requires targeting a recent API) | `targetSdk = 36` (Android 16), `compileSdk = 37` | ✅ current |
| 64-bit support | No app-authored native code; transitive AndroidX `.so`s ship **arm64-v8a + x86_64** | ✅ compliant |
| minSdk | `29` (Android 10) | ✅ fine |
| App Bundle (AAB) required | `bundleRelease` produces a signed `.aab` | ✅ |
| versionCode | `1` | ⚠️ fine for first upload; bump per release |

_Re-verify 64-bit if FLAC/NDK lands later (CLAUDE.md notes NDK/FLAC was deferred)._

---

# PART 5 — SUBMISSION CHECKLIST (the gate to "submittable")

### [CODE — done]
- ✅ Release signing config (local.properties-driven, graceful) + signed APK & AAB verified.
- ✅ R8/minify on with audited keep rules; release builds clean (no serialization/network stripping).
- ✅ `targetSdk 36`, AAB output, 64-bit (transitive) satisfied.
- ✅ Accessibility **prominent disclosure + consent** implemented in-app.
- ✅ FGS types declared (`specialUse|microphone`) + specialUse subtype property.
- ✅ Privacy guardrails that back the Data Safety claims: encrypted keys, audio deleted, sensitive-field
  refusal, optional/local history, private mode, opt-in crash reporting, "clear all data".
- ✅ No analytics/ads SDKs; `QUERY_ALL_PACKAGES` deliberately not requested.

### [CODE — todo] (decisions for you; some are behavior changes deferred per this pass's rules)
- ✅ **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — DONE (M12 fix): permission + restricted one-tap request
  removed; both callers now deep-link to `BatteryOptimization.settingsIntent()` (the non-restricted
  settings list). Release still builds signed.
- ◻️ Set real `LegalLinks.PRIVACY_URL` / `TERMS_URL` once the policy/terms are hosted.
- ◻️ Bump `versionName`/`versionCode` for the actual release.
- ◻️ (Optional) enable v3/v4 APK signature schemes.
- ✅ (verified this pass) `backup_rules.xml` / `data_extraction_rules.xml` exclude `secrets/`,
  `datastore/`, and `quill_history.db` from cloud backup + device transfer — the privacy-policy claim is accurate.
- ◻️ Re-run R8 + a **on-device** signed-release smoke test of dictation (see "YOURS").

### [YOURS — manual / off-device, only you can do]
- 🔑 **Back up the keystore + passwords** somewhere safe (losing it = can't update the app). _Never commit it._
- 🎥 **Film the accessibility demo video** per `docs/accessibility-declaration.md` shot list.
- 🌐 **Host** `privacy-policy.md` (and `terms.md`) at stable URLs; fill the `[FILL IN]` placeholders
  (contact, entity, jurisdiction, DPDPA wording — ideally counsel-reviewed).
- 📝 **Play Console forms:** accessibility justification (draft ready), FGS-type justification (draft),
  Data Safety (draft above), content rating, target audience, store listing (`docs/store-listing.md`).
- 📱 **On-device proof the SIGNED RELEASE build actually dictates** (R8 can only be trusted after this):
  install `app-release.apk`, enable accessibility, dictate into ≥3 apps, confirm STT + clean-up +
  history work in the **minified** build. _Cannot be verified headlessly — this is the real R8 proof._
- 🖼️ Prepare store assets (icon, feature graphic, screenshots).
- _(resolved in code — the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` decision is done; see CODE list.)_

---
_This file is a draft for your review; not committed as final. Generated by the M12 readiness pass._
