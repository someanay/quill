# Contributing

Quill's original source is proprietary and available for viewing only under
[the source viewing terms](LICENSE). The development instructions below apply
only to copyright holders and contributors who have separate written permission
to build or modify the relevant material. This guide grants no additional rights.

Read the product requirements, architecture, milestone list, and testing plan before changing behavior. Keep each change scoped to one issue or milestone.

Run `./gradlew testDebugUnitTest lint assembleRelease` before submitting a change. Changes to overlays, accessibility, or recording also need a physical-device check. Include the device model, Android version, and result in the pull request.

For copy changes, run `./gradlew :app:generateCopyManifest` and include the updated manifest. Accessibility disclosures, permission rationales, privacy promises, and the key-required warning must remain binary-only.

Do not commit credentials, signing material, APKs, IDE settings, or generated reports. Use a concise Conventional Commit message and explain what changed and how you verified it.
