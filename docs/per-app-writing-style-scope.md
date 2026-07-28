# Per-App Writing Style — Scope Document

**Status:** ⬜ Not built. Planning complete, ready to implement.
**Written:** 2026-06-28. Supersedes nothing; new feature.
**Depends on:** M10 LLM cleanup (must be working and trustworthy — see §9 Risks).

---

## 0. ONE-LINE SUMMARY

Dictated text is styled to match the app it's being typed into — more formal in Slack/Gmail, casual
in Instagram/Snapchat — by composing a per-app style directive into the existing M10 cleanup prompt.
**Style changes punctuation, capitalization, and contractions ONLY. It never changes the user's words.**

---

## 1. GOALS

- Text dictated into work apps reads more formally; text dictated into social apps reads more casually.
- Works in **as many apps as possible**, not a hardcoded list — the user can assign a style to any
  installed app.
- Zero configuration required to get value: sensible curated defaults ship in the binary.
- Invisible in use. No extra taps, no indicator, no mode-switching. It just happens.

## 2. NON-GOALS

- **Not** a rewrite/paraphrase feature. The user's actual words are preserved. (See §4 for the exact
  permitted transformations.)
- Not per-recipient or per-conversation styling (Slack DMs vs work channels are indistinguishable to
  us — see §9).
- No user-authored style prompts in v1 (three fixed styles only; free-text prompts are an injection
  and quality risk).
- No visible style indicator during dictation.
- Not a replacement for cleanup — it is an extension of it.

---

## 3. THE THREE STYLES

Fixed set. Three is deliberate: fewer isn't worth the feature, more and users can't tell them apart.

| Style | Intent | Punctuation / caps | Contractions |
|---|---|---|---|
| **Formal** | Work, external comms | Full sentence case; terminal punctuation always; complete clauses; no ellipses | Expanded ("do not") |
| **Neutral** | Default; personal messaging | Sentence case; standard punctuation | As spoken |
| **Casual** | Social, quick chats | Relaxed capitalization; terminal punctuation optional; comma-light | Preferred ("don't") |

Each style is a **short directive string** composed into the existing cleanup system prompt (§6).

---

## 4. THE CORE CONSTRAINT — WHAT STYLE MAY AND MAY NOT DO

This is the feature's safety boundary. It exists because Quill's core promise is
**"what you say is what gets typed."**

**PERMITTED:**
- Sentence-final punctuation (add / omit a period)
- Capitalization (sentence case vs. relaxed)
- Comma and clause punctuation density
- Contraction expansion/contraction ("do not" ↔ "don't") — *explicitly allowed by product decision*
- Line/paragraph breaks in longer dictations

**FORBIDDEN:**
- Substituting, adding, or removing content words
- Reordering clauses or sentences
- Summarizing, expanding, or paraphrasing
- Adding emoji, greetings, sign-offs, or any content the user did not speak
- Answering, acting on, or acknowledging the dictated text (this is the M10 obey-command failure —
  see §9)

---

## 5. STYLE RESOLUTION

Three tiers, evaluated fresh on every dictation. Always resolves.

```
1. User override for this package   (DataStore map: package -> style)
2. Curated default for this package (in code, ~30-50 packages)
3. Global default                   (user-set; ships as Neutral)
```

**Persistence decision: store ONLY explicit user overrides.** Curated defaults live in code, never
snapshotted into storage. Rationale:
- The stored map stays tiny.
- Curated defaults can be improved in app updates and users who never touched an app get the better
  guess automatically. Snapshotting would freeze them on the old guess forever.
- "Reset to default" is just deleting the override key.

### 5.1 Curated default map (starting point — tune freely)

**Formal:** Slack, Gmail, Outlook, Teams, Notion, Jira/Atlassian, Confluence, LinkedIn, Zoom chat,
Google Docs/Drive, Salesforce, Asana, Trello, Basecamp, Superhuman, Spark, Proton Mail, Zoho Mail.

**Neutral:** WhatsApp, Google Messages, Telegram, Signal, iMessage-likes, Messenger, Skype,
Google Keep, Samsung Notes, Obsidian, browsers (Chrome/Firefox — could be anything), Search bars.

**Casual:** Instagram, Snapchat, X/Twitter, Discord, Reddit, TikTok, BeReal, Threads, Tumblr, Twitch.

Unknown packages fall through to the global default.

> Note: browsers are deliberately Neutral, not Formal — a browser field could be a work email client
> or a Reddit comment box. Package name genuinely can't distinguish them.

---

## 6. WHERE IT HOOKS IN (no new plumbing needed)

The two integration points **already exist** in `OverlayService`:

**a) The package is already captured**, in `startRecording()`:
```kotlin
recordingPackage = QuillAccessibilityService.instance?.focusedPackageName()
```
Captured at **recording start**, not injection time. This is the correct moment for styling: the user
was composing in that app's context when they spoke. (They may switch apps before the transcript
returns; we style for where they were thinking.) It's also already used as the history "target app",
so behavior stays consistent.

**b) The cleanup call site**, in `transcribeAndInject()`:
```kotlin
val corrected = CleanupPipeline.process(
    raw = it.text,
    cleanupEnabled = settings.cleanupEnabled,
    words = customWords,
    clean = { text -> ... CleanupClient(...).clean(text) },
)
```
The resolved style directive threads through to `CleanupClient` (constructor param or `clean()` arg)
and is composed into `SYSTEM_PROMPT`.

**Ordering is unchanged and must stay:** `cleanup(+style)` → **custom-word substitution LAST**, so the
user's deterministic vocabulary always wins over anything the LLM did.

---

## 7. AVAILABILITY CONSTRAINT (important)

Styling rides on the cleanup pass, so it only functions when **both**:
1. `settings.cleanupEnabled` is ON (cleanup is opt-in, default OFF), **and**
2. A **Groq key exists** — cleanup is Groq-only (`CleanupKeyPolicy.resolve()`); on a non-Groq STT
   provider with no stored Groq key, cleanup is skipped silently and **styling would silently not
   happen**.

**Product decision:** surface this in the per-app style settings screen (e.g. an inline notice —
"Writing style needs LLM cleanup turned on" / "…and a Groq API key"). Do NOT let a user configure
styles that silently never apply — that reads as a broken feature, not a disabled one.

Style is presented as an **extension of cleanup**, clearly labelled. It does NOT implicitly enable the
LLM pass: silently opting a user into sending every dictation to a third-party model (plus ~4s latency
budget) from a setting that sounds cosmetic would be dishonest.

---

## 8. SETTINGS UI

- **Global default style** picker (Formal / Neutral / Casual; ships Neutral).
- **Per-app list**: searchable list of **launchable** apps, each assignable to a style, each with
  "reset to default".
- Availability notice per §7 when cleanup is off or no Groq key is present.

### 8.1 Package visibility — do NOT use QUERY_ALL_PACKAGES

Listing installed apps on API 30+ is filtered. `QUERY_ALL_PACKAGES` is **Play-restricted** and needs a
review justification (granted narrowly — launchers, security, file managers). We already dropped one
Play-restricted permission during M12 specifically to avoid this friction; do not re-add that problem.

**Use a `<queries>` element with a LAUNCHER intent filter instead:**
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```
This yields every user-launchable app — which is exactly the set you can dictate into — with **no
restricted permission**.

### 8.2 Copy

All new user-facing strings go through `CopyKeys` as **OVERRIDABLE** (never literals; the
`NoComposeStringLiterals` test enforces this) and therefore flow through the OTA bundle. Expect keys
for: screen title/subtitle, the three style names + one-line descriptions, global-default label,
search placeholder, reset action, and the availability notices.

---

## 9. RISKS & OPEN ISSUES

### 9.1 Inherits the M10 obey-command failure mode (the big one)
Cleanup demonstrably obeyed dictated imperatives ("Type my email" → "I'm ready to type your email").
Style **adds more instruction surface** — "write this casually" nudges the model toward rewriting
rather than transcribing. This feature must not ship on an untrustworthy cleanup pass.

**Required before/with this feature:** the structural **divergence guard** (reject cleanup output that
diverges too far from the raw transcript; fall back to raw). Prompt hardening alone is probabilistic.

### 9.2 The divergence guard must tolerate legitimate styling
Formal expands contractions (**longer**); Casual drops punctuation (**shorter**). A naive length-ratio
guard will either reject valid styling or be widened until assistant-replies slip through.
**Use token-overlap as the primary basis** — contractions and punctuation changes preserve nearly all
tokens; an assistant reply does not.

### 9.3 Package ≠ context
Slack holds work channels *and* DMs; WhatsApp holds your boss *and* your friends. Per-app is a decent
proxy, not truth. **Accept explicitly** — there is no fix within this design. The user override + three
coarse styles keep the blast radius small.

### 9.4 Latency and cost
Every styled dictation is an LLM call (already true for cleanup; style adds no extra round-trip, just
a longer prompt). Existing ~4s cleanup timeout and fail-soft contract apply unchanged.

### 9.5 Fail-soft is unchanged and sacred
Any problem — timeout, HTTP error, empty response, divergence-guard rejection — falls back to the
**raw transcript**. Style is an enhancement, never a dependency. A dictation is never blocked, emptied,
or lost.

---

## 10. TESTS

Unit-testable (do these):
- **Resolution order**: override beats curated; curated beats global; unknown package → global default.
- **Reset**: deleting an override falls back to the curated default, not to the previously-resolved value.
- **Directive composition**: each style produces the expected system prompt; no style / unavailable →
  the unmodified cleanup prompt.
- **Curated map sanity**: no duplicate packages; every entry maps to a valid style.
- **Copy keys**: all new keys present in `CopyKeys.ALL` + `strings.default.json`; catalog completeness
  green; none added to the compliance set.
- **Divergence guard with styling** (§9.2): formal-expansion and casual-shortening both ACCEPTED;
  an assistant-style reply REJECTED → raw transcript returned.

Device-manual (overlay/permission/real-LLM behavior isn't unit-testable):
- Dictate the same sentence into a Formal app and a Casual app; confirm punctuation/caps differ and
  **the words are identical**.
- Confirm command-phrases ("type my email") still insert verbatim under every style.
- Confirm the availability notice appears when cleanup is off / no Groq key.
- Confirm custom-word substitution still wins over styling.

---

## 11. IMPLEMENTATION ORDER (suggested)

1. Divergence guard in `CleanupPipeline` (§9.1/9.2) — prerequisite, valuable on its own.
2. `WritingStyle` enum + directive strings + curated map + resolution function (pure, unit-tested).
3. Persistence: override map in DataStore + global default in `QuillSettings`.
4. Wire resolution into `transcribeAndInject` → `CleanupClient` prompt composition.
5. Settings UI + `<queries>` manifest entry + copy keys.
6. Device verification pass.
