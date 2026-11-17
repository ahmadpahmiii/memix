# Shape masks (rectangle, circle, linear, mirror), with the Blend panel and mode list for P4-03a · P4-03b

Fifth Phase 4 spec. P4-03a builds the 10 blend modes (engine) and P4-03b the masks; the PM's kickoff said 03a needs no spec beyond a list. The 03a **picker still needs a place to live**, so its tool and one small panel are here (Blend, below), built with 03a. It reuses, and does not repeat:
- `P4-01b-overlay-layers.md`: overlays, the bars, stage gestures, the "panel open, gestures stay live" and "playhead jumps to the item's start" rules, the selected-item frame and handle.
- `P4-16-clip-crop-rotate-flip-fit.md`: the clip pipeline and the main clip's tools.
- `P4-04-green-screen.md`: the **pipeline order** that fixes where mask and blend sit (key, color edits, Fit or Fill, **mask**, framing, opacity, **blend**).
- `P2-05-draw-and-censor.md`: the corner handle plus four edge handles, and the Width and Height steppers as the single-pointer route.
- `P2-04-image-tools.md` and `P3-09-audio-effects.md`: the panel with tabs and a header button.
- Component READMEs: ToolPanel, OptionTile, SegmentedTabs, Slider, Toggle, Toast.

## Job
"Show only part of this clip": a circle around a face, a rectangle to crop a window out of a clip, a half-and-half split, a mirrored strip. And "blend": a smoke or glitch overlay that lights up the clip beneath instead of covering it. PRD → Video editor → Layers: blend modes, shape masks (rectangle, circle, linear, mirror). **One main action: none.** Export stays the one blue action; the panels' Done is Ghost (ToolPanel).

**Done when** (TICKETS): each mask matches its reference render (P4-03b); each blend mode matches its reference render (P4-03a). Reference tables in Measured.

**Fast path, with tap counts:**
- **A circle around a face:** the clip (1), Mask (2), Circle (3), drag on the preview to place it (4).
- **A soft edge:** Edge softness (1 drag).
- **Show the outside instead:** Invert mask (1).
- **Lighten the video with a smoke overlay:** the overlay (1), Blend (2), Screen (3).
- **No dragging at all:** the Position tab (Width, Height, Rotation, Move, Center) and the tiles do everything.

**Principles check.** Content first: the mask frame shows only while the panel is open; nothing is permanent chrome. Fast path: shape in one tap, with a sensible size and place already. One-handed: tiles, slider and toggle in the lower half. Forgiving: every change one undo step; None removes a mask. Accessible: the Position tab is the no-drag route; blend tiles are plain buttons. Five languages: names measured at 200% (Copy).

**Color roles:** no blue except the sliders' `primary` fill. White is selection: the selected tile, the mask frame on the stage (two-tone, white with a near-black edge). No new color.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Mask panel (ToolPanel) | The **Mask** tool of a selected main video or photo clip, or an overlay | Done, system back, Escape. The selection stays; the mask frame goes |
| Blend panel (ToolPanel) | The **Blend** tool of a selected overlay (video or photo) | Done, system back, Escape |

- **Arrives from:** the clip bars, in the consolidated order set in P4-04 and P4-02b (**Mask** after Blend on overlays, after Green screen on main clips; **Blend** after Crop on overlays). **Not for** captions, stickers or sounds; **Blend is not on main clips** (nothing beneath the main video to blend with: the background is a flat color, Blur or a picture, and a mode on it would be a color tweak the Filters tool already does).
- **Playhead:** playback keeps running if it was running (the point is to see the result). If the playhead is outside the item's span it jumps to the item's start when the panel opens (P4-01b rule).
- **Kept:** the mask and blend mode on the clip, in the project (autosave, undo). Not kept: the open tab, the panels.
- **While the Mask panel is open,** stage gestures edit **the mask** (below), not the clip; the clip's own selection frame and handle are hidden. While the Blend panel is open the stage gestures stay live on the overlay (P4-01b).
- **A clip whose file is missing:** the tools are absent (P4-16 order).

## Layout
Board: none. The panels follow the Opacity, Position and Edit photo panels; the tool bars are P4-04's table plus these two tools.

### The tools
Icons `mask` and `blend` (new, below); labels "Mask" (`tool_mask`) and "Blend" (`tool_blend`), the normal tool look. Name = visible label; click labels "Show only part of this clip" and "Mix this overlay with what's beneath it" (TalkBack: "Mask, button, double-tap to show only part of this clip").

### The Mask panel (ToolPanel)
Title row: **Mask** (`title`, heading), **Done** (Ghost). Under it, **SegmentedTabs: Shape · Position** (two views of one panel). The panel opens on **Shape** (the tab is remembered for the visit).

**Shape tab**
1. **Five OptionTiles** (stacked layout; pictures **drawn from data**: a 20 dp shape in `text-secondary`, selected `on-primary` on white): **None · Rectangle · Circle · Linear · Mirror**, one choice, scrolling edge to edge. A tap applies at once, one undo step ("Mask"). **None** removes the mask (the tile is selected when there is no mask). Tapping the selected tile does nothing.
2. **Edge softness**: Slider 0 to 100%, step 10%, default 0%, readout `0%`. How gradually the edge fades. Hidden while None is selected.
3. **Invert mask**: a Toggle row ("Invert mask"), default off: show the outside instead of the inside. Hidden while None is selected.
4. **Rounded corners**: Slider 0 to 100%, step 10%, default 0%, only for **Rectangle**. 100% rounds the corners as far as the rectangle's shorter side allows (a pill).
5. For **Linear**, one hint line under the tiles in `label` `text-secondary`: "The arrow points to the side that stays." Nothing for the other shapes.
- Sliders: live on the stage while dragging, one undo step on release; double-tap the thumb resets; keyboard and TalkBack steps of 10%. No snap.

**Position tab** (the single-pointer route for the frame's gestures, WCAG 2.5.7; P2-05's censor rows, applied to the mask). Shown only when a shape is selected (with None the tab explains `explain_mask_none`).

| Row | Shapes | What a tap does |
| --- | --- | --- |
| **Width** (minus, readout, plus) | Rectangle, Circle, Mirror (the band's width) | 2% of the clip's box per tap, hold repeats (400 ms, then every 50 ms); readout in percent of the box |
| **Height** (minus, readout, plus) | Rectangle, Circle | the same |
| **Rotation** (rotate left, readout, rotate right) | all | the P1-10 panel's step |
| **Move** (four arrows) | all | 2% of the clip's box per tap, hold repeats; the center stays within the box |
| **Center** (Secondary button) | all | puts the mask at the clip's center |

- **Linear** has Rotation, Move and Center (its place is a line through a point); **Mirror** has Width, Rotation, Move, Center.
- **Limits explain on tap:** `explain_mask_min` at the smallest size (5% of the box), `explain_mask_max` at the biggest (200%), `explain_mask_edge` for a Move arrow at the box's edge.
- Undo steps: each tap, each hold (on release), Center: "Mask position".

**Budgets** (region 229 / 302 / 348 dp; content = region − 96 = **133 / 206 / 252 dp**; **rows measured from the component code**: a stacked OptionTile is 24 + 4 + 16 + 16 = 60 dp; a Slider row is a 16 dp label line over a 48 dp rail row = 64 dp, with the earlier specs' 68 dp from the P3-09a build as the safe figure; a Toggle row 48; a stepper row 48; SegmentedTabs 48; gaps 16):
- **ToolPanel pinned rule:** the title row (48) plus the tabs (48) = 96 dp may take 40% of the panel at most (`pinned-bar-max-fraction`): that is 91.6 dp at 360 × 640, so **there the tabs scroll with the content** and only the title stays; at 360 × 800 (121 dp) and 412 × 915 (139 dp) the tabs are pinned.
- **Shape tab, Rectangle (the longest):** 60 + 16 + 68 + 16 + 48 + 16 + 68 = **292 dp**; Circle and Mirror 208 dp; Linear 60 + 32 (hint) + 16 + 68 + 16 + 48 = 240 dp; None 60 dp.
- **360 × 640:** the tabs and the tiles show (48 + 16 + 60 = 124 of 133); Edge softness is under the fold and scrolls. **360 × 800** (pinned tabs, 206 − 48 − 16 = 142 dp of content): the tiles and Edge softness show (60 + 16 + 68 = 144; the last 2 dp of the slider's target is cut); Invert mask and Rounded corners scroll. **412 × 915** (204 dp of content): the tiles, Edge softness and Invert show (208); Rounded corners scrolls.
- **Position tab:** at most 5 rows × 48 + 4 gaps × 16 = 304 dp; it scrolls at every size but 412 × 915, where the first four rows show (the Center button is cut). Accepted: it is the secondary route, and the Position panels of P4-01b and P4-16 scroll the same way.
- At 200% font every tab scrolls on every phone, the tabs themselves size to their labels and scroll sideways (SegmentedTabs rule).

### The mask on the stage
While the Mask panel is open and a shape is selected, the stage shows the **mask frame** and takes the touches inside the clip's box (touches outside the box play or pause as everywhere):

| Shape | The frame | Handles |
| --- | --- | --- |
| Rectangle | The rectangle's outline (rounded as the corners are), turned with it | The `canvas-handle-size` scale-and-rotate handle at its bottom-right corner (`scale_rotate`) and the four edge handles (`edge-handle-length` by `edge-handle-thickness`, a `selection` bar with a 1 px `on-primary` edge, P2-05) for Width and Height |
| Circle | The ellipse's outline, turned with it | The same handle and edge handles, on its box |
| Linear | A line through the mask's point, extended across the clip's box (clipped at the stage's edge), with a solid arrowhead (`arrow`, `icon-small`, two-tone) at the point showing the **side that stays** | The scale-and-rotate handle at one end of the line turns it; a drag anywhere on the line moves it across the box |
| Mirror | Two parallel lines (the band's edges), turned with it | An edge line dragged changes the width; the scale-and-rotate handle at one end of the middle turns it; a drag inside moves it |

- **Look:** two-tone as P1-10: `stroke-selection` white with a `stroke-hairline` `on-primary` edge (4.3:1 or more over any picture); the handle and edge handles as the censor's. Clipped at the stage's edge; the handle's center is never closer than half its size to the stage edge (P4-01b).
- **Frame edge** (P1-11): shows while the mask is moved, sized or turned and while the panel is open. Never otherwise.
- **Gestures** (the caption gestures, applied to the mask): one finger inside the frame moves it 1:1; the handle scales and turns it; two fingers anywhere on the stage pinch, twist and pan the mask. Snaps (one SegmentTick per engagement, with a guide): the mask's center to the clip's box center within `space-2` on screen, its sides flush with the box edges within `space-2`, rotation to 0°, 90°, 180°, 270° within 4°. **Limits:** size 5% to 200% of the box; the center within the box; nothing toasts while dragging.
- **Live feedback:** the mask is a parameter of the clip's own draw, so the preview redraws it per frame as the finger moves; **no stand-in** (unlike a moved overlay, P4-01b): the mask shape and the picture under it change together in one pass.
- **One gesture is one undo step**, committed on release: "Mask position". Undo and redo are ignored while a finger is down.
- **Picking a shape places it** at the box's center at its default size; the user then drags. Defaults: Rectangle 70% of the box's width by 70% of its height; Circle 70% of the box's shorter side; Linear through the center, 0°, keeping the upper side; Mirror a band 40% of the box's shorter side wide, 0° (a horizontal band). Edge softness 0%, Invert off, Rounded corners 0%.

### The Blend panel (ToolPanel; built with P4-03a)
Title row: **Blend** (`title`, heading), **Done** (Ghost). Content: one scrolling row of **OptionTiles, label layout** (no picture; `body-strong`, one line, `space-4` side padding, edge to edge, the selected tile scrolled into view): **Normal · Multiply · Screen · Overlay · Darken · Lighten · Add · Difference · Soft light · Hard light · Exclusion** (Normal plus the 10 modes). A tap applies at once, one undo step ("Blend"); the result is live on the stage while playback runs. Under the row, one hint line in `label` `text-secondary`: "Mixes this overlay with the picture beneath it."
- **Budget:** tile row 48 + 16 + hint 32 (two lines) = **96 dp**; fits at every size (133 / 206 / 252). At 200% the tile labels stay one line and widen, the hint wraps to three lines and the panel scrolls at 360 × 640.
- **Nothing beneath** the overlay at some moment (the overlay lies past the main video's end, over the black gap): blends against black, as the engine does.

## Rules behind the layer
### Blend: the 10 modes (P4-03a's list; ids saved forever)
Per color channel in **sRGB gamma space on 8-bit values**, as the W3C separable blend modes, with *Cb* the backdrop (everything painted beneath the overlay: the main video and its background, the lanes below) and *Cs* the overlay's color, then composited by the W3C formula with the overlay's alpha (its own alpha times its opacity): *Cr* = (1 − αs) · *Cb* + αs · *B*(*Cb*, *Cs*) for an opaque backdrop. Captions and stickers are above the overlays and are never blended into them.

| Id | Name | *B*(*Cb*, *Cs*) | What it does (for translators and QA) |
| --- | --- | --- | --- |
| `normal` | Normal | *Cs* | The overlay covers (the default; not one of the 10) |
| `multiply` | Multiply | *Cb* · *Cs* | Darkens; white disappears, black stays black. Shadows, ink, text on paper |
| `screen` | Screen | *Cb* + *Cs* − *Cb* · *Cs* | Lightens; black disappears. Smoke, glow, light leaks |
| `overlay` | Overlay | HardLight(*Cs*, *Cb*), that is the two swapped | Boosts contrast: darks darker, lights lighter, mids from the overlay |
| `darken` | Darken | min(*Cb*, *Cs*) | Keeps the darker of the two |
| `lighten` | Lighten | max(*Cb*, *Cs*) | Keeps the lighter of the two |
| `add` | Add | min(1, *Cb* + *Cs*) | Piles the light on; glow, sparks, lens flares |
| `difference` | Difference | \|*Cb* − *Cs*\| | Inverts where the overlay is bright; glitch and psychedelic looks |
| `soft_light` | Soft light | the W3C soft-light function | A gentle contrast, as a diffused spotlight |
| `hard_light` | Hard light | Multiply(*Cb*, 2 *Cs*) if *Cs* ≤ 0.5, else Screen(*Cb*, 2 *Cs* − 1) | A strong contrast, as a hard spotlight |
| `exclusion` | Exclusion | *Cb* + *Cs* − 2 · *Cb* · *Cs* | Like Difference with less contrast |

- **Opacity applies first** (the overlay's alpha), then the blend. The pipeline position is P4-04's: blend is the last step over everything beneath.
- **Not offered:** modes that work on hue, saturation and luminosity (Hue, Color, …), and Color dodge and Color burn: ten is the PRD's number and the 10 above are the ones whose names a meme editor's user knows. A later list change is a data change (new ids), never a rename.

### Mask: how it draws (preview and export match)
A parameter on the clip, never a new file; part of every draft's look, so fixed; in `design/tokens.json` → `blendMask`. Numbers are starting values the reference renders pin (the P2-04 rule).

| Rule | Value |
| --- | --- |
| Space | The clip's **own box**: its picture after turn, mirror, crop and Fit or Fill, **before** framing (P4-16) and before keyframed motion, so the mask moves, scales and turns with the clip. Fractions of that box |
| One mask per clip | Setting a shape replaces the previous one |
| Alpha | Inside the shape 1, outside 0 (Invert swaps), multiplied into the clip's alpha. Edge: *f* = softness × 25% of the box's shorter side, centered on the edge: alpha = 1 − smoothstep(−*f*/2, +*f*/2, *sd*), *sd* the signed distance (negative inside) in pixels of the box |
| Rectangle | center, width, height (fractions of the box's width and height), rotation, corner roundness *r* (0 to 1): corner radius = *r* × half the rectangle's shorter side |
| Circle | center, width and height as fractions of the box's shorter side (so a new one is round), rotation |
| Linear | a point and an angle; the half-plane on the arrow's side stays |
| Mirror | a point, an angle and a width (fraction of the box's shorter side); the band between two parallels stays |
| Pipeline | after key and color edits, after Fit or Fill, **before framing**, before opacity and blend (P4-04 table) |
| Not keyframed | In v1 a mask's own parameters don't animate; the clip's keyframes (P4-02b) move the masked clip as a whole. Later |

**Reference renders** (CLAUDE.md rule 5) in `docs/qa/references/P4-03-blend-masks/`, 1080p frames at 1.0 s, 9:16 canvas, on the debug draft **`blend-mask-check`** (Requests 6). Blend: a main clip of a horizontal black-to-white ramp over a color bar strip (top half ramp, bottom half six bars) and an overlay at 60% size centered, a vertical gradient with six saturated patches; masks: one 12 × 12 lettered grid clip as the overlay.

| File | Setup | The frame must show |
| --- | --- | --- |
| `blend-normal` … `blend-exclusion` (11 files) | Each mode, overlay opacity 100% | The pixel values from the formulas within 3/255 per channel on a flat sample of each patch |
| `blend-multiply-50`, `blend-screen-50` | Opacity 50% | The blend at half strength (opacity before blend) |
| `mask-rect` | Rectangle at the defaults | A 70% × 70% window of the grid, sharp edge |
| `mask-rect-rounded` | Rounded corners 50% | The same, corners rounded to half the possible |
| `mask-circle` | Circle at the defaults | A round window, diameter 70% of the shorter side |
| `mask-circle-soft` | Circle, Edge softness 50% | A soft edge about 12.5% of the shorter side wide, centered on the circle's edge |
| `mask-linear` | Linear at the defaults | The upper half of the grid kept, a sharp horizontal edge |
| `mask-linear-rotated-invert` | Linear, 30°, Invert | The other side kept, edge turned 30° |
| `mask-mirror` | Mirror at the defaults | A horizontal band, 40% of the shorter side tall |
| `mask-mirror-rotated-soft` | Mirror, 45°, Edge softness 25% | A diagonal soft band |
| `mask-on-clip-moved` | A circle on an overlay moved and scaled with Position | The mask moved and scaled with the overlay |
| `mask-on-main-over-image` | Circle on the main clip, canvas Image background | The picture around the circle |
| `mask-plus-key` | Green screen (P4-04) then a circle | The mask cuts the keyed subject |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tools on the bars | `mask` and `blend` icons and labels, normal look | `tool_mask`, `tool_blend` |
| Mask panel, no mask | Shape tab: the tiles with None selected; no sliders; the Position tab explains | `mask_title`, `mask_none`, `explain_mask_none` |
| A shape picked | Tile white; the shape at its defaults on the stage with its frame and handles; the sliders appear (200 ms height change, none under reduce motion) | `mask_*` |
| Linear | The hint line; the arrow on the frame | `mask_linear_hint` |
| Mask moved, sized or turned on the stage | The mask follows the finger; guides; the frame edge | none |
| Slider dragged, Invert toggled | The result live; one step on release | `mask_feather`, `mask_invert`, `mask_corners` |
| Position tab | Rows for the shape | `position_width`, `position_height`, `position_rotation_*`, `position_move_*`, `position_center` |
| Size limit, edge limit | Toast, the control keeps its look | `explain_mask_min`, `explain_mask_max`, `explain_mask_edge` |
| Mask panel closed | The frame goes; the mask stays in the result | none |
| Blend panel | The tile row, the current mode white; the result live | `blend_title`, `blend_*`, `blend_hint` |
| Blend on a photo overlay | The same | same |
| Undo or redo | Applies at once; a panel on a clip that no longer exists closes; a toast names the step | `editor_undo_toast`, `edit_*` |
| Preview catching up | The last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Item not visible at the playhead | The playhead jumped to its start when the panel opened | none |
| Missing file | The tools are absent | `editor_missing_media`, `editor_missing_overlay` |
| Reorder mode | Main bar replaced; tools not reachable | P1-06 |
| Offline, permission | Not applicable | none |
| Long content (5 languages, 200% font) | Tile names stay one line and the tile widens; the tabs size to their labels and scroll sideways; sliders' labels wrap beside their readouts; panels scroll with the title row in place | none |
| First run | Same as every run; picking a shape places it, which is the teaching | none |

## Interactions
- **Press feedback:** token pressed states, 120 ms; nothing moves.
- **Haptics:** SegmentTick for each snap engagement on the stage; nothing else.
- **Undo steps** (each is one): a shape tile ("Mask"); a slider release or the Invert toggle ("Mask"); a stage gesture, a Position tab tap, a hold on release, Center ("Mask position"); a Blend tile ("Blend"). Closing a panel records nothing.
- **Replacing a mask:** picking another shape keeps what carries over: Edge softness and Invert stay; the place becomes the new shape's default (a mask moved to the corner doesn't carry its place into a different shape: the sizes mean different things).
- **Keyboard (basic, P1):** Tab reaches the tools, tabs, tiles, sliders (arrows step by 10%), the toggle, every Position row; Enter activates; Escape is system back. The stage frame needs a pointer: the Position tab is the keyboard route (arrow-key nudging is Later, L-01).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens or closes | 200 ms slide (ToolPanel) | 120 ms fade (instant on Android) |
| Tab switch | 200 ms content change | 120 ms fade |
| Mask frame and handles appearing | 120 ms fade | same |
| Sliders and Invert appearing after a shape is picked | 200 ms height change | at once |
| The mask and the blend on the stage | At once, per frame | same |
| Tile selection | 120 ms fade | same |

## Copy
English source; drafts for id, es, pt and hi as in P4-01b; the engineer machine-drafts the rest until P5. Terms: mask = *masker*, *máscara*, *máscara*, *मास्क*; blend = *Campur*, *Mezcla*, *Mesclar*, *ब्लेंड*. Budgets are characters, placeholders expanded.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_mask` | Mask | 12 | Masker · Máscara · Máscara · मास्क | Tool label and panel title (`mask_title` is the same string) |
| `tool_mask_click` | Show only part of this clip | 40 | Tampilkan hanya sebagian klip ini · Mostrar solo una parte de este clip · Mostrar só uma parte deste clipe · इस क्लिप का सिर्फ़ एक हिस्सा दिखाएं | Click label; 27 en |
| `tool_blend` | Blend | 12 | Campur · Mezcla · Mesclar · ब्लेंड | Tool label and panel title (`blend_title`) |
| `tool_blend_click` | Mix this overlay with what's beneath it | 50 | Campur overlay ini dengan yang ada di bawahnya · Mezclar esta superposición con lo que hay debajo · Misturar esta sobreposição com o que está embaixo · इस ओवरले को नीचे की तस्वीर से मिलाएं | Click label; 39 en, es 48 |
| `mask_tab_shape` | Shape | 12 | Bentuk · Forma · Forma · आकार | Tab; the other tab is `tool_position` (reuse) |
| `mask_none` | None | 12 | Tidak ada · Ninguna · Nenhuma · कोई नहीं | Tile; reuse `filters_none` if the shipped word fits ("Tidak ada", "Ninguno") |
| `mask_rectangle` | Rectangle | 16 | Persegi panjang · Rectángulo · Retângulo · आयत | Tile: a rectangular window |
| `mask_circle` | Circle | 12 | Lingkaran · Círculo · Círculo · वृत्त | Tile: a round window |
| `mask_linear` | Linear | 12 | Linier · Lineal · Linear · रेखीय | Tile: one side of a straight line stays |
| `mask_mirror` | Mirror | 12 | Cermin · Espejo · Espelho · मिरर | Tile: a strip stays, like a mirror's band |
| `mask_feather` | Edge softness | 20 | Kelembutan tepi · Suavidad del borde · Suavidade da borda · किनारे की कोमलता | Slider label; the same words as `green_screen_softness` |
| `mask_invert` | Invert mask | 24 | Balikkan masker · Invertir máscara · Inverter máscara · मास्क उलटें | Toggle label: show the outside instead of the inside |
| `mask_corners` | Rounded corners | 24 | Sudut membulat · Esquinas redondeadas · Cantos arredondados · गोल कोने | Slider label (rectangle only) |
| `mask_linear_hint` | The arrow points to the side that stays. | 50 | Panah menunjuk ke sisi yang tetap terlihat. · La flecha apunta al lado que se queda. · A seta aponta para o lado que fica. · तीर उस तरफ़ है जो बना रहता है। | Hint under the tiles for Linear; 39 en, id 46 |
| `explain_mask_none` | Pick a shape first. | 40 | Pilih bentuk dulu. · Elige primero una forma. · Escolha antes uma forma. · पहले कोई आकार चुनें। | Toast, the Position tab with no mask |
| `explain_mask_min` | This is the smallest the mask gets. | 50 | Ini ukuran masker terkecil. · Es el tamaño mínimo de la máscara. · Este é o menor tamanho da máscara. · मास्क इससे छोटा नहीं होता। | Toast; 35 en |
| `explain_mask_max` | This is the biggest the mask gets. | 50 | Ini ukuran masker terbesar. · Es el tamaño máximo de la máscara. · Este é o maior tamanho da máscara. · मास्क इससे बड़ा नहीं होता। | Toast |
| `explain_mask_edge` | The mask can't go further this way. | 50 | Masker tidak bisa bergerak lebih jauh ke arah ini. · La máscara no puede ir más lejos así. · A máscara não pode ir mais longe assim. · मास्क इस तरफ़ और आगे नहीं जा सकता। | Toast; 35 en, id 51 (trim "bisa" when drafting) |
| `blend_hint` | Mixes this overlay with the picture beneath it. | 60 | Memadukan overlay ini dengan gambar di bawahnya. · Mezcla esta superposición con la imagen de abajo. · Mistura esta sobreposição com a imagem de baixo. · इस ओवरले को नीचे की तस्वीर से मिलाता है। | Under the tiles; 47 en |
| `blend_normal` | Normal | 14 | Normal · Normal · Normal · सामान्य | Tile |
| `blend_multiply` | Multiply | 14 | Multiply · Multiplicar · Multiplicar · मल्टीप्लाई | Tile: darkens (as the mode of that name in photo editors) |
| `blend_screen` | Screen | 14 | Screen · Trama · Tela · स्क्रीन | Tile: lightens. Not the phone screen |
| `blend_overlay` | Overlay | 14 | Overlay · Superponer · Sobrepor · ओवरले | Tile: the **blend mode** named Overlay, boosts contrast. Different from the Overlay tool (a video or photo on top); the group name "Blend" makes it clear. Keep the word editors in your language use for the mode |
| `blend_darken` | Darken | 14 | Gelapkan · Oscurecer · Escurecer · गहरा | Tile: keeps the darker of the two |
| `blend_lighten` | Lighten | 14 | Terangkan · Aclarar · Clarear · हल्का | Tile |
| `blend_add` | Add | 14 | Tambah · Sumar · Adicionar · जोड़ें | Tile: adds the light |
| `blend_difference` | Difference | 14 | Selisih · Diferencia · Diferença · अंतर | Tile |
| `blend_soft_light` | Soft light | 14 | Cahaya lembut · Luz suave · Luz suave · सॉफ़्ट लाइट | Tile |
| `blend_hard_light` | Hard light | 14 | Cahaya keras · Luz fuerte · Luz direta · हार्ड लाइट | Tile |
| `blend_exclusion` | Exclusion | 14 | Eksklusi · Exclusión · Exclusão · एक्सक्लूज़न | Tile |
| `a11y_state_masked` | Mask: %1$s | 24 | Masker: %1$s · Máscara: %1$s · Máscara: %1$s · मास्क: %1$s | Appended to the clip's spoken description; %1$s a shape name |
| `a11y_state_blended` | Blend: %1$s | 24 | Campur: %1$s · Mezcla: %1$s · Mesclar: %1$s · ब्लेंड: %1$s | Appended when the mode isn't Normal |
| `edit_mask` | Mask | 24 | Masker · Máscara · Máscara · मास्क | Edit names after "Undo: " (reuse `tool_mask`) |
| `edit_mask_position` | Mask position | 24 | Posisi masker · Posición de la máscara · Posição da máscara · मास्क की जगह | |
| `edit_blend` | Blend | 24 | Campur · Mezcla · Mesclar · ब्लेंड | Reuse `tool_blend` |

Reused unchanged: `tool_position`, `position_width`, `position_height`, `position_rotation_*`, `position_move_*`, `position_center`, `filters_none` (if its words fit), `panel_done`, `editor_undo_toast`, `editor_updating_preview`, `editor_missing_media`, `editor_missing_overlay`.

Translator notes:
- **Mask** shows only part of a clip, through a shape. **Edge softness** is a gradual fade at the shape's edge, not blur. **Invert** swaps inside and outside.
- **Linear** is a straight dividing line; **Mirror** is a strip of the picture. **Rectangle** and **Circle** need no explanation; the circle can be stretched into an oval.
- **Blend modes:** the 10 names come from photo and video editors. Use the term your language's editors use, or keep the English name where that is what people say. **Overlay** here is the blend mode, not the Overlay tool.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

## Accessibility
- **Panels:** ToolPanel rules: focus to the title on open, back to the tool on close; what they cover leaves the accessibility tree (`coveredByModal`). The shape tiles and the blend tiles are single-choice groups ("Mask shape", "Blend mode"): each reads its name, "selected", its place ("Circle, 3 of 5"); the thumbnails are decorative.
- **The mask frame and its handles** are drag-only and hidden from accessibility; the **Position tab** (Width, Height, Rotation, Move, Center) is the full single-pointer alternative (WCAG 2.5.1, 2.5.7), reachable by keyboard and TalkBack. After each step the result is announced politely ("Width, 60 percent").

  | Gesture | Alternative |
  | --- | --- |
  | Drag to move the mask | Position tab → Move arrows, Center |
  | Pinch or handle to size and turn | Width, Height, Rotation steps |
  | Edge handles | Width and Height steps |

- **Sliders:** the Slider rules, steps of 10% on the grid; the value announced politely after a change.
- **The clip's spoken description** gains `a11y_state_masked` and `a11y_state_blended` after P4-04's and P4-02b's states (order: Cropped, Rotated, Flipped, Moved or resized, Green screen, Mask, Blend, Keyframes). Never in the state description.
- **Touch targets:** every tool, tile, tab, toggle row and stepper is 48 dp or more; the edge handles' and corner handle's targets are 48 dp (inner halves at the stage edge).
- **Contrast:** tiles `text` on `surface-raised` 12.4:1, selected `on-primary` on white 18.9:1; the frame white with a near-black edge 4.3:1 or more over any picture; the Toggle as its README (off thumb 4.7:1 on its track, on thumb 7.9:1); the hint `text-secondary` on `surface` 5.7:1.
- **200% font:** the title row wraps Done under the title; the tabs size to their labels and scroll; tile names stay one line and the tile widens; the hint and slider labels wrap; panels scroll with the first control in view. Hindi at 200% is a QA screenshot.
- **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never for opening a panel, switching a tab or when a tool only explains). New ids for the PM to confirm (they are the edit names in the undo history):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A shape tile that changes the mask (including None) | `tool: "mask"`, **`value`: `none`, `rectangle`, `circle`, `linear` or `mirror`** |
| `tool_use` | A slider release, the Invert toggle, a stage gesture release, a Position tab step | `tool: "mask_adjust"` |
| `tool_use` | A blend tile that changes the mode | `tool: "blend"`, **`value`: the mode id** |

- **`value` is used for the shape and the mode** (it is the canvas tools' optional parameter today, PRD → Metrics; the PM confirms this second use, as the `filter` parameter was confirmed for P2-04). Without it a shape or a mode can't be told apart in the dashboards. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `blend-mask-check` (Requests 6) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-03b/`.
1. **The bars:** an overlay's bar scrolled to Blend and Mask; a main clip's bar scrolled to Mask; Spanish at 200%.
2. **The Mask panel**, Shape tab: None; Rectangle (the three sliders and toggle, scrolled); Linear with its hint; at 360 × 640 (tabs scrolling with the content), 360 × 800 (tabs pinned) and 412 × 915. Hindi at 200% font.
3. **The Position tab** at 360 × 800 for Rectangle and for Mirror; the toast at the smallest size.
4. **Each mask** on the stage with its frame and handles (Rectangle with corner and edge handles; Linear with its arrow; Mirror with its band lines), then the same shape after Done: the frame gone, the result kept.
5. **Gestures:** a screen recording: move, pinch, twist, a snap with its guide, the frame edge; no stand-in flash.
6. **The Blend panel** with each of the 11 tiles on the debug clip, next to its reference render; Hindi at 200% font.
7. **Mask on a main clip** over an Image background; **mask plus key**.
8. **Undo** after each: exactly that edit goes.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Each blend mode matches its reference render** (03a): export `blend-mask-check` at 1080p30, frames at 1.0 s; sample the centers of the six patches in the export and compute the formula's value from the sampled backdrop and overlay colors: within 3/255 per channel; the preview screenshot (cropped to the frame, scaled to the export width) within 3/255 of the export on 99% of pixels.
- **Each mask matches its reference render** (03b): the edge position within 1% of the box's width on three samples along the edge; the inside alpha 253/255 or more and the outside 2/255 or less away from the edge band (hard edges); the soft edge's width within 2% of the box's shorter side; preview and export within 3/255 on 99% of pixels.
- **Each edit is one undo step:** a shape, a slider release, Invert, a stage gesture, a Position tap, Center, a blend tile: Undo once after each.
- **Old drafts open:** a draft saved before P4-03 has no mask and Normal blend, and looks as before.
- **Speed:** a masked 1080p overlay over a 1080p main clip stays at 30 fps or more (Media3 logs, the owner's phone); a mask drag for 3 s three times: janky frames under 5%.
- **Leaks:** LeakCanary: open the Mask panel, pick shapes, drag, Invert, switch tabs, Done, open Blend, pick modes, close the editor; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Blend | `MediaClip.blend: BlendMode = NORMAL` for overlays (`NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `DARKEN`, `LIGHTEN`, `ADD`, `DIFFERENCE`, `SOFT_LIGHT`, `HARD_LIGHT`, `EXCLUSION`), ids as the table (saved forever); main clips ignore it |
| Mask | `MediaClip.mask: ClipMask? = null`: `shape` (RECTANGLE, CIRCLE, LINEAR, MIRROR), `centerX`, `centerY`, `width`, `height`, `rotationDegrees`, `softness` (0 to 1), `invert`, `cornerRoundness` (0 to 1); null means none. Fractions of the clip's own box (the space above) |
| Declarative | A `BlendSpec` and `MaskSpec` registry in `:core:model` with the formulas as constants; each has a reference render (the `media-effect` skill, rule 5) |
| Edits | `setClipBlend`, `setClipMask`, `setClipMaskPlace`, pure, one `commit` each |
| Rules | Split and Duplicate copy both; Replace keeps both; a crop re-applies the mask to the new box (its fractions stay) |
| Version | A `schemaVersion` bump with an identity migration (fields default to `NORMAL` and null); one bump per ticket pair as the kickoff proposal says (03a adds both fields) |

## Requests to the principal mobile engineer
1. **Blend** (P4-03a): the 10 modes as GLSL in the compositor, the formulas above in sRGB gamma on 8-bit values; verify the current Media3 compositor API at the start (a custom `VideoCompositorSettings` or shader over the overlay, with the backdrop as input). iOS: marked stub that reports unsupported.
2. **Mask** (P4-03b): one fragment pass in the clip's own space before framing; signed-distance functions for the four shapes; preview and export from one plan.
3. **Panels and tools:** the Mask panel (SegmentedTabs, OptionTile pictures drawn from data, Slider, Toggle, the Position tab's rows reusing the censor's stepper rows), the Blend panel (OptionTile label layout), the mask frame on the stage (two-tone, corner and edge handles from P2-05), gestures with the stand-in not needed, snapping; `coveredByModal` on the covered region.
4. **Edits** in `:core:domain` as in Model needs.
5. **Icons:** `mask` and `blend` (below). **Strings** in the five files. **Analytics:** the three `EditorTool` values and the `value` on `mask` and `blend`.
6. **Debug draft `blend-mask-check`** (the ramp-and-bars main clip, the gradient overlay, the lettered grid overlay) and the reference scenes; frames saved to `docs/qa/references/P4-03-blend-masks/`.
7. **Constants:** one commonMain `BlendMaskCatalog` (the 10 ids, defaults, limits, feather share) with a comment pointing to this spec and `design/tokens.json` → `blendMask`; never through `MemixTheme`.

## Tokens and icons
- **New content constants** in `design/tokens.json` and `design/system/tokens.json` → `blendMask` (the 10 ids and formulas, mask shapes, defaults, ranges, feather share, pipeline position). Kotlin `BlendMaskCatalog`. Not in `MemixTheme`: user content.
- **`clipFrame.pipeline`** (in the same two files) is extended to the full Phase 4 order, set in P4-04: turn, mirror, crop, key, color, fit-or-fill, mask, framing, opacity, blend (the mask sits before framing, so it moves with the clip).
- **No new color, size, radius or motion token.** The edge handles reuse `edge-handle-length` and `edge-handle-thickness`; the frame reuses `canvas-handle-size`. The mask shape tiles are drawn from data (a 20 dp shape; Rectangle, Circle, Linear (a line with an arrowhead), Mirror (a band)), like the ratio shapes, not icon files.
- **Two new icons** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review):
  - `mask` (Kotlin `MemixIcons.Mask`, `ic_mask.xml`): a stroked 20 × 20 px square (2 px stroke, 1 px corners) holding a **solid 12 px circle** (the core), 2 px clear of the stroke on every side. It must not read as `overlay` (a solid rectangle in a rectangle), `green_screen` (a small bust in a landscape frame) or `photo` (a hill).
  - `blend` (Kotlin `MemixIcons.Blend`): two stroked 14 px circles overlapping by 6 px horizontally (2 px stroke), their **overlap solid** (the core, a lens 6 px wide), the circles' outlines 1.4 px or more from each other where they don't overlap. It must not read as `duplicate` (rectangles) or `opacity` (a circle with a solid half-disc).
- **Pointer edits made with this spec:** ToolBar README (Blend, Mask in the bars), DESIGN_SYSTEM (iconography, the Phase 4 pipeline, a Blend and masks note).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Where do blend modes live?**
- *Problem:* 10 modes need a picker. They could be an extra row in the Opacity panel or their own tool.
- *Considerations:* **A. Their own Blend tool and panel** (recommended; applied): the Opacity panel stays one slider and fits every phone; the tool is one more entry in an overlay bar that already scrolls (it sits behind Crop); cost: one extra tap compared with a combined panel. **B. A tile row inside the Opacity panel:** one fewer tool, but the panel grows from 68 dp to about 150 dp and scrolls at 360 × 640, and a blend is not an opacity.
- *Reverse:* move the row; no migration.

**2. Can masks be put on the main clip, or only on overlays?**
- *Problem:* a mask on the main clip reveals the canvas background around it (black, a color, Blur or an Image).
- *Considerations:* **A. Both** (recommended; applied): a circle window on your own clip over a picture is a classic job; the background rules already exist. **B. Overlays only:** simpler, but the main clip can't be masked, the same trick-workaround as keying.
- *Reverse:* hide the tool on main clips; saved masks would be ignored.

**3. Should blend modes work on the main clip?**
- *Problem:* nothing sits beneath the main clip except the background.
- *Considerations:* **A. No** (recommended; applied): a mode on a flat background is a color tweak the Filters tool already does. **B. Yes, blend with the background:** works with an Image background (a clip multiplied over a texture); more to explain for a small gain.
- *Reverse:* a data-only change.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The 10 separable blend modes and the soft-light and hard-light functions, and the compositing formula, are standard | Guidance (W3C) | [Compositing and Blending Level 1](https://www.w3.org/TR/compositing-1/) |
| The four masks (rectangle, circle, linear, mirror) are the standard set of mobile video editors, each with a feather, an invert and a position; the PRD names the four | Assumption (product knowledge of CapCut and VN; their help pages weren't reachable here) | none |
| A drag-only frame needs a single-pointer alternative; the Position tab is it | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| White plus a dark edge keeps a mark visible over any picture (4.3:1 or more) | Guidance (SC 1.4.11 needs 3:1; Memix computed the figure in P1-10) | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Media3 offers custom compositor settings and GL shader programs for per-frame effects | Guidance (API docs; verify at the start of the build) | [Define layouts with a custom VideoCompositorSettings](https://developer.android.com/media/media3/transformer/videocompositorsettings) |
| Memix's screen-measured row heights (tile 60, slider 64 or 68, toggle 48, tabs 48) | Evidence (read from `OptionTile.kt`, `Slider.kt`, the component READMEs) | the files in `core/designsystem` |
