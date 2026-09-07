"""Synchronize bundled Android copy/fonts and keep iOS-only wording locally editable.

Run after Android copy changes. This is a source-authoring step; the installed app never
downloads copy. Existing iOS values are preserved; --refresh-defaults resets iOS defaults.
"""
import json
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
IOS = ROOT / "ios"
DEFAULTS = {
    "ios.onboarding.tagline": "Speak, instead of typing. Your voice, your provider, your words.",
    "ios.onboarding.welcome_body": "Record in Quill, then send your words to the Quill keyboard or copy them into your app.",
    "ios.onboarding.example": "A thought becomes a sentence.",
    "ios.onboarding.warning_title": "Bring your own key",
    "ios.onboarding.record_title": "Your recording bubble, in Quill",
    "ios.onboarding.record_body": "The same simple tap-to-record flow. Start in Quill, speak, and tap again to turn your voice into text.",
    "ios.onboarding.overlay_limit": "iOS does not allow a floating bubble over other apps. Recording starts in Quill; the keyboard inserts the finished text.",
    "ios.onboarding.ready_body": "Tap the microphone on Home to record. When your words are ready, send them to the Quill keyboard or copy them into your app.",
    "ios.workflow.step1": "1. Open Quill. Tap the microphone, speak, then tap to stop.",
    "ios.workflow.step2": "2. Tap Send to keyboard when the transcript is ready.",
    "ios.workflow.step3": "3. Return to your app, switch to the Quill keyboard, and tap Insert dictation within 60 seconds.",
    "ios.keyboard.title": "Quill keyboard",
    "ios.keyboard.checklist": "Insert into supported apps",
    "ios.keyboard.setup_title": "Set up the Quill keyboard",
    "ios.keyboard.setup_steps": "Open Settings → General → Keyboard → Keyboards → Add New Keyboard, then choose Quill. Tap Quill and enable Allow Full Access. Return here and switch keyboards with the globe key to try it.",
    "ios.keyboard.full_access_title": "Why Full Access?",
    "ios.keyboard.full_access_body": "Full Access lets the keyboard read and remove the transcript you explicitly send from Quill. The keyboard does not record audio, make network requests, read your API keys, or save what you type. Keys, audio and history stay in the Quill app.",
    "ios.keyboard.test_field": "Try the Quill keyboard here",
    "ios.keyboard.setup_done": "I've set up the keyboard",
    "ios.keyboard.home_help": "How to insert your dictation",
    "ios.keyboard.how_title": "From voice to text",
    "ios.keyboard.sensitive_limit": "iOS switches to the system keyboard for password and phone-number fields. Apps can also disable custom keyboards. Quill cannot insert in those fields. Use Copy only in a field you choose.",
    "ios.keyboard.next": "Next keyboard",
    "ios.keyboard.insert": "Insert dictation",
    "ios.keyboard.shift": "Shift",
    "ios.keyboard.delete": "Delete character",
    "ios.keyboard.space": "Space",
    "ios.keyboard.return": "Return",
    "ios.keyboard.selection": "Tap to place the cursor without a selection before inserting. Your existing text stays intact.",
    "ios.keyboard.field_unavailable": "Quill can't insert into this field. Choose an ordinary text field in an app that allows custom keyboards.",
    "ios.keyboard.full_access_needed": "Type normally here. To insert a dictation, allow Full Access for Quill in Settings → General → Keyboard → Keyboards.",
    "ios.keyboard.record_help": "Record in the Quill app, tap Send to keyboard, return here and tap Insert dictation.",
    "ios.keyboard.transfer_error": "Couldn't read that dictation. Return to Quill and send it to the keyboard again.",
    "ios.settings.open": "Open Settings",
    "ios.mic.rationale": "Quill uses the microphone only when you start a recording or microphone test. Dictation audio goes directly to your selected provider using your own key.",
    "ios.mic.blocked": "Microphone access is off. Open Settings, choose Quill, and enable Microphone. Return here to record.",
    "ios.mic.test_private": "This microphone test stays on your iPhone. It is deleted when the test stops and is never uploaded.",
    "ios.keys.label": "Key name (optional)",
    "ios.keys.security": "Keys are stored in this iPhone's Keychain and excluded from backups and iCloud sync. A key is sent only to its selected provider over HTTPS. Keys never appear in logs or reach the keyboard.",
    "ios.keys.endpoint_binding": "Custom keys are bound to the exact HTTPS base URL used when you verify them. After changing the URL, add and verify a key for that endpoint. Speech and custom cleanup share this endpoint.",
    "ios.keys.remove_confirm": "Remove this saved key?",
    "ios.models.empty": "No matching models were returned. The bundled models and manual model ID remain available.",
    "ios.models.loading": "Refreshing models…",
    "ios.models.refresh": "Refresh provider models",
    "ios.home.title": "Ready when you are",
    "ios.home.subtitle": "Tap, speak, and let your words flow.",
    "ios.home.recent": "Recent dictations",
    "ios.home.no_history": "No dictations yet. Try a recording from Home.",
    "ios.home.dictations_count": "%1$d dictations",
    "ios.home.words_count": "%1$d words in today's saved history",
    "ios.home.no_last_used": "No saved dictations yet. Tap the microphone to begin.",
    "ios.home.tip_title": "Bring your words into another app.",
    "ios.home.tip_body": "Send your transcript to the Quill keyboard, then return to your app and tap Insert dictation. Tap here for setup help.",
    "ios.result.title": "Your dictation",
    "ios.result.send_keyboard": "Send to keyboard",
    "ios.result.copy": "Copy text",
    "ios.result.dismiss": "Dismiss transcript",
    "ios.result.copied": "Copied. Paste into the field you choose. This copy stays until you replace or clear it.",
    "ios.result.sent": "Ready in the Quill keyboard for 60 seconds. Return to your app, switch to Quill, and tap Insert dictation.",
    "ios.transfer.clear": "Clear pending keyboard text",
    "ios.transfer.cleared": "Pending keyboard text cleared.",
    "ios.recording.start": "Start dictation",
    "ios.recording.stop": "Stop and transcribe",
    "ios.recording.start_test": "Start microphone test",
    "ios.recording.stop_test": "Stop microphone test",
    "ios.recording.cap_warning": "Recording will stop in less than 15 seconds.",
    "ios.recording.retry": "Retry dictation",
    "ios.recording.retry_retention": "Your recording stays on this iPhone until Retry succeeds or you tap Cancel, including in private mode.",
    "ios.phase.idle": "Tap to dictate",
    "ios.phase.recording": "Listening",
    "ios.phase.transcribing": "Transcribing…",
    "ios.phase.cleaning": "Tidying your words…",
    "ios.phase.ready": "Your words are ready",
    "ios.phase.failed": "Let's get your words back",
    "ios.history.private": "Private mode is on. New transcripts aren't saved. Previous history remains until it expires or you clear it.",
    "ios.history.clear": "Clear all history",
    "ios.history.clear_confirm": "Delete every saved dictation? This cannot be undone.",
    "ios.history.delete": "Delete dictation",
    "ios.cleanup.inactive": "Automatic cleanup is currently off for this provider. Your transcript and custom words still work. Choose a cleanup provider and model to enable it manually.",
    "ios.cleanup.example_before": "um can we meet tomorrow at five",
    "ios.cleanup.example_after": "Can we meet tomorrow at five?",
    "ios.style.body": "Choose how to punctuate and capitalize your dictation. The selected style applies through AI cleanup.",
    "ios.style.app_limit": "iOS does not expose the destination app to Quill. Choose a style here before recording; automatic per-app styles aren't available.",
    "ios.contacts.denied": "Couldn't import contacts. Allow Contacts for Quill in Settings and try again. Your previous import is kept.",
    "ios.audio.trigger_help": "Tap the recording bubble in Quill to start. Tap it again to stop and transcribe.",
    "ios.audio.cap": "Stops after %1$d seconds",
    "ios.audio.silence_detail": "A quiet pause of 3.5 seconds stops recording after a 2-second startup grace. The microphone meter uses the same measured audio level.",
    "ios.audio.format": "WAV · 16 kHz · mono · 16-bit PCM",
    "ios.audio.route": "Quill uses the iPhone's built-in microphone. Calls, unplugging an input, or permission changes interrupt recording and show a recovery action.",
    "ios.size.small": "Small", "ios.size.medium": "Medium", "ios.size.large": "Large",
    "ios.theme.system": "Match system", "ios.theme.light": "Light", "ios.theme.dark": "Dark",
    "ios.visual.help": "Size changes the recording bubble inside Quill. Theme follows this preference; larger text and Reduce Motion follow your iPhone's accessibility settings.",
    "ios.privacy.direct": "Audio goes directly to your speech provider. Optional cleanup sends the transcript to your selected cleanup provider. No Quill account or Quill server handles either. Provider terms and usage charges apply.",
    "ios.privacy.private_detail": "Private mode skips new transcript history. It does not erase earlier history. A failed recording is kept locally until Retry succeeds or you discard it. Send to keyboard and Copy are explicit actions you control.",
    "ios.permission.allowed": "Allowed", "ios.permission.off": "Off",
    "ios.diagnostics.opt_in": "Allow reviewed diagnostic reports",
    "ios.diagnostics.body": "Nothing is sent automatically. When enabled, Quill stores only numeric crash, hang and exception counts from iOS. You can review and share those counts with the app version, iOS version, microphone permission and recording state. No keys, recordings, transcripts, contacts, clipboard, provider replies, stack traces or endpoint addresses are included.",
    "ios.diagnostics.review": "Review diagnostic report",
    "ios.diagnostics.share": "Share this report",
    "ios.diagnostics.enable": "Enable reviewed diagnostic reports in Privacy to share this report.",
    "ios.advanced.timeout_detail": "Transcription: up to 90 seconds. Cleanup: 4 seconds, then your original transcript is used. Key checks and model refresh: 15 seconds.",
    "ios.advanced.background": "A recording started visibly in Quill can continue in the background. iOS controls background execution and may interrupt processing; return to Quill to Retry. There is no always-on microphone, background restart or floating overlay permission.",
    "ios.about.version": "Version %1$s · iOS",
    "ios.about.provider_terms": "Quill is free to use. Your speech and cleanup providers may charge for API usage. Choose models and providers according to your own accounts and terms.",
    "ios.about.privacy": "Privacy details",
    "ios.about.website": "Quill website",
    "ios.about.licenses": "Font licenses",
    "ios.notice.title": "Quill",
    "ios.error.endpoint": "Enter a valid HTTPS base URL without a username, password, query or fragment.",
    "ios.error.no_key": "Add and verify a key for your selected speech provider in Settings → API keys.",
    "ios.error.no_speech": "No speech was returned. Retry or record again closer to the microphone.",
    "ios.error.rate_limit": "The provider is rate-limiting requests. Wait a moment, then tap Retry.",
    "ios.error.provider": "The provider couldn't complete this request. Check its service status and model, then tap Retry.",
    "ios.error.response": "The provider returned an incomplete or unreadable transcript. Tap Retry or choose another model.",
    "ios.error.audio": "Couldn't start the microphone. Allow microphone access and close other recording apps, then try again.",
    "ios.error.storage": "Couldn't save data on this iPhone. Check available storage and try again.",
    "ios.error.interrupted": "Recording or processing was interrupted. Return to Quill and tap Retry for the captured audio, or Cancel to discard it.",
    "ios.error.expired": "That keyboard transfer expired. Send the transcript from Quill again.",
    "ios.error.cancelled": "Dictation cancelled.",
}

android = json.loads((ROOT / "app/src/main/assets/copy/strings.default.json").read_text(encoding="utf-8"))
manifest = json.loads((ROOT / "copy-manifest.json").read_text(encoding="utf-8"))
output = IOS / "Quill/Resources/copy/strings.default.json"
existing = json.loads(output.read_text(encoding="utf-8"))["strings"] if output.exists() else {}
strings = dict(android["strings"])
strings.update({key: existing.get(key, value) if "--refresh-defaults" not in sys.argv else value for key, value in DEFAULTS.items()})
strings["onboarding.welcome.title"] = "Speak, instead of typing, across your apps. For free."
android["strings"] = strings
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(android, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for key, value in strings.items():
    if key in manifest["keys"]:
        manifest["keys"][key]["default"] = value
    else:
        manifest["keys"][key] = {"default": value, "placeholders": sorted(set(re.findall(r"%\d+\$[sdf]", value))), "overridable": True, "optionalBlank": False}
(IOS / "copy-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for font in (ROOT / "app/src/main/res/font").glob("*.ttf"):
    shutil.copyfile(font, IOS / "Quill/Resources" / font.name)
shutil.copyfile(ROOT / "app/src/main/assets/FONT-LICENSE.txt", IOS / "Quill/Resources/FONT-LICENSE.txt")
fixture = ROOT / "tests/parity/cleanup.json"
if fixture.exists():
    target = IOS / "QuillCore/Tests/QuillCoreTests/Fixtures"
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(fixture, target / "cleanup.json")
    (target / "copy-placeholders.json").write_text(json.dumps(manifest["placeholderTestVectors"], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Synchronized {len(strings)} bundled strings, fonts, manifests and available Android parity fixtures.")
