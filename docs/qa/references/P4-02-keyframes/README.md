# P4-02 keyframes: reference renders

Motion of the debug draft `keyframe-check` (`memix.openEditor keyframe-check`), drawn on a 1080 x 1920 frame by a desktop harness from the engine's own `OverlayClip.lookAt` and `OverlayPlacement.of` through Media3 1.11.1's overlay matrix chain (the same method as `docs/qa/references/P4-01-overlays/`), so each strip is what the compositor draws at those times. The overlay is a 12 x 12 lettered grid picture (cell A1 top left); a red cross marks where the spec's formula puts the center, computed by an independent Bézier, not by the repo. Each strip lists the look under every frame. Spec: `docs/ux/specs/P4-02b-keyframes.md` → How it draws and Reference renders.

| File | Setup | Shows |
| --- | --- | --- |
| `ease-linear.png`, `ease-smooth.png`, `ease-in.png`, `ease-out.png` | Center (0.2, 0.2) to (0.8, 0.8) over 2 s, scale 40%; frames at 25, 50 and 75% of the segment | Center on the straight line at the easing's progress (linear 0.25 / 0.5 / 0.75; smooth 0.277 / 0.5 / 0.723 of the way) |
| `ease-hold.png` | The same, Hold; frames at 0.5, 1.0, 1.5, 1.99 and 2.0 s | At (0.2, 0.2) until 1.99 s, at (0.8, 0.8) at 2.0 s |
| `scale-geometric.png` | Scale 50% to 200%, linear, 2 s | 100% (not 125%) at 1.0 s; the 200% frame overflows the canvas on purpose |
| `rotate-spin.png` | Turn 0 to 360, linear, 2 s | 90, 180 and 270 degrees clockwise at 0.5, 1.0 and 1.5 s (the arrow on the top edge shows it) |
| `opacity-fade.png` | Opacity 100% to 0%, linear, 2 s | 50% see-through at 1.0 s, gone at 2.0 s |
| `slide-in.png` | Center (-0.3, 0.5) to (0.5, 0.5), smooth, 1 s | Partly outside the frame at the start, centered at the end |
| `caption-pop.png` | Caption scale 50% to 100%, smooth, 1 s | Size follows the curve (the text is a stand-in: size and place are what compare, not the font) |
| `split-continuity.png` | Two keyframes, split at 2.0 s (1.967 s left piece, 2.000 and 2.033 s right piece) | The frames either side of the cut agree: no jump |
| `volume-ramp.png` | Tone 0% to 100% over 2 s, linear | -12.04, -6.02 and -2.50 dB below the final at 0.5, 1.0 and 1.5 s (spec: -12, -6, -2.5 within 0.5 dB); gain per sample at 48 kHz, no step |

Not a device render: a render of the same draft on a phone, and the exported file's frames at these times, are compared with these by eye and by the numbers above (docs/qa/phase-4/engineer-hand-checks.md → P4-02a). The iOS port renders the same times and compares with them.
