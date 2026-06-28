# Quill — Privacy Policy

**DRAFT for review.** _Last updated: 2026-06-28. Applies to: Quill for Android (`com.tihor.quill`), v1.0._
_Contact: **[FILL IN: support/privacy email]**._

> This draft is written to match what the app **actually does** in code (citations below), not boilerplate.
> Edit the bracketed placeholders, host it at a stable URL, then set `LegalLinks.PRIVACY_URL`
> (`app/src/main/java/com/tihor/quill/ui/LegalLinks.kt`) to that URL.

## The short version

Quill is a **"bring your own key" (BYOK)** voice-dictation app. You supply your own speech-to-text
(and optional clean-up) provider API key. **We do not operate any server that receives your audio,
your transcripts, or your API key.** Your key is encrypted on your device; your audio goes directly
from your device to the provider **you** chose, using **your** key.

## Who is the data controller

The developer of Quill, **[FILL IN: name / entity]**, **[FILL IN: jurisdiction]**. For Indian users,
this policy is intended to be consistent with the **Digital Personal Data Protection Act, 2023 (DPDPA)**.

## What Quill collects and where it goes

| Data | Stored where | Sent to us? | Sent to third parties? |
|------|--------------|-------------|------------------------|
| **Your provider API key(s)** | On your device only, **encrypted** (Android Keystore, AES‑256‑GCM, non-exportable key) | **No** | Only as the `Authorization` header to **the provider you configured**, to authenticate your own requests |
| **Dictation audio** | A temporary file on your device, **deleted after transcription** | **No** | Streamed to **your chosen speech provider** (e.g. Groq, OpenAI) over HTTPS, using your key |
| **Transcribed text** | Inserted into the app you're typing in; optionally saved to a **local** on-device history | **No** | No (history is local; clean-up text, if enabled, is sent to your chosen LLM provider) |
| **Custom-word vocabulary** | On your device only (local storage) | **No** | No |
| **Crash reports** (optional, **off by default**) | On your device only, until you choose to share | **No automatic upload** | Only if **you** tap "Send" and pick an app in your own share sheet |

**We run no analytics SDK, no advertising SDK, and no telemetry.** There is no Quill account and no
Quill server that stores your personal data.

### The one thing Quill fetches from us
Quill may fetch a small, **static, signed "copy bundle"** (UI text/translations) over a read-only
HTTPS request. This contains **no personal data**, is the same for every user, and sends us nothing
about you beyond an ordinary network request. _(Spec §2.3; OTA delivery is a later milestone — not in v1
unless `PRIVACY_URL`/copy host is configured.)_

## How your data is protected

- **API keys** are encrypted with a hardware-backed **Android Keystore** key (AES‑256‑GCM, key size
  256, non-exportable). _Code: `app/src/main/java/com/tihor/quill/secrets/SecretStore.kt`._
- **Encryption in transit:** all provider calls are HTTPS. _Code: `stt/OpenAiCompatibleTranscriptionClient.kt`,
  `cleanup/CleanupClient.kt`._
- **Audio is transient:** the recording is written to a temporary file and **deleted** after the
  transcription attempt (kept only briefly on failure for retry, then removed). _Code: `overlay/OverlayService.kt`._
- **Backups excluded:** encrypted secrets, settings, and dictation history are excluded from Android
  cloud backup **and** device transfer (the Keystore key can't be restored anyway). _Verified config:
  `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`._
- **Sensitive fields are off-limits:** Quill refuses to read or type into password/banking fields.
  _Code: `accessibility/SensitiveFieldClassifier.kt`._
- **Clipboard hygiene:** when a transcript must be copied because it couldn't be inserted, the clip is
  flagged sensitive and auto-cleared ~60s later. _Code: `overlay/ClipboardHygiene.kt`, `overlay/OverlayService.kt`._

## Your speech/LLM provider is a separate controller

When your audio or text is sent to the provider you configured (e.g. Groq, OpenAI), **that provider's
privacy policy and terms govern what they do with it** (including any retention or model-training
practices). Quill cannot control that. Review your provider's policy and choose accordingly.

## Dictation history (on by default, local, time-limited)

If history is on, transcribed text is saved **only on your device** with a retention window (default
**1 hour**; configurable, including "keep forever" or off) and a count cap. **Private mode** disables
saving entirely. You can delete individual entries or clear all history. _Code: `history/HistoryStore.kt`,
`history/HistoryRepository.kt`, `settings/SettingsRepository.kt`._

## Permissions and why

- **Microphone (`RECORD_AUDIO`)** — to capture your dictation.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`)** — the floating bubble; also what legally lets the
  mic foreground service start while you're in another app.
- **Accessibility service** — to detect the focused text field and insert your dictation into it
  (see the separate in-app disclosure). It does **not** read password/banking fields and sends nothing
  to us.
- **Foreground service (mic / special use)** — to keep recording while you're in another app.
- **Notifications** — recording status and "couldn't insert / re-enable" alerts.
- **Run after reboot / ignore battery optimisations** — to keep the bubble alive on phones that
  aggressively kill background apps. Optional and user-controlled.

## Data retention & deletion

- API keys: until you remove them or clear app data.
- Audio: deleted right after transcription.
- History: auto-deleted per your retention setting; "Clear all" wipes it immediately.
- Everything: **Settings → Advanced → Clear all data & keys** wipes keys, settings, history, and
  vocabulary. Uninstalling the app removes all local data. _Code: `settings/AppDataReset.kt`._

## Children
Quill is not directed at children under **[FILL IN age per market]** and does not knowingly collect
their data.

## Your rights (incl. DPDPA 2023)
Because your data stays on your device (or goes only to your chosen provider), you control it directly:
access/erase via in-app history and "Clear all data". For requests about this policy, contact
**[FILL IN: email]**. _DPDPA-specific rights (access, correction, erasure, grievance redressal,
nomination) — confirm wording with counsel before publishing._

## Changes
We'll update this policy as the app changes and revise the "Last updated" date.

---
_Not legal advice. Have counsel review before publishing, especially the DPDPA and children's sections._
