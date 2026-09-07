#!/bin/bash
# Isolated runner only: no TCC database edits, simulated access or production flags.
# Exit 0 = actual TextEdit checks passed; 78 = platform permission/state blocker;
# all other nonzero exits = build, test or evidence failure. The JSON is authoritative.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_OS:-}" != macOS ]]; then
  echo 'The TextEdit integration probe requires an isolated GitHub macOS runner.' >&2
  exit 64
fi
cd "$(dirname "$0")/.."
mkdir -p TextEditEvidence
xcodegen generate
xcodebuild build -project QuillMac.xcodeproj -scheme QuillAXProbe \
  -configuration Debug -destination 'platform=macOS' \
  -derivedDataPath AXProbeBuild CODE_SIGNING_ALLOWED=NO \
  2>&1 | tee TextEditEvidence/build.log
set +e
AXProbeBuild/Build/Products/Debug/QuillAXProbe "$PWD/TextEditEvidence" \
  2>&1 | tee TextEditEvidence/probe.log
probe_status=${PIPESTATUS[0]}
set -e
if [[ "$probe_status" == 78 ]]; then
  echo 'BLOCKED: real macOS Accessibility permission or isolated desktop state is unavailable.'
elif [[ "$probe_status" != 0 ]]; then
  echo 'FAILED: actual TextEdit integration did not satisfy its assertions.' >&2
fi
exit "$probe_status"
