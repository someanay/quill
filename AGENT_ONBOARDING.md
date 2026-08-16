# AGENT_ONBOARDING.md — Quill: Comprehensive Project State

> **Purpose.** This document exists so that any AI agent (or human) with zero prior context on
> this project — Claude Code, a different model, a future you — can start **full autonomous work**
> without re-deriving anything. It is a synthesis, not a replacement: the other planning docs
> remain authoritative for their domain (see §1 "Document Map"), and this file's job is to tell you
> which ones to trust for what, give you the current ground truth where those docs have drifted,
> and flag everything risky before you touch the repo.

> **Provenance & epistemic status.** Written 2026-08-12 by an AI instance working via a read/write
> filesystem connector — **not** shell/git execution access. Every claim below was either (a) read
> directly from source files/git-internal files in this pass, (b) carried over from another doc with
> its staleness explicitly assessed, or (c) explicitly marked unverified. Where a fact could only be
> settled by running an actual `git` command, that is stated outright rather than guessed. **This
> document will itself go stale.** Before trusting anything here as "current" more than a few days
> after 2026-08-12, re-verify against `git log`, `git status`, and the source files directly — that
> is this project's own stated working principle (§9), and this document was produced by following it.

---

## 0. TL;DR

**Quill** (`com.tihor.quill`) is a **BYOK (bring-your-own-key) Android voice-dictation app** — a
Wispr Flow analog. A floating bubble overlay lets the user dictate into the currently-focused text
field of **any app**. Audio is captured locally and sent to a speech-to-text provider using **the
user's own API key** (five provider options as of this writing). The transcript is optionally
cleaned up and restyled per-app by an LLM, then inserted via an `AccessibilityService`. There is
**no backend for user data** — the only server Quill talks to besides the user's chosen AI providers
is a static, Ed25519-signed, read-only copy-bundle fetch that updates UI text without an app release.

**Core promise, said by the project itself: "what you say is what gets typed."** Every enhancement
(cleanup, per-app styling, OTA copy) is architected to degrade to a safe raw baseline rather than
ever silently lose, block, or corrupt a dictation. This fail-soft posture is the single most
important thing to preserve when extending this codebase.

**Owner:** Rohit Tikmany, goes by **ynamkit** / **anayt**. Based in Mumbai, India (IST). Git commits
authored as `ynamkit <ynamkit@gmail.com>`. Stated preferences (from direct instruction and observed
pattern): skeptical/evidence-first analysis, decide-then-implement, lead with the weakest point
first, one gating decision at a time, **verify claims against actual code, not against session
reports** — this document's own production method follows that rule.

---

## 1. Document Map — what to trust for what

Two repositories on one machine, both referenced constantly below:

| Repo | Path | Purpose | Remote |
|---|---|---|---|
| **Android app** | `C:\Users\anayt\AndroidStudioProjects\Quill\` | The app itself | **NONE — see §2, risk 1** |
| **Copy authoring tool** | `C:\Users\anayt\quill-copy-tool\` | Next.js+Supabase OTA-copy draft UI + CI signer | `someanay/quill-copy-tool` on GitHub, branch `main` |

Inside the Android repo, in descending order of how much you should trust each for **current state**:

| Doc | What it's for | Trust for current state? |
|---|---|---|
| **This file** | Orientation + risk flags + current synthesis | Yes — but re-verify if it's old (see header) |
| `CLAUDE.md` / `AGENTS.md` | **Byte-identical.** Working rules: build/adb commands, milestone order, the one hard rule (compliance copy is binary-only). Not a state doc. | Yes, for process — keep both in sync if you edit either (known duplication risk, nothing enforces it) |
| `todo.md` | The milestone state machine / task queue. Checkboxes are reliable. | **Checkboxes yes; prose under a milestone can go stale** — e.g. M3's narrative still describes decisions that were reversed *again* in later commits it was never updated to reflect (see §6.3). Post-M13 and "Tracked refinements" sections are current as of their stated ship dates. |
| `spec.md` | The engineering contract: tech stack, the two big architecture decisions with reasoning (§2.1 injection, §2.2 audio), the Copy Catalog design (§2.3), Play risk (§8). | **Decisions and reasoning: yes. Some implementation specifics are aspirational/stale** — see §5 below (FLAC-vs-WAV, original insert-at-cursor description). |
| `prd.md` | Problem statement, **explicit out-of-scope list** (read before proposing additions), user stories, P0/P1/P2 tables, decisions log, success metrics. | Yes — this is a "won't build" boundary document and is actively useful as one. |
| `testing_plan.md`, `design_system.md` | Test shells/manual scripts; visual design tokens. | Not independently re-read cover-to-cover for this document, but every other doc treats them as authoritative for their domain and nothing found in this pass contradicts that. |
| `PROJECT_HANDOFF.md` | A dated narrative snapshot. | **STALE for current state** — written 2026-06-28, its own "uncommitted work" section (§9) describes HEAD as `879ce44`; **at least 15 commits have landed since**, including features it has no knowledge of. Its §3 (external resource IDs) and §4 (environment gotchas) are stable facts, reproduced in §11–12 below, and still likely reliable. |
| `docs/*.md` + `.txt` | Feature-scoped: accessibility-declaration, audit-2026-06 (dated — check before trusting), carousel-content-spec, copy-bundle-format, m13b-authoring-tool-scope, **per-app-writing-style-scope.md** (current, detailed — see §6.4), play-readiness, store-listing, privacy-policy, terms, open-source-licenses. | Individually assessed below where relevant. |
| `graphify-out/` | Auto-generated knowledge graph (gitignored, regenerate don't rely on a stale copy). | Last generated ~2026-07-15 per its own manifest — **stale**, six weeks of work postdate it. Regenerate or just read source, as this document did. |

In `quill-copy-tool`: `README.md` is authoritative for setup and the Pass A/B trust boundary.
`app-manifest.json` (repo root) is a generated, committed snapshot of the app's copy keys, used to
gate CI publishing — see §6.5.

---

## 2. ⚠️ Critical risks — read this before doing anything else

1. **No git remote on the Android repo.** Verified directly: `.git/config` has no `[remote]`
   section at all. **Every commit — roughly 60 of them spanning ~2 months, including everything
   described in this document — exists on this one machine only.** A prior session was given a
   prompt that gated remote creation behind a keystore-safety check (`git ls-files "*.jks"` +
   `git log --all -- quill-release.jks`) specifically so a tracked signing key wouldn't get pushed
   to GitHub history. The reflog shows the `chore(build)` commit from that same prompt landed
   (JDK pin + `.gitignore` additions), but **no remote was ever created and nothing in git history
   references why it stopped there.** I cannot run `git ls-files` or `git log -- <path>` myself
   (filesystem read/write only, no shell). **Action: run those two commands before creating a
   remote. If the keystore was ever tracked, that needs history surgery (and possibly key rotation)
   before anything is pushed anywhere — do not skip the check because a `.gitignore` rule now
   exists; that only affects future adds, not history.**
2. **`quill-release.jks` is sitting in the repo root right now**
   (`C:\Users\anayt\AndroidStudioProjects\Quill\quill-release.jks`), even though `PROJECT_HANDOFF.md`
   documents the canonical signing-key location as `C:\quillkeys\quill-release.jks`, **outside** the
   repo. Whether this in-repo copy is git-tracked is exactly the unresolved question in risk 1.
   Loose `.apk` files and `uidump.xml` also still sit in the repo root — lower stakes (covered by
   `.gitignore`) but worth a tidy-up pass.
3. **`LegalLinks.kt` (`ui/LegalLinks.kt`) still has both URLs empty**
   (`PRIVACY_URL = ""`, `TERMS_URL = ""`). The privacy policy, terms of use, and open-source-licenses
   text were fully drafted weeks ago (`docs/privacy-policy.txt`, `terms.txt`,
   `open-source-licenses.txt`) but have evidently never been hosted, or were hosted and never wired
   in. The About screen currently shows these rows disabled ("Coming soon"). **This blocks Play
   submission (M12).**
4. **Current uncommitted state is unknown.** HEAD is `91d32f233c25e70962efd14e7c2505225fb14a36`
   (`feat(style): per-app writing style composed into the cleanup prompt`). I cannot run
   `git status` — **run it before starting any new work; do not assume the tree is clean.** This
   project's own history shows uncommitted work accumulating between sessions more than once.
5. **Several docs are stale for "current state" specifically** — see §1's table and §6.3 below for
   the two concrete reversed-decisions todo.md's prose hasn't caught up with.
6. **No real video clips exist yet** for the demo-video carousel —
   `app/src/main/assets/carousel/{home,accessibility,key}/` each contain only `.gitkeep`. The
   carousel code correctly fail-softs to rendering nothing when empty; this is an open **content**
   task, not a code gap.
7. **The M11 on-device survival matrix (reboot / force-stop / overnight / accessibility-revocation)
   has never been run**, per every doc that mentions it. Load-bearing for ship-readiness and has
   been outstanding since M11 was built.

---

## 3. Architecture — the decisions everything else hangs on

### 3.1 Text injection: `AccessibilityService` + overlay bubble, **not** an IME *(accepted, spec.md §2.1)*
A custom keyboard (IME) would have a clean, sanctioned way to commit text — but only works where
the user has selected it as their keyboard, and can't produce a floating "dictate anywhere" bubble.
The overlay + accessibility approach is how Wispr Flow itself works. **Dual purpose, easy to miss:**
the visible overlay is also what **legally permits a microphone foreground service to start while
the app is backgrounded** on Android 14+ — it is load-bearing for the permission model, not just UX.
An IME-based injector is kept as a documented escape hatch behind the `TextInjector` interface, for
if Play review ever blocks the accessibility approach.

### 3.2 Audio capture: `AudioRecord` → raw PCM → WAV, **not** `MediaRecorder` *(accepted, spec.md §2.2)*
Chosen because the live level meter needs raw per-buffer amplitude (`MediaRecorder.getMaxAmplitude()`
is too coarse), and a future streaming/VAD path consumes PCM as it arrives. **Spec-vs-shipped gap:**
the plan was to encode to FLAC before every upload; this was **deferred** (provisioning the NDK/libFLAC
build was judged too risky for early milestones) and **v1 ships plain WAV** via a `WavPassthroughEncoder`
behind an `AudioEncoder` seam — Groq and OpenAI both accept WAV directly on `/audio/transcriptions`, so
this cost nothing functionally, only larger upload size (~9–10 MB/5 min uncompressed vs. FLAC's roughly
half). `spec.md` §1/§2.2 still describes FLAC as "decided" — that's the doc, not the code, being stale.
Revisit if upload size/latency ever becomes a real problem.

### 3.3 Copy delivery: 3-layer resolution + Ed25519-signed OTA bundles *(accepted, spec.md §2.3)*
Full detail in §6.5. The short version: baked-in JSON asset (always-available fallback) → validated
cached override → live signed fetch, none of which can ever blank a screen. A hardcoded compliance
allowlist means the accessibility disclosure, permission rationales, privacy promises, and the BYOK
key warning **can never be changed over the air** — only the app binary can change them.

---

## 4. The end-to-end pipeline

```
tap bubble
  → OverlayService (foreground service; type=microphone while recording; persistent notification)
      → AudioRecorder (AudioRecord, 16 kHz mono PCM → WAV; silence auto-stop; 300 s hard cap)
          → package of the focused app captured HERE (recordingPackage), for per-app style later
  → TranscriptionClient  (provider selected by the user — §5)  ──HTTPS──▶  [chosen STT provider]
  → raw transcript
  → CleanupPipeline (optional; Groq-only regardless of STT provider — §6.1)
      → per-app WritingStyle directive resolved + composed into the prompt (§6.2)
      → CleanupClient call  ──HTTPS──▶  Groq chat-completions
      → CleanupGuard.preservesMeaning() gates the output — fails closed to RAW transcript (§6.1)
  → custom-word substitution (deterministic, always LAST — always wins over anything the LLM did)
  → AccessibilityTextInjector
      → sensitive-field refusal FIRST, on the actually-focused node (§7.1)
      → InsertionComposer: insert-at-caret with a caret-position placeholder heuristic (§7.2)
      → ACTION_SET_TEXT → ACTION_SET_SELECTION (reposition caret)
      → on failure: clipboard + confirmed-paste fallback (never a silent timed restore)
      → IME injector: documented escape hatch, interface-ready, not built (§3.1)
```

Every arrow that can fail degrades to the safe value one step back — never a blocked, empty, or
silently-altered dictation. This is the "fail-soft everywhere" principle (§9) made concrete.

---

## 5. Multi-provider STT

Five selectable configurations: **Groq** (default), **OpenAI**, **OpenRouter**, **Gemini**,
**Custom** (user-supplied OpenAI-compatible endpoint). Deepgram was evaluated and explicitly
dropped — not OpenAI-compatible, no adapter built.

- **Per-provider keys.** `secrets/SttKeyStore.kt` — one Android-Keystore-encrypted alias per
  provider (`stt_key_<id>`). The legacy single-slot key migrates to the Groq alias exactly once. A
  key saved for one provider is never sent to another.
- **Three client implementations** behind `TranscriptionClientFactory`:
  - `OpenAiCompatibleTranscriptionClient` — multipart form upload. Covers Groq, OpenAI, and Custom.
  - `OpenRouterTranscriptionClient` — OpenRouter's STT endpoint takes **JSON + base64 `input_audio`**,
    not multipart; this needed its own client. Model IDs are namespaced (e.g. `openai/whisper-large-v3-turbo`).
  - `GeminiTranscriptionClient` — Gemini has **no** Whisper-style endpoint; this calls the native
    `generateContent` API (`x-goog-api-key` header) with a hardened "transcribe verbatim, do not
    answer or act on this" prompt plus fence/label stripping. **Because Gemini is fundamentally a
    chat model being asked to transcribe, it carries the same obey-the-instruction risk class as the
    cleanup pass** — mitigated by prompt hardening, not structurally eliminated the way `CleanupGuard`
    eliminates it for cleanup (there's no separate raw transcript to fall back to; Gemini *is* the
    transcriber). This is disclosed to the user via a UI caveat rather than pretended away.
- **Model catalogs are provider-specific and current-gen** (e.g. OpenRouter's default is Voxtral,
  not Whisper — Whisper was delisted from its catalog). A `paidOnly` flag + `KeyValidator.hasPaidAccess`
  probe hides paid-only models once a saved key is confirmed free-tier, a deliberate bill-shock guard.
- **Key validation** (`stt/KeyValidator.kt`): a tiny silent WAV is sent through the real client to
  confirm a saved key actually works before the user relies on it.
- **Language hint** routing differs by client shape: a multipart param (Whisper-style), a JSON field
  (OpenRouter, ISO-639-1), or folded directly into the prompt text (Gemini).

---

## 6. Cleanup, per-app writing style, and the copy/OTA system

### 6.1 LLM cleanup — Groq-only, fail-soft, structurally guarded
`cleanup/CleanupClient.kt` calls Groq chat-completions **regardless of which provider did the STT**.
`CleanupKeyPolicy` resolves this: if the active STT provider isn't Groq, cleanup still runs using a
*separately stored* Groq key if one exists; if none exists, cleanup is **silently skipped** and the
raw transcript is used (never blocked, never an error the user has to handle).

**The known failure mode:** an imperative dictation ("type my email") can get *obeyed* by the LLM
instead of transcribed/tidied ("I'm ready to type your email"). Fixed with two layers:
1. A hardened system prompt (frames the input as data, forbids acting on it).
2. **`cleanup/CleanupGuard.kt`** — a structural, pure, unit-tested check on the *output*, not the
   prompt. `preservesMeaning(raw, cleaned)` requires **both**:
   - **Precision ≥ 0.7** — nearly every token in the cleaned text must already exist in the raw
     transcript (catches invented content — an assistant reply pulls in new words).
   - **Recall ≥ 0.5** — enough of the raw transcript's tokens must survive into the cleaned text
     (catches summarizing).
   Both are **multiset** (occurrence-count) comparisons over contraction-normalized tokens, so
   punctuation/casing changes and contraction expansion/contraction don't trip it. **There is
   deliberately no length-ratio check** — an earlier version had one, and it was removed because
   legitimate restyling breaks it in both directions (formal styling *expands* contractions →
   longer; casual styling *drops* punctuation → shorter), so no length window can discriminate
   "restyled" from "assistant reply" without either false-rejecting real styling or being widened
   until replies slip through. On failure: discard the cleanup output, fall back to raw — the exact
   same severity as a timeout.

### 6.2 Per-app writing style — **new, shipped 2026-07-28**, not yet on-device verified
Full spec: `docs/per-app-writing-style-scope.md` (excellent, detailed — read it directly for
anything beyond this summary). This is **feature #4 on the product's own reward/roadmap list**
("per-app-category styles similar to Wispr Flow") — see §13.

- Three fixed styles: **Formal / Neutral / Casual**. They may change **only** sentence-final
  punctuation, capitalization, comma density, contraction expansion/contraction, and paragraph
  breaks. They may **never** change, add, or remove content words, reorder clauses, or
  summarize/paraphrase. Neutral composes to the **byte-identical unmodified** cleanup prompt — a
  deliberate no-op so the shipped global default changes nothing for users who never opt in.
- **Resolution, three tiers, always resolves:** explicit per-app user override (DataStore map) →
  curated default (in code, **41 packages**, individually verified against live Play listings — the
  original draft's Skype/iMessage-likes/"Search bars" entries were dropped as invalid/nonexistent
  packages) → global default (ships Neutral).
- **Hooks into two already-existing integration points**, no new plumbing: the focused app's
  package is captured at **recording start** (not injection time — styling reflects the context the
  user was thinking in when they spoke) via the same accessibility hook that already feeds history's
  "target app" field; the resolved style directive is composed into the same `CleanupClient` prompt
  call site.
- **Availability constraint:** only functions when cleanup is on AND a Groq key exists (same gate as
  §6.1). The settings UI must say so explicitly — a style a user configured that silently never
  applies would read as a broken feature, not a disabled one.
- Uses a `<queries>` LAUNCHER-intent manifest element to list installed apps, **not** the
  Play-restricted `QUERY_ALL_PACKAGES` (the project already removed one Play-restricted permission
  in M12 specifically to reduce review friction and is deliberate about not reintroducing that class
  of problem).
- **This feature is exactly why `CleanupGuard`'s length-ratio check had to be removed** — the two
  changes shipped together in the same commit sequence, and the scope doc itself documents this as
  a prerequisite, not a coincidence.
- **Reset-settings does not clear per-app overrides**, but "Clear all data & keys" does — matches
  how custom-word overrides already behave.
- **Outstanding:** §11 step 6 in the scope doc — the on-device verification pass (confirm words stay
  identical across styles, command-phrases still insert verbatim under every style, the availability
  notice appears correctly, custom-word substitution still wins over styling).

### 6.3 Two decisions todo.md's prose no longer reflects (resolved in this pass, by reading current source)
Both of these were flagged as **unverified drift** by an earlier knowledge-graph pass over this
repo; this document resolves them definitively by reading the live source files.

- **Insert-at-caret vs. replace-whole-field.** `todo.md`'s M3 notes describe a 2026-06-24 decision
  to *replace the whole field* on every injection (because some apps, e.g. WhatsApp, expose a
  placeholder as plain `text` with zero hint metadata, making it indistinguishable from real
  content). **A later commit (`dc4e9d3b…`, "insert at caret instead of replacing the field")
  reversed this**, and the reversal is what's actually live. Confirmed by reading
  `accessibility/InsertionComposer.kt` directly: it solves the exact placeholder-vs-content problem
  that motivated the original "replace always" decision, but via **caret position** instead of
  text/hint metadata — a placeholder is only ever shown while the field is genuinely empty, so its
  caret sits at position 0; real content being dictated into has a caret past position 0. So it
  **replaces only when the caret is at 0 (or unknown)**, and **inserts at the caret** (preserving
  existing content, with a smart same-position no-double-space rule) otherwise. This is a
  genuinely elegant fix and is the **current, correct** behavior. `todo.md`'s M3 checkbox is still
  correctly checked; only its explanatory prose is stale.
- **Sensitive-field detection.** `todo.md`'s M3 notes describe a "closed, by request" decision that
  *any* browser-context editable field not positively confirmed non-secret is treated as sensitive
  (to cover Chrome reporting web password fields as plain `isPassword=false` text fields). **A later
  commit (`c5015917…`, "treat only positive password signals as sensitive") reversed this too.**
  Confirmed by reading `accessibility/SensitiveFieldClassifier.kt` directly — its own KDoc explains
  why: the blanket rule made ordinary web forms, address bars, and search boxes undictatable, which
  was the common case, for a residual risk that's already covered downstream (a refused/failed
  injection is never lost — it falls back to the clipboard rather than being written into the wrong
  place). **Current, correct behavior: positive signals only** — `isPassword` flag or a
  password-variant `inputType` (including Chrome's web-password variation).

### 6.4 Bubble visibility, dismiss, auto-return, and shake-to-summon
Unified around **two intentionally distinct DataStore-backed flags**: `bubbleVisible` (single source
of truth, synchronously mirrored into SharedPreferences so a `START_STICKY` recovery restart can
paint the correct state before DataStore resolves) and `bubbleActive` ("service desired," gates M11
recovery). They're kept separate on purpose: a *hidden* bubble must restore as hidden after an OEM
kill, which a single flag couldn't express. **Hide vs. stop are two deliberate levels** — the Home
toggle OFF, drag-to-dismiss, and the notification's "Show Quill" action all just flip visibility
(the foreground service + notification stay alive); the **only** full-stop path is the
notification's explicit "Dismiss bubble" action.

Three sub-features, all shipped:
- **Drag-to-dismiss + notification-summon** (2026-06-28) — a bottom-edge dismiss zone
  (`BubbleGeometry.isInDismissZone`), summon via the FGS notification's action.
- **~10-minute auto-return** (2026-07-27) — a **plain suspended coroutine on the living service**
  (`AutoReturn`), deliberately **not** `AlarmManager`/`WorkManager` — avoids
  `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (Play-restricted; one such permission was already dropped
  in M12). Accepted trade-off: not doze-proof (fires on next wake in deep idle, not on the minute);
  no persistence across a process kill. Scoped to drag-dismiss only — the Home master toggle being
  off is a stated preference and must never flip itself back on.
- **Shake-to-summon** (2026-07-27) — a pure, Android-free `ShakeDetector` (gravity low-pass removal
  + a tuned linear-jolt threshold + same-axis direction-reversal counting + cooldown), registered
  **only while hidden**.
- A **bin target view** (its own `NOT_FOCUSABLE|NOT_TOUCHABLE` overlay window) renders the visual
  drop target for the dismiss gesture.

### 6.5 Copy Catalog + OTA delivery + the two-repo sync system
**Goal:** no hardcoded UI strings anywhere; every string is soft-coded via a typed `CopyKeys` key,
enforced by `NoComposeStringLiteralsTest`; copy can be corrected without an app release.

- **3-layer resolution, computed fresh at startup:** baked-in `assets/copy/strings.default.json`
  (complete catalog, CI-enforced, the always-available offline fallback) → last-validated cached
  remote override → live background fetch. A bad/partial/corrupt remote bundle can **never** blank
  or half-blank a screen — worst case is the baked-in default.
- **Compliance lock:** `CopyKeys.COMPLIANCE` (11 keys: accessibility disclosure, permission
  rationales, privacy promises, the BYOK key warning) is a hardcoded allowlist **excluded** from
  `OVERRIDABLE`. The device physically ignores any attempt to override these — this is the one hard
  rule stated in both `CLAUDE.md`/`AGENTS.md` and `spec.md` §2.3/§6, and it must never be loosened.
- **Trust boundary (strict, two repos, three privilege levels):**
  - **Draft side** (`quill-copy-tool`, Supabase **anon** key) — a local Next.js authoring UI. Can
    stage overrides and mark a revision "ready." Cannot sign or publish anything.
  - **CI signer** ("Pass B," GitHub Actions in `quill-copy-tool`) — the **only** thing that can sign
    and publish. Holds the Ed25519 **private** key and the Supabase **service_role** key, both as
    CI-only secrets, never in the app or the browser. Runs a round-trip self-test gate before
    signing.
  - **Device** — verifies every fetched bundle's Ed25519 signature against a public key **pinned in
    the binary**, enforces a **monotonic revision** (rollback guard), validates schema + placeholder
    parity, and applies atomically or not at all.
- **Live warm-open apply:** `MainActivity.onStart` re-fetches and re-applies on every foreground
  (not just cold launch) — a bundle accepted mid-session shows live in the same session.
- **Two-repo sync — recently fixed, previously a manual and error-prone ritual.** The app repo now
  generates a **guarded, committed `copy-manifest.json`** at its root (`CopyManifestTest` asserts it
  can't drift from `CopyKeys`/`strings.default.json` without a red build — same pattern as the
  string-literal guard). `quill-copy-tool`'s `scripts/seed.ts` reads that manifest **directly** via
  a `QUILL_APP_REPO_PATH` env var (no more copying a snapshot file by hand), derives
  overridable/compliance status **from the manifest** rather than a hardcoded list (the old
  `src/lib/compliance.ts` was deleted — confirmed gone in this pass), and writes its own committed
  `app-manifest.json` snapshot that CI's publish gate cross-checks before allowing anything to sign.
  `seed-strings.default.json` (the old manual snapshot) was deleted — confirmed gone in this pass.
  Net effect: any new copy key added anywhere in the app is now visible to the tool on the next
  `npm run seed` (which also now runs automatically before `npm run dev`), and a stale/mismatched
  key set **cannot reach a signed, published bundle** — the pipeline fails closed.
- **Placeholder-parsing parity is load-bearing and was itself a live bug, fixed pre-launch.** A
  mismatch between the app's on-device `%`-token parser and the tool's TypeScript one would make a
  device reject an entire bundle. The device parser originally treated the space character as a
  format flag, so literal copy like "100% local" parsed a phantom token. This was identified via a
  cross-repo extractor-agreement test and fixed on the **device side** (space is not a flag; `%n` is
  argument-less) rather than papered over in the tool, on the reasoning that pre-launch is the
  cheapest possible moment to fix parser semantics — post-launch, old and new app versions would
  parse one signed bundle differently forever.

---

## 7. Text injection details

### 7.1 Sensitive-field guard
Checked on the **actually-focused node first**, before target resolution can wander to a
non-sensitive sibling — see `AccessibilityTextInjector.insert()`. Current rule: positive signals
only (§6.3). This is stated in the in-app disclosure and is both a privacy promise and a Play-policy
expectation (PRD P0-5).

### 7.2 Insertion algorithm
`InsertionComposer.composeForInjection()` — see §6.3 for the current caret-position-based
placeholder heuristic. `ACTION_SET_TEXT` replaces the *entire* field (there is no native "insert"
action), so the composer always reads existing text + caret, splices the transcript in correctly,
and hands back the caret position to restore via a follow-up `ACTION_SET_SELECTION` (SET_TEXT resets
the caret otherwise).

### 7.3 Fallback ladder
SET_TEXT on the resolved editable node → clipboard + `ACTION_PASTE`, marked `EXTRA_IS_SENSITIVE`,
**restored only after a confirmed paste** (re-reads the target node to verify the transcript landed
— never a silent timed restore, which would race a real paste and clobber it) → manual Tap-to-Paste
→ the IME escape hatch (interface-ready, not built).

---

## 8. Data model

- **Settings** — DataStore (Preferences): provider, per-provider model, language hint, cleanup
  on/off, per-app writing style overrides + global default, private mode, session cap (300 s
  default), history enabled + retention window, bubble visibility/active flags, "check for updated
  text" toggle, onboarding-complete.
- **Secrets** — Android Keystore, AES-256-GCM, non-exportable key. **Per-provider aliases**
  (`stt_key_<id>`) since the multi-provider expansion, migrated once from the original single
  `stt_api_key` slot. Excluded from Auto Backup (`data_extraction_rules.xml`/`backup_rules.xml`).
- **History** — **hand-rolled SQLite** (not Room — `PROJECT_HANDOFF.md` §2/§5 say "Room," but
  `todo.md`'s M9 section, which is the more actively-maintained source, says hand-rolled SQLite with
  no ORM; trust `todo.md` here). Time-based retention (default 1 h; user-selectable up to
  "forever"), plus a row-count backstop cap. Gated entirely off when private mode is on. Temp audio
  files are deleted immediately on successful transcription, kept only on failure (for retry), and
  this holds even in private mode — private mode governs the transcript row, not transient audio.
- **Copy catalog** — baked-in asset (source of truth) + validated cached override (app-private
  files) + applied-revision counter (DataStore) + pinned Ed25519 public key (binary constant).

---

## 9. Working principles — how to operate on this codebase

1. **Fail-soft everywhere.** Every enhancement (cleanup, styling, OTA, recovery) must degrade to its
   safe baseline (raw transcript / baked-in copy / do nothing) — never block, empty, or silently
   alter a dictation. This is the product's actual north star, not a nice-to-have.
2. **"What you say is what gets typed."** Weigh any new LLM-adjacent feature against this before
   building it. If it can rewrite content rather than just restyle punctuation/casing, it needs a
   `CleanupGuard`-style structural output check, not just a better prompt.
3. **Compliance copy is locked to the binary, forever.** Never make the 11 `CopyKeys.COMPLIANCE`
   keys overridable, in any form, for any reason.
4. **All user-facing strings are soft-coded** via `CopyKeys`, enforced by
   `NoComposeStringLiteralsTest`. New features add keys, never literals.
5. **Respect deliberate user intent.** Force-stop stays stopped (no respawn war). A hidden bubble is
   not a stopped one, and must restore as hidden, not visible.
6. **The OTA trust boundary is non-negotiable.** The Ed25519 private key and the Supabase
   service_role key exist only as CI secrets. The draft/authoring side can only ever stage.
7. **Verify claims against actual code, not against session reports or prior summaries.** This has
   burned this project multiple times (Claude Code launching in the wrong repo directory and
   reporting false "file not found"; this very document existing because prior state docs drifted).
8. **One milestone (often one sub-task) = one commit**, Conventional Commits format
   (`feat:`/`fix:`/`chore:`/`test:`/`docs:`/`refactor:`). Test-first where the relevant doc says so.
   Overlay/accessibility/mic behavior is **manual, on-device only** — it is not meaningfully
   unit-testable, and this codebase doesn't pretend otherwise.
9. **If you inherit a working tree with entangled, uncommitted, multi-feature changes**, this
   project has an established, working discipline for it: stage one feature's hunks only (hunk-split
   via exact byte-level patches if `git add -p` isn't available), verify `git diff --cached` is
   feature-only, move aside any *untracked* files that reference now-stashed symbols (a plain
   `git stash` won't touch them), `git stash push --keep-index`, run the **full** test suite against
   the **staged state alone** (not alongside the unstaged rest), commit, `git stash pop`, restore
   moved files, confirm the full tree still builds. This has been used successfully in this repo's
   own history — don't invent a different approach.
10. **Anti-scope-creep rule** (`prd.md` §3): any scope addition needs either an equivalent scope
    removal or an explicit timeline extension — never a silent "while we're at it." Check any new
    idea against the problem statement first; `prd.md` §3 has an extensive explicit won't-build list.

---

## 10. Permissions & Play Store policy posture

The biggest external risk is Play review, not the code — Quill uses `AccessibilityService` for a
non-accessibility purpose. Current posture (`spec.md` §8):

| Permission | Why | Note |
|---|---|---|
| `RECORD_AUDIO` | Capture dictation | Standard, runtime |
| `SYSTEM_ALERT_WINDOW` | Bubble; also legalizes background mic-FGS start | Standard for overlay apps |
| `FOREGROUND_SERVICE` (+`_MICROPHONE`) | Record while in another app | FGS type declared |
| `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`) | Detect focused field + insert | **Main review risk** — declaration + in-app disclosure + demo video required |
| `POST_NOTIFICATIONS` | FGS status + bubble show/summon | Runtime, Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Restore bubble after reboot | Low risk |
| ~~`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`~~ | — | **Dropped in M12** (Play-restricted); replaced with a plain deep-link to the system battery settings page |
| *(deliberately avoided)* `QUERY_ALL_PACKAGES` | — | Both the accessibility service's own package reporting and the newer per-app-style app list use narrower alternatives (a `<queries>` LAUNCHER filter for the latter) specifically to avoid this |

**Stance:** does **not** claim `isAccessibilityTool` (Quill isn't a disability aid) — uses the
disclosure + Play Console declaration route, same posture as Wispr Flow, which is cited as existence
proof the use case is permitted. If the declaration is rejected twice, the documented contingency is
executing the IME fallback.

---

## 11. Where things stand — condensed milestone status

M0–M9 done and committed (scaffold → overlay → accessibility skeleton → text injection → audio
capture → STT integration → end-to-end happy path proven on-device → Keystore secrets/settings →
onboarding + Copy Catalog foundation → local dictation history). M10 (LLM cleanup) done, later
hardened with the divergence guard (§6.1). M11 (OEM/reliability hardening, crash reporting) code
done; **the on-device survival matrix has never been run** (risk 7). M12 (Play release readiness)
code-side done (signing config, R8 keep-rules, dropped the restricted battery permission);
**paperwork still pending** (risks 2–3, plus accessibility demo video, data-safety form, store
listing, launcher icons — see §14). M13 (OTA copy) fully built and proven end-to-end, later extended
with the two-repo sync fix (§6.5). Post-M13: full multi-provider STT expansion (§5), demo-video
carousel component (built, no real content yet — risk 6), bubble dismiss/auto-return/shake (§6.4),
and per-app writing style (§6.2) — the newest major feature, code-complete and unit-tested, device
pass outstanding.

**A June 2026 audit** (`docs/audit-2026-06.md`) flagged several WIRED/DEAD/FAKE control
discrepancies (hold-to-talk, stop-on-silence toggle wiring, a keep-alive setting, crash opt-in
persistence, an inert `sessionCapSeconds` setting, ApiKey Replace/Test-again). **This document does
not re-verify their current status** — six weeks of subsequent work could plausibly have touched
some of them. Worth a fresh pass before relying on that audit's specifics.

**Launcher icons:** flagged by the graphify knowledge-graph pass in July as still the stock Android
Studio template robot (unbranded). **Not re-verified in this pass** — check
`app/src/main/res/mipmap-*/` directly before assuming this is fixed or unfixed.

---

## 12. External resources / identifiers

Carried over from `PROJECT_HANDOFF.md` §3 as stable structural facts — **not independently
re-verified live in this pass** (I have no way to test whether a Supabase project or published
bundle URL is still reachable from here):

- Supabase project ref: `tznxppfwlgdhbvsplewl`; public bucket: `copy-bundles`
- Published bundle: `https://tznxppfwlgdhbvsplewl.supabase.co/storage/v1/object/public/copy-bundles/strings.signed.json`
- Supabase tables: `copy_keys`, `copy_overrides`, `bundle_revisions`
- Ed25519 OTA public key (pinned in app, base64): `bds0/2n1NYbYv7yvxwW3LI+uxGBeuf4KS7LBxHHB8hA=`
- Release keystore (canonical location per docs): `C:\quillkeys\quill-release.jks`, alias `quill`,
  RSA 4096 — **note the discrepancy with risk 2**: a copy also currently sits in the repo root
- OTA signing keys: `C:\quillkeys\quill-copy-signing-private.pem` / `...-public.pem`
- GitHub Actions secrets (in `quill-copy-tool`): `ED25519_PRIVATE_KEY`, `SUPABASE_SERVICE_ROLE_KEY`
- Test device: Honor **BVL-N49**, Android 16 (API 36), MagicOS, serial `A5CS024313005887` — the
  deliberately-hostile daily driver this whole project is tuned against

---

## 13. Product roadmap status (the owner's own reward-ranked feature list)

| # | Feature | Status |
|---|---|---|
| 1 | Landing page + Play assets + privacy policy live | **Not done** — `LegalLinks` empty, no evidence of hosting (risk 3) |
| 2 | Easy multi-provider key addition for newcomers (incl. video guidance) | **Mostly done** — 5 providers, `KeyConsoleDialog`, free-tier-aware model hiding; explicit video guidance still open |
| 3 | Shake to bring Quill back within ~10 min | **Done** (2026-07-27); device calibration pass outstanding |
| 4 | Per-app-category writing styles | **Done** (2026-07-28, newest feature); device pass outstanding — §6.2 |
| 5 | Auto cleanup similar to Wispr Flow | **Done** (M10 + divergence-guard hardening) |
| 6 | Automated vocabulary extraction from contacts + past emails | **Not started.** `VoiceWordCapture` (manual, voice-driven vocabulary entry) exists as a head start. **Recommendation on record:** drop the "past emails" half — Gmail API scopes need an annual CASA security assessment, disproportionate for a solo BYOK app and in tension with the no-backend privacy positioning; contacts-only is the sane scope. |
| 7–9 | macOS / Windows / iOS+iPadOS native ports | **Not started.** Note: macOS is architecturally closest (accessibility APIs exist there too); iOS has **no** accessibility-injection equivalent — system-wide dictation there means a custom keyboard extension, sharing close to zero code with this Android app. Don't treat 7–9 as three instances of one port. |
| 10 | Cross-device sync using BYOS (bring-your-own-storage, WhatsApp-style) | **Not started.** Architecturally elegant fit with the BYOK philosophy when it's tackled. |

---

## 14. Legal / Play-readiness checklist

- **Privacy policy, terms, open-source licenses:** fully drafted (`docs/*.txt` are the hostable
  plain-text versions; `.md` are the editable source). Bracketed placeholders remain for: contact
  email, legal-entity confirmation, children's target age, DPDPA grievance-officer wording,
  liability cap, governing-law/venue confirmation — all explicitly marked for counsel review.
  **Not yet hosted; `LegalLinks.kt` not wired** (risk 3).
- **Accessibility declaration + demo video:** shot list exists (`docs/accessibility-declaration.md`)
  — **not filmed**. Worth reconciling against §6.3's corrected insert/sensitivity behavior before
  filming, so the video shows what the app actually does now.
- **Data-safety form:** drafted pre-multi-provider; wording is generic enough ("shared with the
  provider you choose") to likely still cover all five providers, but give it one read before
  submitting.
- **Store listing:** drafted (`docs/store-listing.md`).
- **Launcher icons:** status unverified this pass — check before assuming either way (§11).
- **Keystore backup + git remote:** blocked on risks 1–2 above.
- **M11 survival matrix, signed-release smoke test:** outstanding (risk 7).

---

## 15. Environment gotchas (saves real time — read before debugging blind)

- **`JAVA_HOME`/jlink:** Gradle can auto-pick a jlink-less JRE (e.g. from VS Code) if `JAVA_HOME`
  isn't set, causing a `jlink.exe does not exist` failure. Fix:
  `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` + `./gradlew --stop`. A commit claims
  this is now pinned via `org.gradle.java.home` in `gradle.properties` — verify that file directly
  if a build fails mysteriously.
- **PowerShell has no `adb`/`openssl` on PATH.** Use full paths (see `CLAUDE.md`/`AGENTS.md` for the
  exact aliases) or Android Studio's GUI tools instead of the CLI.
- **`Log.d` is stripped by R8 in release builds** — OTA/release logcat debugging needs a debug build,
  or filter to `logcat *:E`.
- **A release APK can't install over a debug build** (different signing →
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall first — this wipes app data, so you'll re-onboard
  and re-enter keys.
- **Claude Code has launched in the wrong repo directory before**, producing false "file not found"
  reports. Always confirm `pwd` before trusting a not-found claim.
- **adb-injected `input tap`/`swipe` never reach the overlay window** — they follow input focus, not
  the top window. Bubble gestures need a real finger; the math is covered by `BubbleGeometryTest`
  instead.
- **Honor BVL-N49 / MagicOS specifics:** whitelist Quill manually via PowerGenie settings (App
  launch management + Battery Unrestricted) — this is **not** settable via adb; doze-whitelist and
  `RUN_IN_BACKGROUND` appops do not override it. Screenshot via `adb shell screencap` + `adb pull`,
  not `exec-out … > file.png` through PowerShell (corrupts the binary).

---

## 16. Quick-reference — "I need to know X"

| You need to know... | Read |
|---|---|
| Is it safe to push to a new remote yet? | §2 risk 1 — run the two git commands first |
| Why is a setting/feature silently not working? | Check `CleanupKeyPolicy`/§6.1/§6.2 availability gates first — most "broken" features here are actually the documented fail-soft skip |
| What does the app promise users, exactly? | `docs/privacy-policy.txt` (hostable) or `.md` (editable source) |
| Why does injection behave a certain way in app X? | §6.3 + `InsertionComposer.kt`/`SensitiveFieldClassifier.kt` directly — don't trust `todo.md`'s M3 prose |
| How do I add a new STT provider? | §5 + `TranscriptionClientFactory` + the existing three client implementations as templates |
| How do I add new user-facing copy? | `CopyKeys.kt`, then `strings.default.json` — never a literal; `NoComposeStringLiteralsTest` will catch it |
| How does copy get from code to the two repos staying in sync? | §6.5 |
| What's explicitly out of scope? | `prd.md` §3 |
| What's the build/adb/test command set? | `CLAUDE.md` / `AGENTS.md` |
| What's left before Play submission? | §14 |
