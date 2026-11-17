# P4-06a video effect references

Reference renders of the 15 effects of Motion, Light and Color (P4-06a), from `EffectMath` (`:core:domain`), the CPU twin that the GL shaders (`EffectsProgram`, `EffectShaders` in `:engine:video`) were written from. Desktop only: **the shaders have not run on a GPU** and these are not frames from a phone; QA compares the device's export frame with them (CLAUDE.md rule 5; iOS parity in P7-04). The first build that passes on a phone pins them.

- **Size 540 x 960**, not the spec's 1080 x 1920 exports: the twin is size independent (parameters are shares of the shorter side; checked by a harness) and 78 frames at 1080 x 1920 would be about 40 MB. Scale an export frame to 540 wide before comparing.
- **The scene** is the `effects-check` clip (a 12 x 12 lettered grid over colour bars with a moving white square) with the caption "CHECK" drawn over it, as the composition hands it to the effects pass. `make-effects-check.sh` makes the clip (scale 1) and the frames these were drawn from (scale 0.5, `png-seq`). The caption here is the desktop's Anton with a black outline, an approximation of the app's caption renderer: expect a difference in the letters only.
- **Items** are 2 s long starting at 1.0 s in the scene. File names are `<effect id>-<intensity percent>-<ms into the item>.png`. Every effect has 100% and 50% at its reference time (spec P4-06 → The list, "Ref time"; an effect that shows the same at any time is taken at 1000 ms, where its envelope is full); the time-based ones (zoom punch, slow push, shake, wobble, bounce, flash, light pulse, sun leak) also at 500, 1000 and 1500 ms (25, 50 and 75% of the item).
- Shake is centered; Sun leak goes out and back along the top edge (reference time 1667 ms, the glow at the middle of the top edge).
- `scene-1080ms.png`: the scene with no effect, at the start of the effect items plus 80 ms.
- `stack-two-zoom-then-fry.png`, `stack-two-fry-then-zoom.png`: a 100% zoom punch and a 70% Deep-fry over the same instant, in lane order (first lane first) and in the other order: the two differ (the harness measured the share of pixels that differ by more than 3/255).
- `contact-sheet.png`: all of them in one picture.

## What the harness checked (not in the repo)

140 checks pass (see `docs/qa/phase-4/engineer-hand-checks.md` → P4-06a): intensity 0 leaves the frame unchanged, the output is deterministic, every effect changes the frame, the moves never show a canvas edge, 540 x 960 against 270 x 480 agree, and the photosensitivity series below.

**Photosensitivity (WCAG 2.2 SC 2.3.1).** Frame-by-frame mean relative luminance of the scene with the effect, 30 fps; a flash is a pair of opposing changes of 10% or more of the range; the most flashes in any 1 s window:

| Case | Luminance range | Flashes in 1 s |
| --- | --- | --- |
| Scene only | 0.223 to 0.229 | 0 |
| Flash 100% | 0.223 to 0.854 | 1 |
| Flash 80% (default) | 0.223 to 0.634 | 1 |
| Two Flash items 0.34 s apart | 0.223 to 0.854 | 2 |
| Three Flash items 0.34 s apart | 0.223 to 0.854 | 3 |
| Light pulse 100% | 0.225 to 0.285 | 0 (the swell is under 10% of the range on this scene) |
| Light pulse 50% (default) | 0.225 to 0.262 | 0 |

Glitch and Static (the spec's other two) belong to P4-06b.

## QA on a device

`--es memix.openEditor effects-check`, `stack-two`, `stack-two-reverse` (steps in `docs/qa/phase-4/engineer-hand-checks.md` → P4-06a). The spec's allowance for the H.264 encode: 99% of pixels within 3/255 per channel after scaling to 540 wide.
