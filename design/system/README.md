Memix is a meme maker for photos and videos, built around sound. The look is **neutral chrome, one blue**: CapCut's layout and darkness with Adobe's untinted grays, a single sky-blue accent, and white for selection. The user's meme is always the most colorful thing on screen. Dark theme only.

## Why this palette

- **Neutral gray chrome.** Adobe moved Photoshop to a dark gray workspace because gray works like a mat around a framed picture and is neutral, so "the colors you see in an image are not affected by any other colors around it" ([CreativePro](https://creativepro.com/photoshop-cs6-beta-welcome-dark-side/)). Photo-editing UIs keep color only for meaning and let the chrome recede ([Darkroom guide](https://blakecrosley.com/guides/design/darkroom)). So every background, surface and divider here has zero tint.
- **Values from Spectrum.** `canvas`, `surface` and the disabled grays follow Adobe Spectrum's dark theme: background-base `#111111`, layer-1 `#1B1B1B`, gray-300 `#393939` ([Spectrum color aliases](https://opensource.adobe.com/spectrum-design-data/tokens/color-aliases/)).
- **CapCut's structure.** CapCut pairs deep charcoal backgrounds with high-contrast white text and a teal-cyan accent, with barely visible borders and elevation by value steps ([IMG.LY's CapCut-style theme](https://img.ly/blog/capcut-like-video-editor-web-react/), approx. `hsl(190 85% 48%)`). Memix keeps that structure: black stage behind the preview, white selection and playhead, flat surfaces.
- **Sky blue, between the two.** `primary` `#2BB3F3` is CapCut's cyan shifted 9° toward Spectrum's accent blue (`#4069FD`), into sky blue. It stays light enough that near-black text on it reaches 7.9:1.
- **Saturated color only in small areas.** Material's dark-theme guidance is to use desaturated colors on large dark surfaces and keep saturated color for small branded elements ([summary](https://blog.prototypr.io/how-to-design-a-dark-theme-for-your-android-app-3daeb264637)). Here: one primary button, the active tool, meme sounds, timeline clips.
- **Track colors follow editing conventions.** Clip type is coded by color as in pro editors (Final Cut Pro shows default video clips in blue and colors clips by role, [Apple](https://support.apple.com/guide/final-cut-pro/ver0753c4884/mac)). Audio is green, text orange, effects violet, stickers gold, video neutral; meme sounds take the brand blue.

## Content fundamentals

- Speak as the product, to "you": "Your drafts", never "My drafts". No "I".
- Short, verb-first, sentence case: "Make a meme", "Drop a sound", "Remove background", "Make it a video meme", "Export".
- Uppercase only in Anton display styles (`display-xl`, `display`): "MAKE A MEME", "VIDEO MEME".
- Meme-literate, never mean. Light jokes in empty states only: "No drafts yet. Go make something unhinged."
- Errors say what happened and what to do: "That video won't open. Try another one."
- No emoji in the interface chrome. Users' memes can hold any emoji they like.
- Numbers as digits with units: "0:02", "1080p · 30 fps", "Est. 18 MB".

## Visual foundations

**The one rule: neutral chrome, one blue.**
- Chrome (every screen, sheet, bar and editor panel) uses only `canvas`, `surface`, `surface-raised`, `hairline` and the three text grays. No outlines around shapes, no decorative shadows, no gradients.
- `primary` sky blue marks exactly one main action per screen, the active tool, links, progress, and meme sounds everywhere they appear (play buttons, the meme sound track, the Meme sounds tool). Never two primary buttons on one screen. The Create button in the bottom navigation is the one global exception: it stays blue on every browse screen.
- `selection` white marks what is selected: clip and layer outlines, trim handles, the playhead, the selected chip. Selection never uses a hue, so it can't be confused with a clip type.
- Inside the editors the preview sits on `stage` black; panels below it are `canvas` and `surface`.

**Color.**
- Backgrounds: `canvas` for screens, `surface` for cards, sheets, nav and tool bar, `surface-raised` for controls on top of `surface`.
- Text: `text` first, `text-secondary` for supporting copy, `text-muted` for hints and timecodes. All three pass 4.5:1 on `canvas`, `surface` and `surface-raised`.
- `primary-subtle` (16% sky) backs the active tool, the playing sound row and sound play buttons; icons on it use `primary`.
- Track colors encode clip type on the timeline and in the layers panel, and nowhere else:

| Track | Token |
| --- | --- |
| Video and overlays | `track-video` (thumbnails; labels in `text`) |
| Text | `track-text` (orange) |
| Stickers, images, cut-outs | `track-sticker` (gold) |
| Meme sounds | `track-meme-sound` (Memix blue) |
| Audio (original, imported, voiceover) | `track-audio` (green) |
| Effects and filters | `track-effect` (violet) |

  Labels on every colored fill use `on-track`.
- `danger` and `success` always come with a word or an icon; never rely on hue.

**Type.**
- Anton (`display-xl`, `display`) only for the wordmark, the Home hero and entry cards; `meme-caption` is the default caption in both editors: white fill with a 3px black stroke.
- Space Grotesk for everything else in the interface: `title-l` screen titles, `title` sheet titles, `body` and `body-strong` rows, `label` chips and tabs, `caption` tool bar labels and metadata.
- Space Mono `timecode` for playback time, ruler marks and durations.
- All three are SIL OFL. The Compose app bundles the font files; these previews load them from Google Fonts.

**Spacing and layout.**
- 4px grid. Side gutter `space-4`; between sections `space-6`; card and sheet padding `space-4`.
- Designed for phones 360 to 430 dp wide. Bottom navigation `nav-height`; editor tool bar `toolbar-height`.
- Every control is at least `touch-target`.

**Radii, borders, shadows.**
- `radius-sm` timeline clips and badges, `radius-md` buttons, inputs and tool highlights, `radius-lg` cards and sheets, `radius-full` chips, segmented tabs and play buttons.
- Borders are `stroke-hairline` in `hairline`, used for dividers and the search field only. Depth comes from value steps, not lines or shadows.
- Bottom sheets use `shadow-sheet` over `scrim`; floating menus and toasts use `shadow-float`.

**States.**
- Pressed: `primary` fills switch to `primary-pressed`; neutral controls go one value step lighter (`surface-raised` → `hairline`). Nothing moves.
- Selected clip or layer: `stroke-selection` outline in `selection` plus white trim handles.
- Selected chip: `selection` fill with `on-primary` text. Selected tab: `text` label with a 2px `selection` underline. Selected segment: `surface-raised` pill with `text`.
- Active tool: `primary` icon and label on `primary-subtle`.
- Disabled: `text-muted` label. Prefer keeping controls enabled and explaining on tap.
- Focus: `stroke-focus` ring in `focus-ring`, 2px outside the control.

**Motion.**
- Durations: 120 ms for presses and toggles, 200 ms for sheets and tab changes, 320 ms for screen transitions.
- The "bonk": a sound or sticker landing on the timeline scales 1 → 1.12 → 1 over 240 ms with a slight overshoot. Use it only for placing content.
- Respect the system's reduce-motion setting: replace movement with a 120 ms fade.

**Imagery.** The user's content is the imagery. Template thumbnails are 9:16 in `radius-lg` frames. No stock illustrations or mascots.

## Iconography

One outline icon family at 24px with 1.75px strokes and round joins, colored `text-secondary` (inactive), `text` (current nav item) or `primary` (active tool). Proposed set: Lucide (ISC license), imported into Compose as vector drawables. The previews here use simple hand-drawn stand-ins. There is no Memix logo yet: until one exists, write the name "Memix" in Anton.

## In Compose

Map each token to the Memix theme objects, kebab-case to camelCase: `track-meme-sound` → `MemixColors.trackMemeSound`, `body-strong` → `MemixType.bodyStrong`, `space-4` → `MemixSpacing.space4` (dp), `radius-lg` → `MemixShapes.radiusLg`. Never hardcode a color, size or font in a screen; add a token here first.
