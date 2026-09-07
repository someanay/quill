# Onboarding document review

Rechecked September 8, 2026 against [Copy changes in the Quill app onboarding flow](https://docs.google.com/document/d/1YRghkmN6Hts_phe1yvnKz_Wm2NWwovxwVJVwhXdFAIk/edit). Both the text and seven embedded screenshots were inspected.

| Document item | Result |
| --- | --- |
| Splash tagline | Applied the pending copy-tool wording, superseding the older document tagline. |
| Welcome title, description, privacy pill and actions | Applied the pending welcome title and body exactly. Privacy pill and actions match the document; regular hyphens remain as requested. |
| BYOK warning, free-tier note, usage note, privacy explanation and reading prompt | Already present. Existing correctly spelled provider names and privacy wording are retained. |
| Key entry screenshots | These show the older chip layout. The later approved accordion, Gemini-first setup, validation and removal of OpenAI from new-key choices remain in place. |
| Remove the three-step explanation and go straight to the checklist | Already implemented after a verified key is saved. |
| Checklist heading, progress, four rows and subtitles | Already present. The key subtitle now names the actual connected provider rather than always saying Groq or using generic saved-key text. |
| Request microphone permission on the checklist and remove the microphone priming screen | Implemented. Allow microphone opens the Android permission prompt directly. Denial retains the checklist with recovery actions. |
| Open the mic test immediately after permission is granted | Implemented. The existing mic-test title and description remain. Notification permission is handled separately after the test. |

This is an M8 onboarding follow-up. No changes to recording, transcription, injection or accessibility disclosure behavior were requested here. New interface text remains bundled in the app and editable through the local source-copy workflow.

Automated tests cover initial permission requests, retryable denial, permanent denial and already-granted permission routing. Physical permission-dialog and microphone tests remain pending because the connected-device list was empty during this review. See [the manual checks](key-setup.md#verification).

All 13 pending editor changes were applied to bundled source revision 12. The editor now has zero pending edits. Its older entries were missing the Android platform label; those entries were migrated before applying. The three-provider accordion remains as explicitly requested in the supplied wireframes.
