# Quill for Android

Voice dictation with your own API key. Tap the floating bubble, speak, and Quill inserts the transcript into a supported text field without switching keyboards.

Recordings go directly to your chosen speech provider. Optional cleanup uses your chosen language model. There is no Quill account or server handling your recordings.

## Status

Android is the only release target. Version 1.0.2 builds as a signed APK; final device testing and Google Play submission are pending. See [release readiness](docs/play-readiness.md).

## Features

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
./scripts/build-android-release.ps1 -VersionCode 3 -VersionName 1.0.2
# To apply the copy tool's pending drafts before building:
./scripts/build-android-release.ps1 -ApplyCopy -VersionCode 4 -VersionName 1.0.3
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
