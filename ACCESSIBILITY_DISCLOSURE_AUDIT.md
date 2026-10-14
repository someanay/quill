# Accessibility disclosure audit

September 7, 2026. Quill Android 1.0.0. Pre-fix findings recorded before implementation changes.

## Entry points and pre-fix results

The Android settings intent is constructed at `AccessibilityUtil.kt:33–36`.
All paths below are under `app/src/main/java/com/tihor/quill/`.

| Entry point | 1 Screen | 2 Normal use | 3 Before Settings | 4 Content | 5 Action | 6 Standalone | 7 Resources only |
|---|---|---|---|---|---|---|---|
| `ui/OnboardingActivity.kt:231–236` | PASS: dedicated step at 709 | PASS: follows overlay | FAIL: repeat opens from ACC_ENABLE at 236 bypass consent | FAIL: old wording at 719 | PASS: explicit buttons at 711/726 | PASS: accessibility-only step | FAIL: copy lookups at 711–726 |
| `ui/screens/SystemStateScreens.kt:83` recovery (Home/settings/revocation) | FAIL: no disclosure on route | FAIL: direct Settings | FAIL: direct intent | FAIL: missing disclosure | FAIL: no Agree/Not now gate | FAIL: no standalone disclosure | FAIL: disclosure absent |
| `overlay/OverlayService.kt:1152` failed-insertion/re-enable notification | FAIL: no disclosure on route | FAIL: direct Settings | FAIL: direct pending intent | FAIL: missing disclosure | FAIL: no Agree/Not now gate | FAIL: no standalone disclosure | FAIL: disclosure absent |
| `accessibility/AccessibilityDisclosureActivity.kt:41` | PASS: full-screen Activity | FAIL: other paths bypass it | PASS: callback at 40 | FAIL: old resources | PASS: accept/decline at 40–44, default Back finishes | PASS: only accessibility | FAIL: resources used here, but duplicate keys exist in bundle |

Home/settings and foreground-revocation recovery converge on the recovery screen.
Boot recovery does not directly launch accessibility settings. The bubble's failed
insertion notification is the distinct direct-settings recovery route.
No persisted acceptance flag gates the standalone Activity; the bypasses above are
navigation paths. `accessibilityEnabledOnce` is used for recovery detection, not consent.

## Manifest

`app/src/main/res/xml/accessibility_service_config.xml:5–12`:

- PASS: `isAccessibilityTool` absent (non-tool track).
- FAIL: description is static, but does not match the requested one-line summary.
- PASS: `canRetrieveWindowContent` supports editable-node lookup and insertion.
- PASS: all five declared event types are handled in `QuillAccessibilityService.kt:55–66`.
- PASS: interactive windows flag supports `windows` traversal; input-method-editor
  flag supports Android 13+ editor-connection insertion. `flagDefault` is the zero
  default, not an additional capability. No unused capability/event found.
- FeedbackGeneric and 100 ms notification timeout do not grant extra data access.

## Content conflict and out-of-scope observations

The supplied wording cannot be used verbatim without misdescribing existing behavior:

- `accessibility/AccessibilityTextInjector.kt:66–72` reads `target.text` and selection
  offsets to compose an insertion preserving existing content/cursor position.
- The same file at 115 reads `focus.text` to confirm clipboard paste.
- `accessibility/QuillAccessibilityService.kt:111–112` reads `getSurroundingText`
  to confirm editor-connection insertion.
- `QuillAccessibilityService.kt:78–88` logs package/class, password/input-type and
  sensitive-field metadata; `AccessibilityTextInjector.kt:63–66` logs length and
  selection offsets. These are accessibility-derived data even without field text.
- Focused nodes are held transiently in memory. Existing field text is not deliberately
  saved as dictation history or sent to a provider. Dictated text has separate history
  and provider handling; that is not a promise that every kind of app data stays local.

Accordingly, “does not read other apps' content” and “nothing obtained through the
service is logged” are not accurate blanket statements. Corrected disclosure should
describe focused-field reads and local metadata diagnostics while preserving runtime
behavior, unless the user authorizes a wider change. Literal check 4 remains an explicit
exception rather than a false PASS. No Accessibility Shortcut warning was found in the
existing disclosure or other app UI.

## Policy references

- [Google Play accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Prominent disclosure and consent guidance](https://support.google.com/googleplay/android-developer/answer/11150561)

These support a dedicated disclosure and affirmative consent; this audit is not a
Google Play approval decision.

## Verification limits

The physical phone was not connected when checked on September 7. Fresh-install and
revocation UI runs are pending; no data was cleared or device settings modified.

## Changes and post-fix results

The user approved accurate wording with runtime behavior preserved. Check 4 below
is evaluated against that approved correction; the original blanket no-read/no-log
wording is intentionally not shipped.

| Entry point (post-fix) | 1 | 2 | 3 | 4* | 5 | 6 | 7 | Evidence |
|---|---|---|---|---|---|---|---|---|
| Onboarding | PASS | PASS | PASS | PASS* | PASS | PASS | PASS | `ui/OnboardingActivity.kt:230` launches the dedicated Activity on every request |
| Home/recovery action | PASS | PASS | PASS | PASS* | PASS | PASS | PASS | `ui/screens/SystemStateScreens.kt:83` uses disclosureIntent |
| Failed-insertion/re-enable notification | PASS | PASS | PASS | PASS* | PASS | PASS | PASS | `overlay/OverlayService.kt:1152` uses disclosureIntent |
| Shared disclosure | PASS | PASS | PASS | PASS* | PASS | PASS | PASS | `accessibility/AccessibilityDisclosureActivity.kt:41–45,66–78` |

*Approved accurate wording replaces the original check 4's contradictory assertions.

- Removed the old copy-based onboarding disclosure. Its enable action now opens
  the shared full-screen Activity. Returning without enabling allows another request,
  which presents the disclosure again. There is no stored consent bypass.
- `AccessibilityUtil.kt:33` now creates only the disclosure intent. The sole system
  accessibility-settings launch is inside the Activity's Agree callback at line 42.
- Not now finishes the Activity; ordinary system Back finishes it without opening
  Settings. It is a full-screen Activity, with no outside-tap dismissal or timer.
- All four visible strings use Android resources (`res/values/strings.xml:11–14`).
  Removed their former keys from CopyKeys, bundled JSON, and generated copy manifest.
  The manifest now exports 394 keys, revision 7. No OTA publication was performed.
- Service description and disclosure heading share the same static string at
  `res/values/strings.xml:9–11`. Accessibility Shortcut warning is now in the body.
- No service capabilities, event flags, focus/injection algorithms, or logging
  behavior were changed in this disclosure patch. Existing uncommitted runtime fixes
  from earlier requested work are separate from this audit's change scope.
- `AccessibilityDisclosureTest.kt` covers resource isolation, malicious override
  attempts, disclosure routing, the single Settings-launch location, and labels.
  Existing compliance-key tests now exercise the remaining accessibility rationale.

Manifest checks now pass with the corrected static summary; flags remain unchanged.
415 unit tests, debug lint, and signed release APK/AAB builds passed. Physical manual
acceptance remains pending because no phone is connected; source checks are not
claimed as fresh-install or revocation UI test results.

### Physical verification script

On a test installation (do not clear the daily-driver app's data): reach onboarding's
accessibility step, open the disclosure, choose Not now, and confirm Settings stays
closed. Repeat with Back. Open again, scroll through all copy, choose Agree, and
confirm Android Accessibility Settings opens. Enable Quill and return. Revoke only
Quill's service, use Home recovery and the failed-insertion notification in turn,
and confirm each shows the full disclosure before Settings. Repeat at large font size.

## October 4 follow-up: post-consent video guidance

The shared host now plays the supplied accessibility clip in native PiP after Agree,
then opens Settings once Android confirms entry. Unsupported/disabled PiP and video
failure fall back to normal Settings. The single Settings-launch location remains
`AccessibilityDisclosureActivity`; its session coordinator now gates asynchronous
callbacks instead of opening Settings inline in Agree. A post-consent timeout cannot
open Settings before acceptance or after the session ends.

The disclosure still has no timer, stored acceptance bypass or video before consent.
Its body, Agree/Not now labels and service summary remain XML resources. Guide labels
also use XML resources so the standalone Activity has no copy-catalog dependency.
Process recreation ends the guide; a new setup request requires the full disclosure.
Eight `AccessibilityGuideFlowTest` cases cover these sequencing and cancellation
rules alongside the existing disclosure tests. The added manifest PiP declaration
and handled configuration changes do not change accessibility service capabilities.

Physical PiP and disclosure verification remains pending because no phone is connected.
The extended manual script is in [accessibility-video-guide.md](docs/accessibility-video-guide.md).
