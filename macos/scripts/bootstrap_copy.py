"""Rebuild the initial macOS catalog from Android plus accurate platform wording.
Run only when intentionally rebasing copy; normal authoring uses the Quill Copy Tool.
"""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
catalog = json.loads((root / 'app/src/main/assets/copy/strings.default.json').read_text(encoding='utf-8'))
catalog['strings'].update({
    'mac.error.bad_key': 'That key was rejected. Check that you copied the complete key for this provider.',
    'mac.error.quota': 'Your provider account has run out of usage or credits. Check your plan, then retry.',
    'mac.error.rate_limit': 'Your provider is limiting requests. Wait a moment, then retry.',
    'mac.error.network': 'Could not reach your provider. Your recording is kept in memory so you can retry.',
    'mac.error.timeout': 'The provider took too long. Retry the recording or cancel it.',
    'mac.error.endpoint': 'Enter an HTTPS base URL without a username, password, query, or fragment.',
    'mac.error.model': 'Choose a model or enter its exact model ID.',
    'mac.error.microphone': 'Microphone access is off. Allow Quill in System Settings → Privacy & Security → Microphone.',
    'mac.error.accessibility': 'Allow Quill in System Settings → Privacy & Security → Accessibility to insert into apps.',
    'mac.error.sensitive': 'Dictation is off in password and secure fields.',
    'mac.error.focus_changed': 'The text field changed. Your dictation is ready to copy; focus the intended field before pasting.',
    'mac.error.storage': 'Quill could not save data securely. Check available storage and access to this Mac.',
    'mac.error.provider': 'The provider did not return a complete transcript. Retry, or choose another model in Speech & model.',
    'mac.error.login': 'Quill could not update its login item. Check Login Items in System Settings.',
    'mac.error.contacts': 'Quill could not read contacts. Allow Contacts access in System Settings and try again. Your previous import is kept.',
    'mac.status.cancelled': 'Dictation cancelled.',
    'mac.status.transcribing': 'Transcribing…',
    'mac.status.inserted': 'Dictation inserted.',
    'mac.status.unconfirmed': 'The app accepted an insertion request, but Quill could not confirm the text. Check the field before pasting again. Your dictation is still available to copy.',
    'mac.status.ready_to_copy': 'Your dictation is ready. Copy it, then paste it into your chosen field.',
    'mac.status.pasted': 'Paste sent. Check the field; your dictation is still available to copy.',
    'mac.status.copied': 'Copied to the clipboard.',
    'mac.dictation.start': 'Start dictation',
    'mac.dictation.stop': 'Stop & transcribe',
    'mac.dictation.paste': 'Paste into field',
    'mac.action.retry': 'Retry',
    'mac.action.edit': 'Edit',
    'mac.action.close': 'Close',
    'mac.meter.level': 'Microphone level',
    'mac.key.show': 'Show key',
    'mac.key.label': 'Key name (optional)',
    'mac.key.unverified': 'Not verified',
    'mac.key.security': 'Keys are protected by this Mac’s Keychain and never appear in logs. A key is sent only to its selected provider over HTTPS. Custom keys stay bound to the exact base URL at which you saved them.',
    'mac.mic.test': 'Test microphone',
    'mac.mic.stop_test': 'Stop microphone test',
    'mac.onboarding.overlay': 'Quill can float over other apps on macOS without a separate overlay permission. Show the bubble, then enable Accessibility so your words can be inserted into the focused field.',
    'mac.bubble.show': 'Show floating bubble',
    'mac.accessibility.disclosure': 'Quill uses macOS Accessibility to identify the focused text field, read its existing text and selection to preserve your work, and insert your dictation. Password and secure fields are excluded. Focused field contents are processed on this Mac and are not uploaded. Dictated audio and optional cleanup text go directly to the providers you choose. You can revoke access in System Settings at any time.',
    'mac.accessibility.consent': 'I understand and allow Quill to use Accessibility for dictation.',
    'mac.home.count': '%1$d dictations today',
    'mac.home.words': '%1$d words written today',
    'mac.home.tip': 'Tap to speak, then tap again to send. Drag the bubble to either screen edge. Drag to the bottom to hide it for 10 minutes. Long-press to dictate to the clipboard.',
    'mac.hotkey.label': '⌘ Option Space — start or stop dictation',
    'mac.history.empty': 'Your dictation transcripts show up here. Saved on this Mac only.',
    'mac.history.off': 'Turn on Save dictation history in Privacy and History to keep transcripts on this Mac. Private mode always prevents saving.',
    'mac.models.refresh': 'Refresh models',
    'mac.models.offline': 'Live models could not be loaded. Your saved model and bundled choices are still available.',
    'mac.custom.key_scope': 'Changing the base URL requires saving and verifying a key for the new endpoint. An existing custom key is never sent to a different URL.',
    'mac.style.effective': 'Current style: %1$s',
    'mac.audio.limit': 'Auto-stops at %1$d seconds',
    'mac.audio.silence': 'Silence auto-stop is on: a 2-second startup grace, then 3.5 seconds below a conservative quiet-room threshold.',
    'mac.audio.format': 'WAV · 16 kHz · mono · 16-bit PCM. Matches the current Android upload format.',
    'mac.audio.input': 'Uses the default macOS input device. Choose your microphone in System Settings → Sound → Input.',
    'mac.audio.warning': 'Recording will stop in 15 seconds.',
    'mac.size.small': 'Small', 'mac.size.medium': 'Medium', 'mac.size.large': 'Large',
    'mac.theme.system': 'Match system', 'mac.theme.light': 'Light', 'mac.theme.dark': 'Dark',
    'mac.privacy.body': 'Audio goes directly to your chosen speech provider. Optional cleanup sends the transcript to your chosen cleanup provider. Quill runs no account or data server. Keys, settings, contact names and history stay on this Mac. Private mode prevents new history entries. Audio is transient and is discarded after success or cancellation.',
    'mac.login.enable': 'Start Quill at login',
    'mac.login.help': 'Restores the bubble at login when it is enabled. macOS may require you to allow the login item. Quill never restarts a recording after sleep or a crash.',
    'mac.login.settings': 'Open Login Items',
    'mac.report.save': 'Save reviewed report…',
    'mac.about.copy': 'All interface text is bundled with this app. Copy changes arrive only in a new app release.',
    'mac.menu.quit': 'Quit Quill', 'mac.menu.open': 'Open Quill', 'mac.menu.toggle_bubble': 'Show / hide bubble',
    'mac.legal.privacy': 'Quill is a bring-your-own-key dictation app. Recordings are sent directly to your chosen speech provider over HTTPS; optional cleanup sends dictated text directly to your chosen cleanup provider. Provider usage is subject to that provider’s terms. Quill has no account, data server, analytics or automatic report uploads. Keys are stored in Keychain. History is stored locally with a default 1-hour retention; private mode prevents new history. Imported contact names stay local. Accessibility is used to preserve and insert into the focused field; password fields are refused. You may delete keys, history and settings in Advanced.',
    'mac.legal.terms': 'Quill requires a supported provider account and your own API key. Your provider may charge for usage and impose limits. You are responsible for using your provider account in accordance with its terms. Review dictated text before sending it: transcription and cross-app insertion can be inaccurate or unsupported in some apps. This app does not provide emergency, medical, legal or financial services.',
    'mac.cleanup.example.before': 'um i dont know maybe tomorrow',
    'mac.cleanup.example.after': "I don't know, maybe tomorrow.",
})
catalog['strings']['settings.keys.banner'] = catalog['strings']['mac.key.security']
catalog['strings']['compliance.rationale.accessibility'] = catalog['strings']['mac.accessibility.disclosure']
catalog['strings']['settings.privacy.banner_text'] = catalog['strings']['mac.privacy.body']
catalog['strings']['settings.audio.format_sub'] = catalog['strings']['mac.audio.format']
catalog['strings']['settings.privacy.history_sub'] = 'Kept on this Mac, then auto-deleted'
catalog['strings']['settings.advanced.keepalive_sub'] = catalog['strings']['mac.login.help']
path = root / 'macos/QuillMac/Resources/copy/strings.default.json'
path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
manifest = {'revision': catalog['revision'], 'keys': {key: {'default': value, 'placeholders': re.findall(r'%(?:\d+\$)?[sdif]', value), 'overridable': True, 'optionalBlank': False} for key,value in sorted(catalog['strings'].items())}}
(root / 'macos/copy-manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(f'Bootstrapped {len(catalog["strings"])} macOS copy keys')
