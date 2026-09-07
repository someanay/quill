#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v xcodegen >/dev/null
swift test --package-path QuillCore
xcodegen generate
xcodebuild -project QuillMac.xcodeproj -scheme QuillMac -configuration Release \
  -destination 'generic/platform=macOS' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO ONLY_ACTIVE_ARCH=NO ARCHS='arm64 x86_64' build
mkdir -p ../dist/macos
app='build/Build/Products/Release/Quill.app'
if [[ -n "${QUILL_DEVELOPER_ID:-}" ]]; then
  codesign --force --options runtime --timestamp \
    --entitlements QuillMac/App/QuillMac.entitlements \
    --sign "$QUILL_DEVELOPER_ID" "$app"
  codesign --verify --deep --strict --verbose=2 "$app"
fi
ditto -c -k --keepParent "$app" ../dist/macos/Quill-macOS.zip
if [[ -n "${QUILL_NOTARY_PROFILE:-}" ]]; then
  xcrun notarytool submit ../dist/macos/Quill-macOS.zip --keychain-profile "$QUILL_NOTARY_PROFILE" --wait
  xcrun stapler staple "$app"
  ditto -c -k --keepParent "$app" ../dist/macos/Quill-macOS.zip
fi
shasum -a 256 ../dist/macos/Quill-macOS.zip > ../dist/macos/SHA256SUMS.txt
