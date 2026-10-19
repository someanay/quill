# Dictation errors and recovery

The Android 1.0.23 preview uses 42 distinct error categories. The bubble shows a short cause and the next action, with a longer explanation in its toast and accessibility description. Custom-word recording uses the same speech and recorder error messages. The onboarding microphone test reports capture failures instead of claiming success.

A failed request offers **Saved · Tap to retry** only while its original usable recording is available in the running session. Retry sends that recording again; it does not start the microphone. Errors that need setup open microphone, overlay, accessibility or provider settings. After visiting provider settings, tap the bubble to retry the retained recording. Cancel or explicitly starting another recording discards the previous take. Cache/process cleanup can make a recording unavailable, in which case Quill asks for a new recording.

Notification access and contacts access are optional for dictation and do not block the speech pipeline. Accessibility setup still passes through Quill’s disclosure. Clipboard dictation intentionally works without accessibility. Android/OEM restrictions can prevent a settings screen or overlay from opening; the explanation remains available without claiming that access was granted.

Only locally defined messages and known HTTP/error codes are presented. Provider error text, keys, recordings and transcripts are not included in these error messages or error logs. A generic HTTP 403 is access denied rather than proof of an invalid key; a generic 429 is a limit rather than proof that paid credits are exhausted. A missing reply is not treated as successful dictation. Unrecognized failures have a safe fallback message.

| Bubble message | Explanation |
| --- | --- |
| Microphone access is off | Allow Microphone in Quill’s Android app permissions, then return and tap the bubble. No audio was recorded. |
| Microphone is muted | Unmute the microphone. Check Microphone access in Android’s Quick Settings or Privacy settings, then record again. |
| Bubble permission is off | Allow Quill to display over other apps in Android settings, then return to Quill. |
| Accessibility disconnected | Open Quill’s accessibility setup and enable Quill. If it is already on, turn it off and on again. |
| Android blocked recording | Open Quill and try again with the bubble visible. Check microphone access and your phone’s background-app settings. |
| Microphone unavailable | Quill could not start the microphone. End any call or other recording, then try again. |
| Recording interrupted | The microphone stopped delivering audio. Check microphone access, end other recordings and record again. |
| Could not save audio | Quill could not write the recording to temporary storage. Free some phone storage, reopen Quill and record again. |
| Recording unavailable | The temporary recording is missing, empty or no longer readable. Record your dictation again. |
| No audio captured | The microphone returned no usable sound. Check Android’s microphone switch and your audio input, then record again. |
| Speech key is missing | Add or select a key for your current speech provider in Settings → API keys. Keys for other providers cannot be used here. |
| Saved key could not open | Quill could not decrypt the selected speech key. Add it again in Settings → API keys. |
| Key format is invalid | The selected speech key contains spaces, line breaks or unsupported characters. Paste the complete key again in Settings → API keys. |
| Speech key rejected | The speech provider rejected this key. Check that it is complete, active and belongs to the selected provider in Settings → API keys. |
| Provider access denied | Your provider refused access to this request. Check your key’s permissions, model access and account or regional restrictions. |
| Provider credits exhausted | Your speech provider reports exhausted credits or quota. Check your provider’s usage and billing, or select another configured provider. |
| Provider limit reached | Your speech provider is limiting requests. Wait before retrying. If it continues, check your account’s request and daily limits. |
| Speech model unavailable | Choose an available speech model in Settings → Speech & model. The current model may have been removed or may not be available to your key. |
| Provider address failed | The speech endpoint could not be used. Check your custom HTTPS base URL and selected provider in Settings → Speech & model. |
| Recording is too large | Your speech provider rejected the upload size. Record a shorter dictation. |
| Audio was rejected | Your speech provider could not use this audio format or recording. Record again; for a custom provider, check that it accepts WAV audio. |
| Request was rejected | The speech provider rejected the request settings. Check the speech model, language and custom provider address in Settings → Speech & model. |
| Provider took too long | The speech request timed out. Check your connection and try again. |
| Provider address not found | Your phone could not find the speech provider’s server. Check your internet connection, VPN or private DNS; for a custom provider, check its address. |
| Could not reach provider | Your phone could not connect to the speech provider. Check your connection or VPN and retry. |
| Connection interrupted | The speech request could not finish because of a connection problem. Check your internet connection and retry. |
| Secure connection failed | Quill could not establish a secure connection to the speech provider. Check your phone’s date and time, VPN or network, then retry. |
| Provider server error | Your speech provider had a server error. Wait a moment and try again. |
| Speech provider is busy | Your speech provider is temporarily unavailable. Wait a moment and retry, or choose another configured provider. |
| Unreadable provider reply | The speech provider returned an unexpected response. Retry; if it continues, check that the selected model and endpoint support transcription. |
| No transcript returned | The speech provider returned no words. Speak clearly and record again, or choose another speech model. |
| Provider blocked audio | The speech provider blocked this recording and returned no transcript. Try a different recording or another configured speech provider. |
| Transcript was cut short | The speech provider stopped before finishing. Record a shorter dictation or choose another speech model. |
| Could not read settings | Quill could not load local dictation settings. Reopen Quill and try again. |
| Dictation could not finish | Quill hit an unexpected problem. Try again, or reopen Quill if it continues. |
| Protected text field | Dictation is disabled in password and other protected fields. Select a normal text field to dictate. |
| Select a text field | Tap an editable text field first, or long-press the bubble to dictate to the clipboard. |
| Text copied instead | The target field is unavailable or refused insertion. Your dictation was copied for manual paste. Quill clears this fallback copy after about one minute. |
| Protected field — copied | Quill did not insert into this protected field. Your dictation was copied for manual paste and clears after about one minute. |
| Text copied — access lost | Quill’s accessibility service disconnected before insertion. Paste your copied dictation within about one minute, then re-enable Quill accessibility. |
| Could not copy text | Android did not accept the clipboard copy. Open Quill and check History if history was enabled for this dictation. |
| No internet connection | Your phone has no active network connection. Connect to Wi-Fi or mobile data, then try again. |

If writing History fails, Quill warns separately and still attempts insertion of the completed dictation. Optional cleanup retains its existing fail-soft behavior: raw transcription remains usable when cleanup cannot finish.

The automated tests cover permission guards, microphone mute, caption semantics, retry eligibility, safe error mapping and all speech adapters with controlled HTTP responses. Real-phone permission revocation, settings navigation and microphone/OEM behavior remain pending in the [test plan](../testing_plan.md#specialized-dictation-errors-m11-follow-up).
