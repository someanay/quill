# Quill

**Source viewing only.** Quill's original source is proprietary and all rights
are reserved. You may read and inspect it; building, running, modifying or
redistributing it requires written permission from the applicable copyright
holder. GitHub's platform rights and third-party licenses are preserved.
See [the source viewing terms](LICENSE).

Voice dictation with your own API key. Use a keyboard shortcut on Windows and Mac,
the floating control on Android, or dictate from the iOS keyboard during a timed session.

Recordings go directly to your chosen speech provider. Optional cleanup uses your chosen language model. There is no Quill account or server handling your recordings.

## Status

Windows, macOS, iPhone and iPad now use independent native layouts with Quill's
shared typography, colours, provider and privacy foundations. Desktop setup
teaches keyboard shortcuts and native permissions. iOS setup teaches the actual
timed host microphone session and installed-keyboard controls, with a required practice insertion.
The approved design pass covers setup, workspace, history and every relevant
settings page. See [the design review](docs/native-design-review.md) and
[platform experience](docs/platform-experience.md).

The iOS keyboard-session follow-up removes repeated app switching during an
explicitly enabled five-minute session. Native iPhone/iPad baseline/current tests,
installed-keyboard background dictation, both appearances and packages are verified.
See [the results and remaining device gates](docs/ios-keyboard-session.md).

The additional [cross-platform functionality pass](docs/native-functionality.md)
also passes Windows x64/ARM64 and all four Intel/Apple Silicon Mac configurations,
including real editor checks and a fresh local Windows microphone lifecycle check.

Windows 2.0.3 is installed normally on this workstation with a Start shortcut and
Installed apps entry. Native verification runs on Windows x64/ARM, macOS 15/26
on Intel/Apple Silicon, and iPhone/iPad simulators. Exact results and limitations
are recorded in [Windows verification](windows/VERIFICATION.md) and
[Apple validation](docs/apple-validation.md); an unsigned CI package is not a
public release sign-off.

Android version 1.0.10 builds as a signed APK and AAB; final device testing and Google Play submission are pending. The native Windows, Mac and iOS redesigns pass their complete available CI matrices, including baseline/current Apple runners, real Mac editors and installed iOS keyboards. Exact producing revisions and remaining physical-device, provider-account and distribution checks are recorded in [platform validation](docs/platform-rebuild.md) and [Android release readiness](docs/play-readiness.md).

## Android features

- Floating recording bubble, with clipboard fallback where direct insertion is unavailable.
- Provider keys encrypted with Android Keystore.
- Optional transcript cleanup, custom words, and local history.
- Automatic writing-style defaults for [540 app packages](docs/app-styles.md), with per-app overrides.
- Private mode and configurable history retention.
- Interface text bundled in the APK. All copy changes ship through app releases.

Android 10 or later and a supported provider API key are required. Provider usage may incur charges. Quill needs microphone, overlay, and accessibility permissions. Password fields are excluded; browser fields have additional restrictions. Compatibility varies by app—see the [device test matrix](testing_plan.md).

## Build

Open the project in Android Studio with Android SDK 37 installed. Use the checked-in wrapper:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lint
./gradlew assembleRelease bundleRelease
```

On Windows, use `gradlew.bat`. Release signing reads `QUILL_KEYSTORE_PATH`, `QUILL_KEYSTORE_PASSWORD`, `QUILL_KEY_ALIAS`, and `QUILL_KEY_PASSWORD` from local properties or environment variables. Without credentials, the release is unsigned. Keep signing keys and credentials out of Git.

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk`
- Play bundle: `app/build/outputs/bundle/release/app-release.aab`

Keep distributable copies in the ignored `dist/android/` directory, with SHA-256
checksums. APKs, app bundles, and installers do not belong in the source tree.

Test overlay gestures, accessibility insertion, and background recording on a physical Android device. OEM battery management can affect all three.

## Copy catalog

Interface text is loaded only from the installed APK. Quill does not check a copy
server, download text bundles, or apply old cached overrides. On upgrade it cancels
the retired background copy job. Provider calls still go directly to the user's
configured speech/cleanup providers.

Use the local Quill Copy Tool to save drafts and **Apply to app source**. Changes
update `app/src/main/assets/copy/strings.default.json` and `copy-manifest.json`.
All catalog strings are editable locally, including compliance text. Accessibility disclosure remains in Android XML resources.

Build both signed artifacts with `BUILD-ANDROID.cmd`, or:

```powershell
./scripts/build-android-release.ps1 -VersionCode 11 -VersionName 1.0.10
# To apply the copy tool's pending drafts before building:
./scripts/build-android-release.ps1 -ApplyCopy -VersionCode 12 -VersionName 1.0.11
```

The script validates copy, runs tests/lint, verifies signatures, and writes APK/AAB
and SHA-256 checksums into `dist/android/`. Signing credentials must already be
configured locally; the script fails if signing is unavailable. Upload the AAB to
Play Console using a version code higher than every previous upload. It does not
publish copy remotely, commit source, or upload to Play automatically.
## Project notes

- [Product requirements](prd.md)
- [Architecture](spec.md)
- [Milestones](todo.md)
- [Testing](testing_plan.md)
- [Contributing](CONTRIBUTING.md)

The planning documents include historical decisions; the release checklist records the current shipping scope.

Cleanup supports all five providers independently of transcription. Choose the cleanup provider/model under Settings → Clean up with AI; the selected provider needs its own saved key. Formal, Neutral and Casual styles apply through cleanup. A missing key, failed request or rejected rewrite preserves the original transcript.
