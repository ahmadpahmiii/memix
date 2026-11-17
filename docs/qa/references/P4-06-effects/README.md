# P4-06 video effect references (06a: Motion, Light, Color; 06b: Glitch, Blur, Retro)

Reference renders of all 30 effects (P4-06a: Motion, Light, Color; P4-06b: Glitch, Blur, Retro), from `EffectMath` and, for the 06b effects, `GlitchMath`, `BlurMath` and `RetroMath` (`:core:domain`), the CPU twins that the GL shaders (`EffectsProgram`, `EffectShaders`, `GlitchShaders`, `BlurShaders`, `RetroShaders` in `:engine:video`) were written from. Desktop only: **the shaders have not run on a GPU** and these are not frames from a phone; QA compares the device's export frame with them (CLAUDE.md rule 5; iOS parity in P7-04). The first build that passes on a phone pins them.

- **Size 540 x 960**, not the spec's 1080 x 1920 exports: the twin is size independent (parameters are shares of the shorter side; checked by a harness) and about 120 frames at 1080 x 1920 would be about 80 MB. Scale an export frame to 540 wide before comparing.
- **The scene** is the `effects-check` clip (a 12 x 12 lettered grid over colour bars with a moving white square) with the caption "CHECK" drawn over it, as the composition hands it to the effects pass. `make-effects-check.sh` makes the clip (scale 1) and the frames these were drawn from (scale 0.5, `png-seq`). The caption here is the desktop's Anton with a black outline, an approximation of the app's caption renderer: expect a difference in the letters only.
- **Items** are 2 s long starting at 1.0 s in the scene. File names are `<effect id>-<intensity percent>-<ms into the item>.png`. Every effect has 100% and 50% at its reference time (spec P4-06 → The list, "Ref time"; an effect that shows the same at any time is taken at 1000 ms, where its envelope is full); the time-based ones (zoom punch, slow push, shake, wobble, bounce, flash, light pulse, sun leak, and from 06b tear, vhs, static and film grain) also at 500, 1000 and 1500 ms (25, 50 and 75% of the item).
- **06b random effects.** The reference hashes the time into the item (an item that starts at 0: `frameTimeUs` defaults to the time into the item), not the scene's time; the app hashes the composition time, so a device frame of an item that starts later shows a different noise field or glitch than these (the same recipe, a different draw): **start the item at 0:00 to get the same draw** (then the time into the item is the composition time), otherwise compare the look, not the pixels, for **Glitch, Static, Film grain and VHS noise**; Glitch's file (`glitch-*-800ms.png`) is the middle of the first burst (frame 24 of the 1/30 s hash), and no other time of the item is a burst. Tear is at 600 ms (the band's middle at half height) and 500, 1000, 1500; VHS at 1000 ms (tracking band at 75% height) and 500, 1500; Static and Film grain at 500, 1000 and 1500 ms (a new field each time); for VHS, Static and Film grain the 500 and 1500 ms frames are at 100% only (their PNGs are 0.6 to 0.8 MB each). The steady 06b effects (RGB split, Pixelate, the five Blurs, Old screen, Fisheye, Mirror) are only at 1000 ms, 100% and 50%; Glitch only at its burst, 800 ms. **Old screen and VHS draw whole-pixel line patterns, so a 1080 export's stripes sit at other phases**; compare their look.
- **Blur family.** Blur, Tilt blur and Soft focus are four passes on a quarter-size copy (as Glow); Radial blur and Smear 16 taps (TECHNICAL_DESIGN -> Video effects, second batch).
- Shake is centered; Sun leak goes out and back along the top edge (reference time 1667 ms, the glow at the middle of the top edge).
- `scene-1080ms.png`: the scene with no effect, at the start of the effect items plus 80 ms.
- `stack-two-zoom-then-fry.png`, `stack-two-fry-then-zoom.png`: a 100% zoom punch and a 70% Deep-fry over the same instant, in lane order (first lane first) and in the other order: the two differ (the harness measured the share of pixels that differ by more than 3/255).
- `contact-sheet.png`: all of them in one picture.

## What the harness checked (not in the repo)

262 checks pass without the luminance runs, 300 with them (see `docs/qa/phase-4/engineer-hand-checks.md` -> P4-06a and P4-06b): intensity 0 leaves the frame unchanged, the output is deterministic, every effect changes the frame, the moves never show a canvas edge, 540 x 960 against 270 x 480 agree (all but Old screen, whose pattern is whole pixels), the 1/30 s frame rounding, no more than 3 Glitch bursts in any second (checked over 3000 frames), the Glitch slices' ranges, Tear's band (centered at 600 ms, 12% tall, sweeping down), the Blur family removing detail and Tilt blur keeping its middle sharp, Mirror folding at the middle at 100%, and the photosensitivity series below.

**Photosensitivity (WCAG 2.2 SC 2.3.1).** Frame-by-frame mean relative luminance of the scene with the effect, 30 fps, 2 s items at 1.0 s in a 4 s scene, mean luminance over the whole frame, at 100% **alone and with three of the same stacked** (the most the 3-overlap rule allows), plus Glitch, Static and VHS together; a flash is a pair of opposing changes of 10% or more of the range; the most flashes in any 1 s window. The plot is `luminance-plots.png` (every effect of the three 06b tabs, y axis 0 to 1: the scene in gray, the effect white, three stacked blue). Frame times are the real scene times, as in the app.

| Case | Luminance range | Biggest change between two frames | Flashes in 1 s |
| --- | --- | --- | --- |
| Scene only | 0.223 to 0.229 | 0.001 | 0 |
| Flash 100% | 0.223 to 0.854 | 0.624 | 1 |
| Flash 80% (default) | 0.223 to 0.634 | 0.405 | 1 |
| Two Flash items 0.34 s apart | 0.223 to 0.854 | 0.624 | 2 |
| Three Flash items 0.34 s apart | 0.223 to 0.854 | 0.625 | 3 (guard allows at most 3) |
| Light pulse 100% | 0.225 to 0.285 | 0.012 | 0 |
| Light pulse 50% (default) | 0.225 to 0.262 | 0.010 | 0 |
| Glitch 100% | 0.219 to 0.236 | 0.010 | 0 |
| Glitch 100% x3 | 0.211 to 0.251 | 0.032 | 0 |
| RGB split 100% / x3 | 0.222 to 0.229 / 0.219 to 0.229 | 0.002 / 0.004 | 0 / 0 |
| Tear 100% / x3 | 0.223 to 0.229 / 0.222 to 0.230 | 0.002 / 0.004 | 0 / 0 |
| Pixelate 100% / x3 | 0.173 to 0.237 / 0.173 to 0.237 | 0.064 / 0.064 | 0 / 0 |
| **Static 100%** | 0.220 to 0.243 | 0.012 | 0 |
| **Static 100% x3** | 0.228 to 0.396 | 0.110 | 1 |
| Blur 100% / x3 | 0.157 to 0.229 / 0.149 to 0.229 | 0.053 / 0.064 | 0 / 0 |
| Radial blur 100% / x3 | 0.171 to 0.229 / 0.149 to 0.229 | 0.033 / 0.047 | 0 / 0 |
| Smear 100% / x3 | 0.172 to 0.229 / 0.167 to 0.229 | 0.039 / 0.048 | 0 / 0 |
| Tilt blur 100% / x3 | 0.174 to 0.229 / 0.164 to 0.229 | 0.037 / 0.049 | 0 / 0 |
| Soft focus 100% / x3 | 0.193 to 0.229 / 0.204 to 0.229 | 0.015 / 0.025 | 0 / 0 |
| VHS 100% / x3 | 0.189 to 0.229 / 0.149 to 0.229 | 0.016 / 0.041 | 0 / 0 |
| Old screen 100% / x3 | 0.110 to 0.229 / 0.038 to 0.229 | 0.064 / 0.133 | 1 / 1 |
| Film grain 100% / x3 | 0.216 to 0.229 / 0.220 to 0.235 | 0.004 / 0.009 | 0 / 0 |
| Fisheye 100% / x3 | 0.200 to 0.229 / 0.155 to 0.229 | 0.013 / 0.037 | 0 / 0 |
| Mirror 100% / x3 | 0.228 to 0.314 / 0.228 to 0.314 | 0.041 / 0.041 | 0 / 0 |
| Glitch + Static + VHS, 100% | 0.174 to 0.229 | 0.028 | 0 |

None exceeds 3 flashes a second, so no recipe changed. Two things for the designer: **Static x3 and Old screen** change the mean luminance by 0.10 or more over one item (Static x3 lifts it by 0.17, Old screen x3 drops it by 0.19: noise clamping and the vignette), counted as one flash each; a user who lines up many short items of these back to back could pass 3 a second, as with Flash, which the guard polices (the flash guard covers `flash` only).

## QA on a device

`--es memix.openEditor effects-check`, `stack-two`, `stack-two-reverse` (steps in `docs/qa/phase-4/engineer-hand-checks.md` → P4-06a). The spec's allowance for the H.264 encode: 99% of pixels within 3/255 per channel after scaling to 540 wide.
