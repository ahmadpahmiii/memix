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
import app.memix.core.model.project.Transform
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
        SOUND_SYNC -> soundSync()
        TEXT_CHECK -> textCheck()
        CANVAS_CHECK -> canvasCheck()
        else -> null
    }

    /**
     * The P1-08 sync check: 12 s of photos (silent, so only the sounds are heard) and the 2 kHz test tone, whose sound
     * starts 0.5 s into `sound.m4a`, placed so the tone starts at 1.0 s, 6.0 s, 8.0 s (start trimmed further in) and
     * 11.8 s (runs past the end at 12 s, so the last 0.3 s is washed and silent). The preview logs where each is heard
     * (`MemixPreview`, "Sound onset"); the steps are in engineer-hand-checks.md → P1-08.
     */
    private fun soundSync() = project(
        SOUND_SYNC,
        mainClips = (0 until SOUND_SYNC_PHOTOS).map { index ->
            MediaClip("sync-photo-$index", startUs = index * 3_000_000L, source = photo, trimInUs = 0, trimOutUs = 3_000_000)
        },
        extraTracks = listOf(
            Track(
                "sync-sounds",
                TrackKind.MEME_SOUND,
                listOf(
                    AudioClip("sync-start", startUs = 1_000_000, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000),
                    AudioClip("sync-middle", startUs = 6_000_000, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000),
                    AudioClip("sync-trimmed", startUs = 8_000_000, source = sound, trimInUs = 700_000, trimOutUs = 1_000_000),
                    AudioClip("sync-past-end", startUs = 11_800_000, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000),
                ),
            ),
        ),
    )

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

    /**
     * The P1-10 Done when (spec P1-10 → QA compares → Measured): 9 s of clip A, clip B and the photo, with six captions
     * covering every font, Devanagari, accented Latin and emoji, outline on and off, scale 50%, 100% and 250%, rotation
     * −15°, 0° and 30°, one partly off the frame and two overlapping (on two lanes, the lower drawn on top). Pause at
     * 1.0 s, 4.0 s and 7.0 s and compare with the exported frames.
     */
    private fun textCheck(): Project {
        val clips = listOf(
            MediaClip("text-clip-a", 0, clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("text-clip-b", 3_000_000, clipB, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("text-photo", 6_000_000, photo, trimInUs = 0, trimOutUs = 3_000_000),
        )
        fun caption(id: String, startS: Int, endS: Int, text: String, style: CaptionStyle, transform: Transform) =
            TextItem(id, startS * 1_000_000L, (endS - startS) * 1_000_000L, text, style, transform)
        val yellow = ArgbColor(0xFFFFD60A)
        val white = ArgbColor(0xFFFFFFFF)
        val classic = caption("text-classic", 0, 9, "जब BUILD PASS हो 😂", CAPTION, Transform(0.5f, 0.2f))
        val modern = caption("text-modern", 0, 4, "Ação rápida, ¿sí? ¡Ñandú!", CaptionStyle("poppins", yellow, null), Transform(0.5f, 0.45f, scale = 0.5f, rotationDegrees = -15f))
        val serif = caption("text-serif", 5, 9, "THE END", CaptionStyle("rozha", white, null), Transform(0.5f, 0.45f))
        val handwritten = caption("text-handwritten", 0, 5, "me: हाँ, sure", CaptionStyle("kalam", ArgbColor(0xFF64D2FF), BLACK), Transform(0.5f, 0.75f))
        // Over THE END on the frame, on a lane below it, so it's drawn on top.
        val over = caption("text-over", 6, 9, "OVERLAP", CaptionStyle("anton", ArgbColor(0xFFFF3B30), BLACK), Transform(0.55f, 0.47f))
        // Partly off the frame's right edge: clipped there.
        val bubbly = caption("text-bubbly", 3, 9, "BUBBLY\nTWO LINES", CaptionStyle("baloo2", white, ArgbColor(0xFFAF52DE)), Transform(0.85f, 0.7f, scale = 2.5f, rotationDegrees = 30f))
        return project(
            TEXT_CHECK,
            clips,
            extraTracks = listOf(
                Track("text-lane-1", TrackKind.TEXT, listOf(classic)),
                Track("text-lane-2", TrackKind.TEXT, listOf(modern, serif)),
                Track("text-lane-3", TrackKind.TEXT, listOf(handwritten, over)),
                Track("text-lane-4", TrackKind.TEXT, listOf(bubbly)),
            ),
        )
    }

    /**
     * The P1-11 checks (spec P1-11 → QA compares, request 10): a 16:9 video, a 9:16 video and a 4:3 photo, Fit on black,
     * with four captions on their own lanes for all 9 s: a large two-line one near the top at 200% (pulled in by 16:9),
     * one turned 30° near the right edge, one half off the left edge on purpose (never moved) and one in the middle.
     */
    private fun canvasCheck(): Project {
        val clips = listOf(
            MediaClip("canvas-wide", 0, clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("canvas-tall", 3_000_000, clipB, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("canvas-photo", 6_000_000, photo4x3, trimInUs = 0, trimOutUs = 3_000_000),
        )
        fun caption(id: String, text: String, transform: Transform) = TextItem(id, 0, 9_000_000, text, CAPTION, transform)
        val captions = listOf(
            caption("canvas-top", "BUILD\nPASSED", Transform(0.5f, 0.18f, scale = 2f)),
            caption("canvas-turned", "TURNED", Transform(0.8f, 0.5f, rotationDegrees = 30f)),
            caption("canvas-off", "OFF THE EDGE", Transform(0f, 0.62f)),
            caption("canvas-middle", "MIDDLE", Transform(0.5f, 0.5f)),
        )
        return project(CANVAS_CHECK, clips, extraTracks = captions.mapIndexed { index, item -> Track("canvas-lane-${index + 1}", TrackKind.TEXT, listOf(item)) })
    }

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
        const val SOUND_SYNC = "sound-sync"
        const val TEXT_CHECK = "text-check"
        const val CANVAS_CHECK = "canvas-check"
        private val NAMES = listOf(
            EXPORT_CHECK, PREVIEW_CHECK, PREVIEW_ERROR, MISSING_MEDIA, EMPTY, TIMELINE_CHECK, TIMELINE_TRACKS, HOUR_LONG, SOUND_SYNC, TEXT_CHECK,
            CANVAS_CHECK,
        )
        private const val SOUND_SYNC_PHOTOS = 4
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
        private val photo4x3 = media("photo-4x3.png", MediaKind.IMAGE)
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
