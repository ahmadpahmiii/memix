# Clip tools and edits · P1-06 (with the P1-08 meme sound, P1-09 original audio and P1-16 add media and replace controls)

Part of a set of three for the video editor:
- `P1-04-editor-and-preview.md`: the screen frame, preview, transport, undo/redo and saving.
- `P1-05-timeline.md`: the timeline, selection, the "Add a meme sound" row, the original-audio toggle and the Add media tile.
- **P1-06 (this file):** the tool bars and every edit; the meme sound controls (P1-08), the original audio controls (P1-09), and Add media and Replace (P1-16).

## Job
Make the cuts a meme needs (split, trim, delete, duplicate, reorder) and control the sound: drop a meme sound at the playhead, then move, trim and level it; and mute, level or detach the clip's own audio.

**Done when** (TICKETS):

| Ticket | Done when |
| --- | --- |
| P1-06 | Each edit shows in the preview within 100 ms on the owner's phone; LeakCanary shows no leaks |
| P1-08 | The sound stays in sync within 1 frame in preview and export |
| P1-09 | Detached audio becomes its own audio-track clip |
| P1-16 | Media added from the editor lands after the clip under the playhead as one undo step, and a replaced clip keeps its start and length so sounds and text stay in sync |

**Performance bar (owner, 8 Oct):** there is no named reference phone. The bar is no visible lag and no memory leaks, on Android and iOS. The ticket numbers are measured on the owner's own phone with a release-type build.

**Later, not P1 (owner, 8 Oct; TICKETS → Later · Improvements):** keyboard shortcuts are L-01 and the locked-track UI is L-02. Sections marked **Later (L-01)** or **Later (L-02)** are drafts for those tickets: not built or checked in P1.

**Fast path, with tap counts:**
- **Meme sound:** the "Add a meme sound" pill or the Meme sounds tool (1), then a sound's add button (2). The sound lands at the playhead.
- **Cutting out the boring start:** scrub, tap the clip (1), Split (2), select the first piece (3), Delete (4). Or drag the start handle: one gesture.
- **Adding a clip mid-video (P1-16):** scrub, Add media (1), pick in the system picker (2, plus its own confirm when several are picked). Swapping a clip: tap it (1), Replace (2), pick (3).

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
| Picker and import sheet for Add media (P1-16) | The Add media tool or the Add media tile (P1-05) | Items land (Add media below); Cancel, close, back or nothing picked (nothing changes) |
| Picker for Replace (P1-16) | The Replace tool on a main video or photo clip | The new media takes the clip's place (Replace below), a refusal toast, or Cancel, close, back or nothing picked (nothing changes). The clip stays selected. |

## Layout

### Main tool bar (nothing selected)
Component ToolBar (`design/system/components/ToolBar/README.md`, updated with this spec):
- `surface`, `toolbar-height`, `hairline` top border.
- Items at least `tool-item-width` (64) wide, icon over a `caption` label, `space-1` gap, left-aligned, scrolling when they overflow. Labels are never cut (P1-06 review).
- Tools stay left-aligned so each keeps its position as later tickets add more. Before launch, a tool can sit one place to the left until the tool before it is built (for example Add media in place 2 if P1-16 ships before P1-08); no user ever sees that.

| # | Tool | Icon | Ticket | Does |
| --- | --- | --- | --- | --- |
| 1 | Edit | `edit` (new; the board's scissors) | P1-06 | Selects the main clip under the playhead (at the very end: the last clip) and shows its tools. It's the tap route to clip tools for clips too thin to hit. |
| 2 | Meme sounds | `sounds` | P1-08 | Opens the Meme sounds sheet. Always `primary` icon and label, because it's meme-sound UI. On `primary-subtle` while the sheet is open (the board's active look). |
| 3 | Text | `text` | P1-10 | Opens typing for a new caption at the playhead (`P1-10-text.md`). Not shown before P1-10. |
| 4 | Add media | `create` (existing plus) | P1-16 | Opens the P1-02 picker; the items land after the clip under the playhead (Add media below). The route for adding anywhere but the end. |
| later | Audio (P3), Stickers (P4-12), Effects (P4-06), Filters, Overlay (P4-01), Remove BG (P4-13), Speed (P4-09) | | | Each appears with its own ticket, never as a disabled placeholder |

- **Empty project** (P1-04 defensive state, no clips): Add media is the only tool. Edit would have nothing to select, so it isn't shown.

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
| Video clip (main track) | Split · Volume · Delete · Duplicate · Detach audio · Reorder · Replace (P1-16) |
| Photo clip (main track) | Split · Delete · Duplicate · Reorder · Replace (P1-16) |
| Main clip whose file is missing (P1-16 on) | **Replace first**, then the clip's usual tools in their order without Replace. Replace is the only tool that fixes it, and P1-04's toast names it, so it must show without scrolling. |
| Meme sound | Volume · Split · Delete · Move here · Duplicate |
| Audio clip (detached original audio) | Volume · Split · Delete · Move here · Duplicate |
| Text item | Edit text · Style · Position · Delete · Split · Move here · Duplicate (P1-10: Delete ahead of Split, because captions are deleted more often than cut) |
| Any item on a locked track: **Later (L-02)** | Unlock, then that item's usual tools |

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
  - `replace` (P1-16)
  - `lock` (for Unlock): Later (L-02)
- **Look:**
  - Tools: `text-secondary` icon and label.
  - Delete: icon and label in `danger`, 5.2:1 on `surface`, always with its word. `danger` is a status color, not a second accent (DESIGN_SYSTEM → Color). While pressed, on the `surface-raised` press fill, it's 4.3:1: accepted, because it lasts only while the finger is down (P1-06 review).
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
- **Board sample content** (`Sounds.dc.html`: "Boom hit", "Bruh (recreated)", "0:01 · CC0", …): the bundled pack's own metadata replaces it. The starter pack is `content/starter-sounds/manifest.json`: 10 CC0 sounds, each license approved by the owner on 8 Oct (updated 13 Oct; this line used to say release builds bundle none). Debug builds may add self-made test tones titled "Test tone 1…". The empty state stays as the defensive case.
- **Play:** plays that sound alone (the editor stays paused). Only one plays at a time. At its end, the row returns to idle. Fires `sound_preview`.
- **Add:** the sound lands (below), the sheet closes over 200 ms, and `sound_add` fires.
- **Close** (×, scrim, drag down, back, Escape): nothing is added and nothing changes.
- **Long-press on a row:** nothing in P1. P3 adds details, credit and Report.
- **Empty** (no sounds bundled): the P0-05 Sounds empty state, centered in the sheet: "NO SOUNDS YET" / "The meme sound library comes in a later build."

### Bubbles and guides (during drags)
- **Value bubble:** a `surface-raised` pill (`radius-full`, **no shadow**) holding one `timecode`-style value in `text`, with `space-1` vertical and `space-2` horizontal padding.
  - It floats `space-1` above the dragged item's row, centered on the moving edge (trim) or the item's start (move), and kept inside the timeline on both axes: never past its left, right or top edge. On the main row that puts it at the top of the timeline, over the ruler.
  - Trim shows the new duration; move shows the new start time.
  - **No shadow** (changed in the P1-06 review): on `canvas` a soft shadow is invisible, and where it lands on content it reads as dirt. The desktop renders show it as a gray falloff on the white handle and a brown smudge on an orange caption. The value step from `surface-raised` is enough, the same as the duration badge.
  - It fades in over 120 ms and goes at once on release, because the committed edit takes over (ripple, preview). The selection clears the same way (P1-05).
- **Snap guide:** while snapped, a `stroke-hairline` line in `text-secondary` across all lanes at the snap time. None is drawn when the snap target is the playhead, which is already a line. It appears and goes at once, together with the snap's tick, so the line marks the exact moment it snaps.

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
- Every main clip collapses into a square tile (`track-height-video`) showing its first frame, `space-1` apart, in order. When the mode starts, the selected clip's tile is under the finger (drag) or under the playhead (taps).
- The selected tile has the white selection outline.
- The ruler, the other lanes, the leading and trailing areas and the add row get a `scrim` wash and don't respond. **Only the tile strip's row (the main row, 48 dp) takes touches.** A touch anywhere else in the timeline is taken and does nothing: it doesn't scroll, select, grab or move a tile (P1-06 review M1).
- **The playhead fades out with the wash** and back in as the tiles expand. Tiles aren't laid out in time, so a white line crossing one would read as a second selection mark (P1-06 review).
- Collapsing makes a long video reorderable without dragging across minutes of timeline. iMovie lifts a held clip off the timeline the same way (Guidance), and CapCut shrinks clips to tiles (Assumption: product knowledge).

**Moving by drag:**
- The tile follows the finger horizontally.
- When its center passes a neighbor's center, the neighbor slides over (200 ms) to open the slot.
- Near the screen edges, the strip scrolls (Edge auto-scroll).

**Moving by taps:** the tool bar shows the reorder bar: **Move earlier**, **Move later** and **Done** (layout below).
- Each tap moves the clip one slot (200 ms).
- Tapping another tile moves the selected clip to that tile's position.
- At either end, the button explains: "This clip is already first." / "…last."
- **The moving tile always stays in view** (P1-06 review M2). After a move by a button or a tile tap, if the tile's new place is closer than `touch-target` to either side of the timeline, the strip scrolls with the slide (200 ms; at once with reduce motion) so the tile sits `touch-target` in from that side. Without this, on a project with more than about eight clips, a few taps of Move later push the tile off the screen while it keeps moving, unseen.

**Reorder bar** (approved as built in the P1-06 review; it wasn't in the first spec):
- It replaces the clip tool bar in the same region: `surface`, at least `toolbar-height`, the same `stroke-hairline` `hairline` top border as the ToolBar, `space-2` padding.
- **Move earlier** and **Move later:** Secondary buttons, in that order from the left, so the button's side matches the direction.
- **Done:** a Quiet button (`primary` text, no fill), after them. Blue because it's the mode's one confirming action: Done commits the order and back cancels it. That's unlike a ToolPanel's Done (Ghost), which only closes a panel whose changes already applied. In reorder mode nothing else is blue: the meme sounds sit under the wash.
- Words, not icon tools: no icon for "earlier" and "later" exists, and the words carry the direction in every language.
- The row wraps at large font sizes and the bar grows (render: Hindi at 200% puts Done on the second line beside Move later). Nothing is cut. All three fit one line at 360 dp in English.

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
- **Other tracks don't follow the main track.** When main clips ripple (trim, delete, duplicate, reorder), items on other lanes keep their times. A sound placed on a frame can drift off it if earlier clips change afterwards. That's the simple model every mobile editor starts from (Assumption). Undo and Move fix it. **This stays as specified for P1 (owner, 8 Oct):** the owner tries it on the P1-06 build before deciding whether a "keep sounds with their clip" option is worth adding (Questions).

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
- **Past the end while trimming** (P1-06 review polish P8, a follow-up, not in ea38fc6): while a handle is held, the scroll may go past the end of the video, as far as that handle's own limit. Then extending the last clip (or a photo, which has no limit) isn't capped at what the screen shows. On release, the view settles at the new end. Until then, the way round is to release and drag again, or zoom out first.

### Preview after an edit (P1-06 Done when)
Reworded 10 Oct (PM ready check) to say what the user sees, not how the engine works.
- **The timeline first:** every committed edit (a tap, or a drag on release) shows on the timeline at once (owner, 9 Oct).
- **Then the preview:** it keeps showing its last frame and switches to the edited result at the playhead as soon as that's ready. It never flashes black or gray in between.
- **Target:** the preview shows the edit within 100 ms on the owner's phone (TICKETS). P1-06 is built without holding to that number (owner, 9 Oct). It's measured at phase-end QA (risk R1), and a fix ticket follows if edits take longer. How the engine gets there is the engineer's call (TECHNICAL_DESIGN).
- **During a drag,** only the timeline changes, at 60 fps. The preview shows just what the drag needs (the frame at the moving edge while trimming, Trim above). The edit itself reaches the preview on release (`mobile-performance` skill).
- **Any edit pauses playback first** (P1-04).

### Preview catching up (after an edit, until the preview shows it)
- **What stays:** the last frame. Nothing goes black, gray or blank.
- **What still works:**
  - Play switches to Pause at once and playback starts as soon as the preview is ready (P1-04's optimistic Play).
  - Scrubbing moves the timeline, and the preview shows the right frame once it's ready.
  - Another edit simply replaces the one being prepared.
- **Up to 1 second: no indicator.** The timeline has already confirmed the edit, and an indicator that blinks on for a few hundred milliseconds after each edit is noise. Between 0.1 and 1 second people notice a delay but keep their train of thought, and no special feedback is needed (Nielsen's response-time limits, Guidance).
- **After 1 second:** a quiet status, "Updating the preview", in the stage's message slot (P1-04: one slot at the top of the stage).
  - Toast look: `surface-raised`, `label` in `text`, `shadow-float`. No action, no ×, no spinner or animation (MOTION 2).
  - It fades in over 120 ms and fades out over 120 ms when the edited frame shows.
  - The second is counted from the first edit the preview hasn't caught up with, so a quick run of edits doesn't hide a long wait.
  - If the slot is busy (the save banner, or a toast such as "Undo: Trim"), the status doesn't show. One slot, banner first (P1-04).
  - Over 1 second is the exception the R1 measurement looks for. If the owner's phone shows it after ordinary edits, that's a QA finding for the R1 fix ticket, not a reason to make the status louder.
- **If preparing fails:** P1-04's preview error state takes over.
- **TalkBack:** the status isn't announced. The edit's own announcement has already played ("Delete: Boom"), and the preview is visual.
- **Reduce motion:** the same; it's a fade.

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
- **Undo** is one step ("Mute original audio" / "Unmute original audio"). It fires `tool_use` `mute_original` or `unmute_original` (one id per direction, PM 8 Oct).
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

## Locked items: Later (L-02)
Not built or checked in P1 (owner, 8 Oct; TICKETS → Later · Improvements, L-02). Nothing in P1 locks a track. This section is the draft for L-02: `Track.locked` exists and later templates may set it (P1-05 → Locked).
- **A tap selects the item:** outline, no handles.
- **A long-press doesn't lift it.** It selects the item and explains "This track is locked. Unlock it to edit."
- **The clip tool bar** starts with **Unlock** (icon `lock`), then the usual tools. Each of those explains the same way on tap.
- **Unlock** clears `Track.locked` for the whole track. Undo is one step ("Unlock"), and it fires `tool_use` `unlock`.
- **Locked main track:** ripple edits from other main clips are refused with the same explanation.

## Add media (P1-16, final 13 Oct)
P1-16's Done when: "Media added from the editor lands after the clip under the playhead as one undo step." Two entries, each placing items where it says.

**Entries:**
1. **The Add media tool** (main tool bar, place 4, icon `create`). The items land **after the clip under the playhead**. This is the route to anywhere in the video, and the one the Done when describes.
2. **The Add media tile** at the end of the main track (P1-05 → Trailing area). Its items are **always added at the end of the video**, whatever the playhead: the tile sits there, so that's where they go. Without this rule, a short last clip plus a playhead a little before it would put new clips before the last one, right after a tap on the tile behind it.

**Why a tool and not "zoom out first"** (PM ready check, 13 Oct, item a):
- At the default zoom (40 dp a second) the tile is on screen only while the playhead is within about 4.5 s of the end: half of a 360 dp screen.
- Even "Show whole video" fits the video into half the width, so the tile shows only once the playhead is past about a quarter of the video. And landing the playhead on an exact clip at that zoom is fiddly.
- A route that works only at some zoom levels and playhead spots isn't a route. The tool works from anywhere in one tap, and like every other main tool (Meme sounds, Text) it acts at the playhead, where the eye already is (P1-05 → Playhead).
- The tool bar has room: five tools fit at 360 dp, so Add media in place 4 never needs a scroll. Both entries are inside P1-16 (PM, 13 Oct).

**Picking and copying:** the same picker, settings, limit (35 per pick, no project limit), import sheet and results as P1-02. "Some didn't go in" → Continue returns to the editor with what went in. "None went in" → Pick again. Cancel, close or nothing picked: the editor is unchanged, with no step and no event.

**Where the items land:**
- **Tool:** after the clip under the playhead. If the playhead sits exactly on a boundary between two clips, they land there. At the end, they're appended. It never splits a clip.
- **Tile:** at the end of the video.
- **Empty project** (P1-04 defensive state): at 0:00, from either entry.
- In the order they were picked, with the same import rules as P1-02 (a photo's length as in P1-02).
- Later main clips move right by the added length. Items on other lanes keep their times, the same as every ripple (owner, 8 Oct; part of the drift trial).

**After landing:**
- The playhead moves to the start of the first new clip, and the new clips bonk once (`duration-bonk`).
- The selection and the tool bar don't change.
- TalkBack hears "3 clips added" (`a11y_media_added`). Focus returns to the tool or tile that opened the picker.
- **Undo** is one step ("Add media") that removes the whole pick. `tool_use` `add_media` fires once, when the items land, never per item. No `project_create`.
- A save failure afterwards uses P1-07's save banner (P1-02 → Ran out of space).

## Replace (P1-16, final 13 Oct; video and photo clips only)
**Where:**
- The clip tool bar on main video and photo clips, last (after Reorder).
- **On a clip whose file is missing: first.** It's the one tool that fixes that clip, and P1-04's toast now names it ("Replace or delete that clip to fix it", `editor_missing_media` with P1-16).
- Icon `replace` (request 1).

**Tap:** opens the system picker for **one** item (videos and photos). Copying uses P1-02's import sheet when it takes over 300 ms ("1 of 1"). Cancel, close or nothing picked: nothing changes, with no step and no event. An item that can't be read or won't play gets P1-02's "None went in" result (Pick again).

**What fits:**
- **A photo** always fits: it shows for the clip's length.
- **A video at least as long as the clip** is used from its start: trim in 0, trim out = the clip's length.
- **A video shorter than the clip** isn't used. A toast explains: "That video is shorter than this clip (00:03.20). Pick a longer one, or trim this clip first." (`explain_replace_too_short`, with the clip's length as the timecode). Nothing changes, and the new copy is deleted. Shrinking the clip would ripple the main track and pull everything after it out of sync, which is exactly what Replace promises not to do.

**What's kept and what changes:**
- **Kept:** the clip's id, start, length, place on the main track and **volume**. Replace keeps the slot's mix: a clip turned down under a meme sound stays down for the new video. Photos always carry 100%, so a photo replaced by a video plays at 100%.
- **Changed:** the source and the trim (in 0). Thumbnails reload by P1-05's loading rule.
- **Not touched:** sounds, text and every other lane, and "Mute original audio of every clip" (`Track.muted`). That's the point of Replace.
- A later per-clip setting (speed P4-09, filters P4-06) says in its own spec whether it carries over. P1 clips have none.

**A clip whose sound is detached** (PM item c; P1-09 builds Detach, but debug projects already hold detached clips, so P1-16 builds this rule):
- The detached audio clip stays exactly as it is. It's independent once detached (Detach audio → After detaching).
- The new media's own sound is attached (`audioDetached` false, so the "Sound detached" state and its explanation no longer apply) and **starts at 0%**. The slot then sounds the same as before: the detached sound, not doubled. That's the same "nothing suddenly gets louder" rule as detaching while muted.
- When the new media is a video with sound, a toast says so: "The detached sound stays, so this clip's own sound starts at 0%." (`explain_replace_detached`). It can be raised in the Volume panel (P1-09). A photo or a silent video gets no toast.
- This also makes "keep the sound, swap the picture" a two-step meme move: Detach audio, then Replace.

**Afterwards:**
- The clip stays selected, and the new media bonks once: it's content landing.
- The playhead doesn't move. The preview catches up as after any edit (Preview catching up).
- A missing clip is fixed: its "File missing" label and state go, because the new copy exists.
- **Undo** is one step ("Replace", `edit_replace`). TalkBack hears "Replace: clip 2" (`a11y_edit_on_item`). It fires `tool_use` `replace_clip`. The old copy stays on disk while undo can still bring it back.

**Meme sounds: no Replace (owner, 8 Oct).** In P1, swapping a sound is Delete plus add at the playhead; revisit with the P3-05 picker spec. The board shows Replace on a selected sound; that's dropped. Text and overlays (P4-01) have no Replace either.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Nothing selected | Main tool bar: Edit, Meme sounds (blue), Text (P1-10), Add media (P1-16) | `tool_edit`, `tool_meme_sounds`, `tool_add_media` |
| Empty project (P1-16 on) | Main tool bar: Add media only | `tool_add_media` |
| Video clip selected | Clip tool bar: Close, divider, Split · Volume · Delete · Duplicate · Detach audio · Reorder · Replace | `clip_tools_close`, `tool_*` |
| Photo clip selected | Split · Delete · Duplicate · Reorder · Replace | same |
| Missing-file clip selected (P1-16 on) | Replace first, then the usual tools | same |
| Meme sound or audio clip selected | Volume · Split · Delete · Move here · Duplicate | same |
| Split not possible here | Split looks normal; a tap shows the toast | `explain_split` |
| Only one main clip | Delete and Reorder explain on tap | `explain_last_clip`, `explain_reorder_one` |
| Trimming | Handle follows the finger; value bubble with the duration; snap guide when snapped; a gap or overlap on the main track until release | none |
| Trim at the footage limit | Handle stops; one tick | none |
| Preview catching up (after an edit) | The timeline already shows the edit; the preview keeps its last frame, with no black or gray flash. Up to 1 s nothing else shows. After 1 s, a quiet status at the top of the stage until the edited frame shows (Preview catching up). | `editor_preview_updating` |
| Moving a sound | Lifted item with outline, value bubble with the start time; drop on the add row makes a new lane | none |
| Reorder mode | Tiles, washed surroundings, playhead faded out; the reorder bar: Move earlier · Move later · Done (Quiet, blue). The moving tile stays in view. | `reorder_move_earlier`, `reorder_move_later`, `panel_done`, `explain_reorder_first`, `explain_reorder_last`, `a11y_reorder_tile`, `a11y_reorder_position` |
| Media added (P1-16) | New clips after the clip under the playhead (tool) or at the end (tile); playhead at the first new clip; bonk | `a11y_media_added` |
| Replaced (P1-16) | Same start and length, new thumbnails, bonk, still selected | `edit_replace` in `a11y_edit_on_item` |
| Replace refused: video too short | Toast; nothing changes | `explain_replace_too_short` |
| Replaced a clip whose sound was detached | Detached audio unchanged; new sound at 0%; toast (video with sound only) | `explain_replace_detached` |
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
| Locked: **Later (L-02)** | Unlock first; other tools explain | `tool_unlock`, `explain_locked` (L-02) |
| Long content | Tool labels wrap to two lines inside `tool-item-width` (64); at large font scales items widen when a word can't fit, labels may take a third line, and the bar grows. Labels are never cut (Accessibility). Long sound titles end in an ellipsis on the clip and wrap to two lines in the sheet. | |
| Offline, permission | Doesn't apply: everything is on the phone; no permission needed | none |

## Motion and haptics
| What | Motion | Reduce motion |
| --- | --- | --- |
| Main ↔ clip tool bar ↔ reorder bar | 120 ms cross-fade | same |
| Volume panel opens or closes | 200 ms slide up or down over the timeline region | 120 ms fade |
| Meme sounds sheet | 200 ms slide over a fading scrim | 120 ms fade |
| Sound lands, duplicate lands, detached audio appears, media added or replaced (P1-16) | The bonk: 1 → 1.12 → 1 over 240 ms (`duration-bonk`) | 120 ms fade-in |
| Ripple after trim, delete, duplicate or add media on the main track | Clips slide 200 ms | 120 ms cross-fade to the new layout |
| Reorder: collapse, make room, expand; the playhead fades with the wash | 200 ms each | 120 ms fades |
| Reorder: the strip scrolls to keep the moving tile in view | 200 ms, with the slide | at once |
| Value bubble | 120 ms fade in; goes at once on release | same |
| Snap guide | appears and goes at once, with the tick | same |
| Toasts | 120 ms fade (P1-04) | same |

**Reduce motion on Android** (P1-06 review): Memix reads it as "Remove animations", animator duration scale 0, and Compose ends every animation on the next frame at that scale. So on Android each "Reduce motion" cell above shows as an instant change, and the build's 120 ms reorder morph and its jumps look the same as the fades would. Accepted: nothing moves. The cells stay as written for iOS (P7), where Reduce Motion doesn't shorten animations, so each movement must really become the fade (DESIGN_SYSTEM → Motion).

Haptics, kept rare per Android's guidance (Guidance). They use Compose `HapticFeedbackType`; check the names against the current docs.

| Event | Type |
| --- | --- |
| A meme sound lands | Confirm |
| Long-press lifts an item or enters reorder; long-press on the ruler opens the zoom menu (P1-05) | LongPress |
| Snap engages; a trim reaches its footage or neighbor limit; the volume slider snaps to 100% | SegmentTick, once per engagement |
| Anything else | none |

The system's own haptics setting is respected.

## Copy
English source; the engineer machine-drafts id, es, pt and hi until P5. Budgets are characters. Tool labels sit under a 24 dp icon in a `tool-item-width` (64) item: at most two lines of about 10 characters at 100% font. That's a budget, not a cap: at 200% a label may take a third line, and it's never cut.

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
| `tool_unlock` | Unlock | 20 | Later (L-02): don't add in P1 |
| `tool_replace` | Replace | 20 | P1-16. Swap this clip's video or photo for another, keeping its place and length |
| `tool_add_media` | Add media | 20 | P1-16. Main tool bar: adds videos or photos at the playhead. "Media" = videos and photos; use the word your language's editing apps use |
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
| `explain_locked` | This track is locked. Unlock it to edit. | 50 | Later (L-02): don't add in P1 |
| `explain_sound_unreadable` | That sound won't play. Try another one. | 50 | |
| `explain_replace_too_short` | That video is shorter than this clip (%1$s). Pick a longer one, or trim this clip first. | 110 | P1-16. %1$s = the clip's length as a timecode, "00:03.20" (never translated) |
| `explain_replace_detached` | The detached sound stays, so this clip's own sound starts at 0%. | 70 | P1-16. After replacing a clip whose sound was detached, when the new media is a video with sound. Write a single %: Compose resources don't double it |
| `editor_preview_updating` | Updating the preview | 32 | Quiet status at the top of the preview while it catches up with an edit that took over a second. Not a warning; no ellipsis character. |
| `a11y_sound_added` | %1$s added at %2$s | 70 | %1$s = sound title, %2$s = spoken time (P1-04) |
| `a11y_edit_on_item` | %1$s: %2$s | 70 | Announced after each edit: %1$s = edit name, %2$s = item label ("Delete: Boom") |
| `a11y_clip_label` | clip %1$d | 12 | Added in the P1-06 review (approved as drafted). How spoken text names a main clip inside a sentence: "Start of clip 2", "Delete: clip 2". Lowercase because it sits mid-sentence; %1$d = the clip's number in the main track. Use your language's word for a video clip, as in `a11y_clip_media` |
| `a11y_reorder_tile` | %1$s, clip %2$d of %3$d | 40 | Added in the P1-06 review (approved as drafted). A tile in reorder mode: %1$s = "Video" or "Photo" (`import_item_*`), %2$d = its place now, %3$d = the number of clips. "Video, clip 2 of 3". Reorder placeholders freely |
| `a11y_reorder_position` | Clip %1$d of %2$d | 24 | Added in the P1-06 review (approved as drafted). Announced after each move in reorder mode: the moving clip's new place. "Clip 2 of 3" |
| `a11y_media_added` | one: 1 clip added · other: %d clips added | 30 | P1-16. Plural resource. Announced after Add media lands; counts what went in |
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
| `edit_add_media` | Add media | 24 | P1-16 |
| `edit_replace` | Replace | 24 | P1-16 |
| `edit_unlock` | Unlock | 24 | Later (L-02): don't add in P1 |

Translator notes:
- **Edit names** appear after "Undo: " and "Redo: ". Use the noun or verb form that reads naturally there, and keep it short.
- **Explanations** say what to do next. Never blame the user.
- **Tool labels:**
  - Portuguese and Spanish run longest. Hindi needs extra line height (the system Devanagari font).
  - Two lines are fine; three aren't (at 100% font). If a label won't fit in two lines of about 10 characters, choose a shorter synonym. At 200% the app lets a label take a third line rather than cut it.
- **"Meme"** stays as is if your language uses the loanword.

## Accessibility
- **Tool bar:** every tool's visible label is its accessible name (WCAG 2.2 SC 2.5.3), except Move here, which is spoken "Move to playhead". Close clip tools is icon-only and spoken. Focus order runs left to right. The tool bar comes after the timeline in the editor's focus order (P1-04).
- **Add media and Replace** (P1-16): when the picker or the import sheet closes, focus returns to the tool (or the timeline tile) that opened it. Replace keeps the clip selected, so its tool is still there.
- **After each edit**, a polite announcement reads `a11y_edit_on_item` ("Delete: Boom") or `a11y_sound_added`. Explanation toasts are announced too. Nothing takes focus (SC 4.1.3). The "Updating the preview" status isn't announced (Preview catching up).
- **Accessibility actions on the selected item** (TalkBack's actions menu, Switch Access):
  - Split
  - Delete
  - Duplicate
  - Move earlier / Move later (main clips)
  - Move to playhead (other items)
  - Its handles carry "Trim to playhead" (P1-05)

  These are the single-pointer routes that replace dragging (SC 2.5.7, Guidance; Compose custom actions are meant for exactly this, Guidance).
- **Reorder mode:**
  - Tiles are a list. Each reads "Video, clip 2 of 3" with its position (`a11y_reorder_tile`).
  - The selected tile is marked selected.
  - Move earlier, Move later and Done are buttons. After each move, "Clip 2 of 3" is announced politely (`a11y_reorder_position`).
  - **Focus when the bar swaps** (P1-06 review polish P6): starting reorder mode from the Reorder tool removes the focused button, so focus moves to Move earlier. Done, a drop or back returns focus to the Reorder tool in the clip tool bar that comes back.
  - Only the tiles on screen are in the list (Compose leaves off-screen nodes out; P1-05). That's enough: Move earlier and Move later reach every place, the announcement says where the clip is, and the moving tile stays in view (M2).
  - TalkBack users don't need reorder mode at all: the item actions Move earlier and Move later on a selected main clip commit at once.
- **Volume panel:**
  - Opening it moves focus to its title. Done returns focus to the Volume tool.
  - The slider follows the Slider component: adjustable, with the readout as its state.
  - The toggle is a switch.
- **Meme sounds sheet:**
  - Focus moves to the sheet, and its title is the pane title.
  - Each row's play and add buttons are labeled with the sound's name (SoundRow's existing strings).
  - When the sheet closes after adding, focus goes to the new sound on the timeline, and its announcement plays.
- **Keyboard in P1** (basic access, not shortcuts): Tab reaches every tool, panel control and sheet button, and Enter activates it. **Escape** does the same as system back: it closes the panel or sheet, cancels reorder, or clears the selection (P1-04 back order).
- **Keyboard shortcuts: Later (L-01)**, not built or checked in P1. Draft for L-01, alongside the P1-04 and P1-05 drafts:

  | Key | Action |
  | --- | --- |
  | Ctrl+B | Split |
  | Delete / Backspace | Delete |
  | Ctrl+D | Duplicate |
  | Alt+Left / Alt+Right | Move earlier or later (main clips), or nudge by one frame (other items) |

- **Touch targets:**
  - Tools, Close clip tools, handles, the sheet's buttons and the panel's controls are all 48 dp or more.
  - Tiles in reorder mode are 40 dp drawn, in 48 dp targets. Neighboring targets overlap only within the `space-1` gap, so the tile the touch lands on wins.
- **Contrast:**
  - Tool labels: `text-secondary` on `surface`, 5.7:1. Delete: `danger` on `surface`, 5.2:1. The Meme sounds tool: `primary` on `surface`, 7.3:1.
  - Value bubble: `text` on `surface-raised`, 12.4:1 or more.
  - Handles: white against `canvas` 18.9:1, against a `surface` band 17.2:1.
- **200% font scale:**
  - Tool labels wrap, and items widen beyond `tool-item-width` when a word can't break. A label of three short words can take three lines (for example a three-word "Meme sounds" in Spanish or Portuguese), so labels have no line cap. The bar grows taller to fit and keeps scrolling sideways. Checked in the P1-06 renders: Hindi at 200% at 360 dp keeps Split and Delete in view, "क्रम बदलें" takes two lines, nothing is cut.
  - The reorder bar wraps onto a second line (Hindi at 200%: Done beside Move later).
  - The panel's rows wrap.
  - Sheet rows grow; the meta line wraps.
  - The value bubble grows.
- **Reduce motion:** see Motion. Haptics don't depend on it.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Once per committed undo step, never during a drag, never when a tool only explains | `editor: "video"`, `tool`: `split`, `trim`, `delete`, `duplicate`, `reorder`, `move`, `volume`, `mute_original`, `unmute_original`, `detach_audio`; `undo` and `redo` (P1-04); `add_media` (once per pick, when the items land) and `replace_clip` (P1-16); later `unlock` (L-02). Ids confirmed by the PM on 8 Oct. A cancelled pick, a refused Replace or nothing added fires nothing. |
| `sound_add` | A meme sound lands from the sheet. Not for duplicates (those are `tool_use` `duplicate`). | `sound_id` (catalog id of the bundled sound), `tab`: `"starter"` (accepted by the PM, 8 Oct), `editor: "video"` |
| `sound_preview` | A sound starts playing in the sheet | `sound_id`, `tab: "starter"` |

- **Opening a panel or the sheet,** selecting and zooming fire nothing.
- **Analytics stays off until consent** (P5-01). The events are coded now so they're ready.

## QA compares
1. **Video clip selected:** the clip tool bar at 360 dp, with Split, Volume and Delete visible without scrolling. Then the same in Hindi at 200% font.
2. **Trim in progress** on a main clip: handle, value bubble, the gap left on the near side. Then the closed-up result.
3. **A meme sound just landed:** bonk end state, the sound selected with sound tools, the sheet closed. Plus the sheet itself with the starter pack's sounds and with the empty state (a build with no pack, if one can be made).
4. **Volume panel** on a video clip: slider at 100%, the mute-every-clip toggle; preview and transport visible above.
5. **Reorder mode** by the Reorder tool: tiles, washed surroundings, the playhead faded out, the reorder bar (Move earlier, Move later, Done in blue text) with its hairline top border. Then, on the 20-clip project, the moving tile still on screen after six taps of Move later.
8. **Add media and Replace (P1-16):** the main tool bar with Add media in place 4; clips added mid-video with the tool (after the clip under the playhead) and at the end with the tile; a replaced clip with the same start and length; the "too short" toast; a missing-file clip's tool bar with Replace first.
6. **Detached audio:** the green "Original audio" clip on its lane under the clip, and the explanation when Detach audio is tapped again.
7. **Preview catching up:** a screen recording of a delete on the 20-clip project. Every frame between the edit and the edited preview shows the old frame: none black or gray. If an edit takes over 1 s, a still of the "Updating the preview" status.

Measured (note in the QA report):
- **P1-06 Done when:**
  - Screen-record the emulator or phone at 60 fps.
  - For each edit (split, trim release, delete, duplicate, reorder drop, volume release), count frames from the commit to the preview change. Pass: 6 frames (100 ms) or fewer. Owner, 9 Oct: P1-06 is accepted without this number; record it for R1, plus how often the "Updating the preview" status appeared.
  - In the same recordings, note any black or gray frame in the preview. One is a fail (Preview after an edit).
  - Split shows no preview change by design; check that the timeline updates instead.
  - On the owner's phone with a release-type build; emulator numbers are notes only. Pass also needs no visible lag.
- **Leaks:** a debug build with LeakCanary. Open and close the editor 5 times and make every edit; no leak reported.
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
   - `replace` (P1-16): one solid rectangle in the middle (the clip, the core) between two curved strokes, one above it running left to right and ending in a solid arrowhead pointing down, one below it running right to left and ending in a solid arrowhead pointing up. A swap cycle around a single clip, so it can't be mistaken for `reorder` (two clips under one arc) or for undo and redo.
   - Add media (P1-16) reuses `create`; no new icon.
   - `lock` waits for L-02.
2. **Components:**
   - ToolPanel (`design/system/components/ToolPanel/README.md`) and Menu (`design/system/components/Menu/README.md`), in `:core:designsystem` and the debug catalog.
   - ToolBar gains the clip mode: leading Close clip tools button, divider, `danger` Delete.
   - ToolBar (P1-06 review): the item width reads the new token `MemixSize.toolItemWidth` (64), and labels have no line cap.
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
7. **Starter pack:** read the title, duration, credit and catalog id from the bundled pack's metadata (`content/starter-sounds/manifest.json`, the same fields as the catalog: `credit` comes from the license record). Debug builds may add self-made test tones.
8. **Haptics:** Compose `HapticFeedbackType.Confirm`, `LongPress` and `SegmentTick`, as in the Motion and haptics table. Verify the names in the current Compose docs.
9. **Analytics:** as in the table. `sound_add.tab` is `"starter"` (PM, 8 Oct) until P3-05's spec sets the real tab ids.
10. **Preview catching up:** keep the last shown frame on screen until the edited preview has drawn its first frame, then swap, so nothing black, gray or blank ever shows. However the engine rebuilds, that's the visible rule. Show the status after 1 s, as in Preview catching up, and add `editor_preview_updating` in all five languages.

## Proposals and questions for the PM
1. **`sound_add.tab` and `sound_preview.tab` for the starter sheet.** **Decided (PM, 8 Oct):** `"starter"` until P3-05 brings real tabs.
2. **`tool_use` values.** **Decided (PM, 8 Oct):** ids as in the Analytics table, with one id per direction for mute (`mute_original`, `unmute_original`). `unlock` waits for L-02.
3. **P1-16. Decided (owner, 8 Oct):** approved. Replace covers video and photo clips only; in P1, swapping a meme sound is Delete plus add at the playhead. The Replace slot and the Add media tile (P1-05) stay hidden until P1-16 is built. **Finalized 13 Oct** (PM ready check items a–d): Add media and Replace sections above, with a second entry (the Add media tool) inside P1-16's scope. **For the PM's ticket check:** the Done when's "after the clip under the playhead" describes the tool; the tile at the end of the track always appends (its place is its meaning). Replace keeps the clip's volume, so the "only media and trim changed" JSON check holds, except that a clip whose sound was detached also gets `audioDetached` false and volume 0%.
4. **Sounds don't follow their clip.** In v1, items on other tracks keep their times when main clips ripple. That's simple and predictable, but a sound placed on a frame drifts if earlier clips are trimmed later.
   - **Stays as specified (owner, 8 Oct).** The owner tries it on the P1-06 build before deciding. QA's P1 plan and the usability script include "add a sound on a moment, then trim the first clip's start".
   - A "keep sounds with their clip" option would be a proposal before P4 planning, if the trial shows drift hurts.
5. **The board's "Duck audio" chip** isn't in the PRD's v1 audio list, so it's left out (PM agreed, 8 Oct).
6. **Keyboard shortcuts and the locked-track UI** moved to Later, L-01 and L-02 (owner, 8 Oct). The sections here stay as drafts for those tickets.

Open for the owner: only item 4, after trying the P1-06 build.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Tapping a clip selects it and reveals actions such as duplicate, split and delete. Trim handles stop when there are no more frames. Touch-and-hold lifts a clip off the timeline to rearrange it. | Guidance (Apple iMovie for iPhone help) | [Trim and arrange videos and photos in iMovie](https://support.apple.com/102353) |
| Separating a clip's audio puts it on its own track; a speaker icon on the clip mutes it | Guidance (vendor help) | [Clipchamp: mute, separate and delete audio](https://support.microsoft.com/en-us/clipchamp/how-to-mute-separate-and-delete-audio-from-video) |
| CapCut silences a clip by taking its volume to zero; a clip's "Original sound" setting controls whether its audio exports | Evidence (vendor resource pages, read through search results) | [CapCut: how to mute a video](https://www.capcut.com/resource/how-to-mute-a-video), [CapCut help: keep original sound](https://www.capcut.com/help/keep-original-sound) |
| Every dragging function needs a single-pointer alternative; the alternative only has to reach the same result, and buttons are the usual answer | Guidance | [WCAG 2.2 SC 2.5.7 Dragging Movements](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Compose custom accessibility actions are meant to replace complex gestures such as drag and drop | Guidance | [Compose semantics](https://developer.android.com/jetpack/compose/semantics) |
| Up to 0.1 s feels instant; between 0.1 and 1 s people notice the delay but keep their train of thought, and no special feedback is needed; past 1 s they lose the feeling of working directly on the data; 10 s is the limit for keeping attention | Guidance (Nielsen 1993, still NN/g's reference; read through search results, the article page wasn't reachable here) | [NN/g: the 3 response-time limits](https://www.nngroup.com/videos/3-response-time-limits-interaction-design/), [Nielsen, Time scales of UX](https://www.uxtigers.com/post/time-scales-ux) |
| Haptics: less is more; prefer clear, system-consistent effects; weigh how often an effect fires. `SEGMENT_TICK` is for stepping through choices, `CONFIRM` for a completed action, `LONG_PRESS` for a long press that triggers an action. | Guidance | [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles), [Compose HapticFeedbackType](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType) |
| A visible label must be part of the accessible name | Guidance | [WCAG 2.2 SC 2.5.3 Label in Name](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Use undo instead of warnings for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Compose scales its animations by the system animator duration scale (from Compose 1.2); at 0 an animation ends on the next frame | Guidance (API reference; the scale-setting behavior confirmed by a Compose maintainer in the Kotlin Slack, read through search results) | [Compose MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale), [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/505584/in-latest-compose-the-animation-duration-scale-now-affects-t) |
| CapCut swaps its bottom tool bar to clip tools on selection and shrinks clips to tiles while reordering | Assumption (product knowledge; not confirmed from CapCut's help this session) | — |
