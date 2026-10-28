# P2-01 photo scene reference

The scene alone (no editor chrome), drawn through the shared `drawPhotoScene` at 1080 px wide: bottom to top, hidden layers skipped, opacity and transform applied, clipped at the canvas.

- `photo-layers-10-1080.png`: the `photo-layers-10` debug scene (ten visible layers plus a hidden 11th; opacity 60/40/10%, one turned 30 degrees, one 90 degrees, one partly off the frame, one locked).
- `photo-start-4x3-1080.png`: one 4:3 photo on its own 4:3 canvas.

These are **desktop renders** (JVM, Skia) from generated pictures (a black square at the top-left corner and a red one at the bottom-right mark orientation); they are not frames from a phone. On a phone, `--es memix.renderScene photo-layers-10 --ei memix.renderWidth 1080` writes the same scene from the real decoder: compare by eye and within the tolerances in `docs/qa/phase-2/engineer-hand-checks.md`. P2-08 export and the iOS port compare against these.
