# Quill — Privacy Policy

**DRAFT for counsel review.** _Last updated: 2026-07-02. Applies to: Quill for Android
(`com.tihor.quill`), v1.0._
_Contact: **[FILL IN: support/privacy email]**._

> This policy is written to match what the app **actually does** in code (file citations below), not
> boilerplate. Edit the bracketed placeholders, host at a stable URL, then set
> `LegalLinks.PRIVACY_URL` (`app/src/main/java/com/tihor/quill/ui/LegalLinks.kt`).

## The short version

Quill is a **"bring your own key" (BYOK)** voice-dictation app. You supply your own API key for the
speech-to-text provider of your choice. **We do not operate any server that receives your audio, your
transcripts, or your API keys.** Your keys are encrypted on your device; your audio goes directly from
your device to the provider **you** chose, authenticated with **your** key. Quill has no account
system, no analytics, no ads, and no telemetry.

## Who is responsible for Quill

Quill is developed by **Rohit Tikmany, an individual developer based in Mumbai, India**
**[confirm legal identity/entity with counsel]**. For users in India, this policy is intended to be
consistent with the **Digital Personal Data Protection Act, 2023 (DPDPA)**.

## What Quill handles and where it goes

| Data | Stored where | Sent to us? | Sent to third parties? |
|------|--------------|-------------|------------------------|
| **Your provider API key(s)** — one per provider you configure | On your device only, **encrypted** (Android Keystore, AES-256-GCM, non-exportable key; a separate encrypted slot per provider) | **No** | Each key is sent only as the authentication header to **that same provider**, never to any other provider and never to us |
| **Dictation audio** | A temporary file on your device, **deleted after transcription** (kept briefly only if transcription fails, so you can retry) | **No** | Sent over HTTPS to **the speech provider you selected** (see "Your providers" below), using your key |
| **Transcribed text** | Inserted into the app you're typing in; optionally saved to a **local, on-device** history | **No** | If you enable the optional **AI clean-up**, the text (never the audio) is sent to **Groq** using your Groq key (see "AI clean-up" below) |
| **Custom-word vocabulary** | On your device only | **No** | If you add a word **by voice**, that short recording goes to your selected speech provider for transcription, like any dictation |
| **Crash reports** (optional, **off by default**) | Written on your device as a scrubbed text report (no audio, no transcripts, no keys) | **No automatic upload** | Only if **you** tap "Send" and choose an app in your own share sheet |
| **Analytics / advertising / identifiers** | — | **Not collected.** No analytics or ad SDKs are included | — |

### Key validation requests

When you save a key (or re-test it), Quill makes one small test request to that provider — a
fraction-of-a-second **silent** audio clip — to confirm the key works before you rely on it. Where a
provider distinguishes free and paid keys, Quill may make one additional minimal request to detect
your key's tier, solely so the model picker can hide models your key cannot use. These requests use
your key, go only to that provider, and contain no speech.
_Code: `stt/KeyValidator.kt`._

### App updates and interface text

Quill does not contact a copy server or fetch interface text from us. Interface
text is included in the installed app and changes only when you install an app
update, such as through Google Play. Previously cached remote text is ignored.

## Your providers are separate data controllers

Quill supports five provider configurations. When your audio (or, for clean-up, text) reaches a
provider, **that provider's own privacy policy and terms govern what they do with it**, including
retention and any model-training practices. Quill cannot control that. As of this writing:

- **Groq** — [groq.com/privacy-policy](https://groq.com/privacy-policy)
- **OpenAI** — [openai.com/policies/privacy-policy](https://openai.com/policies/privacy-policy)
- **OpenRouter** — [openrouter.ai/privacy](https://openrouter.ai/privacy) (a router: your audio is
  forwarded to the underlying model provider you select through it)
- **Google (Gemini API)** — [ai.google.dev/gemini-api/terms](https://ai.google.dev/gemini-api/terms)
  and [policies.google.com/privacy](https://policies.google.com/privacy)
- **Custom endpoint** — an OpenAI-compatible server **you** configure. You are responsible for that
  endpoint: Quill sends your audio and key wherever you point it.

**A note on free tiers.** Some providers treat free-tier API traffic differently from paid traffic.
For example, as of this writing Google's Gemini API terms state that content submitted on the
**unpaid** tier may be used to improve Google's products, while paid-tier content is not used that
way. Review your provider's current terms before dictating sensitive material on a free key.

### AI clean-up (optional)

If you enable "Tidy my dictation", the transcribed text is sent to **Groq** (chat model) using your
Groq key and the tidied version is inserted instead. If your dictation provider is not Groq, clean-up
runs only when you have also saved a Groq key; otherwise it is skipped and the raw transcript is used.
The audio itself is never sent to the clean-up model.
_Code: `cleanup/CleanupClient.kt`, `cleanup/CleanupKeyPolicy.kt`, `cleanup/CleanupPipeline.kt`._

## How your data is protected

- **API keys** are encrypted with a hardware-backed **Android Keystore** key (AES-256-GCM,
  non-exportable), one slot per provider — a key you saved for one provider is never sent to another.
  _Code: `secrets/SecretStore.kt`, `secrets/SttKeyStore.kt`._
- **Encryption in transit:** all provider and content-host requests are HTTPS.
- **Audio is transient:** recordings are temporary files, deleted after the transcription attempt
  (kept only on failure for retry, then removed). _Code: `overlay/OverlayService.kt`._
- **Backups excluded:** encrypted secrets, settings, and dictation history are excluded from Android
  cloud backup and device-to-device transfer. _Config: `app/src/main/res/xml/backup_rules.xml`,
  `data_extraction_rules.xml`._
- **Password fields are refused:** Quill will not type into fields Android identifies as password
  inputs — native password fields and standard web password inputs. (A website that mislabels its
  password field may not be detectable at the accessibility layer.) Quill only ever writes into the
  text field you focused. _Code: `accessibility/SensitiveFieldClassifier.kt`._
- **Clipboard hygiene:** when a transcript must be placed on the clipboard (fallback when direct
  insertion fails), the clip is flagged sensitive and automatically cleared shortly afterwards
  (~60 seconds). _Code: `overlay/ClipboardHygiene.kt`._

## Dictation history (local, time-limited, optional)

If history is on (default), transcribed text is saved **only on your device**, with a retention
window you control (default **1 hour**; options up to "forever", plus a row cap) and automatic
pruning. **Private mode** disables saving entirely. You can delete individual entries or clear all.
_Code: `history/HistoryStore.kt`, `history/HistoryRepository.kt`._

## Permissions and why Quill asks for them

- **Microphone (`RECORD_AUDIO`)** — to capture your dictation. Recording is user-initiated (bubble
  tap) and capped at 300 seconds per session.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`)** — the floating bubble; also what lets the
  microphone foreground service start while you're in another app.
- **Accessibility service** — solely to detect the focused text field and insert your dictation into
  it. It is enabled only after a separate in-app disclosure and your explicit consent, refuses
  password fields, and sends nothing to us.
- **Foreground service (microphone / special use)** — to keep the bubble available and record while
  you're in another app.
- **Notifications (`POST_NOTIFICATIONS`)** — recording status, the bubble's show/stop controls, and
  "couldn't insert / re-enable" alerts.
- **Run after reboot (`RECEIVE_BOOT_COMPLETED`)** — to restore the bubble after a restart, if you had
  it on. No UI is auto-launched.
- **Internet (INTERNET)** - to validate keys, discover models, and send audio/text
  directly to the provider you configured.

Quill does **not** hold the restricted "ignore battery optimisations" permission; at most it opens
the system battery-settings page for you to make that choice yourself.

## Data retention & deletion

- **API keys:** kept until you remove them, clear all data, or uninstall.
- **Audio:** deleted right after transcription (or after a failed attempt is retried/abandoned).
- **History:** auto-deleted per your retention setting; deletable individually or in bulk at any time.
- **Everything at once:** Settings → Advanced → **"Clear all data & keys"** wipes every stored key
  (all providers), all settings, dictation history, and your custom-word vocabulary.
  _Code: `settings/AppDataReset.kt`._
- **Uninstalling** the app removes all local data. Because secrets are excluded from backup, nothing
  survives in cloud backups either.

Data already sent to a provider is governed by that provider's retention policy — deletion requests
for that data go to them, under your own account.

## Children

Quill is not directed at children under **[FILL IN: 13/16/18 per market and Play target-audience
selection]**, and we do not knowingly process their data.

## Your rights (including DPDPA 2023)

Because your data stays on your device or goes only to the provider you chose under your own account,
you can exercise access, correction, and erasure directly in the app (history view, per-item and
bulk delete, "Clear all data & keys"). For any request or grievance concerning this policy, contact
**[FILL IN: email]**; **[FILL IN: DPDPA grievance-officer details and response timelines — confirm
wording with counsel]**. Data sent to your provider is subject to that provider's rights processes
under your account with them.

## Changes to this policy

We will update this policy as the app changes and revise the "Last updated" date. Material changes
will be noted in the app's release notes. The in-app privacy and consent wording is fixed in the app
binary and only changes via an app update, never remotely.

---
_This document is not legal advice. Have counsel review before publishing — in particular the DPDPA
section, the children's-age selection, and the controller identity._

Cleanup and writing styles can use a provider and model independently of speech recognition. Available choices are Groq, OpenAI, OpenRouter, Gemini and a custom OpenAI-compatible endpoint. Text is sent directly to the selected cleanup provider using that provider's saved key; disabling cleanup skips that request. Custom transcription and cleanup share the configured custom endpoint and key.
