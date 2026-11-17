# P4-03b shape masks: reference renders

Desktop renders of the mask's arithmetic (`MaskMath`, the CPU mirror of `MaskEffect`'s fragment shader) over the generated 12 x 12 grid
(`MaskCheckFixture`, 1080 x 1080; the top-left cell is white and the bottom-right black, to read orientation), as an overlay at scale 1 on a 9:16 canvas
(1080 x 1920) over a stand-in main video (a black-to-white ramp over six color bars), straight-alpha blending. `source-grid` and `source-backdrop` are the
inputs, `mask-none` the unmasked frame. They pin the starting numbers for iOS parity (P7-04) and are not GPU captures: compare a device export frame
(`--es memix.openEditor mask-check`) against them.

| File | Setup |
| --- | --- |
| `mask-rect` | Rectangle at the defaults (70% x 70%, sharp) |
| `mask-rect-rounded` | Rounded corners 50% |
| `mask-rect-invert` | Rectangle, Invert |
| `mask-circle` | Circle at the defaults (diameter 70% of the shorter side) |
| `mask-circle-soft` | Circle, Edge softness 50% (edge 12.5% of the shorter side, centered on the circle's edge) |
| `mask-linear` | Linear at the defaults (upper half kept) |
| `mask-linear-rotated-invert` | Linear, 30 degrees, Invert |
| `mask-mirror` | Mirror at the defaults (band 40% of the shorter side) |
| `mask-mirror-rotated-soft` | Mirror, 45 degrees, Edge softness 25% |
| `mask-on-clip-moved` | Circle on an overlay at 50% scale, moved: the mask goes with the overlay |
| `mask-on-clip-turned` | Rectangle on an overlay turned 20 degrees: the mask turns with it |
| `mask-on-main-over-image` | Circle on a main clip (Fit) over a canvas Image background |
| `mask-plus-key` | Green screen (P4-04), then a Circle: the mask cuts the keyed subject |

Measured on the CPU mirror (spec Done when): the rectangle's edge at 162.0 px on three rows (15% of 1080, within 1% of the width); circle inside alpha 255/255, outside 0/255;
the soft circle's 1%-99% edge band is 119 px against the 135 px formula (within 2% of the shorter side) and centered on the edge (162 px). Not run on a GPU.
