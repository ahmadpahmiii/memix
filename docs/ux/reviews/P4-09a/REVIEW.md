# P4-09a design review: constant speed, Keep/Mute sound

Verdict: **CHANGES (one must-fix, copy only)**. Everything else passes or is a polish/QA item. Evidence: 26 desktop renders (Compose Desktop, 3x, not a phone), strings in 5 files, `ic_speed.xml`, the handoff. Nothing here was seen on an emulator.

## Must-fix
1. **Hindi detach toast is cut with an ellipsis** (`toast-detach-hi.png`): the action ("change the speed first") is lost. en and es fit two lines. Change `explain_detach_speed` in `values-hi` to:
   `गति 0.5× से 2× और आवाज़ चालू हो, तभी अलग होगा।`
   (spec table updated). Then re-render `toast-detach-hi.png` and also check id and pt on the device at 200% font (both are about as long as es).

## Decisions on the flags
1. **Slider moves only the readout: accepted; spec amended** (lines 81 and the States row). A speed change rebuilds the composition, so a live preview would stutter; the live length in the readout plus one-tap tiles is enough. No debounce needed. QA: confirm the "Updating the preview" status shows after 1 s on release at 0.1x and 10x.
2. **Slider README:** a "Non-linear scales" line for `nextValue` is added. API in `Slider.kt` matches (falls back to the even grid, coerced into the range).
3. **Bars:** order matches the spec (main: Split, Volume, Delete, Speed; overlay: Speed after Volume, the earlier Mask and Filters sit before Split, which the later bar consolidation sorts out). Measured from `bar-video-clip-wide.png` (64 dp per tool at 3x): the 4th tool ends about 313 dp, so Split, Volume, Delete, Speed show whole at 360 dp. Pass by arithmetic; the renders are 900 dp wide, so QA confirms at a real 360 dp.
4. **Labels:** hi "स्पीड" matches the shipped `fx_speed` (good, same word as P3-09). One-line tile labels, readout `0.5× · 20.0 सेकंड` fits one line at hi 200% and es 200%; Reset/Done wrap under the title in es 200% as specified. Pass. Note the a11y string `a11y_state_speed` says "गति" while the label says "स्पीड": acceptable, flag for the P5 translation pass.
5. **Retime chip:** correct (scrim, caption, bottom-left, `2×`/`0.5×` readable on a bright thumbnail). Polish: with a clip selected, its white handle covers the first character of the neighbor's chip ("0.5×" reads ".5×", `chips-clip-selected-800.png`). Fix: when the previous clip is selected, start the chip after the handle width. Not drawn under 56 dp: not shown in renders, QA to check on a tiny clip.
6. **Strings:** `speed_length` is "%1$s s" (en, es, pt), "%1$s dtk" (id), "%1$s सेकंड" (hi, per spec "seconds in full"). Good. Reuse of `fx_speed_value` and `fx_reset` accepted. Polish: es, id and pt toast text writes decimals with a comma (0,5×) while the tile and readout use a dot; pre-existing for `fx_speed_value`, so leave for the P5 localisation pass.
7. **Detach toast and Volume note:** the toast is fine in en and es (copy matches spec). **No render of the Volume panel note "Sound is off in Speed." exists**: add one (en and hi) to the QA list.

## Passes
- Icon `ic_speed`: half dial, flat caps, solid needle 2 dp clear of the arc; at 24 dp it reads as a gauge and is distinct from volume (bars) and audio effects (wave). Arc is 18x9 instead of 20x10: fine. Approved.
- Tiles, slider, Sound choice: tokens only, one white selection, no blue except the slider fill and Reset. A value off the tiles (1.5x) shows no selected tile. Mute shows white on Mute. Rough hint at 10x correct, `text-secondary`.
- 360x640: tiles and slider whole, Sound under the fold, as specified.
- Photo clip bar has no Speed (checked in `bar-photo-clip-wide.png`).

## Polish and QA list (not blocking)
- **360x800 and 412x915 renders show the Sound choice and the hint cut off at the panel's bottom edge**; the spec budget said all rows whole at 800 (204 of 206) and 412x915 with the hint. It may be the render harness (no bottom inset). QA: confirm on the emulator that the panel scrolls to the end with the hint fully visible and padding below it. If Sound is under the fold at 800 on a real phone, change the spec line to "Sound under the fold at 800 too" (it scrolls; acceptable, but then say it in the budget).
- Selected-clip handle covering the neighbor's chip (above).
- Hindi 200% panel (`panel-hi-200`): the "आवाज़" label is cut by the fold; scrolls, fine.
- Screenshots on a real device for: Speed after Split/Volume/Delete at 360 dp, the lane-move toast with an actual overlay, Detach on a 2x and a 4x clip, the Volume note, Reset, es at 200% with the tool labels.
