# P4-09 speed references

Reference numbers and frames for the constant speed of main clips and video overlays (P4-09a). Curves are P4-09b.

- `make-speed-check.sh <dir>` makes `speed-check.mp4` (10 s, 1080 x 1920, 30 fps; source time burned in, a 1 kHz tone that steps down 3 dB each second and starts over every 5 s).
- `make-references.sh <speed-check.mp4> <dir>` makes `const-0_1.png`, `const-0_5.png` and `const-2.png`: the expected frames at 25%, 50% and 75% of the output length (source time = output time x speed, nearest earlier frame, no blending). `const-2` also stands for 10x: the same source frames, at output 0.25, 0.5 and 0.75 s.
- Made on the desktop, not by the app: they are what a device export is compared with. The shipped engine does not exist for a desktop, so no engine render is here yet (hand checks: `docs/qa/phase-4/engineer-hand-checks.md` -> P4-09a).

## Numbers the export must match (source = `speed-check.mp4`, whole clip unless noted)

| Speed | Source used | Video length | Source time at output 25% / 50% / 75% | Audio (Keep) | Audio (Mute) |
| --- | --- | --- | --- | --- | --- |
| 0.1x | 0 to 1 s | 10.00 s | 0.25 / 0.50 / 0.75 s | tone 1 kHz within 1%, length 10.00 s (rough) | silent, length 10.00 s |
| 0.25x | 0 to 2 s | 8.00 s | 0.50 / 1.00 / 1.50 s | tone 1 kHz within 1%, 8.00 s | silent |
| 0.5x | 0 to 4 s | 8.00 s | 1.00 / 2.00 / 3.00 s | 1 kHz within 1%, 8.00 s | silent |
| 2x | 0 to 10 s | 5.00 s | 2.50 / 5.00 / 7.50 s | 1 kHz within 1%, 5.00 s, level within 1 dB | silent |
| 4x | 0 to 10 s | 2.50 s | 2.50 / 5.00 / 7.50 s | 1 kHz within 1%, 2.50 s | silent |
| 10x | 0 to 10 s | 1.00 s | 2.50 / 5.00 / 7.50 s | rough | silent |

Pass bars (spec P4-09 -> Measured): video length = source / speed within one frame (33 ms); audio length equals video length within 23 ms; Keep keeps the tone at 1 kHz within 1% and the level within 1 dB of the source; Mute is under -80 dBFS RMS over the clip's span.
The level steps at source seconds 1, 2, 3 ... so at speed s they fall at output seconds 1/s, 2/s, 3/s ...
