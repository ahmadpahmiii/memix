# Clip tools and edits · P1-06 (with the P1-08 meme sound and P1-09 original audio controls)

Part of a set of three for the video editor:
- `P1-04-editor-and-preview.md`: the screen frame, preview, transport, undo/redo and saving.
- `P1-05-timeline.md`: the timeline, selection, the "Add a meme sound" row and the original-audio toggle.
- **P1-06 (this file):** the tool bars and every edit; the meme sound controls (P1-08) and the original audio controls (P1-09).

## Job
Make the cuts a meme needs (split, trim, delete, duplicate, reorder) and control the sound: drop a meme sound at the playhead, then move, trim and level it; and mute, level or detach the clip's own audio.

**Done when** (TICKETS):

| Ticket | Done when |
| --- | --- |
| P1-06 | Each edit shows in the preview within 100 ms |
| P1-08 | The sound stays in sync within 1 frame in preview and export |
| P1-09 | Detached audio becomes its own audio-track clip |

**Fast path, with tap counts:**
- **Meme sound:** the "Add a meme sound" pill or the Meme sounds tool (1), then a sound's add button (2). The sound lands at the playhead.
- **Cutting out the boring start:** scrub, tap the clip (1), Split (2), select the first piece (3), Delete (4). Or drag the start handle: one gesture.

**Color roles:**
- **Blue:** the Meme sounds tool, and the add buttons in the sheet. Both are meme-sound UI. No tool is a primary action.
- **Red:** Delete is `danger` (with its word).
- **White:** selection.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Main tool bar | Nothing is selected | An item is selected |
| Clip tool bar | An item is selected: a tap, a long-press, the Edit tool, or TalkBack double-tap (P1-05) | Its back button, a tap on empty timeline, system back, or the item is deleted (or removed by undo). Each returns to the main tool bar. |
| Volume panel (ToolPanel) | The Volume tool | Done, system back or Escape. The item stays selected. |
| Meme sounds sheet | The Meme sounds tool or the "Add a meme sound" pill | A sound is added (it lands), or close, scrim, drag down, back or Escape (nothing added) |
| Reorder mode | The Reorder tool, or a long-press on a main clip | Done or a drop (applies the order); system back or Escape (cancels, restores the order) |

## Layout

### Main tool bar (nothing selected)
Component ToolBar (`design/system/components/ToolBar/README.md`, updated with this spec):
- `surface`, `toolbar-height`, `hairline` top border.
- Items 64 dp wide, icon over a `caption` label, `space-1` gap, left-aligned, scrolling when they overflow.
- Tools stay left-aligned so each keeps its position as later tickets add more.

| # | Tool | Icon | Ticket | Does |
| --- | --- | --- | --- | --- |
| 1 | Edit | `edit` (new; the board's scissors) | P1-06 | Selects the main clip under the playhead (at the very end: the last clip) and shows its tools. It's the tap route to clip tools for clips too thin to hit. |
| 2 | Meme sounds | `sounds` | P1-08 | Opens the Meme sounds sheet. Always `primary` icon and label, because it's meme-sound UI. On `primary-subtle` while the sheet is open (the board's active look). |
| 3 | Text | P1-10 | P1-10 | **Slot.** P1-10's spec defines it. Not shown before P1-10. |
| later | Audio (P3), Stickers (P4-12), Effects (P4-06), Filters, Overlay (P4-01), Remove BG (P4-13), Speed (P4-09) | | | Each appears with its own ticket, never as a disabled placeholder |

**Board differences:**
- The board's Stickers, Effects, Remove BG and Speed stay out until their tickets.
- Canvas and ratio are entered from the top-bar chip (P1-04 slot for P1-11), not the tool bar.

### Clip tool bar (one item selected)
The same bar, same height. From the left:
1. **Close clip tools:** a 48 dp icon button, `MemixIcons.ArrowBack` in `text`. It clears the selection.
2. A `stroke-hairline` divider in `hairline`, `space-6` tall, centered vertically.
3. **The tools for the selected item**, scrolling horizontally. At 360 dp about four and a half tools fit; the cut-off one shows there's more.

The tools stay in thumb reach, at the bottom where the main tools were. This replaces the board's selected-clip bar (P1-05 → Differences). CapCut swaps its bottom bar the same way when a clip is selected (Assumption: product knowledge).

| Selected item | Tools, in order |
| --- | --- |
| Video clip (main track) | Split · Volume · Delete · Duplicate · Detach audio · Reorder · Replace *(pending P1-16)* |
| Photo clip (main track) | Split · Delete · Duplicate · Reorder · Replace *(pending P1-16)* |
| Meme sound | Volume · Split · Delete · Move here · Duplicate |
| Audio clip (detached original audio) | Volume · Split · Delete · Move here · Duplicate |
| Text item | P1-10 puts its own tools first (edit text, style), then Split · Delete · Move here · Duplicate |
| Any item on a locked track | Unlock, then that item's usual tools |

- **Why this order:**
  - Split comes first because it's the most used cut.
  - Volume is second on clips because lowering the original under a meme sound is the next most common job, and first on sounds.
  - Delete sits third so "Split, then Delete" works without scrolling the bar at 360 dp.
  - Rare tools come last.
- **Tool icons** (new, drawn to the icon grammar; see Requests):
  - `split`
  - `volume`
  - `delete`
  - `duplicate`
  - `detach_audio`
  - `reorder`
  - `move_here`
  - `lock` (for Unlock)
- **Look:**
  - Tools: `text-secondary` icon and label.
  - Delete: icon and label in `danger`, 5.2:1 on `surface`, always with its word.
  - Volume while its panel is open: `primary` on `primary-subtle`, the active-tool look.
- **A tool that can't act right now keeps its normal look and explains on tap** with a toast (design system: "prefer keeping controls enabled and explaining on tap"). Dimming to `text-muted` would be almost invisible next to `text-secondary`, and the explanation teaches the rule. Examples: Split when the playhead isn't inside the clip; Delete on the only clip.
- **Main tool bar ↔ clip tool bar:** a 120 ms cross-fade, no slide.

### Volume panel (ToolPanel)
Component `design/system/components/ToolPanel/README.md` (new). The panel covers exactly the timeline and tool bar region (P1-04), so the preview and the transport row stay visible and working. Playback keeps going, so changes are heard live.
- **Top row:** "Volume" in `title` on the left (`space-4` gutter); **Done** (Ghost button) on the right.
- **Slider** (component Slider), `space-4` side padding:
  - Label: "This clip" or "This sound". The readout is the percent in `timecode` style.
  - Range 0% to 200%. Default and reset value 100%. Keyboard and TalkBack step 10%.
  - While dragging, it snaps to 100% within ±3% with one SegmentTick haptic, so the original level is easy to find again.
  - Double-tap on the thumb resets to 100% (component behavior).
- **Video clips only:** `space-4` below the slider, a Toggle row: "Mute original audio of every clip". It's the same state as the timeline's original-audio toggle (P1-05).
- **Undo steps:** each slider release is one step ("Volume"). Each toggle flip is one step ("Mute original audio" or "Unmute original audio").
- **Above 100%,** loud sounds can distort. Meme sounds arrive normalized to −16 LUFS with −1 dBTP peaks (PRD), so there's headroom up to about +1 dB before clipping. Whether to soft-limit is the engineer's call, as long as preview and export match (request 6). Loud is a meme style; no warning is shown.

### Meme sounds sheet (P1-08)
The standard Sheet (grabber, close button, `title` heading "Meme sounds"). It replaces nothing on the board: the board linked the tool to the Sounds tab. P3-05 later replaces this sheet with the full picker (search, tabs, favorites). The two-tap rule and the landing behavior below stay the same.
- **Height:** opens at about half the screen, so the preview's top stays visible above the scrim. A drag up grows it until its top is `space-10` under the status bar (Sheet long-content rule).
- **List:** one SoundRow per bundled starter-pack sound, in the pack's curated order. No search, tabs, chips or favorites in P1. The SoundRow favorite heart is hidden (request 4).
- **Row content:**
  - The play disc.
  - The title in `body-strong`.
  - The meta line in `label` `text-secondary`: "{duration} · {credit}". Duration is `M:SS`, rounded up to whole seconds, at least 0:01 (the library format on the Sounds board). Credit is the pack's credit text, for example "CC0", "Own recording", or a CC-BY attribution.
  - The meta line may wrap to two lines and is never cut off, because a CC-BY credit is a license condition (PRD → Content and licensing).
  - The blue add button.
- **Board sample content** (`Sounds.dc.html`: "Boom hit", "Bruh (recreated)", "0:01 · CC0", …): the bundled pack's own metadata replaces it. Until licensed sounds exist, release builds show the empty state, and debug builds may use self-made test tones titled "Test tone 1…".
- **Play:** plays that sound alone (the editor stays paused). Only one plays at a time. At its end, the row returns to idle. Fires `sound_preview`.
- **Add:** the sound lands (below), the sheet closes over 200 ms, and `sound_add` fires.
- **Close** (×, scrim, drag down, back, Escape): nothing is added and nothing changes.
- **Long-press on a row:** nothing in P1. P3 adds details, credit and Report.
- **Empty** (no sounds bundled): the P0-05 Sounds empty state, centered in the sheet: "NO SOUNDS YET" / "The meme sound library comes in a later build."

### Bubbles and guides (during drags)
- **Value bubble:** a `surface-raised` pill (`radius-full`, `shadow-float`) holding one `timecode`-style value in `text`, with `space-1` vertical and `space-2` horizontal padding.
  - It floats `space-1` above the dragged item's row, centered on the moving edge (trim) or the item's start (move), and kept inside the timeline width.
  - Trim shows the new duration; move shows the new start time.
  - It's the one floating element on the timeline, so it gets the floating shadow.
- **Snap guide:** while snapped, a `stroke-hairline` line in `text-secondary` across all lanes at the snap time. None is drawn when the snap target is the playhead, which is already a line.

## Edits
All times are in microseconds. The minimum length of any item is 0.1 s (100,000 µs). A "frame" for stepping is 1/30 s.

### Split
- **Acts on** the selected item at the playhead. It needs the playhead inside the item and at least 0.1 s from both of its edges. Otherwise Split explains: "To split, move the playhead inside this clip."
- **Result:** two items that meet exactly at the playhead.
  - The left piece ends where the right piece begins: `trimOutUs` of the left = `trimInUs` of the right = trim in + (playhead − start), for media and audio.
  - Volume, the detached flag and every other property are copied to both pieces.
- **On the main track** nothing shifts. The pieces adjoin, and the normal `space-1` gap appears between them.
- **Selection** moves to the right-hand piece, the part from the playhead on. "Split, then Delete" then removes everything after the playhead, the most common meme cut (Assumption; easy to change if the test script shows otherwise).
- **Preview:** unchanged; a split doesn't change the output.
- **Undo** is one step ("Split").
- iMovie places split and the other clip actions behind a selected clip the same way (Guidance).

### Trim (drag a handle)
- **Handles:** each one is `trim-handle-width` wide, in `selection`.
  - It sits just outside the selection outline at each end, as tall as the item plus the outline and its gap.
  - The outer corners are `radius-sm`.
  - A grip line in `on-primary` (`stroke-selection` wide, half the handle's height) runs down its center (18.9:1 on white). The white handle reads 17:1 or more against the dark background beside it (`canvas` or a `surface` band).
- **Targets:**
  - Each handle's target is `touch-target` wide and the full row height.
  - It extends outward from the item's edge, so even a thin item keeps its own body free to tap.
  - Only where the screen edge cuts off the outward side does the target move inward.
  - A drag on a handle starts at once; no long-press is needed.
- **While dragging,** only the handle moves; nothing else does until release.
  - The moving edge follows the finger and snaps (Snapping below).
  - The value bubble shows the new duration.
  - **Main track:** the clip's far edge and every other clip stay where they are, so a gap or an overlap shows on the near side. On release, the clips close up or push along over 200 ms (ripple): the main track never has gaps.
  - **Other lanes:** the item's other edge stays put. A neighbor on the same lane is a hard stop.
- **Limits:**
  - **Video clips and audio** stop at the source file's start or end. The handle stops with one SegmentTick: no more footage, as in iMovie (Guidance).
  - **Photos** have no source limit.
  - **Every item** stays at least 0.1 s long.
- **Preview while trimming:**
  - Video and photo clips: the preview shows the frame at the moving edge (the new first or last frame).
  - Sounds and audio: the preview stays on the playhead's frame. There's no audio scrubbing.
  - After release, the preview returns to the playhead's frame.
- **Undo** is one step per release ("Trim").
- **The trim alternative without dragging** is Split, then Delete, or the handle's "Trim to playhead" accessibility action (P1-05).

### Delete
- **The selected item is removed.**
  - On the main track, later clips close up over 200 ms.
  - On other lanes, nothing else moves. If the lane is now empty, it goes (P1-05).
- **Afterwards:** the selection clears and the main tool bar returns. The playhead stays, clamped to the new end.
- **Not on the only main clip:** it explains "Your video needs at least one clip." A video project with no clips has no length and nothing to preview, and before P1-16 there would be no way to add one again.
- **Detached audio stays** when its video clip is deleted. It's independent once detached.
- **No confirmation.** Undo brings the item back as one step ("Delete"). Confirmations for undoable actions just get clicked through (Guidance).

### Duplicate
- **Main track:** the copy is inserted right after the original. Later clips move right over 200 ms.
- **Other lanes:** the copy goes right after the original on the same lane if that space is free for its whole length. Otherwise it goes on the first lane of that kind that's free there, otherwise on a new lane.
- **The copy** keeps every property, becomes the selection, and lands with the bonk (it's new content).
- **Undo** is one step ("Duplicate").

### Reorder (main video track)
**Entering reorder mode:**
- **By drag:** long-press a main clip (LongPress haptic).
- **By taps:** the Reorder tool.

**In reorder mode:**
- Every main clip collapses into a square tile (`track-height-video`) showing its first frame, `space-1` apart, in order. The selected clip's tile is under the finger (drag) or under the playhead (taps).
- The selected tile has the white selection outline.
- The ruler, the other lanes, the leading and trailing areas and the add row get a `scrim` wash and don't respond.
- Collapsing makes a long video reorderable without dragging across minutes of timeline. iMovie lifts a held clip off the timeline the same way (Guidance), and CapCut shrinks clips to tiles (Assumption: product knowledge).

**Moving by drag:**
- The tile follows the finger horizontally.
- When its center passes a neighbor's center, the neighbor slides over (200 ms) to open the slot.
- Near the screen edges, the strip scrolls (Edge auto-scroll).

**Moving by taps:** the tool bar shows **Move earlier**, **Move later** and **Done**.
- Each tap moves the clip one slot (200 ms).
- Tapping another tile moves the selected clip to that tile's position.
- At either end, the button explains: "This clip is already first." / "…last."

**Finishing:**
- A drop or Done applies the new order as one step ("Reorder"). The tiles expand back to the time layout (200 ms), and the playhead moves to the moved clip's start so it's in view.
- If the order didn't change, nothing is recorded.
- Back or Escape cancels and restores the order.
- **With one clip,** Reorder explains "Reorder needs at least two clips."

### Move (every lane except the main track)
- **By drag:** long-press an item (LongPress haptic). It lifts with the white outline and follows the finger.
  - Horizontally, it sets the item's start (snapping). The start can't go before 0:00.
  - Vertically, it can change to another lane of the same kind. A meme sound can also be dropped on the "Add a meme sound" row, which creates a new lane. Lanes of other kinds are skipped.
  - The value bubble shows the new start time. The preview stays on the playhead's frame.
  - Dropped where it would overlap another item on that lane, it lands on the next lane of its kind that's free, or a new one. Items never overlap within a lane.
  - The item may reach past the end of the video. That part shows the wash and won't play (P1-05).
- **Move here** (the tap alternative): sets the item's start to the playhead, with the same lane rule.
  - If the playhead is at the end of the video, the item is placed to end there instead, so it's still heard (same rule as landing).
- **Undo** is one step ("Move").
- **Other tracks don't follow the main track.** When main clips ripple (trim, delete, duplicate, reorder), items on other lanes keep their times. A sound placed on a frame can drift off it if earlier clips change afterwards. That's the simple model every mobile editor starts from (Assumption). Undo and Move fix it. Whether a "keep sounds with their clip" option is worth adding is a question for the P1 test (Questions).

### Snapping (trim and move)
- **Targets:**
  - the playhead
  - 0:00
  - the end of the video
  - every boundary between main clips
  - the start and end of every other item, except the one being dragged
- **Range:** within `space-2` on screen. The edge (or, when moving, whichever end of the item is nearer) jumps to the target. One SegmentTick haptic fires, and the snap guide shows. Moving more than `space-2` away releases it.
- **Always on in P1.** There's no snapping switch.
- **Scrubbing doesn't snap** (P1-05).

### Edge auto-scroll
While trimming, moving or reordering, a finger within `touch-target` of the timeline's left or right edge scrolls the timeline that way. The closer the finger is to the edge, the faster it scrolls; the engineer tunes the top speed, about one screen width a second. The content moving under the fixed playhead is expected here.

### Preview after an edit (P1-06 Done when)
- Every committed edit reaches the preview within 100 ms, through `PreviewSession.update`, without rebuilding the player.
- During a drag only the timeline updates, at 60 fps. The engine gets the change on release (`mobile-performance` skill).
- Any edit pauses playback first (P1-04).

## Meme sounds (P1-08)
1. **Add:** the pill or the tool opens the sheet; the sound's add button places it.
2. **Landing:**
   - **When:** at the playhead. If the playhead is at the end of the video (within one frame), the sound is placed so it ends at the end of the video (start = the larger of 0:00 and end − length). A sound dropped at the very end would otherwise be silent.
   - **Where:** the first meme-sound lane that's free for the sound's whole length, otherwise a new lane under the last one.
   - **What:** the whole sound (trim in 0, trim out = its length) at 100% volume.
   - **Then:**
     - The clip bonks (`duration-bonk`).
     - The phone gives one Confirm haptic, the "haptic tick when a sound lands" (`mobile-performance` skill).
     - The new sound becomes the selection, so its tools are ready.
     - The lanes scroll vertically if they need to, to show it.
     - TalkBack hears "Boom added at 3.2 seconds".
   - **Undo** is one step ("Add sound").
   - **A bundled file that won't decode:** nothing lands, and a toast says "That sound won't play. Try another one."
3. **Move:** long-press and drag, or Move here (Edits → Move).
4. **Trim:** handles (Edits → Trim). It stops at the sound's own start and end.
5. **Volume:** the Volume panel, "This sound", 0–200%.
6. **Split, Delete, Duplicate:** as in Edits.
7. **Sync (P1-08 Done when):** the sound's first sample is heard within one frame of its timeline start, in preview and in the export.

## Original audio (P1-09)
"Original audio" is the sound recorded in the user's own videos.

### Mute every clip
- **Where:** the timeline toggle at the head of the main track (P1-05), and the same toggle in any video clip's Volume panel.
- **What it changes:** `Track.muted` on the main video track. It applies to every clip's own sound at once and takes effect in the preview immediately. Detached audio clips aren't affected.
- **Undo** is one step ("Mute original audio" / "Unmute original audio"). It fires `tool_use` `mute_original`.
- **Why both places:** muting the original so only the meme sound plays is the most common audio move in reaction memes (Assumption). The toggle sits where the video starts, and the panel reaches it from anywhere.

### Clip volume
- **Where:** the Volume panel on a video clip, "This clip". It sets `MediaClip.volume` (0–200%).
- **While every clip is muted,** the slider still works. Its value is kept for when the original is unmuted, and the toggle right under it shows why nothing is heard.
- **No sound to adjust:**
  - Photos have no Volume or Detach audio tool.
  - A video with no sound track: Volume and Detach audio explain "This clip has no sound." (request 3)
  - A clip whose sound is detached: Volume explains "This clip's sound is on its own track now. Select it there."

### Detach audio
**What it creates:**
- A new audio clip: same source, same trim in and out, same volume, starting at the video clip's start.
- It goes on the first Audio lane that's free for that span, otherwise a new Audio lane.
- It's labeled "Original audio" in `track-audio` green.
- The video clip's `audioDetached` becomes true, so its sound now plays only from the new clip.

**What happens next:**
- The new audio clip becomes the selection and bonks. There's no haptic; that's reserved for meme sounds landing.
- **Undo** is one step ("Detach audio"). It fires `tool_use` `detach_audio`.
- **If every clip was muted:** the new clip starts at 0%, so nothing suddenly gets louder. A toast explains: "Original audio was muted, so the detached sound starts at 0%."
- **Already detached:** the tool explains "This clip's sound is on its own track now. Select it there."

**After detaching:**
- The clip and its detached audio are independent. Trimming, splitting, moving or deleting one doesn't change the other.
- Deleting the detached audio clip leaves that video silent. That's how to remove one clip's original sound for good.
- Re-attaching isn't in v1; undo is the way back.
- Clipchamp and CapCut separate audio onto its own track the same way (Guidance; Evidence from vendor pages).

## Locked items
Nothing in P1 locks a track, but `Track.locked` exists and later templates may set it (P1-05 → Locked).
- **A tap selects the item:** outline, no handles.
- **A long-press doesn't lift it.** It selects the item and explains "This track is locked. Unlock it to edit."
- **The clip tool bar** starts with **Unlock** (icon `lock`), then the usual tools. Each of those explains the same way on tap.
- **Unlock** clears `Track.locked` for the whole track. Undo is one step ("Unlock"), and it fires `tool_use` `unlock`.
- **Locked main track:** ripple edits from other main clips are refused with the same explanation.

## Replace (pending the owner's decision on P1-16)
- **Slot:** the clip tool bar on main video and photo clips, after Reorder. Hidden until P1-16 is approved and built.
- **Recommended behavior,** so P1-16 can start from it:
  - Replace opens the P1-02 picker for one item. The new media takes the old clip's place and length, so sounds and text stay in sync.
  - **A photo** always fits; it simply shows for the clip's length.
  - **A video at least as long as the clip** is used from its start (trim in 0), with its own sound (audio attached, volume 100%).
  - **A video shorter than the clip** isn't used. A toast explains: "That video is shorter than this clip (00:03.20). Pick a longer one, or trim this clip first." Shrinking the clip would ripple the main track and pull everything after it out of sync, which is exactly what Replace promises not to do.
  - Undo is one step ("Replace"). It fires `tool_use` `replace_clip`.
- **Open question** (for the PM, with P1-16): should Replace also cover meme sounds (swap the sound, keep its start)? The board shows Replace on a selected sound.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Nothing selected | Main tool bar: Edit, Meme sounds (blue) | `tool_edit`, `tool_meme_sounds` |
| Video clip selected | Clip tool bar: Close, divider, Split · Volume · Delete · Duplicate · Detach audio · Reorder | `clip_tools_close`, `tool_*` |
| Photo clip selected | Split · Delete · Duplicate · Reorder | same |
| Meme sound or audio clip selected | Volume · Split · Delete · Move here · Duplicate | same |
| Split not possible here | Split looks normal; a tap shows the toast | `explain_split` |
| Only one main clip | Delete and Reorder explain on tap | `explain_last_clip`, `explain_reorder_one` |
| Trimming | Handle follows the finger; value bubble with the duration; snap guide when snapped; a gap or overlap on the main track until release | none |
| Trim at the footage limit | Handle stops; one tick | none |
| Moving a sound | Lifted item with outline, value bubble with the start time; drop on the add row makes a new lane | none |
| Reorder mode | Tiles, washed surroundings; tool bar Move earlier · Move later · Done | `reorder_move_earlier`, `reorder_move_later`, `panel_done`, `explain_reorder_first`, `explain_reorder_last` |
| Volume panel | Title, Done, slider at the current value, the mute-every-clip toggle (video clips) | `volume_title`, `panel_done`, `volume_this_clip` or `volume_this_sound`, `volume_percent`, `volume_mute_all` |
| Meme sounds sheet, default | Rows with play and add | `sounds_title` (P0-05), `sound_meta` |
| Meme sounds sheet, previewing | That row in its playing state (SoundRow) | none |
| Meme sounds sheet, empty | P0-05 empty state | `sounds_empty_title`, `sounds_empty_body` |
| Sound landed | Bonk, haptic, new sound selected, sound tools | `a11y_sound_added` |
| Sound landed at the end | Placed so it ends at the end of the video | none |
| Sound won't decode | Toast; nothing lands | `explain_sound_unreadable` |
| Clip has no sound | Volume and Detach audio explain | `explain_no_sound` |
| Sound already detached | Volume and Detach audio explain | `explain_detached` |
| Detached while muted | New clip at 0%; toast | `explain_detached_muted` |
| Locked | Unlock first; other tools explain | `tool_unlock`, `explain_locked` |
| Long content | Tool labels wrap to two lines inside 64 dp; at large font scales items widen and the bar grows (Accessibility). Long sound titles end in an ellipsis on the clip and wrap to two lines in the sheet. | |
| Offline, permission | Doesn't apply: everything is on the phone; no permission needed | none |

## Motion and haptics
| What | Motion | Reduce motion |
| --- | --- | --- |
| Main ↔ clip tool bar | 120 ms cross-fade | same |
| Volume panel opens or closes | 200 ms slide up or down over the timeline region | 120 ms fade |
| Meme sounds sheet | 200 ms slide over a fading scrim | 120 ms fade |
| Sound lands, duplicate lands, detached audio appears | The bonk: 1 → 1.12 → 1 over 240 ms (`duration-bonk`) | 120 ms fade-in |
| Ripple after trim, delete or duplicate on the main track | Clips slide 200 ms | 120 ms cross-fade to the new layout |
| Reorder: collapse, make room, expand | 200 ms each | 120 ms fades |
| Value bubble, snap guide | 120 ms fade in and out | same |
| Toasts | 120 ms fade (P1-04) | same |

Haptics, kept rare per Android's guidance (Guidance). They use Compose `HapticFeedbackType`; check the names against the current docs.

| Event | Type |
| --- | --- |
| A meme sound lands | Confirm |
| Long-press lifts an item or enters reorder | LongPress |
| Snap engages; a trim reaches its footage or neighbor limit; the volume slider snaps to 100% | SegmentTick, once per engagement |
| Anything else | none |

The system's own haptics setting is respected.

## Copy
English source; the engineer machine-drafts id, es, pt and hi until P5. Budgets are characters. Tool labels sit under a 24 dp icon in a 64 dp item: at most two lines of about 10 characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_edit` | Edit | 20 | |
| `tool_meme_sounds` | Meme sounds | 20 | |
| `tool_split` | Split | 20 | Cut one clip into two at the playhead |
| `tool_volume` | Volume | 20 | |
| `tool_delete` | Delete | 20 | |
| `tool_duplicate` | Duplicate | 20 | |
| `tool_detach_audio` | Detach audio | 20 | Moves a clip's own sound to its own track. CapCut's local wording is a good guide. |
| `tool_reorder` | Reorder | 20 | Change the order of clips |
| `tool_move_here` | Move here | 20 | "Here" = the playhead; the spoken label says so |
| `tool_move_here_a11y` | Move to playhead | 24 | Screen reader label for Move here |
| `tool_unlock` | Unlock | 20 | |
| `tool_replace` | Replace | 20 | P1-16, pending |
| `clip_tools_close` | Close clip tools | 24 | Screen reader only |
| `reorder_move_earlier` | Move earlier | 20 | |
| `reorder_move_later` | Move later | 20 | |
| `panel_done` | Done | 16 | Tool panels and reorder mode |
| `volume_title` | Volume | 20 | Panel title |
| `volume_this_clip` | This clip | 20 | Slider label |
| `volume_this_sound` | This sound | 20 | Slider label |
| `volume_percent` | %1$d%% | 6 | Use the language's own percent format |
| `volume_mute_all` | Mute original audio of every clip | 48 | Toggle row; may wrap to two lines |
| `sound_meta` | %1$s · %2$s | 80 | %1$s = duration "0:02", %2$s = the pack's credit text (not translated: it's the license's attribution) |
| `explain_split` | To split, move the playhead inside this clip. | 60 | |
| `explain_last_clip` | Your video needs at least one clip. | 50 | |
| `explain_reorder_one` | Reorder needs at least two clips. | 50 | |
| `explain_reorder_first` | This clip is already first. | 40 | |
| `explain_reorder_last` | This clip is already last. | 40 | |
| `explain_no_sound` | This clip has no sound. | 40 | |
| `explain_detached` | This clip's sound is on its own track now. Select it there. | 70 | |
| `explain_detached_muted` | Original audio was muted, so the detached sound starts at 0%. | 70 | |
| `explain_locked` | This track is locked. Unlock it to edit. | 50 | |
| `explain_sound_unreadable` | That sound won't play. Try another one. | 50 | |
| `explain_replace_too_short` | That video is shorter than this clip (%1$s). Pick a longer one, or trim this clip first. | 110 | P1-16, pending. %1$s = timecode "00:03.20" |
| `a11y_sound_added` | %1$s added at %2$s | 70 | %1$s = sound title, %2$s = spoken time (P1-04) |
| `a11y_edit_on_item` | %1$s: %2$s | 70 | Announced after each edit: %1$s = edit name, %2$s = item label ("Delete: Boom") |
| `edit_split` | Split | 24 | Edit names: used in "Undo: …" (P1-04) and announcements |
| `edit_trim` | Trim | 24 | |
| `edit_delete` | Delete | 24 | |
| `edit_duplicate` | Duplicate | 24 | |
| `edit_reorder` | Reorder | 24 | |
| `edit_move` | Move | 24 | |
| `edit_volume` | Volume | 24 | |
| `edit_mute_original` | Mute original audio | 24 | |
| `edit_unmute_original` | Unmute original audio | 24 | |
| `edit_detach_audio` | Detach audio | 24 | |
| `edit_add_sound` | Add sound | 24 | |
| `edit_unlock` | Unlock | 24 | |

Translator notes:
- **Edit names** appear after "Undo: " and "Redo: ". Use the noun or verb form that reads naturally there, and keep it short.
- **Explanations** say what to do next. Never blame the user.
- **Tool labels:**
  - Portuguese and Spanish run longest. Hindi needs extra line height (the system Devanagari font).
  - Two lines are fine; three aren't. If a label won't fit in two lines of about 10 characters, choose a shorter synonym.
- **"Meme"** stays as is if your language uses the loanword.

## Accessibility
- **Tool bar:** every tool's visible label is its accessible name (WCAG 2.2 SC 2.5.3), except Move here, which is spoken "Move to playhead". Close clip tools is icon-only and spoken. Focus order runs left to right. The tool bar comes after the timeline in the editor's focus order (P1-04).
- **After each edit**, a polite announcement reads `a11y_edit_on_item` ("Delete: Boom") or `a11y_sound_added`. Explanation toasts are announced too. Nothing takes focus (SC 4.1.3).
- **Accessibility actions on the selected item** (TalkBack's actions menu, Switch Access):
  - Split
  - Delete
  - Duplicate
  - Move earlier / Move later (main clips)
  - Move to playhead (other items)
  - Its handles carry "Trim to playhead" (P1-05)

  These are the single-pointer routes that replace dragging (SC 2.5.7, Guidance; Compose custom actions are meant for exactly this, Guidance).
- **Reorder mode:**
  - Tiles are a list. Each reads "Video, clip 2 of 3" with its position.
  - The selected tile is marked selected.
  - Move earlier, Move later and Done are buttons. After each move, "Clip 2 of 3" is announced politely.
- **Volume panel:**
  - Opening it moves focus to its title. Done returns focus to the Volume tool.
  - The slider follows the Slider component: adjustable, with the readout as its state.
  - The toggle is a switch.
- **Meme sounds sheet:**
  - Focus moves to the sheet, and its title is the pane title.
  - Each row's play and add buttons are labeled with the sound's name (SoundRow's existing strings).
  - When the sheet closes after adding, focus goes to the new sound on the timeline, and its announcement plays.
- **Keyboard** (with the P1-04 and P1-05 keys):

  | Key | Action |
  | --- | --- |
  | Ctrl+B | Split |
  | Delete / Backspace | Delete |
  | Ctrl+D | Duplicate |
  | Alt+Left / Alt+Right | Move earlier or later (main clips), or nudge by one frame (other items) |
  | Escape | Close the panel or sheet, cancel reorder, or clear the selection |

- **Touch targets:**
  - Tools, Close clip tools, handles, the sheet's buttons and the panel's controls are all 48 dp or more.
  - Tiles in reorder mode are 40 dp drawn, in 48 dp targets. Neighboring targets overlap only within the `space-1` gap, so the tile the touch lands on wins.
- **Contrast:**
  - Tool labels: `text-secondary` on `surface`, 5.7:1. Delete: `danger` on `surface`, 5.2:1. The Meme sounds tool: `primary` on `surface`, 7.3:1.
  - Value bubble: `text` on `surface-raised`, 12.4:1 or more.
  - Handles: white against `canvas` 18.9:1, against a `surface` band 17.2:1.
- **200% font scale:**
  - Tool labels wrap to two lines, and items widen beyond 64 dp when a word can't break. The bar grows taller to fit and keeps scrolling sideways.
  - The panel's rows wrap.
  - Sheet rows grow; the meta line wraps.
  - The value bubble grows.
- **Reduce motion:** see Motion. Haptics don't depend on it.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Once per committed undo step, never during a drag, never when a tool only explains | `editor: "video"`, `tool`: `split`, `trim`, `delete`, `duplicate`, `reorder`, `move`, `volume`, `mute_original`, `detach_audio`, `unlock`; `undo` and `redo` (P1-04); later `add_media` and `replace_clip` (P1-16) |
| `sound_add` | A meme sound lands from the sheet. Not for duplicates (those are `tool_use` `duplicate`). | `sound_id` (catalog id of the bundled sound), `tab`: `"starter"` (pending the PM, Questions), `editor: "video"` |
| `sound_preview` | A sound starts playing in the sheet | `sound_id`, `tab: "starter"` (pending) |

- **Opening a panel or the sheet,** selecting and zooming fire nothing.
- **Analytics stays off until consent** (P5-01). The events are coded now so they're ready.

## QA compares
1. **Video clip selected:** the clip tool bar at 360 dp, with Split, Volume and Delete visible without scrolling. Then the same in Hindi at 200% font.
2. **Trim in progress** on a main clip: handle, value bubble, the gap left on the near side. Then the closed-up result.
3. **A meme sound just landed:** bonk end state, the sound selected with sound tools, the sheet closed. Plus the sheet itself with the empty state (release build) and with test tones (debug).
4. **Volume panel** on a video clip: slider at 100%, the mute-every-clip toggle; preview and transport visible above.
5. **Reorder mode** by the Reorder tool: tiles, washed surroundings, Move earlier, Move later and Done.
6. **Detached audio:** the green "Original audio" clip on its lane under the clip, and the explanation when Detach audio is tapped again.

Measured (note in the QA report):
- **P1-06 Done when:**
  - Screen-record the emulator or phone at 60 fps.
  - For each edit (split, trim release, delete, duplicate, reorder drop, volume release), count frames from the commit to the preview change. Pass: 6 frames (100 ms) or fewer.
  - Split shows no preview change by design; check that the timeline updates instead.
- **P1-08 Done when:** a debug project with a test-tone sound placed on a frame that has a burned-in timecode (P1-03 method).
  - In the preview, step frame by frame: the tone starts on the right frame.
  - In the export, use `ffprobe` or a waveform view against the timecode frames.
  - Pass: within one frame (33 ms at 30 fps) in both.
- **P1-09 Done when:** detach a clip's audio. The project JSON (debug hook) shows a new `AUDIO` track with an `AudioClip` whose source and trim match the clip, and the clip has `audioDetached: true`. The exported file's audio plays once, with no doubling.
- **Two taps:** from the timeline with a clip selected, the pill (1) and add (2) place a sound. From nothing selected, the Meme sounds tool (1) and add (2).

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Icons**, drawn to the grammar (24 grid, 2 dp flat-cap strokes, one solid core):
   - `edit`: the board's scissors, with the finger loops as the core.
   - `split`: two rectangles side by side with a vertical stroke between them.
   - `volume`: three bars rising left to right, the tallest solid.
   - `delete`: a lid stroke over a solid can.
   - `duplicate`: two offset rectangles, the front one solid.
   - `detach_audio`: a rectangle above a short wave line, with a gap between them.
   - `reorder`: two rectangles under a curved two-way arrow.
   - `move_here`: an arrow pointing right into a vertical line.
   - `lock`: from P1-05.
   - `replace` waits for P1-16.
2. **Components:**
   - ToolPanel (`design/system/components/ToolPanel/README.md`) and Menu (`design/system/components/Menu/README.md`), in `:core:designsystem` and the debug catalog.
   - ToolBar gains the clip mode: leading Close clip tools button, divider, `danger` Delete.
3. **Import metadata:** P1-06 needs each video source's full duration (trim limits) and whether it has a sound track (Volume and Detach audio). P1-02 already asks for the duration; add "has audio". If an older draft has neither, read them from the copy when the editor opens.
4. **SoundRow:**
   - A variant without the favorite heart (P1-08 has no Favorites list to show it in).
   - Let the meta line wrap to two lines (CC-BY credits must never be cut).
5. **Edits:**
   - As specified, each one an immutable `Project` change and one undo step committed on gesture end.
   - Main-track ripple keeps the track gap-free from 0:00.
   - Other tracks keep absolute times.
   - Lanes are picked by "first free lane of that kind, else a new lane"; an emptied non-main track is removed in the same step (P1-05).
6. **Volume above 100%:** apply the same gain in preview and export. A soft limiter, or plain clipping, is your call, provided both match.
7. **Starter pack:** read the title, duration, credit and catalog id from the bundled pack's metadata (the same fields as the catalog: `credit` comes from the license record). Until licensed sounds exist, release builds bundle none; debug builds may add self-made test tones.
8. **Haptics:** Compose `HapticFeedbackType.Confirm`, `LongPress` and `SegmentTick`, as in the Motion and haptics table. Verify the names in the current Compose docs.
9. **Analytics:** as in the table. `sound_add.tab` stays `"starter"` until the PM decides.

## Proposals and questions for the PM
1. **`sound_add.tab` and `sound_preview.tab` for the starter sheet.** The PRD's tab values are Trending, Local, Global, Favorites and My sounds; none fits a sheet with no tabs. I propose `"starter"` until P3-05 brings real tabs. No scope change.
2. **`tool_use` values.** Confirm the tool ids above, including `undo` and `redo` (P1-04). The undo rate is the cheapest signal that edits go wrong.
3. **P1-16 (owner decision pending):**
   - The Replace slot and the Add media tile (P1-05) stay hidden until it's approved. The recommended Replace behavior is above.
   - Should Replace also swap meme sounds (keeping the start time)? The board shows it on a sound.
4. **Sounds don't follow their clip.** In v1, items on other tracks keep their times when main clips ripple. That's simple and predictable, but a sound placed on a frame drifts if earlier clips are trimmed later.
   - I'd test it in the P1 build with the owner's usability script before proposing anything.
   - A "keep sounds with their clip" option would be a P4 proposal, if the test shows drift hurts.
5. **The board's "Duck audio" chip** isn't in the PRD's v1 audio list, so it's left out. No proposal now.

Nothing here needs the owner's decision beyond P1-16, which is already pending.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Tapping a clip selects it and reveals actions such as duplicate, split and delete. Trim handles stop when there are no more frames. Touch-and-hold lifts a clip off the timeline to rearrange it. | Guidance (Apple iMovie for iPhone help) | [Trim and arrange videos and photos in iMovie](https://support.apple.com/102353) |
| Separating a clip's audio puts it on its own track; a speaker icon on the clip mutes it | Guidance (vendor help) | [Clipchamp: mute, separate and delete audio](https://support.microsoft.com/en-us/clipchamp/how-to-mute-separate-and-delete-audio-from-video) |
| CapCut silences a clip by taking its volume to zero; a clip's "Original sound" setting controls whether its audio exports | Evidence (vendor resource pages, read through search results) | [CapCut: how to mute a video](https://www.capcut.com/resource/how-to-mute-a-video), [CapCut help: keep original sound](https://www.capcut.com/help/keep-original-sound) |
| Every dragging function needs a single-pointer alternative; the alternative only has to reach the same result, and buttons are the usual answer | Guidance | [WCAG 2.2 SC 2.5.7 Dragging Movements](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Compose custom accessibility actions are meant to replace complex gestures such as drag and drop | Guidance | [Compose semantics](https://developer.android.com/jetpack/compose/semantics) |
| Haptics: less is more; prefer clear, system-consistent effects; weigh how often an effect fires. `SEGMENT_TICK` is for stepping through choices, `CONFIRM` for a completed action, `LONG_PRESS` for a long press that triggers an action. | Guidance | [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles), [Compose HapticFeedbackType](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType) |
| A visible label must be part of the accessible name | Guidance | [WCAG 2.2 SC 2.5.3 Label in Name](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Use undo instead of warnings for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| CapCut swaps its bottom tool bar to clip tools on selection and shrinks clips to tiles while reordering | Assumption (product knowledge; not confirmed from CapCut's help this session) | — |
