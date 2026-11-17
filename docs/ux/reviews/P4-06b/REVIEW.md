# Design review: P4-06b video effects (Glitch, Blur, Retro) and the redone 06a renders

Reviewed 17 Nov 2026 against `docs/ux/specs/P4-06-video-effects.md`: the 18 renders here, the redone 06a renders, the CPU-twin references, the luminance table and `luminance-plots.png`, the strings in en/id/es/pt/hi, the handoff. Not seen (device only): GL output, motion, timeline behavior.

**Verdict: APPROVED WITH POLISH.** No must-fix. (PASS)

## What passes
- Tabs: Glitch, Blur, Retro each hold five tiles; two rows whole at 360x800 and 412x915; six-tab row scrolls, selected tab keeps the white underline; replace mode rings VHS; plain squares before tiles arrive.
- Tile pictures read as different effects (Glitch burst, RGB split, Tear band at half height, Pixelate, Static; Radial blur, Smear, Tilt blur's sharp middle; Mirror fold at center). Sun leak tile now shows the warm glow at the top edge (06a must-fix 3 closed).
- Hindi at 200% (`sheet-retro-hi-200`, `panel-old-screen-hi-200`): Devanagari line height fine, nothing clipped; panel at 360x640 scrolls to the description (accepted in the spec).
- No v1 colors, outlines or gradients; the only blue is the slider.
- Luminance: every 06b effect is 0 to 1 flashes per second at 100% and stacked x3. The plots show steady shifts, no oscillation.
- All 15 names, 3 tabs and 15 descriptions exist in id, es, pt, hi; all within the 48-char budget (longest hi `fx_desc_old_screen`, about 45).

## Builder flags: decisions
1. **Pixelate fade over the first 12.5% of strength: accepted.** The spec formula had a pop (10 px block at 1080p at the first step). Spec amended.
2. **Tear: one full sine period across the band: accepted** (the spec was ambiguous). **Old screen vignette 45% x strength, black rim up to 4% from the bend: accepted** as the CRT-glass look; no cover scale (Fisheye keeps its fill). Spec amended.
3. **Glitch red/blue offset over the whole frame during a burst, 10-frame slots, at most 3 per second: accepted.** Spec amended.
4. **Gaussian below 0.3% fades in from sharp: accepted.** Spec amended.
5. **Film grain amplitude 10% x sqrt(3) x strength (so sigma is 10%); whole-pixel VHS softening and scanlines: accepted.** Spec amended.
6. **Flash guard: keep it `flash` only; no new copy.** Static x3 and Old screen x3 swing the mean luminance once, as a level shift for the whole item, not an oscillation; a single Static item swings 0.012 and a single Old screen 0.064, both under the 0.10 threshold. Four swings in a second needs about nine overlapping or back-to-back items. A general guard needs per-frame luminance analysis on the phone for no real gain. The existing `explain_effect_flash` copy is flash-specific ("Flashes can't come faster than three a second") and would be wrong for these. Spec amended; QA keeps plotting them.
7. **Descriptions: approved as drafted** in all four translated languages (natural, short, the verb-phrase pattern of 06a). Names approved too. No replacements.

## Polish (none blocks the commit)
1. **Spanish at 200%: "Desenfoque" breaks mid-word** ("Desenfoq / ue", also in "Desenfoque radial" and "inclinado", see `sheet-blur-es-200.png`). Request to the engineer: turn on automatic hyphenation for tile names (`Hyphens.Auto` with `LineBreak.Heading` in the tile's TextStyle) so it breaks "Desenfo-que"; if it is not available in the Compose MP version, accept the break (200% font, one language, still legible). Spec line: Accessibility, "200% font: names wrap to 3 lines".
2. Check on a device that Pixelate at the first slider steps (10% to 20%) ramps smoothly after the fade-in.

## Deferrals for the QA list
- Device: GL output of all 15 against the CPU-twin references (99% within 3/255 per channel), then pin them. Glitch, Static, Film grain and VHS noise: start the item at 0:00 to compare pixels, otherwise compare the look (README of the references).
- Device: 30 fps for each 06b effect alone, and Radial blur + VHS + Glow stacked.
- Device: Static x3 and Old screen x3 luminance plot from an export (the table is from the CPU twin); confirm 3 swings or fewer in any second.
- Device: Mirror, Fisheye and Old screen edges (black rim, fold line) look right, not like a bug; Tilt blur band; Glow and Light pulse visible (carried from 06a).
- Device: 06a carried items (lane item, landing, focus return, reduce motion, TalkBack tile and node labels).
- Sheet: Spanish and Hindi at 200% on a real phone (Devanagari fallback face).
- Owner listening-style check: do Glitch and Static feel too harsh at their default 70% / 50%? Tune the default in `EffectCatalog` only.
