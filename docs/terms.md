# Quill — Terms of Use

**DRAFT for review.** _Last updated: 2026-06-28. App: Quill for Android (`com.tihor.quill`)._
_Contact: **[FILL IN: email]**._

> Edit placeholders, host at a stable URL, then set `LegalLinks.TERMS_URL`
> (`app/src/main/java/com/tihor/quill/ui/LegalLinks.kt`).

## 1. What Quill is
Quill is a **bring-your-own-key (BYOK)** voice-dictation tool. It records short audio, sends it to a
**third-party speech-to-text provider that you choose and authenticate with your own API key**, and
inserts the returned text into the app you're using. An optional clean-up step sends the transcript to
an LLM provider you choose, again with your own key.

## 2. You bring your own provider account
- You are responsible for obtaining and paying for your own provider account and API key (e.g. Groq,
  OpenAI, or any OpenAI-compatible endpoint).
- **Your provider's terms and privacy policy govern your use of their API and any data you send to
  them.** Quill is not a party to that relationship and does not control how your provider handles
  your audio or text (including retention or model training).
- You are responsible for all usage, costs, and rate limits incurred on your key.

## 3. Acceptable use
Use Quill lawfully. Do not use it to capture audio you have no right to record, or to violate any
third party's rights or your provider's terms. You are responsible for the content you dictate and
where you insert it.

## 4. Permissions and the accessibility service
Quill uses an Android Accessibility service **solely** to detect the focused text field and insert your
dictation, and an overlay + microphone foreground service to record while you're in another app. You
grant these in-app and can revoke them at any time in system settings. Quill does not read or type into
password/banking fields.

## 5. No warranty
Quill is provided **"as is", without warranties of any kind**, express or implied, including
merchantability, fitness for a particular purpose, and non-infringement. Speech recognition is
imperfect; **do not rely on Quill for safety-critical or high-stakes input** without reviewing the
result. Provider availability, accuracy, and latency are outside our control.

## 6. Limitation of liability
To the maximum extent permitted by law, the developer is not liable for any indirect, incidental,
special, or consequential damages, or for provider charges, data loss, or misinserted text arising
from your use of Quill. **[FILL IN: any liability cap / governing-law clause per counsel.]**

## 7. No fee to us / changes
Quill charges you nothing for the app itself in v1 (you pay your provider directly). We may update the
app and these terms; continued use after an update means you accept the revised terms.

## 8. Governing law
**[FILL IN: jurisdiction, e.g. the courts of Mumbai, India.]**

## 9. Contact
**[FILL IN: email]**

---
_Not legal advice. Have counsel review before publishing._
