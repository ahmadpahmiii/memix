package app.memix.engine.video.reverse

import app.memix.core.model.project.MediaRef
import app.memix.engine.video.CompositionPlan
import app.memix.engine.video.Gap
import app.memix.engine.video.MainVideoSegment
import app.memix.engine.video.OverlayClip
import app.memix.engine.video.OverlaySegment
import app.memix.engine.video.VisualClip

/**
 * One reversed range of a source file (P4-10): the part from [startUs] to [endUs] of [source] that a reversed clip plays. A reversed copy is made for a key
 * and serves every clip whose range lies inside it.
 */
internal data class ReversalKey(val source: MediaRef, val startUs: Long, val endUs: Long) {
    val spanUs: Long get() = endUs - startUs
}

/** What a reversed clip plays while the plan is built. */
internal sealed interface ReversedPiece {
    /**
     * The prepared copy, [copy] naming it: it holds [ReversalKey.source]'s range ending at [copyEndUs] (the range's end) played from the end to the start, so
     * source time t is at `copyEndUs - t` in the copy.
     */
    data class Copy(val copy: MediaRef, val copyEndUs: Long) : ReversedPiece

    /** The copy isn't ready: the clip holds [picture], its first frame, as a silent still. */
    data class Still(val picture: MediaRef) : ReversedPiece
}

/** Every reversed clip of the plan, main video and overlays alike, with the range it needs: (item id, range). */
internal fun CompositionPlan.reversedItems(): List<Pair<String, ReversalKey>> =
    mainVideo.filterIsInstance<VisualClip>().filter { it.reversed }.map { it.itemId to ReversalKey(it.source, it.trimInUs, it.trimOutUs) } +
        overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().filter { it.reversed }.map { it.itemId to ReversalKey(it.source, it.trimInUs, it.trimOutUs) }

/** The reversed ranges the plan's clips need. */
internal fun CompositionPlan.reversalKeys(): Set<ReversalKey> = reversedItems().map { it.second }.toSet()

/**
 * This plan with every reversed clip swapped for what [pieceOf] gives for its range, so the builders only ever play forward: a clip with a [ReversedPiece.Copy]
 * plays the copy over the mirrored range and keeps everything else (speed, volume, framing); one with a [ReversedPiece.Still] becomes a silent photo of the
 * same length; one with no piece (not even a first frame could be read) becomes a gap.
 */
internal fun CompositionPlan.withReversedClips(pieceOf: (ReversalKey) -> ReversedPiece?): CompositionPlan {
    if (reversalKeys().isEmpty()) return this
    fun MainVideoSegment.swapped(): MainVideoSegment {
        if (this !is VisualClip || !reversed) return this
        return when (val piece = pieceOf(ReversalKey(source, trimInUs, trimOutUs))) {
            is ReversedPiece.Copy -> copy(source = piece.copy, trimInUs = piece.copyEndUs - trimOutUs, trimOutUs = piece.copyEndUs - trimInUs, reversed = false)
            is ReversedPiece.Still -> copy(source = piece.picture, trimInUs = 0, trimOutUs = durationUs, speed = 1f, playsOwnAudio = false, reversed = false)
            null -> Gap(durationUs)
        }
    }
    fun OverlaySegment.swapped(): OverlaySegment {
        if (this !is OverlayClip || !reversed) return this
        return when (val piece = pieceOf(ReversalKey(source, trimInUs, trimOutUs))) {
            is ReversedPiece.Copy -> copy(source = piece.copy, trimInUs = piece.copyEndUs - trimOutUs, trimOutUs = piece.copyEndUs - trimInUs, reversed = false)
            is ReversedPiece.Still -> copy(source = piece.picture, trimInUs = 0, trimOutUs = durationUs, speed = 1f, playsOwnAudio = false, reversed = false)
            null -> Gap(durationUs)
        }
    }
    return copy(
        mainVideo = mainVideo.map { it.swapped() },
        overlays = overlays.map { layer -> layer.copy(segments = layer.segments.map { it.swapped() }) },
    )
}
