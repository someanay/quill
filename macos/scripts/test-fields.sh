#!/bin/bash
# Separate from the immutable TextEdit run. Neither probe changes TCC permissions.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_OS:-}" != macOS ]]; then
  echo 'The browser and secure-field probes require an isolated GitHub macOS runner.' >&2
  exit 64
fi
cd "$(dirname "$0")/.."
mkdir -p FieldEvidence
xcodegen generate
xcodebuild build -project QuillMac.xcodeproj -scheme QuillFieldProbe \
  -configuration Debug -destination 'platform=macOS' \
  -derivedDataPath FieldProbeBuild CODE_SIGNING_ALLOWED=NO \
  2>&1 | tee FieldEvidence/build.log
probe="$PWD/FieldProbeBuild/Build/Products/Debug/QuillFieldProbe"
fixture="$PWD/FieldProbeBuild/Build/Products/Debug/QuillAXFixture.app"
set +e
"$probe" safari "$PWD/FieldEvidence" "$fixture" 2>&1 | tee FieldEvidence/safari.log
safari_status=${PIPESTATUS[0]}
"$probe" fixture "$PWD/FieldEvidence" "$fixture" 2>&1 | tee FieldEvidence/fixture.log
fixture_status=${PIPESTATUS[0]}
set -e
if [[ "$safari_status" == 0 && "$fixture_status" == 0 ]]; then
  exit 0
fi
if [[ ( "$safari_status" == 0 || "$safari_status" == 78 ) && ( "$fixture_status" == 0 || "$fixture_status" == 78 ) ]]; then
  echo 'BLOCKED: at least one actual field probe lacks platform access.' >&2
  exit 78
fi
echo 'FAILED: see the separate Safari and native secure-field reports.' >&2
exit 1
