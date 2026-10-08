---
name: android-media-engine
description: Build or change Memix's Android video engine on Media3 — Project-to-Composition mapping, CompositionPlayer preview hosted in Compose, Transformer export in a foreground service, GL effects, audio processors, speed, thumbnails, waveforms and encoder capabilities. Use for any ticket touching :engine:video or :engine:photo androidMain.
---

# Android media engine (Media3)

Read `docs/TECHNICAL_DESIGN.md` → "Video engine" first. Before coding, check the current official docs and the latest stable Media3 version:
- https://developer.android.com/media/media3/transformer/composition
- https://developer.android.com/media/media3/transformer/multi-asset
Never guess class names or signatures from memory. If an API you expected doesn't exist, say so and adapt.

## Mapping

- Mapping rules (order, gaps, trims, mute, detach, overlap lanes, cut at the main track's end) live in the shared `CompositionPlanner` → `CompositionPlan` (`:engine:video` commonMain). Change them there, never only in the Media3 builder, so iOS keeps matching.
- One `Project` → one Media3 `Composition` (`Media3CompositionBuilder`).
- Main video track → one `EditedMediaItemSequence` (clips in order, trims, speed, per-clip effects).
- Overlays, meme sounds, imported audio and voiceover → further sequences that overlap in time.
- Text and stickers → bitmaps rendered once per change and composited as overlay effects.
- Effects/filters/transitions come from their declarative specs (`media-effect` skill); time params in µs.

## Preview

- `CompositionPlayer` drives the preview; host its view in Compose with `AndroidView`.
- `PreviewSession.update(project)` applies edits without rebuilding the player when possible.
- Expose playback position as a `StateFlow` sampled at frame rate; the UI reads it in layout/draw lambdas.

## Export

- `Transformer` export inside a foreground service with a progress notification and Cancel.
- Before export: check `capabilities()` (max resolution/fps per codec, HEVC support) and free space; show the estimate.
- Encode straight to the output file (no length limit); save to the "Memix" album; return duration and size.
- Watermark: small "Memix" text top-left with a 3% margin, unless the export is marked watermark-free.

## Rules

- No FFmpeg, no GPL code. Hardware codecs only.
- Release every player, codec and GL resource on dispose.
- Hit the budgets in `mobile-performance`; measure on the reference phone and put the numbers in the PR.
- For each new effect, save a reference render to `docs/qa/references/<effect-id>.png` (frame from the fixed sample project) so QA and the iOS port can compare by eye.
