# Quill — Demo-video carousel content spec

How to record and drop in the demo clips shown by the reusable `DemoCarousel` (Home + two onboarding
steps). Get these right and the clips just work; the app scans the folders at runtime and needs no
code change per clip.

## Where clips go

One folder per surface, under `app/src/main/assets/`:

| Surface | Folder |
|---|---|
| Home ("See Quill in action" section) | `assets/carousel/home/` |
| Onboarding — accessibility step | `assets/carousel/accessibility/` |
| Onboarding — API-key step | `assets/carousel/key/` |

- Name files `01.mp4`, `02.mp4`, `03.mp4`, … — **sorted by filename**, so zero-pad to keep order.
- **Max 6 clips per folder** (extra files past the sort's first six are ignored).
- Non-`.mp4` files are ignored (the `.gitkeep` placeholder in each folder is harmless).
- A **missing or empty** folder makes the whole section (header included) render nothing and reserve
  no space — this is the state we ship in until real clips exist. No placeholder art, no error.

## Encoding requirements

| Property | Value |
|---|---|
| Container / codec | MP4, H.264 (AVC) |
| Resolution | **720 × 1280** (9:16 portrait) |
| Frame rate | 24–30 fps |
| Bitrate | ~1.5 Mbps |
| Audio | **None** — strip the audio track entirely (clips always play muted) |
| Duration | **≤ 12 s** |
| Looping | Must loop cleanly (last frame → first frame with no visible jump) |
| First frame | Must be **representative** — it is used as the **poster** still on non-active pages and before playback starts |

### Why these matter

- **First frame = poster.** Non-active carousel pages, and any page under reduced-motion (animations
  off), show the clip's first frame decoded via `MediaMetadataRetriever`. Start on a clean, on-brand
  frame — not a black frame or a mid-gesture blur.
- **No audio track.** Playback is always muted; shipping audio just wastes bytes and risks a codec
  edge case. Strip it.
- **9:16 at 720×1280.** Cards are 9:16; anything else letterboxes or crops. Keep text/subjects
  within a safe central area — Home cards are ~180 dp wide, onboarding cards ~124 dp wide.
- **Small + short.** Clips ship inside the APK. Keep each well under a couple of MB (≤ 12 s at
  ~1.5 Mbps ≈ ≤ 2.3 MB). Assets are stored uncompressed so `openFd`/ExoPlayer can read them directly.

### Example ffmpeg recipe

```bash
ffmpeg -i raw.mov \
  -vf "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,fps=30" \
  -c:v libx264 -profile:v high -b:v 1500k -pix_fmt yuv420p \
  -an \
  -movflags +faststart \
  -t 12 \
  01.mp4
```

## Optional captions

Each clip can carry a one-line caption via the Copy Catalog (overridable, so captions can be tweaked
or localised over the air). Keys are pre-registered per surface and **blank by default** — a blank
caption renders no caption row:

- `carousel.home.1.caption` … `carousel.home.6.caption`
- `carousel.accessibility.1.caption` … `carousel.accessibility.6.caption`
- `carousel.key.1.caption` … `carousel.key.6.caption`

The number matches the clip's sorted position (`01.mp4` → `.1.caption`). Set a default in
`app/src/main/assets/copy/strings.default.json` (or override via an OTA bundle). The caption also
becomes the card's TalkBack `contentDescription`; with no caption it falls back to `carousel.card.a11y`
("Demo video"). None of the carousel copy is compliance-locked.

The Home section header is `carousel.home.header` (default "See Quill in action"); onboarding
surfaces show no header.
