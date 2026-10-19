# Apple validation — October 20, 2026

The owner authorized pushing the Apple changes and running native GitHub Actions
tests from the Windows workspace. Local Git write access was confirmed by a
successful push to `codex/apple-native-validation`. Windows/site working changes
remain outside that branch. The earlier Actions billing block is no longer
preventing Mac runners from starting.

Validation is in progress. This file will record the final tested revisions and
outcomes; do not treat an uploaded app or screenshot as a passing test result.

## Findings corrected during execution

- Mac Settings controls had duplicate accessibility identifiers after the desktop
  redesign. Navigation, toolbar and workspace actions now have distinct identifiers.
- Key drafts intentionally survive app restarts. Resize tests explicitly replace
  earlier synthetic input before checking that the same draft survives resizing.
- SwiftUI replaced the manually assigned Mac menu. App commands now belong to the
  SwiftUI scene so standard editing, Settings and Close shortcuts remain available.
- Native toolbar controls expose a wrapper and a real control. UI tests click the
  reachable control and still require actual navigation.
- The Actions display mode originally reverted when the configuration process
  exited. It now persists for the login session, enabling expanded-layout coverage.
- iOS Settings briefly retained dismissed keyboard rows with infinite frames.
  Integration tests reacquire a usable visible row before requesting a tap.

## Scope and remaining acceptance

The iOS integration uses a DEBUG synthetic provider response with real native
recording, Keychain, Settings, App Group transfer and the installed keyboard in a
separate app. It does not verify a live provider account. The iOS app retains its
existing explicit host-record → publish → return → insert workflow; a keyboard-start
and automatic-return redesign is still a separate feasibility task.

Real hardware remains necessary for microphone fidelity, interruptions/lock,
long-running dictation, wider third-party editor compatibility, login behavior
and physical-device protection. Distribution signing and notarization are separate
from these unsigned/ad hoc CI builds. Existing Android and Windows checks are
outside this Apple validation scope.
