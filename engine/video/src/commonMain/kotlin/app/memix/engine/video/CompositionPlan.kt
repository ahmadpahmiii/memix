package app.memix.engine.video

import app.memix.core.domain.project.interpolatedLook
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ClipLook
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform

/**
 * A project laid out the way both platform engines build it: one main video sequence, overlay layers drawn over it and audio-only
 * lanes, each running back to back from time 0, all in microseconds. Plain data with no Media3 or
 * AVFoundation types, so the Project-to-engine mapping is shared by Android and iOS and can be read
 * and checked on its own. Built by [CompositionPlanner].
 */
internal data class CompositionPlan(
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    /** What shows around fitted clips (spec P1-11 → Rendering rules); gaps and missing clips stay black whatever it is. */
    val background: PlanBackground = PlanBackground.Color(BLACK_ARGB),
    /** Where the last main video clip ends. The export is this long; audio lanes never run past it. */
    val durationUs: Long,
    /** The main video track in timeline order; a [Gap] stands for empty time between clips. */
    val mainVideo: List<MainVideoSegment>,
    /**
     * The overlay layers (P4-01), bottom to top: a later layer draws over an earlier one, and every layer draws over the main
     * video. Each runs from 0 to [durationUs] (gaps pad it), so every layer is as long as the video.
     */
    val overlays: List<OverlayLayer> = emptyList(),
    /** Unmuted meme sound and audio tracks, plus extra lanes where clips on one track overlap. */
    val audioLanes: List<AudioLane>,
    /** The captions drawn over the video, in draw order (later ones on top), cut to [durationUs]. */
    val captions: List<CaptionClip> = emptyList(),
    /**
     * The effect items drawn over the whole composed frame, captions included, in lane order (P4-06). The composition carries the effects
     * pass only when this isn't empty; the items it holds are read live per frame, so an edit of one needs no new composition.
     */
    val effects: List<EffectSpan> = emptyList(),
    /** Overlay clips left out because the phone can't decode that many videos at once, or their size isn't known (see [OverlayAdmission]). */
    val skippedOverlayIds: List<String> = emptyList(),
    /** One log line per thing in the project this engine doesn't render yet, naming the ticket that adds it. */
    val notRendered: List<String>,
) {
    /** True when the effects pass is part of the composition (an empty list builds none, so a project with no effects costs nothing). */
    val hasEffects: Boolean get() = effects.isNotEmpty()

    /** True when overlays are drawn: the engine then composites several video sequences instead of playing the main one alone. */
    val hasOverlays: Boolean get() = overlays.isNotEmpty()

    /** True when the export needs an audio track: a main clip plays its own sound, or an audio lane exists. */
    val hasAudio: Boolean
        get() = audioLanes.isNotEmpty() || mainVideo.any { it is VisualClip && it.playsOwnAudio } || overlays.any { it.hasAudio }

    /** Every main clip and overlay the plan draws with a green screen, by item id. */
    val chromaKeys: Map<String, ChromaKey>
        get() = buildMap {
            mainVideo.filterIsInstance<VisualClip>().forEach { clip -> clip.chromaKey?.let { put(clip.itemId, it) } }
            overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().forEach { clip -> clip.chromaKey?.let { put(clip.itemId, it) } }
        }

    /** Every main clip and overlay the plan draws through a mask, by item id. */
    val masks: Map<String, ClipMask>
        get() = buildMap {
            mainVideo.filterIsInstance<VisualClip>().forEach { clip -> clip.mask?.let { put(clip.itemId, it) } }
            overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().forEach { clip -> clip.mask?.let { put(clip.itemId, it) } }
        }

    /** Every main clip and overlay the plan draws with a look (P4-07: filter or adjustments), by item id. */
    val looks: Map<String, ClipLook>
        get() = buildMap {
            mainVideo.filterIsInstance<VisualClip>().forEach { clip -> if (!clip.look.isNeutral) put(clip.itemId, clip.look) }
            overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().forEach { clip -> if (!clip.look.isNeutral) put(clip.itemId, clip.look) }
        }

    /** Every item the plan plays from a media file, with that file. */
    val mediaItems: List<Pair<String, MediaRef>>
        get() {
            val mainMedia = mainVideo.filterIsInstance<VisualClip>().map { it.itemId to it.source }
            val overlayMedia = overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().map { it.itemId to it.source }
            val laneMedia = audioLanes.flatMap { it.segments }.filterIsInstance<SoundClip>().map { it.itemId to it.source }
            return mainMedia + overlayMedia + laneMedia
        }

    /** Where each sound of the audio lanes starts on the timeline, µs, in lane order. */
    val soundStartsUs: List<Long>
        get() = audioLanes.flatMap { lane ->
            var cursorUs = 0L
            lane.segments.mapNotNull { segment ->
                val startUs = cursorUs
                cursorUs += segment.durationUs
                startUs.takeIf { segment is SoundClip }
            }
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
        fun OverlaySegment.gapIfListed() = if (this is OverlayClip && itemId in itemIds) Gap(durationUs) else this
        return copy(
            mainVideo = mainVideo.map { it.gapIfListed() },
            overlays = overlays
                .map { layer -> layer.copy(segments = layer.segments.map { it.gapIfListed() }) }
                .filter { layer -> layer.segments.any { it is OverlayClip } },
            audioLanes = audioLanes
                .map { lane -> lane.copy(segments = lane.segments.map { it.gapIfListed() }) }
                .filter { lane -> lane.segments.any { it is SoundClip } },
        )
    }
}

/** What fills the canvas around a fitted clip. */
internal sealed interface PlanBackground {
    /** One color, as 0xAARRGGBB. */
    data class Color(val argb: Long) : PlanBackground

    /** A copy of the clip itself, scaled to Fill and blurred (CanvasCatalog.BLUR_SIGMA_OF_SHORT_SIDE). */
    data object Blur : PlanBackground

    /**
     * The user's picture (P2-12), scaled to cover the whole frame, centered, the overflow cropped; it shows only in the
     * bars a Fit clip leaves. [source] is the app's copy; a missing file draws black, like a gap.
     */
    data class Image(val source: MediaRef) : PlanBackground
}

internal const val BLACK_ARGB = 0xFF000000

/** One piece of the main video sequence. */
internal sealed interface MainVideoSegment {
    val durationUs: Long
}

/** One piece of an audio lane. */
internal sealed interface AudioSegment {
    val durationUs: Long
}

/** One piece of an overlay layer. */
internal sealed interface OverlaySegment {
    val durationUs: Long
}

/** Empty time: black frames on the main video sequence, silence in an audio lane, nothing drawn in an overlay layer. */
internal data class Gap(override val durationUs: Long) : MainVideoSegment, AudioSegment, OverlaySegment

/** A video or photo on the main track, shown at the canvas size. */
internal data class VisualClip(
    val itemId: String,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    val fit: FrameFit,
    /** The picture's size as shown, rotation applied; null for media added before schema 2 (no background around it). */
    val pixelSize: PixelSize?,
    /**
     * False for photos, videos without a sound track, detached audio and a muted main track; also at volume 0, except
     * in a preview's plan, which keeps silent items so a raise is heard live.
     */
    val playsOwnAudio: Boolean,
    /** 1 is the original loudness; only used when [playsOwnAudio]. 1 when the clip has [keyframes]: they carry the level then. */
    val volume: Float,
    /**
     * The clip's motion over time (P4-02), counted from its start. Only the volume is drawn so far: a main clip's position, size and
     * turn arrive with P4-16 (the planner logs it).
     */
    val keyframes: List<Keyframe> = emptyList(),
    /** The green screen (P4-04): removed from the picture first, before it is scaled; the canvas background shows through it. Null is off. */
    val chromaKey: ChromaKey? = null,
    /** The shape mask (P4-03b): after the key and Fit or Fill, before the background goes behind the frame. Null is none. */
    val mask: ClipMask? = null,
    /** The filter and adjustments (P4-07): after the key, on the clip's own picture, before Fit or Fill. Neutral is none. */
    val look: ClipLook = ClipLook.NONE,
) : MainVideoSegment {
    override val durationUs: Long get() = trimOutUs - trimInUs

    /** A photo: shown for [durationUs] instead of being trimmed. */
    val isStill: Boolean get() = source.kind == MediaKind.IMAGE
}

/**
 * A video or photo floating over the main video (P4-01). Only the part from [trimInUs] to [trimOutUs] plays, with its own sound at [volume] when [playsOwnAudio] (P4-01b). Where it
 * sits comes from [transform] and [opacity] through [OverlayRender].
 */
internal data class OverlayClip(
    val itemId: String,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    /** The picture's size as shown, rotation applied; the scale-1 size of the overlay is this picture fitted inside the canvas. */
    val pixelSize: PixelSize,
    val transform: Transform,
    val opacity: Float,
    /** True for an overlay video with its own sound that plays: not a photo, a silent file, detached sound or a muted layer; at 0% only in a preview's plan. */
    val playsOwnAudio: Boolean = false,
    /** 1 is the original loudness; only used when [playsOwnAudio]. 1 when the clip has [keyframes]. */
    val volume: Float = 1f,
    /** The overlay's motion over time (P4-02), counted from where this clip starts showing; they take over [transform] and [opacity] (and the volume). */
    val keyframes: List<Keyframe> = emptyList(),
    /** The green screen (P4-04): removed from the picture before it is scaled to its drawn size, so the layers beneath show through. Null is off. */
    val chromaKey: ChromaKey? = null,
    /** The shape mask (P4-03b): after the key, in the clip's own box, before the compositor moves, scales and turns it. Null is none. */
    val mask: ClipMask? = null,
    /** The filter and adjustments (P4-07): after the key and the scaling to the drawn size, before the mask. Neutral is none. */
    val look: ClipLook = ClipLook.NONE,
) : OverlaySegment {
    override val durationUs: Long get() = trimOutUs - trimInUs

    /** How the overlay looks [relativeUs] after it starts showing: the keyframes' look, or [transform] and [opacity]. */
    fun lookAt(relativeUs: Long): ItemLook =
        interpolatedLook(keyframes, relativeUs) ?: ItemLook(transform.centerX, transform.centerY, transform.scale, transform.rotationDegrees, opacity)

    /** A photo: shown for [durationUs] instead of being trimmed. */
    val isStill: Boolean get() = source.kind == MediaKind.IMAGE
}

/** Overlay clips and gaps that follow each other from time 0 to the end of the video: one layer of the stack. */
internal data class OverlayLayer(
    val trackId: String,
    val segments: List<OverlaySegment>,
) {
    /** Whether one of its clips plays sound, so its sequence needs an audio track. */
    val hasAudio: Boolean get() = segments.any { it is OverlayClip && it.playsOwnAudio }
}

/** A sound on an audio lane: a meme sound, imported audio or a clip's detached audio. */
internal data class SoundClip(
    val itemId: String,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    /** 1 is the original loudness; 0 only in a preview's plan (export plans leave silent clips out). */
    val volume: Float,
    /** The Audio effects panel's settings (P3-09); preview and export build the same processors from them. */
    val effects: AudioEffects = AudioEffects(),
    /**
     * How long the whole clip lasts on the timeline, µs, before the end of the video cut it: Fade out counts back from
     * here, so a clip that runs past the end still fades at its own end. Equals [durationUs] when nothing was cut.
     */
    val clipLengthUs: Long = effects.timelineLengthOf(trimOutUs - trimInUs),
    /**
     * How long the echo and the voice ring on after the clip, µs: the effects' tail, shortened where the end of the video comes
     * first. The sound is built that long, so its lane is busy for [durationUs].
     */
    val tailUs: Long = 0,
    /** The volume over time (P4-02), counted from the clip's start; [volume] is 1 when there are any. */
    val keyframes: List<Keyframe> = emptyList(),
) : AudioSegment {
    // Timeline length: the part of the source that plays, at the effects' speed, plus the tail that rings after it.
    override val durationUs: Long get() = effects.timelineLengthOf(trimOutUs - trimInUs) + tailUs
}

/**
 * A caption over the video from [startUs] until [endUs] (composition time), drawn with the caption renderer by the
 * rules in spec P1-10 → Caption rendering.
 */
internal data class CaptionClip(
    val itemId: String,
    val startUs: Long,
    val endUs: Long,
    val text: String,
    val style: CaptionStyle,
    val transform: Transform,
    /** The caption's motion over time (P4-02), counted from [startUs]; it takes over [transform]. */
    val keyframes: List<Keyframe> = emptyList(),
) {
    /** Whether it shows at composition time [timeUs]. */
    fun isShownAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    /** Where, how big and how turned the caption is at composition time [timeUs]. */
    fun transformAt(timeUs: Long): Transform = interpolatedLook(keyframes, timeUs - startUs)?.transform ?: transform
}

/** Sounds that play one after another, from one meme sound or audio track. */
internal data class AudioLane(
    val trackId: String,
    val kind: TrackKind,
    val segments: List<AudioSegment>,
)
