# Accessibility setup video guide

October 4, 2026 — M8 onboarding follow-up.

Quill's existing standalone accessibility disclosure remains the entry point for
onboarding, recovery and the re-enable notification. Agree starts the supplied
video in Android's native picture-in-picture window, then opens Accessibility
Settings in a separate task. Not now and Back on the disclosure open neither
Settings nor the video. The disclosure wording and service capabilities are unchanged.

## Bundled asset

`app/src/main/assets/onboarding/quill-accessibility-pip-60fps.mp4` is copied unchanged
from the supplied Downloads file. It is a silent, 838,736-byte MP4: H.264 Constrained
Baseline level 4.2, 720 × 1584 square pixels, 60 fps, 270 frames and 4.5 seconds.
The portrait clip depicts the Honor Settings route through downloaded accessibility
apps to Quill and its enable/Allow controls. Other manufacturers can label these
screens differently. No re-encoding or network download is performed.

Packaged/source SHA-256:
`4F1D9DF0EA6EC9B9734177DD07AFABB7A5C70D469450C64D3C3A97292AC52EBC`.

## Player and session behavior

- One Media3 player and texture surface remain composed across PiP transitions and
  resizing. Playback is muted, uses built-in repeat and preserves the 5:11 aspect
  ratio. The full-screen guide has a title, Settings button and Close guide action;
  PiP contains only the video. Android supplies the floating window controls.
- Entry waits for the first rendered frame while the disclosure Activity is resumed.
  Settings opens only after Android confirms PiP entry. Automatic PiP entry on Home
  is disabled. Native PiP adds no overlay permission or foreground service.
- The player keeps running while the Activity is STARTED, including PiP's paused
  Activity state. It pauses on stop and is released when its composition is disposed.
- A Settings observer closes the guide when Quill's accessibility service is enabled.
  Closing the PiP window uses Android's dismissal. Expanding it reveals the full-screen
  guide; its Settings button explicitly re-enters PiP and reopens Settings.
- Unsupported or disabled PiP, video failure and a five-second preparation/entry
  timeout fall back to normal Settings. Late callbacks cannot relaunch Settings after
  cancellation, failure or completion. Leaving the full-screen guide closes its session.
- Acceptance belongs only to this Activity session. Process recreation ends an active
  guide; a new setup request shows the full disclosure again. PiP size/orientation
  changes are handled without recreating the Activity or player.

Implementation follows Android's [PiP guidance](https://developer.android.com/develop/ui/views/picture-in-picture)
and [parameter API](https://developer.android.com/reference/android/app/PictureInPictureParams.Builder).

## Verification

Eight flow tests cover consent, foreground/first-frame gating, duplicate callbacks,
unsupported PiP, failure/timeout fallback, enabling the service, cancellation and
explicit re-entry. The existing disclosure tests still check the sole Settings
launch host and Android resource isolation. Representative video frames were inspected
and three complete software-decoded loops passed. Build, tests, lint and packaged-asset
verification passed: all 404 unit tests, debug build and lint (no errors). APK inspection
confirmed the unchanged asset and the manifest's PiP declaration. Physical PiP behavior
is not covered by these automated results.

Physical acceptance remains pending: no phone was connected during implementation.
Run on the Honor daily driver and a supported stock Android phone; record OS/device
and results. Use a test installation when disabling accessibility, and do not clear
the daily driver's data.

1. Reach the disclosure from onboarding. Choose Not now, then repeat with Back:
   no Settings or PiP. Repeat from recovery and the re-enable notification.
2. Agree: Settings opens with the portrait video floating above it. Watch at least
   three repeats for black flashes, extra pauses, dropped frames and readable framing.
   Move, resize and close the window using Android's controls; closing must leave
   Settings usable. Verify the next request shows the disclosure again.
3. Expand PiP and use Open Accessibility settings: the same player returns to PiP,
   Settings opens once, and no duplicate player appears. Expand and choose Close guide
   or Back: the session ends. Rotate during preparation, PiP and expansion.
4. Enable Quill and accept the Android confirmation: the guide closes promptly.
   Return to onboarding and confirm its existing permission check advances normally.
   Returning without enabling must not silently reopen Settings; close the guide or
   expand and retry explicitly.
5. Disable Quill's PiP permission and repeat Agree: normal Accessibility Settings opens
   with no blocked setup or crash. Also check a supported device without PiP support
   if available. Restore the PiP permission after the test.
6. Background or lock during preparation; no delayed Settings launch should interrupt
   another app. In PiP, verify visible playback continues, hidden playback pauses,
   and resources are released after dismissal. Kill the process during the guide:
   restoration must not reuse consent or open Settings; a new request starts at disclosure.
