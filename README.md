# Quill for Android

Voice dictation with your own API key. Tap the floating bubble, speak, and Quill inserts the transcript into a supported text field without switching keyboards.

Recordings go directly to your chosen speech provider. Optional cleanup uses your chosen language model. There is no Quill account or server handling your recordings.

## Status

Android is the only release target. Version 1.0.0 builds as a signed APK; final device testing and Google Play submission are pending. See [release readiness](docs/play-readiness.md).

## Features

- Floating recording bubble, with clipboard fallback where direct insertion is unavailable.
- Provider keys encrypted with Android Keystore.
- Optional transcript cleanup, custom words, and local history.
- Automatic writing-style defaults for [540 app packages](docs/app-styles.md), with per-app overrides.
- Private mode and configurable history retention.
- Signed updates to interface text. Permission disclosures and privacy promises stay in the APK.

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

Test overlay gestures, accessibility insertion, and background recording on a physical Android device. OEM battery management can affect all three.

## Copy catalog

Quill checks for signed copy updates every time it opens and approximately every six hours in the background. Checks wait for a network, and Android may defer them while idle. Verified updates apply automatically; offline use keeps the last verified copy or bundled defaults.

The app owns the copy schema and defaults. After changing them, run `./gradlew :app:generateCopyManifest`. The companion Quill Copy Tool imports `copy-manifest.json`. Unit tests detect stale exports and protect locked compliance strings. See [the bundle format](docs/copy-bundle-format.md).

## Project notes

- [Product requirements](prd.md)
- [Architecture](spec.md)
- [Milestones](todo.md)
- [Testing](testing_plan.md)
- [Contributing](CONTRIBUTING.md)

The planning documents include historical decisions; the release checklist records the current shipping scope.

Cleanup supports all five providers independently of transcription. Choose the cleanup provider/model under Settings → Clean up with AI; the selected provider needs its own saved key. Formal, Neutral and Casual styles apply through cleanup. A missing key, failed request or rejected rewrite preserves the original transcript.
