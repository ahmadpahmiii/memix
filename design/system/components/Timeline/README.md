# Timeline

The video editor's multi-track timeline: ruler, labeled tracks, colored clips, playhead.

- **Provide:** the tracks in order (video first, then text, sticker, meme sound, audio, effect), each track's clips with start and duration, the selected clip, and the playhead time.
- **Clip colors:** one token per track type (`track-video`, `track-text`, `track-sticker`, `track-meme-sound`, `track-audio`, `track-effect`); labels in `on-track` (`text` on video).
- **Meme sound clips** use `track-meme-sound`, the brand blue: the one place the timeline shows Memix's own color.
- **Selected clip:** `stroke-selection` white outline with white trim handles on both ends, as in CapCut.
- **Playhead:** `playhead-width` line in `selection` white with a round head; it stays centered while the timeline scrolls under it.
- **Sizes:** video clips `track-height-video`, others `track-height`; ruler and durations in `timecode`.
- **Don't:** add shadows or extra colors; color here always means clip type.
