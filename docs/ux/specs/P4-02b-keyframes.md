# Keyframes: markers on the timeline, the keyframe bar and easing · P4-02b

Fourth Phase 4 spec (written after P4-04). P4-02a builds the model and the interpolation; this file is what the user sees and does. It reuses, and does not repeat:
- `P1-05-timeline.md`: lanes, rows, the clip look, zoom, the value bubble, snapping, TalkBack nodes.
- `P4-01b-overlay-layers.md` and `P4-16-clip-crop-rotate-flip-fit.md`: the on-frame gestures, Position panel, Opacity panel, snapping, one undo step per gesture, the bars (Keyframes is inserted into them).
- `P1-06-clip-edits-and-audio.md` and `P3-09-audio-effects.md`: the Volume panel, the reorder-bar precedent (a mode that replaces the tool bar), ToolPanel rules.
- `P1-10-text.md`: caption gestures and panels (captions are keyframeable).
- Component READMEs: ToolBar, ToolPanel, Timeline, OptionTile, Toast.

"Model needs" lists what the UI expects of P4-02a, so the engineer can tell me where it differs.

## Job
"Make it move": an overlay that slides in, a caption that pops and settles, a sticker that spins, a clip that fades out, a sound that ducks under a voice. PRD → Video editor → Keyframes: position, scale, rotation, opacity and volume, with easing. **One main action: none.** Export stays the one blue action (P1-12).

**Done when** (TICKETS): a keyframe is added, moved and deleted, each as one undo step. "Motion matches between preview and export" is P4-02a's, checked with this spec's reference renders (Measured). Easing changes and Clear all are also one step each.

**Fast path, with tap counts** (the playhead is moved by scrubbing the timeline, which is a drag and not a tap):
- **Slide an overlay in:** the overlay (1), Keyframes (2), scrub to 1 s, **drag the overlay** on the stage (3). That one drag makes two keyframes: where the overlay started and where it is now, so it glides between them (Interactions → Auto-keying). Two taps and a drag.
- **Fade it out at the end:** the overlay (1), Keyframes (2), scrub to the end, Opacity (3), drag to 0% (4).
- **Duck a sound under a voice:** the sound clip (1), Keyframes (2), scrub, Volume (3), lower it (4), scrub, Volume again.
- **No dragging at all:** Previous and Next, Add keyframe, and the Position, Opacity and Volume panels cover everything (Accessibility).

**Principles check.** Content first: the picture is never covered; markers live in their own row under the item. Sound first: the pinned "Add a meme sound" row stays (P4-01b); sound clips are keyframeable themselves. Fast path: auto-keying means a first move is two taps and a drag. One-handed: Previous, Add and Next are the bar's first three tools, in the thumb's reach. Forgiving: every change is one undo step, and Clear all is undoable. Accessible: markers are timeline nodes with actions, and the bar is the no-drag route. Five languages: tool labels measured at 200% (Copy).

**Color roles:** no blue (the Meme sounds lane keeps its own). White is selection: the selected keyframe, the playhead. Keyframes are `text-secondary` diamonds. The active look (`primary` on `primary-subtle`) marks "the playhead is on a keyframe" on the bar's Add and Remove tool.

## Decisions (D1 to D6)
- **D1. A keyframe holds the whole look of the item at that moment**, not one property. Position, scale, rotation and opacity (where the item has them) and volume (where it has sound) are stored together. One diamond type, one easing per diamond, one "Remove" that is clear. CapCut's mobile editor works with one diamond per element and property (its tutorials show a diamond beside each property, [CapCut help resources](https://www.capcut.com/resource/how-to-add-keyframes-in-capcut)); Memix chooses the smaller model for v1 because a meme animates a few things at once and a second row of diamonds per property does not fit a 28 dp lane. A user who animates only position gets scale, rotation and opacity held constant, which looks the same. **Cost:** you can't ease volume differently from position, or key opacity at times that differ from position's. Per-property keyframes are Later (Question 3).
- **D2. Keyframes live in their own row, under the item.** The selected item's lane grows a 32 dp **marker row**; outside the mode a small badge marks "this item has keyframes". Diamonds on the clip itself would sit on thumbnails and labels (a 28 dp text clip already holds its label).
- **D3. A mode, not a panel.** Moving a playhead and watching diamonds needs the timeline, which a ToolPanel covers. The **keyframe bar** replaces the tool bar (like reorder mode), so the timeline, the stage and the transport stay live; the value panels (Position, Opacity, Volume) and Easing still open over it as ordinary ToolPanels.
- **D4. Once an item has keyframes, every edit of it is a keyframe edit.** Changing the item at a time with no keyframe adds one there. (After Effects and CapCut work the same way. The alternative, a dead edit or a silent change of the whole animation, is worse.)
- **D5. Easing belongs to the keyframe and shapes the segment after it,** from this keyframe to the next. Five curves. The last keyframe has nothing after it.
- **D6. Which items:** every item with something to animate, below.

| Item | Position | Scale | Rotation | Opacity | Volume |
| --- | --- | --- | --- | --- | --- |
| Video overlay | yes | yes | yes | yes | yes |
| Photo overlay | yes | yes | yes | yes | no |
| Main video clip (its framing, P4-16) | yes | yes | yes | no | yes |
| Main photo clip | yes | yes | yes | no | no |
| Caption (text) | yes | yes | yes | no | no |
| Sticker in video (P4-12) | yes | yes | yes | yes, if the sticker has opacity | no |
| Meme sound, audio clip | no | no | no | no | yes |

A main clip has no opacity (P4-16 D4): a clip that fades in or out is a Phase 4 text-and-transition job, not here. Captions have no opacity today.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Keyframe bar** (replaces the tool bar) | The **Keyframes** tool of a selected item | Close (the bar's first button), system back (after a panel), Escape; selecting another item (that item's own bar shows) |
| Easing panel (ToolPanel) | The **Easing** tool, with the playhead on a keyframe that has a next one | Done, system back, Escape |
| Position, Opacity, Volume panels | Their tools in the keyframe bar | As in P4-01b and P1-06 |

- **Arrives from:** the clip bar of any item in the table above (Keyframes is inserted, see Layout). It acts on the **selected** item only. The tool is absent from a clip whose file is missing.
- **Back order** (P1-04, extended): a panel closes, then a gesture in progress is cancelled, then the keyframe bar closes, then the selection clears, then the editor closes.
- **The playhead stays where it is** on entry; playback keeps running if it was running (the user may want to watch the motion), except Previous, Next and Add, which pause (they jump).
- **Kept:** the keyframes, in the project (autosave, undo). Not kept: the bar, the selected keyframe, the open panel.

## Layout
Board: none (`VideoEditor.dc.html` has no keyframes). Everything below is new.

### The Keyframes tool in the bars
Icon `keyframe` (new, below), label "Keyframes" (`tool_keyframes`), the normal tool look. Name = visible label; click label "Animate this item over time" (TalkBack: "Keyframes, button, double-tap to animate this item over time"). It opens the keyframe bar and pauses nothing.

Final consolidated order of the Phase 4 pro tools in each bar (P4-04 set the rest; this adds Keyframes):

| Selected | Bar |
| --- | --- |
| Video overlay | Position · Opacity · Delete · Green screen · Split · Volume · Crop · Blend · Mask · Filters · **Keyframes** · Layer order · Move here · Duplicate · Detach audio |
| Photo overlay | Position · Opacity · Delete · Green screen · Split · Crop · Blend · Mask · Filters · **Keyframes** · Layer order · Move here · Duplicate |
| Main video clip | Split · Volume · Delete · Crop · Fill or Fit · Position · Filters · Green screen · Mask · **Keyframes** · Duplicate · Detach audio · Reorder · Replace |
| Main photo clip | Split · Delete · Crop · Fill or Fit · Position · Filters · Green screen · Mask · **Keyframes** · Duplicate · Reorder · Replace |
| Caption | Edit text · Style · Position · Delete · **Keyframes** · Split · Move here · Duplicate |
| Sound or audio clip | Volume · Split · Delete · Audio effects · **Keyframes** · Move here · Duplicate |

Why late: Keyframes is a pro tool that builds on the others (the user sets a look, then animates it), and Delete must stay in view at 360 dp (ToolBar rule). One or two swipes of the bar reach it; the clip's accessibility actions also list it. (The bars scroll anyway at ten to fifteen tools.)

### The keyframe bar (replaces the tool bar)
`surface`, `toolbar-height`, the `hairline` top border, as every bar. Left to right:

| # | Tool | Icon | What it does |
| --- | --- | --- | --- |
| 0 | **Close** (a leading 48 dp button, then a `hairline` divider; the clip-mode pattern) | `arrow_back` | Back to the item's own bar. Spoken "Close keyframes" |
| 1 | **Previous** | `arrow` turned left | The playhead jumps to the previous keyframe (scrolls into view), which becomes the selected keyframe. With none before: explains |
| 2 | **Add keyframe** / **Remove keyframe** (one tool; label and click label say what a tap does) | `keyframe` | Off a keyframe: adds one at the playhead holding the item's look right there. **On** a keyframe (the playhead within half a frame of one): the tool takes the **active look** (`primary` on `primary-subtle`) and reads "Remove keyframe"; a tap removes it |
| 3 | **Next** | `arrow` turned right | The mirror of Previous |
| 4 | **Easing** | `easing` | Opens the Easing panel for the keyframe at the playhead (or the selected one) |
| 5 | **Move here** | `move_here` | Moves the **selected keyframe** to the playhead (the non-drag route to retiming). Same label as the clip tool; its click label is "Move keyframe to playhead" |
| 6 | **Position** | `position` | The Position panel (P4-01b, P4-16, P1-10), values **at the playhead** (not on sound clips) |
| 7 | **Opacity** | `opacity` | The Opacity panel (overlays and stickers only) |
| 8 | **Volume** | `volume` | The Volume panel (clips with sound) |
| 9 | **Clear all** | `delete` in `text-secondary` | Removes every keyframe of this item, one step. The item keeps the look it has at the playhead. Not `danger` (undoable; the word is "Clear", not "Delete") |

- **What shows at 360 dp:** the Close button (48), the divider, then Previous, Add keyframe, Next and Easing whole (4 × 64 = 256, total about 312), and the fifth tool half: the thumb finds the whole motion tool set in the first four. Position, Opacity and Volume sit one swipe right and also work through the stage's gestures.
- **Tools that don't apply** (Position on a sound, Opacity on a main clip, Volume on a photo) are **absent**, never disabled (ToolBar rule). A tool that can't act now (Previous with none before it) keeps its look and explains on tap with a Toast.
- **Looks:** tools `text-secondary`; the active look only on Remove keyframe; labels keep room for two lines.

### Markers on the timeline
- **The marker row:** directly under the selected item's lane, `track-height` + `space-1` (32 dp) tall, a `surface-raised` band (`radius-sm`, 28 dp tall in the 32 dp row) spanning the item's time range, drawn **only while the keyframe bar is open** on that item. It holds the diamonds and, between the first and the last, a 1 dp `text-muted` line (decorative: "this is the animated span"). The lane below slides down by 32 dp (200 ms; reduce motion: at once).
- **A diamond** is a solid square rotated 45°, `keyframe-marker-size` (10 dp) in `text-secondary` (5.7:1 on `surface-raised`); the **selected** one is `keyframe-marker-size-selected` (14 dp) in `selection` white (14:1 on `surface-raised`). A keyframe at the playhead is selected. Touch target: the diamond's row is 32 dp tall and its target 48 dp wide (the documented non-video exception: items in 32 dp rows; equal routes are Previous, Next, Move here and the bar).
- **A keyed item's badge** (every keyed item, always, selected or not): a solid 10 dp diamond in `text` with a `stroke-hairline` `on-primary` edge (4.3:1 or more over any thumbnail, the content-mark rule), 2 dp from the clip's top-right corner, drawn only when the clip is at least 32 dp wide; a label is cut before it. It is a cue, not a control; the spoken description says the count.
- **Past the end of the video:** the part of the marker row past the end gets the `scrim` wash, as every lane. **Keyframes outside the item's trimmed range** are not drawn (they stay in the project, below).
- **Lane budget** (region 157 / 230 / 276 dp at 360 × 640 / 360 × 800 / 412 × 915; ruler 24 + main row 48 + the pinned "Add a meme sound" row 48 leave 37 / 110 / 156 dp for lanes, P4-01b): a selected main clip's marker row (32) fits at every size; a selected **overlay** (lane 32 + marker row 32 = 64 dp) fits from 360 × 800 up; **at 360 × 640 the lanes scroll** (37 dp of lane viewport, 64 dp of lane and marker row), and the lane view scrolls to show the selected item's lane and its marker row, with the main row scrolling away if it must (the P4-01b many-lanes rule keeps the sound pill pinned). Accepted: that size is the floor.

### The Easing panel (ToolPanel)
Title "Easing", Done (Ghost). Content, `space-4` apart:
1. **Five OptionTiles** (stacked layout: a curve picture over the label, scrolling edge to edge, `space-2` gaps): **Smooth · Linear · Ease in · Ease out · Hold**. One choice; the selected tile is white with `on-primary` (OptionTile). Selecting one is one undo step ("Easing") and **plays the segment once** from this keyframe to the next (it stops at the next keyframe or after 4 s; a tap on the stage stops it; a second tap on a tile starts again from the keyframe). Tapping the selected tile replays without a step. The curve pictures are **drawn from data** (a 20 × 20 dp box, 2 dp stroke, flat caps; the curve rising from lower left to upper right, **Hold** a step): not icon files.
2. A hint in `label` `text-secondary`: "How this keyframe moves to the next one."
- **Defaults:** every new keyframe is **Smooth** (ease in-out). Linear is a tap away. (Smooth starts and ends gently, which is what a meme's pop-in and settle read as; Assumption: product knowledge of motion graphics, test at the phone check.)
- **Budget** (content 133 / 206 / 252 dp): tiles 60 + 16 + the hint 32 (two lines at 100%) = **108 dp**: fits at every size. At 200% the hint wraps to three lines and the panel scrolls at 360 × 640.
- **Which keyframe:** the one at the playhead, else the selected one. If there is none, the tool explains; if it is the item's last, the tool explains (nothing comes after it).
- The panel covers the timeline and the bar (`coveredByModal`); closing returns focus to the Easing tool.

### Value panels while the item has keyframes
Position, Opacity and Volume open as before, with two differences:
- **Values are the item's values at the playhead** (the readouts follow the playhead when it moves while the panel is open; while playing they refresh at most 10 times a second).
- **A note** at the top of the panel's content, `label` in `text-secondary`: "This item has keyframes. Changes apply at the playhead." One step later it is clear what happened ("Keyframe added", below). The Opacity panel is 68 + 16 + 32 = **116 dp**, fits at 133; the Position panel (240 dp already) scrolls as before.
- **Ranges widen for keyed items:** Opacity runs **0% to 100%** (P4-01b: 10% for a still item; with keyframes the user can fade to nothing, the line "keyframes may fade to 0" in P4-01b); the **center may leave the frame** by half a frame on each side (−0.5 to 1.5; Question 1). A keyed item fully outside the frame or at 0% is **selected but not visible**, handled as P4-01b already does (nothing on the stage; select it on the timeline).

## Interactions
### Auto-keying (D4)
| The item has | An edit at the playhead (a stage gesture release, a Position step, an Opacity or Volume release, in or out of the bar) | Result |
| --- | --- | --- |
| **No keyframes**, keyframe bar closed | The edit | Static, as today. No keyframe is made |
| **No keyframes**, keyframe bar open, playhead within 0.1 s of the item's start | The edit | **One** keyframe at the playhead with the edited look |
| **No keyframes**, keyframe bar open, playhead later | The edit | **Two** keyframes in one step: at the item's **start** holding the look before the edit, and at the playhead holding the edited look. So the item glides from where it was to where it is now |
| **Keyframes**, the playhead on one (within half a frame) | The edit | That keyframe is **updated** |
| **Keyframes**, the playhead elsewhere | The edit | A **new** keyframe at the playhead holding the item's look there (interpolated) with the edit applied |

- The status after a **new** keyframe: a Toast-look line in the stage's one slot for 2 s, "Keyframe added at 0:02.0" (`keyframe_added`); the first time per item per visit, instead: "Keyframes on. Changes at other times add more keyframes." (`keyframe_on`, 4 s). An update says nothing (the stage moved). The new diamond fades in (120 ms) if the marker row is open.
- Everything is **one undo step**, named after the edit ("Position and size", "Opacity"); the keyframes made with it go with the step.
- The **limit** is 50 keyframes per item (`keyframes.maxPerItem`): at the limit, a new keyframe doesn't happen and a toast explains (`explain_keyframe_max`); updating one still works. Two keyframes can't be closer than one frame at 30 fps (33 ms): Add on or next to one is Remove.

### In the keyframe bar
- **Previous and Next** pause playback, jump the playhead to the keyframe (the timeline scrolls to keep it under the playhead), and **select** it. With none in that direction: toast `explain_keyframe_none_prev` or `_next`.
- **Add keyframe** adds at the playhead (one step "Add keyframe"); **Remove keyframe** removes the one at the playhead (one step "Remove keyframe"); the playhead keeps its place. Outside the item's span: toast `explain_keyframe_outside` ("Move the playhead onto the item first.").
- **Move here** moves the **selected** keyframe to the playhead (one step "Move keyframe"); keeps its look and easing. If another keyframe is within one frame of the playhead, or none is selected: toast (`explain_keyframe_move_none`, `explain_keyframe_move_blocked`).
- **Clear all:** one step "Clear keyframes"; the item keeps the look it has at the playhead as its static look; the bar stays; the marker row empties; the badge goes.

### On the timeline
- **Tap a diamond** (the marker row): the playhead jumps to it and it is selected (no step).
- **Drag a diamond** horizontally (no long-press; the row is its own): it retimes, the value bubble shows the new time (`timecode`, as trimming), snapping to the playhead, the item's start and end, and other keyframes' times; it **cannot cross a neighbor** (a hard stop one frame short) or leave the item's span; one SegmentTick per snap; **LongPress haptic** at pick-up. Release is one step "Move keyframe". The look and the easing go with it.
- **Tap an empty part of the marker row:** nothing.
- **A tap on another item** selects it and closes the keyframe bar (that item's bar shows).

### How other edits treat keyframes
| Edit | What happens to the keyframes |
| --- | --- |
| **Trim** the item | Kept in the project at their times; those outside the new range are hidden and **don't draw**, but the animation across the edge is the same as before (the item evaluates the whole curve), so trimming never changes how the remaining part moves. Undo or extending brings them back |
| **Move** (drag, Move here, main-track ripple) | The keyframes move with the item (they are stored relative to its start) |
| **Split** | Both pieces keep the keyframes inside their range, and **each gets a keyframe at the cut** holding the interpolated look there with the easing continuing, so the motion carries across the cut |
| **Duplicate** | The copy keeps them |
| **Delete, Undo of the add** | Go with the item |
| **Replace** (main clip) | Kept (they animate the slot, P1-16) |
| **Crop** | Untouched (crop, turns and mirrors are about the picture, not its framing, P4-16) |
| **Fit or Fill** (the tool, and the Canvas panel's) | P4-16 resets a clip's framing when it is tapped; on a clip with keyframes that would silently rewrite every keyframe's look. So on a **keyed main clip** the clip's Fit or Fill tool explains and changes nothing (`explain_fit_keyed`: "Clear this clip's keyframes first to change Fit or Fill."); the Canvas panel's Fit or Fill (every clip) skips keyed clips, sets the others, and shows the same toast once if any was skipped |
| **Ratio change** (P1-11) | Every keyframe's center follows the picture-layer rule (fully inside stays inside); the toast is the shared one |
| **Speed** (P4-09) | Keyframes scale with the clip (a note for that spec) |
| **Audio effects Fades** (P3-09) | Keyframed volume multiplies the fades: the order is P3-09's (Fades, then Volume) |

## How it draws (preview and export match)
Part of every draft's look, so fixed; in `design/tokens.json` → `keyframes`. The engine evaluates the item's look at each frame's presentation time in the preview and the export from the same plan.

| Rule | Value |
| --- | --- |
| Before the first keyframe, after the last | The first (last) keyframe's look is held |
| Between two keyframes, progress *t* | *t* = (time − left time) ÷ (right time − left time), then reshaped by the **left keyframe's easing** |
| **Smooth** | CSS `ease-in-out`: cubic Bézier (0.42, 0, 0.58, 1). At *t* = 0.5 the progress is 0.5 |
| **Linear** | progress = *t* |
| **Ease in** | cubic Bézier (0.42, 0, 1, 1) |
| **Ease out** | cubic Bézier (0, 0, 0.58, 1) |
| **Hold** | the left keyframe's look until the right keyframe's time, then a jump |
| Position | straight line between the two centers (canvas fractions); no curved paths in v1 |
| Scale | **geometric**: left × (right ÷ left)^progress, so 50% to 200% passes through 100% halfway (a linear 125% looks like a pause and a rush) |
| Rotation | **linear in the stored angle, never wrapped**: 0° to 360° is one full spin; the stage's twist keeps adding to the stored angle; panels show the angle reduced to −180° to 180° |
| Opacity, volume | linear in the value (volume as a level, not decibels) |
| Easing and the last keyframe | stored, no effect |
| Volume and effects | keyframed volume is the Volume stage of P3-09's chain; fades and effects are unchanged |

**Reference renders** (CLAUDE.md rule 5) in `docs/qa/references/P4-02-keyframes/`, 1080p frames, on the debug clip **`keyframe-check`** (Requests 7): a 10 s 9:16 main video with the current time burned in, and a lettered, 12 × 12-grid overlay at 40% size, plus a caption and a 1 kHz tone on a sound clip:

| File set | Setup | The frame or measure must show |
| --- | --- | --- |
| `ease-linear`, `ease-smooth`, `ease-in`, `ease-out`, `ease-hold` (frames at 25, 50 and 75% of the segment) | Overlay center (0.2, 0.2) to (0.8, 0.8) over 2 s | Position on the straight line at the formula's progress; **Hold** stays at the first center until the end, then jumps |
| `scale-geometric` | 50% to 200% over 2 s, linear easing, frame at 50% | The overlay at 100% (not 125%) |
| `rotate-spin` | 0° to 360° over 2 s, linear, frames at 25 and 50% | 90° and 180° |
| `opacity-fade` | 100% to 0% over 2 s, linear, frame at 50% | The overlay at 50% over the main video |
| `slide-in` | center (−0.3, 0.5) to (0.5, 0.5) | The overlay partly outside the frame at the start, centered at the end |
| `caption-pop` | a caption scale 50% to 100%, smooth | A caption whose size follows the curve |
| `split-continuity` | an overlay with two keyframes, split at the middle | The frames either side of the cut agree: no jump |
| `volume-ramp` (audio) | the tone clip 0% to 100% over 2 s, linear | Levels at 0.5, 1.0, 1.5 s of about −12, −6 and −2.5 dB below the final, within 0.5 dB, preview and export alike |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tool on the bar | `keyframe` icon and label | `tool_keyframes` |
| Keyframe bar open, no keyframes | The bar; the marker row empty; Previous and Next explain; Add keyframe reads "Add keyframe" | none |
| Playhead on a keyframe | Add becomes **Remove keyframe** in the active look; that diamond is white and large | `keyframe_remove` |
| Playhead between keyframes | Add keyframe; the nearest diamonds `text-secondary` | `keyframe_add` |
| A keyframe selected (by Previous, Next, a tap or a drag), playhead elsewhere | The selected diamond white; Move here acts on it | none |
| Marker dragged | The diamond follows; the bubble shows the time; snap ticks; neighbors are stops | none |
| Keyframe added by an edit | Status for 2 s; the diamond fades in | `keyframe_added`, `keyframe_on` |
| At 50 keyframes | A toast on a new one | `explain_keyframe_max` |
| Add outside the item's span | Toast | `explain_keyframe_outside` |
| Previous or Next with none that way | Toast | `explain_keyframe_none_prev`, `explain_keyframe_none_next` |
| Easing with no keyframe at or selected, or on the last | Toast | `explain_easing_none`, `explain_easing_last` |
| Easing panel | Five tiles, the current one white; a tap plays the segment | `easing_title`, `easing_*`, `easing_hint` |
| Value panel on a keyed item | The note; values at the playhead; ranges widened | `keyframe_note` |
| Fit or Fill on a keyed main clip | Toast; nothing changes | `explain_fit_keyed` |
| Clear all | Markers gone, the badge gone; one step | `edit_keyframe_clear` |
| Keyframe bar open, item then deleted or undone away | The bar closes | none |
| Keyed item, not visible at the playhead (outside the frame or 0%) | Nothing on the stage; selected on the timeline | none |
| Reorder mode | Keyframes tool absent (the reorder bar replaces the bars) | P1-06 |
| Missing file | The tool is absent | `editor_missing_media`, `editor_missing_overlay` |
| Offline, permission | Not applicable | none |
| Long content (5 languages, 200% font) | Tool labels wrap; es "Fotograma clave" takes three lines and widens the tool; the bar grows; panels scroll; the marker row holds no text | none |
| First run | The first auto-key shows `keyframe_on`; no coach mark | `keyframe_on` |
| An old draft (before P4-02a) | No keyframes, as before | none |

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Bar swap (item bar to keyframe bar) | 120 ms cross-fade | same |
| Marker row opens or closes (the lane below slides) | 200 ms | at once |
| A diamond appears or goes | 120 ms fade | same |
| Selected diamond (white, large) | 120 ms | same |
| Easing panel opens or closes | 200 ms slide | 120 ms fade (instant on Android) |
| The easing preview | The item plays its segment: this is the content, not interface motion; it runs only after a tap and stops at the next keyframe | still runs (it is user-started playback); the tap on the stage stops it |
| Toasts, status | 120 ms fade | same |

## Copy
English source; drafts for id, es, pt and hi as in P4-01b; the engineer machine-drafts the rest until P5. Terms: keyframe = *keyframe*, *fotograma clave*, *quadro-chave*, *कीफ़्रेम*; playhead follows the word in the shipped `tool_move_here_click` ("Move to playhead") in each language; easing = *easing*, *suavizado*, *suavização*, *ईज़िंग*. Budgets are characters, placeholders expanded.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_keyframes` | Keyframes | 16 | Keyframe · Fotogramas clave · Quadros-chave · कीफ़्रेम | Tool label (the plural: the tool holds several). es "Fotogramas" is 10 characters and widens the tool by about 15 dp; the bar grows at 200% |
| `tool_keyframes_click` | Animate this item over time | 40 | Animasikan item ini dari waktu ke waktu · Animar este elemento en el tiempo · Animar este item ao longo do tempo · इस आइटम को समय के साथ चलाएं | Click label ("double-tap to …"); 27 en. Never replaces the name |
| `keyframe_close_a11y` | Close keyframes | 24 | Tutup keyframe · Cerrar fotogramas clave · Fechar quadros-chave · कीफ़्रेम बंद करें | Spoken name of the bar's first button |
| `keyframe_prev` | Previous | 12 | Sebelumnya · Anterior · Anterior · पिछला | Tool label; click label `keyframe_prev_click` |
| `keyframe_prev_click` | Go to the previous keyframe | 40 | Ke keyframe sebelumnya · Ir al fotograma clave anterior · Ir ao quadro-chave anterior · पिछले कीफ़्रेम पर जाएं | Click label |
| `keyframe_next` | Next | 12 | Berikutnya · Siguiente · Próximo · अगला | Tool label; "Next" with no object is clear in the bar |
| `keyframe_next_click` | Go to the next keyframe | 40 | Ke keyframe berikutnya · Ir al siguiente fotograma clave · Ir ao próximo quadro-chave · अगले कीफ़्रेम पर जाएं | Click label |
| `keyframe_add` | Add keyframe | 24 | Tambah keyframe · Añadir fotograma clave · Adicionar quadro-chave · कीफ़्रेम जोड़ें | Tool label; two lines "Add / keyframe". The click label is `keyframe_add_click` "Add a keyframe at the playhead" |
| `keyframe_remove` | Remove keyframe | 24 | Hapus keyframe · Quitar fotograma clave · Remover quadro-chave · कीफ़्रेम हटाएं | Tool label when the playhead is on a keyframe; click "Remove the keyframe at the playhead" |
| `keyframe_add_click` | Add a keyframe at the playhead | 40 | Tambah keyframe di playhead · Añadir un fotograma clave en el cabezal · Adicionar um quadro-chave na posição atual · प्लेहेड पर कीफ़्रेम जोड़ें | Click label; use the playhead word of `tool_move_here_click` |
| `keyframe_remove_click` | Remove the keyframe at the playhead | 40 | Hapus keyframe di playhead · Quitar el fotograma clave del cabezal · Remover o quadro-chave da posição atual · प्लेहेड वाला कीफ़्रेम हटाएं | Click label; 33 en |
| `keyframe_easing` | Easing | 14 | Easing · Suavizado · Suavização · ईज़िंग | Tool label and panel title (`easing_title` is the same string). How a change speeds up and slows down |
| `keyframe_move_click` | Move keyframe to playhead | 40 | Pindahkan keyframe ke playhead · Mover el fotograma clave al cabezal · Mover o quadro-chave para a posição atual · कीफ़्रेम को प्लेहेड पर ले जाएं | Click label of Move here in this bar (the label is the shared `tool_move_here`) |
| `keyframe_clear` | Clear all | 14 | Hapus semua · Borrar todo · Limpar tudo · सब हटाएं | Tool label; click `keyframe_clear_click` "Remove every keyframe from this item" |
| `keyframe_clear_click` | Remove every keyframe from this item | 40 | Hapus semua keyframe item ini · Quitar todos los fotogramas clave de este elemento · Remover todos os quadros-chave deste item · इस आइटम के सभी कीफ़्रेम हटाएं | Click label; 36 en, es 53 (trim to "Quitar todos los fotogramas clave" when drafting) |
| `easing_smooth` | Smooth | 12 | Halus · Suave · Suave · सहज | Tile: starts and ends gently |
| `easing_linear` | Linear | 12 | Linear · Lineal · Linear · सीधा | Tile: constant speed |
| `easing_in` | Ease in | 14 | Awal lambat · Entrada suave · Entrada suave · धीमी शुरुआत | Tile: starts slowly, ends fast |
| `easing_out` | Ease out | 14 | Akhir lambat · Salida suave · Saída suave · धीमा अंत | Tile: starts fast, ends slowly |
| `easing_hold` | Hold | 12 | Tahan · Mantener · Manter · रुका हुआ | Tile: stays, then jumps at the next keyframe |
| `easing_hint` | How this keyframe moves to the next one. | 60 | Cara keyframe ini bergerak ke keyframe berikutnya. · Cómo pasa este fotograma clave al siguiente. · Como este quadro-chave passa para o próximo. · यह कीफ़्रेम अगले तक कैसे पहुँचता है। | Under the tiles; 40 en, id 50, es 43 |
| `keyframe_note` | This item has keyframes. Changes apply at the playhead. | 70 | Item ini punya keyframe. Perubahan berlaku di playhead. · Este elemento tiene fotogramas clave. Los cambios se aplican en el cabezal. · Este item tem quadros-chave. As mudanças valem na posição atual. · इस आइटम में कीफ़्रेम हैं। बदलाव प्लेहेड पर लागू होते हैं। | Top of the Position, Opacity and Volume panels when keyed; 54 en, es 68 (at the limit: trim a word when drafting) |
| `keyframe_added` | Keyframe added at %1$s | 30 | Keyframe ditambahkan di %1$s · Fotograma clave añadido en %1$s · Quadro-chave adicionado em %1$s · %1$s पर कीफ़्रेम जुड़ा | Status 2 s; %1$s is the time in `timecode`, "0:02.0"; 22 en |
| `keyframe_on` | Keyframes on. Changes at other times add more keyframes. | 70 | Keyframe aktif. Perubahan di waktu lain menambah keyframe. · Fotogramas clave activados. Los cambios en otros momentos añaden más. · Quadros-chave ativados. Mudanças em outros momentos adicionam mais. · कीफ़्रेम चालू। दूसरे समय पर बदलाव से और कीफ़्रेम जुड़ेंगे। | Status 4 s, once per item per visit; 56 en, es 68, pt 66 |
| `explain_keyframe_max` | That's the most keyframes this item can have. | 60 | Ini jumlah keyframe terbanyak untuk item ini. · Es el máximo de fotogramas clave de este elemento. · Esse é o máximo de quadros-chave deste item. · इस आइटम के लिए इतने ही कीफ़्रेम हो सकते हैं। | Toast; the limit is 50, not in the string; 45 en |
| `explain_keyframe_outside` | Move the playhead onto the item first. | 50 | Pindahkan playhead ke item dulu. · Mueve primero el cabezal sobre el elemento. · Mova antes a posição atual para o item. · पहले प्लेहेड को आइटम पर ले जाएं। | Toast; 38 en |
| `explain_keyframe_none_prev` | No earlier keyframe. | 40 | Tidak ada keyframe sebelumnya. · No hay un fotograma clave anterior. · Não há quadro-chave anterior. · इससे पहले कोई कीफ़्रेम नहीं। | Toast |
| `explain_keyframe_none_next` | No later keyframe. | 40 | Tidak ada keyframe berikutnya. · No hay un fotograma clave posterior. · Não há quadro-chave posterior. · इसके बाद कोई कीफ़्रेम नहीं। | Toast |
| `explain_easing_none` | Move the playhead onto a keyframe first. | 50 | Pindahkan playhead ke sebuah keyframe dulu. · Mueve primero el cabezal sobre un fotograma clave. · Mova antes a posição atual para um quadro-chave. · पहले प्लेहेड को किसी कीफ़्रेम पर ले जाएं। | Toast; 40 en |
| `explain_easing_last` | The last keyframe has no next one to move to. | 60 | Keyframe terakhir tidak punya keyframe berikutnya. · El último fotograma clave no tiene otro al que ir. · O último quadro-chave não tem outro para onde ir. · आख़िरी कीफ़्रेम के बाद कोई दूसरा कीफ़्रेम नहीं है। | Toast; 45 en, es 50 |
| `explain_keyframe_move_none` | Tap a keyframe, or use Previous and Next, to pick one first. | 70 | Ketuk keyframe, atau pakai Sebelumnya dan Berikutnya, untuk memilih dulu. · Toca un fotograma clave, o usa Anterior y Siguiente, para elegir uno. · Toque num quadro-chave, ou use Anterior e Próximo, para escolher um. · पहले कीफ़्रेम पर टैप करें, या पिछला और अगला से चुनें। | Toast; 59 en, es 70 |
| `explain_keyframe_move_blocked` | There's already a keyframe right here. | 50 | Sudah ada keyframe di sini. · Ya hay un fotograma clave aquí. · Já há um quadro-chave aqui. · यहाँ पहले से कीफ़्रेम है। | Toast |
| `explain_fit_keyed` | Clear this clip's keyframes first to change Fit or Fill. | 70 | Hapus keyframe klip ini dulu untuk mengubah Pas atau Penuh. · Borra primero los fotogramas clave de este clip para cambiar Ajustar o Rellenar. · Limpe antes os quadros-chave deste clipe para mudar Ajustar ou Preencher. · Fit या Fill बदलने के लिए पहले इस क्लिप के कीफ़्रेम हटाएं। | Toast; the words Fit and Fill match `canvas_fit` and `canvas_fill` exactly in each language; es is over 70 (74): shorten when drafting |
| `a11y_keyframe_node` | Keyframe %1$d of %2$d, at %3$s, %4$s | 60 | Keyframe %1$d dari %2$d, di %3$s, %4$s · Fotograma clave %1$d de %2$d, en %3$s, %4$s · Quadro-chave %1$d de %2$d, em %3$s, %4$s · कीफ़्रेम %1$d / %2$d, %3$s पर, %4$s | The marker's node; %3$s the spoken time (P1-04), %4$s the easing's name |
| `a11y_state_keyframed` | Keyframes: %1$d | 24 | Keyframe: %1$d · Fotogramas clave: %1$d · Quadros-chave: %1$d · कीफ़्रेम: %1$d | Appended to the clip's spoken description when it has keyframes |
| `a11y_keyframe_added` | Keyframe added | 24 | Keyframe ditambahkan · Fotograma clave añadido · Quadro-chave adicionado · कीफ़्रेम जुड़ा | Polite, after an add by the tool or an edit |
| `a11y_keyframe_removed` | Keyframe removed | 24 | Keyframe dihapus · Fotograma clave quitado · Quadro-chave removido · कीफ़्रेम हटा | Polite |
| `edit_keyframe_add` | Add keyframe | 24 | Tambah keyframe · Añadir fotograma clave · Adicionar quadro-chave · कीफ़्रेम जोड़ें | Edit names after "Undo: " (reuse `keyframe_add`) |
| `edit_keyframe_remove` | Remove keyframe | 24 | as `keyframe_remove` | Reuse |
| `edit_keyframe_move` | Move keyframe | 24 | Pindahkan keyframe · Mover fotograma clave · Mover quadro-chave · कीफ़्रेम खिसकाएं | |
| `edit_keyframe_easing` | Easing | 24 | as `keyframe_easing` | Reuse |
| `edit_keyframe_clear` | Clear keyframes | 24 | Hapus keyframe · Borrar fotogramas clave · Limpar quadros-chave · कीफ़्रेम हटाएं | |

Reused unchanged: `tool_position`, `tool_layer_opacity`, `tool_volume`, `tool_move_here`, `panel_done`, `position_*`, `layers_opacity`, `edit_transform_layer`, `edit_layer_opacity`, `canvas_fit`, `canvas_fill`, `editor_undo_toast`, `editor_missing_media`, `editor_missing_overlay`, `text_transform_bubble`.

Translator notes:
- **Keyframe** is a saved moment of an animation: the item's place, size, turn, see-through-ness and loudness at that time. Use the word your language's video editors use (many keep "keyframe").
- **Easing** is how a change speeds up or slows down on its way: Smooth, Linear, Ease in, Ease out, Hold. Short names; they sit under small curve pictures.
- **Playhead** is the line in the middle of the timeline: use the same word as the existing "Move to playhead" string.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

**Tool-label widths (computed from the P1-06 figures; check on the phone):** a tool label is `caption`, about 5.6 dp per Latin character at 100% and about 12 dp at 200%; one word of up to 8 characters fits a 64 dp item at 100% and about 4 at 200% (P4-01b). So at 100% only es "Fotogramas" (10, about 56 dp), pt "Quadros-chave" (hyphenates after "Quadros-") and es "Añadir fotograma clave" widen their items, by under 15 dp; at 200% every word of five or more characters widens its item and the bar grows taller. The keyframe bar shows about three tools at 200% (Close, Previous, Add keyframe); Next and Easing are one scroll away. Honest cost, as the Overlay tool's.

## Accessibility
- **Focus and TalkBack order:** the preview and transport as before; the timeline: the item, its **marker row** (a group "Keyframes", each diamond a node `a11y_keyframe_node`), then the keyframe bar left to right. Opening the bar moves focus to **Add keyframe**; closing it returns focus to the Keyframes tool.
- **Marker actions** (TalkBack's actions menu on a diamond): Go to, Move here (to the playhead), Remove, Easing. Double-tap goes to it (and selects it). So every drag has a no-drag route (WCAG 2.5.1, 2.5.7):

  | Drag | Alternative |
  | --- | --- |
  | Retime a diamond | Select it (tap, Previous, Next), move the playhead, **Move here** |
  | Moving, sizing or turning on the stage | The Position panel (P4-01b), also at the playhead |
  | The opacity slider | Steps of 10% by keyboard and TalkBack, and 0% is reachable |
  | Scrubbing the timeline to a time | The ruler and the transport's controls as P1-05 ("Move here", the playhead's accessibility actions) |

- **Announcements** (polite, never taking focus, SC 4.1.3): `a11y_keyframe_added` after Add or an auto-key; `a11y_keyframe_removed`; after Previous or Next the new node's description; a changed easing's name; toasts are announced.
- **The clip's spoken description** gains `a11y_state_keyframed` after P4-04's "Green screen" (and P4-16's states), in that order; never in the state description.
- **Touch targets:** every bar tool and panel control is 48 dp or more; the diamond's target is 48 dp wide in a 32 dp row (the documented exception, with the routes above); the easing tiles are at least 48 dp.
- **Contrast:** `text-secondary` diamond on `surface-raised` 5.7:1; the selected white one 14:1; the badge `text` on the clip with its `on-primary` edge 4.3:1 or more over any thumbnail (SC 1.4.11 needs 3:1); the connecting line `text-muted` on `surface-raised` 4.6:1 (decorative); tool labels `text-secondary` on `surface` 5.7:1; the active look `primary` on `primary-subtle` over `surface` 6.2:1; easing tiles `text` on `surface-raised` 12.4:1, selected `on-primary` on white 18.9:1. **State is never color alone:** the bar's tool changes its label; a keyframe's selection is a size change as well as white.
- **200% font:** tool labels wrap and widen, the bar grows; the Easing panel's tiles wrap their labels and the panel scrolls; the marker row has no text.
- **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never when a tool only explains; never for opening a bar or a panel). New ids for the PM to confirm (they are the edit names in the undo history):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Add keyframe (the tool), or an edit that **creates** a keyframe | `tool: "keyframe_add"` |
| `tool_use` | Remove keyframe | `tool: "keyframe_remove"` |
| `tool_use` | A diamond's drag release, or Move here | `tool: "keyframe_move"` |
| `tool_use` | An Easing tile that changes the easing | `tool: "keyframe_easing"` |
| `tool_use` | Clear all | `tool: "keyframe_clear"` |

- An edit that only **updates** an existing keyframe fires the edit's own id (`transform_overlay`, `position_overlay`, `overlay_opacity`, `volume`, …), as it does without keyframes. `value` is not used. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `keyframe-check` (Requests 7) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-02b/`.
1. **The bars:** an overlay's bar with Keyframes after Filters (scrolled), a caption's bar (Keyframes after Delete, in view), a sound clip's bar; Spanish at 200% ("Fotogramas clave").
2. **The keyframe bar:** no keyframes; the playhead on a keyframe (Remove keyframe in the active look); between keyframes; Close, Previous, Add, Next, Easing in view at 360.
3. **The marker row** on an overlay at 360 × 800 and 360 × 640 (the lane scrolled), with three diamonds, one selected; a main clip's marker row; the badge on an unselected keyed item; the `scrim` wash past the end of the video.
4. **Auto-key:** a first drag at 1.5 s on an unkeyed overlay: two diamonds (start and 1.5 s), the status line `keyframe_on`; a second drag at 3 s: a third diamond, the status `keyframe_added`.
5. **Drag a diamond** with the value bubble, a snap, and the stop at a neighbor (screen recording stepped frame by frame).
6. **The Easing panel** at 360 × 640 and 360 × 800; Hindi at 200% font.
7. **Value panels with the note:** Opacity (0% reachable), Position (scrolled), Volume on a sound clip.
8. **Split continuity:** a keyed overlay split at the middle; the frames either side of the cut.
9. **Fit or Fill on a keyed main clip:** the toast.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Add, move, delete, each one undo step:** Add keyframe, a diamond drag, Move here, Remove keyframe, an Easing tile, Clear all, and an auto-key from a gesture: perform each, then Undo once each time: exactly that edit goes (an auto-key undoes both keyframes and the edit).
- **Preview = export:** export `keyframe-check` at 1080p30; pull frames at the times of the reference table and compare with the preview: position within 1% of the width, scale within 1%, rotation within 1°, opacity within 3/255, volume levels within 0.5 dB; the same frames match the references in `docs/qa/references/P4-02-keyframes/`.
- **Trim does not change the motion:** trim the start of a keyed overlay by 1 s; the position at a frame inside the remaining range is unchanged (within 1%).
- **Old drafts open:** a draft saved before P4-02a has no keyframes; one with keyframes reopens with the same diamonds and easing.
- **Speed:** stage gestures on a keyed overlay keep janky frames under 5% (three 3 s drags, release-type build, the owner's phone); adding a keyframe shows its diamond within the P1-06 100 ms edit rule.
- **Leaks:** LeakCanary: open the bar, add, drag a diamond, change easing, clear, close the editor; repeat 5 times.

## Model needs (for the PM and P4-02a; I don't edit code)
| Need | Expected model |
| --- | --- |
| A keyframe | `Keyframe(timeUs: Long, look: …, easing: Easing)` on the item, a list sorted by time; `timeUs` relative to the item's start; `Easing` = SMOOTH, LINEAR, EASE_IN, EASE_OUT, HOLD. The UI writes **every property the item has** at each keyframe (D1); if P4-02a models per-property tracks, the UI writes them in lockstep and I change the wording, not the behavior |
| The look | Center x and y (fractions, range −0.5 to 1.5 when keyed), scale, rotation (degrees, unwrapped), opacity (0 to 1, keyed), volume |
| Which items | `MediaClip` (overlay and main), `TextClip`, sticker items (P4-12), sound and audio clips (volume only) |
| Evaluation | One pure function `lookAt(item, timeUs)` in `:core:domain`, used by the preview, the export plan and the stage's frame and handle, so the UI, the preview and the file can't disagree |
| Edits | `addKeyframe`, `removeKeyframe`, `moveKeyframe`, `setKeyframeEasing`, `clearKeyframes`, and the auto-keying wrapper around the transform, opacity and volume edits (the two-keyframe rule); pure, one `commit` each |
| Split | Inserts the interpolated keyframe at the cut on both pieces |
| Version | A `schemaVersion` bump with an identity migration; one bump with P4-02a's model |

## Requests to the principal mobile engineer
1. **Model and evaluation** as above; scale interpolated geometrically, rotation unwrapped, Bézier easing as the table, the same function in preview and export; verify Media3's per-frame matrix and transform inputs at the start (a custom `MatrixTransformation` or `GlEffect` with time).
2. **The keyframe bar** as a bar mode of the tool bar (the reorder bar's pattern): Close, the tools above, the active look on Remove keyframe, tools absent when they don't apply; back order extended.
3. **The marker row** in the timeline (32 dp row under the selected item, diamonds, line, the drag, snapping, `scrim` wash, lane slide) and the **badge**; draw in the draw phase; the lane viewport scroll to show lane and marker row.
4. **Auto-keying** in the edit use cases (D4 and the table), status lines through the existing one-slot banner, polite announcements.
5. **Easing panel** with `OptionTile` stacked tiles whose pictures are drawn from data (five curves) and the playing of one segment on a tile tap.
6. **Value panels:** values at the playhead, the note row, the widened ranges for keyed items.
7. **Debug draft `keyframe-check`** (the 10 s clip with burned-in time, the lettered overlay, a caption, a 1 kHz tone clip) and the reference scenes; frames saved to `docs/qa/references/P4-02-keyframes/`.
8. **Accessibility:** marker nodes with actions, the group "Keyframes", focus rules above; `coveredByModal` on the covered region for the Easing panel.
9. **Icons:** `keyframe` and `easing` (below). **Strings** in the five files. **Analytics:** five `EditorTool` values.
10. **Constants:** one commonMain `KeyframeCatalog` (limits, minimum spacing, easing curves, ranges, the auto-start threshold) with a comment pointing to this spec and `design/tokens.json` → `keyframes`; never through `MemixTheme`.

## Tokens and icons
- **New size tokens** (chrome, so in `MemixTheme`): `keyframe-marker-size` 10px (Kotlin `MemixSize.keyframeMarkerSize`) and `keyframe-marker-size-selected` 14px (`MemixSize.keyframeMarkerSizeSelected`), in `design/tokens.json`, `design/system/tokens.json` and DESIGN_SYSTEM → Size. **Handed to the principal engineer** (logged in `docs/ux/CHANGELOG.md`, "sync to design canvas"). No new color, radius or motion token (the row is `track-height` + `space-1`, its band `surface-raised`, the active look `primary`/`primary-subtle`).
- **New content constants** in `design/tokens.json` and `design/system/tokens.json` → `keyframes` (limits, easing curves, ranges, interpolation rules). Kotlin `KeyframeCatalog`. Not in `MemixTheme`: user content.
- **Two new icons** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review):
  - `keyframe` (Kotlin `MemixIcons.Keyframe`, `ic_keyframe.xml`): a solid 14 px diamond (a square turned 45°, 1 px corners), centered; all core, like `play`. It is also drawn at `keyframe-marker-size` on the timeline (the badge and markers use the same shape, drawn from data).
  - `easing` (Kotlin `MemixIcons.Easing`, `ic_easing.xml`): a stroked S-curve (20 px wide, 14 px tall, 2 px stroke, flat caps) rising from the lower left to the upper right, with a **solid 6 px diamond** (the core) on its middle, 1.4 px clear of the stroke. A monotone curve with a diamond must not read as `audio_effects` (a periodic wave with a round dot on its crest).
- **Reused icons:** `arrow` (Previous and Next, turned left and right), `move_here`, `position`, `opacity`, `volume`, `delete` (Clear all, in `text-secondary`), `arrow_back` (Close).
- **Pointer edits made with this spec:** ToolBar README (the keyframe bar; Keyframes in every bar), Timeline README (marker row, badge), DESIGN_SYSTEM (iconography, size tokens), the P4-01b and P4-16 pointers to the consolidated bar order.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Can a keyframed item leave the frame (slide in from outside)?**
- *Problem:* P4-01b keeps an overlay's center inside the canvas (0 to 1). A slide-in or fly-off, a classic meme move, needs the center outside the frame for a moment.
- *Considerations:* **A. Yes, for keyframed items: center from −0.5 to 1.5** (recommended; applied): the effect people expect works; a keyed item off-screen at some time is simply not drawn then; cost: an item can be dragged out of reach (the Position panel's Center and the timeline selection bring it back). **B. Keep 0 to 1:** nothing can be lost off screen, but a slide-in has to start at the edge with the item half visible, which looks wrong.
- *Reverse:* a range constant; no migration.

**2. Does the first edit in the keyframe bar also add a keyframe at the item's start?**
- *Problem:* a single keyframe makes the whole item hold that look, so the first drag at 2 s would move the item for its whole length and nothing animates.
- *Considerations:* **A. Yes, a start keyframe holding the old look is added in the same step** (recommended; applied): one scrub and one drag make a glide; the user can remove the start keyframe. Cost: one extra diamond the user didn't ask for. **B. No, one keyframe:** predictable, but the user must also add a keyframe at the start by hand before the drag, which most users won't find.
- *Reverse:* a behavior rule; no migration.

**3. One keyframe for all properties, or one per property?**
- *Problem:* a per-property model lets position, opacity and volume have their own times and easing; it also needs five marker rows, or a property picker.
- *Considerations:* **A. One keyframe holds everything** (recommended; applied): one diamond type, one easing, no property picker; fits a 32 dp row; cost: no independent timing per property (a fade at a different time than a move). **B. Per property:** pro-grade control; costs about three times the UI (a picker, five rows or chips) and a second spec round; Later, no ticket.
- *Reverse:* B can be added without changing A (a keyframe would then be a bundle of per-property keyframes at one time).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The common mobile flow is: select the element, put the playhead where the animation starts, tap the diamond, change a value; moving a marker changes timing; position, scale, rotation and opacity each get a diamond | Guidance (vendor resources and third-party tutorials; menus vary by version) | [CapCut: how to add keyframes](https://www.capcut.com/resource/how-to-add-keyframes-in-capcut), [Alphr: how to use keyframes in CapCut](https://www.alphr.com/capcut-how-to-use-keyframes/) |
| I could not confirm an auto-keyframe setting on CapCut mobile; auto-keying as D4 is After Effects' and desktop editors' convention (product knowledge) | Assumption | none |
| `ease-in-out` is the cubic Bézier (0.42, 0, 0.58, 1), `ease-in` (0.42, 0, 1, 1), `ease-out` (0, 0, 0.58, 1) | Guidance (standard) | [MDN: `<easing-function>`](https://developer.mozilla.org/en-US/docs/Web/CSS/easing-function) |
| Dragging needs a single-pointer alternative; target size minimum 24 px and Memix's own 48 dp rule | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| Non-text marks need 3:1; color can't be the only cue | Guidance | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Scaling geometrically feels even, a linear scale between 50% and 200% pauses then rushes | Assumption (motion-design practice; test at the phone check) | none |
| Smooth as the default easing reads as a pop-in and settle | Assumption (test at the phone check) | none |
