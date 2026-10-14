# Provider key setup

Onboarding shows only Groq's numbered instructions and key-entry form, always expanded with no accordion or provider picker. A small "Add other providers later in Settings" button opens a reminder covering Gemini, OpenRouter and other providers. Its top-left Back arrow returns to the form, preserving the draft. Its bottom primary "Skip for now" button bypasses key entry and opens the setup checklist. Without a saved key for the selected provider, the checklist keeps the key pending and excludes it from its completion count. Settings retains the accordion for Gemini, Groq and OpenRouter; Gemini opens first unless a previous selection was saved. Existing OpenAI and custom-provider configurations remain readable, but OpenAI is no longer an option when adding a key.

Each provider includes numbered instructions, a website link in the first step, a masked field with Paste and reveal controls, and its own Save action. Saving a verified key opens the setup checklist directly during onboarding. The former explanation screen is removed.

The checklist identifies the connected provider. Its heading places "Next: microphone" on a new line after "Your key works." Its Allow microphone button requests Android permission directly, with no intervening introduction screen. Granting permission opens the mic test. Denial stays on the checklist with retry or app-settings recovery. Sounds good advances to overlay setup without requesting notification permission. Notification settings remain available in Settings. The overlay checklist row stays pending because its setup screen is still ahead, even if Android retained the permission.

The microphone-test meter suppresses input below 1,000 RMS and reaches full height at 10,000 RMS,
giving quiet background noise less influence than the former 0–7,000 scale. Its existing short
interpolation keeps the bars readable; with reduced motion enabled, bars still track actual input
but update without interpolation. These thresholds affect the displayed meter only.

The overlay permission screen shows five numbered, left-aligned steps above its button: open
Settings, scroll to Quill, tap Quill, enable display over other apps, and return to Quill. Wrapped
instruction text stays aligned with the text of its step, and the screen remains scrollable.

Clipboard availability uses metadata. Quill reads clipboard text only when Paste is tapped. Unfinished keys are stored through Android Keystore encryption, separately from active keys, and writes are atomic. Settings accordion selection and onboarding state survive recreation. Onboarding always shows Groq regardless of the saved Settings accordion selection. Clearing all app data removes key rings and unfinished drafts too.

Local checks reject incomplete, masked and wrong-provider keys. After a short typing debounce, Quill checks authentication using a provider GET endpoint. OpenRouter uses its authenticated `/api/v1/key` endpoint because its model catalog is public. No transcription or cleanup request is made for this check. A green check confirms authentication, not available quota or access to every model. Temporary errors retain the encrypted draft and offer retry.

Model catalogs refresh on entry, app resume and manual refresh. The dropdown and selected-model caption label confirmed free/free-tier models, paid models and previews. Unknown pricing is not presented as free. OpenRouter pricing comes from catalog metadata; Gemini and Groq free-tier labels use known model defaults and published pricing. Free compatible models rank first. Automatic cleanup follows the speech provider only when a known free option exists; an explicit off setting remains off. Manually selecting paid cleanup remains possible.

Pricing references checked September 7, 2026: [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [Groq limits](https://console.groq.com/docs/rate-limits), [OpenRouter free models](https://openrouter.ai/docs/guides/routing/model-variants/free). Provider quotas and account billing still apply. OpenRouter's free cleanup options do not imply that its dedicated speech transcription models are free.

## Verification

Automated coverage checks key prefixes, authenticated endpoint selection, provider errors, encrypted-store draft separation and recreation, free/paid catalog metadata, cache persistence, cleanup defaults and complete key deletion. All interface copy is in the source catalog and exported to the local editor.

Physical device checks remain pending because no phone was connected during implementation:

1. Start onboarding. Verify only Groq instructions and the key form appear, with no provider headers or collapse action. Enter a partial draft, open the small other-providers button, and use its top-left Back arrow; confirm the form and draft remain. Repeat with system Back and outside dismissal. Reopen and choose Skip for now: the checklist opens, the unsaved key stays pending, and the progress count excludes it. Checklist Back must return to the form with the draft intact; a saved selected-provider key must still count as connected. Verify the reminder mentions Gemini, OpenRouter and other providers. In Settings, verify Gemini and OpenRouter are still available and provider headers open/collapse their guides.
2. Check Paste with empty, image and text clipboards. No clipboard-access toast should appear until Paste is tapped.
3. Enter a partial Groq key, open its website, background-kill Quill and return. Verify the draft and onboarding stage survive and Groq remains visible. Repeat in Settings with Gemini/OpenRouter to check accordion selection and drafts. Never include a real key in screenshots or logs.
4. Check wrong-provider, rejected, offline and valid keys. Change the field while a check is running; an older response must not mark the edited key valid.
5. Save a verified key. Confirm the checklist opens directly with the key marked complete.
6. Repeat in Speech & Model and API Keys. Add a second key and confirm the first remains available.
7. Open both model dropdowns. Check price/preview labels, selected-model captions, refresh and offline fallback.
8. Check narrow screens, large text and the visible keyboard. Verify the final Save button remains reachable, and "Next: microphone" always starts on a new line in the checklist heading while its progress count remains readable.
9. On the checklist, tap Allow microphone. Verify the system prompt opens on that screen and granting it opens the mic test immediately. Deny once and retry, deny permanently and recover through app settings, then repeat with permission already granted. Back from the mic test must return to the checklist. Notification denial must not block setup.
10. On entering the mic test, compare a brief quiet pause with normal speech at typical phone distance:
    quiet input should leave resting dots, speaking should clearly raise the bars, and stopping should
    let them settle. Repeat with ordinary room/fan noise and on another physical phone. Disable system
    animations and repeat: silence must not show a fixed active meter, and speech must still change it.
11. On the display-over-other-apps page, verify all five numbered steps appear above the button and
    align left. Check narrow screens and large text: wrapped lines must align with their instruction,
    and the permission button must remain reachable. Follow the steps, return to Quill, and confirm
    onboarding advances after the permission is enabled.
