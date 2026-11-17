# Speed: constant 0.1x to 10x (09a) and speed curves with presets (09b) · P4-09

Eighth Phase 4 spec (rewritten 17 Nov after a lost session). It reuses, and does not repeat:
- `P3-09-audio-effects.md`: the **sound** clip Speed (0.5x to 2x, pitch kept, clip length changes, the lane rule), the panel shell (title, Reset, Done, Tabs, scroll and pinned rules), the Slider with `snapValue`.
- `P1-06-clip-edits-and-audio.md` and `P1-09`: the main clip bar, Detach audio, the "Mute original audio" toggle, Volume, ripple on the main row, "sounds don't follow ripple edits", undo naming, "Preview catching up".
- `P4-01b-overlay-layers.md`: video overlays and the lane rule. `P4-02b-keyframes.md`: keyframes and their note "Speed: keyframes scale with the clip". `P4-08-transitions.md`: a transition that no longer fits is shortened.
- `P4-07-video-filters-addendum.md`: the panel budget method and the main-bar tool order. Component READMEs: ToolPanel, Tabs, SegmentedTabs, Slider, OptionTile, SegmentedChoice, Toggle, Toast.

## Job
"Make it slow-mo, make it fast, make it hit": 2x for the funny part, 0.5x on the fall, a bullet-time dip on the punchline. PRD → Video editor → Speed: constant 0.1x to 10x; speed curves with presets (ramp, bullet, flash) and a custom curve. **One main action: none.** Export stays the one blue action; the panel's Done is Ghost.

**Done when** (TICKETS): 09a audio follows the speed or mutes, per the user's choice; 09b a curve plays the same in preview and export. Both made checkable in Measured.

**Split (for the PM).**

| Part | Contains |
| --- | --- |
| **P4-09a** | The model (speed, curve points, sound choice: **one schema bump for both halves**), the Speed tool and panel (Normal view only, no tabs), constant speed on main video clips and video overlays, the Sound choice, length change and lane rule, keyframe scaling, the retime chip, Detach rules, reference harness |
| **P4-09b** | The **Curve tab** (Tabs appear), the 6 presets, the graph, points and their no-drag route, curve audio, curve references. **Cut-list item** (PRD risk table) |

Before 09b the panel shows no tabs; after it, **Normal · Curve**.

**Fast path, with tap counts.**
- **Play a clip at 2x:** the clip (1), Speed (2), the **2x** tile (3). The panel's playhead and the clip stay; the stage plays at once if playing.
- **Slow-mo:** the same with the 0.5x tile; **any value:** drag the slider.
- **No sound at that speed:** Sound, **Mute** (one more tap, in the same panel).
- **Bullet-time dip (09b):** Speed (2), Curve tab (3), **Bullet** (4).
- **From the main bar:** Speed with nothing selected acts on the clip under the playhead: Speed (1), a tile (2).
- **No dragging at all:** tiles, slider keyboard steps and the Curve tab's steppers do everything.

**Principles check.** Content first: the stage stays live and the panel changes the picture at once. Sound first: the Sound choice sits in the same panel, no hunting; the pinned "Add a meme sound" row is untouched. Fast path: a tile is one tap. One-handed: tiles and slider in the lower half. Forgiving: every change one undo step; Reset; the choice is never destructive (the source is untouched). Accessible: a tile and a stepper for every drag. Five languages: names measured at 200%.

**Color roles:** no blue except the sliders' `primary` fill; selection white (the selected tile, the selected curve point, the segmented choice). The retime chip is `text` on `scrim`. No new color.

## How speed, sound and P3-09 fit together (the interaction rules)
| Thing | Rule |
| --- | --- |
| **Video clip speed** (this spec) | 0.1x to 10x, constant or a curve. Applies to main video clips and video overlays. **Not photos** (a photo's length is its length: trim it) |
| **Sound clip speed** (P3-09: meme sounds, audio clips) | Stays 0.5x to 2x in Audio effects (the owner-approved range where meme sounds stay recognizable). Not changed here |
| **The video clip's own sound, Keep** | Time-stretched **at the clip's speed with the pitch kept**, the same method and quality as P3-09's Speed (`SonicAudioProcessor`-class, verified at build). So a voice at 2x is faster but not a chipmunk, as in P3-09. A chipmunk or a monster voice is Detach audio, then Audio effects (Pitch) |
| **Sound, Mute** | The clip's sound is silent for its whole span. It is a per-clip choice stored with the speed, separate from Volume (see below) |
| **Rough sound** | Outside 0.5x to 2x the stretched sound gets rough. The panel says so with one hint line when Keep is chosen there (`speed_audio_rough`); nothing is changed for the user |
| **Detach audio** (P1-09) on a clip that has a speed | The new audio clip gets **the clip's speed as its own Speed**, **only when that is 0.5x to 2x** and the Sound choice is Keep, so picture and detached sound stay in step and the sound clip's Speed slider (P3-09) shows it. Outside the range, or with Mute chosen, the tool explains (`explain_detach_speed`) and detaches nothing. Curves: not detachable (same toast) |
| **Volume** (P1-06) and **Mute original audio** (P1-05 toggle) | Independent of Speed's Sound choice: the sound plays only if **all three** allow it. When Speed's Sound is Mute, the Volume panel shows one line at the top `volume_off_by_speed` ("Sound is off in Speed.") and keeps working |
| **Length** | Clip length on the timeline = (trimmed source length) / speed (curves: the integral). The start stays; the end moves |
| **Main track** | Later main clips ripple (move earlier or later). **Sounds, captions, overlays and effects do not follow** (the P1-06 rule), so a speed change can desync them: stated here, accepted for P1 consistency, and the end-of-phase test watches whether people trip on it |
| **Overlay clip** | Gets longer: if it now overlaps the next item on its lane, it moves to the first lane free for its whole span, else a new one, lanes slide, 4 s toast `speed_moved_lane_toast` (P3-09's rule, own string). It never overwrites a neighbor |
| **Past the end of the video** | The `scrim` wash on the overhang; nothing said |
| **Trim** a sped clip | The handles work in what you see (timeline time); the source range moves by speed x the drag |
| **Keyframes** (P4-02b) | **Scale with the clip**: each keyframe's time keeps its **share of the clip's length** (a keyframe at 40% stays at 40%) when the speed or curve changes, in the same undo step. Under a curve the share is of the new timeline length (not the source). Keyframes of keyed volume, framing and opacity all scale; easing is unchanged |
| **Transitions** (P4-08) | A transition that no longer fits after the length change is shortened (3 s toast `transition_shortened_toast`) in the same step |
| **Split, Duplicate** | Both pieces keep the speed (a curve is cut: each piece keeps its part of the curve, the cut point becoming a point); the copy keeps everything. **Replace:** keeps the speed and Sound (the user's choice for the slot; the new media's length may differ: its trim resets as in P1-16) |
| **Reverse** (P4-10) | Independent; the sound is reversed too, and Sound Mute still silences it |
| **Maximum length** | A clip may not become longer than **10 minutes** on the timeline (`speed.maxClipLengthSeconds` 600): the thumb stops there, no toast |

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Speed panel** (ToolPanel) | The **Speed** tool of a selected main video clip or video overlay; or the **Speed** tool of the main bar (nothing selected): it selects the video clip under the playhead (the last clip at the very end) and opens | Done, system back, Escape. The clip stays selected |

- **Main bar** Speed: the last tool (final main bar: Edit · Meme sounds · Text · Add media · Overlay · Voiceover · Stickers · Effects · Filters · Remove BG · Speed); **not shown** in an empty project or one with no main video clip. If the clip under the playhead is a photo it picks the nearest video clip to the left, else to the right; with only photos around it, the tool isn't shown.
- **Photos and the missing:** the Speed tool is **absent** on photo clips and on a clip whose file is missing (P4-16 order).
- **Playhead:** playback keeps running if it was running (the change is heard and seen). If the playhead is outside an overlay's span it jumps to its start when the panel opens (P4-01b). **Kept:** speed, curve and Sound choice on the clip. Not kept: the open tab beyond the visit.
- Back order: the panel closes, then the clip bar, then the editor.

## Layout
Board: `VideoEditor.dc.html` lists Speed in the main bar and shows nothing else. Everything below is new.

### The tool and the bars
- **Icon `speed`** (new, below), label "Speed" (`tool_speed`), the normal look; click label "Change how fast this clip plays". The clip's spoken description gains **"Speed 2x"** (below).
- **Final bars** (consolidated with P4-08 and P4-10; the tools before are P4-04, P4-02b, P4-07): **main video clip:** Split · Volume · Delete · **Speed · Freeze frame · Reverse** · Crop · Fill or Fit · Position · Filters · Green screen · Mask · Keyframes · **Transition** · Duplicate · Detach audio · Reorder · Replace. **Video overlay:** Position · Opacity · Delete · Green screen · Split · Volume · **Speed · Reverse** · Crop · Blend · Mask · Filters · Keyframes · Layer order · Move here · Duplicate · Detach audio. Why: Split, Volume, Delete and Speed (four tools) show whole beside Close at 360 dp; time tools sit together before the picture tools because "slow it, freeze it, reverse it" is the meme edit; Crop, Fit and Position, which P4-16 put right after Delete, move three places right, still within two swipes.
- **The retime chip** on a clip (every retimed clip, selected or not): a `scrim` chip (`radius-sm`, 16 dp tall, `space-1` padding) at the clip's **bottom-left**, `space-1` inside, holding in `caption` `text`: the `reverse` icon at `icon-small` first when reversed, then the speed ("2x", "0.5x"), or the `speed` icon alone for a curve. Drawn only when the clip is at least 56 dp wide (a clip's duration badge sits top-left and its keyframe badge top-right; a 40 dp main clip holds badge 16 + gap + chip 16). A cue, not a control. Contrast `text` on `scrim` over a light thumbnail 7.6:1 or more.

### The Speed panel (ToolPanel)
Title row: **Speed** (`title`, heading), **Reset** (Quiet), **Done** (Ghost); Reset and Done wrap under the title at large font. Reset puts the clip back to 1x, no curve, Sound **Keep**, one step "Reset speed"; with nothing set a tap explains (`speed_nothing_to_reset`, a toast, no step).

**09a: Normal only (no tabs). 09b: Tabs "Normal · Curve" (the Tabs component, as P3-09) under the title row, opening on the clip's own mode.** Setting anything in the other tab switches the clip to that mode (one step; undo restores).

**Normal** (`speed_mode_normal`), `space-4` apart:
1. **Tiles:** a scrolling row, edge to edge, of **OptionTiles, label layout**: **0.25x · 0.5x · 1x · 2x · 4x · 8x**; the tile equal to the current speed is white (`on-primary` label); with another value none is. A tap applies at once, one step "Speed". Tapping the selected tile does nothing.
2. **Speed slider:** 0.1x to 10x, **default 1x**, readout `2x · 1.5 s` in `timecode` (the speed, then the clip's **resulting length**), snaps to 1x (one SegmentTick). **The scale is logarithmic:** the thumb position *p* (0 to 1) gives speed = 10^(2*p* − 1), so 0.1x is the left end, 1x the middle and 10x the right end. The grid for steps (keyboard, TalkBack): **0.1 steps from 0.1x to 2x, then 0.5 steps to 10x** (0.1, 0.2 … 2.0, 2.5, 3.0 … 10), always landing on the grid so 1x is reachable without a pointer; dragging is continuous but **rounded to two decimals**; double-tap the thumb resets to 1x; one undo step on release; the preview follows while dragging (audio is heard after release, as P3-09's Pitch and Speed, accepted).
3. **Sound:** a **SegmentedChoice "Keep · Mute"** with the label "Sound" (`speed_sound`): Keep = the clip's own sound follows the speed; Mute = off. One step each ("Speed sound"). Below it, **only** when Keep is chosen and the speed is outside 0.5x to 2x, one hint line in `label` `text-secondary`: "Sound gets rough at this speed. You can mute it." (`speed_audio_rough`). A photo has no sound; this row is shown for every video clip, including one that has no audio track (Keep, silent; nothing is said).
- **Budget** (content 133 / 206 / 252 dp; rows measured from the component code: tile row 48, Slider row 68 as the safe figure, SegmentedChoice 56, hint 32, gaps 16): 48 + 16 + 68 + 16 + 56 = **204 dp** (+ 48 with the hint = 252). **360 × 640:** the tiles and the slider show whole (132 of 133); **Sound is under the fold** and one swipe reaches it (the floor size; accepted, as P4-04's second slider). **360 × 800:** all three rows show (204 of 206; the hint, when present, scrolls). **412 × 915:** all whole, with the hint. With 09b's tabs pinned (360 × 800 and 412 × 915 only: 96 dp is over 40% at 640) the content loses 64 dp: Sound scrolls at 800. At 200% font every row grows and the panel scrolls on every phone.

**Curve (09b)** (`speed_mode_curve`):
1. **Preset tiles:** a scrolling row of OptionTiles, label layout, **Ramp up · Ramp down · Bullet · Flash · Pulse · Slam**; the tile of the current preset is white; a curve the user has edited has none selected. A tap replaces the curve with the preset's points (one step "Speed curve"). A **None** isn't a tile: Normal's tiles and the tab are the way back.
2. **The graph:** 96 dp tall, full panel width inside `space-4` padding, `surface-raised`, `radius-md`. X = **position in the clip's own picture, 0 to 100%** (the source, so a dip sits on the moment it was made for; the timeline gets longer or shorter as a result). Y = speed on the same log scale, **0.1x at the bottom, 1x on a `hairline` line, 10x at the top**, `caption` labels "0.1x" "1x" "10x" at the left in `text-muted` (4.6:1). The curve is a `text` line, 2 dp, smooth (interpolated in log speed with a cosine ease between points). **Points:** at most **8**; each a 12 dp disc in `text-secondary` with a `touch-target` target; the selected one is `selection` white and 16 dp. A caption at the top right of the graph reads the resulting length in `timecode`, `text-secondary` ("3.6 s").
3. **Point controls** (shown when a point is selected): **Previous point and Next point** (IconButtons, `arrow` turned left and right, with the label "Point 2 of 5" between), **Time** stepper (minus, readout in percent of the clip, plus; 2% a tap, hold repeats), **Speed** stepper (minus, readout `2x`, plus; one step on the grid above), **Remove point** (Quiet; the first and last points can't be removed: it explains `speed_point_end`) and **Add point** (Secondary; adds one in the middle of the longest gap on the curve and selects it).
4. **Sound** (the same SegmentedChoice as in Normal, below everything).
- **Gestures on the graph:** **tap on the curve or the empty graph** adds a point there (the speed from the curve's value at that x); **drag a point** moves it: horizontal between its neighbors (never past them, a hard stop 1% short; the end points move only vertically), vertical clamped 0.1x to 10x; snaps to the 1x line within 3% (one SegmentTick). **LongPress haptic** at pick-up. One gesture = one undo step "Speed curve". The preview follows on release (the curve changes the length, so it can't follow live).
- **Presets** (points = source position, speed; all interpolate smoothly in log speed; first and last at 0 and 100%):

| Id | Name | Points | The idea |
| --- | --- | --- | --- |
| `ramp_up` | Ramp up | (0, 0.5), (1, 4) | Starts slow and builds |
| `ramp_down` | Ramp down | (0, 4), (1, 0.5) | Starts fast and settles |
| `bullet` | Bullet | (0, 1), (0.35, 1), (0.5, 0.15), (0.65, 1), (1, 1) | Normal, a deep slow-mo dip, normal again |
| `flash` | Flash | (0, 1), (0.4, 1), (0.5, 6), (0.6, 1), (1, 1) | Normal with a quick whoosh in the middle |
| `pulse` | Pulse | (0, 1), (0.2, 3), (0.4, 0.5), (0.6, 3), (0.8, 0.5), (1, 1) | Fast, slow, fast, slow |
| `slam` | Slam | (0, 3), (0.7, 3), (0.8, 0.1), (1, 0.1) | Races, then crawls to the end |

- **Budget:** at 360 × 640 the tabs scroll with the content (title 48 + tabs 48 over 40%); the first view is tabs plus the tile row (48 + 16 + 48 = 112 of 133). The graph, the point row, the steppers and Sound scroll under it: **about 96 + 16 + 48 + 16 + 48 + 16 + 48 + 16 + 56 + 16 + 48 (buttons) = 424 dp**, so the Curve tab scrolls on every phone, with the pinned tabs and the tile row and the graph in view at 360 × 800 (142 dp of content: tiles 48 + 16 + graph 78 of 96; the graph's lowest 18 dp, the 0.1x end, is one swipe away). Accepted: the Curve tab is a pro tool and the presets are the fast way; the steppers are the single-pointer route (WCAG 2.5.7), so the order is tiles, graph, point row.

## Rules behind the layer
### How it plays (preview and export match)
Part of every draft's look, so fixed; in `design/tokens.json` → `speed`.

| Rule | Value |
| --- | --- |
| Constant speed | Output time = source time / speed. **Picture:** each output frame takes the source frame at its mapped time (nearest earlier frame); no frame blending, no optical flow in v1 (below 0.5x frames repeat and look stepped, above 2x frames are skipped: stated plainly, Questions 3) |
| Curve | speed(*x*) interpolated in **log speed** between points with a cosine ease (the same function in preview and export); the output time is the integral of 1 / speed over the source; evaluated at 1/60 of the clip's length steps and applied as **piecewise-constant** segments the engine can use (the engine verifies Media3's speed-changing support at the start) |
| Sound, Keep | The same piecewise speed, **pitch kept**; at constant speed the P3-09 method. Segments are crossfaded 10 ms so a curve has no clicks |
| Sound, Mute | Silence for the clip's span |
| A still frame | Photos are not retimed |
| Order in the clip's pipeline | Retiming decides **which source frame** a clip shows; everything in P4-04's pipeline (turn ... blend) then works on that frame, so a filter or a key looks the same at any speed |
| Limits | Speed 0.1x to 10x; curve points 2 to 8; clip length on the timeline at most 10 minutes |

**Reference renders** (CLAUDE.md rule 5) in `docs/qa/references/P4-09-speed/`, from exports of the debug clip **`speed-check`** (Requests 4): 10 s, 1080p30, a **burned-in source time** (to the centisecond) over a lettered grid, with a 1 kHz tone whose level steps every second so the audio's mapping can be read.

| File set | Setup | Must show |
| --- | --- | --- |
| `const-0_1`, `const-0_5`, `const-2`, `const-10` (frames at output 0.5, 1.0, 1.5 s) | Constant speeds | The burned-in source time equals output time × speed within one source frame |
| `sound-keep-2`, `sound-mute-2` (audio measures) | 2x with Keep / Mute | Keep: the tone stays 1 kHz within 1%, the length halves; Mute: silent (RMS under −80 dBFS) |
| `curve-ramp_up` ... `curve-slam` (6 presets, frames at output 10% steps) | Each preset | The source time at each output time equals the integral of the preset's curve within **one source frame (33 ms)** |
| `curve-custom` | 4 points set by the harness | Same |
| `keyframes-scale` | A keyed overlay (position, 0 s to 2 s), speed 2x | The keyframe moves from 2 s to 1 s (same share), position at the clip's midpoint unchanged within 1% |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tool in the bars | `speed` icon and label | `tool_speed` |
| Panel, 1x | The 1x tile white; slider at the middle; Sound Keep | `speed_title` |
| A tile equal to the speed | That tile white | `speed_tile_*` |
| A value off the tiles | No tile selected | none |
| Slider dragged | Readout moves; the picture follows; one step on release | `speed_readout` |
| Clip longer than 10 min | The thumb stops | none |
| Keep outside 0.5x to 2x | The hint line | `speed_audio_rough` |
| Sound Mute | The control white on Mute; the clip's chip unchanged; Volume panel note | `speed_sound_mute`, `volume_off_by_speed` |
| Overlay now overlaps its neighbor | Moves lane; lanes slide; toast 4 s | `speed_moved_lane_toast` |
| Transition no longer fits | Shortened; toast | `transition_shortened_toast` |
| Reset with a change | One step; back to 1x, Keep | `edit_speed_reset` |
| Reset with nothing set | Toast, no step | `speed_nothing_to_reset` |
| Photo clip, missing file | Tool absent | none |
| Main bar, no video clip | Tool absent | none |
| Detach on a clip outside 0.5x to 2x, or Mute, or a curve | Toast, nothing detaches | `explain_detach_speed` |
| Curve tab (09b), a preset | Tile white; curve drawn; points; length caption | `speed_curve_*` |
| Edited curve | No tile selected | none |
| Point selected | White 16 dp disc; point row | `speed_point_of` |
| Point at its neighbor's limit | The drag stops 1% short | none |
| 8 points, Add point or tap on graph | Toast | `explain_speed_points_max` |
| Remove the first or last point | Toast | `speed_point_end` |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Preview can't decode fast enough (10x) | Frames skipped; sound never glitches (the engine keeps a prepared copy, the P3-09 rule; "Preparing" status after 1 s) | `fx_preparing` (shared) |
| Undo removes the clip | Panel closes | none |
| Long content (5 languages, 200% font) | Tiles keep one line and widen; the readout and label wrap; the SegmentedChoice stacks; the panel scrolls; the graph keeps 96 dp | none |
| First run | No coach mark | none |
| An old draft | 1x everywhere | none |

## Interactions
- **Tile tap:** one step "Speed"; stays open. **Slider:** drag or tap the rail; one step on release; snap to 1x with one SegmentTick; double-tap resets.
- **Stage taps** play or pause. **Keyboard (basic, P1):** Tab reaches tiles, slider, choice, graph points (arrow keys move the selected point by one grid step or 2%); Enter applies; Escape is back.
- **Haptics:** SegmentTick for the 1x snap (slider and graph); LongPress at a graph point's pick-up.
- **Undo steps:** "Speed", "Speed sound", "Speed curve", "Reset speed"; any keyframe scaling, lane move, transition shortening or trim shortening folds into the step that caused it.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens, closes | 200 ms slide | 120 ms fade (instant on Android) |
| Tab change (09b) | 200 ms cross-fade | 120 ms fade |
| Tile ring, choice, point selection | 120 ms | same |
| Clip end moving, later clips rippling | 200 ms (P1-05) | 120 ms cross-fade |
| Lane move | 200 ms slide | 120 ms cross-fade |
| The curve redrawn after a preset | 120 ms cross-fade (no morph) | same |
| Toasts, status | 120 ms fade | same |

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5). Budgets are characters. Speed is written "2x" with the multiplication sign "×" in the app (as P3-09's `fx_speed_value`), never the letter x.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_speed` | Speed | 12 | Kecepatan · Velocidad · Velocidade · स्पीड | Tool label and panel title (`speed_title`); same words as P3-09's `fx_speed`. es 9, pt 10 characters: one word, fits a 64 dp tool at 100% |
| `tool_speed_click` | Change how fast this clip plays | 40 | Ubah seberapa cepat klip ini diputar · Cambia la velocidad de este clip · Mude a velocidade deste clipe · इस क्लिप की चलने की गति बदलें | Click label; 31 en |
| `speed_mode_normal` | Normal | 12 | Normal · Normal · Normal · सामान्य | Tab (09b) |
| `speed_mode_curve` | Curve | 12 | Kurva · Curva · Curva · कर्व | Tab (09b) |
| `speed_tile_value` | %1$s× | 8 | %1$s× · %1$s× · %1$s× · %1$s× | Tiles 0.25× 0.5× 1× 2× 4× 8×; the slider's readout adds the length: `speed_readout` "%1$s× · %2$s" (the length from the `timecode` format, "1.5 s") |
| `speed_sound` | Sound | 12 | Suara · Sonido · Som · आवाज़ | Label of the choice |
| `speed_sound_keep` | Keep | 10 | Pertahankan · Mantener · Manter · रखें | Segment; the sound follows the speed |
| `speed_sound_mute` | Mute | 10 | Bisukan · Silenciar · Silenciar · म्यूट | Segment; same word as the shipped mute strings |
| `speed_audio_rough` | Sound gets rough at this speed. You can mute it. | 60 | Suara jadi kasar di kecepatan ini. Kamu bisa membisukannya. · El sonido suena áspero a esta velocidad. Puedes silenciarlo. · O som fica áspero nessa velocidade. Você pode silenciá-lo. · इस गति पर आवाज़ खुरदुरी हो जाती है। आप इसे म्यूट कर सकते हैं। | Hint; 49 en, id 59, pt 55 |
| `volume_off_by_speed` | Sound is off in Speed. | 36 | Suara mati di Kecepatan. · El sonido está apagado en Velocidad. · O som está desligado em Velocidade. · स्पीड में आवाज़ बंद है। | Top of the Volume panel; the word matches `tool_speed` per language; 22 en |
| `speed_curve_ramp_up` | Ramp up | 14 | Naik bertahap · Acelerar · Acelerar · धीरे से तेज़ | Preset tile |
| `speed_curve_ramp_down` | Ramp down | 14 | Turun bertahap · Desacelerar · Desacelerar · धीरे से धीमा | Preset tile |
| `speed_curve_bullet` | Bullet | 14 | Bullet · Bullet · Bullet · बुलेट | Keep the loanword where editors use it |
| `speed_curve_flash` | Flash | 14 | Flash · Flash · Flash · फ़्लैश | The PRD's name; a quick whoosh, not a light |
| `speed_curve_pulse` | Pulse | 14 | Denyut · Pulso · Pulso · धड़कन | |
| `speed_curve_slam` | Slam | 14 | Banting · Golpe · Batida · धम | Races, then crawls |
| `speed_curve_length` | %1$s | 8 | the length from `timecode` | Graph caption, "3.6 s" |
| `speed_point_of` | Point %1$d of %2$d | 20 | Titik %1$d dari %2$d · Punto %1$d de %2$d · Ponto %1$d de %2$d · बिंदु %1$d / %2$d | Between Previous and Next |
| `speed_point_time` | Time | 12 | Waktu · Tiempo · Tempo · समय | Stepper label |
| `speed_point_speed` | Speed | 12 | as `tool_speed` | Stepper label |
| `speed_point_add` | Add point | 14 | Tambah titik · Añadir punto · Adicionar ponto · बिंदु जोड़ें | Button |
| `speed_point_remove` | Remove point | 16 | Hapus titik · Quitar punto · Remover ponto · बिंदु हटाएं | Button |
| `speed_point_end` | The first and last points stay. Move them instead. | 60 | Titik pertama dan terakhir tetap ada. Geser saja. · El primer y el último punto se quedan. Muévelos. · O primeiro e o último ponto ficam. Mova-os. · पहला और आख़िरी बिंदु रहेंगे। उन्हें खिसकाएं। | Toast; 51 en |
| `explain_speed_points_max` | That's the most points a curve can have. | 50 | Ini jumlah titik terbanyak untuk kurva. · Es el máximo de puntos de una curva. · Esse é o máximo de pontos de uma curva. · एक कर्व में इतने ही बिंदु हो सकते हैं। | Toast; 40 en |
| `explain_detach_speed` | Detach works at 0.5× to 2× with sound kept. Change the speed first. | 80 | Pisahkan hanya bisa pada 0,5× sampai 2× dengan suara aktif. Ubah kecepatan dulu. · Separar funciona de 0,5× a 2× con sonido. Cambia antes la velocidad. · Separar funciona de 0,5× a 2× com som. Mude antes a velocidade. · अलग करना 0.5× से 2× पर, आवाज़ रखने पर ही होता है। पहले गति बदलें। | Toast; 67 en, es 70 |
| `speed_moved_lane_toast` | Moved to a free layer so nothing overlaps. | 50 | Dipindah ke lapisan kosong agar tidak tumpang tindih. · Movido a una capa libre para que no se solape. · Movido para uma camada livre para não sobrepor. · कोई टकराव न हो, इसलिए खाली लेयर पर ले गए। | Toast 4 s; use the shipped word for "lane" in each language (check `fx_moved_lane_toast` and copy its term); 42 en |
| `speed_nothing_to_reset` | Speed is already normal. | 40 | Kecepatan sudah normal. · La velocidad ya es normal. · A velocidade já está normal. · गति पहले से सामान्य है। | Toast |
| `a11y_state_speed` | Speed %1$s× | 20 | Kecepatan %1$s× · Velocidad %1$s× · Velocidade %1$s× · गति %1$s× | Appended to the clip's spoken description ("Speed curve" for a curve: `a11y_state_speed_curve` "Speed curve" · Kurva kecepatan · Curva de velocidad · Curva de velocidade · स्पीड कर्व) |
| `a11y_speed_changed` | Speed %1$s× | 20 | as `a11y_state_speed` | Polite, after a release |
| `edit_speed` | Speed | 24 | as `tool_speed` | Edit names after "Undo: " |
| `edit_speed_sound` | Speed sound | 24 | Suara kecepatan · Sonido de velocidad · Som da velocidade · स्पीड की आवाज़ | |
| `edit_speed_curve` | Speed curve | 24 | Kurva kecepatan · Curva de velocidad · Curva de velocidade · स्पीड कर्व | |
| `edit_speed_reset` | Reset speed | 24 | Setel ulang kecepatan · Restablecer velocidad · Redefinir velocidade · स्पीड रीसेट | |

Reused unchanged: `panel_done`, the Reset word of P3-09's `fx_reset`, `editor_updating_preview`, `transition_shortened_toast`, `fx_preparing`.

Translator notes:
- **Speed** is how fast the clip plays: 2× is twice as fast, 0.5× is half as fast (slow motion). Use the word of your language's video editors, the same as the sound speed in Audio effects.
- **Sound: Keep** means the clip's own sound plays at the new speed; **Mute** turns it off. **Curve** is a speed that changes over the clip; **Ramp up**, **Bullet** (slow-motion dip), **Flash** (a quick burst), **Pulse** and **Slam** are the presets' short names.
- **Hindi:** the interface uses the system Devanagari face; seconds are written in full.

## Accessibility
- **Focus and TalkBack order:** the title takes focus; then Reset, Done, the tabs (09b), the tiles (a single-choice group "Speed": "2×, selected, 4 of 6"), the slider, the Sound choice. Closing returns focus to the Speed tool.
- **The slider:** the readout is the state description ("2×, 1.5 seconds"); TalkBack and keyboard steps follow the grid above; a polite announcement after a change (`a11y_speed_changed`).
- **The graph (09b):** it is a group "Speed curve"; every point is a node `speed_point_of` with its time and speed spoken ("Point 2 of 5, 50 percent, 0.15×"), actions Move earlier, Move later, Faster, Slower, Remove; the steppers and Previous or Next are visible buttons, so a screen-reader or keyboard user edits a curve without dragging (WCAG 2.5.1, 2.5.7). The drawn line is decorative.
- **The clip's spoken description** gains "Speed 2×" (or "Speed curve"), then "Reversed", then the transition, after Keyframes (P4-08, P4-10). Never in the state description.
- **Touch targets:** tiles 48 dp tall; slider row 48; graph points 48 dp targets; steppers 48; the retime chip is output only.
- **Contrast:** tile labels `text` on `surface-raised` 12.4:1, selected `on-primary` on white 18.9:1; graph labels `text-muted` on `surface-raised` 4.6:1; the curve `text` on `surface-raised` 12.4:1; points `text-secondary` on `surface-raised` 5.7:1 and the selected `selection` 14:1; the chip as above. **State is never color alone:** the selected point is also larger; the selected tile is white and spoken "selected".
- **200% font:** see States. **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never for opening the panel). New ids for the PM to confirm:

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A tile or a slider release that changes the speed | `tool: "speed"`, `value: "<speed>"` (the rounded multiplier as a string, for example `0.5`, `2`) |
| `tool_use` | The Sound choice changes | `tool: "speed_sound"`, `value: "keep"` or `"mute"` |
| `tool_use` | A preset (09b) | `tool: "speed_curve"`, `value: "<preset id>"` |
| `tool_use` | A custom curve gesture or stepper (09b) | `tool: "speed_curve"`, `value: "custom"` |
| `tool_use` | Reset speed | `tool: "speed_reset"` |

Analytics stay off until consent (P5-01).

## QA compares
Use the debug clip `speed-check` (Requests 4) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-09a/` and `P4-09b/`.
1. **The tool:** the main clip bar (Speed fourth, whole beside Close); the main bar's Speed; a photo clip (absent); Spanish at 200%.
2. **The panel** at 360 × 640 (tiles and slider, Sound under the fold), 360 × 800, 412 × 915; at 0.1x, 1x, 10x; Keep with the rough hint; Mute and the Volume panel's note; Hindi at 200%.
3. **The retime chip** on a main clip and an overlay (2×, a curve, reversed).
4. **Length and ripple:** a 10 s main clip at 2x ends at 5 s, the next clip moves; an overlay slowed into its neighbor moves lane with the toast; the wash past the end.
5. **Curve tab (09b):** each preset's graph; a custom curve with 5 points, one selected, the steppers; the 8-point toast.
6. **Detach audio** on a 2× clip (a sound clip with Speed 2×) and on a 4× clip (toast).

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **09a, audio follows or mutes:** export `speed-check` at 0.5×, 2× and 4× with Keep: video length = source/speed within one frame, audio length equals video length within 23 ms, the tone stays 1 kHz within 1%, level within 1 dB of the source. Export with Mute: audio silent (RMS under −80 dBFS) over the clip's span, audio length unchanged. At 0.1× and 10×: length ×10 and ÷10; Keep sounds rough (ear check) and the hint shows.
- **Consistent with P3-09:** a meme sound clip's Speed still stops at 0.5× to 2×; a clip sped 2× and Detached carries Speed 2× and plays in step with the picture within 33 ms; the same clip at 4× refuses with the toast.
- **Keyframes scale:** a keyframe at 40% of the clip stays at 40% after 2× and after a curve (within one frame); one undo step undoes the speed and the keyframe times together.
- **09b, a curve plays the same in preview and export:** for each preset and `curve-custom`, the burned-in source time at ten output times in the editor preview (screen recording, stepped) equals the export's within one source frame and equals the formula within one source frame; the audio's level steps (the tone clip) fall at the same output times within 33 ms.
- **Preview speed:** 2× and 0.5× of a 1080p clip at 30 fps or more on the owner's phone (Media3 logs, release-type build); 10× keeps the sound clean (frames may skip, never audio); a slider drag for 3 s three times: janky frames under 5%.
- **Each edit is one undo step:** a tile, a release, Sound, a preset, a point drag, a stepper tap, Reset: Undo once each.
- **Old drafts open** at 1×; **leaks:** LeakCanary: open the panel, tiles, drag, tabs, points, close; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Speed on a clip | `MediaClip.speed: Float = 1f` (0.1 to 10) and `MediaClip.speedCurve: List<SpeedPoint>? = null` (`positionFraction`, `speed`; 2 to 8 points); a curve wins over the constant; **`soundMode: KEEP | MUTE`** (default KEEP). One `schemaVersion` bump in 09a for all three (09b adds none, as P3-09a did) |
| Derived | `clipDurationUs = integral over the trimmed source range of 1 / speed`; for a constant `(trimOut − trimIn) / speed`. One pure function used by the timeline, the preview and the export plan |
| Declarative | `SpeedSpec` (range, grid, presets with points, interpolation) in `:core:model`, reference renders |
| Edits | `setClipSpeed`, `setClipSpeedCurve`, `setClipSoundMode`, `resetClipSpeed`; each rescales keyframes, applies the lane rule or main ripple, shortens transitions, in one `commit` |
| Detach | `detachAudio` copies `speed` into the new `AudioClip.speed` only when constant, 0.5 to 2 and KEEP; otherwise it refuses |
| Not modeled | Photos have no speed; sound clips keep P3-09's `speed` |

## Requests to the principal mobile engineer
1. **Engine:** verify at the start the current Media3 support for speed changes with audio (`SpeedChangeEffect`, `SpeedProvider`, `SonicAudioProcessor` or the current names), and that a curve can be applied as piecewise-constant segments with 10 ms audio crossfades in **both** the CompositionPlayer and Transformer; report what can't be done. iOS gets a marked stub.
2. **Preview at extremes:** decode with frame skipping at high speeds; at 0.1× repeat frames; never glitch audio (prepared copy as P3-09's slow-phone rule).
3. **The panel:** Normal view, tile row, log slider (the mapping above, snap to 1×), the SegmentedChoice, the hint; the 09b tab, graph (drawn from data), points and steppers, nodes with actions; the readout with the resulting length.
4. **Debug clip `speed-check`** and scenes; references saved to `docs/qa/references/P4-09-speed/`.
5. **Rules:** keyframe scaling, lane move, ripple, transition shortening, Detach refusal, the Volume note, in the same commit as the edit.
6. **Constants:** one commonMain `SpeedCatalog` with a comment pointing to this spec and `design/tokens.json` → `speed`; **strings** in the five files; **analytics:** five `EditorTool` values; **icons:** `speed` (below).

## Tokens and icons
- **No new color, size, radius or motion token.** The graph is a 96 dp content area drawn from data; the retime chip uses `radius-sm`, `scrim`, `caption`, `icon-small`.
- **New content group** `speed` in `design/tokens.json` and `design/system/tokens.json` (range, grid, presets, limits). Kotlin: `SpeedCatalog`, not in `MemixTheme`.
- **One new icon** (the engineer draws, I approve in the review): `speed` (Kotlin `MemixIcons.Speed`, `ic_speed.xml`): a stroked half-dial arc (20 px wide, 10 px tall, 2 px stroke, flat caps) on a 2 px baseline, with a **solid needle** (a 5 px wedge, the core) rising from the baseline's center to the upper right, 2 px clear of the arc. It must not read as `volume` (rising bars) or `audio_effects` (a wave with a dot).
- **Pointer edits made with this spec:** ToolBar README (Speed in the main clip, overlay and main bars), Timeline README (the retime chip), DESIGN_SYSTEM (iconography, speed paragraph), P3-09's pointer to P4-09 stays true.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Does Keep time-stretch the sound with the pitch kept, or change the pitch like a tape?**
- *Problem:* "audio follows the speed" can mean two sounds: the same voice faster, or a chipmunk.
- *Considerations:* **A. Pitch kept, as P3-09's Speed** (recommended; applied): one behavior for every sound in the app; the chipmunk is Detach audio plus Pitch, which already exists; cost: very high and very low speeds sound rough (the hint, Mute). **B. Pitch follows speed:** the meme chipmunk in one tap and a cheaper engine; but sound clips and video clips would behave differently.
- *Reverse:* an engine switch; no migration.

**2. Detach audio on a clip with a speed.**
- *Problem:* the sound clip's Speed only goes 0.5× to 2×, a clip's goes 0.1× to 10×.
- *Considerations:* **A. Detach only within 0.5× to 2× with Keep; otherwise explain** (recommended; applied): picture and sound stay in step and the two ranges never clash; cost: a refusal toast at extreme speeds. **B. Widen the sound clip's Speed to 0.1× to 10×:** one range everywhere, but meme sounds become unrecognizable and P3-09 is already built. **C. Detach at 1×:** the sound would drift from the picture.
- *Reverse:* a rule; no migration.

**3. Smooth slow motion (frame blending) in v1?**
- *Problem:* below 0.5× each frame repeats and motion looks stepped.
- *Considerations:* **A. No, repeat frames** (recommended; applied): honest and cheap; meme slow-mo is usually a short dip. **B. Blend neighboring frames:** smoother at 0.25×, costs a second texture read per frame in preview and export and time to tune.
- *Reverse:* an engine addition later; no migration.

**4. Does a speed change move the sounds, captions and effects that sit later on the timeline?**
- *Problem:* the P1-06 rule says no (they stay at their times), so a 2× on an early clip drags the picture earlier under sounds placed by hand.
- *Considerations:* **A. Keep the P1-06 rule** (recommended; applied): one rule everywhere, and the test will show whether people trip. **B. Ripple everything after the clip:** keeps sync but changes a rule P1 users already learned; needs its own ticket.
- *Reverse:* a project-wide rule change, not this ticket.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Pitch and speed can be changed independently with Media3's sonic audio processor; speed changes can be applied piecewise with a speed provider | Guidance (API docs; verify names at the start) | [Media3 transformations](https://developer.android.com/media/media3/transformer/transformations) |
| Mobile editors offer a constant speed slider plus curve presets and a custom curve with draggable points | Assumption (product knowledge; help pages were not reachable here) | none |
| A speed range as wide as 0.1× to 10× is best set on a logarithmic scale | Assumption (usability reasoning; the slider's resolution at 1× is about 0.014× per dp, checked by arithmetic) | none |
| Dragging needs a single-pointer alternative; target size minimum 24 px and Memix's 48 dp rule | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| Non-text marks need 3:1; color can't be the only cue | Guidance | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Panel row heights (tile 48, slider 68, choice 56) | Evidence (read from the component code in the earlier specs) | `design/system/components/ToolPanel/README.md` |
