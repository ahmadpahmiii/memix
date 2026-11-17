# P4-04 green screen: reference renders

Desktop renders of the shader's arithmetic (`ChromaKeyMath`, the CPU mirror of `ChromaKeyEffect`'s fragment shader) over the generated
`chroma-check` picture (`ChromaCheckFixture`, 1920 x 1080, seed 30), shown through straight-alpha blending over a neutral 12 x 12 grid
(the compositor's `GL_SRC_ALPHA` blend). `source.png` is the unkeyed picture. They pin the starting numbers for iOS parity (P7-04) and
are not GPU captures: compare a device export frame against them (hand checks, P4-04).

| File | Settings |
| --- | --- |
| `default-key` | Green, strength 50%, softness 25% |
| `strength-0`, `strength-100` | strength 0% / 100% |
| `softness-0`, `softness-100` | softness 0% / 100% |
| `blue-key` | Blue key on the blue picture |
| `picked-off-green` | key sampled (9 x 9) from the `#6BB536` picture |
| `main-over-image` | keyed main clip over a picture (the canvas Image background) |
| `overlay-over-video` | keyed overlay at 50% over color bars (a stand-in for the main video) |
| `photo-overlay` | the same key on a still, over color bars |
| `key-plus-mask` | key, then a circle mask (pipeline order) |

Measured on the CPU mirror at the defaults: backdrop cleared 100%, subject kept 100%, strand centers kept 100%, band G - max(R, B) 0/255.
Formula as built (decided with the designer): tolerance `0.05 + 0.20 x strength`, feather `0.02 + 0.08 x softness`, total reach capped at 0.30
(feather cut to `0.30 - tolerance`, never under 0.02). Defaults: tolerance 0.150, feather 0.040, reach 0.190. At 100% strength: tolerance 0.25,
feather 0.02 (softness 0) or 0.05 (softness 100), reach 0.27 or 0.30; white (0.328 from the green key) and skin (0.43) stay at alpha 255 at
100/0, 100/100 and 50/100. Strength 0 leaves 26% of the lit, noisy backdrop with alpha. Picked from the off-green backdrop, the key removes the
backdrop fully but still takes part of the skin (43% of the subject interior kept): lower Strength there. The default green key no longer
touches the off-green backdrop (0% cleared).
