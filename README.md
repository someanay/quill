# Quill — Planning Bundle

> **Quill** · `applicationId com.tihor.quill`

A BYOK (bring-your-own-key) voice-dictation app for Android. A floating bubble lets you dictate into
any text field in any app; the audio is transcribed by a speech-to-text provider using **the user's
own API key**, optionally cleaned up by an LLM, and inserted into the focused field. Modelled on
Wispr Flow's Android experience.

This bundle is deliberately **five files**: this README (overview + how to work on the repo), plus
`prd.md`, `spec.md`, `todo.md`, `testing_plan.md`.

> **Status note (2026-08):** this README began as a planning-only bundle; Quill is now a working app
> across **four** platforms — **all four build and pass their test suites**. The macOS + iOS Swift
> ports (authored on Windows) are built and XCTest-green on GitHub-hosted Macs via
> [`.github/workflows/apple.yml`](.github/workflows/apple.yml). See each platform's README and the audit/fix report.

## Platforms

| Platform | Location | Stack | Status |
|---|---|---|---|
| **Android** (reference) | `app/` | Kotlin, Compose; overlay bubble + AccessibilityService | Builds; 379 unit tests pass |
| **Windows** | `windows/` | .NET 10 WPF, NAudio; global hotkey + UI Automation | Builds; 45 tests pass |
| **macOS** | `macos/` | Swift/SwiftUI/AppKit; menu-bar + Accessibility/CGEvent | Builds + 11 tests pass (CI, Xcode 16.2) — [`macos/README.md`](macos/README.md) |
| **iOS** | `ios/` | Swift/SwiftUI; keyboard extension + in-app dictation | Builds + 11 tests pass (CI, Xcode 16.2) — [`ios/README.md`](ios/README.md) |

The four apps share the same BYOK model, provider catalog, guarded cleanup, custom-word rules, and
privacy posture. Latest cross-platform audit + fixes: [`docs/OVERNIGHT_AUDIT_2026-08-18.md`](docs/OVERNIGHT_AUDIT_2026-08-18.md).

---

## Locked decisions

| Decision | Choice | Why it matters |
|---|---|---|
| Text injection | **AccessibilityService + overlay bubble** (clipboard-paste fallback) | The exact "floats over every app" Wispr feel. Drives the permission model and the Play review risk. |
| Distribution | **Public Google Play** | Accessibility use is scrutinised hardest here — declaration, in-app disclosure, and demo video are part of the build. |
| Audio capture | **`AudioRecord` → 16 kHz mono PCM/WAV** | Raw frames are needed for the live level meter and future VAD/streaming. |
| Backend | **None for user data** | BYOK + local-only for everything the user creates: audio/transcripts go device → provider directly, no accounts. The *one* exception is a **read-only, signed copy bundle** (below). |
| Soft-coded copy | **Copy Catalog** — baked-in defaults + optional signed OTA updates | UI strings aren't hardcoded; they can be fixed without an app update. Ships complete in the APK (works fully offline / airplane), optionally fetches a **signed** update over HTTPS. Compliance strings (accessibility disclosure, privacy promises, key warning) are **locked to the binary** and can't be changed OTA. See `spec.md` §2.3. |

Full reasoning (options considered, consequences, fallbacks) lives in `spec.md` §2.

---

## The four content docs (reading order)

1. **`prd.md`** — What we're building and why: problem, **out of scope** (§3), **product & UX
   decisions** (§5), user stories, requirements (P0/P1/P2), success metrics, and the resolved-decisions log (§8).
2. **`spec.md`** — How: tech stack, **architecture decisions** (§2), components, data model,
   permissions & foreground-service model, security, OEM/Honor hardening, and **Play Store policy
   risk** (§8).
3. **`todo.md`** — Dependency-ordered checklist M0–M12 with explicit test tasks per milestone. The
   project's state machine and Claude Code's task queue.
4. **`testing_plan.md`** — Automated test shells, manual scripts, the cross-app injection matrix,
   and the Honor/MagicOS survival scripts.

---

## Repository & GitHub setup

```bash
# create the project in Android Studio first (M0), then from the project root:
git init
git add .
git commit -m "chore: project scaffold (M0)"

gh repo create quill-android --private --source=. --remote=origin
git push -u origin main
```

> **Multi-account note:** with the dual `ynamkit` / `pia-t` SSH setup, clone/add the remote using
> the **SSH host alias** for whichever account should own these commits, and set this repo's
> `git config user.email` accordingly — otherwise commits attribute to the wrong identity.
> (e.g. `git remote add origin git@github-pia:pia-t/quill-android.git`.)

**`.gitignore` (Android):**
```gitignore
*.iml
.gradle/
/local.properties        # SDK path; never commit
.idea/
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
*.keystore               # signing keys — NEVER commit
keystore.properties      # signing credentials — NEVER commit
/app/release/
```

**Never commit:** app signing keys, `keystore.properties`, any API key, or the **copy-bundle Ed25519
private signing key**. (User API keys live only on the device's Keystore — none in the repo. No
`google-services.json`: no Firebase. The only off-device dependency is a **static, signed copy bundle**
fetched read-only — its **private** signing key lives solely as a **CI secret**, never in the repo or
the app; see `spec.md` §2.3.)

**Branch & commit conventions:** one milestone (often one sub-task) per branch, merged after its
verification gate. Conventional Commits matching the boss's example — `feat:` / `fix:` / `chore:` /
`test:` / `docs:` / `refactor:` (e.g. `feat: insert text via accessibility set_text`). One green
milestone = one (or few) clean commits, so `git diff` stays reviewable and rollback is cheap.

---

## The Claude Code execution loop

Treat the docs as the contract. Per task:

1. **Load context:** `@docs/prd.md @docs/spec.md @docs/todo.md` — "read these to understand the
   project."
2. **Scope the task:** "Implement **M3** from `todo.md`. Do not write code for any other milestone."
   One checkbox at a time. Forbidden: "build the whole app."
3. **Review stop:** read the `git diff` / IDE changes before running anything.
4. **Verify:** run the build + the milestone's test/manual script. **Test on the physical Honor
   Magic 6 Pro, never an emulator** — emulators handle overlays, the accessibility service, and the
   microphone poorly, and won't reproduce MagicOS's background-killing (exactly what you need to
   catch early).
5. **Commit** on a green gate, then **check the box in `todo.md`** and note any deviation.

If a milestone is too big for one pass, split it into sub-tasks and run the loop per sub-task.

---

## On the "Claude Design → Claude Code handoff" step

The brief's handoff workflow is built around **web frontends** (it packages Tailwind/React). Quill is
**native Android / Jetpack Compose**, so a Tailwind bundle doesn't map onto the codebase. Do the
useful version instead:

- **Step 0 — establish design-system context first.** The brief says point Claude Design at your
  existing repo/token library before designing. On a greenfield app there's none yet, so invert it:
  set a **Material 3** baseline, write `docs/design_tokens.md` (colour roles incl. the contrast-safe
  bubble palette, type scale, spacing, radii), and point Claude Design at that file (and the repo
  once `Theme.kt` exists) at the start of every session. Nothing gets designed before this context.
- **Scope is small:** ~4 screens (onboarding, key entry, settings, history) + the bubble states.
- **Use Claude Design to mock those**, export `design_tokens.md`, and have Claude Code translate it
  into a Compose `Theme.kt`. That's the handoff that actually survives the jump to native.
- **Floating-bubble contrast is a hard requirement:** a dark bubble vanishes on a dark-mode app — it
  needs an outline/shadow or adaptive colour, baked into the tokens and verified in the design
  review.
- **Keep the autonomous design review:** contrast, touch targets ≥48 dp, TalkBack labels, and the
  easy-to-forget empty/loading/error states. The `frontend-design` skill is the guide at the UI
  milestones (M8–M10).

---

## The honest summary for the reviewer

The hard 80% of this app is **not the UI**. It's system integration: getting the overlay to appear
reliably, legally starting a microphone foreground service from "the background," writing into the
focused field across dozens of inconsistent apps, surviving aggressive OEM battery managers, and
passing Play review for sensitive permissions. The four visible screens are the easy part. This
bundle is weighted accordingly.
