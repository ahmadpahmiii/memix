# Design review: P4-06a video effects (framework, Motion, Light, Color)

Reviewed 17 Nov 2026 against `docs/ux/specs/P4-06-video-effects.md`: 26 desktop renders, CPU-twin references and contact sheet, strings in en/id/es/pt/hi, `ic_effects.xml`, handoff. Not seen (device only): lane item, landing, motion, GL output.

**Verdict: CHANGES REQUESTED** (3 must-fix, all small; the rest approved or polish).

## Must-fix
1. **Shake drift (spec typo).** `+1`/`+2` give a mean offset of 1.7% / 3.3% of short, not a shake. Corrected, centred formula (amplitude ±2.5% of short):
   x = 2.5% × short × (sin 2π·13τ + 0.5 sin 2π·29τ) / 1.5 ; y = 2.5% × short × (sin 2π·11τ + 0.5 sin 2π·31τ) / 1.5.
   Reach for `cover` becomes 2.5% (x and y both), so `SHAKE_REACH_X/Y` = 1.0 (not 2.5/1.5 and 3.5/1.5); the cover scale gets smaller (less crop). Redo `shake-*` references. Spec amended.
2. **Sun leak pops at the wrap (6.7 s).** The centre jumps from the right edge back to the left edge with the glow visible. Make it ping-pong: x = W × (1 − |1 − 2·frac(0.15τ)|) (starts at 0 as before, so the 0 ms reference stays valid; period 6.7 s, no jump). Spec amended. Redo sun_leak refs only if a time past 3.3 s is used (none are).
3. **Sun leak tile shows nothing.** The tile is a centred square of the 9:16 frame, so a glow on the top edge is cropped out (see `sheet-light-pt.png`, Vazamento de sol tile looks unaffected). Fix: tile for `sun_leak` uses a top-aligned square crop and the instant τ = 1667 ms (centre at half width). Other tiles unchanged. Spec amended.
4. **Indonesian name clash.** Glow and the Light tab are both "Cahaya". Change `fx_name_glow` (values-id) to **Pendar** (6 chars). Also `fx_desc_glow` stays.

(4 items; counted as 3 UI items plus one string.)

## Builder flags: decisions
1. Shake: typo, fixed above.
2. Sun leak: fixed above (ping-pong).
3. Accepted as written, spec amended: Spotlight feather centred on the radius; Glow sigma 2.5% of the short side, applied on the quarter copy as `sigma_full / 4` (the code does this correctly); Poster at strength 0 skipped (needed for "intensity 0 = no effect"). Polish for QA: Poster at tiny strength is already 16 levels, a visible step in the 80 ms ramp-in; watch it on a device, and if it bands, fade `mix(original, quantized, min(1, s / 0.1))`.
4. Accepted: CPU-twin references at 540x960 and approximate caption are fine as provisional; the first device pass pins them. Glow is subtle on this dark scene; check on a device that it is visible at 100%.
5. Accepted: closing the sheet then the toast is consistent with "the sheet closes on a tile tap". Spec amended.
6. Accepted: `effect_delete` is in the spec's analytics table.
7. Deferred to QA list (device): lane item, landing bonk and slide, lane scroll with pinned pill, focus return to new item.
8. Icon approved with one polish (below).
9. Flash guard: not must-fix for 06a (the spec says one Flash item is 1/s and the user's stacking is their choice, and three at 0.34 s is exactly 3/s, within SC 2.3.1). Back-to-back Flash items (e.g. 4 at 0.25 s) can exceed it. **Proposal for the PM (scope, small, do it with 06b):** no more than 3 Flash starts in any 1 s window; a 4th refuses with the existing toast pattern. Copy below. Until decided, the spec stays "can't police".

## What passes
- Sheet (Motion, Light, Color at 360x640/800 and 412x915): tabs, two rows whole at 800, white ring in replace mode, plain squares before tiles, Hindi and Spanish at 200% wrap and scroll, Devanagari line height fine.
- Intensity panel at three sizes, 0% and 100%, es/id/pt/hi: fits. At 360x640 and 200% font the description is below the fold and the panel scrolls (accepted by spec).
- Effect bar: Intensity and Delete whole at 360; icons reuse `adjust` and `replace` as specced. Delete is the red destructive style used across bars.
- No v1 colors, no outlines or gradients; slider is the only blue.
- References and luminance table: Flash 100% = 1 flash/s; stacked three = 3/s (at the limit); Light pulse swell under 10% so 0. Stacking order refs differ as required.

## Icon `ic_effects.xml`: approved with polish
Solid 7-wide core plus three trailing arcs of length 6, 9, 12 reads as motion and differs from `blur` and `audio_effects`. Polish: arcs 2 px thick at 2.5 px spacing leave a 0.5 px gap and will merge at low densities. Use 3.5 px spacing (matches the core's height 8.5 to 15.5):
`M13.5,8.5 Q10.5,7.5 7.5,8.5 M13.5,12 Q9,11 4.5,12 M13.5,15.5 Q7.5,14.5 1.5,15.5`
(spec amended: "3.5 px apart").

## Strings (all five languages)
All 15 names, 3 tabs, 15 descriptions and the toast, a11y and edit strings exist in id, es, pt, hi; budgets met (longest description 47). Placeholders correct (hi "से" = from). Findings:
- `fx_name_glow` (id) clash: set to "Pendar".
- `fx_name_deep_fry` is the literal "Deep-fry" in all locales; the key `edit_photo_tab_deepfry` the spec names does not exist. Acceptable; if the photo editor's label differs, make them the same word.
- Added `a11y_effect_tile` and `a11y_effect_tile_selected` ("%1$s, %2$d of %3$d"; selected adds ", selected"): good, in all languages.

Flash guard toast, if the PM accepts it (`explain_effect_flash`, budget 60):
- en: Flashes can't come faster than three a second. Move one first.
- id: Kilatan tidak bisa lebih dari tiga per detik. Pindahkan satu dulu.
- es: No puede haber más de tres destellos por segundo. Mueve uno antes.
- pt: Não pode haver mais de três clarões por segundo. Mova um antes.
- hi: एक सेकंड में तीन से ज़्यादा फ़्लैश नहीं हो सकते। पहले एक को हटाएं।

## Deferrals for the QA list
- Device: lane item look, landing animation, lane scroll with pinned sound row (six lanes), focus return, TalkBack node and tile labels, reduce-motion.
- Device: GL output vs the CPU-twin references (99% within 3/255), then pin them; check Glow and Light pulse are visible; Poster ramp-in banding; Shake after the formula fix.
- Sheet tiles: Zoom punch, Slow push, Shake and Bounce look alike on this grid scene; confirm they read differently on a real meme frame.
- Hindi 200% at 360x640: description is off-screen until the panel is scrolled.
- Flash/glitch stacking photosensitivity (Glitch and Static plots are 06b).
