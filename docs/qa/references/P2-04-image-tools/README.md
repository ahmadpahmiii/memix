# P2-04 image tools references (part a: geometry)

Renders of the shared `drawPhotoScene` with a generated 4000 x 3000 grid picture in a 4:3 canvas, at 1080 and 2160 px on the shorter side (the 2160 frame is the 1080 frame scaled by 2). Desktop Skia, not a phone; the geometry is the same code the editor and the export use (`drawPictureWithEdit`).

- `geometry-none`: the picture as is. `geometry-crop-1x1`, `geometry-crop-free`: crops fitted inside the canvas (the canvas keeps its shape).
- `geometry-rotate-right`, `geometry-rotate-180`, `geometry-flip-h`, `geometry-flip-v`.
- `geometry-straighten-10`, `geometry-straighten-minus-10`: turned and scaled up so no corner is empty (about 1.22x at 10 degrees on 4:3).
- `geometry-combo-turn-flip-straighten-crop`: quarter turn, mirror, -8 degrees and a crop, in the fixed order.

Part b (adjustments, the 20 filters, deep-fry) adds its references here when it is built: a reference per id (rule 5).

QA on a device: `--es memix.openEditor photo-image-tools`, crop and turn, render with `--es memix.renderScene photo-image-tools --ei memix.renderWidth 1080` and compare.
