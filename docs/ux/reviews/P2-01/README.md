# P2-01 screenshots

Desktop renders (Compose `ImageComposeScene`, 3x density, generated pictures; no emulator was available), not phone frames. 360 x 800 dp unless the name says 360x640. The black corner marker (top-left) and red marker (bottom-right) on each picture show its orientation.

01 default after a gallery pick (4:3) | 02 layer selected, locked selected (lock badge, Unlock first on the bar), hidden selected | 03 Layers panel (selected, 640 dp tall with the Opacity row as the list's first item, nothing selected, locked selected, one layer) | 04 locked toast | 05 Canvas panel (photo start, preset, and scrolled to the bottom so the hint line shows, at 800 and 640 dp) | 06 empty canvas (white, black), missing photo | 07 Opacity and Position panels | 08 Hindi and Spanish at 200% font on 360 x 640 | 09 every LayerRow state.

Not rendered (needs a running gesture or animation): the value bubble and guides mid-pinch, the lifted row mid-drag, the stage refit animation. These are on the device checklist in `docs/qa/phase-2/engineer-hand-checks.md`.

Refreshed 28 Oct after the design review (M1 Unlock first, M3 Opacity in the list when the panel is tight, M2 and P1 and P2 strings, P5 hint line): every frame was re-rendered.
