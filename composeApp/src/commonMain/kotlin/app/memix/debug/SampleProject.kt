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
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform
import app.memix.core.model.project.VideoTimeline

/**
 * A fixed video project with every track kind and every timeline item type, using non-default values
 * and non-Latin text, so a save-and-load round trip has something to get wrong. Debug hand checks only.
 */
internal object SampleProject {
    const val ID = "sample-every-track"

    // 7 Oct 2026, 09:30:00 UTC; the update adds a sub-millisecond part so microseconds are checked too.
    private const val CREATED_AT_EPOCH_US = 1_791_365_400_000_000L
    private const val UPDATED_AT_EPOCH_US = CREATED_AT_EPOCH_US + 90_000_123L

    private val galleryVideo = MediaRef(
        origin = MediaOrigin.GalleryUri("content://media/picker/0/com.android.providers.media.photopicker/media/1000000018"),
        kind = MediaKind.VIDEO,
        cachedCopyPath = "projects/$ID/media/1000000018.mp4",
    )

    fun everyTrackType(): Project = Project(
        id = ID,
        type = ProjectType.VIDEO,
        name = "Sample: every track",
        canvas = Canvas(CanvasRatio.RATIO_9_16, widthPx = 1080, heightPx = 1920, background = CanvasBackground.Solid(ArgbColor(0xFF202020))),
        video = VideoTimeline(
            listOf(mainVideoTrack(), overlayTrack(), textTrack(), stickerTrack(), memeSoundTrack(), audioTrack(), effectTrack()),
        ),
        createdAtEpochUs = CREATED_AT_EPOCH_US,
        updatedAtEpochUs = UPDATED_AT_EPOCH_US,
    )

    private fun mainVideoTrack() = Track(
        id = "track-main",
        kind = TrackKind.MAIN_VIDEO,
        items = listOf(
            MediaClip(
                id = "clip-video",
                startUs = 0,
                source = galleryVideo,
                trimInUs = 1_250_000,
                trimOutUs = 6_250_000,
                volume = 0.8f,
                audioDetached = true,
                fit = FrameFit.FILL,
            ),
            MediaClip(
                id = "clip-photo",
                startUs = 5_000_000,
                source = MediaRef(MediaOrigin.GalleryUri("content://media/picker/0/com.android.providers.media.photopicker/media/1000000021"), MediaKind.IMAGE),
                trimInUs = 0,
                trimOutUs = 3_000_000,
            ),
        ),
    )

    private fun overlayTrack() = Track(
        id = "track-overlay",
        kind = TrackKind.OVERLAY,
        items = listOf(
            MediaClip(
                id = "clip-overlay",
                startUs = 2_000_000,
                source = MediaRef(MediaOrigin.PhotoAsset("4F2B2C1E-8D7A-4E0B-9C61-0D2A5B7E9F10/L0/001"), MediaKind.VIDEO),
                trimInUs = 500_000,
                trimOutUs = 3_000_000,
                volume = 0f,
            ),
        ),
    )

    private fun textTrack() = Track(
        id = "track-text",
        kind = TrackKind.TEXT,
        items = listOf(
            TextItem(
                id = "text-caption",
                startUs = 500_000,
                durationUs = 4_000_000,
                text = "Me explaining memes to my mom\nजब मम्मी पूछे «¿qué?»",
                style = CaptionStyle(fontId = "anton", color = ArgbColor(0xFFFFFFFF), outlineColor = ArgbColor(0xFF000000)),
                transform = Transform(centerX = 0.5f, centerY = 0.12f, scale = 1.4f, rotationDegrees = -6.5f),
            ),
        ),
    )

    private fun stickerTrack() = Track(
        id = "track-sticker",
        kind = TrackKind.STICKER,
        items = listOf(
            StickerItem(
                id = "sticker-skull",
                startUs = 3_000_000,
                durationUs = 2_000_000,
                source = MediaRef(MediaOrigin.CatalogItem("sticker-skull"), MediaKind.IMAGE, cachedCopyPath = "catalog/stickers/sticker-skull.webp"),
                transform = Transform(centerX = 0.8f, centerY = 0.7f, scale = 0.6f, rotationDegrees = 15f),
            ),
        ),
    )

    private fun memeSoundTrack() = Track(
        id = "track-meme-sound",
        kind = TrackKind.MEME_SOUND,
        locked = true,
        items = listOf(
            AudioClip(
                id = "sound-vine-boom",
                startUs = 4_000_000,
                source = MediaRef(MediaOrigin.CatalogItem("sound-vine-boom"), MediaKind.AUDIO, cachedCopyPath = "catalog/sounds/sound-vine-boom.m4a"),
                trimInUs = 0,
                trimOutUs = 1_200_000,
                volume = 1.5f,
            ),
        ),
    )

    // The main clip's audio, detached onto its own track.
    private fun audioTrack() = Track(
        id = "track-audio",
        kind = TrackKind.AUDIO,
        muted = true,
        items = listOf(
            AudioClip(id = "audio-detached", startUs = 0, source = galleryVideo, trimInUs = 1_250_000, trimOutUs = 6_250_000, volume = 0.8f),
        ),
    )

    private fun effectTrack() = Track(
        id = "track-effect",
        kind = TrackKind.EFFECT,
        items = listOf(
            EffectItem(
                id = "effect-zoom-punch",
                startUs = 4_000_000,
                durationUs = 400_000,
                effect = EffectSpec("effect.zoom_punch", params = mapOf("intensity" to 0.75f, "scale" to 1.3f)),
            ),
        ),
    )
}
