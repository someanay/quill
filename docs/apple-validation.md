# Apple validation — October 20, 2026

The owner authorized pushing the Apple changes and running native GitHub Actions
tests from the Windows workspace. Local Git write access was confirmed by a
successful push to `codex/apple-native-validation`. Windows/site working changes
remain outside that branch. The earlier Actions billing block is no longer
preventing Mac runners from starting.

The main acceptance suite and installed-keyboard integration passed. Confirmed results:

The complete Mac/iOS test and release-build steps were also repeated successfully
at `e5807a0` in [run 39309456958](https://github.com/someanay/quill/actions/runs/39309456958).
The only source change after the first green main run is the separately verified
keyboard tutorial handling in its dedicated integration test.

| Check | Source / run | Result |
|---|---|---|
| Mac portable core | `e5ab196` / [39308433649](https://github.com/someanay/quill/actions/runs/39308433649) | 34 passed |
| Mac native logic | same | 43 passed |
| Mac UI | same | 8 passed; 53 screenshots exported |
| TextEdit | `e5ab196` / [39308433694](https://github.com/someanay/quill/actions/runs/39308433694) | 11 real editor checks passed |
| Safari and separate native fields | same | 6 Safari + 3 AppKit checks passed, including secure-field refusal |
| Mac universal Release | `e5ab196` / 39308433649 | Intel + Apple Silicon, correct copy/fonts, DEBUG presentation/history fixtures absent |
| iOS portable core | `e5ab196` / [39308433649](https://github.com/someanay/quill/actions/runs/39308433649) | 49 passed |
| iOS native logic | same | 45 passed, including terminal-recovery regressions |
| Real iOS simulator recorder | same | 5 passed |
| iPhone / iPad UI | same | 6 + 6 passed; 31 iPad screenshots exported |
| Installed keyboard in another app | `e5807a0` / [39309456849](https://github.com/someanay/quill/actions/runs/39309456849) | Passed recording, host restart, publication, explicit insertion and consume-once check |
| iOS device Release archive | `e5ab196` / 39308433649 | arm64 host + embedded keyboard, correct copy/fonts, DEBUG provider fixture absent |

Runner coverage: macOS 15.7.9, Xcode 16.4, iPhone 16 Pro and iPad Pro 11-inch (M4)
simulators running iOS 18.5. This is evidence for those environments, not every
Apple OS version or hardware model.

Inspected Mac ZIP SHA-256:
`3caed1cbf517f037bf24d85083e463ee4d857c36e09cef87601ba9b35e48a4cd`.
It contains catalog revision 18 (563 strings), targets macOS 13+, and is unsigned.
The unsigned iOS device archive targets iOS 17+, contains 551 catalog strings and
has SHA-256 `e81b1800c969ee8eb452e412ade323b6c939e992482ec932796b6432c939a23d`.

## Findings corrected during execution

- Mac Settings controls had duplicate accessibility identifiers after the desktop
  redesign. Navigation, toolbar and workspace actions now have distinct identifiers.
- Key drafts intentionally survive app restarts. Resize tests explicitly replace
  earlier synthetic input before checking that the same draft survives resizing.
- SwiftUI replaced the manually assigned Mac menu. App commands now belong to the
  SwiftUI scene so standard editing, Settings and Close shortcuts remain available.
- Native toolbar controls expose a wrapper and a real control. UI tests click the
  reachable control and still require actual navigation.
- The Actions display mode originally reverted when the configuration process
  exited. It now persists for the login session, enabling expanded-layout coverage.
- iOS Settings briefly retained dismissed keyboard rows with infinite frames.
  Integration tests reacquire a usable visible row before requesting a tap.
- XCTest rejected the iOS privacy paragraph as an identifier longer than 128
  characters. The test now matches the exact label with a predicate; the privacy
  assertion is unchanged.
- A fresh simulator's QuickPath introduction covered the real keyboard globe key.
  The integration walkthrough now completes that visible system tutorial before
  selecting Quill. A blocked/skipped walkthrough is still rejected by CI.

## Screenshot review

Original screenshots and accessibility hierarchies are exported from the native
test result bundles. Review includes the actual pixels, not just test outcomes.

The first iPhone capture set includes the setup checklist, microphone test,
recording/transfer explanation, keyboard setup and ready screens; every Settings
category; light/dark compact keyboard renders; largest Dynamic Type; and landscape.
Text wraps and scrolls, the native segmented controls remain reachable in the
short landscape viewport, and the keyboard/Full Access explanations describe the
actual host-recording workflow. Landscape PNGs contain EXIF orientation; honor it
when building a review sheet rather than interpreting the stored pixel rotation
as a layout defect.

The Mac capture sets include all seven setup stages in both themes, provider
accordions, resize/draft retention, all ten relevant Settings categories, private
and empty history, plus populated history and Home. Short windows use scrolling;
the history reader retains the full transcript rather than truncating it to a row.
Expanded captures now confirm Home's workspace/supporting panes, History's full
reader, three-column onboarding and compact toolbar navigation. Mac UI tests pass
at 660×500, 1160×740 and 760×1000, with light/dark Home and History plus setup draft
retention. The native File/Edit menus are present in the screenshots after the
command-ownership fix. iPad portrait/landscape and large-text captures are readable,
and the actual installed extension screenshot shows `Before This is a simulated
provider response.` in the other app with Insert disabled after consumption.

The iOS experience still merits its own design pass: iPad uses a single content
column with substantial unused space; setup confirms the keyboard with a manual
button rather than a required successful practice insertion; the extension shows
its own globe alongside the system globe; and the saved-count copy says
`1 dictations`. Contacts vocabulary is also still exposed on iOS although its
stored candidates do not enter dictation. Track these in the next iOS experience
milestone. The native recording, provider, storage and handoff foundations are
worth retaining.

## Scope and remaining acceptance

The iOS integration uses a DEBUG synthetic provider response with real native
recording, Keychain, Settings, App Group transfer and the installed keyboard in a
separate app. It does not verify a live provider account. The iOS app retains its
existing explicit host-record → publish → return → insert workflow; a keyboard-start
and automatic-return redesign is still a separate feasibility task.

Real hardware remains necessary for microphone fidelity, interruptions/lock,
long-running dictation, wider third-party editor compatibility, login behavior
and physical-device protection. Distribution signing and notarization are separate
from these unsigned/ad hoc CI builds. Existing Android and Windows checks are
outside this Apple validation scope.
