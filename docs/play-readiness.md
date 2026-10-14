# Android preview and release readiness

Updated October 14, 2026.

## Current preview

| Item | Value |
| --- | --- |
| App version | 1.0.22 |
| Version code | 23 |
| Android package | com.quill.quill |
| Minimum Android version | Android 10 / API 29 |
| Target / compile API | Target 36 / compile 37 |
| Bundled copy | Revision 24, 437 strings |
| Distribution | Signed GitHub preview APK |
| Source usage | Proprietary, viewing only; official app use has separate terms |

The [preview release](https://github.com/someanay/quill/releases/tag/android-v1.0.22) includes the signed APK and SHA-256 checksum. It is a preview, not a claim of completed physical-device testing or Google Play production approval.

The source on the default branch contains the current Android onboarding and package identity. The published APK was signed on October 8, 2026; the repository's publication, documentation and CI updates do not add app features to that binary.

## Automated verification

- 404 Android unit tests pass locally with no failures.
- Debug lint passes.
- Optimized APK and AAB generation, APK signature, 16 KB ZIP alignment and AAB signature checks passed for the preview.
- The APK package is com.quill.quill, version 1.0.22, code 23.
- The copy bundle and onboarding assets were checked in the packaged artifacts.
- The live hosted check is available from the [Android checks workflow](https://github.com/someanay/quill/actions/workflows/android.yml).

The hosted workflow installs the published Android 37.0 platform package and build tools 37.0.0. It uses JDK 21, a portable Gradle configuration and no release-signing secrets. Its CI outputs are verification builds, not the signed download offered to users.

## Physical-device checks still pending

The latest automated pass had no connected phone. Before a production rollout:

- [ ] Install the signed release on the physical Honor phone.
- [ ] Check the new onboarding video, provider Back/Skip flow, microphone meter and overlay instructions.
- [ ] Verify bubble dismissal/restore, all bubble sizes, orientation and gesture navigation with a real finger.
- [ ] Dictate into supported apps with cleanup on/off; verify actual insertion, clipboard fallback, sensitive-field refusal and retry.
- [ ] Check offline/provider-error handling, model refresh and encrypted key/draft persistence.
- [ ] Check aggressive-OEM background survival and accessibility recovery.
- [ ] Check large text, light/dark themes and reduced motion.

A passing hosted build does not establish these results. Use the [manual test plan](../testing_plan.md) and [key setup checks](key-setup.md).

## Before a Google Play production rollout

- [ ] Complete the physical checks above.
- [ ] Review the current [privacy policy](privacy-policy.md), [app terms](terms.md), actual provider behaviour and required declarations together.
- [ ] Use public policy URLs and enable the corresponding in-app legal links in the next app release. The 1.0.22 preview's About shortcuts predate these published documents.
- [ ] Complete accessibility, foreground-service, Data Safety, content-rating and audience forms.
- [ ] Upload the required accessibility demonstration and store artwork.
- [ ] Confirm the Play listing and install link are live before advertising store availability.
- [ ] Increase the version code for a new upload to the same package.
- [ ] Review the internal-test/pre-launch report before production rollout.

The package change creates a separate Android installation from the former com.tihor.quill app. Stored keys and permissions do not automatically migrate between those package identities.

## Retired plans and historical records

Remote copy delivery is retired. Current builds use bundled interface text only. Older notes about signed-copy downloads, their compliance allowlists and periodic copy checks are historical, not current behaviour.

Windows, macOS and iOS experiments retained on other branches or in Git history are not included in this Android preview announcement. Older planning documents can contain superseded versions and test counts; this record identifies the current preview status.
