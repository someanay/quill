# Quill — Play Store Listing (DRAFT)

**DRAFT for review.** Honest copy that survives review. Edit and paste into the Play Console.

## App name
**Quill — Voice Dictation (BYOK)**  _(≤30 chars: "Quill: Voice Dictation" = 22)_

## Short description (≤80 chars)
> Dictate into any app with your own speech-to-text API key. Private, on-device keys.

_(78 chars. Alt: "Voice typing into any app — bring your own STT key. No account, no servers.")_

## Full description (draft)

> **Quill turns your voice into text in any app — using your own speech-to-text key.**
>
> Tap the floating bubble, speak, and Quill inserts the text right where your cursor is — in your
> messages, notes, email, search bars, anywhere. No keyboard switching.
>
> **Bring your own key (BYOK).** Quill has no account and no server that stores your data. You add your
> own API key from a provider you choose (e.g. Groq or any OpenAI-compatible speech endpoint). Your
> audio goes directly from your phone to that provider, using your key. Your key is encrypted on your
> device and never sent to us.
>
> **Private by design.**
> • Your API key is stored encrypted on-device (Android Keystore, AES-256-GCM).
> • Audio is deleted right after it's transcribed.
> • Dictation history is optional, stays on your device, and auto-expires (default 1 hour).
> • Private mode turns off all saving.
> • Quill refuses to dictate into password and banking fields.
> • No analytics SDK. No ads. No tracking.
>
> **Built for real phones.** A persistent bubble, reliable insertion across apps, and survival on
> aggressive battery-saver phones — with controls to keep it alive (or not).
>
> **Optional AI clean-up.** If you want, Quill can tidy filler words and punctuation using an LLM
> provider key you supply (off by default).
>
> **You're in control of permissions** — see below for exactly what each is for.
>
> _Quill requires your own speech provider API key. You pay your provider directly for usage._

## "Honest permissions" block (for the listing / FAQ)

- **Microphone** — to record what you dictate.
- **Display over other apps** — the floating bubble, and to let recording continue while you're in
  another app.
- **Accessibility service** — only to find the focused text field and insert your dictation; it never
  reads password/banking fields and sends nothing to us. (You consent in-app and can revoke anytime.)
- **Notifications** — recording status and "couldn't insert / re-enable" alerts.
- **Run after restart / ignore battery optimisation** — optional, to keep the bubble alive on phones
  that aggressively kill background apps.

## Feature list (honest)
- Voice dictation into any app via a floating bubble
- BYOK: your speech provider, your key, no Quill account
- On-device encrypted key storage; audio deleted after transcription
- Insert-at-cursor with clipboard fallback when insertion isn't possible
- Optional local dictation history with auto-expiry + private mode
- Optional LLM clean-up (filler/punctuation), off by default
- Custom-word vocabulary for names/jargon
- Password/banking field protection
- Reboot + battery-kill survival controls
- Opt-in, local-only crash reporting (off by default)

## Category & tags
- **Category:** Productivity (or Tools)
- **Tags:** dictation, voice typing, speech to text, transcription, BYOK

## What to prepare (assets — YOURS)
- [ ] Feature graphic (1024×500), icon, 2–8 screenshots (show bubble + insertion + privacy/permissions)
- [ ] Short + full description finalised from above
- [ ] Content rating questionnaire
- [ ] Data Safety form (see `docs/play-readiness.md`)
- [ ] Privacy policy URL live (`docs/privacy-policy.md`)

## Notes for review-survival
- Lead with **BYOK / no servers / on-device key** — it pre-empts the "why does a dictation app need
  accessibility + mic + overlay" suspicion.
- The permissions block mirrors the privacy policy and the in-app rationales (single source of truth).
- Don't over-claim "private/secure": every claim here maps to real code (see privacy policy citations).
