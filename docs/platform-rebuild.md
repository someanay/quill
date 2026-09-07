# Native platform rebuild — September 8, 2026

The owner authorized replacement of the former Windows, macOS, and iOS drafts and continued development/testing across all three platforms. This initiative supersedes the historical Android-only restriction. Android remains the behavioral and visual reference; its current source wins over outdated planning notes. The website work already present in the checkout is outside this initiative.

## Parity contract

All implementations follow the same boundaries: recorder → provider client → optional cleanup → insertion/handoff; secure provider key store; local settings, history and vocabulary; bundled copy repository; native permission and reliability adapters. Native frameworks necessarily differ (Android services/Keystore, Windows desktop/UI Automation/DPAPI, Apple AVFoundation/Keychain).

Match current Android onboarding order: splash, welcome, provider warning, key setup, permission checklist, microphone test, floating control setup, insertion access, completion. Match Home/History/Settings and their detail screens. Use the bundled IBM Plex Sans and Serif fonts, cobalt #234DCE, light background #F5F7F5 and dark background #11141A from Android's current theme source.

Required shared behavior includes all five providers, independently selected speech/cleanup models, multiple encrypted keys per provider with explicit active selection, candidate validation that preserves working keys, language/romanization, conservative cleanup with raw fallback, Formal/Neutral/Casual styles, per-app style where destination identity is available, custom/contact vocabulary, transient audio with retry/discard, recording cap and silence stop, bounded local history with one-hour default retention, private mode, theme, permission recovery and local opt-in crash reporting.

## Platform constraints

iOS does not offer Android's general floating overlay or an accessibility service for writing into other apps. Its custom keyboard does not have microphone access. Recording therefore starts in the Quill host app; an explicit, short-lived handoff lets the Quill keyboard insert a completed transcript using `textDocumentProxy`. API keys stay in the host app. This is a functional platform difference, so an iOS build cannot honestly be labelled an exact Android replica. Secure fields and apps that prohibit third-party keyboards remain unavailable. [Apple's custom keyboard guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)

Windows can compile and run on this workstation. Swift core tests also run here using the official Windows toolchain. Native Apple builds and iOS simulator tests use private GitHub Actions Mac runners; a Windows Swift test is not an Apple UI test. Apple signing, physical iPhone/Mac hardware, native permission behavior, real microphone input, live provider accounts and the full cross-app insertion matrix require separate evidence. [Swift Windows installation](https://www.swift.org/install/windows/)

## Verification record

- Android baseline: `testDebugUnitTest`, `lintDebug`, `assembleDebug` passed on September 8. Local log: `dist/verification/android-baseline.log`.
- Windows: 296 unit/provider/pipeline checks passed after the cleanup settings audit. A controlled separate-process native editor passed nine insertion/focus/password/clipboard checks. Real microphone capture produced 62 PCM frames and 98,862 WAV bytes; audio was discarded. Hands-on checks found and fixed WPF style sealing, dropdown contrast and a reserved hotkey (Ctrl+Alt+Space is a working fallback). Font diagnostics and installer verification continue. Local interaction report: `dist/windows/verification/native-smoke.json`.
- iOS: app and keyboard compile on Apple's runner. At `6c081ba`, 28 portable core methods (including the 195-case Android cleanup corpus), all seven keyboard-controller tests, seven pipeline tests, five native storage tests and three simulator UI walkthroughs passed. Ad-hoc simulator signing fixed Keychain access. Two of three coordinated handoff file tests passed; a Data Protection attribute readback was unavailable in the simulator and is being separated from physical-device verification. Expanded onboarding/settings/large-text, iPad and real simulator recorder checks are queued.
- macOS: the native job passed at `6c081ba`: 28 portable core tests, seven native tests (including real Keychain, SQLite and settings persistence) and two UI walkthroughs. The app launched and packaged successfully. Broader settings, audio conversion, insertion adapter and universal Release checks are queued.
- Copy tool: all existing tests, 60 formatter agreement vectors across four catalogs, 10 source-store scenarios and 15 platform-isolation scenarios passed; production build and type check passed. Browser checks covered all platform choices, isolated drafts, required-placeholder refusal and iOS preview; temporary drafts were restored, and no browser errors were observed. Companion commit: `2f3dc6e`.
- No physical Android device is currently attached to ADB. No physical Apple hardware or live provider account has been used in this verification record. CI Android checks still fail in the pre-existing SDK installation step; the local Android baseline remains green.

Shared copy/font verification: `python scripts/verify-platform-parity.py`. Portable Apple rules: `scripts/test-apple-core.ps1`. Native build/test details live in each platform README. Final runtime checks must separately cover focus changes while transcribing, password refusal, partial selections, rejected insertion, clipboard preservation, key revocation, provider failures, cancellation and retry, process restart, and appearance at large text sizes.

## Local copy authoring

The Copy Studio platform picker selects Android, Windows, macOS or iOS. Each platform has its own catalog, manifest, revision, pending drafts and backups. The existing Android draft location is preserved. Applying a draft writes only the selected platform's source and manifest, validates formatting arguments and detects stale source. It does not distribute copy; rebuilt applications are the only delivery mechanism.
