# P2-12 background picture reference

- `photo-background-image-1080.png`: the photo scene `photo-background-image` (a 1:1 canvas, three layers over the patterned picture) drawn by the shared `drawPhotoScene` at 1080 px wide.
- `photo-background-only-9x16-1080.png` and `photo-background-only-1x1-1080.png`: the same function with no layers: the picture alone, covering a 9:16 and a 1:1 canvas (centered, the overflow cropped). The video export draws the same covered picture in the bars of a Fit clip.
- `expected-video-9x16-fit-16x9-clip.png`, `expected-video-9x16-fit-4x3-photo.png`, `expected-video-1x1-fit-16x9-clip.png`: **how the export frame should look**: the covered picture with a synthetic Fit clip pasted in the center by arithmetic (Pillow). These are **not Media3 output**: no video frame was exported (no device). On a phone, export `canvas-image-check` (`--es memix.openEditor canvas-image-check`) and compare three frames with these by eye and by the tolerances in the spec (the same crop of the picture within 1% of the width, no dimming, black in any gap). Replace this folder's expected frames with the real exported frames when QA has them.

All are desktop renders from generated pictures, not phone frames.
