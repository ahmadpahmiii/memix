# Video effects: the list of 30, the Effects sheet, effect items on the timeline · P4-06 (06a and 06b)

Sixth Phase 4 spec (rewritten 17 Nov after a lost session). It reuses, and does not repeat:
- `P1-05-timeline.md`: the Effects lane (lane 8, `track-effect`, 32 dp row, 28 dp item, label = the effect's name), the past-the-end wash, snapping, the value bubble, TalkBack nodes.
- `P1-06-clip-edits-and-audio.md` and `P4-01b-overlay-layers.md`: the clip bar pattern, landing, lane rule ("first free lane below"), the pinned "Add a meme sound" row, the "panel opens, playhead jumps to the item" rule.
- `P2-14-sticker-picker.md`: the Sheet with a 3-column grid (`grid-tile-min`). `P4-07-video-filters-addendum.md`: tiles made from the user's frame at the playhead, made on demand, cached for the visit.
- `P4-04-green-screen.md`: the clip pipeline (effects come after all of it, below). `P4-02b-keyframes.md`: easing curves (the envelopes below reuse its Bézier forms).
- Component READMEs: Sheet, Tabs, ToolPanel, Slider, ToolBar, Timeline, Toast.

## Job
"Make this moment hit": a zoom punch on the punchline, a shake on the jump scare, a glitch, a flash, a deep-fry. PRD → Video editor → Effects: about 30, placed on the timeline with intensity. Also the photo-to-video job (PRD jobs table: photo + zoom punch + sound). **One main action: none.** Export stays the one blue action (P1-12); the sheet has no primary button (a tile tap is the action).

**Done when** (TICKETS 06a and 06b): each effect matches its reference render; preview stays at 30 fps or more. Both made checkable in Measured. P4-14 (Sound + zoom) places one `zoom_punch` item with its defaults; nothing else here depends on it.

**Split (decided here, for the PM).**

| Part | Contains | Why |
| --- | --- | --- |
| **P4-06a** | The framework: Effects lane items (model, landing, lane rule, trim, move, split, duplicate), the Effects tool and sheet (tabs that exist), the effect bar, the Intensity panel, the stacking rule, analytics, the reference harness (`effects-check`); the **15 effects of Motion, Light and Color** | Single-pass, no neighbor sampling: cheap shaders. Zoom punch is in 06a, so P4-14 is unblocked |
| **P4-06b** | The **15 effects of Glitch, Blur and Retro** (Tabs added to the sheet) | Multi-sample shaders (blurs, distortions, noise): the 30 fps risk is here |

Until 06b ships the sheet shows only the tabs that exist; the final tab order is Motion · Glitch · Light · Color · Blur · Retro, and a tab sits one place to the left until the tab before it is built (the ToolBar rule). Cut list: if time runs out 06b can drop effects one by one; ids are saved forever, so never rename or reuse one.

**Fast path, with tap counts.**
- **Zoom punch at the punchline:** scrub to it, Effects (1), **Zoom punch** (2). The sheet closes, the item lands selected on the effect lane. Two taps, no setting.
- **Weaker or stronger:** Intensity (3), drag (4). **Longer or shorter:** drag the item's end handle.
- **Change my mind:** Change effect (1), another tile (2): same place, length and intensity.
- **Off again:** Delete, or Undo.
- **No dragging at all:** the sheet, the Intensity panel (keyboard steps of 10%), Move here and Trim actions do everything (Accessibility).

**Principles check.** Content first: tiles show the user's own frame; effects are timeline items, no permanent chrome. Sound first: the pinned "Add a meme sound" row is untouched (the Effects lane scrolls into view when an item lands, never pushes the pill). Fast path: two taps for the headline effect (Motion is the first tab, zoom punch the first tile). One-handed: the tool, tabs and tiles are in the lower two thirds. Forgiving: every change one undo step; a short Intensity range never hides a way back. Accessible: no effect flashes more than three times a second (Photosensitivity). Five languages: names measured at 200%.

**Color roles:** the violet `track-effect` item and nothing else new; no blue except the Intensity slider's `primary` fill; selection white. No new color.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Effects sheet** (Sheet, opens at its content height up to 70% of the window) | The **Effects** tool of the main bar (nothing selected); the **Change effect** tool of a selected effect item (title "Change effect", replace mode) | A tile tap (lands or replaces), close, scrim, drag down, back, Escape (nothing changes) |
| **Intensity panel** (ToolPanel) | The **Intensity** tool of a selected effect item | Done, system back, Escape. The item stays selected |

- **Not shown in an empty project** (as Overlay). Arrives only from the main bar: with an item selected the bar shows that item's tools, so the user taps empty timeline first (accepted: effects are placed at a time, so the user is already scrubbing; Question 3).
- **Kept:** the effect items in the project (autosave, undo). Not kept: the open tab beyond the visit, the thumbnails.
- Back order: a sheet or panel closes, then the effect bar closes (selection clears), then the editor.

## Layout
Board: `VideoEditor.dc.html` shows only the lane item "Zoom punch" (32 dp row, 92 dp wide at 40 dp/s). Everything else is new.

### The tool and the bars
- **Main bar:** **Effects** (icon `effects`, label "Effects", `tool_effects`) after Stickers, before Filters (final order set in P4-07: Edit · Meme sounds · Text · Add media · Overlay · Voiceover · Stickers · **Effects** · Filters · Remove BG · Speed). Click label "Add an effect at the playhead". Not shown in an empty project.
- **Effect item bar** (clip mode: Close, divider, then): **Intensity · Delete · Change effect · Split · Move here · Duplicate**. Delete third so "Intensity, Delete" show whole at 360 dp. Icons: Intensity reuses `adjust`, Change effect reuses `replace`, the rest as P1-06. No Keyframes (effect parameters don't animate in v1: Later, no ticket), no Volume, no Position. An effect whose item is past the end of the video still has all tools.

### The Effects sheet (Sheet)
Standard Sheet (grabber, close, `surface`, `radius-lg`, `shadow-sheet`, `scrim`). Title "Effects" (`title`, heading; "Change effect" in replace mode). Under it **Tabs** (the Tabs component, as P3-09: selected `text` with the white underline, scrolling row): Motion · Glitch · Light · Color · Blur · Retro. Opens on **Motion**, then remembers the last tab for the visit.

- **The grid:** per tab, the tab's effects in a lazy grid: **3 columns at 360 dp** (`grid-tile-min`, `space-2` gaps, `space-4` side padding). A tile = a square picture (`radius-md`, **the user's frame at the playhead with the effect at its peak**, 100% intensity, made on demand, cached for the visit; the frame of the clip under the playhead or its first frame as P4-07; a not-yet-made tile is a plain `surface-raised` square, static, no spinner) over the name in `caption` `text` (`space-1` below, up to two lines). Pressed: `hairline` fill behind, 120 ms. **The tile is the whole touch target** (104 dp wide at 360).
- **Replace mode:** the tile of the item's current effect gets the white ring (`stroke-selection`) and a tap on it does nothing.
- **Time-based effects' tiles** show the instant where the effect is most visible ("Ref time" below), so a still tile means something.
- **No search, no favorites, no recents** (30 items, six tabs).
- **Budget** (window 640 / 800 / 915; sheet up to 70% = 448 / 560 / 640 dp; the sheet's frame about 110 dp, Tabs 48, bottom padding 24 → **266 / 378 / 458 dp** for the grid): a tile row = 104 square + `space-1` + 16 (a one-line name; 32 if two) = **124 to 140 dp** plus a `space-2` gap. A tab holds five tiles, **two rows = 264 to 296 dp**: both rows show whole at 360 × 800 and 412 × 915; at 360 × 640 the second row's names are cut by 0 to 30 dp when a name takes two lines, and the grid scrolls. Accepted: the last row's picture is always whole.
- At 200% font names take up to three lines, the grid scrolls on every phone, tabs size to their text and scroll sideways. **Landing from the sheet is never blocked by thumbnails**: a tile whose picture isn't made still works.
- The sheet **closes on a tile tap** (200 ms), as the Meme sounds sheet does when a sound is added, so the user sees the item land.

### The effect on the timeline
- **Lane:** the existing Effects lane (lane 8, 32 dp row, item 28 dp, `track-effect`, label `caption` in `on-track` 6.5:1, sticky at the item's left, ellipsis). Overlapping items go on their own lanes in model order (the P1-06 rule: first lane free for the item's whole span, else a new one). **Lane order is application order** (first lane first), see Rules.
- **No picture on the item** (unlike clips): a label only; a 0.3 s item (12 dp at 40 dp/s) is still drawn at least `playhead-width` wide and unlabeled (P1-05).
- **Selected:** white outline and handles as every item; **past the end of the video:** the `scrim` wash (the part past the end doesn't play or export).
- **Landing** (the effect's default length, below): at the playhead, **selected**, a bonk (`duration-bonk`), the lane slides in over 200 ms and the lanes scroll to show it (P4-01b); TalkBack hears "Effect added". No haptic (Confirm belongs to sounds). If less than 0.5 s of video remains, it lands **ending at the end** with the smaller of its default length and the video; a playhead past the end lands the same. The default **intensity** is the effect's own (table). One undo step "Add effect".
- **Trim** (handles, min 0.1 s), **move** (drag; Move here), **Split** (both halves keep effect and intensity), **Duplicate** (the copy lands right after the original on its lane, or the first free lane): the P1-05 and P1-06 rules, nothing new.
- **Trim and the envelope:** an effect's envelope (Rules) is measured from the item's own start and end, so a shorter item plays the same shape faster; nothing is cut off.

### The Intensity panel (ToolPanel)
Title row: the **effect's name** (`title`, heading), **Done** (Ghost). Content: **Intensity**, Slider 0 to 100%, step 10%, default **the effect's default** (table), readout `70%`, double-tap the thumb resets to the effect's default. Live on the stage while dragging; playback keeps running if it was running; one undo step on release ("Intensity"). If the playhead is outside the item's span, it jumps to the item's start when the panel opens (P4-01b). Keyboard and TalkBack steps of 10% land on the grid. **No snap.** 0% is allowed (the item stays, does nothing; useful to disable without deleting).
- **Budget:** 68 dp of the 133 / 206 / 252 dp content: fits everywhere. At 200% the label wraps beside the readout; no scroll needed.
- **Hint** under it (`label`, `text-secondary`): the effect's one-line description (`fx_desc_<id>`, budgets in Copy), because the picture is on the stage but "what is Tear" needs a word: at 100% 16 dp (one line) to 32 dp (two): 68 + 16 + 32 = **116 dp**, fits at 133. At 200% the panel scrolls at 360 × 640.

## Rules behind the layer

### Where effects sit (preview and export match)
- **Scope:** an effect item applies to **the whole composed frame at that time: main video, overlays, captions and stickers**, never the watermark (export only, above everything). It is the last step of the frame, after the clip pipeline (P4-04 order: … framing, opacity, blend), so a Deep-fry on a captioned frame fries the caption, as the meme expects. (Question 2.)
- **Stacking:** items that overlap in time all apply, **one pass each, in lane order from the first lane to the last** (first lane first). The user never orders them; moving an item to another lane (drag) reorders. **At most 3 items may overlap at any moment** (`effects.maxOverlap`): a fourth explains with `explain_effect_max` and doesn't land (30 fps budget). Move, trim and duplicate that would make a fourth overlap are refused the same way, with the item back where it was.
- **Envelope:** every effect has one of four fixed shapes over the item's own span (length *L*, time *τ* since its start): **Steady** (ramps in over min(80 ms, *L*/4), full, ramps out the same: no pop at the item's ends), **Punch** (attack: 0 to full over 80 ms, Bézier (0, 0, 0.58, 1); holds to 70% of *L*; then back to 0 by *L*, Bézier (0.42, 0, 0.58, 1)), **Decay** (full at *τ* = 0, then exponential: `e^(−4τ/L)`, about 2% at *L*), **Ramp** (0 to full by *L*, Bézier (0.42, 0, 0.58, 1); the effect ends with a jump at the item's end, which is what a push-in cut wants). The strength *s* in the table is *intensity × envelope*.
- **Intensity 0** is exactly "no effect": the frame is bit-identical to a render with the item deleted (checked, Measured).
- **Deterministic:** every random-looking effect (glitch bursts, static, grain, shake) draws from a hash of **the frame's presentation time in microseconds rounded to 1/30 s and a per-effect constant**, never a live random number. So the preview, a seek, and the export show the same frame at the same time.
- **Frame rate and size:** the effects work in the output size's pixels with parameters as shares of the shorter side, so a 720p preview and a 1080p export look the same, scaled. Preview may drop to a lower resolution rather than below 30 fps (the Blur precedent, P1-11 and P4-07); no message; export is always full quality.
- **Photosensitivity (SC 2.3.1, Guidance).** No effect makes the whole frame, or a large part of it, flash more than **three times in any one second**: Flash is one burst per item (Decay), Light pulse runs at 2 Hz, Glitch bursts are at most 3 per second, nothing else has a brightness cycle. Two Flash items placed closer than 0.33 s are the user's choice and the spec can't police them; the Effects tab hints nothing. QA checks the ceiling with a frame-by-frame luminance plot of the references (Measured).
- **Originality.** Names and looks are authored for Memix from the formulas below, not taken from another app's preset list; the engineer implements the formulas as written. The PRD's own words (zoom punch, shake, glitch, flash, RGB split, VHS, blur, deep-fry) are generic effect types.

### The list (ids saved forever)
Defaults: length (ms) and intensity (%). *Ref time* = the instant in the item the reference render and the tile picture use. Parameters are the value at **intensity 100%**, "short" = the output's shorter side, "w" = width, "h" = height; each scales with intensity as *s*, linearly unless said.

**Motion** (06a; geometry of the whole frame; every effect that moves the frame scales it up just enough that no canvas edge shows, called *cover*)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `zoom_punch` | Zoom punch | Punch | 600 · 70 | Scale 1 to **1.5**, centered on the frame, fast in, eased out. Edges leave the canvas | 80 ms |
| `slow_push` | Slow push | Ramp | 3000 · 60 | Scale 1 to **1.3** by the item's end | end |
| `shake` | Shake | Steady | 1000 · 60 | Offset x and y = 2.5% of short × (sin 2π·13τ + 0.5 sin 2π·29τ + 1)/1.5 and (sin 2π·11τ + 0.5 sin 2π·31τ + 2)/1.5; rotation ±1.2° at 17 Hz; cover | 120 ms |
| `wobble` | Wobble | Steady | 2000 · 60 | Rotation 6° × sin 2π·1.2τ about the center; cover | 208 ms |
| `bounce` | Bounce | Steady | 2000 · 60 | Offset up 6% of short × \|sin 2π·2τ\|; cover | 125 ms |

**Glitch** (06b)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `glitch` | Glitch | Steady | 1000 · 70 | **Bursts**: up to 3 per second at hashed times, each 90 ms: 6 to 10 horizontal slices 2 to 9% of h tall displaced sideways by up to 8% of w, red and blue channels offset 1% of w | a burst's middle |
| `rgb_split` | RGB split | Steady | 1500 · 60 | Red shifted left and blue right by 1.5% of w; green stays | any |
| `tear` | Tear | Steady | 2000 · 60 | A band 12% of h tall sweeps top to bottom every 1.2 s; inside it pixels shift sideways by 5% of w × sin of their height in the band | band at 50% height |
| `pixelate` | Pixelate | Steady | 1000 · 50 | Mosaic with blocks of 1% + 9% × *s* of short (min 1% = off) | any |
| `static` | Static | Steady | 1500 · 50 | Additive luma noise, 60% × *s*, new per frame, grain size 1.5 px at 1080p | any |

**Light** (06a)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `flash` | Flash | Decay | 250 · 80 | White over the frame at 90% × *s*, fading out. **One flash per item** | 0 ms |
| `light_pulse` | Light pulse | Steady | 2000 · 50 | Brightness × (1 + 0.5 × *s* × (0.5 − 0.5 cos 2π·2τ)): a swell twice a second | 250 ms |
| `glow` | Glow | Steady | 2000 · 60 | Pixels above 60% luma blurred at 2.5% of short and **screen**-added at 80% × *s* | any |
| `spotlight` | Spotlight | Steady | 2000 · 70 | Darkens outside a soft circle centered on the frame: radius 0.9 down to 0.35 of the half-diagonal, outside multiplied by 1 − 0.85 × *s*, edge 20% of the radius | any |
| `sun_leak` | Sun leak | Steady | 2000 · 60 | A soft warm glow (`#FF9A3C`, radius 45% of short) screen-added at 45% × *s*, drifting along the top edge left to right at 0.15 cycles a second | 0 ms |

**Color** (06a)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `deep_fry` | Deep-fry | Steady | 2000 · 70 | **The photo editor's Deep-fry** (P2-04's formula and constants in `photo.imageTools`, the amount = *s*): same look as the photo tool, never a copy | any |
| `mono` | Mono | Steady | 2000 · 100 | Saturation × (1 − *s*), Rec. 709 luma; contrast +10% × *s* | any |
| `negative` | Negative | Steady | 1000 · 100 | mix(color, 1 − color, *s*) per channel | any |
| `duotone` | Duotone | Steady | 2000 · 70 | Luma mapped from shadows `#241B4B` to highlights `#F6B73C`, mixed with the picture by *s* | any |
| `poster` | Poster | Steady | 2000 · 60 | Each channel reduced to *n* = round(16 − 13 × *s*) levels (16 to 3) | any |

**Blur** (06b)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `blur` | Blur | Steady | 2000 · 60 | Gaussian, σ = 2.5% of short × *s* (27 px at 1080p) | any |
| `radial_blur` | Radial blur | Steady | 1000 · 70 | Zoom blur from the center: samples along the ray to the center, length 12% of the distance × *s*, 16 samples | any |
| `smear` | Smear | Steady | 1000 · 60 | Horizontal motion blur, length 8% of w × *s*, 16 samples | any |
| `tilt_blur` | Tilt blur | Steady | 2000 · 70 | A sharp horizontal band 25% of h tall (feather 15%) centered; blur σ = 2% of short × *s* elsewhere | any |
| `soft_focus` | Soft focus | Steady | 2000 · 60 | mix(picture, blur σ = 1.2% of short, 70% × *s*), brightness +10% × *s* | any |

**Retro** (06b; "Retro" in the tab, looks of old screens and film)

| Id | Name | Env | Length · Int. | What it does at 100% | Ref time |
| --- | --- | --- | --- | --- | --- |
| `vhs` | VHS | Steady | 3000 · 70 | Chroma smear sideways 0.6% of w × *s*; scanlines darkening every other 2 px row (scaled to height / 1080) by 12% × *s*; a tracking band 6% of h drifting up 0.25 cycles a second with a 1.5% of w jitter; noise 4% × *s*; softening about 0.5 px | any |
| `old_screen` | Old screen | Steady | 2000 · 60 | 3 px scanline pattern with an RGB stripe mask at 15% × *s*, barrel bend of 4% × *s*, a vignette | any |
| `film_grain` | Film grain | Steady | 3000 · 50 | Luma noise σ = 10% × *s*, grain 1.5 px, new 24 times a second (the 1/30 s hash picks the nearest) | any |
| `fisheye` | Fisheye | Steady | 2000 · 60 | Barrel distortion *k* = 0.45 × *s*, scaled to fill | any |
| `mirror` | Mirror | Steady | 2000 · 100 | The left part mirrored over the right: the fold moves from the right edge (0%) to the center (100%) | any |

**Counts:** 5 + 5 + 5 + 5 + 5 + 5 = **30**; 06a 15 (Motion, Light, Color), 06b 15.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | `effects` icon, label; absent in an empty project | `tool_effects` |
| Sheet open, tab with tiles | The tab's five tiles, pictures of the user's frame | `effects_title`, `fx_tab_*`, `fx_name_<id>` |
| Tile picture not made yet | A plain `surface-raised` square, name below; the tap still works | none |
| Sheet in replace mode | Title "Change effect"; the current effect's tile ringed white | `effects_change_title` |
| Effect lands | Item on the lane, selected, bonk; sheet closed | `a11y_effect_added` |
| Lane scrolled | Lanes scroll to show it; the pinned pill stays | none |
| Effect selected | The effect bar | `tool_intensity`, `tool_effect_change` |
| Intensity panel | Name, slider, one-line description | `fx_desc_<id>`, `effects_intensity` |
| Intensity 0% | Slider at 0; stage as without it | none |
| Item past the end | `scrim` wash on the overhang | none |
| Fourth overlapping item refused | Toast; nothing moves | `explain_effect_max` |
| Too short to trim further (0.1 s) | The handle stops | none |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Preview cannot hold 30 fps | Lower preview resolution, no message | none |
| Undo removes the item | Panel or bar closes | none |
| Missing file | Not applicable (effects have no file) | none |
| Offline, permission | Not applicable (all on the phone) | none |
| Reorder mode | Main bar replaced; effects unreachable | P1-06 |
| Long content (5 languages, 200% font) | Tile names wrap to 3 lines, the grid scrolls, tabs scroll sideways, the Intensity hint wraps, bar labels wrap and the bar grows | none |
| First run | No coach mark; Motion opens, zoom punch is the first tile | none |
| An old draft (before P4-06a) | No effect items; the lane absent | none |

## Interactions
- **Tile tap:** one undo step "Add effect" (replace mode: "Change effect"); the sheet closes; the item is selected. **Long-press a tile:** nothing (no preview-hold in v1: Later).
- **Taps on the stage** play or pause as everywhere, with the item selected, so the user can watch the effect (the panel covers the timeline but not the stage).
- **Drag the item** along its lane (P1-05 move) or **to another lane vertically** (reorders application order, one step "Move effect"). **Drag a handle:** trim; value bubble shows the length.
- **Keyboard (basic, P1):** Tab reaches tool, tabs, tiles; Enter lands one; arrows step Intensity by 10%; Escape is back.
- **Haptics:** none for landing; SegmentTick only at snaps (P1-05 snapping to playhead and neighbors).
- **Undo steps:** "Add effect", "Change effect", "Intensity", "Move effect", "Delete effect", trims and splits as P1-06. A drag on the slider is one step on release.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Sheet opens, closes | 200 ms slide | 120 ms fade (instant on Android) |
| Tab change | 200 ms cross-fade | 120 ms fade |
| Item lands | `duration-bonk`; lane slide 200 ms | no bonk, lane at once |
| Tile picture arrives | 120 ms fade | same |
| Tile pressed | 120 ms | same |
| The effect itself | **Content, not interface motion**: plays with the video | still plays (it is the user's meme) |
| Toasts, status | 120 ms fade | same |

Nothing loops in the chrome (MOTION 2).

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5). Budgets are characters. Names sit under tiles in `caption`: the budget keeps two lines at 100% in a 104 dp tile (about 16 characters a line).

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_effects` | Effects | 12 | Efek · Efectos · Efeitos · इफ़ेक्ट | Main-bar label and sheet title (`effects_title`). Same word the P1 board uses |
| `tool_effects_click` | Add an effect at the playhead | 40 | Tambah efek di playhead · Añadir un efecto en el cabezal · Adicionar um efeito na posição atual · प्लेहेड पर इफ़ेक्ट जोड़ें | Click label; playhead word as the shipped `tool_move_here_click`; 29 en |
| `effects_change_title` | Change effect | 20 | Ganti efek · Cambiar efecto · Trocar efeito · इफ़ेक्ट बदलें | Sheet title and the bar's tool label (`tool_effect_change`, same string) |
| `tool_effect_change_click` | Pick a different effect for this item | 40 | Pilih efek lain untuk item ini · Elige otro efecto para este elemento · Escolha outro efeito para este item · इस आइटम के लिए दूसरा इफ़ेक्ट चुनें | Click label; 36 en |
| `tool_intensity` | Intensity | 12 | Intensitas · Intensidad · Intensidade · तीव्रता | Bar tool; slider label `effects_intensity` is the same string |
| `fx_tab_motion` | Motion | 12 | Gerak · Movimiento · Movimento · मोशन | Tab |
| `fx_tab_glitch` | Glitch | 12 | Glitch · Glitch · Glitch · ग्लिच | Tab |
| `fx_tab_light` | Light | 12 | Cahaya · Luz · Luz · रोशनी | Tab |
| `fx_tab_color` | Color | 12 | Warna · Color · Cor · रंग | Tab |
| `fx_tab_blur` | Blur | 12 | Buram · Desenfoque · Desfoque · ब्लर | Tab |
| `fx_tab_retro` | Retro | 12 | Retro · Retro · Retrô · रेट्रो | Tab |
| `fx_name_zoom_punch` | Zoom punch | 16 | Zoom kilat · Zoom de impacto · Zoom de impacto · ज़ूम पंच | A fast zoom in that settles back |
| `fx_name_slow_push` | Slow push | 16 | Dorong pelan · Empuje lento · Aproximação lenta · धीमा ज़ूम | A slow zoom in |
| `fx_name_shake` | Shake | 16 | Getar · Temblor · Tremor · कंपन | |
| `fx_name_wobble` | Wobble | 16 | Goyang · Vaivén · Balanço · डगमग | A gentle sway |
| `fx_name_bounce` | Bounce | 16 | Pantul · Rebote · Quique · उछाल | The frame hops |
| `fx_name_glitch` | Glitch | 16 | Glitch · Glitch · Glitch · ग्लिच | Keep the loanword where editors use it |
| `fx_name_rgb_split` | RGB split | 16 | Pisah RGB · Canales RGB · Canais RGB · RGB स्प्लिट | Red and blue slide apart |
| `fx_name_tear` | Tear | 16 | Sobek · Rasgado · Rasgo · फटाव | A band of the picture tears sideways |
| `fx_name_pixelate` | Pixelate | 16 | Piksel · Pixelar · Pixelizar · पिक्सल | |
| `fx_name_static` | Static | 16 | Statis · Estática · Chiado · स्टैटिक | TV noise |
| `fx_name_flash` | Flash | 16 | Kilat · Destello · Clarão · फ़्लैश | One white flash |
| `fx_name_light_pulse` | Light pulse | 16 | Denyut cahaya · Pulso de luz · Pulso de luz · रोशनी की धड़कन | |
| `fx_name_glow` | Glow | 16 | Cahaya · Brillo · Brilho · चमक | |
| `fx_name_spotlight` | Spotlight | 16 | Sorotan · Foco · Holofote · स्पॉटलाइट | |
| `fx_name_sun_leak` | Sun leak | 16 | Cahaya bocor · Luz solar · Vazamento de sol · धूप की झलक | Warm light drifting in |
| `fx_name_deep_fry` | Deep-fry | 16 | reuse the photo editor's `edit_photo_tab_deepfry` value | Same word in both editors |
| `fx_name_mono` | Mono | 16 | Mono · Mono · Mono · मोनो | Black and white |
| `fx_name_negative` | Negative | 16 | Negatif · Negativo · Negativo · नेगेटिव | |
| `fx_name_duotone` | Duotone | 16 | Duoton · Duotono · Duotone · ड्यूओटोन | |
| `fx_name_poster` | Poster | 16 | Poster · Póster · Pôster · पोस्टर | Few flat colors |
| `fx_name_blur` | Blur | 16 | Buram · Desenfoque · Desfoque · ब्लर | |
| `fx_name_radial_blur` | Radial blur | 16 | Buram radial · Desenfoque radial · Desfoque radial · रेडियल ब्लर | es and pt take two lines |
| `fx_name_smear` | Smear | 16 | Usap · Estela · Rastro · धुंधली लकीर | A sideways streak |
| `fx_name_tilt_blur` | Tilt blur | 16 | Buram miring · Desenfoque inclinado · Desfoque inclinado · टिल्ट ब्लर | Sharp band, blurred around it |
| `fx_name_soft_focus` | Soft focus | 16 | Fokus lembut · Enfoque suave · Foco suave · सॉफ़्ट फ़ोकस | |
| `fx_name_vhs` | VHS | 16 | VHS · VHS · VHS · VHS | Never translated |
| `fx_name_old_screen` | Old screen | 16 | Layar jadul · Pantalla vieja · Tela antiga · पुरानी स्क्रीन | |
| `fx_name_film_grain` | Film grain | 16 | Butiran film · Grano de película · Granulado de filme · फ़िल्म ग्रेन | es 17: wraps |
| `fx_name_fisheye` | Fisheye | 16 | Mata ikan · Ojo de pez · Olho de peixe · फ़िशआई | |
| `fx_name_mirror` | Mirror | 16 | Cermin · Espejo · Espelho · शीशा | |
| `fx_desc_<id>` (30) | one line each, in the panel | 48 | drafted with the strings | e.g. `fx_desc_zoom_punch` "Zooms in fast, then settles back." (33), `fx_desc_flash` "One quick white flash." (22), `fx_desc_mirror` "Folds the picture onto itself." (30). Pattern: a verb phrase, no "the effect"; 06a writes 15, 06b 15 |
| `explain_effect_max` | Three effects at once is the most. Move one first. | 60 | Tiga efek sekaligus adalah batasnya. Pindahkan satu dulu. · Tres efectos a la vez es el máximo. Mueve uno antes. · Três efeitos ao mesmo tempo é o máximo. Mova um antes. · एक साथ तीन इफ़ेक्ट ही हो सकते हैं। पहले एक को हटाएं। | Toast; 49 en, id 56, es 53 |
| `a11y_effect_added` | Effect added | 24 | Efek ditambahkan · Efecto añadido · Efeito adicionado · इफ़ेक्ट जुड़ा | Polite |
| `a11y_effect_node` | %1$s effect, %2$s to %3$s, intensity %4$d percent | 70 | Efek %1$s, %2$s sampai %3$s, intensitas %4$d persen · Efecto %1$s, de %2$s a %3$s, intensidad %4$d por ciento · Efeito %1$s, de %2$s a %3$s, intensidade %4$d por cento · %1$s इफ़ेक्ट, %2$s से %3$s, तीव्रता %4$d प्रतिशत | Timeline node; times spoken as P1-04; 49 en. Hindi "से" here is "from" (not seconds) |
| `edit_effect_add` | Add effect | 24 | Tambah efek · Añadir efecto · Adicionar efeito · इफ़ेक्ट जोड़ें | Edit name after "Undo: " |
| `edit_effect_change` | Change effect | 24 | as `effects_change_title` | |
| `edit_effect_intensity` | Intensity | 24 | as `tool_intensity` | |
| `edit_effect_move` | Move effect | 24 | Pindahkan efek · Mover efecto · Mover efeito · इफ़ेक्ट खिसकाएं | Lane change |
| `edit_effect_delete` | Delete effect | 24 | Hapus efek · Eliminar efecto · Excluir efeito · इफ़ेक्ट हटाएं | |

Reused unchanged: `panel_done`, `tool_delete`, `tool_split`, `tool_move_here`, `tool_duplicate`, `editor_updating_preview`, the sheet's close label.

Translator notes:
- An **effect** changes how a stretch of the video looks or moves for a moment (zoom, shake, glitch). It is placed on the timeline; **Intensity** is how strong it is. Keep the same word on the tool and the sheet title.
- Names are short labels under pictures: keep the loanword (Glitch, VHS, RGB, Deep-fry) where editors in your language do. Don't add "effect".
- **Hindi:** the interface uses the system Devanagari face; no tile has a fixed text height; "से" in `a11y_effect_node` is "from", not seconds.

## Accessibility
- **Focus and TalkBack order:** the sheet's title takes focus; then the tabs (a tab group), then the tiles in reading order. Each tile is a button "Zoom punch, 1 of 5" (the picture is decorative); closing returns focus to the **new item** on the timeline (the sheet's action moves it somewhere more useful, as the Meme sounds sheet does), announcing `a11y_effect_added`. Cancelling returns it to the Effects tool. The Intensity panel: focus on the title; closing returns it to Intensity.
- **Effect items are timeline nodes** (`a11y_effect_node`) with actions: Change effect, Intensity (opens the panel), Move here, Trim start to playhead, Trim end to playhead, Duplicate, Delete: the no-drag routes (WCAG 2.5.1, 2.5.7).
- **The covered region:** `coveredByModal` on the timeline and tool bar for the Intensity panel and the sheet.
- **Flashing:** see Photosensitivity (SC 2.3.1). Reduce motion does not remove effects (they are the content).
- **Touch targets:** tiles 104 dp; tabs 48; slider row 48; bar tools 48 dp or more; the lane item is 28 dp tall in a 32 dp row, the documented non-video exception, with the item's actions and the bar as equal routes.
- **Contrast:** the item label `on-track` on `track-effect` 6.5:1; the white selection outline on `track-effect` is 2.9:1 on its own, so it is drawn outside with a 1 dp gap (as every item, P1-05); tile names `text` on `surface` 15.6:1; the white ring `selection` on `surface` 17.2:1; slider as its README. **State is never color alone:** the ring is a shape; the current effect also reads "selected" in the spoken name.
- **200% font:** see States.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never for opening the sheet or the panel). New ids for the PM to confirm (the edit names):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A tile lands an effect | `tool: "effect_add"`, `value: "<effect id>"` (for example `zoom_punch`) |
| `tool_use` | Change effect | `tool: "effect_change"`, `value: "<new id>"` |
| `tool_use` | An Intensity release | `tool: "effect_intensity"` |
| `tool_use` | Delete effect | `tool: "effect_delete"` |

- `value` is the effect id (a fixed catalog id, not user content). Moves, trims, splits and duplicates fire the shared item ids. P4-14's one-tap combo fires `effect_add` with `value: "zoom_punch"` plus its own event. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `effects-check` (Requests 5) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-06a/` and `P4-06b/`.
1. **The tool and sheet:** Effects in the main bar; the sheet on Motion at 360 × 640 and 360 × 800 (two rows whole at 800); each tab; replace mode with a ringed tile; Spanish at 200% font (names wrapping, grid scrolling); Hindi at 200%.
2. **A landed item:** the lane slides in, the item selected, the effect bar (Intensity, Delete in view), the pinned "Add a meme sound" row still pinned with six lanes.
3. **The Intensity panel** at three sizes; 0% and 100%; the description line.
4. **The reference renders** (each file next to its editor screenshot): see Measured.
5. **Stacking:** two overlapping items on two lanes, a fourth refused with the toast.
6. **A caption under an effect** (Deep-fry on a captioned frame): the caption is fried too; the watermark (export) is not.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Each effect matches its reference render.** On the debug draft **`effects-check`** (Requests 5): a 9:16, 4 s main clip of a 12 × 12 lettered grid with colour bars along the bottom and a moving white square (so motion shows), a caption "CHECK" and a sticker. For each of the 30 ids, at 100% and at 50% intensity, an item of 2 s (so *τ* is well inside), the frames at the **Ref time** (and, for time-based ones, at 25%, 50% and 75% of the item) are saved to `docs/qa/references/P4-06-effects/` as `<id>-100-<t>.png` and `<id>-50-<t>.png` at 1080 × 1920 from an **export**. The editor preview at the same instants, scaled to the export width, must match: **99% of pixels within 3/255 per channel** (the video encoder's allowance; the references are lossless) and the exported frames within 3/255 of the references. The reference is the first passing build, pinned (CLAUDE.md rule 5; iOS parity).
- **Intensity 0%** equals no item: the exported frame equals the unaffected frame exactly.
- **Stacking order** (`stack-two`): Zoom punch then Deep-fry versus the reverse differ, in lane order; a caption under Deep-fry is fried, the watermark is not.
- **Envelope:** `zoom_punch` scale at 80 ms is 1.35 (default 70% intensity) within 1%, at 600 ms is 1.0.
- **Determinism:** the same frame exported twice, and the preview after a seek to the same time, are identical.
- **Preview at 30 fps or more** on the owner's phone (Media3 logs, release-type build): each of the 30 effects alone at 100% over a 1080p main clip with one overlay; three of the heaviest (Radial blur, VHS, Glow) stacked; janky frames under 5% during a 3 s Intensity drag. A phone that can't hold it must drop preview resolution (checked), never frames.
- **Photosensitivity:** a frame-by-frame mean-luminance plot of `flash`, `light_pulse`, `glitch` and `static` (1080p30 export): no more than three luminance swings of 10% or more of the range in any 1 s window.
- **Each edit is one undo step:** add, change, intensity release, move, delete: Undo once each.
- **Old drafts open;** an export with effects equals the preview; **leaks:** LeakCanary: open the sheet, switch tabs, land, change, Intensity, delete, close the editor; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| The item | `EffectClip(id, startUs, durationUs, effectId: String, intensity: Float)` on a track of kind `EFFECT` (the Phase 1 track kind already exists); lanes as every track kind |
| The specs | `EffectSpec(id, tab, envelope, defaultDurationUs, defaultIntensity, params…)` in `:core:model` for each of the 30 (rule 5), the formulas above as constants; the `media-effect` skill steps (registry, shader, reference render, UI listing) |
| Application | One pure function `effectStrength(item, timeUs)` (envelope) and a per-frame list of active items in lane order; one GL pass per active item |
| Edits | `addEffect`, `changeEffect`, `setEffectIntensity` (+ the shared move, trim, split, duplicate, delete), pure, one `commit` each; the 3-overlap rule in the edit |
| Version | One `schemaVersion` bump in 06a with an identity migration (the 30 ids are data, so 06b adds none); old-draft check |
| Photos | Effects apply to every frame of the composition, so photo clips and photo-only projects work |

## Requests to the principal mobile engineer
1. **Framework (06a):** the model above; the sheet (Sheet + Tabs + a lazy grid with `grid-tile-min`); tiles from the playhead frame at each effect's Ref time and 100% intensity, on demand, cached for the visit (the P4-07 request 4 use case); the effect bar; the Intensity panel; landing, lane slide, the 3-overlap rule; `Modifier.coveredByModal`; focus return to the new item.
2. **Shaders** per the table, **in the output pixel size with shares of the short side** (so preview and export match), time-based ones from the frame's time rounded to 1/30 s and a per-effect hash seed (never `Random`). Verify the current Media3 `GlShaderProgram` / `BaseGlShaderProgram` API at the start; effects run **after** the clip pipeline and the overlay composite, before the export watermark. iOS gets a marked stub that reports unsupported (never a silent no-op).
3. **Performance:** one pass per active item; blurs separable and downsampled; the preview drops resolution before frames (report fps per effect in the handoff).
4. **Deep-fry** calls P2-04's `deepFry` formula (same function) so the two editors agree; `film_grain` and `static` share one noise function.
5. **Debug draft `effects-check`** and the reference scenes; frames saved to `docs/qa/references/P4-06-effects/` (30 effects × 100% and 50% × the times above).
6. **Strings** in the five files (names, tabs, 30 descriptions; machine drafts are fine until P5); **Analytics:** four `EditorTool` values.
7. **Constants:** one commonMain `EffectCatalog` next to `OverlayCatalog`, with a comment pointing to this spec and `design/tokens.json` → `effects`; never through `MemixTheme`.
8. **Icon:** `effects` (below); Intensity reuses `adjust`; Change effect reuses `replace`.

## Tokens and icons
- **No new color, size, radius or motion token.** The grid reuses `grid-tile-min`, the pressed tile `hairline`, the bonk `duration-bonk`.
- **New content group** `effects` in `design/tokens.json` and `design/system/tokens.json` (envelopes, the max overlap, the 30 ids by tab with defaults and parameters, the photosensitivity ceiling). Kotlin: `EffectCatalog` (commonMain next to `OverlayCatalog`), not in `MemixTheme`.
- **One new icon** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review): `effects` (Kotlin `MemixIcons.Effects`, `ic_effects.xml`): a **solid 7 px circle** (the core) at the right with **three stroked arcs trailing to its left** (2 px, flat caps, lengths 6, 9 and 12 px, 2.5 px apart vertically): a thing in motion. It must not read as `blur` (a dot with four strokes around it, no trail) or `audio_effects` (a wave with a dot).
- **Pointer edits made with this spec:** ToolBar README (the effect item bar; Effects in the main bar), Timeline README (effect items), DESIGN_SYSTEM (iconography, "Effects" content paragraph), P4-07's "Deep-fry is one of the effects" line stays true.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Which 30 effects, and which half comes first?**
- *Problem:* the PRD says "about 30" and names eight; the list has to be fixed before the build, and the first half must hold Zoom punch (P4-14 needs it).
- *Considerations:* **A. The list above, 06a = Motion, Light, Color; 06b = Glitch, Blur, Retro** (recommended; applied): 06a is cheap single-pass shaders so the zoom punch, flash and deep-fry arrive early; the heavy blurs and distortions are in 06b where they can be cut. Cost: Glitch and RGB split (PRD names) arrive in 06b. **B. Glitch in 06a, Light in 06b:** the PRD's headline effects first; cost: 06a gets heavier and riskier for 30 fps.
- *Reverse:* a move of five ids between halves; no migration.

**2. Do effects apply to the whole frame, captions included?**
- *Problem:* a Deep-fry or Shake could hit only the video, or the video plus captions and stickers.
- *Considerations:* **A. The whole composed frame** (recommended; applied): the meme look (fried captions, a shaking caption) and the P1-05 order (effects last); no per-layer picker. Cost: a user who wants a clean caption over a glitched clip can't (workaround: a caption added after is still hit; Later: "Video only" switch). **B. Video only (main and overlays):** clean captions, but zoom punch wouldn't zoom the caption, which reads wrong.
- *Reverse:* an engine ordering change; the model is the same.

**3. Is the main bar the only door to Effects?**
- *Problem:* with a clip selected the bar shows clip tools, so adding an effect needs a tap on empty timeline first.
- *Considerations:* **A. Main bar only** (recommended; applied): one door, no clutter in nine clip bars; the extra tap is the same one every main tool needs. **B. Also an Effects tool in every clip bar:** saves a tap but adds a 16th tool to bars that already scroll.
- *Reverse:* add the tool later; no migration.

**4. Three effects at once, at most?**
- *Problem:* stacking costs a shader pass each; unlimited stacking breaks 30 fps on a mid phone.
- *Considerations:* **A. Three** (recommended; applied; a constant): enough for punch + shake + glitch; cost: a toast when a fourth is wanted. **B. Unlimited, drop resolution:** never refuses, but a stack of heavy effects looks soft and the owner can't promise 30 fps. **C. Two:** safest, restricts combos.
- *Reverse:* one constant.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Content with more than three flashes in any one second can trigger seizures; the guideline limit is three flashes or below the general flash threshold | Guidance | [WCAG 2.2 SC 2.3.1](https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html) |
| Easing Béziers (0.42, 0, 0.58, 1) and (0, 0, 0.58, 1) | Guidance (standard) | [MDN: `<easing-function>`](https://developer.mozilla.org/en-US/docs/Web/CSS/easing-function) |
| Media3 offers custom GL shader programs for per-frame effects | Guidance (API docs; verify at the start) | [Transformer: custom effects](https://developer.android.com/media/media3/transformer/transformations) |
| W3C separable blend formula used by `glow` (screen) | Guidance | [W3C Compositing and Blending](https://www.w3.org/TR/compositing-1/) |
| Mobile editors place an effect as a timeline item at the playhead with an intensity control | Assumption (product knowledge; help pages were not reachable here) | none |
| A 3-column, two-row tile tab fits a 70% sheet at 640 dp | Evidence (arithmetic from the Sheet README and `grid-tile-min`) | `design/system/components/Sheet/README.md` |
| The 30 names and recipes are authored here | Evidence | this file |
