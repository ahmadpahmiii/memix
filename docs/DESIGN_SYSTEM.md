# Memix design system (v2)

Source of truth: **this file and [`design/tokens.json`](../design/tokens.json)** (owner decision, 8 Oct 2026). The "Memix" design-system artifact (https://claude.ai/artifact/7HzM44Ra1z4juDDr4m5kpy) and the "Memix App Screens" design canvas (https://claude.ai/artifact/9sJhkuQFurxbC7wRdpFfpB) are the original plan, kept for reference only; changes are not synced back to them. Machine-readable tokens: [`design/tokens.json`](../design/tokens.json). Screen references: [`design/screens/`](../design/screens/) (open any `.dc.html` in a browser). Component specs and CSS reference: [`design/system/components/`](../design/system/components/).

> v1 (yellow `#FFE14D` + pink `#FF4FA3`, black outlines, hard offset shadows) was rejected on 7 Oct 2026. Never reintroduce it.

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
- `primary-subtle` (16% sky) backs the active tool, sound play buttons and the "Add a meme sound" pill; icons and text on it use `primary`. It never fills a whole row: a playing sound row steps one value up instead (`surface` on `canvas`, `surface-raised` on `surface`), so the large area stays neutral and the filled blue play disc carries the state (P1-08 review).
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
- `danger` and `success` always come with a word or an icon; never rely on hue. They are status colors, not a second accent: `danger` marks only destructive actions (the Delete tool, Discard) and recording, never a main action, so blue stays the one accent.

**Type.**
- Anton (`display-xl`, `display`) only for the wordmark, the Home hero and entry cards; `meme-caption` is the default caption look (white fill, black stroke at font size ÷ 12) for interface samples. Inside the editors, captions are content: five caption fonts, sized by the canvas (see Tokens → Captions).
- Space Grotesk for everything else in the interface: `title-l` screen titles, `title` sheet titles, `body` and `body-strong` rows, `label` chips and tabs, `caption` tool bar labels and metadata.
- Space Mono `timecode` for playback time, ruler marks and durations.
- **Hindi:** Anton and Space Grotesk have no Devanagari. When the app language is Hindi, `display-xl` and `display` switch to **Teko Bold** (see Tokens → Type → Hindi display face). The `wordmark` stays Anton. Interface text falls back to the system Devanagari face.
- All four are SIL OFL. The Compose app bundles the font files; these previews load them from Google Fonts.

**Spacing and layout.**
- 4px grid. Side gutter `space-4`; between sections `space-6`; card and sheet padding `space-4`.
- Designed for phones 360 to 430 dp wide. Bottom navigation `nav-height`; editor tool bar `toolbar-height`.
- Every control is at least `touch-target`.

**Radii, borders, shadows.**
- `radius-sm` timeline clips and badges, `radius-md` buttons, inputs and tool highlights, `radius-lg` cards and sheets, `radius-full` chips, segmented tabs and play buttons.
- Borders are `stroke-hairline` in `hairline`, used for dividers and the search field only. Depth comes from value steps, not lines or shadows. Three exceptions, all so a mark can't vanish: a Swatch whose color is under 3:1 against `surface` (Black, "No outline") gets a `stroke-hairline` ring in `text-muted`; white selection marks drawn over the user's content get a `stroke-hairline` `on-primary` edge; and the **frame edge** in the video editor, a `stroke-hairline` line in `text-muted` just outside the canvas frame on the `stage` (6.9:1), shown only while the Canvas panel is open or a caption is being moved, sized or turned, because a black background is invisible on the black stage (P1-11). It never covers content and is never exported.
- Bottom sheets use `shadow-sheet` over `scrim`; floating menus and toasts use `shadow-float`.

**States.**
- Pressed: `primary` fills switch to `primary-pressed`; neutral controls go one value step lighter (`surface-raised` → `hairline`); `primary-subtle` fills (a sound's play disc, the "Add a meme sound" pill) turn `surface-raised`, with their `primary` icon and label staying (5.9:1). Nothing moves.
- Selected clip or layer: `stroke-selection` outline in `selection` plus white trim handles.
- Selected chip, segment (SegmentedChoice) and font tile (FontTile): `selection` fill with `on-primary` text. Selected swatch: a `stroke-selection` white ring 2px outside it. Selected tab: `text` label with a 2px `selection` underline. `surface-raised` never marks a selection (only 1.4:1 against `canvas`, under SC 1.4.11's 3:1); it's the pressed fill of unselected segments (P1-11 spec, P1-10 review).
- Active tool: `primary` icon and label on `primary-subtle`.
- Disabled: `text-muted` label. Prefer keeping controls enabled and explaining on tap.
- Progress: determinate only, a `primary` fill on a `rail-height` rail in `hairline`, always next to a count or percent in text. No looping indeterminate bars (MOTION 2); when the total is unknown, count items or show the amount done.
- Focus: `stroke-focus` ring in `focus-ring`, 2px outside the control.

**Motion.**
- Durations: 120 ms for presses and toggles, 200 ms for sheets and tab changes, 320 ms for screen transitions.
- The "bonk": a sound or sticker landing on the timeline scales 1 → 1.12 → 1 over 240 ms with a slight overshoot. Use it only for placing content.
  - On the timeline the item's height scales by 1.12, and its width grows by the same number of dp, or by 12% if that's less (a very narrow item). A square-ish item (a 1 s sound) looks the same either way; a 10 s clip swells about 2.4 dp at each end instead of 24 dp sweeping over its neighbors (P1-16 review).
- Respect the system's reduce-motion setting: replace movement with a 120 ms fade.
  - **On Android the fade shows as an instant change.** Memix reads reduce motion as "Remove animations" (animator duration scale 0), and Compose scales every animation by that same setting, so at 0 each one ends on the next frame (Compose `MotionDurationScale`; Guidance). Nothing moves, which is the point; the 120 ms fade itself is never seen. Accepted (P1-06 review).
  - **On iOS (P7) the fade is real:** the system's Reduce Motion doesn't shorten animations, so there every movement must actually become the 120 ms fade, as the specs say.

**Dials.** ENERGY 2 / RHYTHM 2 / MOTION 2.
- ENERGY 2: the chrome recedes, and Anton carries the loud moments (Home hero, entry cards, empty-state headlines, sheet heroes) so the app still says hello like a meme tool.
- RHYTHM 2: browse screens share one frame (title, controls, content) so tabs feel predictable; Home breaks it with the oversized hero and two unequal entry cards.
- MOTION 2: everything that moves is a state change (press, toggle, tab, sheet, screen) or content landing (the bonk). Nothing loops or moves on its own.

**Imagery.** The user's content is the imagery. Template thumbnails are 9:16 in `radius-lg` frames. No stock illustrations or mascots.

## Iconography

Memix draws its own icons (owner's decision, 7 Oct 2026; the proposed Lucide set was dropped because its thin, round-capped look is the default of AI-generated interfaces). Icons are drawn only when a ticket needs them, and each new one follows this grammar so the set reads as one family:

- **Grid:** 24px canvas, 2px padding, so shapes live in the central 20px.
- **Stroke:** 2px with flat (butt) caps and mitred joins; rectangles get a 1px corner radius. The flat ends echo Anton's blunt terminals and keep small shapes crisp on dark gray.
- **One solid core:** where the metaphor has a natural mass, that one part is filled: the door of Home, the cone of Sounds, the lens of Video, the hill of Photo, the tab of Drafts. Play and pause are all core. This is the set's signature, the same move as the solid blue Create block.
- **Arrowheads are always solid** (`undo`, `redo`, `reorder`, `move_here`, `replace`) and don't count as the core. Keep at least 1.4px of air between an arrowhead and any other solid part, so they stay apart at 2× density (P1-16 review).
- **Color:** a single color per icon, applied as a tint: `text-secondary` (inactive), `text` (current nav item), `primary` (active tool), `on-primary` on blue fills. No two-tone icons.
- **Meaning first:** every icon names a real action or place and always comes with a visible label or a screen-reader label. No sparkle, magic or robot glyphs.

**The set so far** (`MemixIcons`, 44 icons after P1-14; more listed below to draw with their tickets):
- Navigation and chrome: `home`, `templates`, `create` (plus; also Add media and every "add"), `sounds`, `drafts`, `arrow_back`, `chevron_right`, `close`, `globe`.
- Media and playback: `video`, `photo`, `play`, `pause`, `undo`, `redo`, `heart`, `heart_filled`.
- Editor tools (P1-06): `edit` (scissor blades, finger loops as the core), `split` (two outlined clips around a solid bar), `delete` (lid and handle strokes over a solid can), `duplicate` (open back outline behind a solid front clip), `reorder` (an arc with two solid arrowheads over two outlined clips).
- Sound tools (P1-08, approved in the P1-08 review):
  - `volume`: two stroked bars rising left to right (5 and 10px tall) and a solid block with 1px corners as the tallest (16px), with even 3.5px gaps. Distinct from `sounds` (the speaker cone with waves): `sounds` means "meme sounds", `volume` means "how loud".
  - `move_here`: a horizontal shaft with a solid arrowhead pointing right into a full-height vertical bar (the playhead), 2.5px apart. The bar is the destination, the same white line the user sees on the timeline.
- Clip tool (P1-16, approved in the P1-16 review):
  - `replace`: a solid clip in the center (7 × 4px, 1px corners) between two curved strokes. The upper one runs left to right into a solid arrowhead pointing down; the lower one runs right to left into a solid arrowhead pointing up. It's exactly 180° symmetric, a swap cycle around one clip, so it doesn't read as `reorder` (one arc over two outlined clips) or as `undo`/`redo` (one hooked arrow).
- Original audio (P1-09, approved in the P1-09 review):
  - `volume_off`: `sounds`' solid cone with a 6 × 6 cross of two strokes in place of the waves, centered on the cone's axis and 2.3px clear of its tip. It's the muted state of the timeline's original-audio toggle, whose "on" state is `sounds`.
  - `detach_audio`: a solid clip (16 × 8px, 1px corners) over a two-period wave stroke, 3px apart. The gap carries "detach". It doesn't collide with `split`, `duplicate` or `replace`, none of which has a wave.
- Text (P1-10, reviewed in the P1-10 review):
  - `text`: a "T" whose crossbar is the solid core (16 × 3.5px) over a 2px stem. Main tool bar, place 3.
  - `edit_text`: the same "T", smaller and to the left, beside a stroked text cursor (an I-beam with short serifs), 2px apart.
  - `style`: "Aa": a stroked "A" and a solid single-story "a" (the core). The two must keep at least 1.4px of air between them (review P2 moves the "A" left; as first built they came within 0.4px and fuse).
  - `position`: a solid 5 × 5px center square (the core) with four strokes out to four solid arrowheads.
  - `minus`, `plus`: one or two 14px strokes, no core, like `close`. Size steppers.
  - `rotate_left`, `rotate_right`: an open arc with a solid arrowhead; left is right mirrored.
  - `arrow`: a shaft into a solid arrowhead pointing right; one drawing turned four ways for the Move buttons.
  - `scale_rotate`: a curved stroke with a solid arrowhead at each end, drawn at `icon-small` in `on-primary` on the white handle. No core: it's a handle glyph, not a tool.
- Canvas (P1-11, approved in the P1-11 review):
  - `fit`: a stroked tall frame (12 × 20px outside, 1px corners) with a solid 8 × 6px block (the core) across its middle, touching the frame's inner sides: a wide clip shown whole inside a tall frame.
  - `fill`: the same 12 × 20px frame filled solid (the core, 1px corners), with four 2.5px strokes beyond its left and right sides at the frame's thirds, 1.5px clear of it: the cut-off edges.
  - `blur`: a solid 7px dot (the core) inside four 4px strokes at its top, bottom, left and right, 2.5px clear. Shown at `icon-small` inside the Blur swatch.
  - `ratio_custom`: a stroked 20 × 16px rectangle (1px corners) with a solid 8 × 8px square (1px corners) over its bottom-right corner, flush with the outer edge: a resize handle.
  - The ratio tiles' rectangles are drawn from the ratio (solid, 1px corners, long side 20px), not icon files (`RatioShape`).
- Drafts (P1-14, as built, for the design review): `more`: three solid 3px dots (circles) in a row at x = 5, 12 and 19 on the center line, 4px apart (all core, like play). The row's More options button tints it `text-secondary` (IconButton's `tint`).
- Export (P1-12, as built, approved in the P1-12 review; spec `docs/ux/specs/P1-12-export.md`):
  - `share`: Android's familiar three-node share glyph in the grammar: a solid 6px dot at the left middle (the core, the thing being shared) joined by two strokes to two stroked 6px rings at the top right and bottom right. Used at 20 dp in the Share button. P5-05's More tile reuses it.
  - `check`: one stroked check mark (short leg down-right, long leg up-right), no core, like `close`. For the export status pill (at `icon-small`), and P5-02's "No watermark on this export".
- Photo editor layers (P2-01, to draw with the ticket; specs `docs/ux/specs/P2-01-photo-editor-shell.md`; the engineer draws, the designer approves in the review):
  - `layers`: a solid rhombus (18 × 9px, the core) over two stroked chevrons (the layers beneath), each chevron the rhombus's lower edge shifted down 4.5px, so about 3px of air separates them. Distinct from `duplicate` (rectangles).
  - `lock`: a solid body (14 × 9px, 1px corners, the core) under a stroked shackle (an arch 8px wide) whose legs meet the body.
  - `unlock`: the same body, the shackle's right leg ending 4px above it (lifted), so the open state reads at 24px.
  - `eye`: a stroked eye outline (20 × 12px, pointed ends) around a solid 6px pupil (the core), at least 1.4px of air between them (the outline's inner edge sits 2px from the pupil).
  - `eye_off`: the eye outline with the pupil replaced by one 2px diagonal stroke from the top left to the bottom right (the "slash" is the state).
  - `opacity`: a stroked 18px circle around a solid left half-disc (13px, the core), 1.5px clear of the stroke.
  - The layer-order buttons reuse `arrow` (one drawing, turned up and down). Add photo reuses `create`.
  - Each is drawn at 24px for the tool bar and the Layers panel's row buttons; the lock badge on a locked layer's frame uses `lock` at `icon-small`.

Source files are Android vector drawables in `:core:designsystem` `composeResources/drawable/` (`ic_<name>.xml`), path-only so Compose Multiplatform renders them the same on Android and iOS. There is no Memix logo yet: until one exists, write the name "Memix" in Anton; the launcher icon is a plain placeholder built from that wordmark, not a logo.

## In Compose

Map each token to the Memix theme objects, kebab-case to camelCase: `track-meme-sound` → `MemixColors.trackMemeSound`, `body-strong` → `MemixType.bodyStrong`, `space-4` → `MemixSpacing.space4` (dp), `radius-lg` → `MemixShapes.radiusLg`. Never hardcode a color, size or font in a screen; add a token here first.

## Tokens

### Color (dark theme only)

| Token | Compose name | Hex | Use |
| --- | --- | --- | --- |
| `canvas` | `MemixColors.canvas` | `#111111` | App background behind every screen (Adobe Spectrum's dark background-base). Neutral, untinted gray so the user's colors aren't shifted. |
| `stage` | `MemixColors.stage` | `#000000` | Behind the video or photo preview inside both editors, as in CapCut, so the frame edges read clearly. |
| `surface` | `MemixColors.surface` | `#1B1B1B` | Cards, sheets, bottom navigation, tool bar, timeline lanes (Spectrum layer-1). |
| `surface-raised` | `MemixColors.surfaceRaised` | `#2C2C2C` | Raised controls on surface: secondary buttons, chips, search field, unselected font tiles, pressed segments, empty media frames. It never marks a selected state (only 1.4:1 against canvas). |
| `hairline` | `MemixColors.hairline` | `#393939` | 1px dividers and quiet borders. Decorative only: never the only sign of a control's edge. |
| `text` | `MemixColors.text` | `#F2F2F2` | Primary text and icons (about 95% white, softer than pure white on near-black). 12.4:1 or more on every surface. |
| `text-secondary` | `MemixColors.textSecondary` | `#A6A6A6` | Supporting text and inactive icons. 5.7:1 or more on canvas, surface and surface-raised. |
| `text-muted` | `MemixColors.textMuted` | `#949494` | Hints, placeholders, timecodes and ruler labels. 4.6:1 or more on canvas, surface and surface-raised. |
| `primary` | `MemixColors.primary` | `#2BB3F3` | Memix sky blue: CapCut's cyan moved 9° toward Spectrum blue. The one primary action per screen (Create, Export, Apply), the active tool, links, progress, and everything that is a meme sound. Text on it is on-primary (7.9:1). |
| `primary-pressed` | `MemixColors.primaryPressed` | `#189BD8` | Pressed state of primary fills. Text on it is on-primary (6.0:1). |
| `primary-subtle` | `MemixColors.primarySubtle` | `#2BB3F329` | 16% sky wash behind the active tool, sound play buttons and the "Add a meme sound" pill (over a `canvas` base). Icons and text on it use primary (6.2:1 over canvas). Never a whole row: a playing sound row steps one value up instead. Pressed, these fills turn `surface-raised`. |
| `on-primary` | `MemixColors.onPrimary` | `#111111` | Text and icons on primary, primary-pressed, selection-white fills and danger. Also the 1px dark edge beside white selection marks drawn over the user's content (text frame, handle, snap guides on the preview), so they read on any video: white plus this edge is 4.3:1 or more against any color. |
| `selection` | `MemixColors.selection` | `#FFFFFF` | Selected clip or layer outline, trim handles, playhead, selected chip, segment and font tile fills (text on them `on-primary`), the selected swatch ring: white, as in CapCut, so selection never competes with clip colors. |
| `track-video` | `MemixColors.trackVideo` | `#3D3D3D` | Main video and overlay clips (they show thumbnails). Labels on it are text. |
| `track-text` | `MemixColors.trackText` | `#E78E40` | Text clips on the timeline and text layers in the layers panel. Labels on it are on-track (7.5:1). |
| `track-sticker` | `MemixColors.trackSticker` | `#D8AE31` | Sticker, image and cut-out clips. Labels on it are on-track (9.0:1). |
| `track-meme-sound` | `MemixColors.trackMemeSound` | `#2BB3F3` | Meme sound clips: the brand color, same as primary, so sound is always Memix blue. Labels on it are on-track (7.9:1). |
| `track-audio` | `MemixColors.trackAudio` | `#3DAE79` | Original audio, imported audio and voiceover clips: green, the editing convention for audio. Labels on it are on-track (6.8:1). |
| `track-effect` | `MemixColors.trackEffect` | `#AA84EB` | Effect and filter clips. Labels on it are on-track (6.5:1). |
| `on-track` | `MemixColors.onTrack` | `#111111` | Labels and icons on every colored track fill. |
| `danger` | `MemixColors.danger` | `#F6574C` | Delete, discard and recording states, always with a word or icon. Text on it is on-primary (5.7:1). |
| `danger-pressed` | `MemixColors.dangerPressed` | `#E04E43` | Pressed state of danger fills. Text on it is on-primary (4.8:1). |
| `success` | `MemixColors.success` | `#3BCE7B` | Export finished and saved states, always with a word or icon. Text on it is on-primary (9.3:1). |
| `focus-ring` | `MemixColors.focusRing` | `#FFFFFF` | Keyboard and switch-access focus: a solid 2px ring, 2px outside the control. |
| `scrim` | `MemixColors.scrim` | `#000000B3` | Behind bottom sheets and dialogs. On the timeline: the wash over the part of an item past the end of the video (its label switches to `text`), and the selected main clip's duration badge (`text` on it is 7.5:1 or more over any thumbnail). |

### Type

Families: display = Anton, display-hi = Teko (Hindi only), sans = Space Grotesk (400/500/700), mono = Space Mono. Caption fonts (user content, see Captions): Anton, Poppins, Baloo 2, Kalam, Rozha One, with Teko for Anton's Devanagari. All SIL OFL; bundle the TTFs in `composeResources/font/`.

| Style | Compose name | Family | Size / line (sp) | Weight | Use |
| --- | --- | --- | --- | --- | --- |
| `display-xl` | `MemixType.displayXl` | display | 40 / 44 | 400 | Home hero and empty-state headlines, uppercase. |
| `display` | `MemixType.display` | display | 28 / 30 | 400 | Entry cards and sheet heroes, uppercase. |
| `wordmark` | `MemixType.wordmark` | display | 28 / 30 | 400 | The name "Memix" (Home top bar) until a logo exists. Always Anton, in every language; written as is. |
| `meme-caption` | `MemixType.memeCaption` | display | 36 / 40 | 400 | The classic caption look for interface samples (template cards, catalog): white fill with a 3px black outline stroke. In the editors, a caption's size comes from the canvas (Captions below). |
| `caption-sample` | `MemixType.captionSample` | the caption font it shows | 18 / 28 | that file's | The name on each FontTile, drawn in that caption font (P1-10). Line height 28 fits Devanagari marks in every caption font. |
| `title-l` | `MemixType.titleL` | sans | 22 / 28 | 700 | Screen titles. |
| `title` | `MemixType.title` | sans | 18 / 24 | 700 | Section and sheet titles. |
| `body` | `MemixType.body` | sans | 15 / 22 | 400 | Default text. |
| `body-strong` | `MemixType.bodyStrong` | sans | 15 / 22 | 500 | Row names and button labels. |
| `label` | `MemixType.label` | sans | 13 / 16 | 500 | Chips, tabs, small buttons. |
| `caption` | `MemixType.caption` | sans | 11 / 14 | 500 | Tool bar labels, metadata, timeline clip labels. |
| `timecode` | `MemixType.timecode` | mono | 12 / 16 | 400 | Playback timecode, ruler marks, durations in the editors. |

#### Hindi display face (P1-17, owner-approved 8 Oct 2026)

When the app language is Hindi, `MemixType.displayXl` and `MemixType.display` use these values. The names stay the same, so screens don't change. The other four languages are unchanged.

| Style (Hindi) | Family | Size / line (sp) | Weight | Letter spacing |
| --- | --- | --- | --- | --- |
| `display-xl` | display-hi (Teko) | 40 / 60 | 700 | 0 |
| `display` | display-hi (Teko) | 28 / 44 | 700 | 0 |

- **Where it applies:** every `display-xl` and `display` use, which means the Home hero, entry-card titles, the Create sheet's hero and its two editor rows, the Export sheet's hero, the Export screen's heading and empty-state headlines (all go through `DisplayText`). The whole string is set in Teko, Latin letters included, so one headline never mixes two display faces.
- **Review:** `docs/ux/reviews/P1-17.md` (code only; the device screenshots in `docs/ux/reviews/P1-17/` close it). The caption fallback's size factor is a separate, provisional value (P1-10 review, decision 5).
- **Where it doesn't:**
  - The `wordmark` (always Anton: it's the name, never translated).
  - Interface roles (Space Grotesk, where Devanagari falls back to the system face, Noto Sans Devanagari on Android).
  - `meme-caption`, which is user content. In captions, Teko Bold draws the Devanagari letters Anton lacks (spec P1-10).
- **Letter spacing 0:** tracking opens gaps in the headline bar (shirorekha) that joins Devanagari letters.
- **Line height = Teko's own line spacing:** Devanagari has marks above the headline bar and below the baseline. The planned 1.3× (52 / 36) was shorter than Teko's built-in line spacing (1.433 em, which the candrabindu and the vocalic-rr sign reach), so the P1-17 build uses Teko's, rounded up to the 4 dp grid: 40 sp → 60, 28 sp → 44 (tokens updated 10 Oct 2026 to match `MemixType`).
- **File to bundle:** `teko_bold.ttf`, which is `fonts/ttf/Teko-Bold.ttf` from [googlefonts/teko](https://github.com/googlefonts/teko/tree/master/fonts/ttf) (the 2023 Google Fonts build, 251 KB). Add its `OFL.txt` to the app's font licenses.
  - The variable [`ofl/teko/Teko[wght].ttf`](https://github.com/google/fonts/tree/main/ofl/teko) (285 KB, weights 300–700) is an alternative only if Compose Multiplatform resources can set its weight axis on both Android and iOS. Check at ticket start.
- **Why Teko** (compared 8 Oct 2026 from the google/fonts metadata and file pages; all four are SIL OFL with Latin, Latin Extended and Devanagari):

| Face | Weights | File size | Fit for Anton's role |
| --- | --- | --- | --- |
| **Teko** (Indian Type Foundry) | 300–700 (variable, plus static builds) | 251 KB (Bold), 285 KB (variable) | Chosen. Tall, condensed, square, low contrast; made for headlines and news tickers. 1090 glyphs per font with full Devanagari conjuncts. No Reserved Font Name. The closest to Anton's silhouette (Assumption, visual comparison). |
| Khand (Indian Type Foundry) | 5 static | 350 KB per weight | Compact, monolinear headline face. Wider and calmer than Teko, so less of a meme voice. |
| Rajdhani (Indian Type Foundry) | 5 static | 391 KB per weight | Squared and technical; reads sci-fi rather than meme. |
| Anek Devanagari (Ek Type) | variable: width 75–125, weight 100–800 | 2.09 MB | Its heaviest condensed instance is the closest to Anton's mass, but the file is 8× Teko and would need instancing to ship. |

Sources: [Teko metadata](https://github.com/google/fonts/blob/main/ofl/teko/METADATA.pb), [Teko description](https://github.com/google/fonts/blob/main/ofl/teko/DESCRIPTION.en_us.html), [Teko OFL](https://github.com/google/fonts/blob/main/ofl/teko/OFL.txt), [Khand](https://github.com/google/fonts/tree/main/ofl/khand), [Rajdhani](https://github.com/google/fonts/tree/main/ofl/rajdhani), [Anek Devanagari](https://github.com/google/fonts/tree/main/ofl/anekdevanagari).

### Captions (user content, P1-10)

Captions are the user's meme, not chrome, so they have their own fonts, palette and geometry in `design/tokens.json` → `caption`. They are never read through `MemixTheme`; one commonMain place holds them for the engines and both editors, and one renderer draws them in preview, export and the photo editor. Full rules: `docs/ux/specs/P1-10-text.md` → Caption rendering.

| Id (saved) | Tile name | File | Size | Scripts |
| --- | --- | --- | --- | --- |
| `anton` | Classic (default) | `anton_regular.ttf` | already bundled | Latin, Latin Ext, Vietnamese; **Devanagari from Teko Bold** (whole clusters) |
| `poppins` | Modern | `poppins_bold.ttf` (Poppins-Bold) | 152 KB | Latin, Latin Ext, Devanagari |
| `baloo2` | Bubbly | `baloo2_extrabold.ttf` (Baloo2-ExtraBold, static) | 348 KB | Latin, Latin Ext, Vietnamese, Devanagari |
| `kalam` | Handwritten | `kalam_bold.ttf` (Kalam-Bold) | 450 KB | Latin, Latin Ext, Devanagari |
| `rozha` | Serif | `rozha_one_regular.ttf` (RozhaOne-Regular) | 315 KB | Latin, Latin Ext, Devanagari |

- **Why these five:** together they cover the caption styles people know from TikTok and Instagram (classic meme, clean sans, rounded, handwriting, serif). Every one draws all five launch languages, and the four new files add 1,265 KB. Teko isn't a sixth tile because, as Anton's Devanagari partner, it would draw Hindi exactly like Classic. Considered and dropped: Bangers (no Devanagari), Yatra One (very regional look; second choice), Baloo 2 variable (twice the static size), Apache-licensed marker faces (not OFL). All four new files are SIL OFL 1.1 with no Reserved Font Name.
- **Fallback:** anything a caption font lacks (emoji, other scripts) comes from the system font, in preview and export alike. Emoji get no outline.
- **Palette** (text and outline, opaque): White `#FFFFFF`, Black `#000000`, Yellow `#FFD60A`, Orange `#FF9500`, Red `#FF3B30`, Pink `#FF6BC1`, Purple `#AF52DE`, Blue `#0A84FF`, Light blue `#64D2FF`, Green `#30D158`. Content colors only: they appear in the interface only inside Swatches, and they avoid the rejected v1 values.
- **Default:** Classic, White, Black outline.
- **Geometry:** font size 8% of the canvas's shorter side at scale 1; wrap width 90% of it; scale 25–500%; lines centered; letter spacing 0; outline stroke = font size ÷ 12 with round joins, drawn under the fill; laid out once in canvas pixels, then drawn scaled. These values are part of every saved draft's look: changing one after P1 needs a schema bump and a migration.
- **Devanagari size under Classic:** Teko's runs at 1.0 × the caption size, provisional; set at phase-end QA before the Phase 1 gate from a mixed-line screenshot (Anton cap height ÷ Teko headline height, rounded to 0.05; 1.0 if within 5%). Spec P1-10 → Fonts.

Sources: [Poppins](https://github.com/google/fonts/blob/main/ofl/poppins/METADATA.pb), [Baloo 2](https://github.com/google/fonts/blob/main/ofl/baloo2/METADATA.pb) and its [upstream](https://github.com/google/fonts/blob/main/ofl/baloo2/upstream_info.md), [Kalam](https://github.com/google/fonts/blob/main/ofl/kalam/METADATA.pb), [Rozha One](https://github.com/google/fonts/blob/main/ofl/rozhaone/METADATA.pb), [Anton](https://github.com/google/fonts/blob/main/ofl/anton/METADATA.pb); file sizes from the GitHub file pages, 9 Oct 2026.

### Canvas (user content, P1-11)

The video canvas's shape and what fills it are the user's meme, not chrome, so they live in `design/tokens.json` → `canvas`, next to the caption constants, never in `MemixTheme`. Full rules: `docs/ux/specs/P1-11-canvas.md`.

- **Ratios and sizes:** 9:16 1080 × 1920 (default), 1:1 1080 × 1080, 4:5 1080 × 1350, 3:4 1080 × 1440, 16:9 1920 × 1080. Custom w:h (1 to 99 each, the long side at most 2.39 × the short side) is w·k × h·k with k the even number nearest 1080 ÷ min(w, h). The shorter side stays about 1080, so captions keep their size across ratios.
- **Fit or Fill** applies to every main clip at once; new clips take the shared fit.
- **Background:** black (default), a caption-palette color, or Blur (a Fill-scaled copy of the playing clip, Gaussian sigma 3% of the output's shorter side, no darkening). Gaps and missing clips are always black. **Image** (P2-12, both editors): a picture from the gallery, cover-fitted and centered at the bottom of the stack, full brightness; a missing file draws black; Remove returns to the editor's default color (video black, photo white). Spec `docs/ux/specs/P2-12-canvas-background-image.md`.
- **Ratio change:** a layer fully inside the frame stays fully inside (moved in to a 2% margin, shrunk first if too big); a layer the user hung off the edge keeps its place.

Sources: [Media3 GaussianBlurWithFrameOverlaid](https://developer.android.com/reference/androidx/media3/effect/GaussianBlurWithFrameOverlaid); X's 1:2.39 to 2.39:1 video range ([Social Media Today](https://www.socialmediatoday.com/news/twitter-updates-video-playback-to-ensure-a-higher-quality-viewing-experienc/607184)).

### Photo editor (user content, P2-01)

How the photo editor's canvas and layers start, behave and draw is the user's meme, not chrome, so it lives in `design/tokens.json` → `photo` (and `canvas.background.image`), kept in one commonMain place next to the caption and canvas constants, never in `MemixTheme`. Full rules: `docs/ux/specs/P2-01-photo-editor-shell.md`, `P2-12-canvas-background-image.md`, `P2-13-image-layers.md`.

- **Start:** Photo meme opens the picker (images only, one item); the canvas takes the photo's own shape (a preset within 1%, else the custom ratio with the fewest terms within 1%, at most 2.39:1). A blank canvas is 1:1, white (its Create row ships with P2-03). New photo backgrounds are white; video stays black.
- **An image layer at scale 1** is the picture fitted inside the canvas, recomputed from the canvas's shape; a picture within 1% of the canvas's shape covers it. Scale runs 10% to 500%; opacity 10% to 100%.
- **Lock, hide, opacity:** a locked layer can be selected, unlocked, hidden and duplicated, and nothing else; a hidden layer isn't drawn or exported and can't be hit; opacity draws at once and commits on release.
- **Ratio change:** a layer fully inside stays fully inside (images move in to touch the edge, margin 0; text and stickers keep P1-11's 2%); a layer placed partly outside keeps its center and scale.
- **Stage:** the canvas frame is inset `space-4` (the video editor's is `space-2`), and the stage refits above any panel, which is as tall as its content up to 40% of the window.
- **Layers panel rows** (LayerRow): `surface-raised`, white ring when selected, the layer's picture at `layer-thumb-size`, lock and eye buttons; locked, hidden, faded and missing are told in the meta line and the icons, never by color alone.

### Watermark (user content, P1-13)

The "Memix" mark on free exports is drawn into the file, so it lives in `design/tokens.json` → `watermark`, next to the caption and canvas constants, never in `MemixTheme`. Full rules: `docs/ux/specs/P1-13-watermark.md`.

- **Mark:** "Memix" in Anton (the wordmark face), never translated or uppercased; font size 4% of the output's shorter side (43 px at 1080p).
- **Place:** top-left, the glyphs' ink box 3% of the shorter side from the left and top edges (owner, 8 Oct 2026; kept on 22 Oct, with one private TikTok and Reels test at phase-end QA); above every layer, for the whole video. The two margins are separate tokens (`marginLeftOfShortSide`, `marginTopOfShortSide`), so the test can move only the top (about 0.13 for option B).
- **Look:** white at 80% with a soft black shadow at 50%, no offset, blur 15% of the font size. It's content, so the chrome's no-shadow rule doesn't apply, and it has no outline (the v1 look). Brand text has no WCAG contrast requirement; the shadow keeps it readable on bright footage anyway.
- **Not in the editor preview.** Not saved in drafts, so a value change needs no schema bump.

Sources: owner decision 8 Oct 2026 (`docs/pm/decision-log.md`); [WCAG 2.2 SC 1.4.3 logotype exception](https://www.w3.org/TR/WCAG22/#contrast-minimum).

### Spacing

4px base. Screen side gutter is space-4.

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `space-1` | `MemixSpacing.space1` | `4px` | Icon-to-label gap, gaps between clips on a track. |
| `space-2` | `MemixSpacing.space2` | `8px` | Inside chips and small controls. |
| `space-3` | `MemixSpacing.space3` | `12px` | Between related rows and chips. |
| `space-4` | `MemixSpacing.space4` | `16px` | Screen side gutter, card and sheet padding. |
| `space-5` | `MemixSpacing.space5` | `20px` | Button side padding, tab gaps. |
| `space-6` | `MemixSpacing.space6` | `24px` | Between sections. |
| `space-8` | `MemixSpacing.space8` | `32px` | Above screen titles, large section breaks. |
| `space-10` | `MemixSpacing.space10` | `40px` | Empty-state padding. |

### Radius

Chunky, never sharp; pills for chips and tabs.

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `radius-sm` | `MemixShapes.radiusSm` | `6px` | Timeline clips, badges. |
| `radius-md` | `MemixShapes.radiusMd` | `10px` | Buttons, inputs, the Create button, tool highlights. |
| `radius-lg` | `MemixShapes.radiusLg` | `16px` | Cards, sheets, template thumbnails. |
| `radius-full` | `MemixShapes.radiusFull` | `9999px` | Chips, segmented tabs, play buttons. |

### Shadow

No hard or decorative shadows: surfaces separate by value steps (canvas → surface → surface-raised), as in CapCut and Spectrum. Only sheets and floating menus cast a soft shadow.

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `shadow-sheet` | `MemixElevation.shadowSheet` | `0 -8px 24px #00000080` | Bottom sheets lifting over a screen. |
| `shadow-float` | `MemixElevation.shadowFloat` | `0 8px 24px #00000066` | Floating menus and toasts. |

### Stroke

Border widths.

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `stroke-hairline` | `MemixStroke.strokeHairline` | `1px` | Dividers and quiet borders, with the hairline color. |
| `stroke-selection` | `MemixStroke.strokeSelection` | `2px` | Selected clip or layer outline, with the selection color. |
| `stroke-focus` | `MemixStroke.strokeFocus` | `2px` | Focus ring width, offset 2px. |

### Size

Fixed sizes used across screens (dp on Android).

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `touch-target` | `MemixSize.touchTarget` | `48px` | Minimum tappable size for every control. |
| `nav-height` | `MemixSize.navHeight` | `64px` | Bottom navigation bar. |
| `toolbar-height` | `MemixSize.toolbarHeight` | `72px` | Editor tool bar. |
| `tool-item-width` | `MemixSize.toolItemWidth` | `64px` | Minimum width of one tool in the editors' tool bars (icon over a `caption` label, at least `touch-target` tall). A tool widens only when one word of its label can't fit at large font sizes. About five and a half tools show at 360 dp, so the first tools never need a scroll (P1-06). |
| `track-height-video` | `MemixSize.trackHeightVideo` | `40px` | Main video track clips. |
| `track-height` | `MemixSize.trackHeight` | `28px` | Every other track's clips. |
| `playhead-width` | `MemixSize.playheadWidth` | `2px` | Timeline playhead line, in selection white. |
| `playhead-head-size` | `MemixSize.playheadHeadSize` | `12px` | Round head on top of the playhead, in selection white, centered on the line at the top of the ruler. |
| `trim-handle-width` | `MemixSize.trimHandleWidth` | `12px` | White trim handles at both ends of the selected timeline item, just outside its outline. Each handle's touch target is still `touch-target` wide. |
| `icon-small` | `MemixSize.iconSmall` | `16px` | Icons inside timeline items and badges where 24px doesn't fit (the lock on a locked item). Same drawings, scaled; never a standalone control. |
| `rail-height` | `MemixSize.railHeight` | `4px` | The thin rail under a fill: progress bar and slider. Rail in `hairline`, fill in `primary` (7.3:1 on surface, 4.9:1 against the rail). "Rail", not "track", because a track is a timeline lane in Memix. |
| `canvas-handle-size` | `MemixSize.canvasHandleSize` | `24px` | The white scale-and-rotate handle on a selected caption on the preview (later stickers and layers), with a 1px `on-primary` edge and an `icon-small` icon in `on-primary` (18.9:1). Its target is still `touch-target`. |
| `swatch-size` | `MemixSize.swatchSize` | `32px` | A color swatch (caption text and outline; later brush colors), drawn inside a `touch-target` square. Selected: a `stroke-selection` white ring 2px outside it (17.2:1 on surface). |
| `list-thumb-size` | `MemixSize.listThumbSize` | `56px` | Square thumbnail at the start of a list row (a draft's first frame, P1-14), `radius-sm`, with the type badge (`badge-size`: `scrim`, `icon-small` in `text`) in its corner, shown only over a picture. The row is at least this plus `space-2` above and below. |
| `badge-size` | `MemixSize.badge` | `20px` | Side of a square icon badge over the user's picture (the type badge on a draft's thumbnail, P1-14): a `scrim` chip in `radius-sm` with an `icon-small` icon centered (2px around it), `space-1` from the picture's corner. Not for text badges. |
| `play-disc-size` | `MemixSize.playDiscSize` | `56px` | The big play button centered on a video that isn't playing (the Exported screen, P1-12): a `radius-full` disc in `primary` with a 24px `play` icon in `on-primary` (7.9:1). It only draws; the tap belongs to the whole video, so its target is far above `touch-target`. Not for icon controls: those are IconButtons. |
| `layer-thumb-size` | `MemixSize.layerThumbSize` | `40px` | Square thumbnail at the start of a LayerRow in the photo editor's Layers panel (P2-01), `radius-sm`, the layer's picture fitted inside. The row is at least this plus `space-2` above and below, 56px. |

### Motion

| Token | Compose name | Value | Use |
| --- | --- | --- | --- |
| `duration-press` | `MemixMotion.durationPress` | `120ms` | Presses, toggles, nav item color changes, and the reduce-motion fade that replaces every movement. |
| `duration-sheet` | `MemixMotion.durationSheet` | `200ms` | Bottom sheets sliding in and out, tab content changes. |
| `duration-screen` | `MemixMotion.durationScreen` | `320ms` | Screen pushes and pops. |
| `duration-bonk` | `MemixMotion.durationBonk` | `240ms` | The bonk: content landing on the timeline scales 1 → 1.12 → 1 with a slight overshoot; on the timeline the width grows by the same dp as the height, so long clips don't sweep over their neighbors. Only for placing content. |

## Compose implementation notes

- px in the tokens = dp in Compose; font px = sp. Alpha hex (`#2BB3F329`, `#000000B3`) is RRGGBBAA → `Color(0x292BB3F3)` in Compose (ARGB).
- Put the tokens in `:core:designsystem` as `MemixColors`, `MemixType`, `MemixSpacing`, `MemixShapes`, `MemixStroke`, `MemixSize`, `MemixElevation`, exposed through `CompositionLocal`s and a `MemixTheme { }` wrapper. Map Material 3's `darkColorScheme` from them (`primary` = sky, `onPrimary` = `#111111`, `background` = canvas, `surface` = surface, `surfaceContainerHigh` = surface-raised, `outlineVariant` = hairline) so stock components look right, but screens read Memix tokens directly.
- No borders on shapes and no elevation shadows except `shadow-sheet` (bottom sheets) and `shadow-float` (menus, toasts). Depth = canvas → surface → surface-raised.
- Selection is always white (`selection`): selected clip/layer outline (`stroke-selection`), trim handles, playhead, selected chip fill. Never use `primary` for selection.
- `primary` sky blue: one main action per screen (+ the global Create button), active tool (on `primary-subtle`), links, progress, and all meme-sound UI.
- `meme-caption` is Anton, white fill with a 3 dp black stroke: draw the text twice (stroke pass with `TextStyle(drawStyle = Stroke(width, join = StrokeJoin.Round))`, then fill). This is user content styling, not chrome. Editor captions use the same two passes with the stroke at font size ÷ 12, through the one caption renderer (P1-10); their fonts, palette and geometry are caption constants, not `MemixTheme` tokens.
- Timeline: clips are positioned by time (µs → px via the zoom scale), never by index. Track heights from `trackHeightVideo` / `trackHeight`; playhead `playheadWidth` in `selection` with a `playheadHeadSize` circular head, fixed in the center while the content scrolls; trim handles `trimHandleWidth` in `selection`. Zoom scales and ruler steps are layout math kept as named constants in the timeline component (spec `docs/ux/specs/P1-05-timeline.md`), not tokens.
- Editor tool panels (Volume, Style and Position for text, Canvas) use the ToolPanel component. While a panel is open, the region it covers (timeline and tool bar) leaves the accessibility tree and can't take keyboard focus, so nothing hidden can be reached (P1-08 review). Long lists in a sheet (the editor's Meme sounds sheet, later the P3-05 picker) use the Sheet's half-height opening (`opensAtHalfHeight`); a SoundRow on a sheet uses `surface-raised` for its playing band. Sliders that have a level worth finding again snap to it (`snapValue`, one SegmentTick), and commit one undo step in `onValueChangeFinished`. Font choices use FontTile and color choices use Swatch (the canvas background adds its Blur variant); settings shown as shapes (ratio, Fit / Fill) use OptionTile; a current setting in an editor's top bar (the ratio) uses SettingPill, which speaks "Expanded" or "Collapsed" as its state description; never put other words in the state description of a selectable or checkable control, because it replaces the spoken "selected" (extra meaning goes in the name, after the visible label; P1-11 review); a two-to-five-way setting value (export resolution and frame rate, P1-12) uses SegmentedChoice, with the white selection; floating menus use Menu; short messages use Toast (with `FadingToast` for its fade, one at a time); icon-only controls with no fill (Close, Play/Pause, Undo, Redo, a toast's ×) use IconButton (`design/system/components/`). An IconButton on `surface-raised` presses to `hairline`.
- Photo editor (P2-01): the Layers, Opacity, Position and Canvas panels use the ToolPanel with its content-sized variant (as tall as the content, up to 40% of the window; the stage refits above it with a 200 ms scale, no re-decode), because no timeline reserves the space. Layers use LayerRow (`design/system/components/LayerRow/README.md`); the Background swatch row gains an Image swatch (P2-12) that shows the picked picture in its circle. A locked layer's frame shows a `badge-size` `scrim` chip with the `lock` icon where the handle would be. Selection is white (the ring on a row, the two-tone frame on the canvas).
- Pressed states: `primary` → `primary-pressed`; neutral controls go one value step lighter (`surface-raised` → `hairline`). No translate/scale on press; the 240 ms "bonk" scale is only for content landing on the timeline.
- Icons: Memix-drawn vector drawables in `composeResources/drawable/` (see Iconography), 24 dp, 2 dp flat-cap stroke, tinted per state.
- Tool bars: each tool is at least `toolItemWidth` wide and `touchTarget` tall. Its label is never cut: two lines is the copy budget, not a line cap, so at 200% font a long translation takes a third line and the bar grows (WCAG 2.2 SC 1.4.4).
- Full screens that end in buttons (Exporting, Exported and failure, P1-12) must never clip them: the picture area (`weight(1f)`) shrinks to a stated minimum share of the screen height, and below that the whole screen scrolls (give the scrolling column `heightIn(min = viewport)` so the weighted area still fills it at 100% font). A bottom button that can be pushed off screen at 200% font is a bug (P1-12 review).
- A video that isn't playing gets a `PlayDisc` (`play-disc-size`) centered on it; the tap belongs to the whole video (name "Play your video", "Pause your video" while playing).
- Never put spacing between children with `Arrangement.spacedBy` when some children can be zero-size (an `AnimatedVisibility` that is hidden still takes its gap). Put the gap inside the child as padding, or group the optional children in one Column (P1-12 review).
- Build a component catalog screen (debug builds only) showing every component in every state; the UX designer reviews it from screenshots. No automated screenshot tests in this project.

## Screens in the design canvas

| Board | File | Shows |
| --- | --- | --- |
| Home | `Main.dc.html` | Wordmark + region chip, MAKE A MEME hero, Video meme (blue) and Photo meme (neutral) entry cards, trending templates and sounds, bottom nav |
| Create sheet | `Create.dc.html` | Scrim + sheet: Video meme, Photo meme, Start from a template |
| Templates | `Templates.dc.html` | Video/Photo segmented tabs, category chips (selected = white), 2-column template grid |
| Sounds | `Sounds.dc.html` | Labelled search, Trending/Local/Global/Favorites/My sounds tabs, category chips, sound rows (one playing) |
| Video editor | `VideoEditor.dc.html` | Top bar (close, 9:16, 1080p, Export), preview on black stage, transport row, 6-track timeline with a white-selected meme sound, selected-clip bar, tool bar with active tool. The P1-04/05/06 specs supersede parts of it: no track-label column, clip tools replace the tool bar instead of a separate bar, "Add a meme sound" row, original-audio toggle. |
| Export sheet | `Export.dc.html` | Resolution, frame rate, watermark row with rewarded-ad Remove, size/time estimate, Export video. The P1-12 spec adds 480p, gives selected segments the white selection (SegmentedChoice) and reads the estimate "Up to 18 MB · about 20 s"; the Video editor board's "1080p" pill is dropped. Until P5-02 the watermark row has no Remove and reads "Small text in the top-left corner of your video" (P1-13 spec). |
| Exported | `ExportDone.dc.html` | Result preview with watermark, saved status, share grid, Make another / Done. Until P5-05 one secondary "Share" button (system share sheet) takes the grid's place (P1-12 spec, owner 21 Oct); the status reads "Saved to your gallery". |
| Photo editor | `PhotoEditor.dc.html` | Classic caption canvas with a white-selected text layer, Make it a video meme, format chips, text bar, tool bar. The P2-01 spec supersedes parts of it: no Save until P2-08 and no Make it a video meme until P2-10, no format bar until P2-02, Undo and Redo at the end of the top bar with the ratio pill in the center, a Layers tool and panel, and the text bar becomes P2-03's Style panel. |
| Remove background | `RemoveBackground.dc.html` | Checkerboard cut-out, success status, Erase/Restore, brush size and edge softness, Save as sticker / Apply |

The boards are references for layout, hierarchy and copy, not pixel-final specs. Where a board and this file disagree, the tokens win.
