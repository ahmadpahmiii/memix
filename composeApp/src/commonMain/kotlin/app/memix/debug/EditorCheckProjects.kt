package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.EffectSpec
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.VideoTimeline
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Saves one of the editor's hand-check projects as a draft and returns its id, so the app can open it in the editor
 * (`adb shell am start ... --es memix.openEditor <name>`, debug and benchmark builds; steps in
 * docs/qa/phase-1/engineer-hand-checks.md → P1-04). Null for an unknown name or a failed save, both logged under
 * [EditorCheckProjects.TAG].
 */
internal suspend fun openDebugEditorProject(name: String): String? = EditorCheckProjects().save(name)

/**
 * The projects, all built from the test files pushed into `files/debug-media/` for P1-03 plus `clip-1080p.mp4`.
 * Their media carry no measured lengths, like drafts from before schema 2, so opening them also checks that the
 * editor measures the files itself.
 */
internal class EditorCheckProjects : KoinComponent {
    private val saveProject: SaveProjectUseCase by inject()
    private val logger: Logger by inject()

    suspend fun save(name: String): String? {
        val project = build(name)
        if (project == null) {
            logger.error(TAG, "Unknown memix.openEditor '$name'. Use ${NAMES.joinToString()}.")
            return null
        }
        return when (val saved = saveProject(project)) {
            is Outcome.Success -> {
                logger.debug(TAG, "openEditor: saved ${project.id}, opening it")
                project.id
            }
            is Outcome.Failure -> {
                logger.error(TAG, "openEditor: saving $name failed with ${saved.error}")
                null
            }
        }
    }

    private fun build(name: String): Project? = when (name) {
        EXPORT_CHECK -> ExportCheckProject.build()
        PREVIEW_CHECK -> previewCheck()
        PREVIEW_ERROR -> previewError()
        MISSING_MEDIA -> missingMedia()
        EMPTY -> project(EMPTY, mainClips = emptyList())
        TIMELINE_CHECK -> timelineCheck()
        TIMELINE_TRACKS -> timelineTracks()
        HOUR_LONG -> hourLong()
        else -> null
    }

    /**
     * The P1-04 frame-rate check: 30 s of a 1080 × 1920, 30 fps video on six tracks. Only the main video and the
     * two sound tracks render in P1; text, sticker and effect tracks are skipped with a log line until their tickets.
     */
    private fun previewCheck() = project(
        PREVIEW_CHECK,
        mainClips = listOf(
            MediaClip("check-1080p-first", startUs = 0, source = clip1080p, trimInUs = 0, trimOutUs = 15_000_000),
            MediaClip("check-1080p-second", startUs = 15_000_000, source = clip1080p, trimInUs = 15_000_000, trimOutUs = 30_000_000),
        ),
        extraTracks = listOf(
            Track(
                "check-sounds",
                TrackKind.MEME_SOUND,
                listOf(3_500_000L, 12_000_000L, 24_000_000L).mapIndexed { index, startUs ->
                    AudioClip("check-beep-$index", startUs, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000)
                },
            ),
            Track(
                "check-audio",
                TrackKind.AUDIO,
                listOf(AudioClip("check-a-audio", startUs = 5_000_000, source = clipA, trimInUs = 2_000_000, trimOutUs = 5_000_000)),
            ),
            Track("check-text", TrackKind.TEXT, listOf(TextItem("check-caption", startUs = 0, durationUs = 5_000_000, "Preview check", CAPTION))),
            Track("check-sticker", TrackKind.STICKER, listOf(StickerItem("check-photo", startUs = 10_000_000, durationUs = 2_000_000, photo))),
            Track(
                "check-effect",
                TrackKind.EFFECT,
                listOf(EffectItem("check-zoom", startUs = 4_000_000, durationUs = 400_000, EffectSpec("effect.zoom_punch"))),
            ),
        ),
    )

    /**
     * The P1-05 scroll check (spec → QA compares, Measured): 20 main clips (videos of three shapes and a photo, 80 s)
     * and three more lanes: captions, meme sounds (the last one runs 2 s past the end) and the first clip's detached
     * sound, labelled "Original audio".
     */
    private fun timelineCheck(): Project {
        val pattern = listOf(
            Triple(clipA, 0L, 6_000_000L),
            Triple(clipB, 1_000_000L, 4_000_000L),
            Triple(clip1080p, 0L, 5_000_000L),
            Triple(photo, 0L, 2_000_000L),
        )
        var startUs = 0L
        val mainClips = (0 until TIMELINE_CHECK_CLIPS).map { index ->
            val (source, trimInUs, trimOutUs) = pattern[index % pattern.size]
            MediaClip("timeline-clip-$index", startUs, source, trimInUs, trimOutUs, audioDetached = index == 0).also { startUs += it.durationUs }
        }
        val lengthUs = startUs
        val captions = (0 until lengthUs / 10_000_000).map { index ->
            TextItem("timeline-caption-$index", startUs = index * 10_000_000, durationUs = 3_000_000, "Caption ${index + 1}", CAPTION)
        }
        val sounds = (0 until lengthUs / 7_000_000).map { index ->
            AudioClip("timeline-beep-$index", startUs = index * 7_000_000 + 2_000_000, source = sound, trimInUs = 0, trimOutUs = 1_000_000)
        } + AudioClip("timeline-past-end", startUs = lengthUs - 2_000_000, source = clipA, trimInUs = 0, trimOutUs = 4_000_000)
        return project(
            TIMELINE_CHECK,
            mainClips,
            extraTracks = listOf(
                Track("timeline-text", TrackKind.TEXT, captions),
                Track("timeline-sounds", TrackKind.MEME_SOUND, sounds),
                Track("timeline-original", TrackKind.AUDIO, listOf(AudioClip("timeline-original-0", 0, clipA, trimInUs = 0, trimOutUs = 6_000_000))),
            ),
        )
    }

    /**
     * Many tracks (spec P1-05 → QA compares 2): the preview check's six tracks plus an overlay and a second meme-sound
     * lane whose sound runs 3 s past the end, so the lanes scroll on a phone.
     */
    private fun timelineTracks(): Project {
        val base = previewCheck()
        val video = base.video ?: return base
        val extra = listOf(
            Track("tracks-overlay", TrackKind.OVERLAY, listOf(MediaClip("tracks-overlay-b", startUs = 6_000_000, source = clipB, trimInUs = 0, trimOutUs = 2_000_000))),
            Track("tracks-sounds-2", TrackKind.MEME_SOUND, listOf(AudioClip("tracks-past-end", startUs = 28_000_000, source = clipA, trimInUs = 0, trimOutUs = 5_000_000))),
        )
        return base.copy(id = TIMELINE_TRACKS, name = "Editor check: $TIMELINE_TRACKS", video = video.copy(tracks = video.tracks + extra))
    }

    /** An hour of video (spec P1-05 → States, very long project): the 1080p clip's first 30 s, 120 times. */
    private fun hourLong() = project(
        HOUR_LONG,
        mainClips = (0 until HOUR_LONG_CLIPS).map { index ->
            MediaClip("hour-$index", startUs = index * 30_000_000L, source = clip1080p, trimInUs = 0, trimOutUs = 30_000_000)
        },
    )

    /** Two main clips that overlap: a damaged project the engine refuses, so the editor shows "The preview stopped". */
    private fun previewError() = project(
        PREVIEW_ERROR,
        mainClips = listOf(
            MediaClip("error-a", startUs = 0, source = clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("error-b", startUs = 2_000_000, source = clipB, trimInUs = 0, trimOutUs = 3_000_000),
        ),
    )

    /** Clip A, then 3 s from a file that isn't there (plays black, with the one-time message), then clip B. */
    private fun missingMedia() = project(
        MISSING_MEDIA,
        mainClips = listOf(
            MediaClip("missing-a", startUs = 0, source = clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("missing-gone", startUs = 3_000_000, source = missingFile, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("missing-b", startUs = 6_000_000, source = clipB, trimInUs = 0, trimOutUs = 3_000_000),
        ),
    )

    private fun project(id: String, mainClips: List<MediaClip>, extraTracks: List<Track> = emptyList()) = Project(
        id = id,
        type = ProjectType.VIDEO,
        name = "Editor check: $id",
        canvas = Canvas(CanvasRatio.RATIO_9_16, widthPx = 1080, heightPx = 1920, background = CanvasBackground.Solid(BLACK)),
        video = VideoTimeline(listOf(Track("$id-main", TrackKind.MAIN_VIDEO, mainClips)) + extraTracks),
        createdAtEpochUs = EDITED_AT_EPOCH_US,
        updatedAtEpochUs = EDITED_AT_EPOCH_US,
    )

    companion object {
        const val TAG = "MemixEditorCheck"
        const val EXPORT_CHECK = ExportCheckProject.ID
        const val PREVIEW_CHECK = "preview-check"
        const val PREVIEW_ERROR = "preview-error"
        const val MISSING_MEDIA = "missing-media"
        const val EMPTY = "empty"
        const val TIMELINE_CHECK = "timeline-check"
        const val TIMELINE_TRACKS = "timeline-tracks"
        const val HOUR_LONG = "hour-long"
        private val NAMES = listOf(EXPORT_CHECK, PREVIEW_CHECK, PREVIEW_ERROR, MISSING_MEDIA, EMPTY, TIMELINE_CHECK, TIMELINE_TRACKS, HOUR_LONG)
        private const val TIMELINE_CHECK_CLIPS = 20
        private const val HOUR_LONG_CLIPS = 120

        // 8 Oct 2026, 12:00:00 UTC; saving sets the real edit time.
        private const val EDITED_AT_EPOCH_US = 1_791_460_800_000_000L
        private val BLACK = ArgbColor(0xFF000000)
        private val CAPTION = CaptionStyle(fontId = "anton", color = ArgbColor(0xFFFFFFFF), outlineColor = BLACK)

        private val clip1080p = media("clip-1080p.mp4", MediaKind.VIDEO)
        private val clipA = media("clip-a.mp4", MediaKind.VIDEO)
        private val clipB = media("clip-b.mp4", MediaKind.VIDEO)
        private val photo = media("photo.png", MediaKind.IMAGE)
        private val missingFile = media("missing.mp4", MediaKind.VIDEO)
        private val sound = MediaRef(MediaOrigin.CatalogItem("debug-beep"), MediaKind.AUDIO, cachedCopyPath = "${ExportCheckProject.MEDIA_DIR}/sound.m4a")

        // Pushed by hand, so the origin is a stand-in; the engine plays the copy.
        private fun media(fileName: String, kind: MediaKind) = MediaRef(
            MediaOrigin.GalleryUri("content://app.memix.debug/$fileName"),
            kind,
            cachedCopyPath = "${ExportCheckProject.MEDIA_DIR}/$fileName",
        )
    }
}
