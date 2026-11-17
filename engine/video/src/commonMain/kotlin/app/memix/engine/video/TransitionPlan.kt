package app.memix.engine.video

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.TransitionCatalog

/**
 * One transition at a cut as the engines draw it (P4-08), in composition time: the window is [startUs, startUs + durationUs] around the cut at its
 * middle. [side] tells which picture a layer shows at a frame time: the outgoing clip before the cut, the incoming one after it, for the layer
 * on top (the main video), and the other way round for the layer beneath (the partner sequence).
 */
internal data class TransitionWindow(val id: String, val startUs: Long, val durationUs: Long) {
    val cutUs: Long get() = startUs + durationUs / 2
    val endUs: Long get() = startUs + durationUs

    /** Progress 0 to 1 at [timeUs], clamped; the caller checks [contains] first. */
    fun progressAt(timeUs: Long): Float = ((timeUs - startUs).toDouble() / durationUs).toFloat().coerceIn(0f, 1f)

    operator fun contains(timeUs: Long): Boolean = timeUs >= startUs && timeUs <= endUs
}

/** One piece of the partner sequence (the video-only layer beneath the main video that holds the other picture of a transition). */
internal sealed interface PartnerSegment {
    val durationUs: Long
}

/** A stretch of a clip's own media played in the partner sequence: the incoming clip's footage before its trim, or the outgoing one's after it; a photo is its still. */
internal data class PartnerClip(val clip: VisualClip, val window: TransitionWindow) : PartnerSegment {
    override val durationUs: Long get() = clip.durationUs
}

/**
 * One frame of [clip]'s source at [atUs] held for [durationUs]: used where the clip has no spare media on that side of the cut. The engine
 * extracts the frame once into a still picture it plays like a photo (framed like the clip).
 */
internal data class PartnerHold(val clip: VisualClip, val atUs: Long, override val durationUs: Long, val window: TransitionWindow) : PartnerSegment

/** The partner sequence: pieces and gaps from time 0 to the end of the video. */
internal data class TransitionLayer(val segments: List<PartnerSegment>)

/** Plans the transitions of a project's main clips (spec docs/ux/specs/P4-08-transitions.md → What a transition is). */
internal object TransitionPlanner {
    /** The last frame of a source starts this long before its end; a held "last frame" is read there. */
    private const val FRAME_US = 33_000L
    private const val US_PER_MS = 1_000L

    /** What [plan] found: the windows each main clip is drawn through (by item id), and the partner layer (null when there are no transitions). */
    class Result(val windowsByClipId: Map<String, List<TransitionWindow>>, val layer: TransitionLayer?)

    /**
     * [clips] are the main clips in time order with their plan form [visualOf]. A cut gets a window when its transition is one this build draws, the
     * two clips meet exactly and the window lies inside both. Other cuts stay plain.
     */
    fun plan(clips: List<MediaClip>, visualOf: (MediaClip) -> VisualClip, durationUs: Long): Result {
        val windows = mutableMapOf<String, MutableList<TransitionWindow>>()
        val pieces = mutableListOf<Pair<Long, List<PartnerSegment>>>() // window start to its partner pieces
        for ((index, clip) in clips.withIndex()) {
            val transition = clip.transitionOut?.takeIf { TransitionCatalog.isBuilt(it.id) } ?: continue
            val next = clips.getOrNull(index + 1) ?: continue
            // Whole milliseconds, so the pieces and gaps of the partner sum to exactly what Media3 builds.
            val half = transition.durationUs / 2 / US_PER_MS * US_PER_MS
            val window = TransitionWindow(transition.id, clip.endUs - half, transition.durationUs)
            val fits = clip.endUs == next.startUs && half > 0 && window.startUs >= clip.startUs && window.endUs <= next.endUs && window.endUs <= durationUs
            if (!fits) continue
            windows.getOrPut(clip.id) { mutableListOf() }.add(window)
            windows.getOrPut(next.id) { mutableListOf() }.add(window)
            pieces += window.startUs to (leadOf(next, visualOf(next), half, window) + tailOf(clip, visualOf(clip), half, window))
        }
        if (pieces.isEmpty()) return Result(emptyMap(), null)
        return Result(windows, TransitionLayer(layerSegments(pieces, durationUs)))
    }

    // Before the cut the beneath layer shows the incoming clip: the footage before its trim, else its first frame held (a mix when there is only some).
    private fun leadOf(next: MediaClip, visual: VisualClip, half: Long, window: TransitionWindow): List<PartnerSegment> {
        val still = visual.asPartner()
        if (next.source.kind == MediaKind.IMAGE) return listOf(PartnerClip(still.copy(trimInUs = 0, trimOutUs = half), window))
        val spare = next.trimInUs.takeIf { it >= FRAME_US }?.coerceAtMost(half)?.let(::wholeMs) ?: 0L
        return buildList {
            if (half - spare > 0) add(PartnerHold(still, next.trimInUs - spare, half - spare, window))
            if (spare > 0) add(PartnerClip(still.copy(trimInUs = next.trimInUs - spare, trimOutUs = next.trimInUs), window))
        }
    }

    // After the cut it shows the outgoing clip: the footage after its trim, else its last frame held.
    private fun tailOf(clip: MediaClip, visual: VisualClip, half: Long, window: TransitionWindow): List<PartnerSegment> {
        val still = visual.asPartner()
        if (clip.source.kind == MediaKind.IMAGE) return listOf(PartnerClip(still.copy(trimInUs = 0, trimOutUs = half), window))
        val sourceLength = clip.source.durationUs ?: clip.trimOutUs
        val spare = (sourceLength - clip.trimOutUs).takeIf { it >= FRAME_US }?.coerceAtMost(half)?.let(::wholeMs) ?: 0L
        return buildList {
            if (spare > 0) add(PartnerClip(still.copy(trimInUs = clip.trimOutUs, trimOutUs = clip.trimOutUs + spare), window))
            if (half - spare > 0) add(PartnerHold(still, (clip.trimOutUs + spare - FRAME_US).coerceAtLeast(clip.trimInUs), half - spare, window))
        }
    }

    // The partner plays no sound and carries no transition windows of its own (its effect gets the piece's window). A main clip's keyframes only
    // carry its volume until P4-16 draws its framing, and the partner is silent, so dropping them changes no picture today.
    // TODO(P4-16): fold the held edge's keyframed look into a static one (outgoing: the look at the clip's end, incoming: at its start), so A' and B' don't pop at the cut.
    private fun VisualClip.asPartner(): VisualClip = copy(playsOwnAudio = false, keyframes = emptyList(), transitions = emptyList())

    // The pieces of each window in time order, with a gap before each and one after the last up to the end of the video.
    private fun layerSegments(pieces: List<Pair<Long, List<PartnerSegment>>>, durationUs: Long): List<PartnerSegment> = buildList {
        var cursorUs = 0L
        for ((startUs, segments) in pieces) {
            if (startUs > cursorUs) add(Gap(startUs - cursorUs))
            addAll(segments)
            cursorUs = startUs + segments.sumOf { it.durationUs }
        }
        if (cursorUs < durationUs) add(Gap(durationUs - cursorUs))
    }

    private fun wholeMs(us: Long): Long = us / US_PER_MS * US_PER_MS

    private val MediaClip.endUs: Long get() = startUs + durationUs
}
