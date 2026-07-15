# Quill — Terms of Use

**DRAFT for counsel review.** _Last updated: 2026-07-02. App: Quill for Android (`com.tihor.quill`)._
_Contact: **[FILL IN: email]**._

> Edit placeholders, host at a stable URL, then set `LegalLinks.TERMS_URL`
> (`app/src/main/java/com/tihor/quill/ui/LegalLinks.kt`).

## 1. Agreement

These Terms of Use ("Terms") are an agreement between you and **Rohit Tikmany, an individual
developer based in Mumbai, India** **[confirm legal identity with counsel]** ("we", "us"). By
installing or using Quill you accept these Terms. If you do not accept them, do not use the app.
You must be at least **18 years old** (or the age of majority where you live) and legally capable of
entering into a contract.

## 2. What Quill is

Quill is a **bring-your-own-key (BYOK)** voice-dictation tool. It records short audio clips on your
device, sends them to a **third-party speech-to-text provider that you choose and authenticate with
your own API key**, and inserts the returned text into the app you are using. An optional clean-up
step sends the transcribed text to a chat model (via Groq, using your Groq key) to tidy it. Quill has
no account system and operates no server that receives your audio, text, or keys.

## 3. Your provider account and your keys

- Supported configurations: **Groq, OpenAI, OpenRouter, Google (Gemini API)**, or a **custom
  OpenAI-compatible endpoint you specify**. You obtain your own account and API key directly from the
  provider; **your relationship with the provider is governed by their terms and privacy policy**,
  not by these Terms. Quill is not a party to that relationship.
- **You are responsible for all usage, charges, rate limits, and quota consumption on your keys.**
  Some providers offer free tiers with limits and different data-handling terms; some bill per use.
  Quill helps where it can (it validates keys before use and can hide models your key cannot
  access), but it cannot see or control your billing, and we are **not liable for any charges your
  provider bills you**.
- By saving a key you authorise Quill to make requests to that provider on your behalf from your
  device, including a small silent test request to validate the key.
- If you configure a **custom endpoint**, you are solely responsible for that endpoint — its
  security, behaviour, availability, and what it does with the audio and key you send it.
- Keep your keys confidential. Keys are stored encrypted on your device, but you are responsible for
  your device's security and for rotating a key you believe is compromised.

## 4. Permissions and the accessibility service

Quill uses an Android **accessibility service solely** to detect the focused text field and insert
your dictation into it, and an **overlay plus microphone foreground service** to show the bubble and
record while you are in another app. Each is enabled only after in-app disclosure and your explicit
consent, and you can revoke any of them at any time in system settings. Quill refuses to type into
fields Android identifies as password inputs.

## 5. Acceptable use

Use Quill lawfully. Do not use it to record any person or audio you have no right to record, to
violate any third party's rights, to breach your provider's terms, or to input content into systems
you are not authorised to use. You are responsible for the content you dictate and for where you
insert it.

## 6. Accuracy — review before you rely

Speech recognition and AI clean-up are **imperfect**. Transcripts can contain errors, omissions, or
(with clean-up enabled) rewording. **Always review inserted text before sending or acting on it.** Do
not use Quill as the sole input method for safety-critical, medical, legal, financial, or other
high-stakes content. Where a field's content matters, verify it.

## 7. Third-party services

Provider availability, accuracy, pricing, latency, and data handling are outside our control, and
providers may change or discontinue their APIs at any time. Quill may stop working with a given
provider or model without notice if the provider changes their service. We may add or remove
supported providers and models in app updates.

## 8. Updates; interface text

We may update the app from time to time. Quill may also fetch a cryptographically signed bundle of
**interface wording** (labels, help text, translations) without a full app update. This mechanism
cannot change these Terms, the privacy policy, or any consent or disclosure wording — those are fixed
in the app binary. These Terms may be revised with the app; continued use after an update constitutes
acceptance of the revised Terms. If you do not accept revised Terms, stop using and uninstall the app.

## 9. Licence and intellectual property

We grant you a personal, non-exclusive, non-transferable, revocable licence to install and use Quill
on Android devices you own or control, in accordance with these Terms and Google Play's terms. All
rights in the app not expressly granted are reserved. Quill includes open-source components listed in
the app's **open-source licences** notice; those components are licensed under their own terms.
**You retain all rights to your dictated content.** We claim no ownership of it and — having no
server — never receive it.

## 10. No warranty

Quill is provided **"as is" and "as available", without warranties of any kind**, express or implied,
including merchantability, fitness for a particular purpose, non-infringement, and uninterrupted or
error-free operation. Device manufacturers' battery-management behaviour may interrupt the bubble or
recording; we do not warrant against this.

## 11. Limitation of liability

To the maximum extent permitted by applicable law: (a) we are not liable for any indirect,
incidental, special, consequential, or exemplary damages, or for loss of profits, data, or goodwill;
(b) we are not liable for **provider charges**, provider data-handling, mis-transcribed or
mis-inserted text, or content you send anywhere using Quill; and (c) our total aggregate liability
arising out of or relating to Quill shall not exceed **[FILL IN: cap — e.g. the amount you paid us
for the app in the preceding 12 months (currently ₹0) or a nominal fixed sum, per counsel]**.
Nothing in these Terms limits liability that cannot be limited under applicable law.

## 12. Indemnity

You agree to indemnify us against claims arising from your unlawful use of Quill, your breach of
these Terms, or your breach of a provider's terms. **[Counsel: confirm scope/enforceability for a
consumer app under Indian law.]**

## 13. Termination

You may stop using Quill at any time by uninstalling it. We may suspend or terminate distribution or
support of the app at any time. Sections 5–6 and 9–14 survive termination.

## 14. Governing law and disputes

These Terms are governed by the laws of **India**, and the courts at **Mumbai, Maharashtra** shall
have exclusive jurisdiction, subject to any non-waivable consumer-protection rights in your place of
residence. **[Counsel: confirm venue/arbitration preference.]**

## 15. Severability; entire agreement

If any provision of these Terms is held unenforceable, the remainder stays in effect. These Terms and
the privacy policy are the entire agreement between you and us regarding the app.

## 16. Contact

**[FILL IN: email]**

---
_This document is not legal advice. Have counsel review before publishing — in particular the
liability cap, indemnity, and governing-law/venue clauses._
