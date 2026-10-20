# Native platform experience — October 20, 2026

The owner's direction supersedes Android screen/order parity in `platform-rebuild.md`.
Windows, macOS and iOS are separate products with Quill's DNA: user-owned provider
keys, direct provider connections, conservative cleanup, secure local storage,
privacy, cobalt, IBM Plex typography and the voice/ink control. Android navigation,
permissions, screen proportions and setup steps are not a cross-platform template.

The native cores are worth retaining. Windows already has working WPF, recording,
UI Automation and protected storage. Mac has native SwiftUI/AppKit, recording,
Accessibility and Keychain adapters, with verified real TextEdit and Safari insertion probes. iOS has native host recording, provider and
storage code plus a keyboard transfer implementation. Replacing those foundations
would discard integration work without solving the experience problem. Rebuild
the presentation around those foundations. The desktop, iPhone and iPad design
pass is implemented; exact native acceptance results live in the reports below.

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

## Implemented iPhone and iPad experience

Wispr's current first-party guides teach keyboard enablement and Full Access,
microphone access and a successful practice insertion. Borrow that progression
and the explicit return-to-app guidance, while retaining Quill's own-key/privacy
explanations. Do not borrow account/subscription screens or privacy promises.

1. Welcome: introduce recording in Quill and inserting with its keyboard.
2. Connect a speech provider: explain costs, save and verify the user's key.
3. Enable Quill in Settings → General → Keyboard → Keyboards → Add New Keyboard,
   then Full Access. Explain the protected one-use transcript transfer;
   credentials remain in the host.
4. Request microphone access from the host app, test locally and recover from denial.
5. Practise with a real text field: switch keyboards using the globe, start
   recording in Quill, transcribe, return and insert. Success means visible text,
   not an "I've done this" checkbox.
6. Confirm readiness; offer Copy for apps/fields that reject custom keyboards.

Continue from practice requires the actual matching keyboard insertion receipt.
Typing in the practice field cannot complete the gate. The protected receipt
contains a connection time and inserted handoff identifier, never audio,
credentials or destination text. Keyboard setup requires a recent receipt.

iPhone uses native tabs, navigation and sheets. iPad uses split navigation, a
wider workspace and a list/reader history layout. At accessibility text sizes,
native tabs preserve readable content width. Rows/cards grow vertically with
their text; the final action scrolls above current iOS floating navigation.
Provider forms have native keyboard dismissal, and landscape extension rows adapt
independently. Inactive contacts candidates are hidden from navigation.

The existing Quill workflow records in the host, explicitly publishes a transcript,
then consumes it in the keyboard within 60 seconds. It does not yet implement
Wispr's keyboard-initiated recording/return session. That requires a distinct
platform feasibility and installed-keyboard acceptance milestone before onboarding
may promise it. Do not imply an Android overlay or microphone inside the extension.
This explicit app-switching loop is less seamless than keyboard-initiated
recording. The redesigned onboarding describes the loop the app actually supports.

Sources checked October 19, 2026:
- https://docs.wisprflow.ai/articles/7453988911-set-up-the-flow-keyboard-on-iphone
- https://docs.wisprflow.ai/articles/3152211871-setup-guide
- https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard
- https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html

## Acceptance

Windows x64 and ARM64 each pass 362 tests with zero skipped, 300 screen renders,
42 setup/state checks, 14 native editor/clipboard checks and 11 installer checks
in [run 39405184046](https://github.com/someanay/quill/actions/runs/39405184046).
The actual local 2.0.3 installation, Start shortcut, Installed apps entry and
same-instance reopening are verified. See [Windows evidence](../windows/VERIFICATION.md).

The final Apple suites pass 34 Mac core, 43 native and eleven UI tests on all four
Mac configurations; 51 iOS core, 47 native, five real recorder and thirteen UI tests
per device on both runtimes. All eight TextEdit/Safari/AppKit jobs and all four
installed-keyboard journeys pass. Final screenshots and packages are inspected.
See [Apple validation](apple-validation.md) for exact runs, package hashes and
physical-hardware limits.

Preserve real permission/key gates and all dictation/storage safeguards. Render
compact, wide, tall and short windows in both themes. Check navigation, readable
wrapping, reachable actions, preserved key drafts on resize, complete history text,
and no duplicate live recorders. Native Mac compilation, permissions, shortcuts
and real editor integration require a Mac; Swift parsing/portable tests are not
a substitute. Public release also requires signing/notarization, physical-device
microphone/permission/interrupt testing and live provider/editor acceptance.
Windows is closest to a usable installed release. Mac and iOS retain credible
native foundations and a coherent redesigned interface; their exact execution
gates must be assessed separately from visual polish.

### Product assessment

| App | Honest assessment | Remaining release work |
| --- | --- | --- |
| Windows | The strongest candidate for a private beta. The presentation is consistent, the shortcut is central, resizing is useful, and setup is a real per-user installation with Start and Installed apps integration. Retain the native core. | Signed distribution, physical ARM and multiple-monitor/DPI checks, and broader real editor/provider acceptance. The latest active-desktop foreground refusal is recorded separately from green isolated CI. |
| macOS | A coherent Mac app with native windows, menus and Settings, rather than a phone layout in a desktop window. TextEdit, Safari and native secure fields pass actual integration on Intel and Apple Silicon. Retain the core. | Physical microphone/permission-denial/Settings-return, global shortcut, login and sleep/wake acceptance; signed and notarized distribution; more third-party editors. |
| iPhone | A native, readable own-key setup and app, with actual keyboard connection/practice gates. The largest product weakness is still switching to Quill to record, sending, returning and inserting. A new visual shell alone cannot remove that friction. Retain the host, storage/provider and keyboard foundations. | Physical-device keyboard/audio/lock/interruption and live-provider acceptance, distribution signing, and a separate feasibility milestone if a keyboard-initiated recording session is desired. |
| iPad | Meaningful split navigation, workspace and history layouts, plus readable accessibility navigation and a compact landscape keyboard. It uses the tablet's space without stretching phone controls. | Physical split-view/window resizing, rotation with real audio/keyboard use, external-keyboard and interruption acceptance, plus the iPhone release gates. |

These are private-beta/readiness judgments, not App Store, Gatekeeper or public
release approval. Minimum deployment targets do not establish execution on every
older supported OS. The disconnected Honor phone also prevents a fresh Android
physical retest; its existing UI was preserved as the design reference.
