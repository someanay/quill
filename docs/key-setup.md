# Provider key setup

Onboarding and Settings > Speech & Model share one accordion for Gemini, Groq and OpenRouter. Gemini opens first; a user can open a different provider or collapse all three. Existing OpenAI and custom-provider configurations remain readable, but OpenAI is no longer an option when adding a key.

Each provider includes numbered instructions, a reading acknowledgement before opening its website, a masked field with Paste and reveal controls, and its own Save action. Saving a verified key opens the setup checklist directly during onboarding. The former explanation screen is removed.

Clipboard availability uses metadata. Quill reads clipboard text only when Paste is tapped. Unfinished keys are stored through Android Keystore encryption, separately from active keys, and writes are atomic. Accordion selection and onboarding state survive recreation. Clearing all app data removes key rings and unfinished drafts too.

Local checks reject incomplete, masked and wrong-provider keys. After a short typing debounce, Quill checks authentication using a provider GET endpoint. OpenRouter uses its authenticated `/api/v1/key` endpoint because its model catalog is public. No transcription or cleanup request is made for this check. A green check confirms authentication, not available quota or access to every model. Temporary errors retain the encrypted draft and offer retry.

Model catalogs refresh on entry, app resume and manual refresh. The dropdown and selected-model caption label confirmed free/free-tier models, paid models and previews. Unknown pricing is not presented as free. OpenRouter pricing comes from catalog metadata; Gemini and Groq free-tier labels use known model defaults and published pricing. Free compatible models rank first. Automatic cleanup follows the speech provider only when a known free option exists; an explicit off setting remains off. Manually selecting paid cleanup remains possible.

Pricing references checked September 7, 2026: [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [Groq limits](https://console.groq.com/docs/rate-limits), [OpenRouter free models](https://openrouter.ai/docs/guides/routing/model-variants/free). Provider quotas and account billing still apply. OpenRouter's free cleanup options do not imply that its dedicated speech transcription models are free.

## Verification

Automated coverage checks key prefixes, authenticated endpoint selection, provider errors, encrypted-store draft separation and recreation, free/paid catalog metadata, cache persistence, cleanup defaults and complete key deletion. All interface copy is in the source catalog and exported to the local editor.

Physical device checks remain pending because no phone was connected during implementation:

1. Start onboarding. Verify Gemini opens, the other headers collapse it, and tapping an open header collapses all.
2. Check Paste with empty, image and text clipboards. No clipboard-access toast should appear until Paste is tapped.
3. Enter a partial key, open the provider website, background-kill Quill and return. Verify the accordion, draft and onboarding stage survive. Never include a real key in screenshots or logs.
4. Check wrong-provider, rejected, offline and valid keys. Change the field while a check is running; an older response must not mark the edited key valid.
5. Save a verified key. Confirm the checklist opens directly with the key marked complete.
6. Repeat in Speech & Model and API Keys. Add a second key and confirm the first remains available.
7. Open both model dropdowns. Check price/preview labels, selected-model captions, refresh and offline fallback.
8. Check narrow screens, large text and the visible keyboard. Verify the final Save button remains reachable.
