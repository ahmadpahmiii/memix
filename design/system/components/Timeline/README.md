# Timeline

The video editor's multi-track timeline: ruler, colored items on lanes, a fixed playhead. Full behavior: `docs/ux/specs/P1-05-timeline.md`.

- **Provide:**
  - the tracks in order (main video, overlays, text, stickers, meme sounds, audio, effects), each track's items with start and duration
  - the selected item
  - the playhead time
  - the zoom scale
- **No track-label column.** The full width is time. A track is told apart by its fixed place in the order, its color, the label on each non-video item, and its spoken name.
- **Item colors:**
  - One token per track type (`track-video`, `track-text`, `track-sticker`, `track-meme-sound`, `track-audio`, `track-effect`).
  - Labels in `caption` `on-track` (`text` on video).
  - Main video and overlay items show a thumbnail strip, square tiles at the item's height, `track-video` while loading.
- **Meme sound items** use `track-meme-sound`, the brand blue: the one place the timeline shows Memix's own color. The "Add a meme sound" row under the sound lanes holds a `primary-subtle` pill fixed at the playhead.
- **Lanes:** non-video lanes draw a `surface` band from 0:00 to the end of the video. The part of an item past the end gets a `scrim` wash, and its label switches to `text`.
- **Selected item:**
  - A `stroke-selection` white outline, drawn outside the item with a `stroke-hairline` gap.
  - `trim-handle-width` white handles outside the outline, with an `on-primary` grip line; each handle's target is `touch-target` wide.
  - Main video items also show a duration badge (`scrim`, `timecode`).
- **Locked item:** a `lock` icon at `icon-small` before the label; selectable, no handles.
- **Playhead:** a `playhead-width` line in `selection` white with a `playhead-head-size` round head. It stays centered while the timeline scrolls under it.
- **Sizes:**
  - Main video row `touch-target`, its items `track-height-video`.
  - Other rows `track-height` + `space-1`, their items `track-height`.
  - Ruler: the `timecode` line height + `space-1` above and below.
  - Gaps between items on a track: `space-1`, taken from the end of each item so starts stay exact.
- **Don't:** add shadows or extra colors. Color here always means item type; white always means selected.
