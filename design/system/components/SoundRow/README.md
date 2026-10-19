# SoundRow

One sound in the library: play button, name, duration and category or credit, waveform, favorite and add.

- **Provide:** the sound's name, its meta line (duration and category or credit), waveform peaks (about 32 bars, from P3-09), playing state, favorite state (or none), and the add action.
- **Play button:** a 40px `primary-subtle` disc with a `primary` icon in a `touch-target` square, so sounds read as Memix blue everywhere they appear. Pressed: the disc turns `surface-raised` (the icon stays `primary`, 5.9:1).
- **Name:** `body-strong`, up to two lines, then an ellipsis.
- **Meta line:** `label` in `text-secondary`. It wraps and is **never cut**: it can hold a license's credit, and a CC-BY credit is a license condition (PRD → Content and licensing). The editor's sheet shows "{duration} · {credit}", for example "0:05 · CC0".
- **Playing:**
  - The play disc fills `primary` with an `on-primary` pause icon, and the waveform (when shown) turns `primary`. That disc is the state's main signal.
  - The row gets a band one value step above what it sits on, `radius-md`, `space-2` inner padding: **`surface` on `canvas`** (the Sounds tab, Home), **`surface-raised` on `surface`** (any sheet, such as the editor's Meme sounds sheet). On a sheet a `surface` band would be invisible (P1-08 review). Never `primary-subtle`: a whole blue-tinted row would put a large saturated area next to the blue disc and add button.
  - Contrast on the `surface-raised` band: name `text` 12.4:1, meta `text-secondary` 5.7:1, the `primary` disc and add block 5.9:1.
  - Screen readers: the play button's name changes from "Play {name}" to "Pause {name}".
- **Favorite:** a heart toggle, `text-secondary` or `text` when on. **Optional:** without a favorite action the heart isn't drawn and takes no space. The editor's P1 sheet has no heart because there's no Favorites list to show it in (P1-08); the P3-05 picker brings it back.
- **Add:** a 36px `primary` block (`radius-md`) with an `on-primary` plus, in a `touch-target` square. It places the sound at the editor's playhead; the sound lands with the "bonk". Pressed: `primary-pressed`. Spoken "Add {name} to video".
- **Long-press** opens details: source, license, credit and Report (P3; nothing in P1).
- **Compose:** `SoundRow(name, meta, isPlaying, isFavorite, onPlayToggle, onFavoriteChange = null for no heart, onAdd)`; the band for a row on `surface` is the P1-08 review's request (an `onSurface` flag or equivalent).
