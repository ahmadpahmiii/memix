# backend-engineer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Schema decisions, slow queries and fixes, usage numbers over time, supabase-kt gotchas.

- 2026-10-08 · P1-08 · Audio loudness: for clips under about 1 s, ffmpeg `loudnorm` pass-1 `input_i` read 7.6 LU lower than `ebur128` (−21.9 vs −14.3 on a 0.6 s transient). Intake should measure with `ebur128` (pad short clips to 3 s with `apad=whole_dur=3`) and apply a linear gain of min(−16 − I, −1.5 − TP). AAC adds about 0.5 dB of true-peak overshoot. Scripts tested in the session scratchpad (starter-sounds-tools).
- 2026-10-08 · P1-08 · Cloud sessions can't reach freesound.org, cdn.freesound.org, Wikimedia, OpenGameArt, Pixabay, Mixkit, Kenney, creativecommons.org or web.archive.org (proxy 403). WebSearch with `allowed_domains: ["freesound.org"]` returns per-page license and file details. Downloads, snapshots and measurements need the owner's machine.
- 2026-10-08 · P1-08 · The starter pack must be CC0. There is no in-app credit surface until P3-03 (sound details) and P5-07 (Credits), and CC-BY 4.0 also binds users who share their exports. Pixabay (standalone ban), Mixkit ("can't redistribute … in a tool") and ZapSplat (no apps or soundboards) don't qualify.
- 2026-10-08 · P0 · Memix's project is drnhpnixnewqjfmrchrr; the owner's other project (lzxikotwhhezxgbfbtrh) is off limits. Inserting a report needs `Prefer: return=minimal` (no select policy). Supabase assigns migration versions on apply; rename the local file to match.

<!-- - 2026-10-xx · P0-01 · lesson … -->
