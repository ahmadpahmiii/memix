# P2-12 screenshots

Desktop renders (Compose `ImageComposeScene`, 3x density, generated pictures; no emulator was available), not phone frames. 360 x 800 dp unless the name says 640. The picture is a generated 3000 x 2000 pattern ("BG", a grid, marked corners).

**Photo Canvas panel and scene (`10-bg-*`):** the scene with the picture behind three layers; the panel with a picture set (thumbnail swatch ringed, image row Replace and Remove); no picture yet (the Image swatch with the `photo` icon, first in the row); the picture's file missing (icon in `text-muted`, ringed, the line, the black canvas and the toast); the hint line under a picture behind a full-bleed photo; Hindi and Spanish at 200% font on 360 x 640 scrolled to the Background group (the image row wraps under its label).

**Video Canvas panel (`20-video-*`):** the real `CanvasPanel` with the shared Background group: picture set, no picture yet, Blur selected (Image unselected), missing, the hint line, Hindi at 200%. The stage above the panel is empty black in these frames: the video preview needs Media3 and a device.

Not rendered (needs a device): the picture behind a Fit clip in the preview and the export (see `docs/qa/references/P2-12-background-image/` and `docs/qa/phase-2/engineer-hand-checks.md`), the picker, the import sheet over an editor, the swatch's 120 ms fade (not built, see the handoff).
