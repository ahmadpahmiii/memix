# Filter LUTs (P2-04)

`gen_filter_luts.py` writes the 20 filter LUTs the photo editor's Filters tab uses (and P4-07 will feed to Media3's `SingleColorLut` for video).

- **Original work.** Every look is a short recipe of ordinary operations (saturation, contrast, lift, gain, gamma, per-channel multiplies, shadow and highlight tints, an S-curve, a luma tint) written for Memix from the table in `docs/ux/specs/P2-04-image-tools.md`. Nothing is taken from a third-party preset or LUT pack, so there is no license to record (owner decision, 2 Nov 2026).
- **Files:** `core/ui/src/commonMain/composeResources/files/filters/<id>.lut`, 107,811 bytes each (33 x 33 x 33 x 3), about 2.1 MB for all 20. The generated files are committed; run the script only to change a look, then re-render `docs/qa/references/P2-04-image-tools/`.
- **Format:** bytes R, G, B per lattice point, red varying fastest, then green, then blue: `index = (b * 33 + g) * 33 + r`, the point `(r, g, b) / 32` in sRGB gamma space. Sampling is **trilinear** between the 8 nearest points (`app.memix.core.domain.image.Lut`); the intensity is the caller's mix `out = mix(color, lut(color), intensity)`.
- **Ids are saved in drafts and never renamed**; a changed recipe changes old drafts' look, so after the closed test (about 25 Jan 2027) it needs a schema bump.
- Needs Python 3 and numpy. `python3 core/ui/lut-tools/gen_filter_luts.py`.
