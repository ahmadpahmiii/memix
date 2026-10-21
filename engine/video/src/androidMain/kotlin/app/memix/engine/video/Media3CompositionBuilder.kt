package app.memix.engine.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize

/**
 * Builds the Media3 [Composition] for a [CompositionPlan]: the main video sequence first, then one
 * audio-only sequence per audio lane. Media3 mixes sequences that overlap in time and ends with the
 * longest one, which the plan guarantees is the main video sequence. The same composition feeds the
 * export (Transformer) and the preview (CompositionPlayer), so both render alike.
 *
 * Every main clip is drawn at one frame size, the canvas's shape, by the canvas rules ([CanvasFraming]); captions go over
 * the composited frame.
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
    /** The plan's captions over the composited video; null when the plan has none. */
    private val captionOverlay: CaptionOverlay? = null,
    /**
     * The shorter side every frame is drawn at, when it's smaller than the canvas's: an export's lower resolution (the
     * debug export hook's 720p until P1-12), or the preview's while a blur background plays. Null draws at canvas size.
     */
    private val frameShortSidePx: Int? = null,
) {
    // The size every main clip is drawn at, the canvas's shape: captions and output come out at this size too.
    private val frame: PixelSize = frameSizeOf(PixelSize(plan.canvasWidthPx, plan.canvasHeightPx), frameShortSidePx)

    fun build(): Composition {
        val sequences = listOf(mainVideoSequence()) + plan.audioLanes.map(::audioSequence)
        val composition = Composition.Builder(sequences)
            // Exports are SDR: every chat app shows SDR right, and Media3 refuses SDR clips or photos
            // after an HDR video in one sequence. OpenGL tone mapping works from API 29, our minSdk.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        val videoEffects = compositionVideoEffects()
        if (mixProcessors.isNotEmpty() || videoEffects.isNotEmpty()) composition.setEffects(Effects(mixProcessors, videoEffects))
        return composition.build()
    }

    // Captions are laid out at the canvas size and drawn scaled to the frame, so a lower resolution never re-lays out a
    // caption (spec P1-10 → Geometry: layout resolution).
    private fun compositionVideoEffects(): List<Effect> = listOfNotNull(captionOverlay?.let { OverlayEffect(listOf(it)) })

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
        val audioProcessors = if (clip.playsOwnAudio) itemVolume(clip.itemId, clip.volume) else emptyList()
        val item = EditedMediaItem.Builder(mediaItem.build())
            .setRemoveAudio(!clip.playsOwnAudio)
            // The output rate for photos; a ceiling that drops extra frames from faster videos.
            .setFrameRate(frameRate)
            .setEffects(Effects(audioProcessors, CanvasFraming(clip, frame, plan.background).effects()))
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
        // Detached audio points at the original video file; its picture isn't decoded again here.
        val processors = itemVolume(clip.itemId, clip.volume)
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .setSourceDuration(clip.source, clip.trimOutUs)
            .build()
    }

    // Media3 checks that the trim ends inside the source. A length measured at import can be a few µs shorter
    // than a trim set from another measurement, so the source counts as at least as long as the trim.
    private fun EditedMediaItem.Builder.setSourceDuration(source: MediaRef, trimOutUs: Long): EditedMediaItem.Builder {
        val durationUs = source.durationUs ?: return this
        return setDurationUs(maxOf(durationUs, trimOutUs))
    }

    private fun clipping(trimInUs: Long, trimOutUs: Long): MediaItem.ClippingConfiguration =
        MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(trimInUs)
            .setEndPositionUs(trimOutUs)
            .build()

    // The committed volume, then the preview's live gain if it has one. A 0% item's silence comes from its live gain
    // alone, since a fixed 0 step before it would leave nothing to raise.
    private fun itemVolume(itemId: String, volume: Float): List<AudioProcessor> {
        val liveGain = liveGains[itemId] ?: return volumeProcessors(volume)
        return if (volume == 0f) listOf(liveGain) else volumeProcessors(volume) + liveGain
    }

    // Scales every channel by the volume. Media3 clamps samples past full scale, so volumes above 1
    // get louder without wrapping around.
    private fun volumeProcessors(volume: Float): List<AudioProcessor> {
        if (volume == 1f) return emptyList()
        val processor = ChannelMixingAudioProcessor()
        for (channelCount in 1..MAX_CHANNEL_COUNT) {
            processor.putChannelMixingMatrix(ChannelMixingMatrix(channelCount, channelCount, scaledIdentity(channelCount, volume)))
        }
        return listOf(processor)
    }

    // Row-major: the diagonal of an n x n matrix sits at every (n + 1)th index.
    private fun scaledIdentity(channelCount: Int, scale: Float) =
        FloatArray(channelCount * channelCount) { index -> if (index % (channelCount + 1) == 0) scale else 0f }

    // Media3 takes photo durations in whole milliseconds; never round a photo down to nothing.
    private fun roundedMs(durationUs: Long): Long = ((durationUs + MICROS_PER_MILLI / 2) / MICROS_PER_MILLI).coerceAtLeast(1)

    private companion object {
        /** 7.1 surround; phone recordings are mono or stereo. */
        const val MAX_CHANNEL_COUNT = 8
        const val MICROS_PER_MILLI = 1_000L
    }
}
