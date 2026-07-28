# Quill — Project Handoff / Comprehensive Context

> Purpose: give a fresh AI instance (or new engineer) enough context to work on this project
> without re-deriving it. Written 2026-06-28, end of a long build+debug session.
> The author of this doc worked as reviewer/architect via a Filesystem connector into the real repo;
> "Claude Code" is the agent that does in-repo implementation.

---

## 1. WHAT QUILL IS

Quill is a **BYOK (bring-your-own-key) Android voice-dictation app** — a Wispr-Flow analog.
Package: `com.tihor.quill`. A floating bubble lets the user dictate into the currently-focused
text field of ANY app. Audio is captured locally and sent to a speech-to-text provider using the
**user's own STT API key** (Groq by default, model `whisper-large-v3-turbo`). The transcribed
(and optionally LLM-cleaned) text is injected into the focused field via an AccessibilityService
(`ACTION_SET_TEXT`, with a clipboard-paste fallback).

Core promise: **what you say is what gets typed.** This principle drives design decisions
(see the cleanup-pass bug in §8).

**Owner / user:** Rohit Tikmany (aka ynamkit / anayt), Mumbai. GitHub username: `someanay`.
Git commits authored as `ynamkit <ynamkit@gmail.com>`. Prefers: skeptical/evidence-first analysis,
decide-then-implement, "lead with the weakest point," one gating decision at a time, being
stress-tested rather than validated.

**Worst-case test device (the one that matters):** Honor **BVL-N49**, Android 16, MagicOS.
Device serial `A5CS024313005887`. MagicOS is aggressive about killing background processes and
suppressing notifications — this drove the entire M11 reliability milestone.

---

## 2. TECH STACK & ARCHITECTURE

**Android app** (`C:\Users\anayt\AndroidStudioProjects\Quill\`):
- Kotlin, Jetpack Compose, single-activity (`MainActivity` -> `QuillRoot` -> `QuillApp`).
- minSdk 29, targetSdk 36, versionCode 1, versionName "1.0".
- DataStore for settings; Room/SQLite for dictation history.
- Foreground service (`OverlayService`) hosts the bubble; `QuillAccessibilityService` does injection.
- OTA copy verification uses `net.i2p.crypto:eddsa:0.3.0` (pure-JVM Ed25519 — chosen because the
  platform JCA "Ed25519" only exists on API 33+, and this must work API 29-37 uniformly).
- Networking: OkHttp. Serialization: kotlinx.serialization.
- Release build: R8/minified, signed. ProGuard rules keep eddsa, kotlinx.serialization, OkHttp.

**Package layout** (`app/src/main/java/com/tihor/quill/`):
`accessibility, audio, cleanup, copy (+copy/ota), crash, history, overlay, reliability, secrets,
settings, stt, ui`.

**Companion web app / OTA authoring tool** (`C:\Users\anayt\quill-copy-tool\`):
- Next.js + TypeScript + Supabase. Local-only single-user authoring UI (`npm run dev`).
- Signs & publishes OTA copy bundles via GitHub Actions (Pass B). Pushed to GitHub repo
  `someanay/quill-copy-tool` (branch **main**).

---

## 3. KEY EXTERNAL RESOURCES / IDENTIFIERS

- **Supabase project ref:** `tznxppfwlgdhbvsplewl`
- **Public storage bucket:** `copy-bundles`
- **Published bundle URL (flat file):**
  `https://tznxppfwlgdhbvsplewl.supabase.co/storage/v1/object/public/copy-bundles/strings.signed.json`
- **Supabase tables:** `copy_keys`, `copy_overrides`, `bundle_revisions` (revision, status, content jsonb,
  created_at, published_at). Auth user: `quill@quill.local`.
- **Ed25519 OTA public key (pinned in app, base64):** `bds0/2n1NYbYv7yvxwW3LI+uxGBeuf4KS7LBxHHB8hA=`
  (VERIFIED this session: the live published bundle's signature validates against this key.)
- **Keystores (user-held, OUTSIDE the repo):**
  - Release signing: `C:\quillkeys\quill-release.jks`, alias `quill`, RSA 4096. Release cert SHA-256:
    `141cbc343f6d97bf26d5c9f0ed1432455b30f338bcd36d1787b63e697a8f5253` (DN L=Mumbai, ST=Maharashtra, C=IN).
    Read via `local.properties` keys: QUILL_KEYSTORE_PATH / _PASSWORD / KEY_ALIAS / KEY_PASSWORD.
  - OTA signing: `C:\quillkeys\quill-copy-signing-private.pem` + `...-public.pem` (Ed25519).
- **GitHub Actions secrets** (in quill-copy-tool repo): `ED25519_PRIVATE_KEY`, `SUPABASE_SERVICE_ROLE_KEY`.

---

## 4. ENVIRONMENT GOTCHAS (these wasted a lot of time — read them)

- **Claude Code sometimes launches in the WRONG directory** (Android repo vs quill-copy-tool). This
  caused TWO false "these files don't exist" reports about Pass B files that were actually present.
  ALWAYS have Claude Code run `pwd` first and confirm the repo before trusting any "not found" claim.
- **PowerShell lacks `adb`/`openssl`/multiline-git on PATH.** Use:
  - adb: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`
  - openssl: `& "C:\Program Files\Git\usr\bin\openssl.exe"`
  - Or use Android Studio's GUI (Logcat panel, etc.) instead of the CLI.
- **`Log.d` is stripped by R8 in release builds** — so `adb logcat -s QuillCopyUpdate` shows NOTHING on
  release even when OTA is working. Use a debug build to see OTA logs, or `logcat *:E` for errors.
- **Overlay/notification/permission behavior is NOT unit-testable** (per CLAUDE.md) — verify manually on-device.
- **Release APK can't install over a debug build** (different signing keys ->
  INSTALL_FAILED_UPDATE_INCOMPATIBLE). Uninstall first. Uninstalling wipes data -> re-onboard + re-enter key.

---

## 5. MILESTONE STATUS

- **M0-M9 done** scaffold, overlay bubble (FGS-survival), accessibility, text injection (replace-whole-field;
  web-password fields treated as sensitive), audio capture, STT, end-to-end happy path (verified on device),
  Keystore secrets + settings + "Ink & Signal" theme, onboarding + Copy Catalog, dictation history
  (Room, time-based retention).
- **M10 LLM cleanup done** committed. Optional (default-OFF, opt-in) second pass tidying the transcript.
  Same Groq key, model `llama-3.3-70b-versatile`. Order: cleanup -> custom-word substitution. Fail-soft
  (any error/timeout -> raw transcript). See §8 for an active bug here.
- **M11 OEM survival & reliability done** committed. Boot receiver, foreground recovery, battery-exemption
  deep-link (dropped the Play-restricted REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission), OEM auto-start
  deep-links, silent accessibility-revocation detection + one-tap re-enable, failed-injection clipboard
  fallback (sensitive clips auto-expire 60s). RESPECTS deliberate force-stop (no respawn war — recovery
  only fires on app-foreground/boot). Opt-in local crash reporting (default off).
  **On-device survival matrix (reboot/force-stop/overnight/revocation) NOT YET RUN.**
- **M12 Play release readiness: code done / paperwork pending** — signing config, R8 keep-rules, signed
  AAB+APK all done. PENDING (non-code ship-blockers): host privacy policy live (must now disclose the
  unconditional copy-fetch), accessibility declaration + demo video (#1 rejection risk), data-safety form,
  signed-release dictation smoke test, internal testing track.
- **M13 OTA soft-coded copy: done and PROVEN END-TO-END this session** (on BOTH debug and release builds).
  See §6 for full detail. This was the big deliverable and it works.

---

## 6. M13 — OTA SOFT-CODED COPY SYSTEM (the centerpiece; fully working)

**Goal:** update user-facing interface text WITHOUT an app release.

**Trust boundary (strict):**
- DB / web authoring app = DRAFT side. Unsigned, uses Supabase anon key. Can stage/mark-ready only.
- Storage bucket = DELIVERY side. Written ONLY by CI (GitHub Actions). The Ed25519 PRIVATE key lives
  ONLY in GitHub Actions secrets — never in the app, never in the web app.
- App only trusts a bundle whose Ed25519 signature verifies against the pinned public key.

**Compliance lock:** copy keys are split into COMPLIANCE (locked to the binary — disclosures, privacy
promises, rationale text — can NEVER be changed OTA) and OVERRIDABLE. The device merges only overridable
keys; compliance keys present in a bundle are ignored, not rejected. ~280 keys total (~11 compliance).

**Device pipeline** (`copy/ota/`): `Ed25519Verifier` (raw 32-byte key via EdDSAPublicKeySpec, fail-closed),
`CopyBundleCanonical` (byte-exact signing input), `CopyBundleVerifier`, `CopyBundleCache`, `CopyUpdater`,
`CopyUpdateStore`. Validation gauntlet: signature -> revision strictly-greater-than-applied (rollback guard)
-> schema + placeholder parity -> merge overridable keys via `CopyRepository.applyOverride`.

**Canonical signing contract** (`docs/copy-bundle-format.md`): sign over
`{"revision":<R>,"strings":{<members>}}` — compact UTF-8, keys sorted by UTF-8 byte order, specific escaping
rules, no BOM/trailing newline. The app's `CopyBundleCanonical` and the tool's `scripts/canonical.py` are
byte-for-byte equivalent (verified).

**Authoring tool (Pass A)** — `quill-copy-tool/`: Next.js+Supabase local UI. Edit overrides -> "Mark ready"
(writes a `bundle_revisions` row: revision, status=ready, frozen content snapshot; does NOT sign/upload).

**CI signer (Pass B)** — `quill-copy-tool/.github/workflows/sign-and-publish.yml` +
`scripts/sign_bundle.py` + `canonical.py` + `roundtrip_test.py` + `requirements.txt`. Manual
`workflow_dispatch`, `permissions: contents:read`, gate (round-trip self-test) runs BEFORE signing.
Reads highest status='ready' row via service_role -> builds canonical bytes -> Ed25519-signs -> uploads to
bucket object key **`strings.signed.json`** -> marks row published. Secrets from env only, never printed.

**App fetch/apply flow:**
- `CopyUpdater.checkForUpdate()` fetches `.../copy-bundles/strings.signed.json` (sends `If-None-Match` ETag;
  304 = nothing to do). Off the critical path (`Dispatchers.IO`, never blocks). Silent on failure.
  Returns `Outcome.{Accepted(rev)|NotModified|Rejected(reason)|Failed(reason)}`. Logs at tag
  `QuillCopyUpdate` (stripped by R8 in release — see §4).
- Resolution layers: baked-in (`assets/copy/strings.default.json`) -> cached OTA -> live fetch.
- Apply happens via `CopyRepository.applyCachedOverride` (re-verifies the cached bytes before applying).
  `Copy.repository()` (in `LocalCopy.kt`) calls it when the singleton is first built. `QuillRoot`
  collects `CopyRepository.resolved` (a StateFlow) as Compose state, so `copy(key)` reads recompose
  when the resolved map changes.

**IMPORTANT — apply timing was changed THIS SESSION (see §9, uncommitted):** originally OTA applied only
on the NEXT cold launch (deliberate no-flicker design). User wanted warm-open updates. `MainActivity` was
edited (uncommitted) to (a) call `applyCachedOverride` in `onStart` (every foreground), (b) run the
fetch in `onStart` too, and (c) when the fetch returns `Accepted`, immediately re-apply so the new copy
shows LIVE in the same session. NOTE: `CopyUpdater`'s KDoc still says "applies next launch / we do not
live-apply" — that doc is now STALE relative to the MainActivity behavior. Reconcile when committing.

**Publish gotchas (critical for testing OTA):**
- Every new copy edit MUST be published as a strictly HIGHER revision, or a device that already applied
  the current revision REJECTS it (rollback guard). Currently published: **revision 4**.
- A fresh install resets applied-revision to 0 (so any published revision applies).
- Under the original design you needed TWO launches (fetch on launch 1, apply on launch 2). With the
  uncommitted live-apply change, a warm open should update within the same session once the fetch lands
  (still an inherent ~1 network round-trip delay — can't show bytes before they arrive).

---

## 7. BUBBLE VISIBILITY / DISMISS / SUMMON (committed at 879ce44 + uncommitted notification tweak)

**Feature:** drag the bubble to the bottom (charging-port) edge to HIDE it; bring it back via the
notification. (Auto-return timer + shake-to-summon were DEFERRED to v1.1 — they need AlarmManager/
SensorManager + the unrun M11 device matrix, and are battery/OEM-risky. The notification is the
deliberate, reliable replacement for an auto-return timer.)

**State unification (commit 879ce44 "unify bubble-visibility state"):** previously "is the bubble shown"
was THREE unsynced states (Home toggle that start/stopped the service, a `hidden` flag, and a permission
derive). Now unified:
- **Single source of truth:** `QuillSettings.bubbleVisible` (DataStore Flow, default true). Sole writer:
  `SettingsRepository.setBubbleVisible()`, which writes DataStore + a synchronous SharedPreferences mirror
  (`BubbleVisibilityStore`, in the `settings` package, key `quill_overlay/bubble_visible`) in LOCKSTEP.
  The mirror exists so `OverlayService.addBubble` can read visibility synchronously on a START_STICKY /
  recovery restart (paint correct state, no flash).
- **Second independent flag:** `bubbleActive` = "service desired" (gates M11 recovery). Distinct from
  visibility ON PURPOSE: a hidden bubble (active=true, visible=false) must be restored-but-hidden after an
  OEM kill. Collapsing the two flags would break "hidden restores as hidden."
- **UX decision (Option A):** Home toggle OFF = bubble HIDDEN, but FGS + notification STAY ALIVE (NOT
  stopSelf). Same as drag-dismiss. The ONLY full-stop path is the notification's "Dismiss bubble"
  (`ACTION_STOP` -> clears both flags -> stopSelf). So: hide (recoverable) vs stop (fully gone) are two
  intentional levels.
- **Three entry points all write `setBubbleVisible`; `OverlayService.observeVisibility()` observes the
  Flow and `applyVisibility()` is the single applier:** Home toggle, drag-dismiss (`onBubbleDragEnd` ->
  dismiss-zone hit-test `BubbleGeometry.isInDismissZone`, threshold dp(72) off the VISIBLE bottom edge,
  computed as `params.y + viewSize - contentInset`), and notification "Show Quill" (`ACTION_SHOW`).
- **POST_NOTIFICATIONS** (Android 13+): now requested in onboarding + at first bubble-start. If denied,
  the Home toggle is the alternate way back. (This was a real bug — without the permission the FGS
  notification and its summon action were silently suppressed, stranding a hidden bubble.)
- Notification copy is soft-coded/OVERRIDABLE: `notification.{title,text}.{active,hidden,recording}`,
  `notification.action.{show,stop}`.
- Guardrails held: NO timer/sensor/AlarmManager/SensorManager. hide != stop verified in code.

**Uncommitted notification tweak (this session):** the FGS notification originally had NO
`setContentIntent`, so tapping the notification BODY did nothing (only the action buttons worked). User
wanted tapping the body to summon the bubble. A change was made so the content-intent points at the SAME
`ACTION_SHOW` service intent (NOT MainActivity), with autoCancel=false. (User reported "other fix is
working" — the notification body-tap — but confirm it's committed.)

---

## 8. ACTIVE BUG — LLM CLEANUP OBEYS COMMANDS (partially fixed, uncommitted)

**Symptom:** with M10 cleanup ENABLED, dictating an imperative like "Type my email" returns
"I'm ready to type your email" — the cleanup LLM OBEYS the text instead of cleaning it.

**Root cause:** NOT the STT provider (Whisper transcribes "Type my email" correctly). It's the cleanup
pass (`cleanup/CleanupClient.kt`, Groq `llama-3.3-70b-versatile`) treating the dictated transcript as an
instruction to itself. So it is **app-wide for any STT provider whenever cleanup is on**, not Groq-STT-
specific. Confirm by toggling cleanup off -> command phrases insert verbatim.

**Fix status (uncommitted):** `CleanupClient.SYSTEM_PROMPT` was hardened this session (frames input as
data not instruction, forbids acting on commands, includes the "type my email"->"Type my email." example).
BUT prompt-hardening alone can't guarantee an LLM won't occasionally obey. The RECOMMENDED complete fix
(a prompt for Claude Code was drafted; may not be applied yet):
1. Keep the hardened prompt.
2. **Structural divergence guard** in `CleanupPipeline`/`CleanupClient`: if the cleaned text diverges too
   much from the raw transcript (length ratio outside ~50%-200%, or too few shared tokens), DISCARD the
   cleanup and use the RAW transcript. This makes the worst case "you get the raw transcript" (= what
   cleanup-off gives), which is the guarantee a dictation tool needs. Unit-testable.
3. Wrap the transcript as clearly-delimited data in the user message too.
Fail-soft is sacred: any problem -> raw transcript, never block/empty/lose a dictation.

---

## 9. UNCOMMITTED WORK IN THE TREE (as of end of session — reconcile before proceeding)

HEAD = `879ce44` on branch `master`. Recent commits:
- `879ce44` fix(overlay): unify bubble-visibility state across toggle, dismiss, summon
- `e8b8288` feat(overlay): drag-to-dismiss bubble + notification summon (v1.1 partial)
- `f51331f` docs: update OTA copy bundle format + readiness notes
- `327b9ff` feat(copy): always-on OTA fetch + flat strings.signed.json target (M13)
- `b293a55` feat(copy): verified OTA copy bundle pipeline (M13a)

**Uncommitted direct edits made this session (verify on-device, then commit SEPARATELY):**
1. `MainActivity.kt` — OTA now fetches + applies on every foreground; live-applies on `Outcome.Accepted`
   within the session (warm-open updates). Suggested commit: `fix(copy): apply OTA bundle live on fetch
   within the same session`. NOTE the stale `CopyUpdater` KDoc (says "applies next launch") — update it.
2. `CleanupClient.kt` — hardened SYSTEM_PROMPT (see §8). Suggested commit: `fix(cleanup): harden prompt so
   imperative dictations aren't treated as commands`. Ideally add the divergence guard before committing.
3. Possibly the notification `setContentIntent` change (§7) — confirm whether committed.
4. `todo.md` — v1.1 refinement entry (dismiss/timer/shake) may have an uncommitted edit.

Android repo has **NO git remote** (`git remote -v` empty) — commits are LOCAL ONLY, not backed up.
Consider adding a private GitHub remote. (The quill-copy-tool repo IS on GitHub.)

---

## 10. WHAT'S LEFT (no more core engineering; verification + paperwork)

**On-device verification (needs the Honor BVL-N49):**
- OTA warm-open update (publish revision 5, warm-open, confirm live update) — tests the uncommitted change.
- Cleanup command-phrase test ("Type my email", "Send this", "What time is it") -> verbatim.
- Dismiss/summon + notification body-tap walkthrough.
- Toggle=hide vs notification=stop; hidden-restores-as-hidden after force-stop/reboot.
- M11 survival matrix (reboot/force-stop/overnight/revocation) — long-deferred, load-bearing for ship.
- Signed-release dictation smoke test (R8 proof — largely done, release build confirmed working).

**M12 ship-blockers (paperwork, not code):**
- Host privacy policy live + set LegalLinks URLs. MUST disclose the unconditional copy-fetch GET.
- Accessibility declaration + demo video (#1 Play rejection risk for accessibility-service apps).
- Data-safety form. Internal testing track.

**v1.1 deferred (in todo.md):** bubble auto-return timer + shake-to-summon; insert-at-cursor (vs
replace-whole-field). Also: real fonts, FLAC encoder, OpenRouter STT adapter. Consider: skip cleanup for
short imperative utterances (relates to §8).

---

## 11. KEY DESIGN PRINCIPLES / DECISION PATTERNS (to stay consistent with)

- **Fail-soft everywhere.** Cleanup, OTA, recovery — every enhancement must degrade to the safe baseline
  (raw transcript / baked-in copy / do-nothing), never block or lose user data.
- **"What you say is what gets typed"** is the product's core promise — weigh cleanup/LLM features against
  the risk of corrupting the user's literal words.
- **Compliance copy is locked to the binary; only overridable copy travels OTA.** Never loosen this.
- **All user-facing strings are soft-coded** via `CopyKeys` (OVERRIDABLE unless compliance) -> flow through
  OTA. New features add keys, not literals. A `NoComposeStringLiterals` test guards this.
- **Respect deliberate user intent** (force-stop = stay stopped; a hide isn't a stop; a dismissed bubble
  restores as dismissed).
- **Security trust boundary for OTA is non-negotiable** (private key only in CI; app trusts only pinned-key
  signatures; web app can only draft).
- **Verify claims against the actual code**, not against reports — this session had multiple false
  "file doesn't exist" / "just the notification fix" reports caused by wrong-directory sessions and
  scope confusion. Read the files.
- Overlay/permission/notification = device-manual testing only; everything else should be unit-tested.
