/* ============================================================================
   Quill — demo carousel configuration (single source of truth)

   Mirrors the two existing delivery specs in the codebase:

   - Android: docs/carousel-content-spec.md
       720 × 1280 (9:16 portrait), 24–30 fps, ≤ 12 s, muted, loops cleanly.
       Ordered by zero-padded filename, max 6 clips, first frame = poster.

   - Windows: windows/docs/carousel-content-spec.md
       1280 × 720 (16:9 landscape), 30 fps, ≤ 12 s, muted, loops cleanly.
       Ordered by zero-padded filename, max 6 clips.

   NOTE: the MP4 files are not recorded yet (the asset folders hold only
   `.gitkeep`). Rather than invent placeholder videos, each slide is a live,
   faithful reconstruction of the actual Quill UI for that moment of the
   product story. When real footage lands, drop the files in the asset folders
   and point `src` at them — the carousel is already aspect-ratio correct and
   auto-advance/timer behavior is unchanged.
   ============================================================================ */

window.QuillDemos = {
  // --- delivery-spec constants (the codebase source of truth) ---
  ANDROID: {
    resolution: "720 × 1280",
    aspect: "9:16",
    orientation: "portrait",
    fps: "24–30",
    maxDurationSec: 12,
    maxClips: 6,
    muted: true,
    loops: true,
    slides: [
      {
        id: "android-bubble-idle",
        scene: "android-bubble-idle",
        title: "A bubble over every app",
        description:
          "Quill floats wherever you type — chat, email, search, notes. Tap to start.",
      },
      {
        id: "android-listening",
        scene: "android-listening",
        title: "Tap, then speak",
        description:
          "The bubble turns gold while listening. The ink meter draws your voice.",
      },
      {
        id: "android-transcribing",
        scene: "android-transcribing",
        title: "Your provider transcribes",
        description:
          "Audio goes straight to your own key — no middleman, no account.",
      },
      {
        id: "android-inserted",
        scene: "android-inserted",
        title: "Words land at the cursor",
        description:
          "The transcript drops right where you were typing. What you say is what gets typed.",
      },
    ],
  },

  WINDOWS: {
    resolution: "1280 × 720",
    aspect: "16:9",
    orientation: "landscape",
    fps: "30",
    maxDurationSec: 12,
    maxClips: 6,
    muted: true,
    loops: true,
    slides: [
      {
        id: "windows-ribbon",
        scene: "windows-ribbon",
        title: "Press a key, start talking",
        description:
          "Alt+Q summons a quiet dictation ribbon that never steals focus.",
      },
      {
        id: "windows-setup",
        scene: "windows-setup",
        title: "A guided first run",
        description:
          "Five steps: tour, provider & key, shortcut, privacy, first dictation.",
      },
      {
        id: "windows-typing",
        scene: "windows-typing",
        title: "Types into the focused window",
        description:
          "Unicode SendInput writes where your cursor is; the clipboard covers the rest.",
      },
      {
        id: "windows-private",
        scene: "windows-private",
        title: "Encrypted, by default",
        description:
          "Keys are isolated per provider and protected with Windows DPAPI.",
      },
    ],
  },

  // Combined phone + desktop reel for the single overview page. Interleaves the
  // Android (portrait) and Windows (landscape) scenes so one carousel shows the
  // product on both form factors; the carousel frame is aspect-ratio agnostic.
  ALL: {
    slides: [
      {
        id: "all-bubble",
        scene: "android-bubble-idle",
        title: "A bubble over every app",
        description:
          "On your phone, Quill floats wherever you type — chat, email, search, notes.",
      },
      {
        id: "all-listening",
        scene: "android-listening",
        title: "Tap, then speak",
        description:
          "The bubble turns gold while listening; the ink meter draws your voice.",
      },
      {
        id: "all-ribbon",
        scene: "windows-ribbon",
        title: "A keystroke on the desktop",
        description:
          "On your PC, a shortcut summons a quiet ribbon that never steals focus.",
      },
      {
        id: "all-typing",
        scene: "windows-typing",
        title: "Types where your cursor is",
        description:
          "Your words go straight into the focused window; the clipboard covers the rest.",
      },
      {
        id: "all-inserted",
        scene: "android-inserted",
        title: "Words land at the cursor",
        description:
          "The transcript drops right where you were typing. What you say is what gets typed.",
      },
      {
        id: "all-private",
        scene: "windows-private",
        title: "Encrypted, by default",
        description:
          "Keys are isolated per provider and sealed by the platform keystore.",
      },
    ],
  },
};

/* Which carousel config a page uses. */
window.QuillDemos.forPage = function (platform) {
  return window.QuillDemos[platform.toUpperCase()];
};
