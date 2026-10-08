# Timeline · P1-05 (with the timeline parts of P1-08 and P1-09)

Part of a set of three for the video editor:
- `P1-04-editor-and-preview.md`: the screen frame, preview, transport, undo/redo and saving.
- **P1-05 (this file):** the timeline, including how meme sounds (P1-08) and original audio (P1-09) look on it.
- `P1-06-clip-edits-and-audio.md`: the tool bars and every edit, plus the meme sound and original audio controls.

## Job
Show the whole meme in time so the user can put the playhead on the exact frame ("drop a boom hit on the exact frame of a fail") and pick the one thing to edit.

**Done when** (TICKETS): the timeline scrolls at 60 fps on the owner's phone; LeakCanary shows no leaks.

**Performance bar (owner, 8 Oct):** there is no named reference phone. The bar is no visible lag and no memory leaks, on Android and iOS. The ticket numbers are measured on the owner's own phone with a release-type build.

**Later, not P1 (owner, 8 Oct; TICKETS → Later · Improvements):** keyboard shortcuts are L-01 and the locked-track UI is L-02. Sections marked **Later (L-01)** or **Later (L-02)** are drafts for those tickets: not built or checked in P1.

**Color roles:**
- **Blue:** no primary button lives here. Blue appears only where something is a meme sound: sound clips (`track-meme-sound`) and the "Add a meme sound" row.
- **White:** selection only: the playhead, the selected item's outline and its trim handles.

## Entry and exit
The timeline is part of the editor (P1-04). On every open:
- the playhead is at 0:00
- zoom is the default (40 dp per second)
- nothing is selected
- the lanes are scrolled to the top

Zoom and scroll aren't saved.

## Layout
Board: `design/screens/VideoEditor.dc.html`; component `design/system/components/Timeline/README.md` (updated with this spec).

**Differences from the board, and why:**
1. **No fixed track-label column.** The board spends 72 dp on "Video", "Text", and so on. That's 20% of a 360 dp screen, taken from the area that needs width most for precise timing. Instead, a track is told apart by:
   - its fixed place in the order
   - its color
   - the label on every non-video clip (sound title, "Original audio", the caption's text)
   - its spoken name for TalkBack

   That keeps color from being the only cue (WCAG 1.4.1, Guidance). CapCut, the editor our users already know, has no label column either (Assumption: product knowledge).
2. **The selected-clip bar above the tool bar is gone.** The tool bar itself switches to clip tools (P1-06). The board's chips were 36 dp tall, under `touch-target`.
3. **New "Add a meme sound" row** under the sound lanes, anchored at the playhead (P1-08).
4. **New original-audio toggle** at the head of the main video track (P1-09).
5. **Lane bands:** `surface` bands from 0:00 to the end of the video, so the user can see where the video stops.
6. **Main video clips show thumbnails, not file names.** The model stores no file names, and names like "clip_01.mp4" mean nothing in a meme.
7. **Size changes:** the ruler goes from 20 to 24 dp tall, so a tap on it is easier to hit; the main video row from 46 to 48 dp.

**Board sample content and what replaces it:**

| Board | Real content |
| --- | --- |
| "clip_01.mp4", "clip_02.mp4" | Thumbnails. The duration badge shows only when the clip is selected (below). |
| "When the build…" (text) | The text item's own text, first line (P1-10) |
| "Skull" (sticker) | The sticker's name from its pack (P4-12) |
| "Boom", "Bruh" (meme sounds) | The bundled starter pack's titles (P1-08). Until licensed sounds exist, release builds have no sounds, and debug builds use self-made test tones titled "Test tone 1", "Test tone 2"… (never shipped). |
| "Original audio" | Real copy: the label of a detached audio clip (P1-09) |
| "Zoom punch" (effect) | The effect's name (P4-06) |

### Structure
- **Width:** the timeline spans the full screen width on `canvas`, below the transport row's divider (P1-04).
- **Ruler:** pinned at the top.
- **Lanes:** below the ruler. They scroll vertically when they don't fit.
- **Mapping time to position:** x = center + (t − playhead time) × scale, where center is half the timeline width.
  - Scrolled to the start, 0:00 sits under the playhead and the left half shows the "leading area".
  - Scrolled to the end, the end sits under the playhead and the right half shows the "trailing area".
- **Direction:** the timeline always runs left to right, whatever the language.

### Track order (top to bottom)

| # | Lane | Model | Row height | Item look | Label | Ticket | Shown when |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Main video | `MAIN_VIDEO` | `touch-target` (48), clip `track-height-video` (40) centered | Thumbnail strip on `track-video` | none; duration badge when selected | P1-05 | always |
| 2 | Overlays | `OVERLAY` | `track-height` + `space-1` (32), clip `track-height` | Thumbnail strip on `track-video` | none | P4-01 | it has items |
| 3 | Text | `TEXT` | 32 | `track-text` | The text, first line | P1-10 | it has items |
| 4 | Stickers | `STICKER` | 32 | `track-sticker` | Sticker name | P4-12 | it has items |
| 5 | Meme sounds | `MEME_SOUND` | 32 | `track-meme-sound` | Sound title | P1-08 | it has items |
| 6 | Add a meme sound | (not a track) | `touch-target` (48) | Pill at the playhead | "Add a meme sound" | P1-08 | always, except the empty state |
| 7 | Audio | `AUDIO` | 32 | `track-audio` | "Original audio" (detached); later file name or "Voiceover" (P3) | P1-09 | it has items |
| 8 | Effects | `EFFECT` | 32 | `track-effect` | Effect name | P4-06 | it has items |

- **Several tracks of one kind** (sounds that overlap go on separate lanes) sit next to each other, in model order.
- **Empty tracks:** a track left with no items isn't drawn. The engineer removes it from the project in the same edit (request 7).
- **Why this order:**
  - It's the board's and the Timeline component's order: pictures above, sound below, effects last.
  - Overlays sit next to the main video because they are video.
  - The add row sits right under the sound lanes, so a new sound appears where the user is already looking.
  - In the common P1 case (video plus sounds), the sounds sit directly under the frames they sync to.

### Ruler
- **Height:** the `timecode` line height plus `space-1` above and below (24 at 100%).
- **Labels:** `timecode` style in `text-muted`. Each label starts at its tick, as on the board.
  - Whole seconds read `MM:SS` (`H:MM:SS` from one hour).
  - Between whole seconds, only the fraction shows: ".25", ".50", ".75".
- **Ticks:**
  - Major ticks: a `stroke-hairline` line, `space-2` tall, in `text-muted`, at every label.
  - Minor ticks: the same line at half that height.
  - Minor ticks are hidden when they would sit closer than `space-2` to each other.
- **Range:** no labels before 0:00. After the end, labels continue as normal; the end is shown by where the lane bands stop.
- **Label step:** the smallest step from this ladder whose spacing on screen is at least the widest label's measured width plus `space-4`:
  - Ladder: 0.25 · 0.5 · 1 · 2 · 5 · 10 · 15 · 30 s · 1 · 2 · 5 · 10 min.
  - Minor ticks per step: 5 for 0.25, 0.5, 5, 10 s and 5, 10 min; 4 for 1, 2 s and 1, 2 min; 3 for 15 and 30 s.

| Scale (dp per second) | Labels every | Minor ticks every | Example |
| --- | --- | --- | --- |
| 480 (zoomed all the way in; one 1/30 s frame = 16 dp) | 0.25 s | 0.05 s | 00:03 · .25 · .50 · .75 · 00:04 |
| 160 | 0.5 s | 0.1 s | 00:03 · .50 · 00:04 |
| 40 (default, the board's spacing) | 2 s | 0.5 s | 00:00 · 00:02 · 00:04 |
| 10 | 10 s | 2 s | 00:00 · 00:10 · 00:20 |
| 2 | 30 s | 10 s | 00:00 · 00:30 · 01:00 |

At 200% font the labels are wider, so the same rule picks a longer step on its own.

### Zoom
| Constant (layout math, kept in one place in the timeline component, not tokens) | Value |
| --- | --- |
| Default scale | 40 dp per second, so one 40 dp thumbnail tile is one second |
| Most zoomed in | 480 dp per second: one frame at 30 fps is 16 dp, enough to place a sound on an exact frame |
| Most zoomed out | the smaller of the default and (timeline width ÷ 2) ÷ video length. At that scale, the whole video is on screen wherever the playhead is. |
| Menu zoom step | × 2 or ÷ 2, clamped |

- **Pinch:** zooms continuously around the playhead. The time under the playhead never changes while zooming. CapCut zooms around its playhead the same way (Assumption: product knowledge); iMovie pinches the timeline the same way (Guidance).
- **When the video gets shorter** (delete, trim): if the current scale is now below the new minimum, it snaps to that minimum.
- **Zoom menu:** a long-press on the ruler opens it. It's a floating Menu (`design/system/components/Menu/README.md`) anchored above the press point, with three items:
  - Zoom in
  - Zoom out
  - Show whole video (the "most zoomed out" scale)

  An item at its limit shows in `text-muted` and does nothing.
  - This is the single-pointer alternative to pinching (WCAG 2.2 SC 2.5.1, Guidance). It's also reachable through TalkBack and Switch Access actions, and by keyboard shortcuts later (L-01).

### Lanes and items
- **Lane band:** non-video lanes draw a `surface` band, `track-height` tall, from 0:00 to the end of the video. The main video lane has no band, because its clips fill it. Before 0:00 and after the end there is no band.
- **Items:** `radius-sm`.
  - The left edge sits exactly at the item's start.
  - The drawn width is duration × scale − `space-1`, leaving the gap at the item's end, so start times stay exact.
  - However short the item, it's drawn at least `playhead-width` wide, so it never disappears.
- **Labels:**
  - Style: `caption`, `space-2` side padding, one line, ending in an ellipsis.
  - Color: `on-track` on colored items, `text` on video.
  - When an item's start is scrolled off the left edge, its label sticks to the left edge of the item's visible part. A long "Original audio" clip stays named while the user scrolls through it.
- **Past the end of the video:**
  - The project's length is the end of the main video track (request 2).
  - The part of any item past that point is darkened with a `scrim` wash, and its label switches to `text`. Ratios: `on-track` on the washed blue is 1.5:1 (unreadable); `text` on it is 11.4:1.
  - That part doesn't play or export.
- **Locked items: Later (L-02).** Nothing in P1 locks a track. Draft for L-02 (`Track.locked`; later templates may set it):
  - A `lock` icon at `icon-small` sits before the label, in the label's color.
  - The item can be selected but shows no trim handles (P1-06 → Locked).
- **Missing file** (P1-04 state): the clip shows `track-video` with no thumbnails and the label "File missing" in `text`.

### Thumbnail strip (main video; overlays later)
- **Tiles:** square tiles the clip's height (`track-height-video`, 40). The first tile starts at the clip's start. The last is cut at the clip's end, minus the gap.
- **What each tile shows:**
  - A video clip: the source frame at that tile's start time (trim in + offset).
  - A photo: the same picture in every tile, center-cropped to a square.
- **While loading:** a tile shows the clip's `track-video` fill until its image is ready, then the image fades in over 120 ms (`duration-press`, the same with reduce motion). No spinner, no shimmer, no gray flash for anything already seen (MOTION 2).
- **While zooming:** tiles keep showing the nearest frame already in memory, stretched to the new tile positions, and swap to the exact frame as each arrives.
- **Order of work:** visible tiles first, then one screen to each side (`mobile-performance` skill: virtualize; decode at strip height off the main thread; cache by source, time bucket and height).
- **If a frame fails to decode:** that tile stays `track-video`.

### Playhead: fixed in the center
- **Look:**
  - The line: `playhead-width` in `selection` white, from the top of the ruler to the bottom of the visible lanes.
  - The head: a `playhead-head-size` circle in `selection` at the top of the ruler, centered on the line (as on the board).
  - Layering: above items, below the "Add a meme sound" pill and any bubble or menu.
- **It never moves on screen.** The content scrolls under it. The Timeline component already says so; this spec confirms it. Why:
  - **Nothing to grab.** A 2 dp line is far below `touch-target`. Scrolling the content works anywhere on the timeline, one-handed.
  - **Edits happen where the eye already is.** Split, Add a meme sound and "Move here" all act at the playhead. With the playhead fixed under the play button (P1-04), that point never wanders.
  - **The same model as the editors our users know.** CapCut, VN and InShot all keep the playhead fixed (Assumption: product knowledge, not confirmed from vendor help this session). iMovie moves to the start or end by scrolling the timeline, not by dragging a playhead (Guidance).
- **Cost:** half the width is empty space at the very start and end of a project. Memix uses it: the leading area holds the original-audio toggle, and the trailing area holds Add media.

### Leading area: original audio toggle (P1-09)
- **Where:** in the main video row, with its right edge `space-2` before 0:00. It scrolls with the content, so it shows whenever the start of the video is in view, including on every open.
- **Look:** a `track-height-video` square in `surface-raised` with `radius-sm`, inside a 48 dp target.
  - Sound on: icon `sounds` in `text-secondary`.
  - Muted: icon `volume_off` in `text`. Muted is the exception, so it's the brighter state.
- **What it does:** a tap toggles `Track.muted` on the main video track. That mutes or unmutes every clip's own sound at once. Behavior, undo and analytics are in P1-06 → Original audio.
- **Shown only when** at least one main clip is a video with sound. An all-photo project has nothing to mute.
- **The same control lives in the Volume panel** of any video clip (P1-06), so it can be reached without scrolling back to the start.
- **Precedent:** Microsoft Clipchamp mutes a clip from a speaker icon at the left of its timeline clip (Guidance).

### Trailing area: Add media (P1-16, approved by the owner on 8 Oct)
- **Where:** in the main video row, `space-2` after the end of the video.
- **Look:** a `track-height-video` square in `surface-raised` with `radius-sm`, holding `MemixIcons.Create` in `text`, inside a 48 dp target. Spoken "Add videos or photos".
- **What it does:** opens the P1-02 picker; items land as P1-02 → "Add media" describes.
- **Until P1-16 is built,** leave it out entirely. Don't show it disabled.

### "Add a meme sound" row (P1-08)
- **Where:** a 48 dp row directly under the last meme-sound lane. With no sounds yet, it sits in the meme-sound position of the track order.
- **Look:** one pill, centered on the playhead line and drawn over it.
  - Fill and shape: `primary-subtle`, `radius-full`.
  - Size: the full row height minus `space-1` top and bottom; `space-4` side padding.
  - Content: `MemixIcons.Create` (24) and the label "Add a meme sound" in `label`, both `primary`, with a `space-1` gap.
  - It's blue because it is meme-sound UI.
  - The pill stays fixed in screen space while the timeline scrolls, because it marks where a sound will land: at the playhead.
- **Tap:** clears any selection and opens the Meme sounds sheet (P1-06 → Meme sounds). Choosing a sound there is the second tap, and the sound lands at the playhead.
  - That makes any sound two taps from the timeline, even while a clip is selected and the tool bar shows clip tools. Without this row, that case takes three taps (deselect, Meme sounds, add).
  - CapCut shows a similar "Add audio" row under its main track (Assumption: product knowledge).
- **While a meme sound is being moved,** the pill hides but the row stays. It's a drop target: a sound dropped there gets a new lane (P1-06 → Move).
- **Hidden entirely** while any other item is being moved, in reorder mode, and in the empty-project state.

## Scroll, zoom and scrub

| Gesture | Where | Result |
| --- | --- | --- |
| Touch down | anywhere on the timeline | Pauses playback (P1-04) |
| Horizontal drag | lanes or ruler | Scrolls time. The playhead time is whatever sits under the center line. The preview seeks (P1-04). A fling slows down naturally. Scrolling stops exactly at 0:00 and at the end, with no overscroll stretch. |
| Vertical drag | lanes | Scrolls the lanes when they don't fit. The ruler and the add pill's horizontal position don't move. Movement locks to the first axis that passes touch slop. |
| Pinch | anywhere on the timeline | Zooms around the playhead |
| Tap | ruler | Seeks: that time scrolls under the playhead over 200 ms. The selection is kept. |
| Long-press | ruler | Opens the zoom menu |
| Tap | an item | Selects it; the tool bar switches to its tools (P1-06) |
| Tap | the selected item | Nothing; it stays selected |
| Tap | an empty part of a lane, the leading or trailing area, or below the last lane | Clears the selection |
| Long-press, then drag | an item | Selects and lifts it: reorder on the main video track, move on the others (P1-06) |
| Drag | a handle of the selected item | Trims (P1-06) |
| Tap | original audio toggle | Mutes or unmutes original audio (P1-06) |
| Tap | "Add a meme sound" pill | Opens the Meme sounds sheet (P1-06) |

- **No snapping while scrubbing.** Moving and trimming snap (P1-06), but the playhead doesn't. The perfect frame for a reaction is often one or two frames after a cut, and a playhead that jumps to clip edges fights that (Assumption).
- **During playback** the content scrolls under the playhead at playback speed. Any touch pauses first, then the gesture applies.

## Selection
- **One item at a time.** There's no multi-select in v1.
- **What a selected item looks like:**
  - **Outline:** `stroke-selection` in `selection`, drawn outside the item with a `stroke-hairline` gap. The gap lets the white read against blue sound clips (2.4:1 on the fill itself) by showing the dark background between them (17.2:1 or more).
  - **Trim handles:** at both ends, just outside the outline (P1-06 → Trim).
  - **Duration badge**, main video clips only. It sits in the top-left corner, `space-1` inside the clip: `scrim` fill, `radius-sm`, the duration in `timecode` style in `text` (P1-04 format, "00:03.20"). It shows only if the clip is wider than the badge plus `space-2`.
    - Contrast: `text` on `scrim` stays 7.5:1 or more over any thumbnail, even pure white.
    - Non-video items show their duration only while being trimmed (P1-06).
- **Selecting never moves the playhead.** Clearing the selection never deletes anything.
- **The selection is kept** through playback, scrolling, zooming and undo, if the item still exists (P1-04).
- **TalkBack:** selecting an item scrolls it into view and moves the playhead to its start. That happens on double-tap, not on focus.
- **Locked items: Later (L-02).** Draft: they can be selected (outline, no handles) so the user can see what's locked and unlock it (P1-06).

## Original audio and meme sounds on the timeline
- **Original audio isn't drawn as its own lane** while it's attached. It's heard, and it's controlled from the toggle and the Volume panel. Waveforms come with P3-09.
- **Detached original audio** (P1-09) appears on an Audio lane:
  - It sits under its source clip's time span, labeled "Original audio", in `track-audio` green.
  - From then on it moves and trims on its own (P1-06).
  - The source video clip looks the same; TalkBack adds "sound detached".
- **Meme sounds** (P1-08) are blue clips on the meme-sound lanes, labeled with the sound title.
  - When a sound lands, P1-06 picks the lane (the first one free for the sound's whole length, otherwise a new lane under the last one).
  - A new lane slides in and pushes the lanes below it down over 200 ms (reduce motion: 120 ms fade).
  - If the landing lane is scrolled out of view vertically, the lanes scroll to show it.

## States

| State | What shows | Copy |
| --- | --- | --- |
| One clip, opened | Playhead at 0:00 over the clip's start; leading area with the original-audio toggle if the clip has sound; one main clip with thumbnails loading; the add row | `timeline_add_meme_sound` |
| Many clips | Clips back to back with a `space-1` gap; tiles anchored per clip | none |
| Many tracks (more than fit) | The lanes scroll vertically under the pinned ruler. The playhead spans the visible lanes. The bottom lane is cut by the edge, which shows there is more. | none |
| Loading thumbnails | `track-video` tiles, image fades in per tile | none |
| Playing | Content scrolls under the fixed playhead. The selection is kept. | none |
| At end | The end sits under the playhead. The right half shows the trailing area and lanes with no bands. | none |
| Item selected | Outline, handles, badge (main video) | none |
| Item locked: **Later (L-02)** | Lock icon before the label; when selected, outline and no handles | `a11y_state_locked` (L-02) |
| Item past the end | `scrim` wash on the part past the end; label in `text` | `a11y_state_past_end`, `a11y_state_after_end` |
| Very long project (an hour) | Zooms out to the "whole video" scale; ruler steps in minutes; tiles load only near the view | none |
| Item narrower than its handles' targets | Handle targets reach outward from the item (P1-06), so the item's own body stays tappable. The Edit tool and TalkBack give equal routes. | none |
| Empty project (defensive, P1-04) | Ruler and an empty main row; no bands, no toggle, no add row | P1-04 |
| Missing file | The clip shows no thumbnails and the label "File missing" | `clip_missing_file` |
| 200% font, Hindi | See Accessibility | |

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Tap on the ruler | Scrolls to that time over 200 ms (`duration-sheet`) | Jumps |
| Zoom menu step, "Show whole video" | 200 ms scale change around the playhead | Jumps |
| Pinch | Follows the fingers | Same: the user is driving it |
| Playback | Content scrolls with playback | Same |
| Thumbnail arrives | 120 ms fade-in | Same |
| Selection outline and handles | 120 ms fade | Same |
| Lane added or removed | Lanes below slide over 200 ms | 120 ms fade |
| Menu opens | 120 ms fade | Same |

The bonk and the edit motions are in P1-06.

## Copy
English source; machine drafts for id, es, pt and hi until P5. Budgets are in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `timeline_add_meme_sound` | Add a meme sound | 24 | The pill. Verb first. "Meme" is common in all five languages; keep it if natural. |
| `timeline_original_audio` | Original audio | 24 | Spoken label of the toggle (a switch: TalkBack adds on/off). Also the visible label of detached audio clips. |
| `timeline_add_media` | Add videos or photos | 28 | Spoken label of the Add media tile (P1-16) |
| `timeline_zoom_in` | Zoom in | 18 | Menu item and accessibility action |
| `timeline_zoom_out` | Zoom out | 18 | Same |
| `timeline_zoom_fit` | Show whole video | 24 | Same |
| `clip_missing_file` | File missing | 18 | Inside a 40 dp clip; keep it short |
| `timeline_a11y` | Timeline | 16 | Container name |
| `a11y_playhead` | Playhead | 16 | The playhead control's name |
| `a11y_forward_1s` | Forward 1 second | 24 | Accessibility action |
| `a11y_back_1s` | Back 1 second | 24 | Same |
| `a11y_go_start` | Go to start | 20 | Same |
| `a11y_go_end` | Go to end | 20 | Same |
| `track_video` | Video | 16 | Spoken track names |
| `track_overlay` | Overlay | 16 | |
| `track_text` | Text | 16 | |
| `track_sticker` | Sticker | 16 | |
| `track_meme_sound` | Meme sound | 16 | |
| `track_audio` | Audio | 16 | |
| `track_effect` | Effect | 16 | |
| `a11y_clip_media` | %1$s, clip %2$d of %3$d, %4$s long, starts at %5$s | 100 | %1$s = `import_item_video` or `import_item_photo` (P1-02); %4$s and %5$s = spoken times (P1-04 `a11y_time_*`) |
| `a11y_item` | %1$s, %2$s, %3$s long, starts at %4$s | 110 | %1$s = track name, %2$s = the item's label ("Boom", "Original audio") |
| `a11y_state_locked` | Locked | 16 | State description. Later (L-02): don't add it in P1. |
| `a11y_state_past_end` | Partly after the end of the video | 44 | |
| `a11y_state_after_end` | After the end of the video, so it won't play | 56 | |
| `a11y_state_original_muted` | Original audio muted | 30 | On video clips while the track is muted, or at 0% volume |
| `a11y_state_sound_detached` | Sound detached | 24 | On video clips whose audio was detached |
| `a11y_trim_start` | Start of %1$s | 40 | Handle name; %1$s = the item's label or "clip 2" |
| `a11y_trim_end` | End of %1$s | 40 | |
| `a11y_trim_to_playhead` | Trim to playhead | 24 | Handle accessibility action |

Translator notes:
- **Spoken strings** never contain timecode digits; times come from P1-04's `a11y_time_*`.
- **Placeholders** are numbered, so word order can change.
- **"Playhead"** is the editing term. If your language's editors leave it in English or use a local term (CapCut's own translation is a good guide), match that.

## Accessibility
- **TalkBack structure:**
  1. Timeline container: "Timeline". Actions: Zoom in, Zoom out, Show whole video.
  2. Playhead: "Playhead", value "3.2 seconds of 8 seconds" (`a11y_time_of`). Adjustable: swipe up/down or the volume keys step one frame (1/30 s). Actions: Forward 1 second, Back 1 second, Go to start, Go to end.
  3. Original audio toggle (when shown): a switch.
  4. Tracks top to bottom. Within each track, items in time order.
     - Main clips read `a11y_clip_media`, for example "Video, clip 2 of 3, 4.5 seconds long, starts at 3.2 seconds".
     - Other items read `a11y_item`, for example "Meme sound, Boom, 1.4 seconds long, starts at 3.2 seconds".
     - States are appended: selected (system), muted, sound detached, past the end; locked comes with L-02.
     - Double-tap selects the item, scrolls it into view and moves the playhead to its start.
  5. When an item is selected, its two handles follow it: "Start of Boom", value "3.2 seconds". Each is adjustable by one frame and has the action "Trim to playhead". Edits announce through P1-06.
  6. The "Add a meme sound" pill, then the Add media tile (P1-16).
- **Every item is reachable by TalkBack at any scroll position.** Drawing is virtualized, but the item list isn't. Items' semantics come from the model, not from what's on screen (request 6).
- **Single-pointer alternatives (WCAG 2.2 SC 2.5.1 and 2.5.7, Guidance):**

  | Gesture | Alternative |
  | --- | --- |
  | Pinch | Long-press the ruler for the zoom menu |
  | Drag to scrub | Tap the ruler |
  | Drag to trim | Split, then Delete; or the handle's "Trim to playhead" action |
  | Drag to move or reorder | "Move here" and Reorder's "Move earlier" / "Move later" (P1-06) |

- **Keyboard in P1** (basic access, not shortcuts): Tab reaches the timeline's controls (the original-audio toggle and the "Add a meme sound" pill), Enter activates them, and Escape does the same as system back (P1-04). Known gap until L-01: a keyboard-only user can't move the playhead or step between items. TalkBack and Switch Access reach both through the playhead's and items' accessibility actions.
- **Keyboard shortcuts: Later (L-01)**, not built or checked in P1. Draft for L-01: the P1-04 transport keys, plus:
  - Ctrl+Plus / Ctrl+Minus zoom in and out.
  - Ctrl+0 shows the whole video.
  - Tab enters the timeline at the playhead. Arrow keys move between items in a track; Up/Down move between tracks.
- **Touch targets:**
  - 48 dp or more: the main video row, the add row, the toggle, the Add media tile and every handle.
  - **Documented exception:** non-video items are 28 dp tall in 32 dp rows (`track-height`), below Memix's 48 dp rule. Why it's acceptable:
    - It still meets WCAG 2.2's 24 dp minimum (SC 2.5.8).
    - Equivalent routes exist: the Edit tool selects the clip under the playhead, and TalkBack reaches every item.
    - Six lanes have to fit on a 360 × 640 phone.
  - Items narrower than 24 dp at the current zoom rely on the same equivalent routes (SC 2.5.8 "equivalent" exception).
- **Contrast:**
  - Labels: `on-track` on every track color is 6.5:1 or more; `text` on `track-video` is 9.7:1.
  - Ruler: `text-muted` labels on `canvas` are 4.6:1 or more.
  - Selection outline against the background: 18.9:1 on `canvas`, 17.2:1 on a `surface` band.
  - Add pill: `primary` on `primary-subtle` over `canvas` is 6.2:1.
  - Lane bands (`surface` on `canvas`) are decorative and never the only sign of anything.
- **200% font scale:**
  - Labels and the ruler grow. A clip's height grows to fit its label's line height plus `space-1` above and below, so rows get taller and fewer fit; the lanes scroll.
  - The main video row keeps its height; the badge grows inside it.
  - The pill grows. The ruler picks longer label steps.
  - Devanagari labels use the same rule, so marks above and below the letters are never clipped.
- **Reduce motion:** see Motion. Playback scrolling remains, because it shows position.

## Analytics
None fire from looking at or moving through the timeline. The toggle and every edit fire `tool_use` (P1-06).

## QA compares
1. **One video clip, just opened**, at 360 dp: leading area with the toggle, the playhead over 0:00, thumbnails loaded, the add pill under the playhead, ruler labels every 2 s.
2. **Many tracks** (debug 6-track sample): track order as specified, lane bands ending at the end of the video, a sound reaching past the end with the wash, lanes scrolled vertically.
3. **A selected meme sound:** white outline with the gap, handles outside, the rest unchanged. Then a selected main clip with the duration badge.
4. **Zoomed all the way in and all the way out:** ruler steps of 0.25 s and the "whole video" view. Plus the zoom menu open on a long-press.
5. **Loading:** a 20-clip project opened cold, `track-video` tiles filling in, no spinners. (The locked-track screenshot waits for L-02.)
6. **Hindi at 200% font:** taller rows, nothing clipped.

Measured (note in the QA report):
- **Done when:** a 20-clip, 4-lane debug project with thumbnails loaded, release-type build, the owner's phone. Scripted `adb shell input swipe` flings across the timeline, then `dumpsys gfxinfo` "Janky frames" under 5% (`mobile-performance` skill), and no visible lag. Repeat while thumbnails are still loading, and note the result.
- **Leaks:** a debug build with LeakCanary. Open and close the editor 5 times, scroll, zoom and select; no leak reported.
- **Scrub:** preview updates under 100 ms per update (P1-04).
- **TalkBack:** every item in the 20-clip project is reachable by swiping, at any scroll position.

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Tokens:** add `MemixSize.trimHandleWidth = 12.dp`, `MemixSize.playheadHeadSize = 12.dp` and `MemixSize.iconSmall = 16.dp` (values in `design/tokens.json`). Replace the "12 dp circular head" note's raw value with `playheadHeadSize`.
2. **Project length** is the end of the main video track. Check that P1-03's composition ends there and cuts any audio item at that point in preview and export. Sounds past the end are allowed on the timeline but never heard.
3. **Icons:** draw `volume_off` to the icon grammar (the `sounds` cone with a cross instead of waves). The toggle's "on" state reuses `sounds`. `lock` (shackle stroke, solid body as the core) waits for L-02.
4. **Constants:** the zoom values and ruler ladder above are layout math. Keep them as named constants in one place in the timeline component, with a comment pointing to this spec, so the token audit can tell them from hardcoded design values.
5. **Rendering:** draw the timeline as a few layers (ruler, bands and items, playhead, overlays) that read scroll and zoom inside draw or offset lambdas, so a scroll frame doesn't recompose. Virtualize items and tiles to the visible window plus one screen.
6. **Semantics:** build item semantics from the model, so TalkBack reaches every item regardless of what's drawn. Add the playhead's adjustable semantics and the container's zoom actions.
7. **Empty tracks:** when an edit leaves a non-main track with no items, remove the track in the same edit (one undo step).
8. **Thumbnails:** request at strip height, nearest-frame fallback while zooming, cache as in the `mobile-performance` skill.
9. **Debug samples for QA:**
   - the 6-track sample (P1-03) with one sound past the end (its locked meme-sound track waits for L-02)
   - a 20-clip project
   - an hour-long project made of a looped test clip

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Mobile editors select a clip with a tap that outlines it; trim by dragging handles at both ends, which stop when the footage runs out; rearrange by touch-and-hold until the clip lifts, then drag; zoom by pinching, which doesn't change durations | Guidance (Apple iMovie for iPhone help) | [Trim and arrange videos and photos in iMovie](https://support.apple.com/102353), [Navigate the iMovie timeline on iPhone](https://support.apple.com/guide/imovie-iphone/knaeca4b0ea2/ios) |
| A speaker icon at the left of a timeline clip mutes its audio; "separate audio" puts it on its own track | Guidance (vendor help) | [Clipchamp: mute, separate and delete audio](https://support.microsoft.com/en-us/clipchamp/how-to-mute-separate-and-delete-audio-from-video) |
| VN advertises frame-accurate trimming down to 0.05 s with up to 30× timeline zoom | Evidence (vendor claim, store listing read through search results) | [VN Video Editor on the App Store](https://apps.apple.com/us/app/vn-video-editor/id1343581380) |
| Multipoint gestures (pinch) need a single-pointer alternative such as taps or long presses, and that alternative can't be a drag | Guidance | [WCAG 2.2 SC 2.5.1 Pointer Gestures](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| Anything done by dragging needs a single-pointer way to reach the same result; reordering isn't "essential" dragging | Guidance | [WCAG 2.2 SC 2.5.7 Dragging Movements](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Targets of at least 24 × 24 CSS px, with spacing and "equivalent control" exceptions | Guidance | [WCAG 2.2 SC 2.5.8 Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) |
| Color can't be the only visual means of conveying information | Guidance | [WCAG 2.2 SC 1.4.1 Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Compose custom accessibility actions are the intended replacement for complex gestures such as drag and drop | Guidance | [Compose semantics](https://developer.android.com/jetpack/compose/semantics) |
| About half of observed phone users tap one-handed with the thumb; one-handed reach favors the lower-middle of the screen | Evidence (field observation of 1,300+ people, 2013; not a controlled study) | [Hoober, UXmatters](https://www.uxmatters.com/mt/archives/2013/02/how-do-users-really-hold-mobile-devices.php) |
| CapCut keeps its playhead fixed in the center, zooms around it, shows an "Add audio" row under the main track and a mute control at the track's start | Assumption (product knowledge; CapCut's help pages weren't reachable here, and third-party guides disagree on details) | — |
