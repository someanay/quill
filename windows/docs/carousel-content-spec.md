# Quill for Windows — landscape demo carousel

Windows uses separate desktop footage; do not reuse the Android 9:16 exports.

| Property | Delivery requirement |
|---|---|
| Container / codec | MP4, H.264 (AVC High), `yuv420p` |
| Resolution | **1280 × 720 (16:9 landscape)** |
| Frame rate | 30 fps |
| Video bitrate | approximately 2.5 Mbps |
| Audio | none |
| Duration | 12 seconds maximum |
| Loop | clean last-frame to first-frame transition |
| First frame | representative poster frame; never black |

Place zero-padded clips in one of these folders:

- Home: `src/Quill.Windows/Assets/Carousel/Home/01.mp4` … `06.mp4`
- Setup: `src/Quill.Windows/Assets/Carousel/Setup/01.mp4` … `06.mp4`

Files sort by name and each surface loads at most six clips. The carousel loops the active clip,
plays muted, and exposes previous/next controls. With no clips, it keeps the intentionally reserved
16:9 stage visible.

```bash
ffmpeg -i raw.mov -vf "scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,fps=30" \
  -c:v libx264 -profile:v high -b:v 2500k -pix_fmt yuv420p -an -movflags +faststart -t 12 01.mp4
```
