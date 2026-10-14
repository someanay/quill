# Quill — Product Requirements Document (PRD)

> September 7, 2026: Remote copy updates (M13) have been retired. The app uses
> bundled text only; the local copy tool edits source for the next signed APK/AAB.
> Earlier OTA milestones below are historical and no longer release requirements.


**Status:** Draft for review · **Owner:** Anay and Papa (Tihor online) · **Platform:** Android only (v1)
**Name:** Quill · `applicationId com.quill.quill`

\---

## 1\. Problem statement

Typing on a phone is slow and error-prone, and the OS-level voice typing that ships with
Android is mediocre: it has no cleanup pass, no custom vocabulary, and limited control. People
who write a lot on mobile — messages, emails, notes, prompts — want fast, accurate dictation
that works **everywhere they already type**, not inside one note-taking app.

Wispr Flow solved this with a floating bubble that dictates into any app. But it is a paid,
closed product that runs transcription on its own infrastructure. **Quill** delivers the same
"dictate anywhere" experience on a **bring-your-own-key** basis: the user supplies their own
speech-to-text (and optional LLM) API key, transcription happens against their own account, and
no audio or text passes through a server we operate.

**Who experiences this:** mobile-heavy writers, developers, and AI power users who already hold
an OpenAI/Groq/Deepgram-type key and want dictation without a subscription or a privacy
middleman.

**Cost of not solving it:** these users either tolerate slow typing, pay for a closed product
they can't audit, or send their voice through a third party's servers they don't control.

\---

## 2\. Goals

Outcomes, not outputs:

1. **A user can dictate into a real third-party app (WhatsApp, Gmail, Chrome) and have the text
land in the field, end to end, in under \~3 seconds for a short utterance.** (Measured on the
core happy path over WiFi / strong connectivity; metered or slow links run longer, where the P1
m4a compression path is the lever.)
2. **Text insertion succeeds in ≥90% of the apps in our compatibility matrix**, with a graceful
clipboard-paste fallback everywhere else — so the product never silently loses a dictation.
3. **A new user completes setup** (3 permissions + key entry + first successful dictation)
**in a single sitting** without external help.
4. **The user's API key and transcripts never leave the device except as a direct, encrypted
call to the provider the user chose.** Zero data collected by us.
5. **The app is accepted on the Google Play Store** with its accessibility use declared and
disclosed, and survives the periodic Play permission re-reviews.

\---

## 3\. Out of scope

The single source of truth for what Quill will **not** build. Its job is to make the boundary
explicit to keep changes within the agreed release scope.
Tags: **Won't build** = not in any near-term plan; **P1 / P2** = deliberately deferred, designed-for
but not built (see §6).

**Platform \& distribution**

* **iOS / any non-Android platform.** *Won't build (separate initiative).* The injection mechanism
is Android-specific; a second platform doubles the hardest work.
* **Tablet-optimised layouts, foldable-specific UI, Wear OS, Android Auto.** *Won't build.*
* **Home-screen widget / quick-settings tile.** *Won't build (maybe P2).*

**Transcription pipeline**

* **Streaming / real-time partial transcription.** *P2.* v1 records-then-transcribes. The
`TranscriptionClient` interface must not assume request/response, so streaming can slot in later.
* **On-device / offline STT.** *P2.* All transcription is via the user's cloud provider.
* **Multiple STT providers active at once / automatic failover.** *P1.* One active provider; the
abstraction supports more, the UI exposes one.
* **Translation, summarisation, or any "transform the meaning" feature.** *Won't build.* Quill
transcribes and lightly tidies; it does not change content.

**Editing \& input**

* **Voice editing commands** ("delete that", "new line", "select last sentence"). *P2.* v1 inserts
a transcript; it does not interpret commands.
* **Rich-text / formatted-text injection** (bold, lists, markdown into the field). *Won't build.*
Plain text only — many target fields can't accept anything else anyway.
* **An in-app undo stack for inserted text.** *Won't build.* The host app's own undo covers this.
* **Per-app settings / per-app behaviour.** *Won't build (maybe P2).*
* **Custom-dictionary *editing UI*.** *P1.5.* The substitution pipeline is P1; a management screen
comes later.

**Accounts, data \& monetisation**

* **User accounts, login, cloud settings sync.** *Won't build in v1 (would introduce the first
backend).* BYOK + local-only.
* **Any server/backend we operate.** *Won't build.* A core architectural promise — revisit only
deliberately, because it changes the privacy story and the data-safety declaration.
* **Analytics/telemetry backend.** *Won't build.* No analytics SDK. The only telemetry is an
opt-in, scrubbed, user-reviewed crash report sent via the share sheet — no backend (spec §6).
* **In-app purchases, subscriptions, paywalls, ads.** *Won't build.* BYOK *is* the model.
* **Sharing / social / export-to-formats** (PDF, docx, share-sheet of transcripts). *Won't build.*

**Audio \& misc**

* **Audio editing, noise-removal UI, or recording playback/library.** *Won't build.* Audio is a
transient means to a transcript.
* **Wake word / "hey Quill" hands-free activation.** *Won't build (maybe P2).*
* **Themes / extensive visual customisation** beyond bubble size and the contrast-safe defaults.
*Won't build for v1.*
* **History full-text search / tagging / folders.** *Won't build* (basic list + delete only, P1).

**The anti-scope-creep rule:** any addition to scope must come with **either a removal of equivalent
scope or an explicit timeline extension** — never a silent "while we're at it." Before adding
anything, check it against the problem statement (§1): if it doesn't serve "fast, accurate,
dictate-anywhere on BYOK," it goes in the parking lot, not the build.

\---

## 4\. User stories

**Persona A — the dictating user (primary):**

* As a mobile writer, I want a bubble to appear when I tap into any text field, so I can start
dictating without switching apps or keyboards.
* As a mobile writer, I want to tap the bubble, speak, and tap again to send, and see my words
appear in the field, so dictation feels like one fluid action. *(Press-and-hold to send is an
optional setting for quick snippets — see §5.)*
* As a privacy-conscious user, I want my voice transcribed against *my own* API key, so no
third party I didn't choose ever holds my audio.
* As a user, I want filler words removed and basic punctuation/capitalisation applied, so the
inserted text is usable without editing. *(P1.)*
* As a user, when insertion into a weird app fails, I want my text saved and a one-tap **Paste**
offered, so a dictation is never lost.
* As a user, I want a private mode where nothing is stored, so I can dictate sensitive content
with no local trace.

**Persona B — the setup user (same person, first run):**

* As a new user, I want to be walked through granting microphone, overlay, and accessibility
permissions one at a time with plain-language explanations, so I understand what I'm enabling
and why.
* As a new user, I want to paste my API key once and have it stored securely, so I never re-enter
it.
* As a returning user whose permission an OEM silently revoked, I want a clear notification that
takes me straight to the right settings screen, so the bubble starts working again quickly.

**Edge / negative cases (must be designed, not discovered):**

* Microphone permission revoked mid-session → the app stays alive, the bubble stays visible, and
tapping it re-prompts. It must **not** crash.
* No network / provider returns an error → the recording is saved, a **Retry** is offered, and a
clear message explains the failure.
* Invalid or expired API key → a specific, non-generic error pointing at the key settings.
* Focused field is a **password / banking field** → the accessibility service must **not** read
or write it. This is both a privacy promise and a Play-policy expectation.

\---

## 5\. Product \& UX decisions

These are the product/UX calls — made deliberately, not left to implementation. Each is a decision
with a default; the ones marked *(revisit)* are also tracked in §8.

1. **Trigger model — tap-to-toggle by default, hold-to-talk optional.** Tap the bubble to start,
tap to stop. Hold-to-talk (press-and-hold, release to send) is offered as a setting for quick
snippets. *Why:* one-handed hold-to-talk is awkward for anything longer than a sentence on a
6.8″ phone; toggle scales to long dictation.
2. **Insert, never replace.** Text lands at the cursor; existing field content is preserved.
A leading/trailing space is added only when the adjacent character requires it. *Why:* replacing
a field's contents is the fastest way to destroy a user's half-written message.
3. **The user's words, tidied — not rewritten.** v1 inserts the **raw transcript**. When the
cleanup pass ships (P1) it is *conservative*: filler removal + punctuation/capitalisation only,
never paraphrasing or changing meaning, and it can be turned off. *Why:* trust. A dictation tool
that silently rewords you is worse than no tool.
4. **The bubble appears on field focus, not always-on.** It shows when an editable field is focused
and fades when none is, with an option to pin it always-visible. *Why:* less clutter, less
battery, and it mirrors the moment the user actually wants it. (Wispr behaves this way.)
5. **Bubble states give continuous feedback.** Idle (small, semi-transparent) → recording (clear,
with a live level indicator) → transcribing (spinner) → inserted (brief confirm) → error/paste
(distinct). Draggable, snaps to the screen edge, snooze/dismiss. Must stay legible over any
background (see `spec.md` §2.1).
6. **Onboarding is one permission per screen, with a reason and a payoff.** Microphone → overlay →
accessibility (preceded by the prominent-disclosure consent screen), each explained in plain
language, ending in a **mic test** as the "it works" moment. *Why:* dumping three sensitive
permissions at once is where trust and completion rate die.
7. **A dictation is never silently lost — the product's north star.** Every failure path is
recoverable: insertion fails → clipboard + Paste; network fails → saved + Retry; silence →
"didn't catch that." *Why:* losing spoken words feels far worse than a visible error.
8. **Private mode default: OFF (history kept).** History is useful and matches Wispr's Android
default, but the privacy promise is one tap away and clearly surfaced. History is bounded to
\~1,000 entries (`spec.md` §4.3).
9. **Language: a single hint in settings, default auto-detect.** No per-dictation language switcher
in v1; the STT model auto-detects. *Why:* avoids UI for an edge most users don't need.
10. **Perceived speed is managed, not just measured.** Tapping the bubble gives instant recording
feedback (no lag); the unavoidable network wait is masked by the transcribing state. *Why:* the
round-trip can't be removed in batch mode, so it must be made to feel intentional.
11. **Audio comes from the handset mic in v1.** Since you tap an on-screen bubble, the phone is in
hand and its mic is best. Bluetooth-headset capture is a P1, not v1. *Why:* BT SCO is lower
quality, higher latency, and OEM-fragmented — not worth it for a phone-in-hand interaction.

\---

## 6\. Requirements

### Must-have — P0 (v1 does not ship without these)

|#|Requirement|Acceptance criteria|
|-|-|-|
|P0-1|Floating bubble overlay over any app|Given overlay permission is granted, when the user is in any foreground app and a text field is focused, then a draggable bubble appears; it can be moved and snoozed/dismissed.|
|P0-2|Tap-to-toggle capture in a mic foreground service|When the user taps the bubble, then a microphone foreground service starts (with its persistent notification) and records 16 kHz mono audio; when the user taps again or hits the session cap, recording stops.|
|P0-3|Transcription via user's BYOK key|Given a valid key for the selected provider, when recording stops, then the audio is uploaded over HTTPS and a transcript is returned; failures surface a Retry.|
|P0-4|Text insertion into the focused field|When a transcript is ready, then it is inserted at the cursor of the focused editable field via the accessibility service; **if** the field rejects programmatic set-text, then the text is placed on the clipboard and a one-tap Paste is offered.|
|P0-5|Sensitive-field exclusion|When the focused field is a password or otherwise flagged sensitive field, then the service neither reads nor writes it.|
|P0-6|Secure key storage|Given the user enters an API key, then it is encrypted with an Android Keystore-backed key, stored locally, excluded from backups, and never written to logs.|
|P0-7|Guided onboarding for all 3 permissions|When a new user opens the app, then they are taken through microphone, overlay, and accessibility grants — accessibility preceded by a prominent disclosure + affirmative consent — and a microphone test.|
|P0-8|Permission-revocation recovery|When any required permission is revoked while installed, then the app detects it and shows a notification/card linking directly to the right settings screen.|
|P0-9|Play-compliant disclosure \& declaration|The app ships with an in-app prominent disclosure for accessibility data use and a completed Play Console accessibility + foreground-service declaration. (See `spec.md` §8.)|
|P0-10|Silence auto-stop|When recording stays below an RMS threshold for a conservative quiet window, then recording auto-stops — so a forgotten "stop" can't upload minutes of silence. Tuned so mid-sentence pauses don't cut off.|

### Should-have — P1 (high-priority fast follows)

* **LLM cleanup pass** (optional, toggle): remove filler, fix capitalisation/punctuation, light
formatting. BYOK LLM key (may reuse the STT key if same provider).
* **Dictation history** (on by default, local-only): transcript, timestamp, destination app, char count,
success/fail; deletable; gated by private mode.
* **Settings**: provider, model, language hint, cleanup on/off, private mode, battery-optimisation
prompt.
* **Custom vocabulary** substitution applied during cleanup (UI to manage it is P1.5).
* **Bluetooth headset capture** — route input to a connected BT mic via `setCommunicationDevice()`.
v1 uses the handset mic only, but the `AudioRoutingManager` seam ships in v1 so this needs no
rework later.
* **m4a-on-slow-network compression fallback** — v1 always uploads FLAC; switch to m4a only when a
metered/slow connection makes FLAC upload latency too high.

### Future considerations — P2 (design for, don't build)

* Streaming transcription (the `TranscriptionClient` interface must not assume request/response
only).
* On-device STT as an alternate provider behind the same interface.
* Voice editing commands.
* Settings sync / accounts (would introduce the first backend).

\---

## 7\. Success metrics

**Leading (days–weeks):**

* Setup completion rate (installs → first successful dictation): target **≥70%**.
* Insertion success rate across the compatibility matrix: target **≥90%** (fallback covers the
rest).
* Median end-to-end latency, short utterance: target **≤3 s** (batch mode, measured on WiFi /
strong connectivity). Metered or slow links are expected to exceed this; the P1 m4a compression
fallback is the lever for those cases, so this target is **not** gated on cellular performance in v1.
* Crash-free sessions: target **≥99.5%** (the OEM/permission edge cases are the main threat).

**Lagging (weeks–months):**

* 7-day retention of users who complete setup.
* Play Store rating ≥4.0 with no policy strikes.
* Survival of the periodic Play accessibility re-review with zero takedowns.

**Measurement note:** with no backend and a strong privacy stance, there is no analytics SDK; the
only telemetry is the opt-in crash report (spec §6). These metrics therefore come from manual
testing + Play Console vitals only.

\---

## 8\. Decisions (resolved)

All build-blocking questions are answered; this is the quick-reference log.

|Decision|Resolution|
|-|-|
|App name / package id|**Quill**, `applicationId com.quill.quill` (verify the name is free on Play before public launch).|
|minSdk|**29** (Android 10).|
|First STT provider|OpenAI-compatible client; **default Groq** (`whisper-large-v3-turbo`), OpenAI selectable as the drop-in second.|
|Keys|**One key field per provider**, reused for cleanup when the provider does both (OpenAI/Groq). A second `llm\_api\_key` field appears only when an STT-only provider (e.g. Deepgram) is paired with a different cleanup LLM.|
|`isAccessibilityTool`|**No** — disclosure + declaration path (spec §8).|
|Silence auto-stop|**In v1**, conservative RMS safety net (§5; spec §2.2).|
|Bluetooth capture|**Out of v1** (P1). The audio-source abstraction (`AudioRoutingManager`) still ships in v1 so BT drops in later without rework.|
|IME injector|**Escape hatch**, not built in v1; the M3/M6 matrix decides.|
|Private mode|**Default off** (history on), prominent first-run toggle.|
|Telemetry|**None.** No analytics SDK. On the launch after a crash, a one-time **opt-in** prompt offers to send a scrubbed, user-reviewable report — no auto-upload, no backend (spec §6).|
|Compression|**FLAC in v1**; m4a-on-slow-network fallback is P1.|
|Session cap|**300 s**.|
|Cleanup pass (P1)|**Default on**, conservative (filler + punctuation only), with a toggle.|
|Serializer|**kotlinx.serialization** (compile-time → minimal R8 keep-rules).|
|History retention|**On by default**, time-bounded — keep entries within a window (default 1 h; user picks 1 h / 1 d / 1 w / 30 d / Forever or turns it off). Auto-deletes past the window; ~1,000-row backstop cap; per-item + clear-all delete; gated by private mode.|

\---

## 9\. Timeline \& phasing

No hard external deadline. Phasing follows `todo.md`, sequenced to **prove the riskiest things
first** (overlay → accessibility injection → background mic capture) before investing in STT, UI,
and polish. Treat M1–M6 (a working end-to-end happy path) as the real proof point; everything
after is productisation and Play readiness.


### Cleanup provider selection (September 6, 2026)
The cleanup provider/model is independently selectable from transcription across Groq, OpenAI, OpenRouter, Gemini and custom OpenAI-compatible servers. All styles work through the same cleanup stage. Keys are reused only within their provider. Existing Groq-only installations retain their configuration until the user changes it. Custom endpoint/key configuration is shared and explicitly identified in settings.

### Default cleanup and saved keys
Cleanup is enabled by default for new settings and automatically follows the speech provider, using its active saved key and a recommended starter text model. The user can disable cleanup or select a separate provider/model. Model suitability is task-dependent; these defaults are not a universal quality or cost guarantee. Custom servers require a manually selected text model.

Each provider can retain multiple encrypted keys. The user selects one active key; there is no automatic rotation or quota bypass. A rejected new key leaves existing keys intact.
