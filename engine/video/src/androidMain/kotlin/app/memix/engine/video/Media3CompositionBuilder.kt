package app.memix.engine.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaRef

/**
 * Builds the Media3 [Composition] for a [CompositionPlan]: the main video sequence first, then one
 * audio-only sequence per audio lane. Media3 mixes sequences that overlap in time and ends with the
 * longest one, which the plan guarantees is the main video sequence.
 */
@OptIn(UnstableApi::class)
internal class Media3CompositionBuilder(
    private val plan: CompositionPlan,
    private val uriByMedia: Map<MediaRef, Uri>,
    private val frameRate: Int,
) {
    fun build(): Composition {
        val sequences = listOf(mainVideoSequence()) + plan.audioLanes.map(::audioSequence)
        return Composition.Builder(sequences)
            // Exports are SDR: every chat app shows SDR right, and Media3 refuses SDR clips or photos
            // after an HDR video in one sequence. OpenGL tone mapping works from API 29, our minSdk.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
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
        val audioProcessors = if (clip.playsOwnAudio) volumeProcessors(clip.volume) else emptyList()
        return EditedMediaItem.Builder(mediaItem.build())
            .setRemoveAudio(!clip.playsOwnAudio)
            // The output rate for photos; a ceiling that drops extra frames from faster videos.
            .setFrameRate(frameRate)
            .setEffects(Effects(audioProcessors, listOf(fitToCanvas(clip.fit))))
            .build()
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
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(volumeProcessors(clip.volume), emptyList()))
            .build()
    }

    private fun clipping(trimInUs: Long, trimOutUs: Long): MediaItem.ClippingConfiguration =
        MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(trimInUs)
            .setEndPositionUs(trimOutUs)
            .build()

    private fun fitToCanvas(fit: FrameFit): Presentation = when (fit) {
        FrameFit.FIT ->
            Presentation.createForWidthAndHeight(plan.canvasWidthPx, plan.canvasHeightPx, Presentation.LAYOUT_SCALE_TO_FIT)
        FrameFit.FILL ->
            Presentation.createForWidthAndHeight(plan.canvasWidthPx, plan.canvasHeightPx, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
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
