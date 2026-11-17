package app.memix.engine.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize

/**
 * The overlay half of a Media3 composition (P4-01; TECHNICAL_DESIGN → Overlays). Media3 composites several video sequences with
 * a compositor whose primary input (the first one registered) paces the output and is drawn last, so on top. The main video must be
 * the bottom layer, so the sequences go in this order and [OverlayCompositorSettings] relies on it:
 *
 * 1. the **stage**: a gap as long as the video, drawn invisible. It only gives the output its steady 30 fps pace and its time;
 * 2. the **overlay layers**, top layer first, each as long as the video, with gaps between its clips (drawn invisible);
 * 3. the **main video** sequence, the bottom.
 *
 * A layer's clips are scaled to the size they're drawn at, drawn by [OverlayPlacement], and play their own sound at their volume (P4-01b). Only video overlays hold a
 * decoder, and only while they show: the plan's decoder budget ([OverlayAdmission]) already left out what doesn't fit.
 */
@OptIn(UnstableApi::class)
internal class OverlaySequences(
    private val plan: CompositionPlan,
    private val uriByMedia: Map<MediaRef, Uri>,
    /** The size every frame is drawn at, the canvas's shape. */
    private val frame: PixelSize,
    private val frameRate: Int,
    /** The preview's live volume per overlay video that plays sound (heard while the Volume slider moves); empty for exports. */
    private val liveGains: Map<String, LiveGainProcessor> = emptyMap(),
) {
    private val layersTopFirst: List<OverlayLayer> = plan.overlays.asReversed()

    /** The video sequences in compositing order: the stage, the layers top first, then [main]. */
    fun videoSequences(main: EditedMediaItemSequence): List<EditedMediaItemSequence> =
        listOf(stage()) + layersTopFirst.map(::layerSequence) + main

    fun compositorSettings(): OverlayCompositorSettings = OverlayCompositorSettings(layersTopFirst.map(::timelineOf), frame)

    private fun stage(): EditedMediaItemSequence =
        EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addGap(plan.durationUs).build()

    // A layer with a clip that plays sound has an audio track too; Media3 fills photos, gaps and silenced clips with silence, as on the main sequence.
    private fun layerSequence(layer: OverlayLayer): EditedMediaItemSequence {
        val trackTypes = if (layer.hasAudio) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
        val sequence = EditedMediaItemSequence.Builder(trackTypes)
        for (segment in layer.segments) {
            when (segment) {
                is Gap -> sequence.addGap(segment.durationUs)
                is OverlayClip -> sequence.addItem(clipItem(segment))
            }
        }
        return sequence.build()
    }

    private fun clipItem(clip: OverlayClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder().setUri(uriByMedia.getValue(clip.source))
        if (clip.isStill) mediaItem.setImageDurationMs(roundedMs(clip.durationUs)) else mediaItem.setClippingConfiguration(clipping(clip.trimInUs, clip.trimOutUs))
        // Scaled to the size it is drawn at before it is composited, so a 4K video costs a frame-sized pass, not more.
        val size = OverlayPlacement.of(clip, frame).textureSize
        val scaled = Presentation.createForWidthAndHeight(size.widthPx, size.heightPx, Presentation.LAYOUT_STRETCH_TO_FIT)
        val audioProcessors = if (clip.playsOwnAudio) AudioChain.volume(clip.volume, liveGains[clip.itemId]) else emptyList()
        val item = EditedMediaItem.Builder(mediaItem.build())
            .setRemoveAudio(!clip.playsOwnAudio)
            .setFrameRate(frameRate)
            .setEffects(Effects(audioProcessors, listOf(scaled)))
        if (!clip.isStill) item.setSourceDuration(clip.source, clip.trimOutUs)
        return item.build()
    }

    private fun timelineOf(layer: OverlayLayer): OverlayCompositorSettings.LayerTimeline {
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Long>()
        val settings = mutableListOf<OverlaySettings>()
        val itemIds = mutableListOf<String>()
        val placements = mutableListOf<OverlayPlacement>()
        var cursorUs = 0L
        for (segment in layer.segments) {
            if (segment is OverlayClip) {
                starts += cursorUs
                ends += cursorUs + segment.durationUs
                val placement = OverlayPlacement.of(segment, frame)
                settings += OverlayCompositorSettings.settingsOf(placement)
                placements += placement
                itemIds += segment.itemId
            }
            cursorUs += segment.durationUs
        }
        return OverlayCompositorSettings.LayerTimeline(starts.toLongArray(), ends.toLongArray(), settings, itemIds, placements)
    }

    companion object {
        /** The compositor input of the stage sequence: it must register first to be the primary (unverified on a device). */
        const val STAGE_INPUT = 0

        /** The compositor input of the top overlay layer; each layer below has the next one, and the main video the last. */
        const val FIRST_LAYER_INPUT = 1
    }
}
