# Technical design

Source of truth: the "Memix — Product Requirements" Claude Doc (https://claude.ai/code/artifact/c81028c0-e543-4488-ace3-100bdcbb9e10). This file is an export from Oct 7, 2026; if the two disagree, ask the owner which one wins.

## Architecture at a glance

Shared Kotlin code holds the UI, the domain and the data layer; only the video engine, background removal and platform services are written per platform.

```mermaid
flowchart TB
  subgraph Shared["Shared Kotlin (commonMain)"]
    UI[":feature:* screens + ViewModels<br/>:core:ui, :core:designsystem"]
    DOM[":core:domain — use cases + interfaces<br/>VideoEngine · PhotoRenderer · Segmenter · MediaPicker · Sharer · Ads · Analytics"]
    MOD[":core:model — Project, Track, Clip, Layer…"]
    DATA[":core:data — repositories<br/>supabase-kt (Ktor) · SQLDelight · file cache"]
  end
  subgraph Platform["Platform source sets (androidMain / iosMain)"]
    VE[":engine:video — Media3 | AVFoundation"]
    PH[":engine:photo — encoders"]
    SEG[":engine:segmentation — ML Kit + MediaPipe | Vision"]
    SVC[":platform:services — AdMob+UMP, Firebase, share, picker"]
  end
  subgraph Hosted["Hosted services"]
    SB[("Supabase Postgres<br/>catalog JSON, RLS")]
    R2[("Cloudflare R2<br/>media files")]
    FB[("Firebase<br/>Crashlytics · Analytics · Remote Config")]
    ADS[("AdMob")]
  end
  UI --> DOM
  DATA --> DOM
  DOM --> MOD
  VE -. implements .-> DOM
  PH -. implements .-> DOM
  SEG -. implements .-> DOM
  SVC -. implements .-> DOM
  DATA --> SB
  DATA --> R2
  SVC --> FB
  SVC --> ADS
```

Features and data never talk to each other directly: both go through domain interfaces, which also lets iOS swap in its own engines without touching shared code.

## Modules and layers

The code is one Gradle multi-module Kotlin Multiplatform project using clean architecture: presentation and data both depend on domain, and platform engines sit behind interfaces defined in domain.

| Module | Layer | Holds | Depends on |
| --- | --- | --- | --- |
| `:androidApp` | App | Android `Application`, `MainActivity`, manifest (AGP 9 keeps the Android app out of KMP modules) | `:composeApp` |
| `:composeApp` | App | Shared root: `App()`, navigation host, Koin graph, iOS entry point (`MainViewController`); builds the `ComposeApp` framework that `iosApp/` (Xcode) embeds | All feature modules |
| `:core:model` | Domain | Project, Track, Clip, Layer, Effect, Sound and Template entities; serialization | Nothing |
| `:core:domain` | Domain | Use cases; repository interfaces; engine interfaces (`VideoEngine`, `PhotoRenderer`, `Segmenter`, `MediaPicker`, `Sharer`, `Ads`, `Analytics`) | `:core:model` |
| `:core:data` | Data | Repositories; remote source (supabase-kt on Ktor); local source (SQLDelight); file cache; settings | `:core:domain` |
| `:core:designsystem` | Presentation | Memix tokens, theme, base components | Nothing |
| `:core:ui` | Presentation | Shared composables: timeline, layer handles, sliders, pickers | `:core:designsystem`, `:core:model` |
| `:engine:video` | Platform | `VideoEngine`: Media3 in `androidMain`, AVFoundation in `iosMain` | `:core:domain` |
| `:engine:photo` | Platform | Shared photo renderer plus platform encoders (PNG, JPG, GIF, WebP) | `:core:domain` |
| `:engine:segmentation` | Platform | `Segmenter`: ML Kit and MediaPipe on Android, Vision on iOS | `:core:domain` |
| `:platform:services` | Platform | AdMob and UMP, Firebase, share intents, gallery picker | `:core:domain` |
| `:feature:onboarding`, `:feature:home`, `:feature:templates`, `:feature:sounds`, `:feature:drafts`, `:feature:video-editor`, `:feature:photo-editor`, `:feature:export` | Presentation | Screens and ViewModels | `:core:domain`, `:core:ui` |

- **Domain stays pure:** `:core:model` and `:core:domain` live in `commonMain` with no Android or iOS imports.
- **Features call use cases only:** never repositories or engines directly.
- **Platform code** lives only in the `androidMain` and `iosMain` source sets of engine and platform modules.
- **iOS compiles from day one:** CI builds the iOS framework on every push to a phase branch and every pull request, even before iOS work starts, so Android-only APIs never leak into shared code.
- **State:** each ViewModel exposes one `StateFlow<UiState>` and takes `Intent` events (MVVM with one-way data flow). Editors keep an undo stack of immutable project snapshots.

## Project data model

One serializable `Project` describes both editors, so drafts, templates, undo and export all work from the same data. The sketch below sets the shape; field names can change during implementation.

```kotlin
@Serializable
data class Project(
    val id: String,
    val type: ProjectType,            // VIDEO or PHOTO
    val name: String,
    val canvas: Canvas,               // ratio, width, height, background
    val video: VideoTimeline? = null, // set when type == VIDEO
    val photo: PhotoScene? = null,    // set when type == PHOTO
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = 1,
)

data class VideoTimeline(val tracks: List<Track>)

data class Track(
    val id: String,
    val kind: TrackKind,              // MAIN_VIDEO, OVERLAY, TEXT, STICKER, MEME_SOUND, AUDIO, EFFECT
    val items: List<TimelineItem>,
    val muted: Boolean = false,
    val locked: Boolean = false,
)

sealed interface TimelineItem { val id: String; val startUs: Long; val durationUs: Long }

// MediaClip: source, trimInUs, trimOutUs, speed (constant or curve), transform, volume,
//            filters, mask, chromaKey, removeBackground, keyframes, transitionOut
// AudioClip: source, trim, volume, fadeInUs, fadeOutUs, pitch, speed, audioEffects
// TextItem, StickerItem: content, style, transform, animation, keyframes
// EffectItem: effect spec applied to the whole canvas over its time range

data class PhotoScene(val format: PhotoFormat, val layers: List<Layer>)
// Layers: ImageLayer, PanelImage, TextLayer, StickerLayer, DrawingLayer, CensorLayer, CutoutLayer
```

- **Time** is in microseconds (`Long`), matching Media3 and AVFoundation.
- **Media references** hold the gallery URI (or PHAsset id on iOS) plus a cached copy path, so a draft survives the original file moving.
- **Effects, filters and transitions** are declarative specs (an id plus parameters). Each engine renders the same spec, which keeps Android and iOS output alike.
- **Storage:** projects are JSON rows in a SQLDelight `project` table with a thumbnail file. Auto-save runs 500 ms after the last change.
- **Migrations:** `schemaVersion` plus a migration function per version.
- **Undo:** each edit produces a new immutable `Project`; the editor keeps the last 100.

## Video engine

One shared interface renders a `Project`; each platform implements it with its native framework, so decoding, effects and encoding run on the phone's video hardware.

```kotlin
interface VideoEngine {
    fun createPreview(project: Project): PreviewSession
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Float) -> Unit): ExportResult
    suspend fun thumbnails(source: MediaRef, count: Int, heightPx: Int): List<ImageRef>
    suspend fun waveform(source: AudioRef, buckets: Int): FloatArray
    fun capabilities(): EngineCapabilities   // max resolution and fps per codec, HEVC support
}

interface PreviewSession {
    val state: StateFlow<PlaybackState>
    fun play(); fun pause(); fun seekTo(us: Long)
    fun update(project: Project)   // re-render after an edit without rebuilding the player
    fun release()
}
```

**Android (v1): Media3.** The engine maps a project to a Media3 `Composition`. The main video track becomes one `EditedMediaItemSequence`; overlays, meme sounds, audio and voiceover become further sequences that overlap in time, which Media3 supports for layering ([Media3 Composition](https://developer.android.com/media/media3/transformer/composition)). `CompositionPlayer` drives the preview and `Transformer` exports ([multi-asset editing](https://developer.android.com/media/media3/transformer/multi-asset)).

- **Video effects:** Media3 GL effects plus custom GLSL shader programs for filters (3D LUTs), chroma key, masks, blend modes, glitch, RGB split, VHS and deep-fry; matrix transforms for zoom punch, shake and keyframed motion.
- **Text and stickers:** rendered to bitmaps and composited as overlay effects, re-rendered only when they change.
- **Audio:** audio processors for volume, fades, pitch, speed, echo and bass boost.
- **Speed:** constant speed and per-segment speed curves.
- **Background export:** runs in a foreground service with a progress notification.
- **Preview view:** Media3's player view hosted in Compose through `AndroidView`.

Exact Media3 class names and versions are checked against the current docs when each ticket starts.

**iOS (phase 7): AVFoundation.** `AVMutableComposition` for tracks, `AVMutableVideoComposition` with a custom compositor (Metal and Core Image) for effects and overlays, `AVAudioMix` for volume, `AVAssetWriter` for export, and `AVPlayerLayer` hosted through `UIKitView`.

**Keeping both alike:** every effect spec has a reference render: a fixed sample project rendered on Android and saved as images in `docs/qa/references/`. QA compares iOS output with them side by side.

## Photo rendering and background removal

The photo editor renders in shared Compose code, so it needs very little extra work on iOS. Export draws the same layer scene into a full-resolution offscreen bitmap, then a small platform encoder writes PNG, JPG, GIF or WebP.

- **Filters:** color-matrix adjustments run in shared code; LUT filters run through a small platform filter interface on the GPU, using the same filter ids as video so looks match.
- **Fonts:** bundled OFL fonts (Anton for captions, Space Grotesk for UI) shared by both editors.
- **Make it a video meme:** converts the `PhotoScene` into a `VideoTimeline` with the photo as the main clip and each layer as a timed item.

```kotlin
interface Segmenter {
    suspend fun segmentImage(image: ImageRef): SegmentationResult     // full mask + one mask per subject
    fun videoSession(source: MediaRef): VideoSegmentationSession     // person masks per frame
}
```

| Case | Android (v1) | iOS (phase 7) |
| --- | --- | --- |
| Photo: people, pets, objects | ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation`, beta). The model downloads through Google Play services; check availability first and show "Preparing…" ([docs](https://developers.google.com/ml-kit/vision/subject-segmentation/android)) | `VNGenerateForegroundInstanceMaskRequest` (iOS 17+) |
| Video: people | MediaPipe Image Segmenter in VIDEO mode with a person model ([docs](https://developers.google.cn/mediapipe/solutions/vision/image_segmenter)) | `VNGeneratePersonSegmentationRequest` with one `VNSequenceRequestHandler` per clip ([Apple sample](https://developer.apple.com/documentation/vision/applying-matte-effects-to-people-in-images-and-video)) |

- **Video masks** are computed at preview resolution and cached per frame while editing, then recomputed at export resolution. The GL pipeline applies them as alpha.
- **Refine brush:** the user's erase and restore strokes are saved as a mask layer (PNG at canvas resolution) inside the project.
- **Fallback:** if ML Kit isn't available on a device, photo removal falls back to the MediaPipe person model and says so.

## Backend

Supabase serves small JSON catalog lists and Cloudflare R2 serves every audio, image and video file. There is no custom server code: the app reads tables through Supabase's auto-generated API with supabase-kt ([supabase-kt](https://github.com/supabase-community/supabase-kt)).

```sql
create extension if not exists pg_trgm;

create table categories (
  id text primary key,
  kind text not null check (kind in ('sound','sticker','template')),
  name jsonb not null,                 -- {"en": "Reactions", "id": "Reaksi", ...}
  sort int not null default 0,
  is_active boolean not null default true
);

-- Columns shared by every catalog item (sounds, templates, sticker_packs)
--   title jsonb, category_id text references categories(id),
--   regions text[] default '{global}', tags text[] default '{}',
--   source_url text not null, license_type text not null
--     check (license_type in ('CC0','CC-BY','royalty-free','own','licensed')),
--   license_proof text not null, credit text,
--   is_active boolean default true, added_by text,
--   created_at timestamptz default now(), updated_at timestamptz default now()

create table sounds (
  id uuid primary key default gen_random_uuid(),
  file_path text not null,             -- R2 key: sounds/<yyyy>/<mm>/<id>.m4a
  duration_ms int not null,
  waveform_path text
  -- + shared catalog columns
);

create table templates (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('video_overlay','video_cutout','video_preset','photo_format','photo_template')),
  preview_path text not null,
  asset_paths text[] not null,
  preset jsonb not null                -- slots, timings, sound cues, effects, text styles
  -- + shared catalog columns
);

create table sticker_packs (id uuid primary key default gen_random_uuid(), cover_path text not null /* + shared */);
create table stickers (id uuid primary key default gen_random_uuid(), pack_id uuid references sticker_packs(id),
  file_path text not null, animated boolean default false, sort int default 0);

create table trending (
  month date not null, region text not null,          -- ISO country code or 'global'
  kind text not null check (kind in ('sound','template')),
  item_id uuid not null, rank int not null,
  primary key (month, region, kind, rank)
);

create table reports (
  id uuid primary key default gen_random_uuid(),
  item_kind text not null, item_id uuid not null, reason text,
  status text not null default 'open', created_at timestamptz default now()
);

create index sounds_title_trgm on sounds using gin ((title::text) gin_trgm_ops);
create index sounds_tags on sounds using gin (tags);
-- search_sounds(q text, region text, lim int, off int): trigram match on title and tags, active rows only
```

- **Access rules (RLS):** the app's public key can read rows where `is_active = true` and can insert into `reports`. Nothing else. All other writes happen in the Supabase dashboard; the service key never ships in the app.
- **Files:** one public R2 bucket behind a custom domain (`media.<domain>`). Keys are immutable: a changed file gets a new key, so files can be cached forever. The app joins `media_base_url` from Remote Config with each row's `file_path`.
- **On the phone:** SQLDelight caches catalog rows; downloaded files live in the cache directory with a 500 MB least-recently-used limit.

| App call | Request | Refresh |
| --- | --- | --- |
| Categories | `categories?is_active=eq.true&order=sort` | 24 h |
| Sounds page | `sounds?category_id=eq.X&regions=ov.{ID,global}&order=created_at.desc&limit=30&offset=N` | 6 h |
| Search | `rpc/search_sounds` | Never cached |
| Trending | `trending?month=eq.<this month>&region=in.(ID,global)` plus the items | 6 h |
| Templates page and detail | `templates?kind=eq.X&limit=30`, `templates?id=eq.X` | 6 h / 24 h |
| Sticker packs | `sticker_packs`, `stickers?pack_id=eq.X` | 24 h |
| Report | `POST reports` | — |

**Cost:** the Free plan covers development and launch. With files on R2, Supabase only serves small lists, so its 5 GB monthly egress lasts to about 1,500 to 3,000 DAU. After that, Pro is $25 a month with 250 GB of egress ([Supabase pricing](https://supabase.com/pricing)). Over-quota Free projects get restricted rather than billed ([billing FAQ](https://supabase.com/docs/guides/platform/billing-faq)), so usage needs a weekly check.

## CMS workflow

Until phase 4, the Supabase dashboard is the CMS and a desktop script does the file work. A small admin web page (bulk upload, waveform generation, trending editor) comes in phase 4, hosted separately from the app.

**Adding a sound**

1. Find a candidate: Freesound with the CC0 filter, Pixabay, or record your own recreation.
2. Save the license proof: the license page URL plus a screenshot.
3. Run the intake script: trims silence, normalizes to −16 LUFS with −1 dBTP peaks, encodes AAC 128 kbps `.m4a`, and writes a waveform JSON. Desktop tools such as ffmpeg are fine here because they never ship inside the app.
4. Upload the files to R2 under `sounds/<yyyy>/<mm>/<id>.m4a`.
5. Add the row in the Supabase table editor with every license field filled.
6. Check it in the app after the next catalog refresh.

Templates and sticker packs follow the same steps with their own folders (`templates/`, `stickers/`, `previews/`).

**Monthly trending:** on the 1st of each month, add `trending` rows per launch region and for `global`: 10 to 20 sounds and 5 to 10 templates each.

**Takedowns:** a report or email arrives, set the item's `is_active` to false, record the outcome on the `reports` row, and reply within 72 hours.

## Libraries and platform services

Every version is pinned in `gradle/libs.versions.toml` and set to the latest stable release on the day the project is created. No GPL libraries, and no FFmpeg inside the app.

| Need | Choice |
| --- | --- |
| Language and build | Kotlin (latest stable), Gradle with version catalogs |
| Shared UI | Compose Multiplatform 1.12.1 ([JetBrains' compatibility page](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)); Memix components built on `foundation` under a custom Memix theme |
| Dependency injection | Koin |
| Navigation and ViewModels | Multiplatform Navigation Compose, multiplatform lifecycle ViewModel |
| Async and data | kotlinx.coroutines and Flow, kotlinx.serialization |
| Network | Ktor client with supabase-kt (postgrest-kt) |
| Local storage | SQLDelight; multiplatform settings for small preferences |
| Images | Coil 3 |
| Video, Android | Media3 Transformer, CompositionPlayer, effect libraries |
| Video, iOS | AVFoundation, Core Image, Metal |
| Background removal | ML Kit Subject Segmentation, MediaPipe Tasks Vision, Apple Vision |
| Ads and consent | Google Mobile Ads SDK with the UMP SDK |
| Crashes and analytics | Firebase Crashlytics and Analytics, behind the shared `Analytics` interface |
| Tests | No automated tests; manual QA at the end of each phase (adb-driven emulator runs, ffprobe for exported files) |

## Testing, CI and release builds

Memix has no automated tests (no unit, UI, screenshot or golden-frame tests): quality comes from builds in CI, code and design review on every ticket, and a manual QA pass at the end of every phase. Each phase is one branch and one pull request; every push and every pull request must build Android and compile the iOS framework, and the phase PR merges only when green.

| Check | What | When |
| --- | --- | --- |
| Build | Android debug build, lint, iOS framework compile | Every push to a phase branch and every pull request (CI) |
| Engineer self-check | Run the change on an emulator or phone; list what was checked in the ticket's commit message | Every ticket |
| Code review | Principal mobile engineer's review checklist | Every ticket |
| Design review | UX designer compares the built screens with the spec | Every UI ticket |
| Manual QA | QA runs the phase test plan on an emulator and hands device-only checks to the owner | End of every phase |
| Performance | Cold start, frame timing and export time against the PRD targets (profiler, `dumpsys gfxinfo`) | End of every phase |

- **Device matrix:** one flagship, one upper-mid, and one Android 10 phone, plus Firebase Test Lab when needed.
- **CI:** GitHub Actions. The CI workflow runs the Android build, lint, and the iOS framework compile (on a macOS runner) for pushes to `main` and `phase-*` branches and for pull requests. The release workflow builds a signed app bundle and uploads it to the Play internal testing track.
- **Definition of done** for every ticket is in the Build tickets tab.

## Security and privacy

Memix keeps no user data on its servers: projects, imports and favorites stay on the phone.

- The app ships only the Supabase public (anon) key. Access rules limit it to reading active catalog rows and inserting reports.
- The Supabase service key, R2 write keys and app signing keys never enter the repo. They live in CI secrets and on the admin machine.
- The advertising ID is used only after consent where consent is required. Analytics events carry no personal data.
- The Play Data safety form lists: advertising ID, crash logs and app interaction analytics.

## Decisions

Engineering decisions made during the build, newest first. Each says what, why and the alternative.

| Date | Decision | Why | Alternative |
| --- | --- | --- | --- |
| 2026-10-07 | Module split for AGP 9: `:androidApp` (Android application) + `:composeApp` (shared KMP library) + `iosApp/` (Xcode). | AGP 9 makes `com.android.application` incompatible with the KMP plugin in one module ([kotlinlang.org](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)). | Stay on AGP 8 (removed path; AGP 10 drops the legacy API). |
| 2026-10-07 | Every KMP module uses `com.android.kotlin.multiplatform.library` via the `memix.kmp.library` convention plugin; targets android, iosArm64, iosSimulatorArm64. No iosX64. | One place for target setup across 18 modules; Apple-silicon Macs and CI runners only. | Per-module copies of the target block. |
| 2026-10-07 | Versions pinned on creation day: Kotlin 2.4.20, AGP 9.4.1, Gradle 9.8.0, Compose MP 1.12.1, lifecycle 2.11.0, navigation-compose 2.9.2, coroutines 1.11.0, serialization 1.11.0, Koin 4.2.2, Ktor 3.6.0, supabase-kt 3.8.0, SQLDelight 2.4.1, Coil 3.6.3, Media3 1.11.1, Firebase BOM 34.19.0. compileSdk 37, targetSdk 36, minSdk 29. | Latest stable on 7 Oct 2026, checked on GitHub releases, Maven Central, Google Maven, kotlinlang.org and developer.android.com. targetSdk 36 meets Play's rule; move to 37 before Play's 2027 deadline. | Pre-releases (navigation 2.10 RC, serialization 1.12 RC). |
| 2026-10-07 | Material3 is pinned (1.9.0, the newest stable multiplatform release) but not used by default; Memix components build on `foundation`. | Multiplatform Material3 stable lags three versions behind Compose 1.12, and the design system is custom. | Material3 1.12.0-alpha03, which Compose 1.12.1 pairs with. |

## Sources

Official docs to read before the matching tickets:

- [Compose Multiplatform compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) (JetBrains)
- [Media3 Transformer: Composition](https://developer.android.com/media/media3/transformer/composition) and [multi-asset editing](https://developer.android.com/media/media3/transformer/multi-asset) (Android Developers)
- [ML Kit Subject Segmentation for Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android) (Google)
- [MediaPipe Image Segmenter](https://developers.google.cn/mediapipe/solutions/vision/image_segmenter) (Google)
- [Applying matte effects to people in images and video](https://developer.apple.com/documentation/vision/applying-matte-effects-to-people-in-images-and-video) and [VNGenerateForegroundInstanceMaskRequest](https://developer.apple.com/documentation/vision/vngenerateforegroundinstancemaskrequest) (Apple)
- [supabase-kt](https://github.com/supabase-community/supabase-kt) (Supabase community)
- [Supabase pricing](https://supabase.com/pricing) and [billing FAQ](https://supabase.com/docs/guides/platform/billing-faq)
- [AdMob consent management requirements](https://support.google.com/admob/answer/13554116?hl=en) and [UMP GDPR guide for Android](https://developers.google.com/admob/android/privacy/gdpr)
- [Google Play testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB)
