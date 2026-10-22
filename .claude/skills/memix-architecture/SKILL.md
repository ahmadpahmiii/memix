---
name: memix-architecture
description: Memix module map, layering rules, Project data model and engine interfaces. Use whenever deciding where code goes, adding a module, touching the Project model, or reviewing architecture in the Memix KMP codebase.
---

# Memix architecture reference

Full detail: `docs/TECHNICAL_DESIGN.md`. This is the working summary.

## Modules

| Module | Layer | Holds | May depend on |
| --- | --- | --- | --- |
| `:androidApp` | App | Android `Application`, `MainActivity`, manifest (AGP 9: not a KMP module) | `:composeApp` |
| `:composeApp` | App | `App()`, NavHost, Koin graph, iOS entry; builds the `ComposeApp` framework for `iosApp/` | everything |
| `:core:model` | Domain | `Project`, `Track`, `TimelineItem`, `PhotoScene`, `Layer`, effect/filter/transition specs, catalog entities | nothing |
| `:core:domain` | Domain | use cases; repository + engine interfaces (`VideoEngine`, `PreviewSession`, `PhotoRenderer`, `Segmenter`, `MediaFiles`, `MediaInspector`, `Sharer`, `Ads`, `Analytics`) | `:core:model` |
| `:core:data` | Data | repositories, supabase-kt remote, SQLDelight local, file cache, settings | `:core:domain` |
| `:core:designsystem` | UI | `MemixTheme`, tokens, base components | — |
| `:core:ui` | UI | shared composables with more than one user: time formatting, layer handles, sliders, pickers (the video timeline is in `:feature:video-editor`) | designsystem, model |
| `:engine:video` | Platform | `VideoEngine`: Media3 (androidMain), AVFoundation (iosMain); `VideoPreviewSurface`, which the composition root passes to the editor screen | domain |
| `:engine:photo` | Platform | photo renderer + PNG/JPG/GIF/WebP encoders | domain |
| `:engine:segmentation` | Platform | `Segmenter`: ML Kit + MediaPipe / Vision | domain |
| `:platform:services` | Platform | AdMob+UMP, Firebase, share intents, gallery picker | domain |
| `:feature:onboarding/home/templates/sounds/drafts/video-editor/photo-editor/export` | UI | screens + ViewModels | domain, ui |

## Rules

1. Domain (`:core:model`, `:core:domain`) is pure `commonMain`.
2. Features call use cases only.
3. Platform code only in engine/platform modules, behind domain interfaces, plus the composition root (`:androidApp`, `:composeApp`) for entry and wiring only (driver creation, Koin start-up, debug hooks).
4. The iOS framework must compile on every PR.
5. No automated tests in this project; keep interfaces small anyway so behavior is easy to check by hand.

## Project model essentials

- `Project(id, type: VIDEO|PHOTO, name, canvas, video: VideoTimeline?, photo: PhotoScene?, createdAtEpochUs, updatedAtEpochUs, schemaVersion)` in `app.memix.core.model.project`.
- `Track(kind: MAIN_VIDEO|OVERLAY|TEXT|STICKER|MEME_SOUND|AUDIO|EFFECT, items, muted, locked)`; `TimelineItem(id, startUs, durationUs)` sealed: MediaClip, AudioClip, TextItem, StickerItem, EffectItem. Clip `durationUs` is derived from `trimOutUs - trimInUs`.
- Time = microseconds (`Long`), wall-clock timestamps included. Immutable; each edit → new Project. Editors edit through `ProjectEditSession` (`StartEditSessionUseCase`): `commit(tool) { }`/`undo`/`redo` (the `EditorTool` names the step, shown as "Undo: Split"; a recorded step logs `tool_use` itself and `commit` returns whether it recorded one); clip edits are pure functions in `:core:domain` `TimelineEdits.kt` (split, trim + `trimRange`, delete, duplicate, reorder; main track re-packed, other tracks keep their times), `SoundEdits.kt` (`addSound`, `setVolume`), `MoveEdits.kt` (`moveItem` with a `LaneTarget`, `moveItemToPlayhead`), `OriginalAudioEdits.kt` (P1-09: `setOriginalMuted`, `originalSoundOf`, `detachAudio`; no schema change), `TextEdits.kt` (P1-10: `addText` with its landing slot and lane, `editText`, `setCaptionStyle`, `setTextTransform`, Position panel steps; caption content in `CaptionCatalog`, `:core:model`; 250 characters counted by `text/Graphemes.kt`), `CanvasEdits.kt` (P1-11: `setCanvasRatio` keeps fully-inside captions inside, measured with the caption renderer through `CaptionBlockMeasure`; `setClipsFit`, `setCanvasBackground`, `sharedClipsFit`, `backgroundShows`; sizes and rules in `CanvasCatalog`, `:core:model`; no schema change) and `MediaEdits.kt` (P1-16: `insertMedia` at a `MediaPlace` with the shared fit, `replaceFit` + `replaceClipMedia`), 100 undo steps, auto-save 500 ms after the last change, `saveNow()` on ON_STOP, closed with `addCloseable` (a video draft left with no clip is deleted then); the video editor opens drafts with `OpenVideoDraftUseCase` (measures media saved before schema 2, and pictures with no size); stored as JSON in the SQLDelight `project` table (`:core:data`) + a thumbnail file. The composition root (`:composeApp` `databaseDriverModule`) creates the `SqlDriver`; `:core:data` stays commonMain.
- Media refs = `MediaRef(origin: GalleryUri | PhotoAsset | CatalogItem, kind, cachedCopyPath, durationUs, pixelSize, hasAudio)`; paths are relative to the app's files directory (gallery copies in `media/<project id>/`). Duration, shown size and has-sound are measured at import (schema 2) and null in older drafts. Importing goes through `ImportMediaUseCase` (copy, check, measure) driven by `MediaImportFlow` (`:feature:video-editor` `importmedia/`, sheet timing, space wait, results), which hands the copies to an `ImportLanding`: `SaveGalleryProjectUseCase` for Create → Video meme, the edit session for the editor's Add media and Replace (`EditorMediaImport`, P1-16); the pickers are Compose hooks in `:platform:services` used only by the composition root (TECHNICAL_DESIGN → Media import). Unused copies of a saved draft go at the next app start (`LeftoverMediaCleaner`, P1-16). Meme sounds come from `SoundLibrary` (P1-08: the bundled starter pack in `:platform:services` Compose resources, written once per run to the shared `sounds/<catalog id>.m4a`, origin `CatalogItem(id)`; titles looked up by id, TECHNICAL_DESIGN → Meme sounds).
- Export (P1-12): one `ExportVideoUseCase` single in the app's scope (state `Idle | Running | Done | Failed | Cancelled`, read by the Exporting screen, the editor and the notification); the engine fills `ExportCapabilities` per canvas (`exportCapabilities`); settings, learned speed and the unfinished-export record live behind `ExportPreferences` (`:core:data`, multiplatform-settings); the foreground service and notification behind `ExportForeground`, share behind `Sharer`, exit reasons behind `AppExitReasons` (`:platform:services`); the file goes to the gallery (`Movies/Memix`, MediaStore pending item), only the debug export hook writes `cache/exports/` (TECHNICAL_DESIGN → Export).
- Effects/filters/transitions = `id + params` specs rendered identically by both engines; each has a reference render in `docs/qa/references/`.
- Persisted shape change → bump `Project.CURRENT_SCHEMA_VERSION` and add a `ProjectMigration` step in `:core:data` (`ProjectMigrations.kt`); check by opening an old draft after upgrading. A table change adds `migrations/<n>.sqm`; CI runs `:core:data:verifySqlDelightMigration`.

## Engine interfaces (shape)

```kotlin
interface VideoEngine {   // app.memix.core.domain.video; members join with the ticket that first uses them
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): ExportOutcome  // P1-03; P1-12: Done(ExportedVideo) | Failed(ExportError), file into the gallery
    fun createPreview(project: Project): PreviewSession   // P1-04: play/pause/seekTo/update/close, playback + positionUs StateFlows; P1-05 setScrubbing; P1-06 showTrimFrame/endTrimFrames, playback.isCatchingUp; P1-08 previewVolume; P1-10 hideCaption
    fun openThumbnails(): ThumbnailReader   // P1-05: suspend thumbnail(source, timeUs, sizePx) -> one square JPEG tile; close()
    fun openSoundPlayer(): SoundPlayer   // P1-08: the sounds sheet's previews (one sound at a time); close()
    suspend fun waveform(source: AudioRef, buckets: Int): FloatArray                        // P3-09
    suspend fun exportCapabilities(canvasSize: PixelSize): ExportCapabilities               // P1-12: per canvas, scanned once per process
    suspend fun discardUnfinishedExports()                                                  // P1-12
    fun openResultPlayer(location: String): ResultPlayer                                    // P1-12: the Exported screen's player
}
// Project → engine: CompositionPlanner (:engine:video commonMain) builds a plain CompositionPlan in µs;
// each platform only translates it (Media3CompositionBuilder on Android, AVFoundation in P7).
// Export plans drop 0% items; preview plans keep them (plan(keepSilentItems = true)) so a live gain can raise them.
// Captions (P1-10): CaptionClips in the plan, drawn by the engine's one caption renderer in a composition-level overlay;
// the editor measures through CaptionMeasurer (:core:domain) and draws its stand-in with the engine's CaptionStandIn.
interface Segmenter {
    suspend fun segmentImage(image: ImageRef): SegmentationResult
    fun videoSession(source: MediaRef): VideoSegmentationSession
}
```
