# Overlay layers: picture-in-picture video and photo · P4-01b

First Phase 4 spec. It adds one thing to the video editor and reuses everything else:
- `P1-04-editor-and-preview.md`: frame, top bar, preview stage, transport, undo/redo, toasts, back order.
- `P1-05-timeline.md` and `P1-06-clip-edits-and-audio.md`: lanes, selection, trim, move, split, delete, duplicate, Move here, the tool bars, Add media and Replace, the import sheet.
- `P1-10-text.md`: the on-frame gestures, the two-tone selection frame and handle, snapping, the value bubble, the Position panel. **Overlays use the caption gestures unchanged**, except where this file says otherwise.
- `P2-01-photo-editor-shell.md` and `P2-13-image-layers.md`: "size at scale 1", the scale range, hit testing, opacity, the ratio-change rule for pictures, the Position size ladder. The photo editor is the precedent for every layer rule here.
- `P1-11-canvas.md`: the ratio change and the frame edge.

The model and the engine are P4-01a (in build). This file says what the user sees and does; "Model needs" lists what the UI expects of P4-01a, so the engineer can tell me where it differs.

## Job
"React to a clip", "this vs that", "face in the corner": put a second video or photo on top of the main video, small or large, faded or not, and place it with the fingers. PRD → Video editor → Layers: unlimited video and photo overlays (picture-in-picture), opacity, stacking order. **One main action: none.** Add overlay is a tool; Export stays the one blue action (P1-12).

**Done when** (TICKETS): five overlays at 1080p preview at 30 fps or more; each edit is one undo step. The first half is a phone number (measured on the owner's phone at the end-of-build list; method at the end of this file). The second half is checked by hand with the edit list in Interactions → Undo steps.

**Fast path, with tap counts:**
- **A face in the corner:** Overlay (1), a photo or video in the picker (2); it lands small, selected, so a drag places it (3).
- **Fade it:** Opacity (1, with the overlay selected), drag the slider.
- **Put it behind the other one:** Layer order (1), Send back (2).
- **No dragging at all:** Position (1) is the full alternative (Size, Rotation, Move, Center).

**Principles check.** Content first: the frame and handle show only on the selected overlay; nothing is permanent chrome on the picture. Sound first: any sound stays two taps away even with five overlay lanes (the pill pins, Layout → Many lanes). One-handed: the Overlay tool is the fifth tool, in view at 360 dp; gestures work anywhere on the stage. Forgiving: every edit is one undo step; no confirmation anywhere. Accessible: every gesture has a button route (Accessibility). Five languages: the tool labels were measured at 200% (Copy).

**Color roles:** no blue appears (the meme-sound pill and sound clips keep theirs). White is selection: the frame and handle on the stage, the outline and trim handles on the timeline. Overlay clips on the timeline are `track-video`, as P1-05 and the Timeline README already say.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| System photo picker (videos and photos, one item) | The Overlay tool | An item is picked (it is copied, then lands), or back or nothing picked (nothing changes) |
| Import sheet (P1-02, blocking) | The copy takes over 300 ms | The item lands, or Cancel, or a failure result |
| Opacity panel (ToolPanel) | The Opacity tool of a selected overlay | Done, system back, Escape. The selection stays. |
| Position panel (ToolPanel) | The Position tool of a selected overlay | Same |
| Layer order panel (ToolPanel) | The Layer order tool of a selected overlay | Same |

- **Arrives from:** the main tool bar (nothing selected). The tool is not shown in an empty project (no clips, so no length to place against), as Voiceover is not (P3-08).
- **Back order** (P1-04) is unchanged: a panel closes, then a gesture in progress is cancelled (the overlay returns where it was), then the selection clears, then the editor closes.
- **Kept:** the overlay clip (its media copy under `files/media/<project id>/`, never uploaded), its place in time, lane, transform and opacity. Not kept: the open panel.
- **Picker rules** are P1-02's and P1-16's Replace picker: no permission, no explainer, **one item**, videos and photos together (`PickVisualMedia.ImageAndVideo`), taps ignored while it launches. Copying uses the import sheet after 300 ms with the P1-16 Replace wording (`import_title`, "Adding your media"); a video that can't be read or played gets P1-02's "None went in" result (Pick again). Cancel, close or nothing picked: the editor is unchanged, no step, no event.

## Layout
Board: `design/screens/VideoEditor.dc.html`. The board shows no overlay, no Overlay tool and no overlay lane; this spec adds them.

### Main tool bar: Overlay is the fifth tool
Final order of the video editor's main bar (ToolBar README updated): **Edit · Meme sounds · Text · Add media · Overlay · Voiceover** · then Stickers, Effects, Filters, Remove BG, Speed as their tickets arrive.
- **Why fifth, and why Voiceover moves to sixth** (P3-08 had it fifth, as the only tool after Add media at the time): Overlay is the pro tool people reach for most (PRD Layers row), and it is Add media's sibling (both put a video or photo into the project), so they sit side by side; a voiceover is the rarer job for a meme. Meme sounds stays second (Principle 2). Five tools fit whole at 360 dp (5 × 64 = 320), so Overlay needs no scroll; the sixth shows half. No user has seen the P3 order yet, so nothing is relearned.
- Icon `overlay` (new, below). Label "Overlay" (`tool_overlay`), the normal tool look (`text-secondary`). Name = visible label; click label "Add a video or photo on top" (TalkBack: "Overlay, button, double-tap to add a video or photo on top").
- **Tap:** pauses playback and opens the picker at once. No menu between: Text and Add media do the same. A tap while reorder mode is on can't happen (the reorder bar replaces the main bar).

### Overlay lanes on the timeline (P1-05 order, row 2)
- **Where:** directly under the main video row, **above** the text lanes. Each lane is one row, `track-height` + `space-1` (32 dp), with its clips `track-height` (28 dp) tall, a `surface` band from 0:00 to the end of the video like every non-video lane, and drawn only while it has items.
- **Look:** a thumbnail strip on `track-video` (P1-05: "overlays later"): square tiles 28 dp, the first at the clip's start, the last cut at its end minus the gap; a video shows the source frame at each tile's start time, a photo the same picture in every tile (center-cropped to a square). A tile not yet decoded shows its `track-video` fill and fades the image in over 120 ms. No label (the thumbnails are the cue), no duration badge (only the main track has one); the duration shows in the value bubble while trimming, as every non-video item.
- **Why 28 dp and not the main row's 40:** the two gray strips would otherwise read as one track. Height, the row beneath the main row and the spoken name "Overlay" tell them apart (color is not the only cue, WCAG 1.4.1).
- **Lane order is stacking order.** The lanes of the video editor read top to bottom in **paint order**: the main video is painted first, each overlay lane over the lanes above it, then text, then stickers. So **a lane lower in the list is in front of the lanes above it**, and captions (text lanes, below) are in front of every overlay, which is what P1-10 already promises ("above everything"). The model's order of OVERLAY tracks is the paint order, first = back. (Alternative rejected: lanes above the main row, as CapCut does. It would push the main row down the screen with every overlay and break P1-05's track order.)
- **Selected overlay:** the white outline with the `stroke-hairline` gap and trim handles outside, as every item (P1-05). Handle target `touch-target` wide and the full 32 dp row (the documented non-video exception: 28 dp items in 32 dp rows; equal routes are Split, Delete, "Trim to playhead" and the Position panel).
- **Past the end of the video:** the `scrim` wash on the part past the end; that part doesn't play or export (P1-05).
- **Missing file:** `track-video`, no thumbnails, the label "File missing" in `text` (P1-05 rule); selected: outline and handles, no badge.

**Lane budget, to size the lanes viewport** (100% font; timeline region = 157 / 230 / 276 dp at 360 × 640 / 360 × 800 / 412 × 915, P1-04): ruler 24 + main row 48 + the Add a meme sound row 48 leave **37 / 110 / 156 dp** for lanes, so **1 / 3 / 4 lanes** show whole. One overlay plus one caption lane fits at 800; five overlays never fit anywhere; **the lanes scroll vertically** (P1-05 "Many tracks").

**Many lanes: the "Add a meme sound" row pins (Principle 2).** P1-05 puts the row directly under the last meme-sound lane, so with five overlay lanes above it, it scrolls out of sight, and with an overlay selected the tool bar has no Meme sounds tool: adding a sound would take deselect, Meme sounds, add (3 taps). **New rule: while the lanes need to scroll, the row is pinned to the bottom of the timeline region** (48 dp, the pill centered on the playhead as ever), and the lanes scroll in the viewport above it (the numbers above already assume it). While everything fits, the row stays under the last sound lane, where it is today. The row moves only at the moment lanes begin to overflow, along the lane slide that caused it (200 ms; reduce motion: at once). Question 4 asks the owner; recommended yes.

### The selected overlay on the stage
- **Frame, handle, guides, bubble:** P1-10 → The selected text on the preview, unchanged: a rectangle `space-1` outside the overlay's box and turned with it, `stroke-selection` white plus a `stroke-hairline` `on-primary` edge (4.3:1 or more over any picture); the `canvas-handle-size` handle at the frame's bottom-right corner with `scale_rotate` at `icon-small`; two-tone snap guides; the "120% · 15°" bubble at the top of the stage (`text_transform_bubble`). Everything is clipped at the stage's edge.
- **One addition, for full-bleed overlays:** the handle's center is never closer than half its size to the stage edge, so the 24 dp handle always lies whole on the stage (the video editor's frame is inset `space-2`, 4 dp short of what a corner handle needs). If the frame's corner is outside the stage, the handle still moves to the nearest corner inside it (P1-10).
- **When it shows:** while the overlay is selected **and visible at the playhead** (the playhead is inside its time span). A selected overlay that isn't visible shows nothing on the stage; it is still selected on the timeline.
- **Frame edge** (P1-11): the `text-muted` hairline just outside the canvas frame shows while an overlay is being moved, sized or turned, and while the Canvas panel is open. Never otherwise.

### Overlay tool bar (an overlay is selected)
The P1-06 clip bar: Close clip tools, divider, tools.

| Selected | Tools, in order |
| --- | --- |
| Video overlay | **Position · Opacity · Delete · Split · Volume · Layer order · Move here · Duplicate · Detach audio** |
| Photo overlay | **Position · Opacity · Delete · Split · Layer order · Move here · Duplicate** |
| Overlay whose file is missing | **Delete first**, then the usual order without Position and Opacity (they act on nothing visible) |

- **Why this order:** Position is the non-gesture route for move, size and turn (WCAG 2.5.1, 2.5.7), so it is always one tap away, as on photo layers; Opacity is the next most used; Delete is third and stays in view at 360 dp (ToolBar rule: about four and a half tools show). Split follows because cutting a reaction clip short is the next most common job. P4-16 inserts **Crop** after Volume (and after Split on a photo overlay).
- **No Replace** (P1-06: overlays have none): delete and add again. **No Lock, Hide or Layers panel:** the timeline's lanes are the layers list, and locked tracks are L-02. **No original-audio toggle** here (that is the main track's).
- **Volume** on a video overlay: the P1-06 panel with the label "This clip" and **without** the "Mute original audio of every clip" toggle (that toggle belongs to the main track). **Detach audio** works as P1-09 (the sound goes to an Audio lane, the overlay keeps its picture).
- Split, Delete, Duplicate, Move here and Volume are P1-06's, with the overlay rules below. Look: tools `text-secondary`, Delete `danger` with its word, every label keeps two lines of room (ToolBar rule).

### Opacity panel (ToolPanel)
Title "Opacity", Done. One **Slider** (label "Opacity", readout the percent in `timecode`), **10% to 100%**, default 100%, snaps to 100% within ±3% with one SegmentTick, keyboard and TalkBack steps of 10% landing on multiples of 10 so 100% is reachable (Slider rules). Live on the stage while dragging; one undo step on release ("Opacity"). Why 10% and not 0%: a 0% overlay is invisible and can't be hit on the stage, the state P2-01 already ruled out; fading to nothing is what keyframes are for (P4-02 may key opacity down to 0).
- **Budget** (region 229 / 302 / 348 dp; content = region − 96: top and bottom padding 32, title row 48, gap 16 = **133 / 206 / 252 dp**): one Slider row is about 68 dp. It fits at every size, with room at 800 and up.
- **Playback keeps going** if it was playing (the point is to see the blend), as the Volume panel. If the playhead is outside the overlay's span, it **jumps to the overlay's start** when the panel opens, so the change can be seen (P1-10: Style and Position do the same). Inside the span, it stays where it is.

### Position panel (ToolPanel)
P1-10's panel for overlays, unchanged in rows: **Size** (minus, readout, plus), **Rotation** (rotate left, readout, rotate right), **Move** (four arrows), **Center** (Secondary button). Differences from text:
- **Size ladder:** the photo layers' (P2-01): 10 · 15 · 25 · 33 · 50 · 67 · 75 · 90 · 100 · 110 · 125 · 150 · 175 · 200 · 250 · 300 · 400 · 500%. **100% means the overlay fitted inside the frame** (Size at scale 1 below), so a freshly landed overlay reads 50%.
- **Limits explain on tap:** "This is the smallest size." at 10%, "This is the biggest size." at 500% (reused), and a Move arrow with the center already at that edge: "This overlay can't go further this way."
- **Undo steps:** each tap, each hold (on release) and Center is one step, "Position and size".
- **Budget:** Size 48 + Rotation 48 + Move 48 + Center 48 + three gaps of 16 = **240 dp** of content, against 133 / 206 / 252: **it scrolls at 360 × 640 (the first two rows show whole, 112 dp, and 5 dp of the third) and at 360 × 800 (three rows show, 176 dp, the Center button is cut by the edge), and fits whole at 412 × 915.** Accepted: those two sizes are the floor, the scroll is the ToolPanel rule, and Center is the least-used row. Rows wrap their controls under the label at 200% font (P1-10).
- Playhead rule as the Opacity panel.

### Layer order panel (ToolPanel)
Title "Layer order", Done. The two actions first, so 200% font can't push them under the fold, then one hint line:
1. Two **Secondary** buttons in a wrapping row: **Bring forward** and **Send back** (`space-2` apart).
2. Hint in `label` `text-secondary`: "Changes which overlay is on top where they overlap."
- **What a tap does** (one undo step, "Layer order"): **Bring forward** swaps the overlay's place in the stack with the **nearest overlay in front of it that shares a moment with it** (their time spans overlap), so it ends up directly in front of that overlay. **Send back** does the same toward the back, with the nearest overlay behind it that shares a moment. One tap passes one overlay, so repeated taps climb the stack one step at a time. If no overlay shares a moment in that direction, the button explains (below) and nothing changes.
- **How the lanes follow** (the engineer's rule; the timeline must show the order): the overlay moves to a lane directly after (forward) or before (back) the lane of the overlay it passes, taking the first lane there that is free for its whole span, otherwise a new lane is made at that place. Lanes that end up empty go (P1-05 rule). Lanes slide 200 ms. The lane viewport scrolls to keep the clip in view.
- **Buttons look like any Secondary button** (`surface-raised`, `text`), on the panel's `surface`. The panel itself is the one Secondary-on-`surface` case, so no Secondary-on-`surface-raised` problem (P3-14 lesson).
- **Budget:** two buttons side by side about 250 dp in English (136 + 8 + 107) and 281 in Spanish, wrapping to two rows only in Portuguese (339 > 328); then the hint, up to two lines (32 dp). One row case: 48 + 16 + 32 = 96. Two-row case (pt): 48 + 8 + 48 + 16 + 32 = 152. Against 133 at 360 × 640 the Portuguese case scrolls by 19 dp, under the hint only; the buttons always show. Fits at 800 and up.
- **At the ends:** "No other overlay is in front of this one." / "No other overlay is behind this one." (toast, the P1-06 explain rule). The button keeps its look.

## Rules behind the layer
### What lands
- **Kind and clip:** a `MediaClip` on an OVERLAY lane. A photo or a video, from the gallery, copied into the project like every clip.
- **When:** at the playhead (the new clip's start), in time with these lengths:

| Source | Length |
| --- | --- |
| Photo | 3 s (`overlay.landing.photoDurationMs`, the length of a still everywhere else) |
| Video | its own length |

  If the length would run past the end of the video, it is **cut to end there** (the clip's out-point moves; the rest of the file is still there and the end handle can pull it back out). If that leaves **under 1 s**, the clip lands **ending at the end of the video** instead, as the smaller of its length and 3 s (start = the larger of 0:00 and end − that), the same landing rule as sounds and captions. No toast: what lands is on the timeline.
- **Size at scale 1** is the photo layers' rule (P2-01): the picture, with any crop or turn (P4-16), **fitted inside the frame**, recomputed from the canvas shape every time it is drawn; a picture within 1% of the canvas shape covers it. **It lands at 50%** (`overlay.landing.scale` 0.5). A picture-in-picture is small by definition, and a full-size overlay would hide the main video, the one thing the user needs to see while placing it (Question 1 asks the owner; the photo editor lands layers at 100% on the owner's explicit words).
- **Where:** the first free slot of the center (0.5, 0.5), lower right (0.7, 0.7), upper right (0.7, 0.3), lower left (0.3, 0.7), upper left (0.3, 0.3). A slot is taken when the center of an overlay visible at the playhead is within 0.1 of it; with all five taken, the center. Rotation 0°, opacity 100%.
- **Which lane:** **the first lane that is free for the clip's whole span and lies below every lane that holds an overlapping item**, otherwise a new lane under the last overlay lane. This puts the newest overlay in front of every overlay it shares a moment with, as a new layer is everywhere in Memix (a text lands at the front too). Not "the first free lane" (that would slip it behind an older overlay).
- **Selection and sound:** it becomes the selection; the tool bar switches to the overlay bar; the frame and handle show on the stage. **A video overlay's own sound plays at 100%** (what you see is what you hear; the Volume tool is one tap away). Question 3.
- **Feedback:** the clip bonks on its lane (`duration-bonk`), a new lane slides in over 200 ms and the lanes scroll to show it, TalkBack hears "Overlay added". No haptic (Confirm belongs to sounds).
- **Undo:** one step "Add overlay" removes the clip (and its lane); the media copy stays while undo can bring it back, as every copy.

### Gestures on the stage (P1-10, with these differences)
| Gesture | On | Result |
| --- | --- | --- |
| Tap | An overlay visible at the playhead, **not selected** | Pauses; selects it (timeline selection, overlay bar). The front-most overlay under the finger wins. |
| Tap | The selected overlay | **Play or pause** (not "edit": an overlay has no editing mode, and a big overlay would otherwise make a tap on the stage a dead area) |
| Tap | A caption over an overlay | The caption (text lanes are in front; P1-10) |
| Tap | Anywhere else on the stage | Play or pause (P1-04); the selection is cleared by the P1-06 ways, never by a stage tap |
| Drag (one finger) | An overlay | Pauses, selects it if it isn't, moves it 1:1 with the finger |
| Drag | The handle | Scales and turns the overlay around its center |
| Two fingers (pinch, twist, pan) | Anywhere on the stage, an overlay selected | Scale, rotation and position together (the fingers needn't touch it) |
| Long-press | An overlay | Same as a tap |

- **Hit order:** the selected item's handle, then captions, then overlays front to back. Only overlays whose span contains the playhead can be hit. An overlay's hit area is its box turned with it, **grown to at least `touch-target` in each direction**; transparent parts of a picture count (P2-01). Reaching an overlay beneath another is done from the timeline: tap its clip, which selects it without moving the playhead (P1-05), so it shows on the stage when the playhead is inside it; Position, Opacity and Layer order jump the playhead there when it isn't.
- **Snapping** (one SegmentTick per engagement, with its guide): the overlay's center to the canvas's vertical and horizontal center lines within `space-2`; **its sides flush with the canvas edges within `space-2`** (new here: a corner picture-in-picture is the common layout; P2-01 left edge snapping for later, and the cost is small); rotation to 0°, 90°, 180°, 270° within 4°; size to 100% within 3%.
- **Limits** (nothing toasts while dragging; the overlay simply stops): size 10% to 500% of "fitted inside", the center within 0…1 of the canvas on both axes.
- **Live feedback.** While a finger is down the overlay must follow it at 60 fps, with **no double image**: the engine's own picture of that overlay is hidden for the gesture and a stand-in of its current frame follows the fingers; on release the engine renders the committed transform and the stand-in goes with **no visible jump or flash** (P1-10's rule for captions). How is the engine's call (P4-01a: a live transform input, or the stand-in with the engine layer faded out); the QA check is a screen recording stepped frame by frame around the touch-down and the release.
- **One gesture is one undo step**, committed on release: "Position and size". Undo and redo are ignored while a finger is down. Any touch on an overlay pauses playback first.
- **While a panel is open** (Opacity, Position, Layer order) the stage gestures stay live and the panel's readouts follow after each release. **While the Canvas panel is open** or a caption is being typed, stage touches on overlays do nothing (P1-11 and P1-10 rules: the panel is about the whole frame; the typed words aren't in the project yet).

### On the timeline (P1-06, with these notes)
- **Trim:** handles at both ends; a video overlay stops at the source's start and end (one tick), a photo has no limit; at least 0.1 s; a neighbor on the same lane is a hard stop; snapping to the playhead, 0:00, the end, main-clip boundaries and other items' edges. The preview shows the main video as it is while trimming (overlay clips don't scrub the stage in P4-01b).
- **Move:** long-press and drag. Horizontally sets the start; **vertically changes the lane, which changes the stacking order** (the lane you drop on, or the next free one below it, P1-06). Dropping where it would overlap on that lane lands it on the next lane below that is free, or a new one. A value bubble shows the new start time. Move here sets the start to the playhead with the same lane rule as landing.
- **Split:** two clips with the same transform, opacity, crop and sound settings, meeting at the playhead; selection to the right one. **Delete:** the clip goes, an emptied lane goes, the selection clears, the main bar returns. No confirmation.
- **Duplicate:** the copy goes right after the original on the same lane if that is free for its whole length; otherwise on the first lane free below, otherwise a new lane (the landing rule). **When the copy ends up overlapping the original in time, it is offset 3% of the canvas's shorter side right and down** (`overlay.duplicateOffsetOfShortSide`, P2-01's offset), so the user can see something happened; it is selected and bonks.
- **Main track ripple does not move overlays** (P1-06 owner trial: sounds, text and now overlays keep their times). An overlay lined up with a main-clip moment can drift if earlier clips change; undo and Move here fix it. Revisit with the sounds decision at the P1 test.
- **Reorder mode** (main clips): overlay lanes get the `scrim` wash and don't respond, like every other lane.

### Ratio change
P1-11 → Ratio change and P2-01 → Ratio change, for pictures: every overlay keeps its `centerX`, `centerY`, scale and rotation; its fitted size at scale 1 is recomputed from the new shape; **an overlay that was fully inside the old frame stays fully inside** (moves in just far enough to touch the edge, **margin 0**; too big, it shrinks first, never below 10%); one placed partly outside keeps its center and scale. The shared toast reads "Layers moved to stay inside the frame." (P1-11's `canvas_layers_moved` now says layers, not text).

### Rendering rules (preview and export match)
Part of every draft's look, so fixed; in `design/tokens.json` → `overlay`.
| Rule | Value |
| --- | --- |
| Paint order | main video and its background, overlay lanes top to bottom (back to front), text, stickers, effects over everything below them (P1-05 order) |
| Transform | center and scale as fractions of the canvas, rotation clockwise, about the overlay's own center |
| Opacity | the whole overlay (picture and its edges) multiplied once by its opacity; a photo with transparency keeps its own alpha |
| Blur background | blurs the **main** clip only; overlays are never part of it |
| Missing file | the overlay is **not drawn**, in preview and export alike (a black rectangle over the video would be worse than nothing); a main clip's missing file still plays black (P1-04) |
| Past the end | not drawn, not exported |
| Many video overlays | with more than five video overlays visible at the same moment, the preview may render at a reduced size (the Blur precedent, P1-11 build note); export is always full quality; no message, because a slightly softer preview is the cheaper surprise and nothing in the file changes |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default, no overlays | Main bar with Overlay fifth; the timeline as today | `tool_overlay` |
| Empty project (defensive, P1-04) | Add media only; no Overlay | `tool_add_media` |
| Picker open, nothing picked | The system picker; nothing changes on return | (system) |
| Copying (over 300 ms), no space, none went in, no picker app | P1-02's sheets with P1-16's one-item wording; the editor unchanged under them | `import_title`, `import_*` |
| Landed, video | Clip on a new lane under the main row (200 ms slide, bonk); on the stage at 50%, frame and handle; overlay bar; "Overlay added" | `a11y_overlay_added`, `tool_*` |
| Landed, photo | The same; 3 s long | same |
| Landed near the end | Ends at the end of the video | none |
| Landed where a slot is taken | The next slot (lower right first) | none |
| Overlay selected, visible at the playhead | White frame and handle on the stage; outline and handles on the timeline | none |
| Overlay selected, not visible at the playhead | Nothing on the stage; Position, Opacity and Layer order jump the playhead to its start | none |
| Two overlays overlap | A tap picks the front one; the other from the timeline | none |
| Moving, sizing, turning | Overlay follows (stand-in), frame edge and guides, bubble "% · °" | `text_transform_bubble` |
| Opacity panel | One slider, the blend live on the stage | `layers_opacity`, `panel_done` |
| Position panel | P1-10's rows with the photo layers' size ladder | `position_*`, `explain_text_smallest`, `explain_text_biggest`, `explain_overlay_edge` |
| Layer order panel | Two buttons and a hint | `tool_layer_order`, `layer_order_forward`, `layer_order_back`, `layer_order_hint` |
| Layer order at an end | Toast; nothing changes | `explain_layer_order_front`, `explain_layer_order_back` |
| Overlay past the end of the video | The wash on the part past the end | `a11y_state_past_end`, `a11y_state_after_end` |
| Overlay's file missing | Lane clip says "File missing"; not drawn; a toast once per open; tools: Delete first | `clip_missing_file`, `editor_missing_overlay` |
| Many overlay lanes | Lanes scroll under the pinned ruler; the Add a meme sound row is pinned at the bottom | `timeline_add_meme_sound` |
| More than five video overlays at once | The preview may be softer; no message | none |
| Canvas panel open | Stage touches on overlays do nothing; frame and handle hidden | P1-11 |
| Typing a caption | Overlays inert | P1-10 |
| Reorder mode | Overlay lanes washed | P1-06 |
| Undo or redo | Applies at once; the selection stays if the clip exists; a panel on a clip that no longer exists closes; a toast names the step | `editor_undo_toast`, `edit_*` |
| Long content (5 languages, 200% font) | Tool labels wrap and widen (Spanish and Portuguese "Superposición", "Sobreposição" are one word of 12 to 13 characters: the tool widens by about 10 dp, the ToolBar rule); panels scroll; the lanes hold no text | none |
| Offline, permission | Don't apply: everything is on the phone; the picker needs no permission | none |
| First run | Same as every run; no coach mark. The tool's label is the invitation | none |

## Interactions
- **Press feedback:** the token pressed states, 120 ms; nothing moves.
- **Haptics:** SegmentTick for each snap engagement (gestures and the opacity slider's 100%); LongPress when a clip lifts on the timeline. Landing has none.
- **Undo steps** (each is one, with its edit name): Add overlay; a gesture ("Position and size"); each Position panel tap, hold (on release) and Center ("Position and size"); an Opacity release ("Opacity"); a Bring forward or Send back tap ("Layer order"); Trim; Move (a drag, or Move here); Split; Delete; Duplicate; a Volume release; Detach audio.
- **Keyboard (basic, P1):** Tab reaches the Overlay tool, every panel control and the stage's overlay nodes; Enter activates; Escape is system back. Arrow-key nudging is Later (L-01).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens or closes | 200 ms slide (ToolPanel) | 120 ms fade (instant on Android) |
| Overlay lands | The bonk on its clip (`duration-bonk`); a new lane slides in 200 ms; the pill doesn't draw during the slide and fades in at its new place (P1-05) | 120 ms fade-in |
| Selection frame and handle | 120 ms fade | same |
| Frame edge, guides, bubble | 120 ms fade | same |
| Main ↔ overlay bar | 120 ms cross-fade | same |
| Lanes slide (Layer order, delete, move) | 200 ms | 120 ms fade |
| Opacity, Position and Layer order results on the stage | At once | same |
| Pill moving to the bottom when lanes overflow | With the lane slide | at once |
| Toasts | 120 ms fade | same |

## Copy
English source. Drafts for id, es, pt and hi are given so nothing is left to guess; the engineer machine-drafts the rest until P5. Terms follow what the app already ships: layer = id *lapisan*, es *capa*, pt *camada*, hi *लेयर*; frame = *bingkai*, *marco*, *quadro*, *फ़्रेम*; overlay = *Overlay*, *Superposición*, *Sobreposição*, *ओवरले* (`track_overlay`). Budgets in characters; counts are with placeholders expanded.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_overlay` | Overlay | 14 | Overlay · Superposición · Sobreposição · ओवरले | Tool label, two lines of about 10. The es and pt word is one 12 to 13 character word and widens the tool (ToolBar rule). Same word as `track_overlay`. A video or photo placed on top of the main video. |
| `tool_overlay_click` | Add a video or photo on top | 40 | Tambahkan video atau foto di atas · Añadir un video o foto encima · Adicionar um vídeo ou foto por cima · ऊपर एक वीडियो या फ़ोटो जोड़ें | Click label (TalkBack: "double-tap to …"); never replaces the name |
| `tool_layer_order` | Layer order | 18 | Urutan lapisan · Orden de capas · Ordem das camadas · लेयर का क्रम | Tool label and panel title; "layer" as in `tool_layers`. pt takes two lines ("Ordem das / camadas"). Not "Reorder" (`tool_reorder`, a different tool: main clips in time). |
| `layer_order_forward` | Bring forward | 18 | Majukan · Adelantar · Para a frente · आगे लाएं | Button: one step toward the front. Not "forward in time". |
| `layer_order_back` | Send back | 18 | Mundurkan · Atrasar · Para trás · पीछे भेजें | Button: one step toward the back |
| `layer_order_hint` | Changes which overlay is on top where they overlap. | 70 | Mengubah overlay mana yang di atas saat tumpang tindih. · Cambia qué superposición queda arriba donde se cruzan. · Muda qual sobreposição fica por cima onde elas se cruzam. · जहाँ ओवरले एक-दूसरे पर आते हैं, वहाँ कौन-सा ऊपर रहे, यह बदलता है। | 52 en; the longest draft is about 62 |
| `explain_layer_order_front` | No other overlay is in front of this one. | 50 | Tidak ada overlay lain di depan overlay ini. · Ninguna otra superposición está delante de esta. · Nenhuma outra sobreposição está na frente desta. · इसके आगे कोई और ओवरले नहीं है। | 41 en; es 48, pt 48, id 44: under 50. Toast. |
| `explain_layer_order_back` | No other overlay is behind this one. | 50 | Tidak ada overlay lain di belakang overlay ini. · Ninguna otra superposición está detrás de esta. · Nenhuma outra sobreposição está atrás desta. · इसके पीछे कोई और ओवरले नहीं है। | 36 en; id 46, es 47: under 50 |
| `explain_overlay_edge` | This overlay can't go further this way. | 60 | Overlay ini tidak bisa bergerak lebih jauh ke arah ini. · Esta superposición no puede ir más lejos así. · Esta sobreposição não pode ir mais longe assim. · यह ओवरले इस तरफ़ और आगे नहीं जा सकता। | 39 en; id 54. Position panel's Move arrows. |
| `editor_missing_overlay` | An overlay's file is missing, so it isn't shown. Delete it to fix it. | 90 | File sebuah overlay hilang, jadi tidak ditampilkan. Hapus overlay itu untuk memperbaikinya. · Falta el archivo de una superposición, así que no se muestra. Elimínala para arreglarlo. · O arquivo de uma sobreposição está ausente, então ela não aparece. Exclua-a para corrigir. · एक ओवरले की फ़ाइल नहीं मिली, इसलिए वह नहीं दिखेगा। ठीक करने के लिए उसे हटाएं। | 69 en; es 88, pt 90 (at the limit: trim a word in pt when drafting), id 88. Once per open; if a main clip is also missing, `editor_missing_media` shows and this one waits for the next open. |
| `a11y_overlay_added` | Overlay added | 24 | Overlay ditambahkan · Superposición añadida · Sobreposição adicionada · ओवरले जुड़ गया | Announced politely after landing |
| `a11y_overlay_node` | Overlay, %1$s, layer %2$d of %3$d | 44 | Overlay, %1$s, lapisan %2$d dari %3$d · Superposición, %1$s, capa %2$d de %3$d · Sobreposição, %1$s, camada %2$d de %3$d · ओवरले, %1$s, लेयर %2$d / %3$d | The overlay on the stage. %1$s = `import_item_video` or `import_item_photo`; layer 1 = the front-most among the overlays visible at the playhead. 28 en with "Video". |
| `a11y_overlay_item` | Overlay, %1$s, %2$s long, starts at %3$s | 100 | Overlay, %1$s, panjang %2$s, mulai pada %3$s · Superposición, %1$s, dura %2$s, empieza en %3$s · Sobreposição, %1$s, dura %2$s, começa em %3$s · ओवरले, %1$s, %2$s लंबा, %3$s पर शुरू | The overlay's clip on the timeline; %2$s and %3$s are P1-04's spoken times |
| `edit_add_overlay` | Add overlay | 24 | Tambah overlay · Añadir superposición · Adicionar sobreposição · ओवरले जोड़ें | Edit name after "Undo: " |
| `edit_overlay_order` | Layer order | 24 | Urutan lapisan · Orden de capas · Ordem das camadas · लेयर का क्रम | Edit name |

Reused unchanged: `tool_position`, `position_*`, `tool_layer_opacity`, `layers_opacity`, `tool_delete`, `tool_split`, `tool_volume`, `tool_move_here`, `tool_duplicate`, `tool_detach_audio`, `panel_done`, `track_overlay`, `clip_missing_file`, `import_*`, `import_title`, `import_item_video`, `import_item_photo`, `text_transform_bubble`, `a11y_text_transformed`, `a11y_text_position`, `a11y_layer_order`, `explain_text_smallest`, `explain_text_biggest`, `edit_transform_layer`, `edit_layer_opacity`, `a11y_edit_on_item`, `a11y_state_past_end`, `a11y_state_after_end`. **Photo-editor keys the video editor now needs (`tool_layer_opacity`, `layers_opacity`, `a11y_layer_order`, `edit_transform_layer`, `edit_layer_opacity`) move to `:core:ui` in this commit**, as P2-01 moved the video ones the other way: a second translation of the same words is drift waiting to happen.

Translator notes:
- **Overlay** is a video or photo shown on top of the main video, usually smaller (picture-in-picture). Use the word your language's video editors use; the same word everywhere (tool, track, messages).
- **Layer order / Bring forward / Send back** are about which overlay covers which. "Forward" and "back" are depth, never time; check that your word can't read as "skip ahead".
- **Never** use "overlay" for the caption on the video; that is "text".
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

**Tool-label widths (computed from the P1-06 figures, to check on the phone):** a tool label is `caption`, about 5.6 dp per Latin character at 100% and about 12 dp at 200%. In a 64 dp item with 8 dp of padding either side, one word of up to 8 characters fits at 100% and about 4 at 200%; a longer word widens the item (ToolBar rule). So at 100% only "Superposición" (13, about 73 dp) and "Sobreposição" (12, 67 dp) widen their tool, by under 15 dp; at 200% every word of five or more characters widens its item, "Superposición" to about 155 dp, "Sobreposição" 145, "Overlay" 85. The bar grows taller and nothing is cut. **At 200% only about three tools show at once, so Overlay (fifth) is one scroll away;** Edit, Meme sounds and Text come first, and Add media and Overlay sit side by side after them. That is the honest cost of the fifth place; the sixth would be two scrolls away.

## Accessibility
- **Focus and TalkBack order** (P1-04's list, with the stage's children): Preview, then each caption visible at the playhead (P1-10), then **each overlay visible at the playhead, front to back** ("Overlay, Video, layer 1 of 2", "selected" as the state; double-tap selects; actions: Position, Opacity, Layer order, Delete, Split), then the timeline. In the timeline, overlay clips read `a11y_overlay_item` and take P1-06's actions; double-tap selects, scrolls into view and moves the playhead to the clip's start (P1-05).
- **Single-pointer, no-drag alternatives** (WCAG 2.2 SC 2.5.1 and 2.5.7):

  | Gesture | Alternative |
  | --- | --- |
  | Drag to move on the stage | Position → Move arrows and Center |
  | Pinch or handle drag to size | Position → Size minus and plus |
  | Twist or handle drag to turn | Position → Rotation |
  | Drag to another lane (stacking) | Layer order → Bring forward, Send back |
  | Drag in time, trim | Move here, Split then Delete, "Trim to playhead" (P1-05) |
  | Opacity slider (a drag) | Slider steps of 10% by keyboard and TalkBack |

- **The handle** is hidden from accessibility (drag-only; the Position panel is the full alternative, P1-10).
- **Announcements, polite, never taking focus (SC 4.1.3):** `a11y_overlay_added`; after each Position step `a11y_text_transformed` or `a11y_text_position`; after a Layer order tap `a11y_layer_order` ("Layer 2 of 3", counted among all overlay lanes, 1 = the front-most lane); after the Opacity slider its value ("50 percent opacity"). Gestures announce nothing while moving. Toasts are announced.
- **Panels:** ToolPanel rules: focus to the title on open, back to the tool on close; what they cover (timeline and tool bar) leaves the accessibility tree and keyboard focus (`coveredByModal`). After the picker or the import sheet closes and the overlay lands, the bar switches to overlay tools, so focus goes to the first overlay tool (Position), as P1-08 does after a sound lands; a phone check says where TalkBack actually lands.
- **Touch targets:** every tool, button and panel control is 48 dp or more; an overlay's hit area is grown to 48 dp; the handle's target is 48 dp. The overlay clips on the timeline are 28 dp in 32 dp rows: the documented non-video exception (P1-05) with the routes above.
- **Contrast:** frame, handle and guides white with a near-black edge, 4.3:1 or more over any picture (P1-10); handle icon `on-primary` on white 18.9:1; tool labels `text-secondary` on `surface` 5.7:1; Delete `danger` on `surface` 5.2:1; Secondary buttons `text` on `surface-raised` 12.4:1; slider as the Slider README. Thumbnails carry no text. The wash and labels as P1-05.
- **200% font:** tool labels wrap and the bar grows (Copy); the Position and Layer order panels scroll, with the first control always in view (Layer order's buttons come first on purpose); the timeline has no text in overlay clips. Hindi: the labels use the system Devanagari face, which needs more line height; nothing has a fixed height.
- **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging or holding; never when a tool only explains; never for opening a panel or picking nothing). New ids for the PM to confirm at the ready check (they live in `EditorTool` and are the edit names in the undo history):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The overlay lands (once per pick) | `tool: "add_overlay"` |
| `tool_use` | A stage gesture releases | `tool: "transform_overlay"` |
| `tool_use` | One Position panel step | `tool: "position_overlay"` |
| `tool_use` | The opacity slider is released | `tool: "overlay_opacity"` |
| `tool_use` | Bring forward or Send back | `tool: "overlay_order"` |

- P1-06's own ids (`split`, `delete`, `duplicate`, `move`, `trim`, `volume`, `detach_audio`) fire as today when they act on an overlay; they can't be told apart from the same edit on a main clip. If the owner wants that split, it is a `value` on those events and a PRD change (a proposal for the PM, not asked here).
- A cancelled pick, a failed import and nothing picked fire nothing. `value` is not used. Analytics stay off until consent (P5-01).

## QA compares
Use the debug drafts `overlay-check` (request 8) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-01b/`.
1. **The main bar** with Overlay fifth, whole at 360, in English and Spanish at 200% (the label widened, bar taller).
2. **Landing:** a 16:9 video overlay on the 9:16 main video: at 50% center, frame and handle, the overlay bar with Position, Opacity, Delete in view, the new lane under the main row with thumbnails. Then a photo overlay: lower right slot.
3. **Gestures:** mid-pinch with the bubble; an edge snap with its guide; a center snap; the frame edge visible on a black background; a rotated overlay at 30°.
4. **Opacity** at 50% and 10% over a bright main clip (the blend, not a gray rectangle); the Opacity panel at 360 × 640.
5. **Stacking:** two overlays overlapping; Bring forward and Send back each way; the lanes after each; the toast at an end.
6. **Five overlays + a caption + a sound** at 360 × 640: the lanes scrolling, the Add a meme sound row pinned at the bottom, the playhead spanning the visible lanes.
7. **Position panel** at 360 × 640 and 360 × 800 (the scroll), Hindi at 200%.
8. **Missing file:** the lane label, nothing drawn on the stage, the toast; Delete first in the bar.
9. **Ratio change:** 9:16 to 16:9 with an overlay that fitted and one hung off an edge on purpose; the toast.

**Measured: the Done when** (note in the QA report; PM method in `docs/pm/acceptance-log.md`):
- **Five overlays at 30 fps:** debug draft `overlay-check`: a 1080p main video of 10 s, **five 1080p video overlays** visible together from 2 s to 8 s (sizes 30% to 70%, one rotated 20°, one at 60% opacity, two overlapping), a caption on top. Release-type build, the owner's phone, Media3 logs as in P1-04: preview at 30 fps or more for the whole span. Then drag one overlay for 3 s three times: janky frames under 5% (`dumpsys gfxinfo`). Record the result and the phone; "more than five" is a note, not a gate.
- **Preview = export:** export `overlay-check` at 1080p30. Pull frames at three times with ffmpeg and compare with preview screenshots cropped to the frame and scaled to the export width: each overlay's position and size within 1% of the width, rotation within 1°, opacity (sampled inside the overlay over a flat main clip) within 3 of 255 per channel, the stacking order the same, the missing-file overlay absent from both.
- **Reference renders** in `docs/qa/references/P4-01-overlays/` (CLAUDE.md rule 5), for iOS parity: opacity 100 / 50 / 10%; rotated 30° at 150%; two overlays in both stacking orders; a photo with transparency over a video.
- **Each edit is one undo step:** perform each edit in Interactions → Undo steps, then Undo once each time: exactly that edit goes. Undo of Add overlay removes the lane too; Undo of Layer order restores the lanes.
- **Old drafts open:** a draft saved before P4-01a opens unchanged (no overlay lanes); one saved with overlays reopens with the same order and transforms.
- **Responsiveness:** a Position or Opacity tap shows on the stage within the P1-06 edit rule (100 ms), or "Updating the preview" after 1 s.
- **Leaks:** LeakCanary on a debug build: add an overlay, drag, change opacity, reorder, delete, undo; close the editor; repeat 5 times. No leak.

## Model needs (for the PM and P4-01a; I don't edit code)
What the UI expects. If P4-01a models it differently, tell me and I change the wording, not the behavior.

| Need | Expected model |
| --- | --- |
| An overlay clip | A `MediaClip` on an OVERLAY `Track` (already in TECHNICAL_DESIGN) |
| Transform | `transform: Transform` (center fractions, scale, rotation) on the overlay clip |
| Opacity | `opacity: Float`, 0.1 to 1.0, default 1.0 (per clip, not per track) |
| Stacking | **The order of the OVERLAY tracks in `VideoTimeline.tracks` is the paint order, first = back** (no separate z field); each lane is a track |
| `fit` | `MediaClip.fit` is ignored for overlays (their size is their transform); P4-16 uses it on main clips only |
| Volume, detach | The existing `volume` and `audioDetached`, default volume 1.0 |
| Version | P4-01a bumps `schemaVersion` with an identity migration; the P4-16 fields ride in the same bump if the engineer adds them here (the kickoff proposal's "one bump per pair") |

## Requests to the principal mobile engineer
1. **Model and engine:** as above; transform, opacity and paint order identical in preview and export from one plan; Media3's composition takes per-sequence overlay settings with position, scale, rotation and an alpha scale (see Evidence), which is where this maps; verify the current API at the start.
2. **Stand-in** for the live gesture (Rules → Live feedback) with no double image.
3. **Edits** in `:core:domain` (pure, one `commit` each): `addOverlay(media, playheadUs)` (lengths, slot, lane rule, selection), `setOverlayTransform`, `setOverlayOpacity`, `reorderOverlay(forward|back)` (the lane rule, returns whether anything changed for the toast), the Duplicate offset rule, and the ratio rule for overlays.
4. **Timeline:** the overlay lane look (28 dp thumbnail strip, no label), the bonk and lane slide, lane viewport scrolling, **the pinned "Add a meme sound" row when the lanes overflow**, nodes for TalkBack (`a11y_overlay_item`).
5. **Stage:** the overlay's selection frame and handle (reuse the caption frame code), the handle clamp, hit testing in the order above, the edge snaps, the Position panel lifted for overlays with the photo size ladder, the new Opacity and Layer order panels, the hidden-from-accessibility handle, `coveredByModal` on the covered region.
6. **Icon:** `overlay` (`MemixIcons.Overlay`, `ic_overlay.xml`; DESIGN_SYSTEM → Iconography), the Overlay tool; the Layer order tool reuses `layers`.
7. **Constants:** one commonMain `OverlayCatalog` (landing scale, slots, photo duration, the 1 s and 3 s end rule, opacity range, duplicate offset), comment pointing to this spec and `design/tokens.json` → `overlay`; never through `MemixTheme`.
8. **Debug draft `overlay-check`** (opened with `memix.openEditor`): the 10 s main video, five video overlays as in Measured, one photo overlay with transparency, one overlay with a missing file, a caption, a meme sound.
9. **Analytics:** the five new `EditorTool` values.
10. **Strings** in the five `strings.xml` files, including the moves to `:core:ui`.
11. **Preview cost on a slow phone:** the reduced-size rule for more than five video overlays, measured on the owner's phone.

## Tokens and icons
- **New content constants** in `design/tokens.json` and `design/system/tokens.json` → `overlay` (landing, size, opacity, gestures, duplicate offset, ratio-change margin). Not in `MemixTheme`: user content (the caption and canvas constants' pattern). Kotlin: `OverlayCatalog` (commonMain next to `CanvasCatalog`).
- **No new color, size, radius or motion token** (checked against the sizes above: 28 and 32 dp lanes are `track-height` and `track-height` + `space-1`; the handle is `canvas-handle-size`).
- **One new icon:** `overlay`: a stroked 20 × 16 px rectangle (2 px stroke, 1 px corners) at x 2–22, y 4–20, holding a **solid 8 × 6 px rectangle (the core, 1 px corners) at x 10–18, y 10–16**, floating in its lower right, 2 px clear of the frame's inner edges on the right and bottom (1.4 px or more of air). It must not read as `duplicate` (the solid clip overlaps the outline's corner), `fit` (a tall frame, the block touching its sides) or `formats` (a solid bar flush with the outline's top). Kotlin: `MemixIcons.Overlay`. Added to DESIGN_SYSTEM → Iconography.
- **Pointer edits made with this spec:** ToolBar README (Overlay fifth, Voiceover sixth; overlay tool lists), Timeline README (overlay lane look; pinned pill), P1-06's clip-bar table (overlay rows) and P1-11's `canvas_layers_moved` (now "Layers moved…"). P3-08's "the fifth tool" line points here.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. How big does a new overlay land?**
- *Problem:* a picture-in-picture can land full-size (covering the main video) or small. The photo editor lands a second photo "fitted inside the frame" on the owner's own words (P2-13).
- *Considerations:* **A. 50% of the fitted size, centered or in the next free slot** (recommended; applied): the main video stays visible while the user places the overlay, and a drag or pinch is the natural next step; cost: it differs from the photo editor (a Memix rule to remember: "photo layers land full, video overlays land half"). **B. Fitted inside, like the photo editor:** one rule in both editors, but the main video disappears behind the new overlay and the first thing every user does is shrink it, one gesture away. **C. Ask in the picker:** an extra step for a decision a drag makes anyway.
- *Reverse:* one constant (`overlay.landing.scale`), no migration (saved overlays keep their scale).

**2. One item per pick, or several?**
- *Problem:* the picker can return several videos and photos at once.
- *Considerations:* **A. One** (recommended; applied): the common jobs place one picture; several would land stacked at the same place and size, which rarely helps without a layout. Same as P2-13. **B. Several, in a row in time on one lane,** each at the same place: good for a sequence of reaction photos; the confirm step and the "several landed" announcement cost about half a day. Later, no ticket.
- *Reverse:* B can be added without changing A.

**3. Does a video overlay's own sound play?**
- *Problem:* a reaction clip usually has a voice; a b-roll clip usually doesn't want it.
- *Considerations:* **A. At 100%** (recommended; applied): what you see is what you hear; the Volume tool is one tap away. Risk: the main clip's sound and the overlay's mix, which is the right default for a reaction. **B. At 0%:** safe against a doubled sound, but a user who hears nothing from the reaction thinks the app is broken, and nothing says why.
- *Reverse:* one default; no migration.

**4. Should the "Add a meme sound" row pin to the bottom when the lanes overflow?**
- *Problem:* with five overlay lanes the row scrolls out of sight, and with an overlay selected the tool bar has no Meme sounds tool: adding a sound takes three taps, against Principle 2's two.
- *Considerations:* **A. Pin it to the bottom of the timeline region only while the lanes overflow** (recommended; applied): sounds stay two taps away; it is a small change to the built P1-05 row (about half a day) and it moves once, along a lane slide. **B. Leave it:** no change to built code, but sound-first breaks exactly in the projects that use the pro tools. **C. Pin it always:** stable, but leaves a gap under the lanes in every simple project.
- *Reverse:* none needed; it is layout only.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Media3 composites several sequences with per-sequence overlay settings (position, scale, rotation, an alpha scale), keyed by time, so opacity and transform per overlay map to the engine | Guidance (API docs; the page summary came through search, the API reference pages did not load here, so the engineer verifies at the start) | [Define layouts with a custom VideoCompositorSettings](https://developer.android.com/media/media3/transformer/videocompositorsettings), [OverlaySettings](https://developer.android.com/reference/androidx/media3/common/OverlaySettings) |
| Dragging and pinching need a single-pointer alternative | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures), [SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Color can't be the only cue (the 28 dp overlay strip differs from the 40 dp main strip by height, position and spoken name) | Guidance | [WCAG 2.2 SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Non-text indicators need 3:1 | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Mobile editors add a picture-in-picture from an Overlay tool, then place it with a drag and a pinch, and stack overlays by track | Assumption (product knowledge of CapCut and VN; their help pages weren't reachable here) | none |
| Lane order = paint order is the convention of Premiere-style editors, with the first track at the back | Assumption (product knowledge); Memix reads it top to bottom because the main row is first | none |
| About half of observed phone users tap one-handed with the thumb; the lower middle is easiest to reach | Evidence (field observation, 2013; not a controlled study) | [Hoober, UXmatters](https://www.uxmatters.com/mt/archives/2013/02/how-do-users-really-hold-mobile-devices.php) |
