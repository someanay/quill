#!/bin/bash
set -euo pipefail

# Never change a developer's desktop. This workaround belongs only to disposable
# GitHub Intel macOS 26 VMs, whose IconServices agent crash-loops on a Metal assert.
# https://github.com/actions/runner-images/issues/14751
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_OS:-}" != macOS ]]; then
  echo 'This desktop preparation is restricted to isolated GitHub Mac runners.' >&2
  exit 78
fi
if [[ "$(uname -m)" != x86_64 || "$(sw_vers -productVersion | cut -d. -f1)" != 26 ]]; then
  echo 'IconServices workaround does not apply to this runner.'
  exit 0
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
evidence="$script_dir/../IconServicesEvidence"
mkdir -p "$evidence"
service="gui/$(id -u)/com.apple.iconservices.iconservicesagent"
launchctl print "$service" > "$evidence/before.txt" 2>&1 || true
if grep -Eq 'successive crashes = [1-9]|last terminating signal = (Abort|SIGABRT)' "$evidence/before.txt"; then
  echo 'Applying the documented Intel runner icon-service workaround; file icons use AppKit fallback.'
  # bootout can report Bad request when the crash-looping agent is between spawns.
  launchctl bootout "$service" > "$evidence/bootout.txt" 2>&1 || true
  launchctl disable "$service"
  if launchctl print "$service" > "$evidence/after.txt" 2>&1; then
    echo 'The crash-looping agent remains loaded.' >&2
    exit 1
  fi
else
  echo 'No IconServices crash loop reported; keeping the service intact.'
fi

probe_dir="$(mktemp -d "${TMPDIR:-/tmp}/quill-icon-probe.XXXXXX")"
swiftc "$script_dir/IconServicesProbe.swift" -o "$probe_dir/IconServicesProbe"
"$probe_dir/IconServicesProbe" 2>&1 | tee "$evidence/probe.log"
