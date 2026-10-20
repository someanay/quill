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

October 20 follow-up: [timed keyboard sessions](ios-keyboard-session.md) supersede
the repeated manual handoff described below. The owner explicitly requested this
interaction change; the earlier record/send/return/insert loop remains a fallback.
The session report records its separate native acceptance and physical-device limits.

Wispr's current first-party guides teach keyboard enablement and Full Access,
microphone access and a successful practice insertion. Borrow that progression
and the explicit return-to-app guidance, while retaining Quill's own-key/privacy
explanations. Do not borrow account/subscription screens or privacy promises.

1. Welcome: introduce a timed microphone session and dictation from the Quill keyboard.
2. Connect a speech provider: explain costs, save and verify the user's key.
3. Enable Quill in Settings → General → Keyboard → Keyboards → Add New Keyboard,
   then Full Access. Explain session commands and the protected one-use transcript transfer;
   credentials remain in the host.
4. Request microphone access from the host app, test locally and recover from denial.
5. Practise with a real text field: enable the session in Quill, switch keyboards
   using the globe, dictate and insert. Success means visible text,
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

The October 20 session keeps real microphone input active for five minutes after
an explicit foreground start. Idle buffers are discarded; only keyboard-requested
takes are recorded and sent to the provider. Dictate/Stop work from the destination
app without repeated host activation. Automatic insertion requires the unchanged
original field; changed fields retain explicit Insert. End and expiry release the
microphone. The OS microphone indicator remains visible while the session is live.
The older record/send/return/insert path remains an explicit fallback, with a
60-second one-use handoff. There is no Android overlay, microphone in the extension,
cold background microphone activation or automatic return to an arbitrary app.

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

The earlier Apple redesign suites pass 34 Mac core, 43 native and eleven UI tests on all four
Mac configurations; 51 iOS core, 47 native, five real recorder and thirteen UI tests
per device on both runtimes. All eight TextEdit/Safari/AppKit jobs and all four
installed-keyboard journeys pass. Final screenshots and packages are inspected.
See [Apple validation](apple-validation.md) for exact runs, package hashes and
physical-hardware limits.

The separate [keyboard-session follow-up](ios-keyboard-session.md) records the
new iOS source revision, expanded tests and installed-keyboard background checks.

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
| iPhone | A native own-key setup with real keyboard connection/practice gates. Timed sessions remove repeated app switching and manual handoff during ordinary dictation. One foreground activation remains necessary, and the microphone stays active for the session. Retain the host, storage/provider and keyboard foundations. | Physical-device keyboard/audio/lock/interruption, idle-session battery and live-provider acceptance, plus distribution signing. The session report separates CI acceptance from these gates. |
| iPad | Meaningful split navigation, workspace and history layouts, plus readable accessibility navigation and a compact landscape keyboard. It uses the tablet's space without stretching phone controls. | Physical split-view/window resizing, rotation with real audio/keyboard use, external-keyboard and interruption acceptance, plus the iPhone release gates. |

These are private-beta/readiness judgments, not App Store, Gatekeeper or public
release approval. Minimum deployment targets do not establish execution on every
older supported OS. The disconnected Honor phone also prevents a fresh Android
physical retest; its existing UI was preserved as the design reference.
