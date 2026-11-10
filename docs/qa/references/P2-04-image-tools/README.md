# P2-04 image tools references (a: geometry, b: color tools)

Renders of the shared `drawPhotoScene` with a generated 4000 x 3000 grid picture in a 4:3 canvas, at 1080 and 2160 px on the shorter side (the 2160 frame is the 1080 frame scaled by 2). Desktop Skia, not a phone; the geometry is the same code the editor and the export use (`drawPictureWithEdit`).

- `geometry-none`: the picture as is. `geometry-crop-1x1`, `geometry-crop-free`: crops fitted inside the canvas (the canvas keeps its shape).
- `geometry-rotate-right`, `geometry-rotate-180`, `geometry-flip-h`, `geometry-flip-v`.
- `geometry-straighten-10`, `geometry-straighten-minus-10`: turned and scaled up so no corner is empty (about 1.22x at 10 degrees on 4:3).
- `geometry-combo-turn-flip-straighten-crop`: quarter turn, mirror, -8 degrees and a crop, in the fixed order.

**Color tools (part b)**, on two generated test photos (`chart`: a gray ramp, color and skin-tone patches, a hue sweep and a soft scene; `base`: the grid photo), as contact sheets 1080 and 2160 px wide, each tile labelled with the id:
- `filters-100-<photo>-<size>`: none and the 20 filters at 100%; `filters-50-...`: at 50% (the intensity mix).
- `adjust-<photo>-<size>`: brightness, contrast, saturation, warmth at -100, 0, +100; sharpen and vignette at 0, 50, 100.
- `deep-fry-<photo>-<size>`: 0, 25, 60 and 100%.
- `scene-all-tools-s1080` / `-s2160`: the scene through `drawPhotoScene` with a turn, straighten, crop, adjustments, a filter and deep-fry at once. Built before the principal review made grain and sharpen resolution-independent (the grain averaged over each pixel's square, the sharpen radius not rounded): at that time 2160 scaled to 1080 matched 77.5% of pixels within 3/255. After the change, on a generated picture (`measure` run of `ImagePipeline` at 1080 and 2160, the 2160 render halved by a 2 x 2 average): deep-fry 30% 100% within 3/255 on a smooth picture and 97.6% on a hard-edged one, the other tools 96 to 100%; at deep-fry 100% about 55% (93% within 12/255). The adjust, deep-fry and scene frames here are re-rendered from the new pipeline.

**Look pass test (design review 04b, the script prints it):** over the full color lattice, each filter moves the picture by at least about 8/255 on average (smallest: film 7.7, cool 10.3, cinema 10.7; the rest 12 to 58) and no two filters are closer than 4/255 (closest pair: mono and noir, 7.0; then fade and matte, 7.3; vivid and punch, 8.8).

The renders run the real `ImagePipeline` and the real `.lut` files on the desktop; they are the stand-in for the phone's CPU pass, which runs the same code (`AndroidImageToolRenderer`). iOS (P7-09) compares against these.

QA on a device: `--es memix.openEditor photo-image-tools`, crop and turn, render with `--es memix.renderScene photo-image-tools --ei memix.renderWidth 1080` and compare.
