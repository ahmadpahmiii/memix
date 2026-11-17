# Design review: P4-03b shape masks (uncommitted, head 31bd5d1)

Spec: `docs/ux/specs/P4-03b-shape-masks-and-blend.md` (mask part only; Blend / P4-03a is deferred, see the spec's new note).
Looked at: the 33 renders in this folder, `docs/qa/references/P4-03-masks/`, the mask strings in 5 languages, `ic_mask.xml`, the handoff.

## Verdict: PASS (approved with polish). No must-fix.

Caveat on evidence: the renders are Compose harness frames, not emulator screenshots. The stage frames are drawn over the unmasked picture, so they show the frame, handles and panel layout only. The masked result is judged from the CPU-mirror references, which match the spec's shapes and numbers (rect edge 162 px, soft band within 2%). GPU parity and the real preview are on the QA list.

## Checks against the spec
- Panel layout: pass. Title, then tabs, then 5 tiles. At 360x800 the tiles and Edge softness show and the rest scrolls, as budgeted. At 360x640 the tabs and tiles show. The Mirror tile is cut at the edge, which signals the scroll.
- Tiles: pass. The selected tile is white with `on-primary` content. The pictures are drawn from data. Rectangle, Circle, Linear and Mirror read correctly.
- Position tab: pass. Rows, steppers and readouts are as specified. The min-size toast text matches, and Width reads 5% at the limit.
- Stage frames: pass. Two-tone white and dark frame, the scale-and-rotate handle, four edge handles on Rectangle and Circle, the Linear line with its arrowhead, Mirror's two band lines. The handle sits clear of the stage edge.
- Icon `ic_mask.xml`: pass. A 2 px stroked square with 1 px corners holding a solid 12 px circle, as the spec drew it. In the bar it is distinct from Green screen and Overlay.
- Strings: pass. All 5 languages are present with the spec's drafts. Extras (`mask_shape_group`, `a11y_mask_*`) are sensible. 200% font in es and hi: titles and tiles stay on one line, the tabs fit, and the sliders scroll under the fold.

## Your 9 flags
1. **Blur background black under a masked main clip: accepted for now, not a design pass.** It is consistent with keying. A circle window over a blurred copy of the same clip is a likely meme use. Proposal for the PM: a ticket to make the Blur background render beneath masked and keyed main clips. QA list: confirm the behavior on the device and screenshot it.
2. **`explain_mask_none` as text on the Position tab: approved, better than the spec.** A toast on a tab with nothing else would flash and vanish. Spec amended.
3. **Frame-edge indicator not rendered: device check.** QA must see it while the mask is moved, sized or turned, and never otherwise.
4. **Tile pictures, icon and None tile: approved.**
   - The None tile is a solid outlined square in the renders, not dashed. It is a glyph inside a tile, like an icon stroke, so "no outlines around shapes" (which is about chrome surfaces) is not broken.
   - Polish, optional: the filled Rectangle tile next to the outlined None square is close in size, but the label and the selection state disambiguate them.
   - Polish, optional: the Linear tile's two tones are low contrast. Raise the lower strip so the split reads at 20 dp.
5. **id `explain_mask_edge`: approved.** "Masker tidak bisa lebih jauh ke arah ini." is 41 characters, inside the budget of 50, and reads naturally. Spec amended: the "trim bisa" note is dropped.
6. **Mask parameters not keyframeable in v1: approved.** The spec says this already.
7. **Flush snap only when the axes line up, guides show centre lines only: approved.** It avoids false snaps. Only the centre lines get guides; accept.
8. **Tap outside the clip's box plays or pauses: approved.** The spec says this.
9. **Mask after Green screen in the bars: approved.** That is the P4-04 order. With Blend deferred, Mask is last on overlays; place Blend after Crop and before Mask when it lands.

## Polish list (none block the commit)
- Retake `stage-rectangle` and the shape renders at the default, with Rounded corners at 0%. The frame in every Rectangle render shows large rounded corners (about 50%), but the spec default is 0% and the slider is under the fold, so I can't tell whether the harness set 50% or the default is wrong. If it is the default, that is a bug. QA: pick Rectangle fresh and check a sharp frame, and check `mask-rect` is sharp on the device.
- At 200% the Edge softness readout in es splits across lines ("0" over "%"). Keep the readout on one line (the readout never wraps; the label may). Minor, QA to confirm on the device.
- Tile pictures of Linear: see flag 4.

## Deferrals for the QA list
1. GPU export against the CPU-mirror references: edge position, soft band, preview vs export 99% within 3/255.
2. Frame-edge indicator, snap guides and haptics (SegmentTick), no stand-in flash on drag (screen recording).
3. Blur background under masked and keyed main clips (flag 1).
4. Default Rectangle corners 0% on the device (polish list).
5. Hindi at 200% for the Position tab and the Linear hint; Spanish at 200% bar.
6. 5x LeakCanary pass on Mask open, drag, Invert, tab switch and Done; masked 1080p overlay at 30 fps or more; drag jank under 5%.
7. Old draft opens with no mask.
8. TalkBack: tiles read "Circle, 3 of 5, selected"; each Position step announces its value.
9. Blend (P4-03a) is not built; its panel, tool, strings, icon and QA items wait for the owner's route decision (see STATUS.md).
