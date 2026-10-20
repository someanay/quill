# Native experience design review — October 20, 2026

The later [iOS keyboard-session follow-up](ios-keyboard-session.md) records its
own source revision, Light/Dark session-state review and expanded native gates.
The completed redesign results below remain tied to their earlier revisions.

Status: the native redesign, available execution matrix, final screenshot review
and package inspection are complete. Windows and all Apple jobs are green.
This is not a distribution release sign-off.

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
- Every surface is reviewed in Light and Dark, including provider accordions,
  credential sheets, legal details, diagnostics and custom-word sheets. Matching
  layouts are not accepted as proof that both appearances are readable.
- Windows teaches its actually registered shortcut. Mac teaches Option–Command–Space
  and macOS microphone/Accessibility permissions. iOS teaches an explicit timed
  host microphone session, keyboard Dictate/Stop, Full Access and a real insertion
  practice; manual handoff remains available as a fallback.

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
| Desktop buttons inherited inconsistent typography | Set action labels, provider headers, links and reveal controls to Plex Sans explicitly; inspect the published app's actual glyph runs |
| Every settings group was a framed card | Open desktop form sections and retain framing for editors and separate credentials |
| Speech settings repeated the entire onboarding key form | Show provider/model/language first; make credential management an explicit action |
| Repeated raw model fields competed with the model picker | Put manual model IDs behind a named action; retain custom endpoint support |
| iOS provider keyboard could cover Save | Add native Done/submit dismissal; exercise it in the actual walkthrough |
| iPad first-use keyboard tutorial hid the globe | Handle the native system tutorial and preserve original failure evidence |
| Transcript container identifier overwrote child identifiers | Remove the inherited identifier; keep assertions on the real transcript text |
| Large-text Settings rows were instantiated only after scrolling | Find and scroll to the real row before asserting hit-testing |
| iOS statistics rows overlapped at the largest accessibility text size | Let row labels determine their height, wrap text, and assert adjacent row frames do not overlap |
| iPad sidebar labels broke into fragments at accessibility sizes | Use the native tab layout at accessibility sizes, preserving readable content width |
| Current iOS floating navigation covered the last visible action | Add scroll clearance and require the final Settings action to scroll fully above navigation |
| Mac welcome stacked its columns even in a wide window | Give the two columns explicit available widths; compact windows retain the stacked presentation |
| Mac workspace repeated framed panels | Keep one editor surface; open the setup and recent-dictation sections |
| Provider labels retained promotional Android wording | Use plain provider names, four direct-link instructions, and concise save actions on each native platform |
| Practice fields had a tall hit area around a thin system field | Give the actual input surface a 52-point minimum and the shared surface/border treatment |
| Mac disclosure labels were an awkward target beside a small chevron | Use a full-width, keyboard-operable row while preserving native disclosure accessibility semantics; require actual expanded/collapsed state in UI tests |
| Generated settings concept invented a no-storage privacy claim | Reject that sentence: local optional history and retained failed recordings exist |
| Concept generator brightened the iPhone action colour | Keep the approved Quill cobalt token; do not adopt the generated colour drift |
| Green status text was too faint on light paper | Use deeper green `#23744F` in Light and `#43B585` in Dark on Apple; retain explicit text/icons for status |
| Error colours served both text and filled destructive actions | Use `#B93631` for Light error text and destructive fills, `#E8635B` for Dark error text; white action labels retain contrast |
| Secondary Apple forms lacked explicit captures in both appearances | Add all five provider forms, key sheets, word sheets, disclosure/legal details and diagnostics to native screenshot coverage |

Functional tests and screenshot review are separate gates. A passing build does
not establish visual quality, a synthetic provider response does not establish
live provider quality, and simulator results do not establish physical-device
privacy protection, permission behavior or distribution signing.

The source-based contrast gate checks 28 Windows, 24 Mac, 38 iOS host and 10 keyboard enabled-text
and action pairs across both appearances. Every pair is at least 4.5:1 after the
semantic-colour correction; the smallest measured ratios are 4.775, 5.230,
4.699 and 5.540 respectively. This checks those foreground/background combinations only,
not every accessibility requirement or every disabled/system control.

## Concept comparison

| Point | Native result |
| --- | --- |
| Composition | Desktop welcome has a narrow progress rail, dominant left-aligned title, a single example editor and two intrinsic-width actions. Compact layouts stack and scroll. |
| Typography | The real bundled Plex fonts are verified. Desktop hero is 64 points at wide sizes and 34 when compact; iPhone uses a 38-point display heading. Native forms retain their smaller reading scale. |
| Palette | The same Quill paper, surface, cobalt, lavender and divider tokens are used in actual renders. Generated colour drift is rejected. |
| Actions | Windows primary is 156×46 minimum on Welcome; Mac actions are 44 points high; phone actions are at least 50 points high and iPad actions are capped at 320 points wide. |
| Surfaces | Desktop forms are open sections; the example/practice editor is the primary framed surface. iOS retains native cards and controls with the same colours and type. |
| Copy | Windows keycaps track its registered shortcut. Mac uses Option–Command–Space. iOS explains host recording, explicit handoff and keyboard insertion within 60 seconds. |
| Navigation | Windows has its own rail and compact navigation. Mac uses native sidebars, menus and a separate Settings window. iPhone uses tabs/sheets; iPad uses a split view with an accessibility-size tab fallback. |

The native WPF/SwiftUI/UIKit apps cannot be exercised in a browser canvas. The
frontend design/testing loop is applied to real native renders, installed Windows
UI and Apple simulator/desktop screenshot attachments. The concept images are
references; every interactive control is implemented in native code.

## Final native evidence

Earlier design sets are retained as history under `dist/native-redesign/`,
including `b83-current`, `610-review` and `952-final`. The results below supersede
their intermediate counts and unresolved findings.

The final Windows provider and row typography renders pass all 300 scenarios.
The final installed x64 executable SHA-256 is
`4a98a41a25ea552127d60ed2e32d05eb701dd211a5198ef33143fba955795f16`.
All ten installed files match the final package, including catalog revision 23.
It is installed through the real package installer; Start and Installed apps are
verified. On the preceding typography build, Start reopened PID 48804 / HWND
5901480 after Close hid the main window. Activation code is unchanged and passes
both final CI architectures. App data before and after the final update matches
byte for byte. The update leaves Quill closed. Published-app diagnostics confirm
Plex Sans glyphs for body/keycaps/example text and Plex Serif for the hero.

The final Mac presentation at `928cdcb` passes all 34 core, 43 native and eleven
UI tests on macOS 15 and 26, Intel and Apple Silicon. All 109 current Apple Silicon
named captures were reviewed in
ten sheets at `dist/native-redesign/928-final/mac-gallery/`, including every
settings category at both sizes, all five provider forms and footers, actual
expanded legal details, diagnostics, setup, workspace and history in both themes.
Persistent labels remain visible while editing endpoints, models and custom words.
The universal Release matches all 568 strings at revision 23, both Android font
files, native usage descriptions and both architectures, with no DEBUG fixtures.
All 109 current Intel captures were also reviewed in ten additional sheets at
`dist/native-redesign/928-intel-retry/review/`; its upload retry is fully green.

The final baseline iOS run at `d3691f3` passes 51 core, 47 native, five recorder,
thirteen iPhone UI and thirteen iPad UI checks. All 91 named phone and 87 tablet
captures were reviewed in sixteen sheets at `dist/native-redesign/d369-baseline/`.
They include every settings destination, all six setup stages, five provider
forms and their footers, guide and word sheets, privacy/legal/diagnostics,
largest text, landscape workspace/history and the actual UIKit keyboard in both
appearances. The current iOS 26.4.1 run passes the same complete suites; its own
91 phone and 87 tablet captures were reviewed in sixteen further sheets at
`dist/native-redesign/d369-current/`. The local gallery is
`dist/native-redesign/review-index.md`; exact runs and package hashes are in
[Apple validation](apple-validation.md).

Largest-text rows no longer overlap, the tablet uses readable accessibility
navigation, and the final action scrolls above floating navigation. Wide iPad
Home separates recording and context; History separates selection and reading.
The baseline device and simulator packages match revision 21 / 603 strings,
Android font bytes and permission descriptions; the device Release excludes
DEBUG fixtures.

The final Windows matrix at `7f22dff` passes all 362 tests, 300 renders, 42 setup
checks, 14 editor/clipboard checks and 11 installer checks on x64 and ARM64.
All fifty 1200×800 captures were reviewed again in five sheets at
`dist/native-redesign/windows-ci-final/review/`. Final local packaging passes for
both architectures; the latest local activation run's one foreground-permission
refusal is recorded separately from the green isolated CI suites.
