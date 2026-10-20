#!/bin/bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == macOS ]]
brew install switchaudio-osx
if ! SwitchAudioSource -a -t input | grep -q '^BlackHole 2ch$'; then
  brew install --cask blackhole-2ch
  sudo killall -9 coreaudiod || true
fi
for attempt in {1..20}; do
  if SwitchAudioSource -a -t input | grep -q '^BlackHole 2ch$'; then break; fi
  sleep 1
done
SwitchAudioSource -t input -s 'BlackHole 2ch'
SwitchAudioSource -t output -s 'BlackHole 2ch'
test "$(SwitchAudioSource -c -t input)" = 'BlackHole 2ch'
test "$(SwitchAudioSource -c -t output)" = 'BlackHole 2ch'
system_profiler SPAudioDataType -json > "$RUNNER_TEMP/quill-live-audio-devices.json"
