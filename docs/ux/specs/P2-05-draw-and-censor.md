# Draw and censor in the photo editor · P2-05

Sixth spec for the photo editor. It reuses, and does not repeat:
- `P2-01-photo-editor-shell.md`: the stage, layers and their gestures, the selected-layer bar, the Layers panel and rows, the Position panel, lock, hide, reorder, the ratio-change rule, `drawPhotoScene`, the render hook.
- `P2-03-photo-text-and-stickers.md`: the locked-layer refusals, the main bar, how a layer is announced.
- `P2-02-meme-formats.md`: the formats' panels and the as-placed rule (not touched here).
- `P1-10-text.md`: the caption palette (the ten colors), swatches, `SegmentedChoice`, `OptionTile`, `Slider` READMEs.

## Job
"Circle the thing", "scribble a mustache", "hide the license plate": **Draw** puts hand-drawn strokes on the picture, **Censor** hides a part of it. **Done when** (TICKETS): strokes and censor areas stay editable as layers. **Reading** (PM, 2 Nov; owner can veto): a Draw session makes **one drawing layer of vector strokes** (the eraser is a stroke in the same layer that clears); drawing again with that layer selected **adds to it**; a drawing or censor layer **moves, scales, turns, fades (a drawing; see Censor), locks, hides, reorders, duplicates, deletes and reopens from Drafts** like any layer. Single strokes are not selectable in v1: **Undo is how a stroke is taken back**.

**The one blue action:** none. Done in the Draw bar and the Censor panel are Ghost buttons. White is selection (the selected tile ring, the selected swatch ring, frames and handles).

**Fast path, with tap counts:**
- **Draw a circle in red:** Draw (1), drag on the picture, Done (2). Red and Medium are the defaults.
- **Change the color or size first:** Draw (1), a color (2), a size (3), drag, Done (4).
- **Censor a face:** Censor (1), drag the box over the face (the box lands in the middle already in Pixelate), Done (2).
- **Take a stroke back:** Undo (1).

## Decisions
- **Drawing is freehand only** (no lines, arrows, shapes, no pressure) and **modal**: in Draw mode a one-finger drag always draws and never moves a layer. This is the PRD's "brush sizes and colors, eraser".
- **A drawing is one layer** covering the canvas; its hit area and frame are the **box around its strokes**, not the canvas (a canvas-sized layer would take every touch from the layers below it).
- **A censor is a backdrop effect layer** with a **fixed minimum strength** so that what it hides is never legible, and it **has no opacity control** (a see-through censor shows what it should hide).

## Entry and exit

### Draw
- **The Draw tool** (icon `draw`) is in the main bar (final order below). **Tap:** enters **Draw mode**: the tool bar is replaced by the **Draw bar**, nothing else on the screen changes; the canvas stays where it is (it refits above the bar as for any panel, P2-01). Taps are ignored while the mode opens.
- **Which layer the strokes go to:** if a **drawing layer is selected** (the bar's Draw tool, below), strokes add to it. Otherwise **the first stroke creates a new drawing layer** at the front of the stack and selects it; nothing is created until a stroke is made, so entering and leaving Draw mode with no stroke leaves no layer and no step. All strokes of one session go to that one layer.
- **Leaving:** **Done**, system back or Escape. A stroke in progress is cancelled first (the P2-01 back order: gesture, then mode). Done leaves the drawing layer **selected**, with its bar. The top bar's **Close** still closes the editor (every committed stroke is saved).
- **While in Draw mode:** a one-finger drag draws; **more fingers are ignored** (a second finger never moves, scales or ends a stroke; a palm touch doesn't spoil it); taps do not select layers; no selection frame or handle shows; the ratio pill is **inert** (drawn disabled, spoken "disabled": the canvas must not change under the brush, as while typing); Undo and Redo work and are one stroke per step; the Layers tool is not reachable (the bar is the Draw bar).
- **The selected-drawing bar** (a drawing layer is selected, not in Draw mode): **Position · Opacity · Delete · Draw · Duplicate · Lock · Hide · Layers** (P2-01's bar with **Draw** after Delete, so Delete stays in view at 360 dp; Unlock first when locked). Draw there **adds to this layer** and opens Draw mode.
- **Locked layer:** Draw refuses with P2-03's toast ("This layer is locked. Unlock it to change it."). Draw mode with nothing selected is unaffected by a locked layer elsewhere.

### Censor
- **The Censor tool** (icon `censor`) is in the main bar. **Tap:** adds a **Censor layer** at the center of the canvas, in front of the stack, selected, **and opens the Censor panel** at once (one undo step "Add censor"). The box is **0.40 S wide and 0.25 S tall** (S, the canvas's shorter side), a rectangle, mode **Pixelate**, strength 50%. The user drags it onto the target (canvas touches stay live under the panel).
- **The selected-censor bar:** **Censor · Position · Delete · Duplicate · Lock · Hide · Layers** (no Opacity: see Rules; Unlock first when locked). **Censor** opens the panel again.
- **Locked layer:** the tools explain (P2-03's toast).
- **Kept:** every layer is in the project (autosave, undo). Not kept: Draw mode, the open panel, the chosen brush color and size (they last for the editor visit: a new Draw session starts from the last color and size used in this visit, red and Medium otherwise).

### The main bar, final order
The photo editor's bar (replacing the lines in P2-01 and P2-04): **Formats · [format tool] · Text · Stickers (P2-14) · Add photo · Draw · Edit photo · Crop · Censor · Remove BG (P2-07) · Layers**. The most used tools come first; **Layers moves to the end** (its panel is also the last tool of every selected-layer bar). At 360 dp the first five show whole and the sixth is cut (the scroll cue). Before a tool exists the others sit one place to the left.

## Draw: the Draw bar and the brush
The **Draw bar** replaces the tool bar while in Draw mode (`surface`, `hairline` top border, `space-4` side padding, about 176 dp tall; the canvas refits above it):
1. **Row 1:** a **SegmentedChoice** (`design/system/components/SegmentedChoice/README.md`) **Brush · Eraser** at the start and **Done** (Ghost) at the end.
2. **Row 2 (Brush only):** the **color Swatches**, the ten caption-palette colors in palette order (white, black, yellow, orange, red, pink, purple, blue, light blue, green), scrolling edge to edge, selected = the white ring. **Scroll cue (review P2-05a): the swatches sit at a pitch that leaves the next one partly cut at the edge (about 54 dp apart at 360 dp), never exactly six whole; entering Draw mode scrolls the selected color into view.** Default **red** (the usual "circle it" color). Hidden for the Eraser (colors don't matter).
3. **Row 3:** five **size tiles** (OptionTiles, stacked, a filled dot above its label; **the dots are drawn at 4, 7, 10, 15 and 22 dp, a perceptual scale that keeps Thin and Fine apart (review P2-05a), not at the stroke's true ratio**): **Thin · Fine · Medium · Thick · Heavy**. Default **Medium**. The Eraser uses the same five sizes (its size is remembered separately from the brush's for the visit).

- **Sizes** (fixed rules, shares of S, so a stroke scales with the canvas and the export): **0.004, 0.008, 0.016, 0.032, 0.064 S** (4 to 69 px at 1080).
- **The brush:** round caps and joins, one opaque palette color at 100%, no pressure, no tilt. The stroke follows the finger with **no visible lag** (it draws in the draw phase while the finger is down) and is **smoothed** as it is drawn (midpoint curve through the touch points) so a hand tremor doesn't show as corners.
- **The eraser** is a stroke of mode **erase** in the same layer: it clears what the layer drew **before** it, with the same round brush and sizes. It never touches the picture below (the layer is one group; the clear happens inside it). Choosing the Eraser with nothing drawn yet: the first eraser stroke does nothing and a toast says "Draw something first, then you can erase it." (nothing is created, nothing recorded).
- **One stroke = one undo step**, committed on release: "Undo: Draw" or "Undo: Erase". Undoing the stroke that created the layer removes the layer; Draw mode stays open and the next stroke makes a new one. A stroke too short to be a mark (under 0.004 S of travel) draws a **dot** of the brush size (a tap makes a dot).
- **A full drawing:** a drawing layer holds at most **5,000 points** (quantized and simplified, below). When a stroke would pass the cap it stops where the cap is reached and a toast says "This drawing is full. Close drawing mode and start a new one." A new one is started by deselecting and tapping Draw again.
- **Stored small:** when a stroke ends its points are **quantized to 0.001 S and simplified** (tolerance 0.002 S), so a scribble stays a few KB (the build reports the saved JSON size of a 3-minute scribble: the PM's must-know 2).
- **Panel height:** about 176 dp, the least that fits three rows at 48 dp targets, so the canvas keeps as much room as possible (at 360 × 640 the canvas area is about 360 dp tall).

## Layer behavior

### The drawing layer
- **Its place:** points are stored in units of S relative to the layer's own center (the canvas center when drawn), so a ratio change never stretches a drawing. The layer's transform (center, scale, rotation) is P2-01's: a drag moves all strokes together, the handle scales and turns them. **Scale range 10% to 500%**, as stickers; 100% is "as drawn".
- **Hit area and frame:** the **bounding box of the strokes** (plus half the widest stroke), turned with the layer, **grown to at least 48 dp** in each direction. A touch outside that box goes through to the layers below. The frame, handle and guides are P1-10's two-tone.
- **Opacity** (P2-01, 10% to 100%): the whole drawing is drawn as one group at that opacity; the eraser's clears are inside the group, so a faded drawing never shows overlap darkening.
- **Row:** name **"Drawing"**, thumbnail a **small render of its strokes fitted inside the square** on a `text-muted` tile (so black and white strokes both read: 6.9:1 and 3:1); meta as P2-01.
- **Ratio change:** the same rule as stickers: the strokes' box fully inside stays inside (to the 2% margin, shrinking first, not below 10%); a drawing hung off the edge keeps its center and scale; one undo step and the usual toast.
- **Duplicate** copies the strokes, offset 3% of S as P2-01. **Delete, lock, hide, reorder:** P2-01's.

### The censor layer
- **What it is:** an area (a **Rectangle** or an **Ellipse**) with a **mode**: **Blur**, **Pixelate** or **Black bar**. It affects **what is drawn under it inside its area**, in the editor, the render hook and the export alike; layers **above** it are not touched, so a caption over a censor stays sharp, and moving a censor above or below a layer in the Layers panel changes what it hides. A hidden layer below is not drawn, so it is not censored; a locked one is.
- **Fixed minimums, so censored content is never legible** (rules saved with every draft, `design/tokens.json` → `photo.censor`). *a is the area's shorter side; S the canvas's shorter side; strength is the slider from 0 to 100%.*

  | Mode | Rule |
  | --- | --- |
  | **Pixelate** | Square blocks of side **a × (1/8 at strength 0 to 1/3 at strength 100%)**, **never smaller than 0.012 S**: at most 8 blocks across the area's shorter side, at least 5 for a thin strip. Each block is the average of the backdrop it covers; the grid is aligned to the canvas (anchored at its top-left, in units of S), not turned with the area |
  | **Blur** | A Gaussian blur with sigma **a × (0.12 at strength 0 to 0.35 at 100%)**, **never smaller than 0.012 S**, computed on a downscaled copy of the backdrop (so it loses detail, it is not a re-sharpenable effect) |
  | **Black bar** | An opaque black (`#000000`) area; no strength |

  The slider's lowest value is the minimum above, never "off". **Pass:** a line of text 0.03 S tall under a Pixelate or Blur at strength 0 cannot be read in the 1080 render at 100% zoom by two people (QA, below).
- **No opacity:** a censor is always 100% (the Opacity tool and the Layers panel's Opacity row do not appear for it; P2-01's range does not apply). The model keeps the field at 1.
- **Size and shape:** its own width and height (not just a scale), **0.06 S to 3 S** each, and the layer's transform (center, scale, rotation). Hard edge, no feather.
- **Row:** name **"Censor"**, the `censor` icon on `surface` (like a text row); meta as P2-01.
- **Ratio change:** a box that was fully inside stays fully inside (2% margin, shrinking first, not below its minimum); one that was hung off an edge keeps its place.
- **Handles and gestures:** a selected censor shows P1-10's two-tone frame, the corner handle (uniform scale and turn, as every layer) and **four edge handles**: a white bar (`selection`, `on-primary` edge, 20 dp by 6 dp) in the middle of each edge, dragging it resizes that side along the layer's own axis (the opposite side stays); each handle has a **48 dp target**, and where handles would overlap on a small box the nearest one wins. A one-finger drag inside moves it; two fingers scale, turn and move (P2-01); snapping as P2-01. Width and Height stop at their limits with no toast while dragging.
- **The Censor panel** (ToolPanel, title "Censor", Done): rows **Mode** (OptionTiles **Blur · Pixelate · Black bar**), **Shape** (OptionTiles **Rectangle · Ellipse**), and **Strength** (a Slider, "Strength", 0 to 100%, default 50%, live while dragging, one step on release; hidden for Black bar). Every tap is applied at once and is one undo step. About 280 dp: it scrolls inside the photo editor's 40% cap at 640.
- **The Position panel for a censor** is P2-01's (Size, Rotation, Move, Center) **plus two rows, Width and Height**: each a minus and a plus (2% of S per tap, hold repeats), readouts in percent of S. This is the single-pointer route for the edge handles (WCAG 2.5.7).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Draw mode, nothing drawn | The Draw bar: Brush selected, red, Medium; the canvas unchanged | `tool_draw`, `draw_brush`, `draw_eraser` |
| Drawing | The stroke follows the finger; one layer created at the first stroke and selected (no frame while in mode) | `edit_draw` |
| Eraser, nothing drawn yet | The first stroke does nothing; the toast | `explain_draw_nothing` |
| Drawing full | The stroke stops at the cap; the toast | `explain_draw_full` |
| Draw mode left (Done) | The drawing layer selected with its bar (Draw, Position, …) | `a11y_draw_done` |
| Draw tapped on a locked drawing | The toast; no mode | `explain_layer_locked` |
| Censor just added | The box in the middle (Pixelate, 50%), selected, the Censor panel open | `a11y_layer_censor_added` |
| A censor selected | Frame, corner handle and four edge handles; the bar | `tool_censor`, `tool_position`, … |
| Censor, Black bar | No Strength row | `censor_bar` |
| Censor at a size limit | The edge handle stops; the Position panel's button explains | `explain_censor_min`, `explain_censor_max` |
| Locked drawing or censor | A badge instead of handles; Unlock first; Draw, Censor, Position, Delete, Width and Height explain | `explain_layer_locked` |
| Drawing and censor rows | "Drawing" with its mini render; "Censor" with its icon | `layer_name_drawing`, `layer_name_censor` |
| A draft saved before this ticket | No drawing or censor layers; opens unchanged | none |
| Long content (5 languages, 200% font) | Size labels wrap and the tiles grow; the Draw bar scrolls vertically if taller than 40% of the window; the Censor panel scrolls | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run | none |

## Interactions
- **Back order** (P2-01 with P2-03): typing commits → menu → panel or sheet → **a stroke in progress is cancelled → Draw mode ends** → a gesture in progress → a lifted row dropped → selection clears → editor closes.
- **Undo and redo:** P2-01's rules; "Undo: Draw", "Undo: Erase", "Undo: Add censor". Undo and Redo are live in Draw mode; ignored while a finger is down.
- **Haptics:** SegmentTick for each snap (censor and drawing gestures, P2-01) and the Strength slider is not snapped; nothing for strokes.
- **Keyboard:** Tab reaches the Draw bar's controls and the Censor panel; a drawing needs a pointer (Later: arrow-key lines); the censor's Position, Width and Height panel is the keyboard route.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| The Draw bar replaces the tool bar | 120 ms crossfade, the canvas refit 200 ms | Instant |
| The Censor layer lands | 120 ms fade of its frame | Same |
| A stroke | 1:1 with the finger | Same |
| Panels | 200 ms slide | 120 ms fade |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. Reused: `panel_done`, `tool_position`, `tool_delete`, `tool_duplicate`, `tool_lock`, `tool_unlock`, `tool_hide_layer`, `tool_layers`, `color_*`, `explain_layer_locked`, `position_*`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_draw` | Draw | 20 | Tool: draw on the picture with your finger |
| `tool_censor` | Censor | 20 | Tool: hide part of the picture |
| `draw_brush` | Brush | 14 | Segment: draws |
| `draw_eraser` | Eraser | 14 | Segment: rubs out your drawing (not the picture) |
| `draw_size_thin` | Thin | 8 | Size tiles under a dot. Use five short words from thin to thick |
| `draw_size_fine` | Fine | 8 | |
| `draw_size_medium` | Medium | 8 | |
| `draw_size_thick` | Thick | 8 | |
| `draw_size_heavy` | Heavy | 8 | |
| `a11y_draw_mode` | Drawing. Draw with one finger. | 40 | Announced when Draw mode opens |
| `a11y_draw_done` | Drawing saved | 30 | Announced after Done |
| `explain_draw_nothing` | Draw something first, then you can erase it. | 50 | Toast |
| `explain_draw_full` | This drawing is full. Close drawing mode and start a new one. | 70 | Toast |
| `layer_name_drawing` | Drawing | 16 | Row name |
| `layer_name_censor` | Censor | 16 | Row name |
| `censor_title` | Censor | 16 | Panel title |
| `censor_mode` | Mode | 16 | Row heading |
| `censor_blur` | Blur | 12 | Tile: smears the area |
| `censor_pixelate` | Pixelate | 12 | Tile: turns the area into big squares |
| `censor_bar` | Black bar | 12 | Tile: covers the area with solid black |
| `censor_shape` | Shape | 16 | Row heading |
| `censor_rect` | Rectangle | 12 | Tile |
| `censor_ellipse` | Ellipse | 12 | Tile: an oval, good for faces |
| `censor_strength` | Strength | 16 | Slider label: how much the area is hidden (it never goes below a safe minimum) |
| `position_width` | Width | 16 | Row label in a censor's Position panel |
| `position_height` | Height | 16 | |
| `position_width_less` | Make narrower | 24 | Spoken label of the minus |
| `position_width_more` | Make wider | 24 | Spoken label of the plus |
| `position_height_less` | Make shorter | 24 | |
| `position_height_more` | Make taller | 24 | |
| `explain_censor_min` | A censor can't be smaller than this. | 50 | |
| `explain_censor_max` | A censor can't be bigger than this. | 50 | |
| `a11y_layer_censor_added` | Censor added | 20 | Announced politely |
| `edit_draw` | Draw | 24 | Edit names after "Undo: " |
| `edit_erase` | Erase | 24 | |
| `edit_censor_add` | Add censor | 24 | |
| `edit_censor_mode` | Censor mode | 24 | |
| `edit_censor_shape` | Censor shape | 24 | |
| `edit_censor_strength` | Censor strength | 24 | |

Translator notes:
- **Draw** is to sketch freehand; **Eraser** rubs out only what you drew, never the photo.
- **Censor** here means hiding part of a picture (a face, a number plate); **Pixelate** makes big squares; **Black bar** is a solid black rectangle.
- **Strength** is how much is hidden; the lowest setting still hides the content.
- **Size words** go from the thinnest to the thickest brush.

## Accessibility
- **TalkBack:** a drawing layer reads "Drawing, layer 2 of 4" and a censor "Censor, layer 1 of 4" (`a11y_layer_row`), with the usual actions (Position, Opacity for a drawing, Lock or Unlock, Hide or Show, Duplicate, Delete, Move up and down; **Draw** adds to a drawing). The row's actions are the same.
- **Freehand drawing has no accessible alternative in v1:** drawing a stroke is an **essential** drag (WCAG 2.2 SC 2.5.7's exception), so a screen-reader or switch user cannot make strokes. A censor, which is what accessibility users most need (hiding private details), has the full single-pointer route: the Censor panel, the Position panel with Width and Height, and the layer's TalkBack actions. (Later: straight lines and shapes by tap.)
- **Draw mode and focus:** opening Draw mode announces `a11y_draw_mode`; the Draw bar's controls are focusable in the order Brush, Eraser, Done, colors, sizes; the canvas is exposed as one node "Meme canvas, drawing". Closing returns focus to the layer's bar.
- **The handles** (corner, four edges) are hidden from accessibility; the panels are the alternative.
- **Touch targets:** every tile, segment, swatch and button is 48 dp or more; the edge handles' targets are 48 dp.
- **Contrast:** the Draw bar and panels use `text` on `surface` (16.9:1) and the selected ring `selection` on `surface` (17.2:1); the drawing row's thumbnail tile `text-muted` gives black strokes 6.9:1 and white 3:1; the frame and handles are white with a near-black edge (4.3:1 or more over any picture). Stroke and censor colors are the user's content.
- **200% font:** size and mode tiles widen (their rows scroll); the Draw bar grows and scrolls if taller than 40% of the window; nothing is cut.
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "photo"`, once per committed undo step (never while a stroke is drawn; never when a tool only explains; never for opening Draw mode, a panel or a tile). The ids are the PM's (2 Nov).

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A brush stroke is committed (release) | `tool: "draw_stroke"` |
| `tool_use` | An eraser stroke is committed | `tool: "erase_stroke"` |
| `tool_use` | A censor layer is added (its starting mode), or its mode is changed | `tool: "censor_pixelate"`, `"censor_blur"` or `"censor_bar"` (the mode it now has) |
| `tool_use` | Anything else on these layers: a gesture release, a Position step, lock, hide, opacity (drawings), reorder, delete, duplicate | the P2-01 ids, unchanged (`transform_layer`, `position_layer`, …) |

- **No event** for a censor's shape, strength or size, for brush color or size, and for opening Draw mode (they are not in the PM's ids; say if you want them). Analytics stay off until consent (P5-01).

## QA compares
Use the debug scenes `photo-draw-censor` (a photo with text over it, a face-sized area, a text card for the legibility test, a drawing of five stroke sizes, an erased stroke, a censor in each mode and shape) and a gallery start.
1. **The main bar** at 360 × 800: Formats, Text, Add photo, Draw, Edit photo whole and Crop cut (the first five, the scroll cue); the order to the end.
2. **The Draw bar** at 360 × 800 and 360 × 640: Brush and Eraser, the ten colors (red ringed), the five sizes (Medium ringed); the canvas area left above it; the Eraser state without the colors.
3. **Strokes:** five sizes in five colors; an erased stroke; a dot; then Done: the drawing layer selected, its bar (Draw fourth), its frame around the strokes only; then moved, scaled to 300%, turned, faded to 40%, hidden, reordered under a caption, reopened from Drafts.
4. **The drawing row** in the Layers panel: "Drawing" with its mini render on the gray tile, next to a text row and a photo row.
5. **Censor** over a face-sized area in each mode and both shapes, at strength 0, 50% and 100%; the Censor panel (Black bar has no Strength row); the frame with the corner and four edge handles; resized by an edge; rotated 30°.
6. **Order:** a censor under a caption (the caption is sharp), over it (the caption is hidden), and above a hidden layer.
7. **Hindi at 200% font,** 360 × 640: the Draw bar (size words wrapped), the Censor panel, a Drawing row.
8. **A locked drawing and a locked censor:** the badge, Unlock first, the toasts.

**Measured: the Done when** (PM, 2 Nov; the designer adds):
- **Editable layers:** three strokes, then move, scale, turn, fade, hide, reorder, duplicate, delete, undo and reopen from Drafts: the drawing is the same each time; **drawing again with the layer selected adds to it** (one layer, not two); a censor does the same list (no fade).
- **Legibility test:** a test card with a line of ten digits 0.03 S tall (and a face-sized photo): at strength 0, a Pixelate and a Blur in the 1080 render, viewed at 100% zoom, **cannot be read by two people** (none of the digits identified); Black bar covers fully; the same at 2160.
- **Editor equals render:** on `photo-draw-censor`, the canvas screenshot (cropped to the frame, scaled to the render width) against the render hook at 1080 and 2160: strokes and censor areas within 1% of the width in place and 2% in size, the pixel grid identical (it is in units of S), the eraser's clears the same, and **the 2160 render equals the 1080 render scaled**. References in `docs/qa/references/P2-05-draw-censor/` (CLAUDE.md rule 5): strokes at the five sizes, an erased stroke, each censor mode and shape over the text card, at 1080 and 2160.
- **Draft size:** report the saved JSON size of a 3-minute scribble (expect under 60 KB at the 5,000-point cap) and that autosave still commits within 100 ms.
- **Speed:** a stroke follows the finger with no visible lag (60 fps recording); in the 10-layer `photo-layers-10` scene with one drawing and one censor added, the P2-01 drag bars hold (three 3-second drags under 5% janky frames, owner's phone, release-type build); a censor over a 12 MP photo drags smoothly.
- **API 29:** blur and pixelate work on an API 29 emulator and a current phone with the same look (the PM's must-know 5).
- **Leaks:** LeakCanary, 5 cycles of enter Draw mode, draw, erase, Done, add a censor, change its mode, close.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-knows carry the version bump):

| Need | Model |
| --- | --- |
| Drawing | A new layer kind `DrawingLayer(id, strokes: List<Stroke>, transform, locked, hidden, opacity)`, `@SerialName("drawing_layer")`. `Stroke(mode: draw or erase, color: ArgbColor?, widthS: Float, points)`, the points a flat list of (x, y) in units of S relative to the layer's center, quantized to 0.001 S and simplified (tolerance 0.002 S), at most 5,000 points per layer |
| Censor | A new layer kind `CensorLayer(id, mode: blur, pixelate or bar, shape: rect or ellipse, strength: Float, widthS, heightS, transform, locked, hidden, opacity)`, `@SerialName("censor_layer")`; `opacity` is always 1 (the edits clamp it); width and height 0.06 S to 3 S |
| Version | Bump `CURRENT_SCHEMA_VERSION` (read the constant) with a version-only step (new layer kinds leave old JSON untouched); check by opening a draft saved before |
| Media | None: no media file; `mediaCopyPaths()` is unchanged |
| Constants | Brush sizes, the stroke quantum and tolerance, the point cap, the censor defaults, minimums and formulas, in one commonMain place (`PhotoCatalog` or a `DrawCatalog`) with a comment pointing to this spec and `design/tokens.json` → `photo.draw` and `photo.censor`; fixed rules, part of every draft's look |

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows (2 Nov) carry the rest.
1. **Two layer kinds** with stable `@SerialName`s as above; `drawPhotoScene` is the only place either draws (the editor, the render hook and P2-08's export).
2. **Strokes** draw in the draw phase while a finger is down and commit once on release (one undo step); smoothing is a midpoint quadratic curve, **round caps and joins**; quantize and simplify at commit; report the saved JSON size of a 3-minute scribble.
3. **The eraser clears inside the drawing's own group** (a clear blend into the layer's offscreen), never the picture below; the layer is composited at its opacity after. Stroke order is the saved order.
4. **Hit test and frame for a drawing** use the strokes' bounding box (grown to 48 dp), not the canvas; the ratio-change rule uses the same box. A censor's frame is its width and height box; its four edge handles resize in the layer's own axes.
5. **Censor is a backdrop effect:** it blurs or pixelates what is drawn beneath it inside its shape, in the editor, the render hook and the export, with the **fixed minimums** above. **minSdk is 29:** Compose's blur effect and AGSL shaders need Android 12 or 13, so for 29 to 32 use a downscaled software blur or a GL pass in `:engine:photo` behind a small interface in `:core:domain` (an `iosMain` stub marked for the iOS port that reports unsupported, never a silent no-op); verify the current APIs at the start and test on an API 29 emulator. Blur runs on a downscaled copy so it loses detail. The pixelate grid is aligned to the canvas, in units of S.
6. **Draw mode:** the Draw bar replaces the tool bar; a one-finger pointer draws; **extra pointers are ignored**; Undo and Redo stay live; the ratio pill is inert (as typing); back cancels the stroke, then leaves the mode.
7. **No Opacity for a censor:** the tool, the Layers panel's Opacity row and the TalkBack action do not appear, and the edit clamps to 1.
8. **Rows and thumbnails:** "Drawing" with a mini render of the strokes fitted inside the 40 dp tile on `text-muted`; "Censor" with the `censor` icon on `surface`.
9. **Components and icons:** the Draw bar (SegmentedChoice, Swatches, size OptionTiles drawn from data: a filled dot at the relative size; Censor's mode and shape tiles drawn from data), the corner and edge handles; **new icons** `draw` and `censor` (DESIGN_SYSTEM).
10. **Analytics:** the `EditorTool` values `draw_stroke`, `erase_stroke`, `censor_blur`, `censor_pixelate`, `censor_bar`.
11. **Hand checks:** the PM's list (three strokes then every layer action, censor at the three modes, resized, moved, reordered under and over text, the 1080 and 2160 renders equal the screen, a pixelated area not legible, LeakCanary, the drag bars) plus the legibility test above.
12. **Size watch** (the PM's): past two build sessions, split **drawing** (Draw mode, the drawing layer) from **censor** (the censor layer and its backdrop effect, which is the risky half on API 29 to 32). One schema bump covers both if both kinds are in the model.

## Proposals and questions
**For the PM**
1. **Confirm the ids** (yours) and the reading that a censor's mode change fires its own id and shape, strength, color and size fire nothing; say if you want `censor_strength` and the like.
2. **The main bar's final order** now ends with Layers (Formats · [format tool] · Text · Stickers · Add photo · Draw · Edit photo · Crop · Censor · Remove BG · Layers). This **replaces P2-01's order** (Layers fifth) and P2-04's "before Layers"; the lines in P2-01 and P2-04 are updated. Say if Layers should stay earlier (it is the one tool that reorders and shows hidden layers).
3. **The ellipse shape for a censor** is one small addition beyond the PRD's "blur, pixelate, black bar" (faces are round): a mask, no new mode. Say if you want it out.
4. **No Opacity for a censor** and **no freehand alternative for screen readers** (essential exception) are deliberate; both are listed above.
5. **P2-10 flag** (yours): "Make it a video meme" must carry drawing and censor layers; a censor needs a backdrop effect in the video overlay, so P2-10 decides between baking the censored picture into the clip and warning.

**For the owner** (delegated 2 Nov: the recommendation is applied; the PM logs it for your review)
- **1. Can a censor be made weaker than a safe minimum?** *Problem:* a Strength slider that can reach "almost nothing" lets a user think a face or number plate is hidden when it is not. *Considerations:* **A. The slider's lowest value is a fixed safe minimum** (applied, recommended): what is hidden is never legible, whatever the setting, and the rule is the same in preview and export; the cost is that a user who wants a very light blur as a design effect uses the Blur filter ideas (P2-04 filters) instead. **B. Let it go to zero:** more freedom, but a "censor" that doesn't censor. *Reverse:* a look change to saved censors (the minimums are part of every draft's look), so decide before the closed test (about 25 Jan 2027). **Applied: A.**
- **2. Is drawing modal, with a Done?** *Problem:* a one-finger drag either moves layers or draws. *Considerations:* **A. Modal Draw mode** (applied, recommended): while drawing, a drag always draws and never moves a layer, a second finger never spoils a stroke, and Done returns to editing; the cost is one tap to enter and one to leave. **B. Always-on drawing with a pen button:** fewer taps but constant mis-draws and mis-moves. **Applied: A.**
- **3. Freehand only, or also lines and shapes?** *Problem:* drawing arrows and circles by hand is imprecise. *Considerations:* **A. Freehand only** (applied, recommended: the PRD says "brush sizes and colors, eraser", the smallest build, and the eraser and undo cover mistakes); **B. Add straight lines, arrows and shapes:** very useful for memes ("circle the thing"), roughly a day or two more and new tools to explain. *Reverse:* adding them later is a new ticket, no saved-draft change (strokes stay strokes). **Applied: A.**

**Later, no ticket:** straight lines, arrows and shapes; a color picker beyond the ten; a pressure-sensitive brush; selecting and moving a single stroke; pinch-zoom on the stage while drawing; hold-to-draw lines for screen-reader users.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Freehand drawing is an essential drag, so WCAG's dragging criterion has an exception for it; placing and sizing a layer is not essential and needs a button route | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| A black bar, a pixelate and a blur are the standard ways to hide a face or number plate; a weak blur can be partly reversed, so a minimum is kept | Assumption-level (product knowledge of redaction and photo apps; not confirmed from vendor pages) | none |
| `RenderEffect` (blur) needs Android 12 and `RuntimeShader` (AGSL) Android 13, so minSdk 29 needs a downscaled software or GL fallback | Guidance (API reference) | [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect), [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader) |
| A mid-gray thumbnail tile (`text-muted`) shows black strokes at 6.9:1 and white at 3:1 | Computed from the tokens | `design/tokens.json` |
| The bounding-box hit area keeps a canvas-sized drawing from taking every touch from the layers below | Reasoned from P2-01's hit-testing rule | `docs/ux/specs/P2-01-photo-editor-shell.md` → Hit testing |
