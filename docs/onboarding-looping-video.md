# Quill onboarding video: handoff to the Android implementation AI

Integrate `final-quill-onboarding-1024-60fps.mp4` into the Android onboarding screen as an automatically playing, repeating video. Preserve its square aspect ratio and use the supplied asset without further transcoding. Follow the app's existing architecture and lifecycle handling.

## Asset specification

| Property | Value |
| --- | --- |
| File | `final-quill-onboarding-1024-60fps.mp4` |
| File size | 579,874 bytes (approximately 580 KB) |
| Duration | 9.5 seconds |
| Resolution | 1024 × 1024 pixels |
| Aspect ratio | 1:1, square sample pixels |
| Frame rate | 60 fps, constant |
| Frame count | 570 |
| Container | Standard, non-fragmented MP4 |
| Codec / tag | H.264 AVC / `avc1` |
| Profile / level | Constrained Baseline / 4.0 |
| Pixel format | `yuv420p`, 8-bit |
| Colour | Limited-range BT.709 |
| Scan | Progressive |
| Audio tracks | None |
| B-frames | None |
| Reference frames | One |

## Encoding details relevant to playback

- CAVLC entropy coding and a verified IDR keyframe at the start. This short clip uses a long GOP; repeating it returns to the initial IDR.
- Constant frame durations. The first PTS and DTS are zero, and PTS equals DTS for every packet.
- `+faststart`: the `moov` index precedes `mdat`. No edit list or negative timestamp preroll.
- Video-only stream: no audio, silent audio track, subtitles, or data track. There is no audio track to synchronize or mute.
- Metadata stripped.

H.264 Baseline in MP4 is broadly supported, but the target device must support this asset's resolution, frame rate, and codec level. Check the app's supported device range against [Android's media format documentation](https://developer.android.com/media/platform/supported-formats).

## Implementation requirements

1. Bundle the file with the app when practical, or fully cache it locally before playback. Avoid fetching it again on each loop.
2. Use the app's existing Media3 ExoPlayer integration where available. Create and prepare one player for the active onboarding screen, then use its built-in repeat mode.
3. Keep the player and playback surface stable while the screen is visible. Do not recreate them during recomposition, reload the media item on each repeat, or implement looping through an end-of-playback callback that restarts playback.
4. Display the video in a square region without stretching. The lower-phone crop is already included in the asset. Hide playback controls for this onboarding animation.
5. Pause when the onboarding screen is no longer visible or the app is backgrounded. Resume according to the app's onboarding behavior, and release the player when its owning screen or component is permanently disposed.
6. Do not add an audio track, soundtrack, or audio-clock workaround. This asset is intentionally video-only.

### Media3 setup

Apply this once to the player owned by the screen, using a URI for the bundled or cached asset:

```kotlin
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

player.setMediaItem(MediaItem.fromUri(videoUri))
player.repeatMode = Player.REPEAT_MODE_ONE
player.prepare()
player.playWhenReady = true
```

This is the media setup only; connect player ownership, the playback surface, visibility, and cleanup to the app's existing lifecycle. See [Media3 repeat modes](https://developer.android.com/media/media3/exoplayer/playlists).

## Loop behavior and acceptance checks

The animation intentionally resets from the completed message to the opening scene at the loop boundary. Preserve that edit. The goal is to avoid an extra playback pause or black flash during the reset. File settings alone cannot guarantee gapless playback on every Android device.

The asset passed full software decoding across three consecutive loops. Verification confirmed 570 frames, exact 9.5-second duration, constant timestamps, an initial IDR, no B-frames, no audio, a front-loaded index, and no edit list. Representative frames were visually inspected. Native Android hardware playback has not yet been tested.

Before completing integration, verify on the lowest-end supported Android device and a representative modern device:

- Several consecutive repeats without an extra pause, black flash, or surface reset.
- Smooth keyboard, waveform, label, and ripple motion; inspect dropped-frame diagnostics if playback appears uneven.
- Correct square framing, readable text, and expected colours.
- Correct pause/resume behavior on backgrounding and navigation.
- No duplicate players after recomposition or navigation, and proper resource release when leaving onboarding.

Report any device-specific playback limitation rather than silently changing the asset's resolution, frame rate, or edit.
