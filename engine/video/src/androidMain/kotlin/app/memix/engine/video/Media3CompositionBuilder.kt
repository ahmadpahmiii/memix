package app.memix.engine.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import java.io.File

/**
 * Builds the Media3 [Composition] for a [CompositionPlan]: the video sequences first, then one
 * audio-only sequence per audio lane. The video is the main sequence alone, or with overlays ([OverlaySequences]) the stage,
 * the overlay layers and the main sequence composited together. Media3 mixes sequences that overlap in time and ends with the
 * longest one, which the plan guarantees is as long as the main video sequence. The same composition feeds the
 * export (Transformer) and the preview (CompositionPlayer), so both render alike.
 *
 * Every main clip is drawn at one frame size, the canvas's shape, by the canvas rules ([CanvasFraming]); captions go over
 * the composited frame. Call [build] off the main thread: a background picture is decoded there.
 *
 * Each video and sound item carries its source file's length when the project knows it: CompositionPlayer
 * needs it up front, and Transformer would otherwise read it from the file.
 */
@OptIn(UnstableApi::class)
internal class Media3CompositionBuilder(
    private val plan: CompositionPlan,
    private val uriByMedia: Map<MediaRef, Uri>,
    private val frameRate: Int,
    /** Processors for the whole mix, after the sequences are mixed; only the preview's hand-check measure uses one. */
    private val mixProcessors: List<AudioProcessor> = emptyList(),
    /**
     * The preview's live volume per item that plays sound: sounds and main clips (see [LiveGainProcessor]); empty for
     * exports. An item at 0% with a live gain gets no fixed volume step: its live gain starts at 0 and can raise it.
     */
    private val liveGains: Map<String, LiveGainProcessor> = emptyMap(),
    /** The preview's live fades per sound (the fade sliders are heard while they move); empty for exports. */
    private val liveFades: Map<String, LiveFades> = emptyMap(),
    /** The preview's live Bass boost and Distortion per sound (heard while their sliders move); empty for exports. */
    private val liveBass: Map<String, LiveBass> = emptyMap(),
    /** The preview's live green screen per keyed clip (the Strength and Edge softness sliders move the matte); a clip not listed gets its own fixed key. */
    private val liveKeys: Map<String, LiveChromaKey> = emptyMap(),
    /** The preview's live mask per masked clip (a finger or a slider moves the mask); a clip not listed gets its own fixed mask. */
    private val liveMasks: Map<String, LiveMask> = emptyMap(),
    /** The plan's captions over the composited video; null when the plan has none. */
    private val captionOverlay: CaptionOverlay? = null,
    /** The "Memix" mark of a free export, drawn above the captions; null in previews and watermark-free exports (P1-13). */
    private val watermarkOverlay: WatermarkOverlay? = null,
    /**
     * The shorter side every frame is drawn at, when it's smaller than the canvas's: an export's lower resolution (the
     * debug export hook's 720p until P1-12), or the preview's while a blur background plays. Null draws at canvas size.
     */
    private val frameShortSidePx: Int? = null,
    /** The exact frame size of an export from the sheet (P1-12: the resolution, aligned to the encoder); wins over [frameShortSidePx]. */
    private val frameSizePx: PixelSize? = null,
    /** The file of the plan's background picture, when it has one and it is on the phone (null draws the bars black). */
    private val backgroundFile: File? = null,
    /** The preview's hide switch for an overlay under a finger (P4-01b); exports pass none and the preview takes the one the composition made. */
    private val onOverlayVisibility: (OverlayVisibility) -> Unit = {},
) {
    // The size every main clip is drawn at, the canvas's shape: captions and output come out at this size too.
    private val frame: PixelSize = frameSizePx ?: frameSizeOf(PixelSize(plan.canvasWidthPx, plan.canvasHeightPx), frameShortSidePx)

    // The background picture at the frame's size, decoded once for every clip (P2-12). Reads the file: build() runs off the main thread.
    private val pictureBars: BackgroundBars? = if (plan.background is PlanBackground.Image) BackgroundBars(backgroundFile, frame) else null

    private fun framingOf(clip: VisualClip): CanvasFraming {
        val key = clip.chromaKey?.let { liveKeys[clip.itemId] ?: LiveChromaKey(it) }
        val mask = clip.mask?.let { liveMasks[clip.itemId] ?: LiveMask(it) }
        return CanvasFraming(clip, frame, plan.background, pictureBars, key, mask)
    }

    fun build(): Composition {
        // With overlays, the video is several sequences composited in a fixed order (OverlaySequences); without, just the main one.
        val overlays = if (plan.hasOverlays) OverlaySequences(plan, uriByMedia, frame, frameRate, liveGains, liveKeys, liveMasks) else null
        val main = mainVideoSequence()
        val videoSequences = overlays?.videoSequences(main) ?: listOf(main)
        val composition = Composition.Builder(videoSequences + plan.audioLanes.map(::audioSequence))
            // Exports are SDR: every chat app shows SDR right, and Media3 refuses SDR clips or photos
            // after an HDR video in one sequence. OpenGL tone mapping works from API 29, our minSdk.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        overlays?.let {
            val settings = it.compositorSettings()
            onOverlayVisibility(settings.visibility)
            composition.setVideoCompositorSettings(settings)
        }
        val videoEffects = compositionVideoEffects()
        if (mixProcessors.isNotEmpty() || videoEffects.isNotEmpty()) composition.setEffects(Effects(mixProcessors, videoEffects))
        return composition.build()
    }

    // Captions are laid out at the canvas size and drawn scaled to the frame, so a lower resolution never re-lays out a
    // caption (spec P1-10 → Geometry: layout resolution). One effect draws its overlays in list order, so the watermark
    // goes last: above everything (spec P1-13 → The mark).
    private fun compositionVideoEffects(): List<Effect> {
        val overlays: List<TextureOverlay> = listOfNotNull(captionOverlay, watermarkOverlay)
        return if (overlays.isEmpty()) emptyList() else listOf(OverlayEffect(overlays))
    }

    private fun mainVideoSequence(): EditedMediaItemSequence {
        // With the audio track type, Media3 fills photos, gaps and silenced clips with silence, so the
        // audio track runs as long as the video and the audio lanes mix over it.
        val trackTypes = if (plan.hasAudio) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
        val sequence = EditedMediaItemSequence.Builder(trackTypes)
        for (segment in plan.mainVideo) {
            when (segment) {
                is Gap -> sequence.addGap(segment.durationUs)
                is VisualClip -> sequence.addItem(visualItem(segment))
            }
        }
        return sequence.build()
    }

    private fun visualItem(clip: VisualClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder().setUri(uriByMedia.getValue(clip.source))
        if (clip.isStill) {
            // Media3 treats an item as a photo only when it has an image duration (required since 1.8).
            mediaItem.setImageDurationMs(roundedMs(clip.durationUs))
        } else {
            mediaItem.setClippingConfiguration(clipping(clip.trimInUs, clip.trimOutUs))
        }
        val audioProcessors = if (clip.playsOwnAudio) AudioChain.volume(clip.volume, clip.keyframes, liveGains[clip.itemId]) else emptyList()
        val item = EditedMediaItem.Builder(mediaItem.build())
            .setRemoveAudio(!clip.playsOwnAudio)
            // The output rate for photos; a ceiling that drops extra frames from faster videos.
            .setFrameRate(frameRate)
            .setEffects(Effects(audioProcessors, framingOf(clip).effects()))
        // A photo's length comes from its image duration.
        if (!clip.isStill) item.setSourceDuration(clip.source, clip.trimOutUs)
        return item.build()
    }

    private fun audioSequence(lane: AudioLane): EditedMediaItemSequence {
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        for (segment in lane.segments) {
            when (segment) {
                is Gap -> sequence.addGap(segment.durationUs)
                is SoundClip -> sequence.addItem(soundItem(segment))
            }
        }
        return sequence.build()
    }

    private fun soundItem(clip: SoundClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(uriByMedia.getValue(clip.source))
            .setClippingConfiguration(clipping(clip.trimInUs, clip.trimOutUs))
            .build()
        // Detached audio points at the original video file; its picture isn't decoded again here. The effects run first, then the
        // volume (spec P3-09 → rule 1); the preview and the export both build this item, so they play the same chain.
        val processors = AudioChain.forSound(clip, liveGains[clip.itemId], liveFades[clip.itemId], liveBass[clip.itemId])
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .setSourceDuration(clip.source, clip.trimOutUs)
            .build()
    }
}

// Media3 checks that the trim ends inside the source. A length measured at import can be a few µs shorter
// than a trim set from another measurement, so the source counts as at least as long as the trim.
@OptIn(UnstableApi::class)
internal fun EditedMediaItem.Builder.setSourceDuration(source: MediaRef, trimOutUs: Long): EditedMediaItem.Builder {
    val durationUs = source.durationUs ?: return this
    return setDurationUs(maxOf(durationUs, trimOutUs))
}

@OptIn(UnstableApi::class)
internal fun clipping(trimInUs: Long, trimOutUs: Long): MediaItem.ClippingConfiguration =
    MediaItem.ClippingConfiguration.Builder()
        .setStartPositionUs(trimInUs)
        .setEndPositionUs(trimOutUs)
        .build()

// Media3 takes photo durations in whole milliseconds; never round a photo down to nothing.
internal fun roundedMs(durationUs: Long): Long = ((durationUs + MICROS_PER_MILLI / 2) / MICROS_PER_MILLI).coerceAtLeast(1)

private const val MICROS_PER_MILLI = 1_000L
