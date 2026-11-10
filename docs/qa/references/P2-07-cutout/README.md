# P2-07 cut-out reference renders

The scene alone through `drawPhotoScene` (no chrome): a 4:3 test photo with a stand-in mask (head and body, soft edge) and two refine strokes, over a second picture. Desktop render of the shared code at 1080 and 2160 on the shorter side (the formats' two export sizes). For visual comparison and iOS parity; not Android output.

- `photo-cutout-soft30-*` default softness (30%); `soft0`, `soft100` the two ends.
- `photo-cutout-turned-cropped-*`: the same cut-out under a quarter turn and a crop (the cut picture goes through the geometry).

**2160 scaled to 1080 against the 1080 render** (area-averaged, pixels differing by more than 64/255 in any channel; the P2-05 method):

| Render | pixels over 64/255 | worst |
| --- | --- | --- |
| soft30 | 0.002% | 79 |
| soft0 | 0.003% | 86 |
| soft100 | 0.001% | 74 |
| turned-cropped | 0.002% | 75 |

The feather and the stroke widths are shares of the picture, so the two sizes agree to edge anti-aliasing. This is the desktop check only; the Done-when comparison of the editor with the render at 1080 and 2160 still needs a phone.
