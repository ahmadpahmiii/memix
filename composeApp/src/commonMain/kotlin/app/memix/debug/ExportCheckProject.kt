package app.memix.debug

import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.EffectSpec
import app.memix.core.model.project.FrameFit
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

/**
 * The project [ExportCheck] exports, built from four test files pushed into `files/debug-media/`
 * (clip-a.mp4 and clip-b.mp4 of at least 5 s, photo.png, sound.m4a of at least 1 s). Every track
 * kind is present; the timings are chosen so ffmpeg can check each mapping rule in the output:
 *
 * | Time (s)  | Picture                       | Sound                                                  |
 * | 0 – 3     | clip A, source 2–5 s, fill    | clip A's own audio at volume 0.5                       |
 * | 3 – 5     | photo, fit                    | meme sound at 3.5–4.0 (source 0.5–1.0); muted beep at 4.2 must stay silent |
 * | 5 – 8     | clip B, source 1–4 s, fit     | clip B's audio once, from the audio track (detached)    |
 *
 * Text, sticker, overlay and effect items are there to show the engine's "not rendered yet" log lines.
 */
internal object ExportCheckProject {
    const val ID = "export-check"
    const val MEDIA_DIR = "debug-media"
    const val LENGTH_US = 8_000_000L

    private val clipA = media("clip-a.mp4", MediaKind.VIDEO)
    private val clipB = media("clip-b.mp4", MediaKind.VIDEO)
    private val photo = media("photo.png", MediaKind.IMAGE)
    private val sound = MediaRef(MediaOrigin.CatalogItem("debug-beep"), MediaKind.AUDIO, cachedCopyPath = "$MEDIA_DIR/sound.m4a")

    // 8 Oct 2026, 12:00:00 UTC; the timestamps only matter if the project is ever saved.
    private const val EDITED_AT_EPOCH_US = 1_791_460_800_000_000L

    fun build(): Project = Project(
        id = ID,
        type = ProjectType.VIDEO,
        name = "Export check: every track",
        canvas = Canvas(CanvasRatio.RATIO_9_16, widthPx = 1080, heightPx = 1920, background = CanvasBackground.Solid(ArgbColor(0xFF000000))),
        video = VideoTimeline(
            listOf(mainVideoTrack(), memeSoundTrack(), detachedAudioTrack(), mutedAudioTrack(), overlayTrack(), textTrack(), stickerTrack(), effectTrack()),
        ),
        createdAtEpochUs = EDITED_AT_EPOCH_US,
        updatedAtEpochUs = EDITED_AT_EPOCH_US,
    )

    private fun mainVideoTrack() = Track(
        id = "track-main",
        kind = TrackKind.MAIN_VIDEO,
        // Listed out of order on purpose: clips are placed by start time, never by list position.
        items = listOf(
            MediaClip(id = "clip-photo", startUs = 3_000_000, source = photo, trimInUs = 0, trimOutUs = 2_000_000, fit = FrameFit.FIT),
            MediaClip(id = "clip-a", startUs = 0, source = clipA, trimInUs = 2_000_000, trimOutUs = 5_000_000, volume = 0.5f, fit = FrameFit.FILL),
            MediaClip(
                id = "clip-b",
                startUs = 5_000_000,
                source = clipB,
                trimInUs = 1_000_000,
                trimOutUs = 4_000_000,
                audioDetached = true,
                fit = FrameFit.FIT,
            ),
        ),
    )

    private fun memeSoundTrack() = Track(
        id = "track-meme-sound",
        kind = TrackKind.MEME_SOUND,
        items = listOf(AudioClip(id = "sound-beep", startUs = 3_500_000, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000)),
    )

    // Clip B's audio after "Detach": its own track, same trims, so it plays exactly once.
    private fun detachedAudioTrack() = Track(
        id = "track-audio",
        kind = TrackKind.AUDIO,
        items = listOf(AudioClip(id = "audio-clip-b", startUs = 5_000_000, source = clipB, trimInUs = 1_000_000, trimOutUs = 4_000_000)),
    )

    private fun mutedAudioTrack() = Track(
        id = "track-audio-muted",
        kind = TrackKind.AUDIO,
        muted = true,
        items = listOf(AudioClip(id = "sound-muted", startUs = 4_200_000, source = sound, trimInUs = 500_000, trimOutUs = 1_000_000)),
    )

    private fun overlayTrack() = Track(
        id = "track-overlay",
        kind = TrackKind.OVERLAY,
        items = listOf(MediaClip(id = "overlay-a", startUs = 1_000_000, source = clipA, trimInUs = 0, trimOutUs = 1_000_000)),
    )

    private fun textTrack() = Track(
        id = "track-text",
        kind = TrackKind.TEXT,
        items = listOf(
            TextItem(
                id = "text-caption",
                startUs = 0,
                durationUs = 3_000_000,
                text = "Export check",
                style = CaptionStyle(fontId = "anton", color = ArgbColor(0xFFFFFFFF), outlineColor = ArgbColor(0xFF000000)),
            ),
        ),
    )

    private fun stickerTrack() = Track(
        id = "track-sticker",
        kind = TrackKind.STICKER,
        items = listOf(StickerItem(id = "sticker-photo", startUs = 5_000_000, durationUs = 1_000_000, source = photo)),
    )

    private fun effectTrack() = Track(
        id = "track-effect",
        kind = TrackKind.EFFECT,
        items = listOf(EffectItem(id = "effect-zoom", startUs = 3_500_000, durationUs = 400_000, effect = EffectSpec("effect.zoom_punch"))),
    )

    // Pushed by hand, so the origin is a stand-in; the engine plays the cached copy.
    private fun media(fileName: String, kind: MediaKind) =
        MediaRef(MediaOrigin.GalleryUri("content://app.memix.debug/$fileName"), kind, cachedCopyPath = "$MEDIA_DIR/$fileName")
}
