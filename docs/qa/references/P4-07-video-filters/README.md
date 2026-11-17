# P4-07 video filter and adjustment references

Reference renders of the video look, next to the photo editor's own output for the same look. Desktop only: they come from `VideoLookMath` (`:core:domain`), the CPU twin that the two GL passes of the video engine (`LookColorEffect`, `LookDetailEffect`) were written from, and from the real `ImagePipeline` (the photo path) and the real `.lut` files. **Neither shader has run on a GPU** and these are not frames from a phone; QA compares the device's export frame with them (CLAUDE.md rule 5; iOS parity in P7-04).

Every cell of the contact sheets is **two pictures side by side: left the photo path (`ImagePipeline`), right the video path (`VideoLookMath`)**. They should look the same; the harness measured the difference.

- `filters-100-chart`, `filters-100-base`: None and the 20 filters at 100%, in the strip's order. `filters-50-...`: at 50% (the intensity mix).
- `adjust-chart`, `adjust-base`: brightness, contrast, saturation and warmth at -100, 0, +100; sharpen and vignette at 0, 50, 100.
- `filter-on-moving-clip`: a moving test pattern (a sweeping bar, a bouncing disc, a fixed grid) at 0.5 s and 3 s, with and without Cinema + sharpen + vignette, so a per-frame error would show.
- `adjust-on-fit-clip`: a landscape clip Fit in 9:16, vignette 100 and saturation +30: the bars stay black, the vignette is on the clip.
- `filter-on-overlay`: an overlay with Noir over an unfiltered main clip.
- `filter-all-clips`: three clips with the same look (Sunset), as the main bar's Filters writes it.
- `filter-then-key`: a green screen subject keyed, then Cinema: the matte is the same with and without the look (max alpha difference 0), the subject is graded.
- `limited-range`: a studio-swing source expanded to full range first, then the look, against the full-range still: the same within 2/255 (the expansion is the decoder's, not our shaders').

## Parity numbers (harness, not in the repo)

Three procedural 1080 x 1440 pictures (a chart: gray ramp, skin and color patches, a hue sweep, a soft scene; a hard-edged picture; a soft one) stand in for P2-04's two test photos, which are kept only as contact sheets. 198 cases (20 filters at 100% and 50%, the six adjustments at -100, 0, +100 and 50, all six with a filter): **worst mean difference 0.221/255 per channel, 100% of pixels within 6/255, worst pixel 2/255**, where the spec allows a mean of 2/255 and 99% within 6/255 for the encoded export. The same look at 2160 halved against 1080: 100% within 3/255 (soft), 99.2% (chart). The difference left is the 8-bit rounding between the shaders' two passes and the sharpen's single kernel against the photo path's four box blurs near an edge.

Sharpen's blur reach is capped at about 8 texels (radius 3.99 px), so a clip above about 2600 px on its shorter side sharpens slightly less than the photo path would.

## QA on a device

`--es memix.openEditor filters-check` and `filters-mixed` (steps in `docs/qa/phase-4/engineer-hand-checks.md` → P4-07). Export 1080p30, pull the frame at 1.5 s and compare with the matching sheet cell; the spec's allowance for the H.264 encode is a mean of 2/255 and 99% of pixels within 6/255.
