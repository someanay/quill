# AGENT_MEMORY_ADDENDUM.md — Independent source-verified pass (2026-08)

> **What this file is.** A second, independent read of the Quill repo — **source code and git
> internals directly**, not the planning docs — reconciled against `AGENT_ONBOARDING.md` (2026-08-12)
> and the rebuilt knowledge graph (`graphify-out/`, refreshed same day: 1,733 nodes / 2,953 edges,
> 148 communities, 266 new nodes vs. the stale July graph). Where the two passes agree, that's
> corroboration. Where they don't, this file wins (it post-dates the doc and reads the same sources).
> Written by Kimi (pi session). Same epistemic rule as the onboarding doc: everything below was read
> from disk; anything unverifiable is marked.

---

## 1. Git state — RESOLVED risks (verified by me, with shell access the doc's author lacked)

| Onboarding risk | Verdict now |
|---|---|
| **#1 No git remote; keystore maybe tracked** | ✅ **Safe to push.** `git ls-files "*.jks"` → empty; `git log --all -- quill-release.jks` → empty. The keystore was **never tracked**, no history surgery needed. Still: `quill-release.jks` physically sits in the repo root — delete it or rely on `.gitignore` (it's ignored), and don't `git add -f` it ever. No remote exists yet; creating one is now unblocked. |
| **#4 Unknown uncommitted state** | ✅ Tree is clean at HEAD `91d32f2` except **two untracked docs**: `AGENTS.md` and `AGENT_ONBOARDING.md` themselves. They should be committed — the project's own onboarding file isn't in git, which is exactly the single-machine-loss scenario risk #1 worries about. |
| **#3 LegalLinks empty** | ❌ **Confirmed in source**: `ui/LegalLinks.kt` has `PRIVACY_URL = ""`, `TERMS_URL = ""`. Still blocks M12. The fail-soft design (disabled "Coming soon" rows) works as documented. |
| **#7 M11 survival matrix** | ❌ Still unrun; no evidence in repo contradicts the doc. |

---

## 2. Doc claims I re-verified against source (all hold)

- **`InsertionComposer`** (accessibility/): the caret-position placeholder heuristic is real and
  exactly as described — replace when `selectionEnd <= 0` / hint showing / text == hintText; insert
  with a no-double-space rule otherwise. `todo.md` M3 prose is indeed stale; the code is the truth.
- **`SensitiveFieldClassifier`**: positive-signals-only (`isPassword` + password inputType
  variations incl. `WEB_PASSWORD`). Blanket browser rule genuinely gone.
- **`CleanupGuard`**: precision ≥ 0.7 / recall ≥ 0.5, multiset overlap, contraction-normalized
  tokens both sides, no length ratio. Enforced at **two** layers (`CleanupPipeline.cleanedOrRaw` —
  the choke point — *and* inside `CleanupClient`), so no injected `clean` lambda can bypass it.
  KDoc documents the known order-blindness limitation (bag-of-words can't catch clause reordering).
- **`CleanupPipeline` order**: cleanup → custom-word substitution **last**; single output to both
  injector and history. Confirmed.
- **`CleanupKeyPolicy`**: Groq-only cleanup; non-Groq provider falls back to stored Groq key, else
  silent skip → raw. Confirmed; also confirmed the active provider's key is never cross-sent.
- **OTA chain**: `Ed25519Verifier` uses `net.i2p.crypto:eddsa` uniformly (minSdk 29 < API-33
  platform provider), raw 32-byte pinned key `bds0/2n1NYbYv7yvxwW3LI+uxGBeuf4KS7LBxHHB8hA=` (matches
  doc §12). `CopyBundleVerifier` = parse → signature over canonical `{revision, strings}` → schema
  (unknown key rejects all) → placeholder parity; revision rollback policy lives in the *caller*
  (strictly-greater on fetch, equal on cache re-apply). `Placeholders` parity = arg-index→conversion
  map, conservative (rejects benign removals — safe direction).
- **`CuratedAppStyles`**: exactly **41** `to WritingStyle` entries — matches the doc's claim.
- **`CopyKeys.COMPLIANCE`**: exactly **11** keys; `OVERRIDABLE = ALL - COMPLIANCE`. Matches the
  hard rule in AGENTS.md.
- **Manifest**: `specialUse|microphone` FGS types, `<queries>` LAUNCHER (no `QUERY_ALL_PACKAGES`),
  no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. All as documented.
- **SttKeyStore** legacy migration is exactly-once and idempotent, never overwrites an existing
  Groq-alias key. Confirmed.
- **Provider catalogs** (`SttProvider.kt`): 5 providers, three client shapes (multipart /
  OpenRouter JSON+base64 / Gemini generateContent), `paidOnly` flag + free-tier probe hiding.
  Model comments show 2026-07 catalog checks (Whisper delisted from OpenRouter → Voxtral default;
  Gemini 2.5-series omitted for a 2026-10 shutdown).

## 3. What the onboarding doc did NOT capture (new from this pass)

1. **Long-press dictates to the clipboard** (`clipboardMode` in `OverlayService.onBubbleLongPress`)
   — records without any focused field, copies the transcript, shows a 3.5 s confirmation. A
   user-facing feature absent from every doc I've read.
2. **`ClipboardHygiene`** — failed-injection transcripts staged on the clipboard are auto-cleared
   after **60 s**, but only if the current clip still carries the `Quill dictation` label (never
   clobbers a user's newer copy). User-initiated clipboard dictations are exempt from expiry. An
   M11 privacy feature no doc mentions.
3. **Cancel button + caption + bin are four separate overlay windows** managed by `OverlayService`
   (bubble, `CaptionView`, `CancelButtonView`, `BinTargetView`), with careful
   NOT_FOCUSABLE/NOT_TOUCHABLE flags and add-once/remove-on-destroy lifecycle to survive mid-drag
   service death. The doc covers the bin but not the caption/cancel windows.
4. **Quota/rate-limit surfacing**: `TranscriptionError.QuotaExhausted`/`RateLimited` → explicit
   toast ("top up" semantics) rather than a bare ERROR bubble. Nice fail-soft touch, undocumented.
5. **`DEBUG_FAKE_TRANSCRIPT`** broadcast — drives the full injection path (incl. failure→clipboard)
   with a fake transcript; not in AGENTS.md's dev-trigger list (which stops at `DEBUG_INJECT`).
6. **Sensitivity → recording refusal**: a focused password field dims the bubble to 0.45 alpha AND
   makes tap-to-record refuse with a toast — injection refusal is only the second layer.
7. **Paste-confirmation reads the node back** (`AccessibilityTextInjector.paste`): prior clipboard
   restored only after the transcript is *verified present* in the field. As documented, and the
   double-check on `focus.refresh()` before comparing is there.
8. **Auto-return toasts**: `applyVisibility` distinguishes auto-return-fired summons from user
   summons to explain itself — respects the "a preference that flips itself back on isn't a
   preference" rule for the Home toggle (timer arms only on drag-dismiss via `hiddenByDrag`).

## 4. Gaps / drift found in THIS pass (not in any doc)

1. **Hardcoded user-facing strings in `OverlayService`** — three toasts are literals:
   `"Dictation is off in password fields — for your security."` (×2),
   `"No text input selected — long-press to dictate to the clipboard."`, `"Dictating to clipboard…"`.
   `NoComposeStringLiteralsTest` only scans `ui/`, so these sail through. Violates working rule #4
   (soft-code everything via CopyKeys) in spirit; they're not compliance strings, but they can't be
   OTA-fixed or localized. **Small, safe fix: four new CopyKeys + baked-in defaults.**
2. **Notification small icons are stock** (`android.R.drawable.ic_btn_speak_now`,
   `ic_menu_edit`) — fine functionally, but off-brand for a Play listing; fold into the
   launcher-icon task.
3. **`AGENT_ONBOARDING.md` itself is untracked** (see §1) — commit it and `AGENTS.md`.

## 5. Integrated mental model (the durable part)

- **The app's spine is `OverlayService`** (66 graph edges, god node #1). It owns: 4 overlay windows,
  the mic FGS type flip, recording, STT dispatch, cleanup orchestration, style resolution, history
  save, injection result handling, clipboard hygiene, hide/show/shake/auto-return lifecycle, and
  debug receivers. Settings flow in via one `SettingsRepository.settings` Flow; visibility has a
  synchronous SharedPreferences mirror for restart-correct first paint.
- **Every risk class has a structural (not prompt-level) guard**: injection placeholder confusion →
  caret heuristic; LLM obedience → token-overlap guard at the pipeline choke point; OTA tampering →
  pinned-key Ed25519 + atomic reject; clipboard leaks → labeled 60 s auto-expiry; key cross-talk →
  per-provider Keystore aliases; paid-model bill shock → free-tier probe hiding.
- **The fail-soft contract is a total function**: every stage returns the previous stage's safe
  value — raw transcript → clipboard copy → notification. There is no code path where a dictation
  blocks or vanishes; the worst case is "it's on your clipboard with a heads-up notification".
- **Doc trust order** (reconfirmed): source > `AGENT_ONBOARDING.md` + this file > `todo.md`
  checkboxes > `todo.md`/`spec.md` prose > `PROJECT_HANDOFF.md` (stale) > `graphify-out/` (now
  fresh again as of today, but regenerate before trusting it later).
- **Remaining ship blockers, ranked**: (1) host legal docs + wire `LegalLinks.kt`; (2) run the M11
  Honor survival matrix; (3) film accessibility demo video + carousel clips; (4) commit the two
  untracked docs and create the git remote (keystore check passed); (5) launcher icons; (6)
  data-safety form + Play declaration; (7) device-verify writing-style + shake/auto-return.
- **Easy wins available now**: soft-code the 4 service toasts (§4.1), commit the untracked docs,
  delete or relocate the root-level `quill-release.jks` copy.
