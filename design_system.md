# Quill — Design System ("Ink & Signal")

> Compose/Material 3 handoff for `com.tihor.quill`. This is the **visual source of truth**, paired
> with `quill-mockups.html` (the rendered screens). Where this doc and the mockups agree, that's the
> target; where a CSS effect doesn't map cleanly to Compose, this doc's intent wins. Build the theme
> layer **first** (before M8 screens) so every screen is born on-design rather than re-skinned later.

---

## 0. How to read this doc

- **Colours are exact** (hex is hex — no scaling). Copy them verbatim.
- **Sizes are device-scale targets.** The mockup frames are drawn at ~0.8× of a real phone (a 300 px
  frame standing in for a ~360–412 dp device). So the `px` in the board's CSS are **reference, not
  literal dp**. This doc gives the **dp/sp values to use on device**, with the source px noted for
  traceability. Don't transcribe board px 1:1.
- **Two things are custom-drawn** and specified to the pixel because Material gives you no component
  for them: the **floating bubble** (§9) and the **ink-stroke level meter** (§10). Everything else is
  standard Material 3 re-themed.
- **Copy is never hardcoded in the UI** (§13) — it comes from the Copy Catalog. This doc governs how
  things *look*; the Catalog governs what they *say*.

---

## 1. Design thesis

**Ink & Signal.** A calm, editorial, voice-first tool — deliberately *not* the warm-cream/serif/
terracotta look. The world is **ink on cool paper**: a cool off-white surface, a deep **ink-indigo**
brand, near-black ink text. Exactly one warm accent — **gold** — and it means one thing only:
**"listening / live."** Gold is never decoration; if something is gold, Quill is actively hearing you.

Two signature elements carry the identity; everything else stays quiet:
1. The **ink-stroke level meter** — your voice drawn as gold nib-strokes.
2. The **contrast-safe bubble** — a single floating object that stays legible on *any* app behind it,
   and lights up with a gold halo while recording.

Kin to Wispr Flow (soft, rounded, voice-centric, friendly onboarding) — but ink & gilt, not warm &
coral. Material 3 structure throughout; the **serif wordmark** is the only editorial flourish.

---

## 2. Colour

### 2.1 Core tokens — Light (default)

| Token | Hex | Role |
|---|---|---|
| `board` | `#E6E8EF` | Behind device frames only (not an app surface) |
| `bg` | `#EFF1F6` | App background / window |
| `surface` | `#FFFFFF` | Cards, sheets, lists, fields |
| `surface-2` | `#F6F7FB` | Sunken/inset rows, code blocks, plain lead-icons |
| `line` | `#E3E5EE` | Hairlines, dividers, card borders |
| `line-strong` | `#D3D6E2` | Field borders, switch track (off), chip borders |
| `text` | `#1C1B33` | Primary ink text |
| `text-2` | `#4A4A5C` | Secondary text, body copy |
| `muted` | `#6E6E81` | Captions, subtitles, helper text |
| `faint` | `#9A9AAC` | Placeholders, disabled, tertiary icons |
| **`brand`** | `#4A3FD0` | **Ink-indigo** — primary fills (white text passes AA) |
| `brand-ink` | `#3A2EA3` | Brand as *text/icon* on light surfaces |
| `brand-soft` | `#ECEAFB` | Tinted brand fill (selected chips, lead-icons, info banner) |
| `on-brand` | `#FFFFFF` | Text/icon on a brand fill |
| **`gold`** | `#E0A03A` | **Signal** — live/listening fills, dots, halos |
| `gold-deep` | `#8A5A12` | Gold as *text* on light (contrast-safe) |
| `gold-soft` | `#FBEFD6` | Tinted gold fill (live pill, warn banner) |
| `ok` | `#2E9E6B` | Success |
| `ok-soft` | `#E1F3EA` | Success fill |
| `err` | `#D6453D` | Error / destructive |
| `err-soft` | `#FBE7E5` | Error fill |

### 2.2 Core tokens — Dark

Same token names, re-valued. The brand **lifts to lavender** for text/icons; gold **warms** slightly
for legibility on dark.

| Token | Hex |
|---|---|
| `bg` | `#0F1117` |
| `surface` | `#191B23` |
| `surface-2` | `#20232D` |
| `line` | `#2A2D38` |
| `line-strong` | `#363A48` |
| `text` | `#ECEDF3` |
| `text-2` | `#C3C5D2` |
| `muted` | `#9A9DAC` |
| `faint` | `#71748A` |
| `brand` (fills) | `#5A4FE0` |
| `brand-ink` (text/icon) | `#A79BFF` |
| `brand-soft` | `#26233F` |
| `on-brand` | `#FFFFFF` |
| `gold` | `#E8AE4E` |
| `gold-deep` (text) | `#F0C277` |
| `gold-soft` | `#332919` |
| `ok` / `ok-soft` | `#43B585` / `#16291F` |
| `err` / `err-soft` | `#E8635B` / `#2E1817` |

### 2.3 Mapping to Material 3 `ColorScheme`

M3 has slots for most of this. Map as below; the tokens M3 has **no** slot for (gold family,
brand-ink, the `-soft` tints) live in a small **extension** colour set (§14).

| M3 role | Light | Dark |
|---|---|---|
| `primary` | `#4A3FD0` | `#5A4FE0` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` |
| `primaryContainer` | `#ECEAFB` | `#26233F` |
| `onPrimaryContainer` | `#3A2EA3` | `#A79BFF` |
| `background` | `#EFF1F6` | `#0F1117` |
| `onBackground` | `#1C1B33` | `#ECEDF3` |
| `surface` | `#FFFFFF` | `#191B23` |
| `onSurface` | `#1C1B33` | `#ECEDF3` |
| `surfaceVariant` | `#F6F7FB` | `#20232D` |
| `onSurfaceVariant` | `#6E6E81` | `#9A9DAC` |
| `outline` | `#D3D6E2` | `#363A48` |
| `outlineVariant` | `#E3E5EE` | `#2A2D38` |
| `error` | `#D6453D` | `#E8635B` |
| `onError` | `#FFFFFF` | `#FFFFFF` |
| `errorContainer` | `#FBE7E5` | `#2E1817` |
| `tertiary` (→ gold) | `#E0A03A` | `#E8AE4E` |
| `tertiaryContainer` | `#FBEFD6` | `#332919` |
| `onTertiaryContainer` | `#8A5A12` | `#F0C277` |

> **Do not enable Material You / dynamic colour.** Quill's identity depends on the fixed ink-indigo +
> gold; wallpaper-derived colour would erase it. Hard-set the scheme.

### 2.4 Gradients (exact stops)

Used on the bubble, the wordmark mark, illustration cores, and meter strokes. All `150°` linear
(top-left → bottom-right) unless noted.

| Name | Stops | Where |
|---|---|---|
| Brand | `#4A3FD0 → #6A5BEA` | Bubble idle, wordmark mark, illustration core |
| Gold (live) | `#E0A03A → #EFC069` | Bubble recording, gold illustration core |
| Busy | `#5B5BD6 → #7E78E8` | Bubble transcribing |
| Error | `#D6453D → #E8635B` | Bubble error |
| Meter gold | `#E0A03A → #C9871F` (vertical) | Meter strokes while recording |
| Meter brand | `#4A3FD0 → #3A2EA3` (vertical) | Meter strokes (idle/brand contexts) |

### 2.5 Contrast notes (verified targets)

- White on `brand #4A3FD0` ≈ 7.3:1 — passes AA/AAA.
- `brand-ink #3A2EA3` text on white ≈ 7:1.
- `muted #6E6E81` on white ≈ 4.9:1 — AA for body.
- `gold #E0A03A` is **light** — never use it for text on light surfaces; use `gold-deep #8A5A12`
  for gold text. Gold is for fills/dots/halos.

---

## 3. Typography

### 3.1 Families

| Role | Family | Android |
|---|---|---|
| **UI / body / headings** | Humanist sans | **Inter** (bundle) or system default; weights 400/500/600/700 |
| **Wordmark only** | Editorial serif | A serif close to *Iowan Old Style / Palatino* — e.g. bundle **Lora** or **Source Serif 4**, weight 600. Used **only** for the "Quill" logotype, nowhere else. |

> The serif is the single editorial flourish (§1). If you reach for it anywhere but the wordmark,
> you've over-spent the boldness budget — stop.

### 3.2 Type scale (device-scale targets)

Anchored to Material 3 roles. `src` = the mockup class it derives from.

| M3 role | sp | Weight | Line height | Tracking | Colour | src | Used for |
|---|---|---|---|---|---|---|---|
| Display Small | 28 | 700 | 34 | −0.2 | text | `.h-lg` | Onboarding hero ("Dictate into any app you use") |
| Headline Small | 22 | 600 | 28 | 0 | text | `.h-md` | Screen titles in body, "First, a heads-up" |
| Title Large | 20 | 600 | 26 | +0.1 | text | `.appbar .ttl` | Top app-bar title |
| Title Medium | 17 | 600 | 22 | 0 | text | `.h-sm` | Card headers, key role ("Speech-to-text") |
| Body Large | 16 | 400 | 24 | 0 | text-2 | `.lead` | Primary body copy, paragraphs |
| Body Large (input) | 16 | 400 | 24 | 0 | text | `.field` | Text-field input (≥16sp to avoid zoom) |
| Label Large | 15 | 600 | 20 | +0.1 | — | `.btn` | Button labels, links |
| Body Medium | 14 | 400 | 20 | 0 | muted | `.small` | Helper text, subtitles, captions |
| List title | 16 | 500 | 22 | 0 | text | `.row .t` | List-row title |
| List subtitle | 13 | 400 | 18 | 0 | muted | `.row .s` | List-row subtitle |
| Label / chip | 14 | 500 | 18 | 0 | — | `.chip` | Chips |
| Label Small | 12 | 400 | 16 | 0 | faint | `.tiny` | Tertiary captions, version strings |
| Overline (eyebrow) | 12 | 700 | 16 | +1.5, ALL-CAPS | brand-ink | `.eyebrow` | Section eyebrows ("THREE STEPS") |
| Section header | 12 | 700 | 16 | +0.8, ALL-CAPS | muted | `.section-h` | Settings group headers |
| Nav label | 11 | 600 | 14 | 0 | — | `.navbar a` | Bottom-nav labels |
| Wordmark | 24 | 600 | — | 0 | text | `.lock .nm` | "Quill" logotype (serif) |

---

## 4. Shape & corner radii

Maps cleanly onto the M3 shape scale (dp).

| Token | dp | M3 shape | Applied to |
|---|---|---|---|
| `xs` | 8 | extraSmall | small inner chips/insets |
| `sm` | 12 | small | text fields, banners, toasts |
| `md` | 16 | medium | **cards, lists** (the default card radius) |
| `lg` | 20 | large | larger containers |
| `xl` | 28 | extraLarge | **dialogs, bottom sheets** |
| pill | 50% | full | buttons, chips, switches, status pills, the bubble |
| lead-icon | 10 | — | the 40 dp leading-icon squares in rows |

---

## 5. Spacing & layout

8 dp grid; the recurring values are **4 / 8 / 12 / 14 / 16 / 18 / 20 / 24**.

- **Screen horizontal padding:** 16 dp (`.screen` = 16 px → 16 dp).
- **Section spacing:** group header has ~18 dp top, 8 dp bottom before its list.
- **Card padding:** 16 dp.
- **Stack gaps:** 12 dp default (`.stack`), 8 dp tight (`.stack-sm`).
- **List rows:** 14 dp vertical / 16 dp horizontal padding; 12–13 dp gap between leading icon and text.
- Centered/empty screens: content max-width ~230 dp for body copy, centred.

**Screen scaffold (every screen):**
```
Scaffold
├─ (status bar — system)
├─ TopAppBar           // back chevron + title; or wordmark lockup on Home
├─ content (scroll)    // 16dp h-padding; sections, cards, lists
└─ NavigationBar       // Home screens only: Home / History / Settings
```

---

## 6. Elevation & shadows

Use M3 tonal elevation for surfaces; reserve real shadow for things that *float*.

| Element | Treatment |
|---|---|
| Cards, lists | Level 1 — 1 dp border (`line`) + a whisper shadow. `sh-1` ≈ `y1 blur2 rgba(28,27,51,.06)`. |
| Sheets, dialogs, menus | `sh-pop` ≈ `y18 blur40 rgba(28,27,51,.22)` (dark: heavier, on black). |
| **Bubble** | Custom — see §9. Always carries its white ring + drop shadow so it floats over any app. |
| FAB / overlays | `sh-2` ≈ `y6 blur18 rgba(28,27,51,.10)`. |

In **dark**, shadows are near-useless on near-black; lean on tonal elevation and the `line`/`line-strong`
borders for separation. The bubble keeps its shadow regardless (it floats over arbitrary apps).

---

## 7. Iconography

**Style:** single-weight line icons — 24 dp grid, **stroke ~1.8–2 dp**, round caps & joins, `currentColor`,
no fill (a few are filled: the overlay dot, the recording dot). This matches **Material Symbols —
Rounded** almost exactly; use that set (or ship the board's SVGs as vector drawables). Default render
size **20 dp**; in buttons **18 dp**; in the bubble **26–28 dp**; in lead-icons **20 dp**.

| Glyph (board id) | Meaning | Material Symbol (Rounded) |
|---|---|---|
| `i-nib` | Quill brand mark | (custom — keep the nib) |
| `i-mic` | Microphone / record | `mic` |
| `i-wave` | Voice / dictation / speech | `graphic_eq` |
| `i-key` | API key | `key` |
| `i-spark` | AI cleanup | `auto_awesome` |
| `i-overlay` | Floating bubble / display-over | `picture_in_picture` |
| `i-acc` | Accessibility / type-into-apps | `accessibility_new` |
| `i-shield` | Privacy | `shield` |
| `i-lock` | Encrypted / password field | `lock` |
| `i-gear` | Settings | `settings` |
| `i-clock` | History | `history` |
| `i-home` | Home | `home` |
| `i-globe` | Language / base URL | `language` |
| `i-layers` | Advanced | `layers` |
| `i-info` | Info / tip | `info` |
| `i-alert` | Warning / error | `warning` / `error` |
| `i-wifi-off` | Offline | `wifi_off` |
| `i-check` | Success / valid | `check` |
| `i-x` | Close / negative | `close` |
| `i-chev` / `i-back` | Forward / back | `chevron_right` / `chevron_left` |
| `i-copy` `i-share` `i-trash` `i-plus` `i-eye` `i-restart` `i-power` | copy / share / delete / add / reveal / reset / on-off | `content_copy` `share` `delete` `add` `visibility` `restart_alt` `power_settings_new` |

---

## 8. Components

dp values are device-scale; states reference §2 tokens. All interactive elements meet **≥48 dp touch
target** (§12) even when the visual is smaller.

### 8.1 Top app bar
M3 `TopAppBar`. Leading: 36–40 dp circular icon-button (back chevron), `text-2`. Title: Title Large.
On **Home**, replace the title with the **wordmark lockup**: a 34 dp rounded-square brand mark
(brand gradient, nib glyph) + "Quill" in serif. Trailing: settings/overflow icon-button.

### 8.2 Buttons
Fully-pill (`height/2` radius). Height **52 dp** (board 46 px), horizontal padding 20 dp, label Label
Large (15 sp / 600), 8 dp gap to a leading icon (18 dp).

| Variant | Container | Label/icon | Use |
|---|---|---|---|
| **Primary** (`Button`) | `brand` fill + sh-1 | `on-brand` | The one main action per screen |
| **Tonal** (`FilledTonalButton`) | `brand-soft` | `brand-ink` | Secondary ("Add a key", "Try a dictation") |
| **Ghost** (`OutlinedButton`) | transparent, 1 dp `line-strong` border | `brand-ink` | Tertiary ("Not now", "Cancel") |
| **Danger** | `err` fill | white | Destructive confirm ("Delete all") |
| **Text/link** (`TextButton`) | — | `brand-ink`, 600 | Inline links ("Where do I get a key?") |
| Small | height 38 dp, 13 sp | — | dense contexts |

### 8.3 Text field
M3 `OutlinedTextField`, radius **12 dp**. Default border 1.5 dp `line-strong`; **focus** 2 dp `brand`
+ a 3 dp `brand-soft` focus ring; **error** 2 dp `err` + 3 dp `err-soft` ring + `i-alert` trailing.
Optional leading icon 18 dp `faint`. Field min-height ~52 dp. Input text 16 sp. Above it: a 12 sp/600
`text-2` **label** (`.flabel-i`); below it: 11.5 sp `muted` **helper** (`.help`), or `err` when bad.
Masked secrets render in a **monospace** face with `+0.5` tracking.

### 8.4 Cards
`surface` fill, 1 dp `line` border, **16 dp** radius, level-1 shadow, **16 dp** padding. The API-keys
card pattern (§ mockups C3): header row (40 dp lead-icon + Title Medium role + subtitle + status pill)
→ a field → an action row. The card is the repeatable unit; stack with 12 dp gaps.

### 8.5 List & rows
`list` = `surface` container, 1 dp `line`, **16 dp** radius, clipped; rows divided by 1 dp `line`
(no divider above the first). Row: min-height **56 dp**, 14/16 dp padding. Anatomy:
**leading icon** (40 dp rounded-square, radius 10 dp) · **text block** (List title 16/500 + optional
subtitle 13 `muted`) · **trailing** (value text `muted`, or chevron `faint`, or a switch).
Leading-icon variants: default `brand-soft`/`brand-ink`; `.gold` = `gold-soft`/`gold-deep`;
`.plain` = `surface-2`/`text-2`.

### 8.6 Switch
M3 `Switch`. Off: track `line-strong`, white thumb. On: track `brand`, white thumb. (Board: 46×27
with a 19 dp thumb → use the standard M3 switch sizing.)

### 8.7 Chips (selectable)
M3 `FilterChip`, pill, height **32 dp**, 14 sp/500. Unselected: `surface` + 1 dp `line-strong` border,
`text-2`. Selected: `brand-soft` fill, no border, `brand-ink`, optional leading `check` (14 dp).
Used for provider/model/size/theme pickers.

### 8.8 Section header
12 sp/700 ALL-CAPS, `muted`, `+0.8` tracking, ~18 dp above / 8 dp below. Groups settings lists.

### 8.9 Banners / callouts
Rounded 12 dp tinted block, 13/14 dp padding, 19 dp leading icon, 12.5 sp text. Optional bold title
(`.bt` 680) + body (`.bd`). Variants by `-soft` fill + matching text colour: **info** (`brand-soft`/
`brand-ink`), **ok** (`ok-soft`/`ok`), **warn** (`gold-soft`/`gold-deep`), **err** (`err-soft`/`err`).
Used for the encryption reassurance, the noisy-room caveat, the revocation alert, etc.

### 8.10 Status pills
Pill, 30 dp, 12.5 sp/650, 7 dp gap. **Live** (`livepill`): `gold-soft`/`gold-deep` + an 8 dp `gold`
dot — "Listening". **OK** (`okpill`): `ok-soft`/`ok` — "Valid"/"Granted". Compact variant (24 dp,
11 sp) sits inside fields/cards.

### 8.11 Bottom navigation
M3 `NavigationBar` on the three Home screens: **Home / History / Settings**. Selected item shows a
54×30 dp **pill indicator** in `brand-soft` behind the 22 dp icon; label 11 sp/600; selected colour
`brand-ink`, unselected `faint`.

### 8.12 Dialog & bottom sheet
**Dialog** (`AlertDialog`): `surface`, **28 dp** radius, ~86% width, 20 dp padding, `sh-pop`. Destructive
dialogs lead with a 42 dp `err-soft`/`err` icon tile. **Bottom sheet** (`ModalBottomSheet`): top corners
28 dp, 20 dp padding, a 34×4 dp `line-strong` grip. Used for item actions, the crash-report review, the
key-required warning.

### 8.13 Snackbar / toast
Dark pill (`#26242E`), white text, 14 dp radius, `sh-pop`, 18 dp leading icon (gold for the
sensitive-field notice). Use sparingly — e.g. "Dictation is off in password fields."

### 8.14 Slider
M3 `Slider`: 5 dp track `line-strong`, `brand` fill, 18 dp `brand` thumb with a 3 dp `surface` ring.
Used for the session-cap setting and as the warning's read-gate progress bar (gold fill there).

### 8.15 Progress dots (onboarding)
Row of 7 dp dots, `line-strong`; the active one is a 22 dp `brand` pill. Centred.

### 8.16 Permission illustration
A 118 dp circle with a radial `brand-soft` glow, a dashed 1.5 dp `line-strong` ring inset, and a 60 dp
rounded-square **core** (brand gradient, 30 dp white glyph) centred. `.gold` variant uses the gold
gradient; success uses an `ok` gradient + `check`. Used on permission/empty/success screens.

### 8.17 Empty / loading / error patterns
- **Empty:** the permission-illustration circle in a *muted* treatment (`surface-2` core, `faint`
  glyph) + Title Medium + a directing line + a tonal CTA. Copy invites action, never dead-ends
  ("No dictations yet → Try a dictation").
- **Loading:** the `busy` bubble spinner or an inline `brand-soft` card with a small spinner +
  "Checking your key…". Never block the whole screen.
- **Error:** `err` banner or the bubble error state. Errors **direct the fix**, never apologise
  ("That key was rejected (401). Check you copied it fully.").

---

## 9. SIGNATURE 1 — The floating bubble

The product's hero and the hardest visual requirement: it must stay legible **over any app** (light
or dark), so its contrast affordances are **baked in**, not theme-dependent. Drawn with `Canvas` in
a `WindowManager` overlay — not a standard component.

**Base geometry**
- Diameter **56 dp**, perfect circle.
- Glyph centred, **26–28 dp**, white.
- **White contrast ring:** a `2.5 dp` ring at `rgba(255,255,255,0.92)` hugging the circle edge — this
  is what guarantees legibility on dark host apps.
- **Drop shadow:** soft, `y10 blur24 rgba(28,27,51,0.34)` — guarantees legibility on light host apps.
- Snaps to the nearer screen edge; rests ~⅓ down the right edge by default.

**States** (fill = the §2.4 gradients; ring + shadow constant unless noted):

| State | Fill | Glyph | Extra |
|---|---|---|---|
| **Idle** | Brand `#4A3FD0→#6A5BEA` | `mic` | base ring + shadow |
| **Recording** | Gold `#E0A03A→#EFC069` | `mic` | **+ gold halo** `0 0 0 9dp rgba(224,160,58,.22)`; gentle pulse |
| **Transcribing** | Busy `#5B5BD6→#7E78E8` | spinner (24 dp, 3 dp ring, white top) | base ring |
| **Paste** | Brand | `content_copy` (the "P") | shown when SET_TEXT is rejected; waits for tap |
| **Error** | Error `#D6453D→#E8635B` | `error` | shadow `rgba(150,30,30,.30)` |

**Recording halo + pulse (exact):** the gold halo breathes between
`0 0 0 7dp rgba(224,160,58,.28)` and `0 0 0 13dp rgba(224,160,58,.10)`, **1.8 s** ease-in-out, looping.
**Honour reduced-motion** — when set, drop the pulse (and the spinner animation) to a static state.

> The recording state is the one place gold appears at full strength. It must read as "Quill is
> hearing you" at a glance, from across the screen, on any background.

**Companion live pill** (optional, beside the bubble while recording/transcribing): a small `surface`
card carrying the **ink-stroke meter** (§10) + an elapsed timer (`gold-deep`), or "Transcribing…"
(`brand-ink`), or the error/retry line. See mockups Section E.

---

## 10. SIGNATURE 2 — The ink-stroke level meter

Voice rendered as hand-inked nib strokes. A row of vertical rounded bars whose heights track live
mic amplitude. Appears in the **mic-test** screen (large) and beside the **recording bubble** (compact).

- **Bars:** width **4 dp** (large: 5 dp), corner radius 3 dp, gap **3 dp** (large: 4 dp), centred on a
  shared baseline (grow up & down from centre).
- **Height:** compact ~24 dp tall pill context; mic-test **64 dp**.
- **Colour:** while recording, **meter-gold** gradient (`#E0A03A→#C9871F`, vertical). Idle/brand
  contexts use **meter-brand** (`#4A3FD0→#3A2EA3`). Inactive bars rest at `line-strong`.
- **Count:** ~6 bars in the compact pill, ~13 across the mic-test meter.
- **Motion:** each bar's height = smoothed RMS of the current audio frame (the recorder already
  computes RMS for silence-detection — reuse it; don't add a second analyser). Animate height with a
  short spring/ease (~80–120 ms) so it feels inked, not jittery. Honour reduced-motion by damping to a
  steady low-amplitude state.

> This is the component that makes Quill *feel* like Quill. It's worth getting the easing right — too
> twitchy reads as a generic audio visualiser; smoothed strokes read as ink.

---

## 11. Motion

Quiet and purposeful. Spend animation on the two signatures; keep navigation standard M3.

| Motion | Spec |
|---|---|
| Bubble recording pulse | 1.8 s ease-in-out, looping (gold halo breathe) |
| Bubble transcribing spinner | 1 s linear rotation |
| Meter bars | ~80–120 ms height spring per frame |
| Switch / chip / field focus | M3 defaults (~150–200 ms) |
| Screen transitions | M3 default container transforms; nothing custom |
| **Reduced motion** | All looping/auto animations (pulse, spinner, meter) collapse to static states. Respect the system setting globally. |

---

## 12. Accessibility & quality floor

Non-negotiable — these were design requirements from day one.

- **Touch targets ≥ 48 dp** for every interactive element, even where the visual is smaller (icon
  buttons, chips, the bubble's gesture area).
- **Contrast:** body text ≥ 4.5:1, large text/UI ≥ 3:1. Gold is fill-only on light (use `gold-deep`
  for gold text). The bubble's ring+shadow are the contrast guarantee over arbitrary apps — never
  remove them.
- **TalkBack / content descriptions:** every icon-only control is labelled by what it *does*
  ("Show bubble", "Start dictation", "Replace key"), pulled from the Copy Catalog. The bubble
  announces its state ("Listening", "Transcribing", "Tap to paste", "Couldn't transcribe").
- **Dynamic type:** use `sp` for all text; layouts must reflow at larger font scales (don't pin
  heights that clip text).
- **Dark mode:** first-class, not an afterthought — every screen ships both themes (§2.2). Test the
  bubble's legibility on both a light *and* a dark host app (the mockups' contrast-proof frame).
- **Focus order & visible focus** for keyboard/switch-access users.

---

## 13. Copy & the theming boundary

The design system governs **how things look**; it never hardcodes **what they say**.

- No string literals in composables. All copy is read by key from the **Copy Catalog** (`spec.md`
  §2.3) via `CopyKeys` / `LocalCopy`. A label component takes a key, not a string.
- **Compliance copy is locked to the binary and never themed/soft-coded away:** the accessibility
  prominent-disclosure + consent text, permission rationales, privacy/data-safety promises, and the
  BYOK key-required warning. Style them with these tokens, but their *wording* is fixed and
  non-overridable.
- Voice & tone (mirrors the mockups): plain verbs, sentence case, active voice, errors that direct
  the fix, empty states that invite action. "Save changes", not "Submit".

---

## 14. Compose implementation starter

Theme layer to stand up **before M8 screens**. Four files + two custom composables.

### `ui/theme/Color.kt`
```kotlin
// Brand
val InkIndigo        = Color(0xFF4A3FD0)
val InkIndigoText    = Color(0xFF3A2EA3) // brand-ink (on light)
val InkIndigoSoft    = Color(0xFFECEAFB)
val InkIndigoDark    = Color(0xFF5A4FE0) // brand fill (dark)
val Lavender         = Color(0xFFA79BFF) // brand-ink (dark)
val InkIndigoSoftDk  = Color(0xFF26233F)
// Gold (signal)
val Gold             = Color(0xFFE0A03A)
val GoldDeep         = Color(0xFF8A5A12)
val GoldSoft         = Color(0xFFFBEFD6)
val GoldDark         = Color(0xFFE8AE4E)
val GoldDeepDark     = Color(0xFFF0C277)
val GoldSoftDark     = Color(0xFF332919)
// Ink / surface (light)
val Ink              = Color(0xFF1C1B33)
val Ink2             = Color(0xFF4A4A5C)
val Muted            = Color(0xFF6E6E81)
val Faint            = Color(0xFF9A9AAC)
val Paper            = Color(0xFFEFF1F6)
val PaperSurface     = Color(0xFFFFFFFF)
val PaperSunken      = Color(0xFFF6F7FB)
val Line             = Color(0xFFE3E5EE)
val LineStrong       = Color(0xFFD3D6E2)
// Ink / surface (dark)
val InkBgDark        = Color(0xFF0F1117)
val InkSurfaceDark   = Color(0xFF191B23)
val InkSunkenDark    = Color(0xFF20232D)
val LineDark         = Color(0xFF2A2D38)
val LineStrongDark   = Color(0xFF363A48)
val TextDark         = Color(0xFFECEDF3)
val Text2Dark        = Color(0xFFC3C5D2)
val MutedDark        = Color(0xFF9A9DAC)
val FaintDark        = Color(0xFF71748A)
// Semantic
val Ok = Color(0xFF2E9E6B); val OkSoft = Color(0xFFE1F3EA)
val Err = Color(0xFFD6453D); val ErrSoft = Color(0xFFFBE7E5)
val OkDark = Color(0xFF43B585); val OkSoftDark = Color(0xFF16291F)
val ErrDark = Color(0xFFE8635B); val ErrSoftDark = Color(0xFF2E1817)
```

### `ui/theme/QuillColors.kt` — extension colours M3 has no slot for
```kotlin
@Immutable
data class QuillColors(
    val brandInk: Color,      // brand as text/icon
    val brandSoft: Color,
    val gold: Color,
    val goldDeep: Color,
    val goldSoft: Color,
    val ok: Color, val okSoft: Color,
    val line: Color, val lineStrong: Color,
    val muted: Color, val faint: Color,
    val text2: Color,
)
val LocalQuillColors = staticCompositionLocalOf<QuillColors> { error("QuillColors not provided") }
val MaterialTheme.quill: QuillColors
    @Composable get() = LocalQuillColors.current
```

### `ui/theme/Theme.kt`
```kotlin
private val LightScheme = lightColorScheme(
    primary = InkIndigo, onPrimary = Color.White,
    primaryContainer = InkIndigoSoft, onPrimaryContainer = InkIndigoText,
    background = Paper, onBackground = Ink,
    surface = PaperSurface, onSurface = Ink,
    surfaceVariant = PaperSunken, onSurfaceVariant = Muted,
    outline = LineStrong, outlineVariant = Line,
    error = Err, onError = Color.White, errorContainer = ErrSoft,
    tertiary = Gold, tertiaryContainer = GoldSoft, onTertiaryContainer = GoldDeep,
)
private val DarkScheme = darkColorScheme(
    primary = InkIndigoDark, onPrimary = Color.White,
    primaryContainer = InkIndigoSoftDk, onPrimaryContainer = Lavender,
    background = InkBgDark, onBackground = TextDark,
    surface = InkSurfaceDark, onSurface = TextDark,
    surfaceVariant = InkSunkenDark, onSurfaceVariant = MutedDark,
    outline = LineStrongDark, outlineVariant = LineDark,
    error = ErrDark, onError = Color.White, errorContainer = ErrSoftDark,
    tertiary = GoldDark, tertiaryContainer = GoldSoftDark, onTertiaryContainer = GoldDeepDark,
)

@Composable
fun QuillTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme           // NB: no dynamicColor — see §2.3
    val ext = if (dark) QuillColors(Lavender, InkIndigoSoftDk, GoldDark, GoldDeepDark, GoldSoftDark,
                                    OkDark, OkSoftDark, LineDark, LineStrongDark, MutedDark, FaintDark, Text2Dark)
              else      QuillColors(InkIndigoText, InkIndigoSoft, Gold, GoldDeep, GoldSoft,
                                    Ok, OkSoft, Line, LineStrong, Muted, Faint, Ink2)
    CompositionLocalProvider(LocalQuillColors provides ext) {
        MaterialTheme(colorScheme = scheme, typography = QuillType, shapes = QuillShapes, content = content)
    }
}
```

### `ui/theme/Shape.kt`
```kotlin
val QuillShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),   // fields, banners
    medium     = RoundedCornerShape(16.dp),   // cards, lists
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),   // dialogs, sheets
)
```

### `ui/theme/Type.kt`
Build a `Typography` from §3.2 (UI sans for everything; the serif `FontFamily` is **only** wired into
the wordmark composable, not the type scale). Example anchors:
```kotlin
val QuillType = Typography(
    displaySmall  = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleMedium   = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge     = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge    = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelSmall    = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
)
// Overline/section-header & nav-label as named TextStyles or via labelSmall + ALL-CAPS + tracking.
```

### Custom composables (spec, not full code)
- `QuillBubble(state: BubbleState, modifier)` — `Canvas`-drawn per §9: 56 dp circle, gradient by
  state, the constant 2.5 dp white ring + drop shadow, gold halo + 1.8 s pulse on `Recording`
  (reduced-motion aware), glyph/spinner per state.
- `InkMeter(amplitude: Float, bars: Int, large: Boolean, recording: Boolean, modifier)` — per §10:
  rounded vertical strokes from a centre baseline, height = smoothed amplitude, gold/brand gradient,
  ~100 ms easing.
- `Wordmark()` — 34 dp brand-gradient rounded-square + nib glyph + "Quill" in the serif `FontFamily`
  (the only place the serif is used).

---

## 15. Screen map → mockup frames

Each screen's exact layout lives in `quill-mockups.html`, grouped A–F. Build against the frame:

- **A — Onboarding:** splash · welcome · key-required warning (3 s gate) · add key · verifying · key
  error · how-it-works · setup checklist · mic · mic-test (ink meter) · overlay · accessibility
  disclosure · accessibility enable · all set.
- **B — Home:** bubble-on · paused · dark.
- **C — Settings:** index · speech & model · **API keys (card / multi / empty)** · AI cleanup ·
  custom words · audio & recording · privacy · bubble appearance · advanced · about · dark.
- **D — History:** list · empty · item sheet · clear-all confirm.
- **E — Bubble states:** idle · recording · transcribing · tap-to-paste · error · dark-app contrast.
- **F — System states:** permission revoked · crash report · offline · sensitive-field blocked.

> Treat the board as **direction, not pixel-target** — Compose/M3 won't be a pixel clone (fonts and
> some effects differ). The identity (palette, the bubble, the meter, the layout language, M3
> structure) is what must carry over. Expect a round of "match this frame closer" once real screens
> sit beside the mockups.
