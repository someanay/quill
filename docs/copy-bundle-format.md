# Copy distribution

Remote copy distribution was retired on September 7, 2026. The former signed-bundle
format and publisher are historical; they are not supported by the Android app.

Use the local Copy Tool to apply edits to the Android source catalog. Run
`scripts/build-android-release.ps1` to validate, build and verify a signed APK/AAB.
Upload the AAB through Play Console. Every text change requires an app update.

The current schema is exported by `generateCopyManifest` into `copy-manifest.json`.
The editor uses it to validate keys, locked status, optional blanks, and formatting
placeholders. It stores only local drafts and rollback backups.
