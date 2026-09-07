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
- iOS portable core, initial implementation: 20 tests passed with Swift 6.3.3 on Windows. Local log: `dist/verification/ios-core.log`.
- Copy tool: existing suite and 15 new platform-isolation scenarios passed; type check passed. Final four-catalog and browser checks are pending.
- Native Windows build/runtime and Apple build/simulator results are pending implementation completion. Pending means unverified, not passed.

Shared copy/font verification: `python scripts/verify-platform-parity.py`. Portable Apple rules: `scripts/test-apple-core.ps1`. Native build/test details live in each platform README. Final runtime checks must separately cover focus changes while transcribing, password refusal, partial selections, rejected insertion, clipboard preservation, key revocation, provider failures, cancellation and retry, process restart, and appearance at large text sizes.

## Local copy authoring

The Copy Studio platform picker selects Android, Windows, macOS or iOS. Each platform has its own catalog, manifest, revision, pending drafts and backups. The existing Android draft location is preserved. Applying a draft writes only the selected platform's source and manifest, validates formatting arguments and detects stale source. It does not distribute copy; rebuilt applications are the only delivery mechanism.
