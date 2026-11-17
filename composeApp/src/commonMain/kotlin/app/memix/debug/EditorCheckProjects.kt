package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ChromaKeyCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.Easing
import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.EffectSpec
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform
import app.memix.core.model.project.VideoTimeline
import kotlin.uuid.Uuid
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Saves one of the editor's hand-check projects as a draft and returns its id, so the app can open it in the editor
 * (`adb shell am start ... --es memix.openEditor <name>`, debug and benchmark builds; steps in
 * docs/qa/phase-1/engineer-hand-checks.md → P1-04). Null for an unknown name or a failed save, both logged under
 * [EditorCheckProjects.TAG].
 */
internal suspend fun openDebugEditorProject(name: String): DebugEditor? = EditorCheckProjects().save(name)

/** The editor to open for a hand-check project: its kind, and whether it is a blank canvas made in memory (`photo-empty`). */
internal class DebugEditor(val projectId: String, val type: ProjectType, val blank: Boolean = false)

/**
 * The projects, all built from the test files pushed into `files/debug-media/` for P1-03 plus `clip-1080p.mp4`.
 * Their media carry no measured lengths, like drafts from before schema 2, so opening them also checks that the
 * editor measures the files itself.
 */
internal class EditorCheckProjects : KoinComponent {
    private val saveProject: SaveProjectUseCase by inject()
    private val logger: Logger by inject()

    suspend fun save(name: String): DebugEditor? {
        if (name == PhotoCheckProjects.EMPTY) return DebugEditor(Uuid.random().toString(), ProjectType.PHOTO, blank = true)
        // The photo scenes' pictures are generated on first use.
        if (name in PhotoCheckProjects.NAMES || name == CANVAS_IMAGE_CHECK || name == GREENSCREEN_MAIN) writeDebugPhotos()
        // The green screen scenes' pictures are drawn on first use (ChromaCheckFixture).
        if (name == GREENSCREEN_CHECK || name == GREENSCREEN_MAIN) writeChromaCheckPictures()
        val project = build(name)
        if (project == null) {
            logger.error(TAG, "Unknown memix.openEditor '$name'. Use ${NAMES.joinToString()}.")
            return null
        }
        return when (val saved = saveProject(project)) {
            is Outcome.Success -> {
                logger.debug(TAG, "openEditor: saved ${project.id}, opening it")
                DebugEditor(project.id, project.type)
            }
            is Outcome.Failure -> {
                logger.error(TAG, "openEditor: saving $name failed with ${saved.error}")
                null
            }
        }
    }

    private fun build(name: String): Project? = PhotoCheckProjects.build(name) ?: when (name) {
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
        TEXT_SHADOW_CHECK -> textShadowCheck()
        CANVAS_CHECK -> canvasCheck()
        CANVAS_IMAGE_CHECK -> canvasImageCheck()
        OVERLAY_CHECK -> overlayCheck()
        OVERLAY_PERF -> overlayPerf()
        KEYFRAME_CHECK -> keyframeCheck()
        GREENSCREEN_CHECK -> greenScreenCheck()
        GREENSCREEN_MAIN -> greenScreenMain()
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
     * The P2-11 Done when (spec P2-11 → QA compares → Measured): `text-check`'s three clips with the seven captions of
     * `photo-text-shadow-box` on their own lanes for all 9 s, in shadow, box and both. Pause at 1.0 s, compare with the
     * exported frame (and the photo editor's render of the same captions).
     */
    private fun textShadowCheck(): Project {
        val clips = listOf(
            MediaClip("sb-clip-a", 0, clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("sb-clip-b", 3_000_000, clipB, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("sb-photo", 6_000_000, photo, trimInUs = 0, trimOutUs = 3_000_000),
        )
        val lanes = ShadowBoxCheckCaptions.all.mapIndexed { index, caption ->
            Track("sb-lane-${index + 1}", TrackKind.TEXT, listOf(TextItem(caption.id, 0, 9_000_000, caption.text, caption.style, caption.transform)))
        }
        return project(TEXT_SHADOW_CHECK, clips, extraTracks = lanes)
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

    /**
     * The P2-12 check: `canvas-check`'s three clips (16:9 video, 9:16 video, 4:3 photo, all Fit) on a 9:16 canvas over the
     * patterned background picture, no captions, so the bars show the picture and a shift of it would show: compare the
     * preview with the export (spec P2-12 → QA compares 3). Remove the picture in the Canvas panel for the black baseline.
     */
    private fun canvasImageCheck(): Project {
        val clips = listOf(
            MediaClip("canvas-wide", 0, clipA, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("canvas-tall", 3_000_000, clipB, trimInUs = 0, trimOutUs = 3_000_000),
            MediaClip("canvas-photo", 6_000_000, photo4x3, trimInUs = 0, trimOutUs = 3_000_000),
        )
        val withPicture = project(CANVAS_IMAGE_CHECK, clips)
        return withPicture.copy(canvas = withPicture.canvas.copy(background = CanvasBackground.Image(PhotoCheckProjects.backgroundMedia(missing = false))))
    }

    /**
     * The P4-01a overlay check: 10 s of the 1080p clip with five overlays on five layers (bottom to top: a wide video, a tall video
     * turned 12°, a square photo turned −10° at 60%, a 4:3 photo, and the 1080p clip small in the corner), each at its own place,
     * size, opacity and time. The media carry their measured sizes and lengths, so the export hook sees them without the editor
     * having opened the draft. `docs/qa/references/P4-01-overlays/` is this layout drawn from the planner's own numbers.
     */
    /**
     * The P4-02 motion check: the 10 s 1080p clip (silent), a grid picture overlay that moves, scales, turns and fades from 1 s to 4 s
     * (smooth), a caption that pops from 50% to 100% in 1 s, and a 1 kHz tone whose volume ramps 0 to 100% over 2 s, holds, then dips to
     * 25% (linear). Steps and the preview-versus-export comparison: docs/qa/phase-4/engineer-hand-checks.md → P4-02a.
     */
    private fun keyframeCheck(): Project {
        val overlay = MediaClip(
            "keyframe-overlay", startUs = 1_000_000, source = gridMeasured, trimInUs = 0, trimOutUs = 6_000_000, volume = 0f,
            keyframes = listOf(
                Keyframe(0, ItemLook(0.2f, 0.2f, 0.4f, 0f, 1f)),
                Keyframe(3_000_000, ItemLook(0.8f, 0.8f, 0.8f, 180f, 0.3f)),
            ),
        )
        val caption = TextItem(
            "keyframe-caption", startUs = 5_000_000, durationUs = 4_000_000, text = "POP", style = CAPTION, transform = Transform(0.5f, 0.2f),
            keyframes = listOf(Keyframe(0, ItemLook(0.5f, 0.2f, 0.5f)), Keyframe(1_000_000, ItemLook(0.5f, 0.2f, 1f))),
        )
        val toneClip = AudioClip(
            "keyframe-tone", startUs = 0, source = toneMeasured, trimInUs = 0, trimOutUs = 10_000_000,
            keyframes = listOf(
                Keyframe(0, ItemLook(volume = 0f), Easing.LINEAR),
                Keyframe(2_000_000, ItemLook(volume = 1f), Easing.LINEAR),
                Keyframe(6_000_000, ItemLook(volume = 1f), Easing.LINEAR),
                Keyframe(8_000_000, ItemLook(volume = 0.25f), Easing.LINEAR),
            ),
        )
        return project(
            KEYFRAME_CHECK,
            // Silent, so only the tone is heard.
            listOf(MediaClip("keyframe-main", 0, clip1080pMeasured, trimInUs = 0, trimOutUs = 10_000_000, volume = 0f)),
            listOf(
                Track("keyframe-overlay-layer", TrackKind.OVERLAY, listOf(overlay)),
                Track("keyframe-text", TrackKind.TEXT, listOf(caption)),
                Track("keyframe-sounds", TrackKind.AUDIO, listOf(toneClip)),
            ),
        )
    }

    /**
     * The P4-04 green screen check: the 10 s 1080p clip with three `chroma-check` pictures over it (spec docs/ux/specs/P4-04-green-screen.md →
     * Requests 7): the green one and the off-green one (`#6BB536`, for the eyedropper) not keyed yet, the blue one keyed with the Blue
     * preset. Steps: docs/qa/phase-4/engineer-hand-checks.md → P4-04. To check a video instead of a picture, make
     * `chroma-check.mp4` from the picture with the ffmpeg command there and swap the media in.
     */
    private fun greenScreenCheck(): Project {
        fun layer(name: String, source: MediaRef, centerY: Float, key: ChromaKey?) = Track(
            "gs-$name-layer",
            TrackKind.OVERLAY,
            listOf(MediaClip("gs-$name", 1_000_000, source, trimInUs = 0, trimOutUs = 8_000_000, volume = 0f, transform = Transform(0.5f, centerY, 0.9f, 0f), chromaKey = key)),
        )
        val layers = listOf(
            layer("green", chromaGreen, 0.2f, key = null),
            layer("blue", chromaBlue, 0.5f, key = ChromaKeyCatalog.keyOf(ChromaKeyCatalog.BLUE_ARGB, ChromaKeyCatalog.DEFAULT_STRENGTH, ChromaKeyCatalog.DEFAULT_SOFTNESS)),
            layer("off-green", chromaOffGreen, 0.8f, key = null),
        )
        return project(GREENSCREEN_CHECK, listOf(MediaClip("gs-main", 0, clip1080pMeasured, trimInUs = 0, trimOutUs = 10_000_000, volume = 0f)), layers)
    }

    /** The P4-04 main-clip check: the green picture as the main clip (3 s) over the canvas Image background of P2-12. */
    private fun greenScreenMain(): Project {
        val base = project(GREENSCREEN_MAIN, listOf(MediaClip("gs-main-picture", 0, chromaGreen, trimInUs = 0, trimOutUs = 3_000_000)))
        return base.copy(canvas = base.canvas.copy(background = CanvasBackground.Image(PhotoCheckProjects.backgroundMedia(missing = false))))
    }

    private fun overlayCheck(): Project {
        val layers = listOf(
            overlayLayer("a", clipAMeasured, startUs = 1_000_000, lengthUs = 6_000_000, Transform(0.30f, 0.25f, 0.5f, 0f), opacity = 1f),
            overlayLayer("b", clipBMeasured, startUs = 2_000_000, lengthUs = 6_000_000, Transform(0.70f, 0.30f, 0.45f, 12f), opacity = 0.8f),
            overlayLayer("photo", photoMeasured, startUs = 1_000_000, lengthUs = 8_000_000, Transform(0.50f, 0.55f, 0.6f, -10f), opacity = 0.6f),
            overlayLayer("photo43", photo4x3Measured, startUs = 3_000_000, lengthUs = 6_000_000, Transform(0.25f, 0.80f, 0.4f, 0f), opacity = 1f),
            overlayLayer("corner", clip1080pMeasured, startUs = 4_000_000, lengthUs = 6_000_000, Transform(0.75f, 0.80f, 0.35f, 0f), opacity = 0.9f, trimInUs = 10_000_000),
        )
        return project(OVERLAY_CHECK, listOf(MediaClip("overlay-main", 0, clip1080pMeasured, trimInUs = 0, trimOutUs = 10_000_000)), layers)
    }

    /**
     * The P4-01 performance check ("five overlays at 1080p preview at 30 fps or more"): 10 s of the 1080p clip with five more 1080p
     * videos over it at the same time, so six videos decode at once, each at a different place and size.
     */
    private fun overlayPerf(): Project {
        val places = listOf(
            Transform(0.28f, 0.22f, 0.45f, 0f), Transform(0.72f, 0.22f, 0.45f, 0f), Transform(0.28f, 0.50f, 0.45f, 0f),
            Transform(0.72f, 0.50f, 0.45f, 0f), Transform(0.50f, 0.78f, 0.45f, 0f),
        )
        val layers = places.mapIndexed { index, place ->
            overlayLayer("perf-$index", clip1080pMeasured, startUs = 0, lengthUs = 10_000_000, place, opacity = 1f, trimInUs = 2_000_000L * index)
        }
        return project(OVERLAY_PERF, listOf(MediaClip("overlay-main", 0, clip1080pMeasured, trimInUs = 0, trimOutUs = 10_000_000)), layers)
    }

    private fun overlayLayer(
        name: String,
        source: MediaRef,
        startUs: Long,
        lengthUs: Long,
        transform: Transform,
        opacity: Float,
        trimInUs: Long = 0,
    ) = Track(
        "overlay-$name-layer",
        TrackKind.OVERLAY,
        listOf(
            MediaClip(
                "overlay-$name",
                startUs,
                source,
                trimInUs = if (source.kind == MediaKind.IMAGE) 0 else trimInUs,
                trimOutUs = if (source.kind == MediaKind.IMAGE) lengthUs else trimInUs + lengthUs,
                volume = 0f,
                transform = transform,
                opacity = opacity,
            ),
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
        const val SOUND_SYNC = "sound-sync"
        const val TEXT_CHECK = "text-check"
        const val TEXT_SHADOW_CHECK = "text-shadow-check"
        const val CANVAS_CHECK = "canvas-check"
        const val CANVAS_IMAGE_CHECK = "canvas-image-check"
        const val OVERLAY_CHECK = "overlay-check"
        const val OVERLAY_PERF = "overlay-perf"
        const val KEYFRAME_CHECK = "keyframe-check"
        const val GREENSCREEN_CHECK = "greenscreen-check"
        const val GREENSCREEN_MAIN = "greenscreen-main"
        private val NAMES = listOf(
            EXPORT_CHECK, PREVIEW_CHECK, PREVIEW_ERROR, MISSING_MEDIA, EMPTY, TIMELINE_CHECK, TIMELINE_TRACKS, HOUR_LONG, SOUND_SYNC, TEXT_CHECK,
            TEXT_SHADOW_CHECK, CANVAS_CHECK, CANVAS_IMAGE_CHECK, OVERLAY_CHECK, OVERLAY_PERF, KEYFRAME_CHECK, GREENSCREEN_CHECK, GREENSCREEN_MAIN,
        ) + PhotoCheckProjects.NAMES
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

        // The same files with the facts the editor measures at import (P1-03's ffmpeg commands make them), for the overlay checks.
        private val clip1080pMeasured = clip1080p.copy(durationUs = 32_000_000, pixelSize = PixelSize(1080, 1920), hasAudio = true)
        private val clipAMeasured = clipA.copy(durationUs = 6_000_000, pixelSize = PixelSize(1280, 720), hasAudio = true)
        private val clipBMeasured = clipB.copy(durationUs = 6_000_000, pixelSize = PixelSize(720, 1280), hasAudio = true)
        private val photoMeasured = photo.copy(pixelSize = PixelSize(1080, 1080), hasAudio = false)
        // The 12 x 12 lettered grid picture and the 1 kHz tone of P4-02 (desktop-made, steps in the hand checks); measured like the others.
        private val chromaSize = PixelSize(ChromaCheckFixture.WIDTH_PX, ChromaCheckFixture.HEIGHT_PX)
        private val chromaGreen = media("chroma-check.png", MediaKind.IMAGE).copy(pixelSize = chromaSize, hasAudio = false)
        private val chromaBlue = media("chroma-check-blue.png", MediaKind.IMAGE).copy(pixelSize = chromaSize, hasAudio = false)
        private val chromaOffGreen = media("chroma-check-offgreen.png", MediaKind.IMAGE).copy(pixelSize = chromaSize, hasAudio = false)
        private val grid = media("grid-12.png", MediaKind.IMAGE)
        private val gridMeasured = grid.copy(pixelSize = PixelSize(1080, 1080), hasAudio = false)
        private val toneMeasured = MediaRef(
            MediaOrigin.CatalogItem("debug-tone-1k"), MediaKind.AUDIO, cachedCopyPath = "${ExportCheckProject.MEDIA_DIR}/tone-1k.m4a", durationUs = 10_000_000,
        )
        private val photo4x3Measured = photo4x3.copy(pixelSize = PixelSize(1440, 1080), hasAudio = false)
        private val sound = MediaRef(MediaOrigin.CatalogItem("debug-beep"), MediaKind.AUDIO, cachedCopyPath = "${ExportCheckProject.MEDIA_DIR}/sound.m4a")

        // Pushed by hand, so the origin is a stand-in; the engine plays the copy.
        private fun media(fileName: String, kind: MediaKind) = MediaRef(
            MediaOrigin.GalleryUri("content://app.memix.debug/$fileName"),
            kind,
            cachedCopyPath = "${ExportCheckProject.MEDIA_DIR}/$fileName",
        )
    }
}
