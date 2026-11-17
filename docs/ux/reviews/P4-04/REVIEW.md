# P4-04 Green screen: design review

Reviewed: 23 renders, the 12 reference frames, strings (5 languages), both icons, the handoff. Verdict: **CHANGES REQUESTED** (one must-fix, a constants change; everything else approved or polish).

## Must-fix
1. **Strength and softness ranges** (spec "How it draws"). Spec expectation: `strength-100` keeps skin and white (reference table); the build removes them. The cause is the formula, not the build. Chroma distances from Green `#00B140`: white 0.33, skin 0.43, so a tolerance up to 0.40 (and feather to 0.30) can't keep them. The builder's `0.05 + 0.20 × strength` alone is not enough: at softness 100% the feather (0.30) still made the shirt see-through. New constants, now in the spec, both token files and the changelog:
   - tolerance `t = 0.05 + 0.20 × strength` (0.05 to 0.25)
   - feather `f = 0.02 + 0.08 × softness` (0.02 to 0.10)
   - Defaults become t 0.15, f 0.04. At both sliders 100%, t + f = 0.35: the shirt reads about 90% opaque, accepted as the extreme corner.
   - **Engineer request:** `ChromaKeyCatalog.TOLERANCE_PER_STRENGTH = 0.20f`, `FEATHER_PER_SOFTNESS = 0.08f` (KDoc stays). No schema bump (nothing shipped).
   - **Re-run the harness after the change:** Done when (a) to (d) at the defaults must still pass. If (a) or (d) slips, raise `TOLERANCE_BASE` only (0.05 to 0.08, which also moves `strength-0`) and tell me. Re-render all 12 references and the two panel shots for strength 0 and the 100/100 corner.
   - The new `softness-100` must show a visibly wider edge than `softness-0` at the strands; if it doesn't, raise the gain to 0.10 (t + f max 0.37).

Follow-up: the code reviewer's `MAX_REACH` 0.30 cap on t + f is approved and synced (feather = min(0.02 + 0.08 × softness, max(0.02, 0.30 − t))); the harness shows white and skin opaque at 100/0, 100/100 and 50/100, and Done when (a) to (d) pass. This supersedes the "about 90% opaque" corner above.

## Your flags
2. **Off-green backdrop:** no copy, no behavior change. The fix in 1 resolves it. `#6BB536` is 0.247 from skin; the old default reached 0.315 and removed skin, the new one reaches 0.19. A pick on a yellow-green still sits near skin at 100% strength: the user sees it live and Strength is the control.
3. **`bleedGuardAlpha`:** belongs in tokens; added as `chromaKey.formula.bleedGuardAlpha` 0.25 with a note, and a row in the spec's rules table.
4. **Icons:** approved.
   - `green_screen`: bust centered in the frame, reads clearly as a keyed portrait and not as `cutout`, `overlay` or `photo` (bar render at 360 dp). The measures are 1 px off the spec; the spec bends, not the icon.
   - `eyedropper`: the diagonal tube and a solid bulb read at `icon-small`. Skip the nozzle: at 24 px it would be a 2 px nub with no gain.
5. **Swatch README:** `clickWhenSelected` note added (design/system/components/Swatch/README.md).
6. **Loupe:** deferred to the QA list.
7. **Builder choices:**
   - **Blur under a keyed main clip shows black:** accepted for v1. The blur is made from the clip's own picture, which is keyed; spec line "Keyed main clip: the canvas background shows through" holds for Color and Image. The panel has no Blur option to choose here, so nothing misleads.
   - **Keyed overlay without a drag stand-in, updates on release:** accepted as polish, QA to judge the lag on the phone.
   - **Green screen after Delete on a main clip:** accepted until Crop, Fill or Fit, Position and Filters land; the order in the spec's bar table is final.

## Checked and fine
- Bars: Green screen is fourth on an overlay (whole at 360, label wraps to two lines: ok); main clip one swipe away.
- Panel at 360x640 / 360x800 / 412x915, Spanish, Indonesian, Portuguese and Hindi at 100% and 200% (title row wraps Turn off and Done, the heading wraps above the swatches, Hindi has no clipping).
- Pick mode banner, miss banner, Picked swatch (fourth place, white ring; the spec's "third" is a count of the preset row, fine), Blue swatch hairline ring.
- Strings: 18 keys by name, in en, id, es, pt and hi, match the spec's drafts exactly; reused keys cover the rest. No budget breaks.
- Tokens only: no hex in chrome seen in renders; the key swatches are content colors.

## Polish (not blocking)
- `docs/qa/references/P4-04-green-screen/README.md` "Known:" paragraph is obsolete after fix 1; replace it with the new numbers.
- Re-save `panel-strength-100-softness-100.png` after the fix as the review's evidence for the corner.

## Deferrals for the QA list
- Loupe on a device: screenshot while dragging, above the finger, white ring visible on light and dark pixels; fade 120 ms.
- Owner's real green clip at the defaults, then Strength alone, then Softness alone, by eye (the Done-when real-clip check).
- Keyed overlay drag lag on the phone (no stand-in); key plus Blur background (black).
- Hindi at 200% "nothing found" banner (render exists, confirm on device).
- Preview versus export alpha comparison and the speed and leak checks in the spec's "Measured".
