# M13b — Copy Authoring Tool: Scoping Doc

> The web tool that produces the signed `strings.signed.json` bundle the Quill app fetches and verifies.
> **Separate repo** (Next.js + Supabase), not part of the Quill Android app. This doc is the plan to
> agree before building. Companion to the device-side pipeline (M13a, already shipped) and the
> canonical signing-input contract in `CopyBundleCanonical`'s KDoc + `docs/copy-bundle-format.md`.

---

## 0. The one rule everything else serves: the trust boundary

There are two sides, and they must never touch:

- **Draft side** (mutable, unsigned, low-trust): the Next.js web app + the Supabase **Postgres database**.
  You log in, edit copy, save drafts. A compromise here must NOT be able to push copy to users.
- **Delivery side** (signed, what the app trusts): the Supabase **Storage bucket** `copy-bundles`,
  holding `strings.signed.json`, written **only by CI**.

**The web app NEVER writes to Storage and NEVER signs.** Publishing = the web app marks a draft
"ready" in the database; **CI** (holding the Ed25519 private key + the Supabase `service_role` key as
GitHub secrets) reads the ready draft, builds the canonical bytes, signs, and uploads to the bucket.

Why this matters: the private key is the only thing that can produce a bundle the app trusts. It lives
in CI and nowhere else — not in the browser, not in the web app, not in Supabase. Even a full breach of
the web app or its database cannot forge a valid bundle (no private key) and cannot write the bucket
(no service-role key in the app). The app's signature check is the backstop; this boundary is the
front-line. **Pass A (web app) is built so it is structurally incapable of signing or publishing.**

---

## 1. What the tool is (Pass A) and is not

**Is:** a single-purpose internal editor. A table of the app's **overridable** copy keys, each showing
the baked-in default and the current override (if any), editable, with validation, draft-saving, and a
"mark ready to publish" action that bumps a revision.

**Is not:** a public app, a CMS, a translation system (v1 is single-locale — the app has one English
catalog), or the signer/publisher (that's CI, Pass B).

---

## 2. The keys it edits (the source of truth)

The app's `CopyKeys.kt` defines ~250 keys. The tool must respect three facts about them, all derivable
from the app's committed artifacts:

1. **Only `OVERRIDABLE` keys are editable.** `COMPLIANCE` keys (the ~11 listed below) are **never**
   shown as editable — ideally not shown at all, or shown read-only and clearly locked. The tool must
   not let you author a compliance key, because the app's `applyOverride` will silently ignore it
   anyway (so editing it is a confusing no-op) and because it's the one category that must never drift.
   Locked keys: `compliance.accessibility.disclosure_{title,body,accept,decline}`,
   `compliance.rationale.{mic,overlay,accessibility}`, `compliance.privacy.promise`,
   `compliance.key.required_{title,body}`, `compliance.privacy.welcome_pill`.
2. **Every key has a baked-in default + a placeholder shape.** The app ships
   `app/src/main/assets/copy/strings.default.json` (key → default string). Some values contain
   `printf`-style placeholders (`%s`, `%1$d`, etc.). The tool MUST preserve placeholder parity — an
   override whose placeholders don't match the default will be **rejected wholesale by the app's
   verifier** (mismatched `%` args crash `String.format`). So validate this in the editor, before it
   ever reaches CI.
3. **The app rejects unknown keys.** An override key not in `CopyKeys.ALL` fails the app's schema check.
   So the tool may only ever emit keys that exist in the app.

**Seeding decision (open — see §6):** the cleanest source of truth for "what keys exist, their defaults,
and their placeholder shapes" is the app's own `strings.default.json` + the `OVERRIDABLE`/`COMPLIANCE`
split. The tool should import that file as the seed rather than maintaining a hand-kept key list that
can drift from the app.

---

## 3. The data model (Supabase Postgres, draft side)

Minimal v1 schema:

- **`copy_keys`** (the catalog seed): `key` (PK, text), `default_value` (text), `placeholders` (text[]
  or text — the `%`-token shape extracted from the default), `overridable` (bool), `group` (text, e.g.
  "settings.privacy" for UI grouping). Populated by importing the app's `strings.default.json`.
- **`copy_overrides`** (the working draft): `key` (FK → copy_keys), `value` (text), `updated_at`,
  `updated_by`. One row per key you've chosen to override. Absence = use the app's baked-in default.
- **`bundle_revisions`** (publish history): `revision` (int, monotonic), `status` (draft/ready/published),
  `created_at`, `published_at`, optionally a frozen snapshot of the overrides at publish time.

The **revision** is the heart of the OTA contract: it must increase monotonically (the app rejects
`revision ≤ current`). The tool owns allocating the next revision when you mark a draft "ready."

Row-Level Security: the web app uses the **`anon` key** + RLS so only your authenticated user can
read/write. CI uses the **`service_role` key** (bypasses RLS) to read the ready draft and write the
bucket. Never the reverse.

---

## 4. The UI (Pass A)

- **Auth:** Supabase Auth, restricted to you (see §6 single-vs-multi). v1 can be a single allow-listed
  email.
- **Key table:** searchable/filterable list of overridable keys, grouped by namespace (`settings.*`,
  `onboarding.*`, `home.*`, …). Each row: key, default value (read-only), override value (editable),
  a "modified" indicator, and the placeholder shape shown as a hint.
- **Editing:** inline or side-panel edit of the override value, with **live placeholder-parity
  validation** (block save if the override's `%`-tokens don't match the default's). Show a diff vs.
  default.
- **Compliance keys:** shown read-only + visibly locked, or hidden behind a "locked copy" filter —
  never editable.
- **Publish flow:** a "Mark ready to publish" action that validates the whole draft (every override
  passes parity + is an existing overridable key), allocates the next revision, and sets status=ready.
  **This does not sign or upload** — it sets the flag CI watches. A clear status line: "Revision N
  published / Revision N+1 ready, awaiting CI / draft dirty."
- **Preview (nice-to-have):** show the resulting bundle JSON read-only so you can eyeball it before
  publish.

---

## 5. How publish reaches CI (the Pass A ↔ Pass B seam)

Two viable triggers (decision in §6):

- **(a) Repo-commit trigger:** "Mark ready" writes the ready bundle's source (the overrides + revision)
  to a file committed to the authoring repo; a GitHub Action fires on that commit, signs, uploads.
  Pro: signing input is in git history (auditable); fully GitHub-native. Con: the web app needs to
  commit to the repo (a GitHub token), or you commit manually.
- **(b) DB-flag trigger:** "Mark ready" just sets `status=ready` in Postgres; CI runs on a schedule or
  a manual `workflow_dispatch`, reads the ready draft via the `service_role` key, signs, uploads.
  Pro: web app needs no GitHub write access — cleaner separation. Con: not auto-instant (cron/manual),
  and the signing input lives in the DB not git (less audit trail).

Either way, **CI is the only signer/uploader.** Recommended: **(b)** with a manual `workflow_dispatch`
to start (you click "run" in GitHub Actions after marking ready) — simplest, safest, no GitHub token in
the web app, and you stay in control of when a publish actually happens.

---

## 6. Decisions to make before building Pass A

1. **Seed source:** import the app's `strings.default.json` as the `copy_keys` seed (recommended), or
   maintain the key list separately? (Recommended: import — single source of truth, no drift.)
2. **Single-user vs multi-user:** just you (single allow-listed email, simplest), or will Pia / others
   edit (needs real multi-user auth + maybe roles)? Affects auth + whether it's truly local-only or
   deployed.
3. **Local-only vs deployed:** run the web app only on `localhost` (you run `npm run dev` when editing),
   or deploy it (Vercel) so it's reachable anywhere? Local-only is the smallest attack surface; deployed
   is convenient if multi-user. (The Supabase DB is cloud either way; this is just about the web UI's
   host.)
4. **Publish trigger:** (a) repo-commit or (b) DB-flag + manual `workflow_dispatch` (recommended b).
5. **Revision source of truth:** DB `bundle_revisions` table (recommended) — the tool allocates; CI
   reads it. Confirm the app's currently-shipped baked-in revision so the first OTA bundle is `> that`.
   (App is at revision 2 per M13a's bump.)

---

## 7. Build order within M13b

1. **Infra (yours, partly done):** ✅ Supabase project + `copy-bundles` public bucket. ⬜ GitHub repo +
   secrets (Ed25519 private key, Supabase `service_role` key) — these go in when Pass B's CI is built.
2. **Pass A — the Next.js + Supabase authoring app** (this doc). Build, run locally, confirm you can
   edit + mark-ready + see the would-be bundle. No signing yet.
3. **Pass B — the CI signing + upload pipeline:** GitHub Action that reads the ready draft, builds the
   **canonical bytes per `CopyBundleCanonical` / `docs/copy-bundle-format.md`**, signs with the private
   key, uploads `strings.signed.json` to the bucket. **First test = round-trip:** sign a bundle, fetch
   it, confirm the app's `Ed25519Verifier` accepts it. Also satisfies the M13a follow-up: emit an
   RFC-8032 reference vector the app's verifier test consumes.
4. **Wire the app's `BASE_URL`** to
   `https://tznxppfwlgdhbvsplewl.supabase.co/storage/v1/object/public/copy-bundles/strings.signed.json`
   — only once Pass B is publishing a real file there (until then the app fetches 404 → silent
   baked-in fallback).

---

## 8. The highest-risk integration point (read this twice)

**Canonicalization must match byte-for-byte.** The app verifies signatures over bytes produced by
`CopyBundleCanonical` (compact JSON, keys sorted by UTF-8 byte order, specific escaping, no trailing
newline — full spec in its KDoc + `docs/copy-bundle-format.md`, which ships a reference Python signer).
Pass B's CI signer MUST reproduce those exact bytes. One difference — key sort, an escape, a trailing
newline — and **every bundle fails verification in production, silently** (app keeps using baked-in
copy; no error surfaced). So Pass B's first and most important test is a **round-trip against the app's
real verifier**, not "did it sign something." Use/port the committed reference signer; do not
re-derive the canonical form from scratch.
