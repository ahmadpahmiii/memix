---
name: mobile-performance
description: Performance budgets and techniques for a smooth, native-feeling Memix — Compose recomposition and stability, timeline scrolling, Media3 decoding and GL, startup, memory, threading — and how to measure by hand with the profiler, Perfetto and adb. Use when building or reviewing anything on a hot path (editors, timeline, preview, export, startup, lists).
---

# Mobile performance

## Budgets (PRD; reference phone = 2023+ upper-mid/flagship Android, release build)

| Metric | Target | How to measure |
| --- | --- | --- |
| Cold start to Home | < 1.5 s | `adb shell am start -W -n <pkg>/<activity>` → TotalTime (median of 5, after `am force-stop`) |
| Open an editor | < 1 s | Perfetto trace section from tap to first preview frame |
| Preview 1080p, 6 tracks | ≥ 30 fps, 60 where possible | `adb shell dumpsys gfxinfo <pkg> framestats` + Media3 dropped-frame logs |
| Scrub response | < 100 ms per update | Perfetto trace sections around seek → frame rendered |
| Timeline scroll | 60 fps, < 5% janky frames | `dumpsys gfxinfo` "Janky frames" after a scripted `adb shell input swipe` |
| Export 60 s @ 1080p30 | ≤ 60 s | timestamp from start to file written (logcat) |
| Crash-free users | ≥ 99.5% | Crashlytics |

Always measure release (or `benchmark`) builds, never debug. Report device, OS, build type, median and worst run. There are no automated benchmark suites in this project; these are manual measurements.

## Compose

- UI state is immutable and stable (`@Immutable` data, `ImmutableList`); check the Compose compiler stability report when a screen recomposes too often (Layout Inspector recomposition counts).
- Read fast-changing values (playhead µs, scroll offset, export progress) as late as possible: `Modifier.offset { }`, `drawBehind { }`, `graphicsLayer { }` lambdas, so a frame tick doesn't recompose the screen.
- `derivedStateOf` for values derived from fast state; `remember` expensive objects (Paths, Paints, formatters).
- Lazy lists and grids: stable `key`, `contentType`, fixed item sizes where possible, images sized to the cell.
- No allocation in `draw` blocks; no `Bitmap` decoding in composition.

## Timeline

- Virtualize: only compose clips in the visible time window (+1 screen of buffer).
- Thumbnail strips decoded off the main thread at strip height, cached by (source, time bucket, height); waveforms precomputed once and cached.
- Drag and trim update a lightweight in-memory state at frame rate; commit a new `Project` (undo step) on gesture end only.

## Media (Android)

- Hardware decoders only; respect the device's concurrent codec limit (query capabilities; degrade overlays to a lower-res proxy if over).
- Preview can render at reduced resolution while scrubbing; export always at full resolution.
- Compile GL shader programs once and reuse; release players, codecs, surfaces and GL resources when leaving the editor.
- Background removal masks: compute at preview resolution, cache per frame, recompute at export resolution.

## Startup and memory

- Baseline profile for startup and the editor-open path (the baseline-profile generator module is build tooling, not a test suite).
- No network or disk-heavy work before the first frame; lazy Koin singletons for engines and ads.
- Coil with downsampling; never hold full-resolution bitmaps for thumbnails; watch native memory when codecs are open.
- StrictMode (thread + VM policies) on in debug builds; LeakCanary in debug.

## Feel

- Visual response to every tap within 100 ms (pressed state, optimistic UI), progress for anything over 1 s, skeletons for network lists.
- Haptic tick when a sound or sticker lands; motion 120/200/320 ms; respect reduce-motion.

## iOS (phase 7)

Same budgets. Measure with Instruments (Time Profiler, Animation Hitches, Allocations) on a recent iPhone.
