# Native experience design review — October 20, 2026

Status: implementation and native verification in progress. This is not a release sign-off.

The owner approved the new Windows welcome concept and requested the same quality on
every page of Windows, macOS, iPhone and iPad. Android remains the brand reference.
The design references and native render evidence are in the ignored
`dist/native-redesign/` folder. Source changes remain native WPF, SwiftUI and UIKit.

## Approved direction

- IBM Plex Serif display headings and IBM Plex Sans body and controls; bundled font
  files match Android. Desktop welcome uses a large two-line heading; forms use a
  smaller heading and comfortable labels. Small windows reduce the display size.
- Dark paper `#11141A`, surface `#171B23`, text `#E0E7F6`, muted text `#B2B9C6`,
  dividers `#353C48`, cobalt actions `#234DCE`, lavender accents `#B6C9FF`.
  The light palette continues Android's paper `#F5F7F5` and white surfaces.
- Open, aligned forms and a single purposeful editor surface. Avoid repeated
  framed cards, decorative badges and oversized full-width desktop buttons.
- Native window controls and title bars follow appearance. Navigation belongs to
  the platform: desktop sidebars, iPad split navigation, iPhone tabs and sheets.
- Windows teaches its actually registered shortcut. Mac teaches Option–Command–Space
  and macOS microphone/Accessibility permissions. iOS teaches host recording,
  explicit keyboard handoff, Full Access and a real insertion practice.

## Complete surface inventory

| Platform | Surfaces to review |
| --- | --- |
| Windows | All seven setup stages; workspace; history list/reader; settings overview; speech/model; saved keys and provider forms; cleanup; writing style; custom words; audio/shortcut; appearance/floating control; privacy/history; advanced; about/legal; tray and floating control states |
| macOS | All seven setup stages; workspace; history list/reader; settings overview and ten categories; provider/key sheets; custom words; native menus, title bars and floating control states |
| iPhone/iPad | All six setup stages; required-key disclosure; provider forms; workspace; history list/reader; eleven settings destinations; provider/key sheets; custom keyboard, unavailable/expired/consumed handoff states |

## Fidelity and correctness ledger

| Finding | Correction / verification |
| --- | --- |
| Installed Windows build still showed mobile welcome copy | Install the revised 2.0.3 package, launch through its Start shortcut, compare the actual window and package hash |
| Welcome text sat at the top of large Windows windows | Centre the complete composition when it fits, retain scrolling in short windows |
| Default shortcut could differ from Windows' registered fallback | Refresh the keycaps when registration changes; verify against the live shortcut label |
| Desktop buttons inherited inconsistent typography | Set the action label's Plex Sans family, size and weight explicitly |
| Every settings group was a framed card | Open desktop form sections and retain framing for editors and separate credentials |
| Speech settings repeated the entire onboarding key form | Show provider/model/language first; make credential management an explicit action |
| Repeated raw model fields competed with the model picker | Put manual model IDs behind a named action; retain custom endpoint support |
| iOS provider keyboard could cover Save | Add native Done/submit dismissal; exercise it in the actual walkthrough |
| iPad first-use keyboard tutorial hid the globe | Handle the native system tutorial and preserve original failure evidence |
| Transcript container identifier overwrote child identifiers | Remove the inherited identifier; keep assertions on the real transcript text |
| Large-text Settings rows were instantiated only after scrolling | Find and scroll to the real row before asserting hit-testing |
| Generated settings concept invented a no-storage privacy claim | Reject that sentence: local optional history and retained failed recordings exist |
| Concept generator brightened the iPhone action colour | Keep the approved Quill cobalt token; do not adopt the generated colour drift |

Functional tests and screenshot review are separate gates. A passing build does
not establish visual quality, a synthetic provider response does not establish
live provider quality, and simulator results do not establish physical-device
privacy protection, permission behavior or distribution signing.
