# Image tools: crop, rotate, flip, straighten, adjustments, 20 filters, deep-fry · P2-04

Fifth spec for the photo editor. It reuses, and does not repeat:
- `P2-01-photo-editor-shell.md`: the stage, layers and their gestures, the selected-layer bar, the Layers panel and rows, lock and hide, `drawPhotoScene` and the render hook, the "size at scale 1" rule.
- `P2-02-meme-formats.md`: the picture area and "as placed" (a crop changes a picture's shape, below); Panels' cells (not touched here).
- `P2-03-photo-text-and-stickers.md`: the locked-layer refusals and the main bar.
- `P1-11-canvas.md`, `P1-10-text.md`: `OptionTile`, `Slider`, the panel patterns.
- Slider and SegmentedTabs READMEs under `design/system/components/`.

## Job
"Fix the photo, then caption it": crop it, turn it, straighten it, brighten it, put a look on it, or fry it for the meme. **Done when** (TICKETS, reworded 2 Nov): the 20 filters, the adjustments and deep-fry each have a stable id and a reference render; on an image layer the editor and the render hook at 1080 and 2160 show the same look; crop, rotate, flip and straighten keep the layer editable and give the same picture in the editor and the render; the ids and LUT data are the ones P4-07 reuses for video.

**The one blue action:** none in the panels. The **Crop screen is a mode of its own with one blue action, Done** (the editor's "Apply", as the design system allows one per screen). White is selection (the selected tile ring, the crop frame, the handles).

**Fast path, with tap counts:**
- **A filter on the only photo:** Edit photo (1) on the main bar, a filter (2), Done (3). The tool selects the main photo for you.
- **Crop to a ratio:** Crop (1), a ratio tile (2), Done (3).
- **Brighten:** Edit photo (1), Adjust (2), drag Brightness, Done (3).
- **Deep-fry:** Edit photo (1), Deep-fry (2), drag Amount, Done (3).

## Decisions (D1 to D5)
- **D1. The tools act on the selected image layer, not the whole canvas** (the PM's recommendation, applied): the first photo is an ordinary layer (P2-01), every other photo is one too, and a caption or a sticker is not a photo. **Text and sticker layers show no image tools** (no dead controls). Stickers get them later if wanted (Later, no ticket). Photos held by Panels' cells (P2-02) are not layers and are not touched in v1.
- **D2. Crop does not change the canvas.** The cropped picture is **fitted inside the canvas** like any image layer (P2-01 → Size at scale 1), and the canvas keeps its shape. So a crop changes the layer's **picture shape**, not the canvas. Rotate, flip and straighten are parts of the same crop screen (one step).
- **D3. Adjustments are six sliders; filters are a strip of 21 tiles (None plus 20); deep-fry is one slider.** Each is a parameter on the layer, never a new file, so everything is editable until the end.
- **D4. The 20 filters have stable ids and names below** (never renamed; owner, 2 Nov: original looks made by a script, no third-party pack).
- **D5. A layer with any edit shows "Edited" in its Layers row.**

## Entry and exit
- **The tools:** two tools for image layers, **Crop** (icon `crop`) and **Edit photo** (icon `adjust`).
  - **Selected image layer's bar:** **Position · Opacity · Delete · Edit photo · Crop · Remove BG (P2-07) · Duplicate · Lock · Hide · Layers** (P2-01's bar, the two tools inserted after Delete so Delete stays in view at 360 dp; Unlock first when locked, as P2-01). At 360 dp Position, Opacity, Delete and Edit photo show whole; Crop is the fifth place and is cut in half (the scroll cue). It is the one-level-down route for any photo.
  - **Main bar:** **Edit photo** and **Crop** sit after Draw (final order, set in the P2-05 spec: Formats · [format tool] · Text · Stickers (P2-14) · Add photo · Draw (P2-05) · **Edit photo · Crop** · Censor (P2-05) · Remove BG (P2-07) · Layers). They exist only while the canvas has at least one image layer. **Tapping one with nothing selected selects the main photo (the lowest image layer) and opens the tool on it**: one tap for the common single-photo case, and the white frame shows which layer is being edited. (This replaces P2-01's earlier "Adjust and Filters" and "Crop" slots, which had no target.)
- **Edit photo** opens the **Edit photo panel** (ToolPanel). **Crop** opens the **Crop screen** (below). Back, Done and Escape close the panel; the layer stays selected.
- **Locked layer:** Crop and Edit photo explain with P2-03's toast ("This layer is locked. Unlock it to change it."). **Missing file:** they explain (`explain_layer_missing`). **Hidden layer:** allowed; the panel shows a line "This layer is hidden, so you won't see the change." (the picture on the canvas doesn't change). They never act while a gesture is in progress.
- **Kept:** every edit is in the project (autosave, undo). Not kept: the panel's open tab (it opens on Filters).

## The Edit photo panel (ToolPanel)
Title "Edit photo", **Reset** (Quiet button: resets the open tab) and **Done**. Under the title, **SegmentedTabs** with three tabs: **Filters · Adjust · Deep-fry**. The canvas refits above the panel (P2-01), so every change is seen on the real frame. Canvas touches stay live (the layer can still be moved while the panel is open).

### Filters tab
- **The strip:** a horizontally scrolling row (padding inside the scroll, `space-2` gaps) of **21 tiles**: **None** first, then the 20 filters in the table order. Each tile is a **72 dp square** (`radius-md`) showing **the user's photo with that filter on** (the layer's picture, geometry edits included, the other color edits not), with the name under it (`caption`, `text`). The tile is at least 48 dp each way.
- **Selected:** a `stroke-selection` white ring around the tile (17.2:1 on `surface`), the name `text`. Unselected: no ring. Never `surface-raised`.
- **Tap:** applies the filter at once at **100%**, one undo step "Filter"; the selected one scrolls into view when the panel opens or after undo. Tapping the selected tile does nothing and records nothing. **None** removes the filter.
- **Intensity:** under the strip, while a filter is on: a **Slider** (`design/system/components/Slider/README.md`): label "Intensity", readout the percent in `timecode`, **0 to 100%**, default 100%, snap to 100% within ±3% with one SegmentTick. Live while dragging; one step on release ("Filter").
- **Thumbnails** are made on demand at 72 dp (about 216 px) from the picture already decoded at its drawn size, cached for the panel's visit, never from the original; the first visible tiles come first. A tile not yet made shows a `surface-raised` square (no spinner, nothing loops).

### Adjust tab
- **Six rows**, each a label at the start, the readout at the end, and a Slider under them:

  | Row | Range | Default | Note |
  | --- | --- | --- | --- |
  | Brightness | −100 to +100 | 0 | |
  | Contrast | −100 to +100 | 0 | |
  | Saturation | −100 to +100 | 0 | −100 is black and white |
  | Warmth | −100 to +100 | 0 | − is cooler (bluer), + is warmer (more orange) |
  | Sharpen | 0 to 100 | 0 | |
  | Vignette | 0 to 100 | 0 | Darkens the edges |

- **Snap to 0** within ±3 with one SegmentTick (Sharpen and Vignette at their start). **Keyboard and TalkBack steps of 10**, landing on multiples of 10 so 0 is reachable (Slider rules). Live while dragging; **one step on release**, "Adjust".
- **Reset** (header) sets all six to 0 in one step "Reset". There is no per-row reset (a tap on a readout would be hidden); the snap at 0 and the header Reset cover it.
- The panel **scrolls** (six rows of about 64 dp); the first three rows show at once at 360 × 800 inside the 40% cap.

### Deep-fry tab
- One row: **Amount** with a Slider, 0 to 100%, default 0 (off); a one-line hint under it: "Crunchy, oversaturated and grainy: the meme look." Live while dragging; one step on release ("Deep-fry"). **Reset** sets 0. Taking it from 0 to any value turns it on at that value.
- **What it does** (one recipe, scaled by the amount, fixed rules): boosts saturation and contrast, adds a warm-red tint, hard-sharpens, and adds a fixed grain; the numbers are in How it draws.

## The Crop screen (a mode, reusable)
Opens over the editor (200 ms slide up; reduce motion: 120 ms fade). It shows **the layer's whole picture**, with its rotation, flip and straighten applied, centered on the black stage, with a crop frame on it. P4-16 (video clip crop) **reuses this screen** as a component: it takes a picture (a bitmap or a video frame), a crop state and the aspect presets, and returns a crop state.

### Layout, top to bottom
1. **Top bar** (56 dp, `canvas`): **Close** at the start (`close`, spoken "Cancel crop"), the title "Crop", **Reset** (Quiet) and **Done** (Primary, the one blue action) at the end.
2. **Stage** (`stage` black): the picture, the crop frame, handles. The area outside the frame is dimmed by the `scrim`.
3. **Bottom area** (`surface`, about 230 dp on 360 × 640, as the content needs):
   - **Aspect row:** OptionTiles (stacked, scrolling): **Free · Original · Canvas · 1:1 · 4:5 · 3:4 · 9:16 · 16:9**. *Original* is the picture's own shape (after rotate and flip); *Canvas* is the canvas's current ratio (so a crop can fill the frame exactly). Ratios are drawn from data and never translated. Selecting a tile **replaces the frame by the largest centered frame of that shape inside the picture** and locks the frame's shape to it; **Free** unlocks. Default: Free, the frame the whole picture.
   - **Tool row:** four IconButtons (48 dp, spoken labels): **Rotate left**, **Rotate right** (`rotate_left`, `rotate_right`), **Flip horizontally**, **Flip vertically** (`flip`, one drawing, turned 90° for vertical), then the **Edges** button (a Secondary Button, label "Edges"): the non-drag route below.
   - **Straighten:** a **Slider**, label "Straighten", readout in degrees ("−3.5°"), **−45° to +45°** in 0.5° steps, default 0, snap to 0 within ±1° with one SegmentTick.

### The crop frame
- **Look:** two-tone as P1-10: `stroke-selection` white with a `stroke-hairline` `on-primary` edge; a **thirds grid** (1 dp lines, white at 40%, content-on-picture marks) shows **only while a handle or the picture is being dragged**.
- **Handles:** a white **L-shaped corner** (`canvas-handle-size` arms, the white `selection` fill with the `on-primary` edge) at each corner and a short white bar at each edge's middle; every handle has a **48 dp target** (inner halves when a handle sits at the stage edge). A one-finger drag on a corner or an edge resizes the frame; a drag inside the frame **moves** it. With an aspect locked, corners keep the shape and edges are inert.
- **Limits:** the frame stays inside the picture (never over empty corners) and is **at least 10% of the picture's width and of its height**; the drag simply stops. No toast while dragging.
- **Rotate and flip** (90° steps and mirrors) turn and mirror the **picture and the frame together** (the frame keeps its place on the picture); the picture is refitted to the stage.
- **Straighten** turns the picture under the frame; **the picture is scaled up as little as needed so the whole original picture stays covered** (no empty corners ever), so the frame never has to move. At 10° the scale is about 1.2×; the readout shows only the angle.
- **Edges (single-pointer route, WCAG 2.5.7 and 2.5.1):** the Edges button replaces the bottom area with a small ToolPanel "Edges": four rows **Left, Right, Top, Bottom**, each with a minus and a plus IconButton and a readout in percent of the picture (how much is trimmed from that side), **1% per tap**, hold repeats (400 ms, then every 50 ms). It covers moving and resizing the frame; with an aspect locked the opposite edge follows to keep the shape. Done in the panel returns to the Crop screen. Rotate, flip, Straighten and the aspect tiles are already single-pointer.
- **Done** applies everything made on the screen (crop, rotate, flip, straighten) as **one undo step "Crop"**, closes the screen and returns to the editor with the layer selected. **Close, system back and Escape discard** the screen's changes (no question: the screen is a mode and its changes were never applied; Assumption, QA watches for lost work). **Reset** returns the screen to the state it opened in.

### What a crop does to the layer
- The layer's **picture shape** becomes the crop's shape. Its transform (center, scale, rotation, opacity) is kept; its drawn size is recomputed as usual (**fitted inside the canvas at scale 1**, times its scale; a picture within 1% of the canvas's shape covers it).
- **A crop is not a canvas change:** the ratio-change rule is not triggered; the layer may now sit partly outside (center limits keep it findable).
- **Under a picture-area format** (P2-02) an **as-placed main photo is placed again** in the area for its new shape; a photo the user had moved stays.
- **Stored non-destructively** (Model): the file is never changed; the crop, rotation, flip and straighten are parameters, and the Crop screen reopens with them so the user can adjust the crop again, not only apply a new one.

## How it draws (preview, render and export match)
One declarative spec per tool, one pipeline in a fixed order. These rules are saved with every draft's look (`design/tokens.json` → `photo.imageTools`).

**Pipeline, in this order:** 1. geometry (rotate 90°, flip, straighten with its automatic scale-up, crop) → 2. brightness, contrast, saturation, warmth → 3. filter (LUT) at its intensity → 4. deep-fry → 5. sharpen → 6. vignette. Colors are processed in sRGB gamma space on 8-bit values, clamped after each step.

| Tool | Definition (starting values; the reference renders pin them) |
| --- | --- |
| Brightness *b* in [−1, 1] | add *b* × 0.5 to each channel |
| Contrast *c* in [−1, 1] | (value − 0.5) × (1 + *c*) + 0.5 |
| Saturation *s* in [−1, 1] | mix(luma, color, 1 + *s*), luma = 0.2126 R + 0.7152 G + 0.0722 B |
| Warmth *w* in [−1, 1] | R × (1 + 0.12 *w*), B × (1 − 0.12 *w*) |
| Filter | `out = mix(color, lut(color), intensity)`; the LUT is 33 × 33 × 33, **trilinear** sampling, the same data in every engine (P4-07 feeds the same data to Media3) |
| Deep-fry *a* in [0, 1] | saturation × (1 + 1.2 *a*), contrast × (1 + 0.8 *a*), warm-red tint (R × (1 + 0.10 *a*), B × (1 − 0.10 *a*)), a hard sharpen of strength 2 *a*, and a grain of amplitude 0.12 *a*. **The grain** is a fixed hash field over **cells 0.002 of the canvas's shorter side wide**, each pixel taking **the field averaged over that pixel's square** (resolution-independent: every output size draws the same grain, scaled; review 04b with the principal). **The sharpen** runs in **one combined unsharp pass with Sharpen**: the amounts add (deep-fry 2 *a* plus Sharpen *s* × 1.5) over the same radius, so the two never stack as two passes |
| Sharpen *s* in [0, 1] | unsharp mask, radius 0.0015 of the shorter side (a sigma in the same units, so 1080 and 2160 agree), amount *s* × 1.5; combined with deep-fry's sharpen in one pass (above) |
| Vignette *v* in [0, 1] | multiply by 1 − *v* × smoothstep(0.35, 1.0, *r*), *r* the distance from the center as a share of the half-diagonal |

**The 20 filters** (ids saved forever; recipes for the generating script, all original; contrast, saturation and tints as a share of the full range, "lift" raises the blacks, "gain" scales the whites):

| # | Id | Name | Look and recipe |
| --- | --- | --- | --- |
| 1 | `vivid` | Vivid | Punchy: saturation × 1.35, contrast +10% |
| 2 | `warm` | Warm | Golden hour: R × 1.10, B × 0.86, saturation × 1.12 (review 04b: stronger) |
| 3 | `cool` | Cool | Overcast blue: R × 0.88, B × 1.12, saturation × 1.05 (review 04b: stronger) |
| 4 | `mono` | Mono | Neutral black and white, contrast +5% |
| 5 | `noir` | Noir | Dark high-contrast black and white: S-curve +35%, gamma 1.10 |
| 6 | `fade` | Fade | Washed out: lift +8%, saturation × 0.80, contrast −10% |
| 7 | `vintage` | Vintage | Old print: lift +6%, saturation × 0.75, R × 1.06, B × 0.88, gain −4% |
| 8 | `film` | Film | 35 mm: contrast +12%, lift +3%, shadows teal (R −6%, G +3%), highlights warm (R +5%, G +2%), saturation × 0.90 (review 04b: stronger, so it differs from Cinema) |
| 9 | `sepia` | Sepia | Brown monochrome: luma tinted (1.00, 0.84, 0.62) |
| 10 | `cinema` | Cinema | Teal and orange: shadows R −10%, B +10%; highlights R +10%, B −10%; contrast +15% (review 04b: stronger) |
| 11 | `pastel` | Pastel | Soft and light: brightness +10%, saturation × 0.65, lift +6%, slight pink (R +3%, B +2%) (review 04b: brighter and pinker, so it differs from Fade and Matte) |
| 12 | `punch` | Punch | Meme boost: contrast +25%, saturation × 1.50 |
| 13 | `drama` | Drama | Moody: saturation × 0.70, contrast +30%, gamma 1.15, shadows cool |
| 14 | `sunset` | Sunset | Pink and orange: highlights R +12%, G −3%, B +4%; midtones R × 1.08; shadows B +8%; saturation × 1.25 (review 04b: stronger) |
| 15 | `neon` | Neon | Cross-processed: shadows G +8%, B +12%; highlights R +12%, B +10%; saturation × 1.45 (review 04b: stronger) |
| 16 | `aqua` | Aqua | Teal wash: R × 0.80, G × 1.04, B × 1.10, shadows teal (G +4%, B +4%), saturation × 1.15 (review 04b: stronger, greener than Cool) |
| 17 | `matte` | Matte | Flat and dull: lift +14%, gain −10%, contrast −5%, saturation × 0.95 (review 04b: highlights dulled, so it differs from Fade) |
| 18 | `chrome` | Chrome | Silver: contrast +30%, saturation × 0.70, highlights B +6% (review 04b: stronger) |
| 19 | `dusk` | Dusk | Purple twilight: gamma 1.15, R × 0.95, G × 0.90, B × 1.12, lift +3%, saturation × 0.85 (review 04b: purpler, so it differs from Cool) |
| 20 | `bleach` | Bleach | Bleach bypass: saturation × 0.55, contrast +28% |

Names are one short word each (budget 12). The looks are made by a script that writes the 20 LUT images; the script and a README saying the looks are original work live in the repo (decision, 2 Nov).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Selected image layer | The bar with Edit photo and Crop; no other layer kind has them | `tool_crop`, `tool_edit_photo` |
| Main bar, no image layer | No Crop or Edit photo (a text-only canvas, Panels' cells) | none |
| Edit photo panel, Filters | The strip (None selected), no intensity row | `edit_photo_title`, `filters_none`, `edit_photo_tab_filters` |
| A filter on | Its tile ringed, the Intensity row, the look on the canvas | `filters_intensity` |
| Adjust | Six sliders; the changed ones show their value | `adjust_*` |
| Deep-fry | Amount slider and the hint | `deep_fry_amount`, `deep_fry_hint` |
| Thumbnail not made yet | A `surface-raised` square | none |
| Hidden layer | The panel's hint line | `edit_photo_hidden` |
| Crop screen, just opened | The whole picture, the frame the whole picture, Free selected, Done enabled | `crop_title` |
| Crop screen, a handle dragged | The frame follows; the thirds grid; the dimmed outside | none |
| Crop screen, at the minimum | The frame stops at 10%; nothing else | none |
| Crop screen, straightened | The picture turned and scaled up; the readout in degrees | `crop_straighten` |
| Crop screen, Edges panel | Four rows with steppers; the readouts | `crop_edges` |
| Locked, missing file | The tools explain | `explain_layer_locked`, `explain_layer_missing` |
| An edited layer | "Edited" in its row's meta line | `layer_meta_edited` |
| Undo and redo | Each commit is one step; the panel or screen shows the restored values; a panel editing a layer that no longer exists closes | `editor_undo_toast` |
| A draft saved before this ticket | No edits; looks as before | none |
| Long content (5 languages, 200% font) | Tab labels and tile names wrap (the tile grows taller); the Crop screen's bottom area scrolls; nothing is cut | none |
| Offline, permission | Don't apply (everything is on the phone) | none |
| First run | Same as every run | none |

## Interactions
- **Applying:** a filter tap, an Intensity or an Adjust slider release, a Deep-fry release, a Reset and the Crop screen's Done are each **one undo step**, named "Filter", "Adjust", "Deep-fry", "Reset" and "Crop". Nothing is applied while a slider is dragged except on the preview.
- **Undo during a drag** is ignored (as everywhere).
- **Several edits on one layer** stack in the fixed pipeline order, whatever order they were made in.
- **Selecting another layer** while the panel is open closes the panel if that layer is not an image layer, and otherwise shows its values (the panel follows the selection).
- **Haptics:** SegmentTick at each slider's snap; nothing else.
- **Keyboard:** Tab reaches the tabs, the tiles, the sliders (arrow keys step by 10, Home and End to the ends), the Crop screen's controls and the frame; the crop frame's arrow keys (1%, Shift 5%) are **Later with the other keyboard shortcuts (L-01)**; the Edges panel is the keyboard and single-pointer route (review P2-04a); Escape is system back (the Crop screen discards).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Edit photo panel opens | 200 ms slide (ToolPanel) | 120 ms fade |
| Crop screen opens and closes | 200 ms slide up | 120 ms fade |
| A filter or an adjustment appears on the canvas | At once while dragging; at once on a tap | Same |
| Tile ring, tab switch | 120 ms fade | Same |
| Crop frame and grid | 1:1 with the finger; the grid fades in and out over 120 ms | Same |
| Rotate and flip on the Crop screen | 200 ms turn or mirror of the picture and frame | Instant |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. Reused: `panel_done`, `tool_position`, `position_rotate_left`, `position_rotate_right`, `explain_layer_locked`, `explain_layer_missing`, `explain_text_smallest`, `editor_undo_toast`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_crop` | Crop | 20 | Tool: cut the picture down to the part you want. Shared with the video editor's clip crop (P4-16) |
| `tool_edit_photo` | Edit photo | 20 | Tool: filters and color changes for a photo |
| `edit_photo_title` | Edit photo | 20 | Panel title |
| `edit_photo_tab_filters` | Filters | 14 | Tab: ready-made looks |
| `edit_photo_tab_adjust` | Adjust | 14 | Tab: sliders for brightness and so on |
| `edit_photo_tab_deep_fry` | Deep-fry | 14 | Tab: the "fried meme" look. Keep the word memes use in your language, or a short description |
| `edit_photo_reset` | Reset | 14 | Puts the open tab back to normal |
| `edit_photo_hidden` | This layer is hidden, so you won't see the change. | 60 | Line under the tabs |
| `filters_none` | None | 12 | First tile: no filter |
| `filters_intensity` | Intensity | 16 | Slider label: how strong the filter is |
| `filter_vivid` … `filter_bleach` | Vivid, Warm, Cool, Mono, Noir, Fade, Vintage, Film, Sepia, Cinema, Pastel, Punch, Drama, Sunset, Neon, Aqua, Matte, Chrome, Dusk, Bleach | 12 each | One key per filter id (`filter_<id>`). Short names of looks; **keep the word photo apps in your language use** (many keep "Noir", "Sepia", "Vintage") |
| `adjust_brightness` | Brightness | 16 | Slider labels |
| `adjust_contrast` | Contrast | 16 | |
| `adjust_saturation` | Saturation | 16 | How vivid the colors are |
| `adjust_warmth` | Warmth | 16 | Cooler (blue) to warmer (orange) |
| `adjust_sharpen` | Sharpen | 16 | |
| `adjust_vignette` | Vignette | 16 | Darker edges |
| `deep_fry_amount` | Amount | 16 | Slider label |
| `deep_fry_hint` | Crunchy, oversaturated and grainy: the meme look. | 60 | Under the slider |
| `crop_title` | Crop | 16 | Crop screen title |
| `crop_cancel_a11y` | Cancel crop | 20 | Spoken name of the close button; screen reader only |
| `crop_free` | Free | 12 | Aspect tile: any shape |
| `crop_original` | Original | 14 | Aspect tile: the picture's own shape |
| `crop_canvas` | Canvas | 14 | Aspect tile: the canvas's current shape (same word as `canvas_title`) |
| `crop_flip_h` | Flip horizontally | 24 | Spoken name of the button: mirrors left to right |
| `crop_flip_v` | Flip vertically | 24 | Mirrors top to bottom |
| `crop_straighten` | Straighten | 16 | Slider label: turns the picture a few degrees to level it |
| `crop_edges` | Edges | 14 | Button: move the crop's sides with buttons |
| `crop_edge_left` | Left | 14 | Row labels in the Edges panel: the side being trimmed |
| `crop_edge_right` | Right | 14 | |
| `crop_edge_top` | Top | 14 | |
| `crop_edge_bottom` | Bottom | 14 | |
| `crop_edge_more_a11y` | Trim more from %1$s | 30 | Spoken name of the plus; %1$s = the side |
| `crop_edge_less_a11y` | Trim less from %1$s | 30 | Spoken name of the minus |
| `crop_frame_a11y` | Crop frame | 16 | Screen reader only (the frame is drag-only; the Edges panel is the alternative) |
| `explain_crop_min` | The crop can't get smaller than this. | 50 | When Edges or the keyboard reaches the minimum |
| `layer_meta_edited` | Edited | 14 | Row meta: the layer has a crop, a filter or another edit |
| `edit_crop` | Crop | 24 | Edit names after "Undo: " |
| `edit_filter` | Filter | 24 | |
| `edit_adjust` | Adjust | 24 | |
| `edit_deep_fry` | Deep-fry | 24 | |

Translator notes:
- **Crop** is cutting a picture down; **Straighten** is turning it slightly to level the horizon; **Flip** is a mirror.
- **Filter names** are looks, not instructions; short words, shown under small tiles.
- **"Deep-fry"** is the meme term for a heavily oversaturated, grainy picture. If your language has no such word, a short description ("Crunchy") is fine.
- **Warmth** runs from cool (blue) to warm (orange), not temperature of the room.

## Accessibility
- **Tabs:** SegmentedTabs rules; each tab reads its name, "selected" and its place ("2 of 3").
- **Filter tiles:** a single-choice group "Filters": each tile reads its name, "selected" and its place ("Vivid, 2 of 21"); None reads "None". The thumbnail is decorative.
- **Sliders:** the Slider rules: keyboard and TalkBack steps of 10 landing on multiples of 10, the value announced politely after a change ("Brightness, 30"); Straighten steps of 1°; Intensity and Amount in steps of 10%.
- **The Crop screen:** the focus order is Close, Reset, Done, the aspect tiles, the tool buttons, Edges, Straighten. **The frame and its handles are hidden from accessibility** (drag-only); the aspect tiles, the Edges panel and Straighten are the full single-pointer alternative (WCAG 2.5.1 and 2.5.7). Each rotate and flip button is announced with its result ("Rotated left").
- **Touch targets:** every tile, tab, button, tool and handle is 48 dp or more; the filter tile is 72 dp.
- **Contrast:** tile name `text` on `surface` 16.9:1; the ring `selection` on `surface` 17.2:1; slider track and knob as the Slider README; the Edges readouts `text` on `surface-raised` 12.4:1; the crop frame, handles and grid are white with a near-black edge (4.3:1 or more over any picture); Done `on-primary` on `primary` 7.9:1.
- **200% font:** the tab labels and tile names wrap and grow the tile; the Adjust and Edges panels scroll; the Crop screen's bottom area scrolls; the aspect row scrolls sideways; the stage shrinks but never below 30% of the window (below that the bottom area scrolls instead).
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "photo"`, once per committed undo step (never while dragging; never when a tool only explains). The ids are the ones P4-07 and P4-16 will reuse.

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The Crop screen's Done, once for each part that changed | `tool: "crop"` (the frame changed), `"rotate_image"` (a 90° turn), `"flip_image"` (a mirror), `"straighten"` (the angle) |
| `tool_use` | An Adjust slider is released with a new value | `tool: "adjust_brightness"`, `"adjust_contrast"`, `"adjust_saturation"`, `"adjust_warmth"`, `"adjust_sharpen"`, `"adjust_vignette"` |
| `tool_use` | A filter tile tap, or an Intensity release | `tool: "filter"`, **`filter`: the filter id** (`none` when the filter is removed) |
| `tool_use` | A Deep-fry release | `tool: "deep_fry"` |
| `tool_use` | A tab's Reset | the ids of the parts that changed (`filter` with `filter: none`, the `adjust_*` ids, `deep_fry`) |

- **`filter` is a new optional parameter** (owner, 2 Nov by delegation; PRD → Metrics updated): sent only when `tool` is `filter`, in both editors. `value` stays for the canvas tools. Analytics stay off until consent (P5-01).

## QA compares
Use a debug scene `photo-image-tools` (a patterned 4:3 photo, a portrait photo, a gray ramp) and a gallery start.
1. **The bars:** an image layer selected at 360 × 800 (Position, Opacity, Delete, Edit photo whole, Crop cut in half); the main bar with Edit photo and Crop after Draw; a text layer's bar without them; a locked image layer (Unlock first).
2. **Filters:** the strip scrolled through all 21 tiles on the portrait photo; a filter on at 100% and at 50%; the Intensity row; the selected tile ringed. Hindi at 200% font (tile names wrapped).
3. **Adjust:** each slider at −100, 0 and +100 on the gray ramp and the portrait; the panel scrolled; Reset.
4. **Deep-fry** at 25%, 60% and 100%.
5. **The Crop screen** at 360 × 800 and 360 × 640: the aspect row, the tool row, Straighten; the frame with the grid while dragging; a locked 1:1; rotate left, flip, straighten −10° (no empty corners); the Edges panel; Hindi at 200% font.
6. **After a crop:** a 4:3 photo cropped to 1:1 fitted inside a 4:3 canvas (the canvas unchanged); the layer's row showing "Edited"; Undo; reopening the Crop screen showing the saved crop.
7. **A formats check (P2-02):** a main photo under Caption bar, cropped: it is placed again in the picture area.

**Measured: the Done when** (PM method in `docs/pm/acceptance-log.md`; the designer adds):
- **Reference renders** in `docs/qa/references/P2-04-image-tools/` (CLAUDE.md rule 5), at **1080 and 2160**: a contact sheet of the 20 filters at 100% (and at 50%) on three test photos; the six adjustments at −100, 0, +100; deep-fry at 25, 60, 100%; crop, rotate, flip and straighten on a grid photo. A reference per spec id.
- **Editor equals render:** on the scene `photo-image-tools`, screenshot the canvas (cropped to the frame, scaled to the render's width) and compare with the render hook at 1080 and 2160: **at least 99% of pixels differ by less than 3 of 255 per channel**; crop edges, rotation and flip within 1% of the width; the grain pattern the same; the 2160 render equals the 1080 render scaled.
- **Same ids and data for video:** the filter ids, the LUT files and the sampling rule in `:core:model` are what P4-07 will use (no editor-specific copy); checked by reading.
- **Non-destructive:** the media file's size and hash do not change after every tool; reopening a draft gives the same picture; Undo of each step restores exactly.
- **Old drafts:** a draft saved before this ticket opens unchanged.
- **Speed:** with a 12 MP layer in `photo-layers-10`, an Adjust slider drag and an Intensity drag keep janky frames under 5% (three 3-second drags, the owner's phone, release-type build); picture decode at the drawn size (P2-01 rule); opening the Filters tab shows the first five tiles within 300 ms.
- **API 29:** the whole tool works on an API 29 emulator (the GL path) and on a current phone (the AGSL path) with the same look.
- **Leaks:** LeakCanary, 5 cycles of open Edit photo, change a filter and an adjustment, open Crop, rotate, Done, close.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-knows carry the version bump):

| Need | Model |
| --- | --- |
| Edits on a layer | `ImageLayer.edit: ImageEdit? = null`, null meaning none: `quarterTurns: Int` (0 to 3), `flipH`, `flipV`, `straighten: Float` (degrees, −45 to 45), `crop: CropRect` (left, top, right, bottom as fractions of the rotated, flipped and straightened picture, default 0, 0, 1, 1), `adjust` (six floats), `filter: FilterRef(id, intensity)?`, `deepFry: Float` |
| Never destructive | The media file is never changed (media copies are never changed in place); `mediaCopyPaths()` is unchanged |
| Declarative specs | In `:core:model`: for each filter an id, a name resource and a LUT resource; for each adjustment an id, a range and a default; a registry; each spec has a reference render (the `media-effect` skill) |
| Version | Bump `CURRENT_SCHEMA_VERSION` (read the constant) with a version-only step (the new field defaults to null); check by opening a draft saved before |
| Constants | The pipeline order, the formulas, the LUT size (33), the straighten range, the crop minimum (10%), the grain cell size, the thumbnail size, in one commonMain place (`ImageToolCatalog`) with a comment pointing to this spec and `design/tokens.json` → `photo.imageTools`; fixed rules, part of every draft's look |
| LUT data | 20 LUT resources (33³, as 1089 × 33 PNG strips or an equivalent compact form), well inside the 50 MB budget (about 20 to 60 KB each), made by a script with a README (original work) |

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows (2 Nov) carry the rest.
1. **Non-destructive, declarative** as the Model says; one `drawPhotoScene` path draws an edited layer in the editor, the render hook and P2-08's export, from the same spec, so they can't disagree.
2. **One look in both engines:** the LUT data and the trilinear sampling are the shared artifact. Verify the current Media3 `SingleColorLut` and Android APIs at the start. **minSdk is 29:** AGSL (`RuntimeShader`) needs Android 13 and `RenderEffect` Android 12, so for 29 to 32 add a GL pass in `:engine:photo` behind a small interface in `:core:domain`; the `iosMain` implementation is a stub marked for the iOS port that **reports unsupported**, never a silent no-op. Test on an API 29 emulator.
3. **Draw at the drawn size:** filters and adjustments run on the bitmap decoded at the layer's drawn size (P2-01); a slider drag recomputes only the affected pass, not the decode; full resolution only at export (P2-08). Cache the 21 thumbnails per picture for the panel's visit.
4. **The Crop screen as a reusable component in `:core:ui`:** inputs a picture provider, a crop state and the aspect list; outputs a crop state; no knowledge of layers (P4-16 reuses it with a video frame).
5. **The tools in the bars:** Crop and Edit photo in the selected image layer's bar (after Delete) and in the main bar (after Draw, only while an image layer exists; selecting the lowest image layer on tap); a locked, missing or hidden layer as the States table says; the Layers row's "Edited" meta part (ordered after "File missing", before the opacity part: Locked · Hidden · File missing · **Edited** · 40% opacity). The row thumbnail shows the crop, rotation and flip but not the color edits.
6. **Re-run P2-02's as-placed rule** after a crop (the picture's shape changed).
7. **The LUT script:** written once (the backend engineer can own it), original work, the README in the repo; the generated files are committed.
8. **Analytics:** the `EditorTool` values `crop`, `rotate_image`, `flip_image`, `straighten`, `adjust_*` (six), `filter`, `deep_fry`, and the optional `filter` parameter (PRD → Metrics, updated by the PM).
9. **Size watch** (the PM's): past two build sessions, split **P2-04a** crop, rotate, flip, straighten (the Crop screen, and the layer's `edit` geometry) and **P2-04b** adjustments, 20 filters, deep-fry; one schema bump covers both if `ImageEdit` is defined whole in 04a.

## Proposals and questions
**For the PM**
1. **Confirm the ids** (yours, 2 Nov) and the `filter` parameter (delegated, applied).
2. **The tools' place** (Crop and Edit photo in the selected image layer's bar after Delete, and in the main bar after Draw): this replaces P2-01's "Adjust and Filters" and "Crop" main-bar slots, which named no target. P2-01's final-order line is updated.
3. **Stickers and Panels cells** get no image tools in v1 (D1). Say if you want stickers included.
4. **P2-10 flag:** "Make it a video meme" must carry the edits. The video overlay has no LUT in Phase 2, so P2-10 decides between baking the edited picture into the clip and warning.
5. **P4-07 and P4-16:** they reuse the ids and LUT data and the Crop screen.
6. **Reset per tab, not per row** (no hidden controls): say if QA finds it too coarse.

**For the owner** (delegated 2 Nov; the PM already applied 1A, 2A, 3A; my own calls here are applied the same way and logged for your review)
- **1. Does a crop change the canvas?** *Problem:* cropping a photo could resize the canvas to the new shape. *Considerations:* **A. No: the cropped picture is fitted inside the canvas, the canvas keeps its shape** (applied, recommended): the canvas never moves under the user's hands (P2-02's reasoning), every layer keeps its place, and a user who wants the new shape picks it in the ratio pill. **B. Yes: the canvas follows the crop:** the output matches the crop, but every layer's place and every caption would be re-laid, the P2-02 formats would shift, and it fights the photo-shape start rule. *Reverse:* a look change to how crops behave, not to saved drafts (the crop is stored as fractions). **Applied: A.**
- **2. Where do the image tools live?** *Problem:* they need a target layer, but the main bar shows with nothing selected. *Considerations:* **A. In the selected image layer's bar, plus main-bar entries that select the main photo** (applied, recommended): one tap in the common single-photo case and the target is always visible. **B. Only in the selected layer's bar:** simpler, but every filter costs a tap on the photo first. **C. A permanent "photo" tool acting on the whole canvas:** wrong with several layers. **Applied: A.**
- **3. Discard on back in the Crop screen?** *Problem:* a user could lose a carefully set crop with one back gesture. *Considerations:* **A. Back discards, Done applies** (applied, recommended: the mode screen's usual contract, nothing was applied yet); **B. Back applies:** risks applying a half-made crop. *Reverse:* a small behavior change. **Applied: A.** QA watches for lost work.

**Later, no ticket:** a hold-to-compare "see the original"; image tools for stickers and Panels cells; a curve or color-mix tool; per-row reset in Adjust; perspective correction; a rotate dial with free angles beyond ±45°.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A 3D LUT of size 33 sampled trilinearly is a standard, compact way to carry color looks; Media3 applies one as a GL effect | Guidance (API reference) | [Media3 `SingleColorLut`](https://developer.android.com/reference/androidx/media3/effect/SingleColorLut) |
| `RuntimeShader` (AGSL) needs Android 13 and `RenderEffect` Android 12, so minSdk 29 needs a GL fallback | Guidance (API reference) | [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader), [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) |
| Straightening by scaling the picture up so no corner is empty, with the frame fixed, is how most photo editors do it | Assumption-level (product knowledge of Photos apps; not confirmed from help pages) | none |
| Dragging a crop handle needs a single-pointer alternative; the Edges steppers and the aspect tiles are it | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| The formulas are conventional starting points; the reference renders and the owner's eye decide the final numbers | Assumption | none |
| The looks are generated from original recipes, so there is no pack license to record | Decision (owner, delegated 2 Nov) | `docs/pm/decision-log.md` |
