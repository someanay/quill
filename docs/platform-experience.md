# Platform experience — October 19, 2026

The owner's direction supersedes Android screen/order parity in `platform-rebuild.md`.
Windows, macOS and iOS are separate products with Quill's DNA: user-owned provider
keys, direct provider connections, conservative cleanup, secure local storage,
privacy, cobalt, IBM Plex typography and the voice/ink control. Android navigation,
permissions, screen proportions and setup steps are not a cross-platform template.

The native cores are worth retaining. Windows already has working WPF, recording,
UI Automation and protected storage. Mac has native SwiftUI/AppKit, recording,
Accessibility and Keychain adapters, with real TextEdit evidence and a Safari fix
still awaiting native verification. iOS has native host recording, provider and
storage code plus a keyboard transfer implementation. Replacing those foundations
would discard integration work without solving the experience problem. Rebuild
the desktop presentation and validate the iPhone recording/keyboard journey.

## Desktop design

Cool paper #F5F7F5, surface #FFFFFF, ink #202522, muted #5C665F, cobalt #234DCE;
retain the matching existing dark palette. IBM Plex Serif marks major headings;
IBM Plex Sans carries controls and reading text. Left align instructions. Spend
visual emphasis on the dictation/practice surface, not decorative permission art.

Resizable setup has a progress rail, an actionable step and, when width permits,
a contextual demonstration. Compact or tall windows stack content; short windows
scroll independently. Do not enlarge typography or lock the whole window to a
phone-shaped aspect ratio. Never rebuild a key form during resizing.

Desktop setup: welcome → explain own-key costs/privacy → connect provider →
test microphone → insertion access → shortcut/practice introduction → ready.
Windows needs desktop microphone access and explicit insertion consent; macOS
needs microphone permission and Accessibility trust. Neither needs an Android
overlay permission, accessibility service, battery exemption or keyboard setup.
The bubble is an optional control, alongside the shortcut and tray/menu bar.

Primary desktop destinations are Home and History, with Settings as the place
for providers, audio, cleanup, words, privacy and appearance. Configuration
categories are not extra primary tabs. Wide Home uses a workspace and supporting
configuration/history. History gains a selectable list and full transcript reader;
compact windows stack these. Settings uses persistent categories on larger windows.
The contacts candidate importer is hidden from desktop navigation until those
names actually influence dictation; its existing stored data is retained.

## iPhone proposal (research, not an implemented keyboard redesign)

Wispr's current first-party guides teach keyboard enablement and Full Access,
microphone access and a successful practice insertion. Borrow that progression
and the explicit return-to-app guidance, while retaining Quill's own-key/privacy
explanations. Do not borrow account/subscription screens or privacy promises.

1. Welcome: demonstrate how Quill works from the iPhone keyboard.
2. Connect a speech provider: explain costs, save and verify the user's key.
3. Enable Quill in Settings → General → Keyboard → Keyboards → Add New Keyboard.
4. Explain Full Access accurately and guide the user to enable it. Quill uses it
   for a one-use shared transcript transfer; credentials remain in the host.
5. Request microphone access from the host app and recover from denial.
6. Practise with a real text field: switch keyboards using the globe, start
   recording in Quill, transcribe, return and insert. Success means visible text,
   not an "I've done this" checkbox.
7. Confirm readiness; offer Copy for apps/fields that reject custom keyboards.

The existing Quill workflow records in the host, explicitly publishes a transcript,
then consumes it in the keyboard within 60 seconds. It does not yet implement
Wispr's keyboard-initiated recording/return session. That requires a distinct
platform feasibility and installed-keyboard acceptance milestone before onboarding
may promise it. Do not imply an Android overlay or microphone inside the extension.
Keep iPhone navigation compact; adapt iPad independently with wider panes.

Sources checked October 19, 2026:
- https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone
- https://docs.wisprflow.ai/articles/3152211871-setup-guide
- https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard
- https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html

## Acceptance

October 19 verification: Windows Release build, 250 screen renders, 35 setup/state
retention checks and seven focused first-launch/theme/font tests passed. The full
Windows suite passed 360/361; the existing shortcut-reopen foreground assertion
remains failed. See [Windows evidence](../windows/VERIFICATION.md). All 49 iOS and
34 Mac portable core tests also passed on Windows.

October 20 Apple execution: 34 Mac core, 43 native and eight UI tests pass, as do
real TextEdit/Safari/native secure-field probes. The new universal Mac Release
contains the desktop redesign and was inspected against source. iOS passes 49
core, 45 native, five real simulator recorder, six iPhone UI and six iPad UI tests;
the actual installed keyboard also completes recording/publication/insertion in
a separate app. See [Apple validation](apple-validation.md) for exact runs,
screenshots, package hashes and remaining hardware/design scope.

Preserve real permission/key gates and all dictation/storage safeguards. Render
compact, wide, tall and short windows in both themes. Check navigation, readable
wrapping, reachable actions, preserved key drafts on resize, complete history text,
and no duplicate live recorders. Native Mac compilation, permissions, shortcuts
and real editor integration require a Mac; Swift parsing/portable tests are not
a substitute. The existing explicit iOS transfer loop now passes; the proposed
keyboard-start/return redesign has not been implemented or validated.
