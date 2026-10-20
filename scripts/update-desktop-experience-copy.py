"""Add desktop experience copy without replacing existing locally authored strings."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COPY = {
    "desktop.provider.choose": "Choose a provider",
    "desktop.provider.gemini": "Google Gemini",
    "desktop.provider.groq": "Groq",
    "desktop.provider.openrouter": "OpenRouter",
    "desktop.setup.progress": "Step %1$d of %2$d",
    "desktop.setup.local": "Your key. Your provider. Your words.",
    "desktop.setup.welcome_title": "A little less typing. A lot more flow.",
    "desktop.setup.welcome_body": "Turn a thought into text without leaving the app you're writing in. Set up Quill for your desktop in a few steps.",
    "desktop.setup.privacy_body": "Quill connects directly to your speech provider. Choose who processes your recordings before you start.",
    "desktop.setup.provider_title": "Connect your speech provider",
    "desktop.setup.provider_body": "Add and verify a key. You can change providers, models and cleanup later in Settings.",
    "desktop.setup.audio_title": "Check your microphone",
    "desktop.setup.access_title": "Let your words reach your apps",
    "desktop.setup.access_body": "Quill can insert your finished dictation into the text field you selected. Review and allow text insertion below.",
    "desktop.setup.shortcut_title": "One shortcut, wherever you write",
    "desktop.setup.shortcut_body": "Focus a text field. Press your shortcut to start, speak, then press it again to finish.",
    "desktop.setup.controls": "The floating control and tray menu are available too. Closing this window keeps Quill in the tray.",
    "desktop.setup.bubble_choice": "Show the floating recording control",
    "desktop.setup.ready_title": "Make room for your next thought.",
    "desktop.setup.ready_body": "Your workspace has a practice field. Try a short dictation there, then use the same shortcut in your everyday apps.",
    "desktop.setup.practice": "Open the practice workspace",
    "desktop.setup.finish": "Go to Home",
    "desktop.demo.label": "Example dictation",
    "desktop.demo.spoken": "Let's move our meeting to Thursday. I'll send the notes before then.",
    "desktop.dictation.stop": "Stop dictation",
    "desktop.practice.title": "Try Quill here",
    "desktop.practice.body": "Click the field, then press your dictation shortcut. Your real provider processes the recording.",
    "desktop.workspace.title": "Space for your next thought.",
    "desktop.workspace.setup_needed": "Finish connecting your provider and permissions to start dictating.",
    "desktop.workspace.configuration": "Your setup",
    "desktop.workspace.recent": "Recent dictations",
}
STAGES = {
    "welcome": ("Welcome", "From a thought to a finished sentence.", "A shortcut keeps your attention on what you are writing. Quill puts your words in the field you already have open."),
    "privacy": ("Your data", "Choose where your voice goes.", "Your provider processes the recording with your own key. Quill does not route it through a Quill server. Check your provider's pricing and privacy terms."),
    "provider": ("Connect provider", "Bring the provider you trust.", "Keys are encrypted on this device. Verifying a key checks authentication; it does not record speech or prove that model credits are available."),
    "audio": ("Microphone", "A quick sound check.", "Test your usual microphone before dictating. This test stays on your device and is discarded; nothing is uploaded."),
    "access": ("Text insertion", "Your cursor is the destination.", "Quill checks the focused field before inserting. Password fields are excluded. If an app cannot accept insertion, your transcript remains available to copy."),
    "shortcut": ("Shortcut & practice", "Stay in the app you are using.", "Start and finish with your keyboard shortcut. The floating control is optional. You can always reopen Quill from its desktop menu."),
    "ready": ("Ready", "Try it where you already write.", "A short sentence is enough for your first try. Change providers, cleanup, writing style and history retention whenever you need to."),
}
for stage, (label, title, body) in STAGES.items():
    COPY[f"desktop.setup.stage.{stage}"] = label
    COPY[f"desktop.setup.companion.{stage}"] = title
    COPY[f"desktop.setup.companion_body.{stage}"] = body

MAC = {
    "mac.setup.shortcut_unavailable": "This shortcut is unavailable. Use the floating control or menu bar. Check other apps and macOS keyboard shortcuts before trying it again.",
    "mac.setup.audio_body": "Allow microphone access, then start a local sound check. This test is discarded and never sent to a provider. If access was denied, enable Quill in System Settings and return.",
    "mac.setup.access_body": "Enable Quill in System Settings → Privacy & Security → Accessibility. This lets Quill insert text into the field you choose. Return here after enabling it.",
    "mac.setup.controls": "Use Command–Option–Space to start and finish. The optional floating control and menu bar are available too. Closing the window keeps Quill in the menu bar.",
    "mac.setup.finish": "Keep Quill in the menu bar",
}

for platform, source in [
    ("windows", "windows/src/Quill.Windows/Assets/copy/strings.default.json"),
    ("macos", "macos/QuillMac/Resources/copy/strings.default.json"),
]:
    path = ROOT / source
    manifest_path = ROOT / platform / "copy-manifest.json"
    catalog = json.loads(path.read_text(encoding="utf-8-sig"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    additions = COPY | (MAC if platform == "macos" else {})
    changed = False
    for key, value in additions.items():
        if platform == "macos" and key == "desktop.setup.access_body":
            continue
        if key not in catalog["strings"]:
            catalog["strings"][key] = value
            manifest["keys"][key] = {"default": value, "placeholders": re.findall(r"%(?:[1-9]\d*\$)?[sdf]", value), "overridable": True, "optionalBlank": False}
            changed = True
    if changed:
        catalog["revision"] += 1
        manifest["revision"] = catalog["revision"]
        catalog["updatedAt"] = "2026-10-19T00:00:00Z"
        for target, data in [(path, catalog), (manifest_path, manifest)]:
            target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{platform}: {len(catalog['strings'])} strings, revision {catalog['revision']}")
