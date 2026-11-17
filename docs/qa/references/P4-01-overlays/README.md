# P4-01 overlays: reference render

The five-overlay layout of the debug draft `overlay-check` (`memix.openEditor overlay-check`), drawn on a 1080 x 1920 frame from the engine planner's own `OverlayPlacement` numbers through Media3 1.11.1's overlay matrix chain, so it shows what the compositor draws. Each overlay is a labeled card in its source's shape with an arrow on its top edge, so turn direction (clockwise on screen), size, opacity and stacking can be read off.

- `overlay-check-sheet.png`: the frame at 0.5 s, 2.5 s, 5 s and 9.5 s.
- `overlay-check-5s.png`: all five overlays at once, half size.
- `placements.md`: the numbers per overlay (anchor, scale, turn, texture size, drawn corners in pixels) for the iOS port to match.

Layers bottom to top: A (16:9 video, 1 to 7 s), B (9:16 video, 12 degrees, 80%, 2 to 8 s), PHOTO (1:1, -10 degrees, 60%, 1 to 9 s), PHOTO 4:3 (3 to 9 s), CORNER (9:16 video, 90%, 4 to 10 s). The main video is the grid. A real-device render of the same draft is compared by eye on the phone (docs/qa/phase-4/engineer-hand-checks.md → P4-01a).
