# P2-02 screenshots (Classic, Caption bar, Demotivational)

Desktop renders (Compose `ImageComposeScene`, 3x density, generated pictures, text by a stand-in; no emulator). 360 x 800 dp unless the name says 640. **Built and shown: Plain, Classic, Caption bar, Demotivational.** Panels and Bubbles have their whole model, drawing and references (`docs/qa/references/P2-02-formats/`) but no editor tiles or tools yet (a tile never shows before its format works).

- 50-formats-panel-plain-*: the Formats panel on a fresh start, Plain white, no hint line (360x800 and 640)
- 50-formats-panel-caption-bar-hint, 50-formats-panel-demotivational-tall-canvas-hint: the hint line when the photo doesn't fill the area's width
- 50-main-bar-formats-first: the main bar, Formats first
- 50-classic-hints, 50-classic-top-filled-bottom-hint, 50-classic-9x16-hints, 50-classic-hints-hidden-while-selected
- 50-caption-bar-hint / -filled, 50-demotivational-hints / -filled, 50-demotivational-canvas-panel-hint
- 50-hindi-200-*, 50-spanish-200-*: 200% font on 360 x 640

Note for the designer: the slot hint is `scrim` as specified, which is invisible over the Demotivational poster's black (only its label shows), the same effect as the empty card's M1; a `surface-raised` fill would read everywhere.
