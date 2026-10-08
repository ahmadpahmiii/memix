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

## Preview (built in P1-04: `Media3PreviewSession`, TECHNICAL_DESIGN → Preview)

- `CompositionPlayer` plays the same `Composition` as export (same plan, same builder, same HDR mode). It needs every video and sound item's source length (`EditedMediaItem.setDurationUs`, at least the trim end); items without one, and missing files, become gaps and set `missingMedia`.
- Create the player only once the first composition is ready: in Media3 1.11.1, releasing a player that never got a composition leaves its playback thread running. `seekTo` before `setComposition` crashes.
- `PreviewSession.update(project)` calls `setComposition(new, positionMs)`, which rebuilds every sequence player in 1.11.1 (no incremental update yet). Measure it before promising an edit-to-preview time.
- Show it with `VideoPreviewSurface(session, modifier)` (media3-ui-compose `PlayerSurface`, a SurfaceView; CompositionPlayer refuses TextureView). The composition root hands it to the editor screen as a slot, because features can't depend on the engine.
- Position: a `StateFlow<Long>` ticked by `Choreographer` while playing; the UI reads it only where it's drawn.
- `CompositionPlayer` and `media3-ui-compose` are `@UnstableApi`, `CompositionPlayer` also `@ExperimentalApi`: opt in to both with `androidx.annotation.OptIn` (lint).

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
