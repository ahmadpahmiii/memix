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
| `:core:domain` | Domain | use cases; repository + engine interfaces (`VideoEngine`, `PreviewSession`, `PhotoRenderer`, `Segmenter`, `MediaPicker`, `Sharer`, `Ads`, `Analytics`) | `:core:model` |
| `:core:data` | Data | repositories, supabase-kt remote, SQLDelight local, file cache, settings | `:core:domain` |
| `:core:designsystem` | UI | `MemixTheme`, tokens, base components | — |
| `:core:ui` | UI | timeline, layer handles, sliders, pickers | designsystem, model |
| `:engine:video` | Platform | `VideoEngine`: Media3 (androidMain), AVFoundation (iosMain) | domain |
| `:engine:photo` | Platform | photo renderer + PNG/JPG/GIF/WebP encoders | domain |
| `:engine:segmentation` | Platform | `Segmenter`: ML Kit + MediaPipe / Vision | domain |
| `:platform:services` | Platform | AdMob+UMP, Firebase, share intents, gallery picker | domain |
| `:feature:onboarding/home/templates/sounds/drafts/video-editor/photo-editor/export` | UI | screens + ViewModels | domain, ui |

## Rules

1. Domain (`:core:model`, `:core:domain`) is pure `commonMain`.
2. Features call use cases only.
3. Platform code only in engine/platform modules, behind domain interfaces.
4. The iOS framework must compile on every PR.
5. No automated tests in this project; keep interfaces small anyway so behavior is easy to check by hand.

## Project model essentials

- `Project(id, type: VIDEO|PHOTO, name, canvas, video: VideoTimeline?, photo: PhotoScene?, createdAt, updatedAt, schemaVersion)`.
- `Track(kind: MAIN_VIDEO|OVERLAY|TEXT|STICKER|MEME_SOUND|AUDIO|EFFECT, items, muted, locked)`; `TimelineItem(id, startUs, durationUs)` sealed: MediaClip, AudioClip, TextItem, StickerItem, EffectItem.
- Time = microseconds (`Long`). Immutable; each edit → new Project; undo stack 100; autosave 500 ms debounce; stored as JSON in the SQLDelight `project` table + a thumbnail file.
- Media refs = gallery URI / PHAsset id + cached copy path.
- Effects/filters/transitions = `id + params` specs rendered identically by both engines; each has a reference render in `docs/qa/references/`.
- Persisted shape change → bump `schemaVersion` and add a migration; check by opening an old draft after upgrading.

## Engine interfaces (shape)

```kotlin
interface VideoEngine {
    fun createPreview(project: Project): PreviewSession
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): ExportResult
    suspend fun thumbnails(source: MediaRef, count: Int, heightPx: Int): List<ImageRef>
    suspend fun waveform(source: AudioRef, buckets: Int): FloatArray
    fun capabilities(): EngineCapabilities
}
interface Segmenter {
    suspend fun segmentImage(image: ImageRef): SegmentationResult
    fun videoSession(source: MediaRef): VideoSegmentationSession
}
```
