# SoundRow

One sound in the library: play button, name, duration and category, waveform, favorite and add.

- **Provide:** the sound's name, duration, category or credit, waveform peaks (about 32 bars), playing state, favorite state, and the add action.
- **Play button:** `primary-subtle` disc with a `primary` icon, so sounds read as Memix blue everywhere they appear.
- **Playing:** the row gets `aria-current` and a `surface` background; the play disc fills `primary` with an `on-primary` pause icon and the waveform turns `primary`.
- **Add:** a small `primary` block that places the sound at the editor's playhead; it plays the "bonk" landing motion.
- **Long-press** opens details: source, license, credit and Report.
