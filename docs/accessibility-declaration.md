# Quill — Accessibility Service Declaration & Demo (Play Console)

**DRAFT for review.** For the Play Console "Accessibility" / Permissions declaration and the required
demo video. This is the **#1 rejection risk** for Quill — get it exact.

## Posture: NOT an accessibility tool

Quill's `AccessibilityService` is declared **`isAccessibilityTool = false`** (it's a productivity
dictation tool, not an aid for users with disabilities). _Config: `app/src/main/res/xml/accessibility_service_config.xml`._
That means Play requires a **prominent in-app disclosure + affirmative consent** (already implemented:
`accessibility/AccessibilityDisclosureActivity.kt`) **and** a Console declaration + demo video
justifying the non-assistive use.

## Exactly what the service does (truthful, matches code)

Declared config: `accessibilityEventTypes="typeViewFocused|typeWindowStateChanged"`,
`canRetrieveWindowContent="true"`, `accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"`.

1. **Detects the focused editable field** when you start dictation, so text goes to the right place.
   _Code: `accessibility/QuillAccessibilityService.kt`._
2. **Inserts the transcribed text** into that field via `ACTION_SET_TEXT` (insert-at-caret), with a
   clipboard + `ACTION_PASTE` fallback when set-text isn't possible.
   _Code: `accessibility/AccessibilityTextInjector.kt`, `accessibility/InsertionComposer.kt`._
3. **Refuses sensitive fields:** it will not read or type into password/banking fields.
   _Code: `accessibility/SensitiveFieldClassifier.kt`._

What it does **not** do: it does not log keystrokes, scrape screen content for analysis, read content
from apps other than the focused field you're dictating into, or send anything it observes to any
server. There is no Quill backend.

## Why injection *requires* the accessibility service

Android provides **no other supported API** for a third-party app to insert text into an arbitrary
other app's focused field system-wide. An IME (keyboard) can only type when it is the selected
keyboard and the user switches to it; Quill's value is hands-on dictation into **any** app without
swapping keyboards. `ACTION_SET_TEXT`/`ACTION_PASTE` via the accessibility node tree is the only
mechanism. The service is used **only at the moment of insertion**, gated behind explicit consent.

## Draft Console justification text (paste-ready)

> **How your app uses the AccessibilityService API:**
> Quill is a voice-dictation productivity app. With the user's explicit, separately-obtained consent,
> the AccessibilityService is used solely to (1) identify the currently focused editable text field and
> (2) insert the user's transcribed speech into that field using ACTION_SET_TEXT (with a clipboard/
> ACTION_PASTE fallback). This is the only available Android mechanism to insert dictated text into a
> third-party app's focused field system-wide without forcing the user to switch keyboards. The service
> does not collect, log, or transmit screen content; it explicitly refuses password and banking fields;
> and it transmits nothing to any server operated by us (the app has no backend). Users see a prominent
> in-app disclosure describing this use and must affirmatively consent before enabling the service, and
> can disable it at any time in system settings.
>
> **Is it an accessibility tool for users with disabilities?** No — it is declared isAccessibilityTool=
> false and uses the prominent-disclosure + consent route.

## Demo video — shot list / script (what reviewers must see)

Keep it ~60–90s, on a real device, no cuts that hide steps.

1. **Disclosure first.** Launch Quill → show the prominent disclosure screen (full text visible) →
   user taps the affirmative consent button. _(Proves disclosure precedes enabling.)_
2. **Enable the service** through the system Accessibility settings (show the toggle being turned on).
3. **Core use:** open a normal app (e.g. Messages/Notes) → tap a text field → tap Quill's bubble →
   speak a sentence → show the transcript **inserted at the cursor** in that field.
4. **Sensitive-field refusal:** focus a password field → show Quill dimming/refusing to dictate.
   _(Proves the privacy guardrail.)_
5. **Failure-safety (optional):** show the "couldn't insert → copied to clipboard + notification" path.
6. **Disable:** show the service can be turned off in settings.
7. **Narration/caption** stating: "Quill uses the accessibility service only to insert dictated text
   into the focused field; it does not read password fields and sends nothing to our servers."

## Checklist for this declaration
- [ ] In-app prominent disclosure present & shown before enable — **DONE in code** (`AccessibilityDisclosureActivity`).
- [ ] Console accessibility justification text — **draft above; paste into Console (YOURS).**
- [ ] Demo video filmed per shot list — **YOURS (on-device).**
- [ ] Privacy policy URL live and reflects accessibility use — see `docs/privacy-policy.md` — **YOURS.**
- [ ] Confirm `isAccessibilityTool=false` is still correct for your positioning — **decision (YOURS).**
