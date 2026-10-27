# Canvas: ratio, fit and background · P1-11

Fifth spec for the video editor. It adds to the frame and regions of the others and doesn't repeat them:
- `P1-04-editor-and-preview.md`: frame, top bar, preview stage, transport, undo/redo, back order, toasts.
- `P1-05-timeline.md` and `P1-06-clip-edits-and-audio.md`: timeline, tool bars, ToolPanel, edits, Add media and Replace.
- `P1-10-text.md`: captions, their geometry (sized from the canvas's shorter side) and the Position panel.
- **P1-11 (this file):** the ratio pill in the top bar, the Canvas panel (ratio, custom ratio, fit or fill, background color or blur), what a ratio change does to clips and captions, the frame edge on the preview, and the canvas rendering rules that make preview and export match.

## Job
Make the meme the right shape for where it's going (9:16 for TikTok, Reels and Shorts; 1:1 or 4:5 for a feed post; 16:9 for YouTube) and decide what fills the space a clip doesn't cover. The classic case: a landscape fail clip in a 9:16 meme, with black bars above and below and the caption on the top bar, or the clip filling the screen, or the clip over a blurred copy of itself.

**Done when** (TICKETS): changing ratio keeps every layer inside the frame. "Layer" in P1 means captions (P1-10). Stickers (P4-12) and overlays (P4-01) follow the same rule when they arrive. Exact meaning: Ratio change → Layers.

**Performance bar (owner, 8 Oct):** no visible lag and no memory leaks. A choice in the Canvas panel shows on the preview within the P1-06 edit rule (100 ms, plus "Updating the preview" after 1 s). Preview with a blurred background still plays at 30 fps or more on the owner's phone.

**Fast path, with tap counts:**
- **Change the shape:** the ratio pill (1), a ratio (2).
- **Fill the screen with a landscape clip:** the pill (1), Fill (2).
- **Blurred background:** the pill (1), Blur (2).
- Each choice applies at once. Done, back or a second tap on the pill closes the panel.

**Color roles:**
- **Blue:** none new. The ratio pill takes the active look (`primary` on `primary-subtle`) while its panel is open, the same as an active tool. Export (P1-12) stays the one blue action.
- **White:** selection: the selected ratio and fit tiles (white fill) and the selected background swatch (white ring).
- **Background colors** are the user's content, from the caption palette, shown only inside swatches.

**Default for every new project (unchanged from P1-01):** 9:16, Fit, black background. Black bars are the meme look: the caption sits on the bar above a landscape clip, and P1-10's first caption slot (`centerY` 0.25) lands there. No change to P1-02 or P1-16.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Canvas panel (ToolPanel) | A tap on the ratio pill in the top bar | Done, a second tap on the pill, system back, Escape. A selection on the timeline is kept. |

- **Why the top bar and not the tool bar** (P1-04 reserved the slot; P1-06 put Canvas there): the shape is set once or twice per project, the pill shows the current ratio at all times, and the tool bar's places go to things done many times per meme. Top center is a stretch for one thumb (Assumption), which is acceptable for a once-per-project setting (Principle 4 covers primary actions).
- **Opening from other states:**
  - Nothing or an item selected: the panel covers the timeline and the tool bar as every ToolPanel does. The selection stays.
  - Another ToolPanel open (Volume, Style, Position): that panel closes (its changes are already applied) and Canvas opens in its place. Panels never stack.
  - Typing a caption (P1-10): typing commits first, then the panel opens.
  - Reorder mode: the pill explains instead ("Tap Done to finish reordering first."). It doesn't cancel the user's reorder.
  - A sheet is open: the pill is under the scrim and out of reach (Sheet rule).
- **Back order** (P1-04) is unchanged: the Canvas panel is "an open sheet or tool panel".
- **Playback:** opening the panel doesn't pause, and a choice doesn't pause either, so a ratio or background can be judged while the video plays. This follows the Volume panel (P1-04 → "Keeps playing"), not the tool-tap rule, because seeing the change is the point.
- **Kept:** every choice is in the project (autosave, undo). Not kept: the custom stepper values of an unfinished visit (Custom ratio).

## Layout
Board: `design/screens/VideoEditor.dc.html` (the "9:16" pill in the top bar). The board has no Canvas panel; this spec adds it.

### The ratio pill (top bar, center)
Component SettingPill (`design/system/components/SettingPill/README.md`, new).
- Label: the current ratio in `timecode` (`text`): "9:16", "1:1", "4:5", "3:4", "16:9", or the custom ratio in lowest terms ("5:8").
- `surface-raised` fill, `radius-full`, `space-3` side padding and `space-2` above and below the text (drawn 32 dp tall at 100% font), centered in a target at least `touch-target` tall and wide.
- **Open:** while the Canvas panel is open, `primary` label on a `primary-subtle` fill (6.2:1 over `canvas`), the active-tool look. It's the only thing in the top bar that says which panel is open.
- **Pressed:** `surface-raised` → `hairline` (open: `primary-subtle` → `surface-raised`). Nothing moves.
- **Place:** in the top bar's center (P1-04), centered on the screen. P1-12 adds Export at the right; its draft spec drops the board's separate quality pill ("1080p"), so the ratio pill stays alone in the center.
- **Board differences:** the board's pill is 32 px with no target; here it's 32 dp drawn inside a 48 dp target.

### Canvas panel (ToolPanel)
ToolPanel rules (`design/system/components/ToolPanel/README.md`): title "Canvas", Done (Ghost), content scrolls under a fixed top row, rows `space-4` apart. Rows, top to bottom:

**1. Ratio.** Heading "Ratio" (`label` `text-secondary`), then a horizontally scrolling row of six OptionTiles (`design/system/components/OptionTile/README.md`, new; stacked layout), `space-2` gaps, `space-4` side padding, scrolling edge to edge:

| Tile | Shape (drawn) | Label |
| --- | --- | --- |
| 9:16 | a solid 11 × 20 rectangle | 9:16 |
| 1:1 | 20 × 20 | 1:1 |
| 4:5 | 16 × 20 | 4:5 |
| 3:4 | 15 × 20 | 3:4 |
| 16:9 | 20 × 11 | 16:9 |
| Custom | icon `ratio_custom` | Custom |

- The shape is the ratio itself, drawn as a solid rectangle with 1 dp corners whose long side is 20 dp inside the 24 dp icon box (icon grammar: the central 20 px). It's layout math from the ratio, not an icon file.
- The selected tile is scrolled into view when the panel opens and when undo or redo changes the selection (review P1): with Custom selected, at 200% or in Spanish and Portuguese, it is otherwise fully off screen while its steppers show.
- Order is the PRD's. At 360 dp in English, 9:16 to 16:9 show in full (tiles about 48–54 dp wide) and Custom is cut by the edge, which shows the row scrolls (FontTile pattern). Longer translations of "Custom" only push it further out.
- Shape and label in `text-secondary` and `text` on `surface-raised`; the selected tile is white (`selection` fill, both in `on-primary`).

**2. Custom ratio** (only while Custom is selected). Two stepper rows directly under the ratio tiles, in the Position panel's row pattern (P1-10): a `body` `text-secondary` label at the start, then IconButton `minus`, a readout, IconButton `plus` at the end.

| Row | Readout | Step |
| --- | --- | --- |
| Width | "9" (`timecode` `text`, at least `touch-target` wide) | ±1, whole numbers 1 to 99. Holding repeats after 400 ms, every 50 ms. |
| Height | "16" | same |

- Limits (explain on tap, P1-06 rule): a step that would make the long side more than **2.39 times** the short side doesn't apply ("This is as wide as a video can be." / "This is as tall as a video can be."). Below 1 or above 99: "1 is the smallest number." / "99 is the biggest number." 2.39:1 covers every format people post (21:9 is 2.33, a 9:19.5 phone screen 2.17) and is the widest range X accepts for video, the strictest major platform (Evidence). P1-12 checks the encoder separately.
- No text field and no keyboard: steppers are reachable with one thumb, by keyboard and by TalkBack, and the keyboard would cover the panel.

**3. Clips.** One row: the label "Clips" (`body` `text-secondary`) at the start, two OptionTiles (inline layout: icon beside label) at the end, `space-2` apart: **Fit** (icon `fit`) and **Fill** (icon `fill`). When they don't fit beside the label (200% font), they wrap under it; if the pair is still too wide, Fill wraps under Fit (review P3: pt at 200% leaves about 6 dp to spare on 360 dp).
- **Fit:** the whole clip shows, scaled down to fit inside the frame; the background shows around it.
- **Fill:** the clip covers the whole frame, scaled up and centered; the edges that don't fit are cut off.
- **It applies to every main clip at once.** Users fix black bars for the whole meme, not clip by clip (Clipchamp's per-clip Fit/Fill and its "no way to set all clips at once" are the counter-example; Guidance). The model's per-clip `fit` stays, for later per-clip framing (Proposals 2).
- **Mixed** (defensive: an older debug draft, or a later per-clip feature): neither tile is selected; a tap sets every clip.

**4. Background.** Heading "Background", then a horizontally scrolling Swatch row (`design/system/components/Swatch/README.md`): **Blur** first (the new blur variant), then the ten caption palette colors in palette order (White, Black, Yellow, …). Selected: the white ring.
- **When nothing would show the background** (every main clip is Fill, or every clip already has the canvas's shape within 1%), a line under the swatches in `caption` `text-secondary`: "Your clips fill the frame, so the background doesn't show." The swatches still work and the choice is saved: it shows as soon as a clip fits. This explains instead of disabling (design system: keep controls enabled and explain).

**Panel height** (100% font, Custom not selected): about 352 dp of content (top row 48, Ratio 84, Clips 48, Background 72, plus the gaps). On 412 × 915 (348 dp region) it just scrolls; on 360 × 800 (302) the swatch row is cut by about 14 dp and the panel scrolls; on 360 × 640 (229) it scrolls further. The order follows the dependency (shape, then how clips sit in it, then what shows around them) and most-used first (Assumption).

### The frame edge on the preview
P1-04 left showing the canvas boundary to this ticket. On a black stage, a black background makes the frame invisible, so a fitted landscape clip and a caption near the edge give no hint where the meme ends.
- **What:** a `stroke-hairline` line in `text-muted` around the canvas frame, drawn **just outside** it on the `stage`, so it never covers the user's content and is never exported.
- **When:** while the Canvas panel is open, and while a caption is being moved, sized or turned on the preview (P1-10 gestures), when the edge matters. Never otherwise: the meme is the most colorful thing on screen, and a permanent line would be chrome on content.
- **Contrast:** `text-muted` on `stage` black 6.9:1 (SC 1.4.11 needs 3:1). It sits in the frame's `space-2` inset, so it always has room.
- This is the third border exception in DESIGN_SYSTEM (Radii, borders, shadows), alongside the low-contrast swatch ring and the content-selection edge.

## Ratio change: what happens to clips and layers
One tap on a ratio is **one undo step** ("Ratio") that changes the canvas and, in the same step, anything the rules below move.

**Canvas size** (saved as `Canvas.ratio`, `widthPx`, `heightPx`; export scales it to the chosen resolution, P1-12). The shorter side is always about 1080 px, so captions keep their size across every ratio (P1-10 sizes them from the shorter side):

| Ratio | Canvas px |
| --- | --- |
| 9:16 | 1080 × 1920 |
| 1:1 | 1080 × 1080 |
| 4:5 | 1080 × 1350 |
| 3:4 | 1080 × 1440 |
| 16:9 | 1920 × 1080 |
| Custom w:h | `w·k × h·k`, where w:h is reduced to lowest terms and k is the even whole number nearest 1080 ÷ min(w, h). Both sides come out even (encoders need even sizes) and the ratio stays exact, so the pill can show it back in lowest terms. 5:8 → 1080 × 1728; 7:16 → 1078 × 2464. |

**Main clips** need no change: each is drawn Fit or Fill into the new shape.

**Layers** (captions now; stickers and overlays later, through their transform):
1. Every layer keeps its `centerX`, `centerY` (fractions of the canvas), scale and rotation.
2. **A layer that was fully inside the old frame must be fully inside the new one.** Its box is its laid-out block (outline included, P1-10 geometry), turned by its rotation; "inside" means that box lies within the canvas.
   - If it now crosses an edge, it moves straight in, just far enough to sit a margin of 2% of the canvas's shorter side inside that edge (22 px at 1080).
   - If it's too big for the new frame (taller or wider than the canvas less two margins), it first shrinks, keeping its center, until it fits, never below P1-10's 25%, then moves in as above.
3. **A layer the user had placed partly outside** (P1-10 lets text hang off the frame) keeps its center fractions and size. It was outside on purpose, and its center stays within the frame (P1-10 center limits), so it's never lost.
4. If any layer moved or shrank, a toast explains: "Text moved to stay inside the frame." Undo puts everything back in one step.

- **Why "fully inside stays fully inside"** and not "everything inside": the Done when says the ratio change *keeps* layers inside, which is about not pushing a caption off the frame. Pulling in a caption the user deliberately hung off the edge would undo their choice (Proposals 4 asks the PM to confirm this reading).
- **Round trips:** going 9:16 → 16:9 → 9:16 may leave a pulled-in caption where it was pulled to. Undo is the exact way back; the minimal-move rule keeps the drift small.
- **Playhead, timing and the timeline** don't change. No item moves in time.

## Rendering rules (preview and export match)
The engine draws the canvas the same way in the preview and the export, at any resolution. These rules are part of every saved draft's look, so they're fixed; they're also in `design/tokens.json` → `canvas`.

| Rule | Value |
| --- | --- |
| Fit | The clip scaled uniformly so the whole frame fits inside the canvas, centered (Media3 `LAYOUT_SCALE_TO_FIT`, already mapped) |
| Fill | Scaled uniformly to cover the canvas, centered, the overflow cropped (`LAYOUT_SCALE_TO_FIT_WITH_CROP`, already mapped) |
| Color background | The color fills the canvas behind every fitted clip |
| Blur background | Behind each fitted clip, a copy of **that same clip** (the one playing at that moment) scaled to Fill and blurred with a Gaussian blur of sigma = **3% of the output's shorter side** (32 px at 1080p, 14 px at 480p), so the look is identical at every resolution and preview size. Full brightness, no darkening. |
| Gaps and missing clips | Black, whatever the background. A missing clip is an error state, black keeps P1-04's "plays black" true, and it stands out against any color. |
| Captions | Above everything here (P1-10), clipped at the canvas edge |

**Build note (review):** while Blur is the background, the preview renders at a 720 px shorter side to hold 30 fps; the sigma follows that output (3% of 720 = about 22 px), so the softness looks the same as the 1080p export. With a color background the preview keeps its normal size.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default (panel closed) | The pill "9:16" in the top bar; no frame edge | `canvas_pill_a11y` |
| Panel open, a preset ratio | Pill in the active look; 9:16 tile white; Fit white; Black ringed; frame edge on | `canvas_title`, `canvas_ratio`, `canvas_clips`, `canvas_fit`, `canvas_fill`, `canvas_background`, `canvas_background_blur`, `color_*`, `panel_done` |
| Custom selected | Custom tile white; Width and Height steppers under the tiles; pill shows the ratio in lowest terms | `canvas_ratio_custom`, `canvas_custom_width`, `canvas_custom_height`, `canvas_custom_*_a11y` |
| Stepper at a limit | Toast explains; nothing changes | `explain_canvas_widest`, `explain_canvas_tallest`, `explain_canvas_min`, `explain_canvas_max` |
| Ratio changed, a caption pulled in | The new frame; the caption at its new place; toast | `canvas_layers_moved` |
| Background can't show | The hint line under the swatches | `canvas_background_hidden` |
| Blur background | Fitted clips over a blurred, filled copy of themselves | none |
| Mixed fit (defensive) | Neither Fit nor Fill selected | none |
| Preview catching up after a choice | The P1-06 rule: last frame kept, "Updating the preview" after 1 s. The frame changes shape at once; until the first frame at the new shape arrives, the frame shows the canvas background color (black for Blur), never a stretched old frame. | P1-06 |
| Reorder mode | Pill explains on tap | `explain_canvas_reorder` |
| Empty project (P1-04 defensive) | The pill works; the frame shows the background color (black under Blur) | none |
| A clip's file is missing | It plays black (Rendering rules), as P1-04 says | P1-04 |
| Long content (5 languages, 200% font) | Tile labels grow; "Custom" is the longest ("Personalizado", 13 characters); the Clips tiles wrap under their label; panel scrolls | none |
| Offline, permissions | Don't apply: everything is on the phone | none |
| First run | Same as every run. No coach mark: the pill always shows the ratio, which is the hint. | none |

## Interactions
- **Ratio tile:** a tap applies at once (one step "Ratio"). Tapping the selected tile does nothing and records nothing.
- **Custom tile:** a tap selects Custom and shows the steppers, filled with the current ratio's terms (from 9:16: 9 and 16). Nothing changes yet and no step is recorded, so the picture doesn't jump. The first stepper change makes the canvas custom (one step "Ratio").
  - While holding a stepper, the readout and the pill change on every repeat; the canvas and the preview update once, on release (one step). A tap is one step.
  - During the visit the steppers show the numbers as stepped (10 and 16), even when the ratio reduces (5:8 on the pill); reopening the panel shows lowest terms (5 and 8). Reducing on every step would make the numbers jump under the thumb.
  - If undo (or a preset tile) leaves Custom, the stepper rows go.
- **Fit / Fill:** a tap sets every main clip (one step: "Fit" or "Fill"). The selected one does nothing on tap.
- **New clips follow the project:** Add media (P1-16) gives new clips the fit every main clip shares (Fit when mixed). Replace, Split and Duplicate keep the clip's own fit.
- **Background swatch:** a tap applies (one step "Background"). The selected one does nothing.
- **The preview while the panel is open:** a tap on the stage plays or pauses, including over a caption (no caption selection while the panel is open: the panel is about the whole frame, and its title and focus would contradict a selected caption's tools). Two-finger gestures do nothing.
- **Undo and redo** stay in the transport row, above the panel. After an undo, the panel's tiles and swatches show the restored values (ToolPanel rule).
- **Haptics:** none. Choices land on the preview, and nothing snaps.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens or closes | 200 ms slide (ToolPanel) | 120 ms fade (instant on Android) |
| Pill to and from the open look | 120 ms color change (`duration-press`) | same |
| Canvas frame changes shape | At once, like undo: a morph would show a frame that's neither ratio, and the engine renders only the final shape | same |
| Caption pulled inside | At once, with the frame | same |
| Custom stepper rows appear or go | 120 ms fade, rows below move at once | same |
| Frame edge appears or goes | 120 ms fade | same |
| Toasts | 120 ms fade (P1-04) | same |

## Copy
English source; the engineer machine-drafts id, es, pt and hi until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `canvas_pill_a11y` | Canvas, %1$s | 30 | Spoken name of the pill; %1$s = the ratio as shown ("9:16"). The name must contain the visible label, so speech input ("tap 9:16") still works (WCAG 2.5.3); "Canvas" adds what it opens. |
| `canvas_title` | Canvas | 20 | Panel title. The frame the video is made in, its shape and what's behind the clips. Not "painting canvas"; use your language's video-editor word (CapCut's local label for this tool is a good guide). |
| `canvas_ratio` | Ratio | 20 | Row heading: the shape (width to height) |
| `canvas_ratio_custom` | Custom | 14 | Tile label: a shape the user sets. Short: it's a tile. |
| `canvas_ratio_a11y` | %1$s, %2$s | 40 | Spoken tile name: the visible label, then what it means: "9:16, tall"; also "Fit, Shows the whole clip" (review M2). %1$s comes first in every language (the name starts with the visible label, WCAG 2.5.3). |
| `canvas_shape_tall` | tall | 12 | Lower case: follows the ratio in `canvas_ratio_a11y` |
| `canvas_shape_square` | square | 12 | |
| `canvas_shape_wide` | wide | 12 | |
| `canvas_custom_width` | Width | 16 | Stepper row label: the first number of the ratio |
| `canvas_custom_height` | Height | 16 | The second number |
| `canvas_custom_width_down` | Decrease width | 24 | Spoken label of − |
| `canvas_custom_width_up` | Increase width | 24 | Spoken label of + |
| `canvas_custom_height_down` | Decrease height | 24 | |
| `canvas_custom_height_up` | Increase height | 24 | |
| `canvas_clips` | Clips | 16 | Row label: how the videos and photos sit in the frame |
| `canvas_fit` | Fit | 12 | Show the whole clip, with space around it. Short: a tile beside the label. |
| `canvas_fill` | Fill | 12 | Cover the whole frame, cutting the edges |
| `canvas_fit_a11y_hint` | Shows the whole clip | 30 | Spoken after "Fit", as the second part of its name (`canvas_ratio_a11y`), not the state description (review M2) |
| `canvas_fill_a11y_hint` | Covers the whole frame | 30 | Spoken after "Fill" |
| `canvas_background` | Background | 20 | Row heading: what shows around clips that don't cover the frame |
| `canvas_background_blur` | Blur | 16 | Spoken name of the blur swatch: a blurred copy of the clip behind it |
| `canvas_background_hidden` | Your clips fill the frame, so the background doesn't show. | 70 | |
| `canvas_layers_moved` | Text moved to stay inside the frame. | 50 | Toast after a ratio change moved or shrank a caption. P4-12 widens it when stickers exist. |
| `explain_canvas_widest` | This is as wide as a video can be. | 50 | |
| `explain_canvas_tallest` | This is as tall as a video can be. | 50 | |
| `explain_canvas_min` | 1 is the smallest number. | 40 | Use your language's digit |
| `explain_canvas_max` | 99 is the biggest number. | 40 | |
| `explain_canvas_reorder` | Tap Done to finish reordering first. | 50 | "Done" must match `panel_done` exactly |
| `edit_canvas_ratio` | Ratio | 24 | Edit names, after "Undo: " (P1-04) |
| `edit_canvas_fit` | Fit | 24 | |
| `edit_canvas_fill` | Fill | 24 | |
| `edit_canvas_background` | Background | 24 | |

New in `:core:designsystem` (review M1, for every SettingPill):

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `setting_expanded` | Expanded | 16 | Spoken state of a top-bar pill whose panel is open. Use the word your language's TalkBack uses for an open dropdown. |
| `setting_collapsed` | Collapsed | 16 | Its panel is closed |

Reused: `panel_done`, `color_*` (P1-10, the swatch names), P1-04's `a11y_*`.

Review note on `explain_canvas_reorder`: pt "Toque em Concluído para terminar de reordenar primeiro." is 56 of 50; use "Toque em Concluído para terminar de reordenar." (46).

Translator notes:
- **Ratios** ("9:16") are never translated or localized: ASCII digits and a colon in every language, like timecodes.
- **Fit and Fill** are the pair every video editor uses; keep the two words short and clearly different. Look at what CapCut or the phone's own photo editor uses in your language.
- **Hindi:** interface text falls back to the system Devanagari face; no row here has a fixed text height.

## Accessibility
- **Focus and TalkBack order** (P1-04's list): Close → **ratio pill** → (P1-12's Export) → save banner → preview → … With the panel open, after the transport row: the panel title (pane title, focus lands here on open) → Done → Ratio group → Width and Height rows (when shown) → Clips group → Background group → the hint line. Closing returns focus to the pill.
- **The pill:** a button. Name "Canvas, 9:16"; state "Expanded" while the panel is open, "Collapsed" otherwise, spoken as the state description (`setting_expanded`, `setting_collapsed`; review M1: Compose's expand/collapse are actions, which TalkBack doesn't speak as a state). Double-tap opens or closes the panel.
- **Ratio tiles, Fit/Fill and swatches** are single-choice groups named by their heading ("Ratio", "Clips", "Background"). Each option reads its name ("9:16, tall", "Fit, shows the whole clip", "Blur", "Yellow"), "selected" when it is, and its position ("1 of 6"). Fit and Fill carry their meaning in the name, after the visible label (`canvas_ratio_a11y` with the hint), never in the state description, which would replace "selected" (review M2; this spec first said state description, my error).
- **Captions on the preview while the panel is open** stay readable ("Text: …") but take no double-tap and offer no Style or Position action, matching "no caption selection while the panel is open" (Interactions; review M3).
- **Steppers:** the readout is the row's state ("Width, 9"). After each step the new ratio is announced politely ("5:8"). Limit toasts are announced.
- **After a ratio change** the selected tile's state changes (TalkBack reads it) and, when it happens, the toast is announced politely. Focus stays on the tile.
- **What the panel covers** (timeline and tool bar) leaves the accessibility tree and focus (`coveredByModal`, ToolPanel rule).
- **Keyboard in P1** (basic access): Tab reaches the pill and every panel control; Enter activates; Escape closes the panel. Shortcuts: Later (L-01).
- **Touch targets:** the pill, every tile, stepper button and swatch is 48 dp or more.
- **Contrast:**
  - Pill: `text` on `surface-raised` 12.4:1; open `primary` on `primary-subtle` over `canvas` 6.2:1; pressed-open `primary` on `surface-raised` 5.9:1.
  - OptionTile: label `text` on `surface-raised` 12.4:1, shape and icon `text-secondary` 5.7:1; selected `on-primary` on white 18.9:1.
  - Swatches: Swatch README (white ring 17.2:1 on `surface`; Black gets the `text-muted` ring). The blur swatch's icon `text-secondary` on `surface-raised` 5.7:1.
  - Hint line `text-secondary` on `surface` 7.1:1. Frame edge 6.9:1 on `stage`.
- **200% font:** tile labels grow and the tiles widen and grow taller (the shape stays 24 dp); the Clips tiles wrap under the label; stepper rows wrap their controls under the label; the pill grows taller with its text and the top bar grows with it (P1-04's top bar only keeps its height while it holds icons). Nothing is cut.
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Once per committed step (never while holding a stepper; never when the pill or a stepper only explains; never for opening the panel or tapping a selected option) | `editor: "video"`, `tool`: `canvas_ratio`, `canvas_fit`, `canvas_background`; `value` (owner, 21 Oct): `canvas_ratio` → `9:16`, `1:1`, `4:5`, `3:4`, `16:9` or `custom` (never the custom numbers); `canvas_fit` → `fit` or `fill`; `canvas_background` → `blur` or `color` (never the color itself) |

- The ids are confirmed (PM, 20 Oct). They are also edit names in the undo history (TECHNICAL_DESIGN → Editing).
- `value` is for these three tools only; any other use comes back as a proposal (decision log, 21 Oct).
- Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `canvas-check` (request 10).
1. **Panel open** at 360 × 800, default project: the pill in its open look, 9:16 tile white, Fit white, Black ringed, the frame edge around a fitted landscape clip; the swatch row cut at the bottom edge.
2. **Each preset ratio** (1:1, 4:5, 3:4, 16:9) on `canvas-check`: the frame's shape, the captions, and after 16:9 the "Text moved…" toast with the top caption pulled in and the off-frame caption unchanged.
3. **Custom:** steppers at 2:3, then the width-limit toast at 12:5 (2.4); the pill reading the ratio in lowest terms after 10:16 ("5:8").
4. **Backgrounds:** Blur and Yellow behind a landscape clip in 9:16; then Fill, with the "background doesn't show" line.
5. **Hindi at 200% font**, 360 × 640: pill, panel scrolled to each row, Clips tiles wrapped under their label.
6. **A caption being dragged** on a black background: the frame edge visible on the stage, gone on release.

**Measured: the Done when and preview = export** (note results in the QA report):
- **Layers inside:** on `canvas-check`, go 9:16 → 16:9 → 1:1 → 4:5 → custom 9:21 → 3:4. After each, screenshot the preview: every caption that started fully inside is fully inside (outline included), at least 2% of the short side from the edges it was moved off; the deliberately off-frame caption keeps its place. One undo restores the previous ratio and every caption exactly.
- **Preview = export:** export `canvas-check` at 1080p30 in 9:16 Blur, 1:1 Yellow and 16:9 Fill. Pull frames at three times with ffmpeg; compare with preview screenshots cropped to the frame and scaled to the export width. Pass: the same crop and placement within 1% of the width, the same background color sampled outside the clip, the blur visually alike (no sharp copy, no darkening, same softness at 720p and 1080p).
- **Reference render:** those three 1080p frames go in `docs/qa/references/P1-11-canvas/` (CLAUDE.md rule 5), for iOS parity in P7.
- **Responsiveness:** a ratio tap shows on the preview within 100 ms of the screen recording, or "Updating the preview" after 1 s. Preview with Blur on the 1080p `preview-check` draft: 30 fps or more on the owner's phone (release-type build, Media3 logs as in P1-04).
- **Leaks:** LeakCanary on a debug build. Open the panel, cycle all ratios, step a custom ratio, switch backgrounds; close the editor; repeat 5 times. No leak.

## Model
**No new fields and no schema bump.** P1-01 already saves everything P1-11 needs:

| Need | Model |
| --- | --- |
| Ratio | `Canvas.ratio` (`9:16`, `1:1`, `4:5`, `3:4`, `16:9`, `custom`) |
| Size | `Canvas.widthPx`, `heightPx` (the Canvas size table) |
| Background | `Canvas.background`: `Solid(color)` or `Blur` |
| Fit or Fill | `MediaClip.fit` on every main clip (the panel sets all of them) |
| Layer moves | `TextItem.transform` (`centerX`, `centerY`, `scale`) |

The blur strength, the layer margin, the custom-ratio limit and the size rule are fixed rules (Rendering rules, Ratio change). Making any of them a choice later needs a new field and a schema bump. A background image (PRD) would be a new `CanvasBackground` subtype and a bump (Proposals 1).

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Edits** in `:core:domain` (pure functions returning a new `Project`, one `commit` each):
   - `setCanvasRatio(ratio, w?, h?)`: the size table, the custom `k` rule, and the layer rule (Ratio change → Layers) in the same step. Return whether any layer moved, for the toast.
   - `setClipsFit(fit)`: every main clip.
   - `setCanvasBackground(background)`.
   - Add media (P1-16 `insertMedia`) gives new clips the shared fit (Fit when mixed). Replace keeps the clip's fit.
   - The layer rule needs each caption's laid-out box: use the one caption renderer's layout (P1-10 request 1), measured in canvas pixels, never a second measuring path.
2. **Engine (preview and export alike, from the same plan):**
   - Draw the color background behind fitted clips.
   - Blur: verify Media3's `GaussianBlurWithFrameOverlaid(sigma, scaleSharpX, scaleSharpY)` against the current docs at ticket start: it draws a frame over a blurred copy of itself, which is this look. Sigma is in pixels of the frame it blurs, so compute it from the output size (3% of the shorter side) for each preview size and export resolution. The blur layer may be rendered at reduced resolution in the preview if that's what keeps 30 fps; it's blurred anyway.
   - Gaps and missing clips stay black whatever the background.
   - Remove "the canvas background (P1-11)" from TECHNICAL_DESIGN's "Not rendered yet" list and describe the rules there.
3. **Preview frame:** on a ratio change the frame changes shape at once, and no stretched or squashed old frame is ever shown: the frame shows the background color (black for Blur) until the first frame at the new size arrives. Check how `PlayerSurface` (a `SurfaceView`) behaves on resize against Media3's docs, and keep P1-06's "no black or gray flash" for every other edit.
4. **Frame edge:** draw it outside the canvas frame while the Canvas panel is open and while a caption gesture is in progress (P1-10).
5. **Pill:** the SettingPill component in `:core:designsystem` (README); the ratio label from `Canvas` in lowest terms (already in `VideoEditorViewModel.ratioLabelOf`; with the custom `k` rule its GCD gives the exact ratio).
6. **Panel:** `CanvasPanelControl` behind `EditorHost`, like `VolumePanelControl`; Custom stepper values live in panel UI state for the visit (Interactions); hold-repeat as P1-10's Move arrows, committing on release.
7. **Components:** OptionTile (new README; stacked and inline layouts) and the Swatch blur variant, in the debug catalog.
8. **Icons**, drawn to the grammar (DESIGN_SYSTEM → Iconography): `fit` (a stroked tall frame with a solid wide block inside it, touching its sides), `fill` (the same frame filled solid, with short strokes beyond its left and right sides for the cut edges), `blur` (a solid dot inside a ring of four short strokes), `ratio_custom` (a stroked rectangle with a solid square at its bottom-right corner, the resize handle). The ratio tiles' shapes are drawn from the ratio, not icon files.
9. **Canvas constants** (the size table, the custom rule and limit, blur sigma, layer margin) in one commonMain place next to the caption constants, with a comment pointing to this spec and `design/tokens.json` → `canvas`.
10. **Debug sample `canvas-check`** (opened with `memix.openEditor`): a 16:9 video, a 9:16 video and a 4:3 photo on the main track; captions: one large two-line caption near the top (scale 200%), one turned 30° near the right edge, one deliberately half off the left edge, one in the middle. Black background, Fit.
11. **Analytics:** as in the table, with `value`.

## Changes to other specs (made with this spec)
- P1-04 → Preview stage: the frame-edge line now points here; the missing-clip state plays black whatever the background.
- P1-06 → Add media: new clips take the project's shared fit. Replace: the clip's fit is kept.

## Proposals and questions for the PM
All answered (owner, 21 Oct; PM, 20 Oct); kept for the record.
1. **Answered** (owner, 21 Oct, B): new ticket P2-12, background image in both editors. P1-11 stays color and blur. **Background image (PRD scope gap).** The PRD's video canvas has "background color, blur **or image**"; P1-11 covers color and blur, as the ticket says. The photo editor's canvas (PRD: "background color or image") has no ticket that names it either; P2-01 says only "canvas". Owner decision below.
2. **Answered** (owner, 21 Oct, A): new ticket P4-16, clip crop, rotate, flip and per-clip Fit or Fill, after P4-10. **Per-clip framing.** P1-11 sets Fit or Fill for every clip at once; the model keeps a per-clip `fit` for later. The PRD's video "crop, rotate, flip" (Basic edits) has no ticket (P4-10 covers reverse and freeze only), and per-clip fit belongs with it. I recommend a P4 ticket "Clip crop, rotate, flip and per-clip fit" after P4-01, which builds the transform gestures it needs. This is a scope proposal; it doesn't block P1-11.
3. **Answered** (owner, 21 Oct, A): `value` added; see Analytics. **Analytics value** (PRD change, like the 9 Oct `font` parameter): an optional `value` on `tool_use` for the canvas tools: the ratio (`9:16` … `custom`), the fit (`fit`, `fill`), the background (`blur`, `color`, never the color itself). It tells which shapes people make, which informs template shapes in P4-05 and the default ratio. Recommend yes.
4. **Answered** (PM, 20 Oct): confirmed; TICKETS' Done when reworded to match. **Done when reading.** I read "keeps every layer inside the frame" as: a layer fully inside stays fully inside; a caption the user hung off the edge on purpose keeps its place (Ratio change → Layers). Please confirm, so QA tests the same thing.
5. **Answered** (P1-12 final spec → What the phone can do): sizes are checked at the real canvas size with the long side as width, and a size the shape can't do explains with its own toast. **For P1-12 (FYI):** canvases are about 1080 px on the shorter side, and custom ratios go up to 2.39:1 (31:13 is 1092 × 2604, the extreme). P1-12's capability check must handle the long side above 1920 at "1080p" (the resolution names the shorter side) and say what happens when an encoder can't.

## Decision for the owner: background image (decided 21 Oct: B, ticket P2-12)
- **Problem:** the PRD promises a background image for the video canvas (and the photo canvas). No ticket builds it. Without it, users who want a picture behind a fitted clip have no way to do it until overlays (P4-01).
- **Considerations:**
  - **A. Add it to P1-11 now:** about 1 more day in a full Phase 1, a schema bump (a new `CanvasBackground.Image` with a picked photo), the P1-02 picker reused. Users get it in the first test build.
  - **B. One ticket in Phase 2 for both editors' canvas background image,** next to P2-01: built once, one schema bump, the photo editor needs it anyway. About 1 day in P2. Video users wait about four weeks.
  - **C. Drop it from v1:** no cost; P4-01 overlays can approximate it (an overlay photo at the bottom of the stack). The PRD line changes.
- **Recommendation: B.** It's the same feature in both editors, and Phase 1 has no slack.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Media3 has a Gaussian blur effect that overlays the sharp frame on its blurred copy (`GaussianBlurWithFrameOverlaid(sigma, scaleSharpX, scaleSharpY)`); sigma is in pixels | Guidance (API reference) | [GaussianBlurWithFrameOverlaid](https://developer.android.com/reference/androidx/media3/effect/GaussianBlurWithFrameOverlaid), [GaussianBlur](https://developer.android.com/reference/androidx/media3/effect/GaussianBlur) |
| Clipchamp: ratio picked at the top beside Export; Fit and Fill are per clip, with no way to set all clips at once; newly added footage fills the chosen ratio | Guidance (vendor help) | [Clipchamp: change the aspect ratio of a video](https://support.microsoft.com/en-us/clipchamp/how-to-change-the-aspect-ratio-of-a-video) |
| InShot's Canvas offers a background color, blur or image behind a clip that doesn't fill the frame | Assumption-level evidence (third-party tutorials) | [CapCut's InShot tutorial](https://www.capcut.com/resource/inshot-tutorial), [InShot overview](https://discourse.weareopen.coop/news/inshot-your-go-to-video) |
| CapCut puts ratio and background (color, blur) under a Canvas tool | Assumption (product knowledge; CapCut's mobile help wasn't reachable, and search found only low-quality pages) | — |
| X accepts video from 1:2.39 to 2.39:1 | Evidence (consistent secondary sources; no current official page found) | [Social Media Today](https://www.socialmediatoday.com/news/twitter-updates-video-playback-to-ensure-a-higher-quality-viewing-experienc/607184), [Typito](https://typito.com/blog/twitter-video-requirements) |
| Non-text indicators need 3:1 against adjacent colors | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| A control's accessible name includes its visible label | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Dragging needs a single-pointer alternative (the steppers, not a ratio slider) | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
