# Quill — Testing Plan (testing_plan.md)

> September 7, 2026: Remote copy updates (M13) have been retired. The app uses
> bundled text only; the local copy tool edits source for the next signed APK/AAB.
> Earlier OTA milestones below are historical and no longer release requirements.


Much of this app **cannot be fully automated** — accessibility insertion and OEM survival are
verified by hand on real devices. So this plan has three parts: (1) automated test **shells**
written before the code, (2) **manual verification scripts** per milestone, and (3) the **cross-app
injection matrix**, which is where dictation apps actually live or die.

---

## 1. Automated test shells (write these first, as failing/`TODO` stubs)

### Unit (JVM / Robolectric)
- [ ] `TranscriptionClient` parses a successful transcription response (MockWebServer)
- [ ] `TranscriptionClient` maps **401** to a "bad/expired key" error (not a generic failure)
- [ ] `TranscriptionClient` maps **5xx / timeout** to a retryable error
- [ ] `SecretStore` round-trips: encrypt → store → retrieve returns the original key
- [ ] `SecretStore` returns null for a missing alias and never throws into logs
- [ ] `AudioRecorder` writes a valid WAV header (sample rate 16 kHz, mono, 16-bit)
- [ ] `SettingsRepository` persists and reads back each setting
- [ ] `CleanupClient` returns raw text unchanged when cleanup is disabled *(P1)*
- [ ] Vocabulary substitution replaces configured phrases, case-handling defined *(P1.5)*
- [ ] Crash-report **scrubber**: a report contains stack / app version / device only — **never** transcript, audio, clipboard, or API key
- [ ] History **retention**: entries past the time window auto-delete (default 1 h) on insert and on
  opening History; the ~1,000-row backstop cap still prunes oldest when retention is set to Forever
- [ ] **Copy Catalog — resolution:** a key missing from the remote override falls back to the baked-in default (no blank); the resolved map contains *every* referenced key
- [ ] **Copy Catalog — signature:** a bundle with an invalid / absent Ed25519 signature is **rejected**, leaving the current catalog intact
- [ ] **Copy Catalog — rollback guard:** a bundle whose `revision` ≤ the applied revision is ignored
- [ ] **Copy Catalog — locked keys:** an override bundle that *tries* to set a locked key (accessibility disclosure / permission rationale / privacy promise / key warning) does **not** change that string
- [ ] **Copy Catalog — schema/placeholder:** a bundle that drops a required key, exceeds a length cap, or breaks **placeholder-arg parity** (e.g. removes `%1$s`) is rejected **whole** (no partial apply)

### Instrumented (device/emulator)
- [ ] Room `transcripts` DAO: insert / query / delete; **no** rows written when private mode on
- [ ] DataStore persists across process restart
- [ ] Compose UI: onboarding advances only when each permission is granted
- [ ] Compose UI: API-key screen rejects empty input and surfaces validation errors
- [ ] **Copy Catalog — offline cold start:** with **airplane mode on**, a fresh install renders the full UI from baked-in defaults with **no blank/garbled strings**; identical to the with-network first paint
- [ ] **Copy Catalog — OTA apply:** a newer valid signed bundle is fetched on app foreground and by job 1301 approximately every six hours, then applied in the same session. Offline or rejected updates keep existing copy.

> Accessibility insertion and overlay behaviour are **not** reliably unit-testable — cover them in
> §2/§3 on real devices.

---

## 2. Manual verification scripts (per milestone)

Run on at least one **stock** device (Pixel) and one **aggressive-OEM** device — prioritise the
**Honor Magic 6 Pro** if that's your handset, since Honor/Huawei-lineage power management is among
the most aggressive at killing background services and silently revoking accessibility, making it
close to a worst-case device for this app. Record pass/fail + device + OS version.

> **Do not** verify overlay, accessibility, or microphone behaviour on an **emulator** — emulators
> handle overlays and mic capture poorly and produce false failures. These tests require a physical
> device.

**Overlay (M1)**
1. Grant overlay permission. 2. Open another app. 3. Confirm bubble appears, drags, dismisses, and
   survives backgrounding Quill.

**Accessibility detection (M2)**
1. Enable the service via the disclosure → settings flow. 2. Focus a field in WhatsApp → node
   logged. 3. Focus a **password** field → flagged sensitive, dictation disabled.

**Injection (M3) — see the matrix in §3.**

**Recording (M4)**
1. Tap bubble → recording starts, notification shows. 2. Tap again → stops, WAV saved.
3. Revoke mic permission mid-session → **no crash**, bubble stays, re-prompt works.
4. Let it hit the session cap → warning then auto-stop.
5. **Silence auto-stop (v1):** start recording, stay silent → auto-stops after the quiet window;
   then record with natural mid-sentence pauses → does **not** cut off prematurely.

**STT (M5)**
1. Valid key → transcript returned. 2. Wrong key → specific bad-key error. 3. Airplane mode →
   recording saved + Retry offered. 4. **Minified build:** run a `minifyEnabled true` variant and
   confirm the transcription response still parses (catches R8 stripping the JSON models early).

**End-to-end (M6)** — ✅ PASSED on Honor BVL-N49 (2026-06-24)
1. Speak into WhatsApp/Gmail/Chrome → correct text lands at the cursor. 2. Latency for a short
   utterance feels ≤~3 s.
> Result: lands cleanly in WhatsApp / Gmail / Messages, **no placeholder prefix** (after the M3
> replace-whole-field fix), transcript accurate, latency fine on WiFi. v1 🎯 met. Note: v1 replaces
> the field on inject (see todo.md tracked P1 "insert-at-cursor when a field has genuine content").

**Secrets (M7)**
1. Enter key → restart device → key still works. 2. Trigger a backup/restore (or inspect backup
   rules) → confirm the encrypted blob is **excluded**. 3. Grep logs → key never appears.

**Onboarding (M8)**
1. Fresh install (clear data) → complete the whole flow to first dictation with no outside help.

**Hardening (M11)**
1. Force-stop the app → reopen → bubble returns. 2. Reboot device → bubble auto-starts (after
   permissions). 3. Toggle off accessibility in settings → **bring Quill to the foreground** → a
   high-priority notification appears and deep-links to the right screen. *(Detection is an
   on-foreground re-check plus a best-effort periodic check — a revoked service can't observe its own
   revocation, so on aggressive OEMs the alert is expected on next foreground, **not** the instant of
   revocation. PASS = surfaced reliably on next foreground.)* 4. On the OEM device, leave Quill idle
   in background for 30+ min → bubble still works (after battery-exemption granted).

**Crash reporting (M11)**
1. Force an uncaught exception (debug trigger) → relaunch → the send-report dialog appears **once**.
2. Review the report → it shows stack / app version / device only — **no** transcript, audio,
   clipboard, or key.
3. **Send** opens the share sheet / email draft (nothing auto-uploads); **Decline** discards it and
   does not re-prompt.

**Honor Magic 6 Pro / MagicOS (primary device — do this setup once, then test against it constantly)**

*Whitelist Quill on the device (paths vary slightly by MagicOS 8 vs 9 — search the Settings search bar for these terms):*
1. Settings → Apps → **App launch** (or "App startup management") → find Quill → switch from
   **Auto-manage** to **Manual** → enable **Auto-launch**, **Secondary launch**, **Run in background**.
2. Settings → Battery → **App battery management / Protected apps** → set Quill to
   **Unrestricted / Protected**.
3. Confirm "Display over other apps" and the accessibility toggle are still on after a reboot.

*MagicOS-specific survival checks (the ones that break dictation apps on Honor):*
4. With Quill whitelisted, lock the phone for 1+ hour → return → tap a field → bubble still appears
   and dictation still works (the service was **not** killed).
5. Run a memory-heavy app (camera/game) → return to a text field → bubble still functions.
6. After an overnight idle, confirm the **accessibility permission was not silently revoked**. If it
   was, then on **next bringing Quill to the foreground** the revocation notification must fire and
   deep-link to the right screen. *(A revoked service is dead and can't self-detect, and MagicOS
   suppresses background checks — so "fires on next foreground" is the bar, not "fires the moment it's
   revoked." If the notification never surfaces even on foreground, that's the fail.)*
7. Repeat the whole flow **without** the manual whitelist to confirm the in-app battery-exemption
   prompt + helper-library deep link actually get the user there.

---

## 3. Cross-app injection compatibility matrix (the make-or-break test)

For each target: does `ACTION_SET_TEXT` insert at the cursor? Does the **clipboard-paste fallback**
work? Is the result correct? Mark ✅ / ⚠️ (fallback only) / ❌. Target: **≥90% ✅, 100% at least via
fallback.**

| App / surface | SET_TEXT | Paste fallback | Cursor correct | Notes |
|---|---|---|---|---|
| Messages (AOSP/Google) | ✅ | | ✅ | M3 hand-test passed (native) |
| WhatsApp | ✅ | | ✅ | M3 hand-test passed (native) |
| Gmail compose | ✅ | | ✅ | M3 hand-test passed (native) |
| Chrome address bar | 🚫 | 🚫 | — | **Refused by browser-safety rule** (see note) |
| Chrome web text field | 🚫 | 🚫 | — | **Refused by browser-safety rule** — was ⚠️ paste before the rule |
| Google search bar | | | | (in-app native ✅ expected; in-browser 🚫) |
| Notes (Keep) | | | | |
| Slack | | | | |
| A Jetpack Compose `TextField` | | | | Compose node exposure varies — untested |
| Instagram DM | | | | |
| Samsung Messages / Notes | | | | OEM editor |
| A banking app login (native) | ✅❌ refused | — | — | M3 hand-test: **untouched, refused** ✅ (correct) |

> **M3 hand-test (2026-06-24, Honor BVL-N49):** native fields (WhatsApp, Gmail, Messages) inject
> cleanly at the cursor; native banking/password field refused/untouched; clipboard paste-fallback
> confirmed working *before* the browser-safety rule (bubble showed P, pasted on tap, prior clipboard
> restored); bubble gestures (tap-inject, drag-snap, long-press-dismiss) all good.
>
> **Browser-safety rule (added post-matrix, by request):** Chrome reports web `<input type=password>`
> as `isPassword=false` with a plain-text input type — indistinguishable from a normal web text field.
> So in any **browser context** Quill now refuses to inject unless the type *positively* confirms a
> non-secret (email / url / phone / date). This safely catches undetectable web passwords, but it also
> disables dictation into the **URL bar and generic web text fields** (they refuse rather than
> paste-fall-back). Deliberate safety-over-convenience trade-off; covered by `SensitiveFieldClassifierTest`.
> *If too broad, the one-line softening is to return the clipboard fallback (manual paste) for
> unconfirmable browser fields instead of a hard refuse.*

---

## 4. Pre-release (M12) checks
- [ ] Play **pre-launch report** passes (no crashes on the test device farm)
- [ ] Accessibility declaration submitted + demo video uploaded
- [ ] Data-safety form matches actual behaviour (audio → user's own provider; local transcripts;
      nothing collected by us) — **and declares the read-only, no-user-data copy-bundle GET if OTA ships in v1**
- [ ] **Copy host live check:** `…/copy/en.json` + `.sig` serve over HTTPS, the bundle verifies against the **pinned public key**, and the private signing key exists **only** as a CI secret (not in repo/app); privacy policy mentions the copy fetch + automatic foreground and background checks
- [ ] R8/minify build runs the full app without reflection/serialization breakage
- [ ] Crash-free target ≥99.5% across the manual matrix

---

## Exit criteria for v1
1. End-to-end dictation works on stock **and** one aggressive-OEM device.
2. Injection matrix ≥90% ✅, with fallback covering the remainder; banking field untouched.
3. No crash in any of the §2 edge scripts (mic revoke, no network, bad key, force-stop, reboot).
4. Key storage verified encrypted, backup-excluded, log-clean.
5. Play pre-launch report clean and the accessibility declaration submitted.

### Automatic copy refresh (M13 follow-up)
On the physical Honor, open Quill and verify job 1301 appears in `adb shell dumpsys jobscheduler`.
Use `adb shell cmd jobscheduler run -f com.tihor.quill 1301` to exercise a scheduled check without waiting six hours.
Serve a newer signed revision and confirm the UI updates in the same session. Repeat offline and with an invalid signature: existing copy must remain usable.
Reopen repeatedly and confirm only one periodic job remains. Reboot and confirm the persisted schedule returns. After force-stop, reopen manually before expecting jobs to resume.
Six hours is the requested cadence, not an exact alarm: Android/OEM battery management can defer it.

### Independent cleanup provider verification
Test all five provider selections with all three styles using controlled chat responses, verify provider-specific key lookup and endpoint validation, truncated output fallback, selected-model persistence and independence from transcription. Run the existing timeout, malformed response, HTTP failure, instruction-preservation, vocabulary and private-mode regressions.
On the Honor, keep Gemini transcription selected, choose Gemini cleanup, enable cleanup and dictate an unsent test sentence in Gmail (Formal) and WhatsApp (Neutral). Temporarily override WhatsApp to Casual and repeat, then remove the override. Inspect both visible text and cleanup status diagnostics; raw transcription alone is not a styling pass. Other provider live tests require their respective saved keys and are not implied by mock-server coverage.

### Gmail editor connection and multiple keys
On Android 13+, verify Gmail compose-body detection where the accessibility tree reports only a focused WebView. Confirm actual insertion with the editor connection and visible output; repeat with selection replacement, nonempty drafts and cancellation. A changed foreground package must preserve the transcript for manual paste instead of writing into the new app. Verify password input types are blocked and Android 10–12 retain the node/clipboard fallback. This matrix is pending physical testing, not implied by compilation.

Verify multiple same-provider keys can be added, selected, removed and retained across app recreation. Rejecting a new key must not erase a working key. Verify automatic cleanup follows speech provider changes, separate choices remain independent, and disabling cleanup stays disabled.
# API key onboarding follow-up

The provider accordion, clipboard, browser-return, process-recreation and model-label manual checks are listed in [key setup](docs/key-setup.md). Run these on the physical phone before Play submission. Authentication checks do not prove model quota or successful dictation.

### Specialized dictation errors (M11 follow-up)

Added October 19, 2026 for 1.0.23. Automated classification and adapter checks do not establish physical-device results. Run on the physical Honor; no phone was connected during implementation.

1. Deny/revoke Microphone, then tap and long-press the bubble: see “Microphone access is off”, no capture/upload, and a microphone settings action. Grant access and return: a new take works.
2. Turn Android’s global microphone switch off: see a muted-microphone message. Turn it off during recording too; there must be no crash or fake saved-audio retry. End another app’s microphone session and retry any unavailable-mic failure.
3. Disable overlay access: reopening Quill explains overlay access; grant it and restore the bubble. Revoke accessibility: normal dictation explains the missing service, opens the disclosure flow, and explicit clipboard dictation still works.
4. Deny Notifications and Contacts: ordinary dictation must remain usable; those optional permissions must not be misreported as STT failures.
5. Remove the selected speech provider key or select a provider without a key: see the provider-key message and API keys shortcut. Correct it and retry the original audio after returning.
6. Use a controlled provider to return 401, 403, 402, 429, 404/model_not_found, 413, 415, 500, 503, 504, malformed success and empty text: check the distinct message/action against [the error catalog](docs/dictation-errors.md). Never display a response body or key.
7. Start in airplane mode, speak, then restore connectivity: “No internet connection” and a saved-audio retry. Tap retry: the same utterance returns without microphone capture or a new take. Cancel then tap: a new recording starts.
8. Check DNS failure, connection refusal, timeout and TLS failure on a controlled endpoint. A failed upload keeps the take for retry; a missing/empty cache file requests a new take instead.
9. Change target app or disconnect accessibility during transcription: the bubble says text was copied rather than failed transcription; verify actual clipboard content and fallback expiry. Check a protected field remains untouched.
10. Repeat permission/capture/provider errors in custom-word recording and the onboarding microphone test. The test must not advance as successful before audio arrives and must explain a capture failure.
11. With a controlled storage-write failure, verify a recording failure explains storage. A History-write failure must still permit insertion of the transcript.
12. Cancel an in-flight request: no error should appear later, and no cancelled transcript should be inserted. Tap repeatedly while transcribing: only one request should run.
