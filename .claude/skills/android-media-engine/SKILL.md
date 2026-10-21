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

- Captions (P1-10): `AndroidCaptionRenderer` is the one caption renderer (layout once in canvas pixels; bundled TTFs with `Typeface.CustomFallbackBuilder`, Classic → Teko → system). `CaptionOverlay` is a composition-level `CanvasOverlay` (composition time in both player and Transformer in 1.11.1) that redraws only when the shown set changes; a lower export resolution draws every clip at that frame size and the overlay scales to it. `hideCaption` uses a per-composition switch plus a seek to the same position (no rebuild; `experimentalRedrawLastFrame` needs the replayable cache).
- Canvas (P1-11): `CanvasFraming` draws each main clip into the frame size by the canvas rules: Fill and Fit are `Presentation` layouts (stretch within 1% of the shape), a color background is two scaled 1 × 1 `BitmapOverlay` bars, Blur is `Presentation`(fitted) → `GaussianBlurWithFrameOverlaid(sigma·s, s, s)` → `Presentation`(crop) with s = fit ÷ fill (stock effects; checked in the 1.11.1 source). Gaps stay black. The preview draws at a 720 short side under Blur; `PreviewPlayback.shownCanvasSize` lets the editor cover a picture of the old shape.
- Mapping rules (order, gaps, trims, mute, detach, overlap lanes, cut at the main track's end) live in the shared `CompositionPlanner` → `CompositionPlan` (`:engine:video` commonMain). Change them there, never only in the Media3 builder, so iOS keeps matching.
- One `Project` → one Media3 `Composition` (`Media3CompositionBuilder`).
- Main video track → one `EditedMediaItemSequence` (clips in order, trims, speed, per-clip effects).
- Overlays, meme sounds, imported audio and voiceover → further sequences that overlap in time.
- Text and stickers → bitmaps rendered once per change and composited as overlay effects.
- Effects/filters/transitions come from their declarative specs (`media-effect` skill); time params in µs.

## Preview (built in P1-04: `Media3PreviewSession`, TECHNICAL_DESIGN → Preview)

- `CompositionPlayer` plays the same `Composition` as export (same plan, same builder, same HDR mode). It needs every video and sound item's source length (`EditedMediaItem.setDurationUs`, at least the trim end); items without one, and missing files, become gaps and set `missingMedia`.
- Create the player only once the first composition is ready: in Media3 1.11.1, releasing a player that never got a composition leaves its playback thread running. `seekTo` before `setComposition` crashes.
- `PreviewSession.update(project)` calls `setComposition(new, positionMs)`, which rebuilds every sequence player in 1.11.1 (no incremental update yet). Measure it before promising an edit-to-preview time. The last frame stays on screen meanwhile: from the second composition on, CompositionPlayer keeps its video graph and surface (TECHNICAL_DESIGN → Preview → No black or gray frame); keep it that way (never clear or re-attach the surface on an edit) and keep `isCatchingUp` honest.
- Trim frames (P1-06): one composition of the clip's whole source per drag, then scrubbing seeks; never a rebuild per drag frame, and never publish the trim's time as `positionUs`.
- Show it with `VideoPreviewSurface(session, modifier)` (media3-ui-compose `PlayerSurface`, a SurfaceView; CompositionPlayer refuses TextureView). The composition root hands it to the editor screen as a slot, because features can't depend on the engine.
- Changes heard while a control moves (P1-08 Volume) go through a `LiveGainProcessor` per item with sound (each sound, and since P1-09 each main clip playing its own sound) that every preview composition carries (`previewVolume`), never a rebuild per frame; the release commits and rebuilds. Preview plans keep 0% items (`keepSilentItems`) with the live gain at 0 and no fixed volume step, so a raise from 0% is heard; export plans leave them out. Hand-check builds also put `SoundOnsetLog` on the whole mix (composition-level audio processors are flushed with the seek position as `StreamMetadata.positionOffsetUs` in 1.11.1), logging where each sound is heard (`MemixPreview`). Single sounds outside a project (the sheet) play on their own ExoPlayer (`Media3SoundPlayer`).
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
