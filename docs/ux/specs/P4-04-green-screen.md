# Green screen keying on clips and overlays · P4-04

Third Phase 4 spec. It adds one tool and one panel to the video editor and reuses, and does not repeat:
- `P4-01b-overlay-layers.md`: overlay lanes, the overlay and main bars, stage gestures, the "panel open, gestures stay live" and "playhead jumps to the item's start" rules, the one-slot status banner.
- `P4-16-clip-crop-rotate-flip-fit.md`: the clip pipeline (turn, mirror, crop, Fit or Fill, framing), the main clip's tools, Replace, the Canvas panel.
- `P1-06-clip-edits-and-audio.md` and `P3-09-audio-effects.md`: the ToolPanel with a header button (Reset there, Turn off here), "Preview catching up".
- `P2-12-canvas-background-image.md`: what shows through a keyed main clip (the canvas background).
- Component READMEs: ToolPanel, Slider, Swatch, Toast.

The model and the engine are P4-04 itself (a `ChromaKey` on the clip, the shader); P4-01a is built before it. "Model needs" lists what the UI expects.

## Job
"Put a character on my clip" (a meme character shot on green, laid over the user's video) and "put me somewhere else" (the user's own green screen clip over a picture). PRD → Video editor → Layers: green screen keying (pick color, strength, edge softness); PRD Templates: "Green screen, cut-out" keys a character out of green. **One main action: none.** Export stays the one blue action (P1-12); the panel's Done is Ghost (ToolPanel).

**Done when** (TICKETS): a standard green screen clip keys cleanly at default settings. Measured on a fixture, with a real clip on the owner's list (Measured, below). "Cleanly" is made checkable: no green left in the background, no holes in the subject, no green fringe at the edge.

**Fast path, with tap counts:**
- **A green clip as an overlay:** Overlay (1), the clip in the picker (2), **Green screen (3)** (it is the fourth tool of the overlay's bar, in view at 360 dp). Keying is on, at the defaults, the moment the panel opens. Three taps, no setting touched.
- **Your own green clip, main track:** the clip (1), Green screen (2, one swipe of the bar). Put a picture behind it with the Canvas panel's Image background (P2-12).
- **A blue screen, or a green that isn't standard:** Green screen (1), the **Blue** swatch or the eyedropper and a tap on the preview (2, 3).
- **Fringe or holes:** Strength and Edge softness: drag (1 each).
- **Off again:** Turn off (1), or Undo.

**Principles check.** Content first: the panel is three controls; the picture stays on the stage and live. Sound first: nothing here touches sound; the pinned "Add a meme sound" row is untouched. Fast path: on at open (Question 1). One-handed: swatches and sliders sit in the lower half; the eyedropper's loupe floats above the finger, never under it. Forgiving: every change is one undo step; a pick that misses explains and changes nothing. Accessible: presets and sliders are the whole route for a screen-reader user; the one task that needs sight, finding the color, has the presets as its fallback (Accessibility). Five languages: tool and panel labels were measured at 200% (Copy).

**Color roles:** no blue except the panel's `primary` slider fill (Slider README). The key swatches show **content colors** (the color being removed), not chrome. White is selection (the selected swatch's ring, the loupe's ring).

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Green screen panel (ToolPanel) | The **Green screen** tool of a selected main video or photo clip, or a video or photo overlay | Done, system back, Escape; **Turn off** (keying removed); Undo of the step that turned keying on. The selection stays. |
| Pick mode (stage) | The eyedropper swatch | One pick (a release on the clip), or the eyedropper swatch again, or Done, back or Escape (nothing picked) |

- **Arrives from:** the clip bar of the selected item (table below). **Not for** captions, stickers, sounds or cut-outs; none has a picture to key (stickers and cut-outs already have transparency).
- **First open on an item that isn't keyed:** keying turns on at the defaults **as the panel opens**, as one undo step ("Green screen"). The panel therefore always means "keyed". Open on a keyed item: the saved values, no step.
- **Playhead:** playback keeps running if it was running (the point is to see the matte). If the playhead is outside the item's span, it jumps to the item's start when the panel opens (P4-01b rule). **Pick mode pauses** playback and keeps it paused until the mode ends.
- **Kept:** the key color, strength and softness on the clip, in the project (autosave, undo). Not kept: pick mode, the panel.
- **A clip whose file is missing:** the tool is absent from the bar (P4-16: "usual order without the tools that act on nothing").

## Layout
Board: none. The panel follows the Volume and Opacity panels; the tool bars below are new.

### The tool in the bars
| Selected | Bar (final consolidated order for the Phase 4 pro tools; the other tools arrive with their tickets) |
| --- | --- |
| Video overlay | Position · Opacity · Delete · **Green screen** · Split · Volume · Crop · Blend · Mask · Filters · Keyframes · Layer order · Move here · Duplicate · Detach audio |
| Photo overlay | Position · Opacity · Delete · **Green screen** · Split · Crop · Blend · Mask · Filters · Keyframes · Layer order · Move here · Duplicate |
| Main video clip | Split · Volume · Delete · Crop · Fill or Fit · Position · Filters · **Green screen** · Mask · Keyframes · Duplicate · Detach audio · Reorder · Replace |
| Main photo clip | Split · Delete · Crop · Fill or Fit · Position · Filters · **Green screen** · Mask · Keyframes · Duplicate · Reorder · Replace |

- **Why fourth on an overlay:** the common overlay job is a character on green; the tool must be in view without a scroll (Close, then Position, Opacity, Delete and the fourth tool show whole at 360 dp, ToolBar rule). Split follows, one place right. **Why late on a main clip:** keying a main clip is the rarer job and the framing tools come first (P4-16's order); one swipe finds it.
- Icon `green_screen` (new, below), label "Green screen" (`tool_green_screen`), the normal tool look (`text-secondary`). Name = visible label; click label "Remove a color from this clip" (TalkBack: "Green screen, button, double-tap to remove a color from this clip").
- A keyed clip's tool needs no "on" look: the result is on the stage, and the clip's spoken description says "Green screen" (Accessibility).

### The Green screen panel (ToolPanel)
Title row: **Green screen** (`title`, heading), **Turn off** (Quiet), **Done** (Ghost). Turn off and Done wrap under the title at large font (P2-04 pattern). Three rows, `space-4` apart:

| # | Row | Control | Notes |
| --- | --- | --- | --- |
| 1 | **Color to remove** | A Swatch row: **Eyedropper** (the Pick variant) · **Green** · **Blue** · **Picked** (only when the key color is neither preset). The heading sits **beside** the swatches at 100% font and wraps above them when it doesn't fit | One choice; the selected swatch has the white ring. Tapping Green or Blue sets the key color at once, one step. |
| 2 | **Strength** | Slider 0 to 100%, step 10%, default 50% | How much of the color's range is removed. 0 removes only the exact color, 100 a wide range of similar colors. Readout `50%`. |
| 3 | **Edge softness** | Slider 0 to 100%, step 10%, default 25% | How gradually the removed color blends into the picture at the subject's edge. 0 is a hard cut-out, 100 a wide soft edge. Readout `25%`. |

- **Order = order of use:** what to remove, how much, how soft. Strength and Edge softness are the PRD's names for the three settings (PRD → Layers).
- **Sliders:** the Slider component. Live on the stage while dragging (the matte follows the thumb); one undo step on release ("Green screen"); double-tap the thumb resets; keyboard and TalkBack steps of 10%. **No snap** (no level worth finding again).
- **Spill removal is not a control.** The green light that bounces onto a subject's edges is reduced by a fixed rule (How it draws) on every keyed clip; a fourth slider would make a three-setting tool a pro keyer, and the PRD asks for three.
- **Swatch row details:** the Swatch component with two new variants (DESIGN_SYSTEM → Swatch, to update): **Eyedropper**, a `surface-raised` circle with the `eyedropper` icon at `icon-small` in `text-secondary` (5.7:1), like Blur and Image; and the key colors, **content colors** from `design/tokens.json` → `chromaKey.presets` (Green `#00B140`, Blue `#0047BB`; the picked color is whatever was sampled). A swatch of a color under 3:1 against `surface` gets the `text-muted` ring (Swatch rule; none of these needs it). Spoken names: "Pick from video", "Green", "Blue", "Picked color".
- **Budget** (region 229 / 302 / 348 dp; content = region − 96 = **133 / 206 / 252 dp**; rows measured from the component code: a Swatch row is 48 dp, a Slider row is a 16 dp label line over its 48 dp rail row = 64 dp, and the earlier specs' 68 dp from the P3-09a build is used as the safe figure; gaps 16):
  - Rows: 48 + 16 + 68 + 16 + 68 = **216 dp**.
  - **360 × 640 (133):** the swatch row and Strength show whole (132 dp); Edge softness is under the fold and scrolls. **360 × 800 (206):** all three show; the last 10 dp of Edge softness's target is under the edge (the rail and thumb are in view). **412 × 915 (252):** whole, 36 dp spare.
  - Accepted: at 360 × 640 the second slider scrolls, the ToolPanel rule; the row's order puts the most used first. At 200% font every row grows and the content scrolls on every phone.
- Reset is not offered: Turn off is the way back to nothing, and the sliders' double-tap resets one.

### Pick mode (the eyedropper)
- **Start:** a tap on the eyedropper swatch. The swatch takes the white ring; playback pauses; the stage shows a status banner (the one stage slot, Toast look, no spinner): "Tap the color you want to remove." It stays while the mode lasts. Stage taps no longer play or pause, and stage gestures on overlays are off, so the finger can't move the clip by accident.
- **Picking:** press on the clip and the **loupe** appears: a `swatch-size` circle of the color under the finger with a `stroke-selection` white ring and a `stroke-hairline` `on-primary` edge, floating `touch-target` + `space-2` above the finger (never under it), following it; **release to pick**. Drag to look, release to take. A plain tap picks too. The loupe fades in and out over 120 ms.
- **What is sampled:** the clip's **own** picture (before its geometry, key, filters and framing: the source), through the inverse of its transform, as the average of the 9 × 9 source pixels around the touch. A camera's noise makes one pixel a poor sample. Already-keyed (transparent) places of the clip sample the source color under them, so a user can re-pick the green they already removed.
- **A release outside the clip's drawn box:** nothing is picked; the mode stays; the banner changes to "Tap inside the clip to pick a color." for 2 s, then reads as before.
- **After a pick:** one undo step ("Green screen"); the mode ends; the new color is the key (a **Picked** swatch appears, selected, in the third place); playback stays paused until the user plays.
- **End without a pick:** the eyedropper swatch again, Done, system back or Escape (the first back press ends the mode, the second closes the panel). No step.
- **The sample is the whole decision:** strength and softness are unchanged by a pick.

### The selected item on the stage
- The overlay's white frame and handle (P4-01b) show while the panel is open (gestures stay live) except in pick mode, where they are hidden.
- **What shows through** is the real result, not a checkerboard: the layers beneath (main video, other overlays), or for a keyed main clip the canvas background (black, a color, Blur or the Image of P2-12). The user judges the key against what the meme will actually show. A matte view is Later (Questions).

## How it draws (preview and export match)
A parameter on the clip, never a new file. Same in the preview and in the export. Part of every draft's look, so fixed; in `design/tokens.json` → `chromaKey`. Numbers are starting values; the reference renders pin them (the P2-04 rule).

**Pipeline position** (the whole Phase 4 clip pipeline, which this spec fixes for the specs after it): 1. turn → 2. mirror → 3. crop → **4. key** → 5. color edits, filters and adjustments (P4-07) → 6. Fit or Fill → 7. mask (P4-03b, in the clip's own box, so it moves, sizes and turns with the clip) → 8. framing (P4-16, P4-01) → 9. opacity → 10. blend over everything beneath (P4-03a). Keying runs before the color edits, so a filter never shifts the green and re-keys the clip, and before everything geometric, so it runs on source pixels at source resolution.

| Rule | Value |
| --- | --- |
| Color space | sRGB 8-bit, converted to YCbCr (BT.601, full range); only **Cb and Cr** are compared, luma is ignored, so the shadowed parts of a screen key like the lit parts |
| Distance | *d* = the distance between the pixel's (Cb, Cr) and the key's, each in −0.5 to +0.5 |
| Tolerance | *t* = 0.05 + **0.20** × strength (0.05 to 0.25; was 0.35, changed in the design review: at 100% it removed a white shirt, 0.33 from the key, and skin, 0.43) |
| Feather | *f* = min(0.02 + **0.08** × edge softness, max(0.02, **0.30 − t**)) (was 0.02 + 0.28 × softness, which made the subject see-through at 100%). `MAX_REACH` = 0.30 caps t + f so white (0.328 from the key) stays fully opaque at every setting: default 50/25 gives t 0.15, f 0.04; 100/0 gives 0.25, 0.02; 100/100 gives 0.25, 0.05 (reach 0.30); 50/100 gives 0.15, 0.10 |
| Bleed guard | below alpha 0.25 the key-direction chroma is neutralized fully (rising to the 60% at 0.25), so green never bleeds into a scaled edge; engine detail added in the build, in `design/tokens.json` as `bleedGuardAlpha` |
| Alpha | `smoothstep(t, t + f, d)`: 0 inside the tolerance, 1 beyond it plus the feather; the clip's own alpha (a PNG) is multiplied, never replaced |
| Spill removal | where alpha is above 0, the pixel's chroma component along the key's direction is reduced by 60% × (1 − alpha) toward neutral; fixed, no control |
| Default key | Green `#00B140`, strength 50%, edge softness 25% |
| Where it applies | main video and photo clips, video and photo overlays |
| Alpha is premultiplied after the key | so opacity, blend and mask (later steps) never see a dark halo |
| Missing file | nothing to key (the clip isn't drawn or plays black, as P4-01b and P1-04) |

**Reference renders** (CLAUDE.md rule 5) in `docs/qa/references/P4-04-green-screen/`, 1080p frames at 1.0 s, on the debug clip **`chroma-check`** (Requests 7): 4 s, 1080p30, a flat `#00B140` backdrop with a horizontal brightness gradient of ±20% and per-frame noise of σ = 4/255 (a badly lit screen), and a subject made of a skin-tone ellipse (`#E0A98C`), a white rectangle (a shirt), 30 one-pixel anti-aliased dark strands (hair) and a green rim on its edge (G + 12/255, the spill). Each is shown over the same neutral test picture (a 12 × 12 grid) so holes and fringes read.

| File | Settings | The frame must show |
| --- | --- | --- |
| `default-key` | defaults | Backdrop gone, subject whole, strands present, no green rim: the Done when |
| `strength-0` | strength 0% | Most of the noisy backdrop still there as a speckle (the exact color only) |
| `strength-100` | strength 100% | Backdrop gone; the skin tone and white intact; the green rim gone |
| `softness-0` | softness 0% | A hard edge, no blend |
| `softness-100` | softness 100% | A wide soft edge; the subject's outline visibly thinner at the strands |
| `blue-key` | Blue swatch, on the blue version of the clip | The same result as `default-key` |
| `picked-off-green` | picked from a `#6BB536` backdrop version | Backdrop gone with the picked color |
| `main-over-image` | main clip keyed, canvas Image background | The subject over the picture |
| `overlay-over-video` | keyed overlay at 50% over a main clip | The subject over the video; the backdrop shows the video |
| `photo-overlay` | a photo overlay with the same subject | Same key on a still |
| `key-plus-mask` | key, then a circle mask (P4-03b) | The mask cuts the keyed subject (pipeline order) |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tool on the bar | `green_screen` icon and label, normal look | `tool_green_screen` |
| Panel opens on an unkeyed item | Keying on at the defaults (one step); the result on the stage; the swatch row with Green selected | `green_screen_title` |
| Panel opens on a keyed item | Saved values; no step | none |
| Nothing found at this moment | After 600 ms on the open panel, or after a color change that leaves under 1% of the visible frame keyed, a status banner for 4 s: nothing changes in the controls | `green_screen_nothing` |
| Slider dragged | The matte follows; readout moves; one step on release | `green_screen_strength`, `green_screen_softness` |
| Green or Blue chosen | Ring moves; the key changes; one step | `key_green`, `key_blue` |
| Pick mode, waiting | Eyedropper ringed; banner; playback paused | `green_screen_pick_hint` |
| Pick mode, finger down | The loupe above the finger | none |
| Pick mode, released outside the clip | Banner changes for 2 s; mode stays | `green_screen_pick_miss` |
| Picked | Third swatch with the sampled color, selected; mode ended; one step | `key_picked` |
| Turn off | Keying removed (one step "Turn off green screen"); the panel closes | `edit_green_screen_off` |
| Undo of the opening step | Keying removed; the panel closes | `editor_undo_toast` |
| Preview catching up | The last frame stays; "Updating the preview" after 1 s (P1-06) | `editor_updating_preview` |
| Item not visible at the playhead | The playhead jumped to its start on open | none |
| Keyed main clip | The canvas background shows through | none |
| Strength 0% or 100%, softness 0% or 100% | Nothing special; the rail ends | none |
| Missing file | The tool is absent (P4-16 order) | `editor_missing_media`, `editor_missing_overlay` |
| Reorder mode | Main bar replaced; the tool isn't reachable | P1-06 |
| Offline, permission | Not applicable (everything is on the phone) | none |
| Long content (5 languages, 200% font) | The tool label wraps (es "Pantalla verde" takes two lines); the panel title row wraps Turn off and Done under it; the heading above the swatches; slider labels wrap beside their readouts; the panel scrolls with the first row in view | none |
| First run | Same as every run; no coach mark. Keying on at open and the stage banner are the teaching | none |

## Interactions
- **Press feedback:** token pressed states, 120 ms; nothing moves. Swatch pressed: the `surface-raised` disc behind it (Swatch).
- **Undo steps** (each is one, edit name "Green screen" unless noted): the panel opening on an unkeyed item; a swatch tap; a pick; a slider release; **Turn off** ("Turn off green screen"). Undo and redo are ignored while a finger is down, including in pick mode.
- **Haptics:** none (no snaps). The pick's release gives no haptic.
- **Stage taps** play or pause (P1-04) except in pick mode. Stage gestures on a keyed overlay stay live while the panel is open (P4-01b).
- **Keyboard (basic, P1):** Tab reaches the tool, the swatches, the sliders (arrows step by 10%), Turn off and Done; Enter activates; Escape is system back. Pick mode needs a pointer (the presets are the keyboard route).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens or closes | 200 ms slide (ToolPanel) | 120 ms fade (instant on Android) |
| The matte while a slider moves | At once, per frame | same |
| Swatch ring, swatch appearing | 120 ms fade | same |
| Loupe, banner | 120 ms fade | same |
| Keying on or off | At once; the preview keeps its last frame until the engine is ready | same |

## Copy
English source; drafts for id, es, pt and hi as in P4-01b; the engineer machine-drafts the rest until P5. Terms follow the shipped strings (background = *latar*, *fondo*, *fundo*, *बैकग्राउंड*). Budgets are characters with placeholders expanded.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_green_screen` | Green screen | 16 | Layar hijau · Pantalla verde · Tela verde · ग्रीन स्क्रीन | Tool label and panel title (`green_screen_title` is the same string). The tool also removes blue or any picked color; the name is the one people search for. es "Pantalla" is 8 characters: fits a 64 dp item at 100%. |
| `tool_green_screen_click` | Remove a color from this clip | 40 | Hapus satu warna dari klip ini · Quitar un color de este clip · Remover uma cor deste clipe · इस क्लिप से एक रंग हटाएं | Click label ("double-tap to …"); 30 en. Never replaces the name. |
| `green_screen_color` | Color to remove | 20 | Warna yang dihapus · Color a quitar · Cor a remover · हटाने वाला रंग | Heading beside the swatches; 15 en, id 18. At 100% font it needs about 112 to 126 dp beside 4 × 48; it wraps above when it doesn't fit. |
| `green_screen_strength` | Strength | 16 | Kekuatan · Fuerza · Força · ताकत | Slider label: how much of the color's range is removed |
| `green_screen_softness` | Edge softness | 20 | Kelembutan tepi · Suavidad del borde · Suavidade da borda · किनारे की कोमलता | Slider label: how gradually the edge blends. 13 en, es and pt 18. |
| `green_screen_off` | Turn off | 14 | Matikan · Desactivar · Desativar · बंद करें | Header button (Quiet). Spoken name `green_screen_off_a11y`, "Turn off green screen", which contains the visible words (WCAG 2.5.3). |
| `green_screen_off_a11y` | Turn off green screen | 30 | Matikan layar hijau · Desactivar la pantalla verde · Desativar a tela verde · ग्रीन स्क्रीन बंद करें | Spoken name only |
| `key_green` | Green | 12 | Hijau · Verde · Verde · हरा | Spoken name of the Green swatch (a key color, not the caption palette's Green; separate key so the two can differ) |
| `key_blue` | Blue | 12 | Biru · Azul · Azul · नीला | Spoken name of the Blue swatch |
| `key_picked` | Picked color | 16 | Warna terpilih · Color elegido · Cor escolhida · चुना हुआ रंग | Spoken name of the third swatch |
| `key_pick` | Pick from video | 24 | Pilih dari video · Elegir del video · Escolher do vídeo · वीडियो से चुनें | Spoken name of the eyedropper swatch; click label "Tap a color in your clip" is `key_pick_click` below |
| `key_pick_click` | Tap a color in your clip | 40 | Ketuk warna di klipmu · Toca un color de tu clip · Toque numa cor do seu clipe · अपनी क्लिप का कोई रंग छुएं | Click label of the eyedropper |
| `green_screen_pick_hint` | Tap the color you want to remove. | 50 | Ketuk warna yang ingin dihapus. · Toca el color que quieres quitar. · Toque na cor que quer remover. · जो रंग हटाना है उस पर टैप करें। | Banner while the mode lasts; 33 en |
| `green_screen_pick_miss` | Tap inside the clip to pick a color. | 50 | Ketuk di dalam klip untuk memilih warna. · Toca dentro del clip para elegir un color. · Toque dentro do clipe para escolher uma cor. · रंग चुनने के लिए क्लिप के अंदर टैप करें। | Banner for 2 s after a miss; 36 en, es 45, pt 46 |
| `green_screen_nothing` | Nothing to remove here. Pick the color you want gone. | 70 | Tidak ada yang dihapus di sini. Pilih warna yang ingin dihilangkan. · No hay nada que quitar aquí. Elige el color que quieres quitar. · Nada a remover aqui. Escolha a cor que quer tirar. · यहाँ हटाने को कुछ नहीं है। जो रंग हटाना है उसे चुनें। | Status banner 4 s; 52 en, es 63, id 66 |
| `a11y_green_screen_picked` | Color picked | 24 | Warna dipilih · Color elegido · Cor escolhida · रंग चुना गया | Polite announcement after a pick |
| `a11y_state_keyed` | Green screen | 16 | Layar hijau · Pantalla verde · Tela verde · ग्रीन स्क्रीन | Appended to the clip's spoken description when keyed (same words as the tool; reuse `tool_green_screen`) |
| `edit_green_screen` | Green screen | 24 | Layar hijau · Pantalla verde · Tela verde · ग्रीन स्क्रीन | Edit name after "Undo: " (reuse `tool_green_screen`) |
| `edit_green_screen_off` | Turn off green screen | 24 | Matikan layar hijau · Desactivar pantalla verde · Desativar tela verde · ग्रीन स्क्रीन बंद | Edit name; id 21, es 27 (trim "la" as drafted) |

Reused: `panel_done`, `editor_undo_toast`, `editor_updating_preview`, `editor_missing_media`, `editor_missing_overlay`.

Translator notes:
- **Green screen** is the studio backdrop that gets removed from a video so another picture shows through. Use the term your language's video editors use ("chroma key" is fine where that is the word people say; keep the same word on the tool and the panel).
- **Strength** is how much of the color (and colors close to it) is removed. **Edge softness** is how soft the border of the kept subject is: not blur, a gradual blend.
- **Pick from video** means "pick a color from the clip you see", not from a file.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

## Accessibility
- **Focus and TalkBack order** (panel): title (focus lands here), Turn off, Done, then the swatches (a single-choice group "Color to remove": "Green, selected, 2 of 4"), Strength, Edge softness. The panel covers the timeline and tool bar (`coveredByModal`); closing returns focus to the Green screen tool.
- **Sliders:** the Slider rules; value announced politely after a change ("Strength, 60 percent"); steps of 10% land on the grid.
- **Picking without sight, without a drag:** the tap needs no drag (SC 2.5.1, 2.5.7). Finding a color is a visual task; the **Green and Blue presets plus Strength** are the route for a screen-reader user, and the Picked swatch is announced "Picked color". The eyedropper swatch's click label says what a tap does ("Tap a color in your clip"). In pick mode the banner is announced when it appears.
- **The clip's spoken description** (P1-05 `a11y_clip_media`, P4-16 appends) gains "Green screen" after "Cropped", "Rotated", "Flipped" and "Moved or resized", in that order. Never in the state description.
- **Touch targets:** swatches 48 dp squares (32 dp circle in a `touch-target`); sliders' rail row 48 dp; header buttons 48 dp; the loupe is output only.
- **Contrast:** swatch ring white on `surface` 17.2:1; the eyedropper icon `text-secondary` on `surface-raised` 5.7:1; readouts `text-secondary` on `surface` 5.7:1; the banner `text` on `surface-raised` 12.4:1; the loupe's white ring plus `on-primary` edge 4.3:1 or more over any picture; slider as the README. The key colors are content (no contrast rule; Green `#00B140` is 6.0:1 and Blue `#0047BB` 2.3:1 against `surface`: the Blue swatch is selected by its ring and named, never by hue alone).
- **200% font:** the title row wraps Turn off and Done under the title; the heading wraps above the swatches; slider labels wrap beside their readouts; the panel scrolls; the tool label takes up to three lines and the bar grows (ToolBar rule). Hindi at 200% is a QA screenshot.
- **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never for opening the panel without a step on a keyed clip; never for a miss or an ended pick mode). New ids for the PM to confirm (they are the edit names in the undo history):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Keying turns on (the panel opening on an unkeyed item), a swatch tap, a pick, a slider release | `tool: "green_screen"` |
| `tool_use` | Turn off (an Undo fires nothing) | `tool: "green_screen_off"` |

- The key color is **not** sent (content). `value` is not used. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `chroma-check` (Requests 7) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-04/`.
1. **The bars:** an overlay selected (Green screen fourth, whole at 360); a main video clip (Green screen after Filters, one swipe away); Spanish at 200% ("Pantalla verde" wrapped).
2. **The panel, opening on an unkeyed overlay:** keyed at defaults over a main video; the three rows at 360 × 640 (Edge softness scrolled), 360 × 800 and 412 × 915.
3. **Strength and softness at their ends** over the 12 × 12 grid: each of the reference frames next to its editor screenshot.
4. **Pick mode:** the banner; the loupe above a finger on a slightly off-green backdrop (screen recording); the Picked swatch after; a miss outside the clip.
5. **A keyed main clip** over a canvas Image background; over a Blur background.
6. **Nothing found:** a clip with no green, the status banner; Hindi at 200% font.
7. **Turn off and Undo:** the panel closes both times; the stage returns to the unkeyed picture.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Default key on the fixture** `chroma-check` at the defaults, in preview and in a 1080p30 export, frame at 1.0 s, **measured by script on the export**: (a) at least 99.5% of backdrop pixels more than 3 px from the subject have alpha 2/255 or less; (b) at least 99% of pixels 3 px inside the subject's shapes have alpha 253/255 or more; (c) in the 3 px band outside the subject, the mean of G − max(R, B) is 6/255 or less (the fixture's rim is 12/255 before spill removal); (d) at least 99% of strand pixels' centers have alpha 128/255 or more.
- **Preview = export:** the same frame compared (preview screenshot cropped to the frame, scaled to the export width): alpha within 3/255 for 99% of pixels, the subject's outline within 1% of the width.
- **A real clip:** the owner's real green screen clip, at the end-of-build phone check, keys with no setting changed or with Strength or Softness alone (the owner's list; recommendation in the PM's kickoff). "Cleanly" for the real clip is the owner's eye on the four reference-style frames; it is not a number.
- **Preview speed:** a keyed 1080p overlay over a 1080p main video, preview at 30 fps or more on the owner's phone (Media3 logs as in P1-04); a Strength drag for 3 s three times, janky frames under 5%.
- **Reference renders:** each file in the table above, from the first passing build, pinned (CLAUDE.md rule 5; iOS parity).
- **Each edit is one undo step:** the opening step, a swatch, a pick, a slider release, Turn off: Undo once each; exactly that edit goes.
- **Old drafts open:** a draft saved before P4-04 has no key and looks as before.
- **Leaks:** LeakCanary: open the panel, pick, drag both sliders, Turn off, close the editor; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| The key | `MediaClip.chromaKey: ChromaKey? = null` with `colorArgb: Int`, `strength: Float` (0 to 1), `softness: Float` (0 to 1); null means off. Applies to main and overlay clips alike |
| Declarative | A `ChromaKeySpec` in `:core:model` (id, ranges, defaults, the formulas as constants) with the reference renders (the `media-effect` skill, rule 5) |
| Edits | `setClipChromaKey(clipId, key?)`, pure, one `commit` per step, the open step included |
| Version | `schemaVersion` bump with an identity migration (the new field defaults to null); one bump with the P4-07 `edit` fields if the engineer adds them together (the kickoff proposal's "one bump per pair") |
| Rules | Split and Duplicate copy the key; **Replace keeps it** (a new take shot on the same screen is the common reason to replace); Crop, turn and mirror don't touch it; trims and moves don't |

## Requests to the principal mobile engineer
1. **Shader** (`media-effect` skill): one fragment pass at source resolution, before any scale (pipeline position above); the formulas as constants in the spec; verify the current Media3 `GlShaderProgram` / `BaseGlShaderProgram` API at the start. iOS gets a marked stub that reports unsupported, never a silent no-op.
2. **Preview and export from one plan,** like every effect; alpha premultiplied after the key.
3. **The pick:** a use case returns the 9 × 9 average of the clip's source frame at the playhead through the clip's inverse transform (the engine reads the frame it already decodes); the loupe is drawn from that value live while the finger moves (a read per move at most every 50 ms).
4. **"Nothing found":** the engine reports the share of the visible frame with alpha under 0.5 at the playhead (one cheap pass or a downsampled read); the UI shows the banner under 1%.
5. **Panel and tool:** the new tool in the four bars (table above), `Swatch` variants Eyedropper and key colors, the panel with Turn off in the header, `coveredByModal` on the covered region, the playhead-jump rule.
6. **Icons:** `green_screen` (`MemixIcons.GreenScreen`, `ic_green_screen.xml`) and `eyedropper` (`MemixIcons.Eyedropper`, `ic_eyedropper.xml`); DESIGN_SYSTEM → Iconography is updated here.
7. **Debug clip `chroma-check`** (generated, no third-party footage, original) and a debug draft holding the reference scenes; frames saved to `docs/qa/references/P4-04-green-screen/`.
8. **Constants:** one commonMain place (a `ChromaKeyCatalog` next to `OverlayCatalog`) with a comment pointing to this spec and `design/tokens.json` → `chromaKey`; never through `MemixTheme`.
9. **Analytics:** two `EditorTool` values. **Strings** in the five files.

## Tokens and icons
- **New content constants** in `design/tokens.json` and `design/system/tokens.json` → `chromaKey` (presets, defaults, ranges, the sampling window, the formula constants). Kotlin: `ChromaKeyCatalog` (commonMain next to `OverlayCatalog`). Not in `MemixTheme`: user content.
- **No new color, size, radius or motion token.** The loupe is a `swatch-size` circle with the white selection ring; its offset is `touch-target` + `space-2`.
- **Two new icons** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review):
  - `green_screen` (Kotlin `MemixIcons.GreenScreen`): a stroked 20 × 16 px rectangle (2 px stroke, 1 px corners) at x 2 to 22, y 4 to 20, holding a **solid bust** (the core): a 5 px head circle and a 10 × 4 px shoulders half-ellipse, 2 px apart, centered, with 1.4 px or more of air to the frame on every side. It must not read as `cutout` (no frame; the shoulders there are stroked), `overlay` (a solid rectangle in a frame) or `photo` (a solid hill).
  - `eyedropper` (Kotlin `MemixIcons.Eyedropper`): a stroked diagonal tube (2 px, about 11 px long) running from a 2 px nozzle at the lower left to a **solid 6 px bulb** (the core) at the upper right, 2 px clear of the tube's end. The core is at the opposite end from `draw`'s tip, so the two pencil-like shapes don't merge.
- **Swatch README** gains the Eyedropper and key-color variants (edited with this spec). **ToolBar README** gains the tool in the four bars.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Does opening Green screen turn keying on at once?**
- *Problem:* a tool that opens "off" and needs a switch costs a tap before anything happens; one that turns on at once changes the picture the moment it opens.
- *Considerations:* **A. On at open, at the defaults** (recommended; applied): a standard green clip is clean after one tap and nothing else to touch; Turn off and Undo are one tap away; cost: a user who opens the tool on a clip with no green sees a status line, not an empty panel. **B. A switch at the top of the panel:** one more tap and 48 dp of the 133 dp budget, which pushes Strength off the screen at 360 × 640.
- *Reverse:* a behavior change only; no migration.

**2. Can the user's main clip be keyed, or only overlays?**
- *Problem:* keying a main clip lets the user put themselves on a picture (the Canvas Image background), but it adds a path where the canvas background shows through.
- *Considerations:* **A. Both** (recommended; applied): the second common job ("put me somewhere else") works with no extra tool; the background rules already exist (P1-11, P2-12). **B. Overlays only:** simpler, but the user has to add their clip as an overlay over a blank main clip, a trick nobody finds.
- *Reverse:* hide the tool on main clips; saved keys on main clips would be ignored.

**3. A matte view (show the cut-out as black and white) for tuning?**
- *Problem:* pros judge a key on a black-and-white matte; a phone user judges it on the picture.
- *Considerations:* **A. No in v1** (recommended; applied): three controls and the real result are enough for memes; **B. A "Show matte" toggle while the panel is open:** one more row (48 dp) and a second engine view; Later, no ticket.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The common mobile chroma key flow is: pick the color with an eyedropper on the clip, raise Intensity until the background goes, nudge Shadow for green glow at the edges; the app's help says the background should be clear and evenly lit | Guidance (vendor help, plus third-party tutorials whose menu names vary by version) | [CapCut help: Chroma key](https://www.capcut.com/help/chroma-key-feature) |
| CapCut's three controls are a color picker, Intensity and Shadow; Memix's PRD names them color, strength and edge softness, with spill handled by a fixed rule | Guidance | same, [CapCut: chroma key green screen guideline](https://www.capcut.com/resource/chroma-key-green-screen) |
| Comparing only chroma (Cb, Cr) and ignoring luma is the standard way to key a screen that is unevenly lit | Assumption (product knowledge of chroma keyers; confirm with the fixture) | none |
| Dragging and pointer-only controls need a single-pointer route; the presets and sliders are it | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures), [SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| A control's accessible name includes its visible label | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Non-text marks need 3:1; color can't be the only cue | Guidance | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Media3 offers custom GL shader programs for per-frame effects | Guidance (API docs; verify at the start of the build) | [Transformer: custom effects](https://developer.android.com/media/media3/transformer/transformations) |
