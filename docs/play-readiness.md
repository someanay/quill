# Android release checklist

Updated September 6, 2026 for Quill 1.0.0 (version code 1).

The signed build is ready for device testing. This is not yet a completed Google Play submission.

## Verified locally

Latest catalog build: 540 app package defaults, 411 unit tests passing, debug lint
passing (38 warnings and 1 hint remain), and signed APK/AAB generation successful.
APK signature and 16 KB ZIP alignment checks passed; AAB JAR signature verified.
The catalog sources and assignment policy are in [app styles](app-styles.md).
The user confirmed all three styles during physical testing in the preceding pass;
the broader device matrix and Play submission items remain open. The historical
checks below describe the earlier release-preparation pass.

- 390 Android unit tests passed, including catalog parsing, pagination, ranking, cache isolation, cleanup model persistence, and existing copy/security checks.
- Android lint completed with 0 errors, 38 warnings, and 1 hint. Warnings remain in the report under `app/build/reports`.
- Optimized release APK and AAB built successfully.
- APK signature verified; APK alignment check passed with 16 KB page alignment. This check does not by itself prove every native dependency's ELF alignment.
- Android copy export and copy-tool manifest match byte-for-byte: 393 keys, revision 5.
- Live copy metadata was updated and read back successfully; existing overrides and revisions were preserved.
- Copy-tool tests, type checks, production build, and offline signing/placeholder checks passed.

## Before distributing the APK

- [ ] Install the signed release on the physical Honor phone. No device was connected during this pass.
- [ ] Review Home, onboarding, Settings, History, the model dropdowns, and the overlay in light/dark themes and large text.
- [ ] Check bubble taps, drag, dismiss, restore, recording waveform, and disabled system animations with a real finger.
- [ ] Dictate into three supported apps with the optimized build. Check cleanup on/off, clipboard fallback, sensitive-field refusal, and failure/retry.
- [ ] Test model refresh with each configured provider key, no network, and an unavailable model. Check custom endpoint refresh before recording.
- [ ] Confirm background survival and accessibility recovery on the target OEM.

## Before Google Play submission

- [ ] Complete and host the [privacy policy](privacy-policy.md) and [terms](terms.md). Both still contain publisher/contact placeholders.
- [ ] Set the real hosted addresses in `LegalLinks.kt`. Both URLs are currently empty.
- [ ] Update the policy and Data Safety answers against current code, including optional contacts vocabulary, direct provider calls, model-list requests, and automatic signed-copy fetches. Do not reuse historical claims that all data stays on-device.
- [ ] Record the disclosure and accessibility demo using [the declaration draft](accessibility-declaration.md).
- [ ] Complete accessibility, foreground-service, Data Safety, content-rating, and audience forms in Play Console.
- [ ] Prepare store screenshots, feature graphic, icon, and [listing copy](store-listing.md).
- [ ] Confirm version code 1 has not already been uploaded; increment it if necessary.
- [ ] Upload the signed AAB to internal testing and review the pre-launch report before production rollout.

## Release scope

Android is the only active release. Windows, macOS, and iOS source remains on the local machine and in Git history, but is removed from the tracked release tree. Repository changes have not been pushed to GitHub during this pass.

The copy editor only stages revisions. Publishing remains a separate manual GitHub workflow; no copy update was published during this work. The full device/submission gate is still open.
