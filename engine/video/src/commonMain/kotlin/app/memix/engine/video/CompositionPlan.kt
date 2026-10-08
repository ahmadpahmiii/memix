package app.memix.engine.video

import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.TrackKind

/**
 * A project laid out the way both platform engines build it: one main video sequence plus audio-only
 * lanes, each running back to back from time 0, all in microseconds. Plain data with no Media3 or
 * AVFoundation types, so the Project-to-engine mapping is shared by Android and iOS and can be read
 * and checked on its own. Built by [CompositionPlanner].
 */
internal data class CompositionPlan(
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    /** Where the last main video clip ends. The export is this long; audio lanes never run past it. */
    val durationUs: Long,
    /** The main video track in timeline order; a [Gap] stands for empty time between clips. */
    val mainVideo: List<MainVideoSegment>,
    /** Unmuted meme sound and audio tracks, plus extra lanes where clips on one track overlap. */
    val audioLanes: List<AudioLane>,
    /** One log line per thing in the project this engine doesn't render yet, naming the ticket that adds it. */
    val notRendered: List<String>,
) {
    /** True when the export needs an audio track: a main clip plays its own sound, or an audio lane exists. */
    val hasAudio: Boolean
        get() = audioLanes.isNotEmpty() || mainVideo.any { it is VisualClip && it.playsOwnAudio }

    /** Every item the plan plays from a media file, with that file. */
    val mediaItems: List<Pair<String, MediaRef>>
        get() {
            val mainMedia = mainVideo.filterIsInstance<VisualClip>().map { it.itemId to it.source }
            val laneMedia = audioLanes.flatMap { it.segments }.filterIsInstance<SoundClip>().map { it.itemId to it.source }
            return mainMedia + laneMedia
        }

    /**
     * This plan with the items [itemIds] replaced by gaps of the same length: black and silent on the main video
     * sequence, silent in their lane. Lanes left with nothing but silence are dropped. The preview uses it to keep
     * playing when a clip's media file is missing; export refuses such a project instead.
     */
    fun withGapsFor(itemIds: Set<String>): CompositionPlan {
        if (itemIds.isEmpty()) return this
        fun MainVideoSegment.gapIfListed() = if (this is VisualClip && itemId in itemIds) Gap(durationUs) else this
        fun AudioSegment.gapIfListed() = if (this is SoundClip && itemId in itemIds) Gap(durationUs) else this
        return copy(
            mainVideo = mainVideo.map { it.gapIfListed() },
            audioLanes = audioLanes
                .map { lane -> lane.copy(segments = lane.segments.map { it.gapIfListed() }) }
                .filter { lane -> lane.segments.any { it is SoundClip } },
        )
    }
}

/** One piece of the main video sequence. */
internal sealed interface MainVideoSegment {
    val durationUs: Long
}

/** One piece of an audio lane. */
internal sealed interface AudioSegment {
    val durationUs: Long
}

/** Empty time: black frames on the main video sequence, silence in an audio lane. */
internal data class Gap(override val durationUs: Long) : MainVideoSegment, AudioSegment

/** A video or photo on the main track, shown at the canvas size. */
internal data class VisualClip(
    val itemId: String,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    val fit: FrameFit,
    /** False for photos, videos without a sound track, detached audio, a muted main track and volume 0. */
    val playsOwnAudio: Boolean,
    /** 1 is the original loudness; only used when [playsOwnAudio]. */
    val volume: Float,
) : MainVideoSegment {
    override val durationUs: Long get() = trimOutUs - trimInUs

    /** A photo: shown for [durationUs] instead of being trimmed. */
    val isStill: Boolean get() = source.kind == MediaKind.IMAGE
}

/** A sound on an audio lane: a meme sound, imported audio or a clip's detached audio. */
internal data class SoundClip(
    val itemId: String,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    /** 1 is the original loudness; never 0, because silent clips are left out of the plan. */
    val volume: Float,
) : AudioSegment {
    override val durationUs: Long get() = trimOutUs - trimInUs
}

/** Sounds that play one after another, from one meme sound or audio track. */
internal data class AudioLane(
    val trackId: String,
    val kind: TrackKind,
    val segments: List<AudioSegment>,
)
