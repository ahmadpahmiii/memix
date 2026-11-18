package app.memix.engine.video.reverse

import android.net.Uri
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.engine.video.CompositionPlan
import app.memix.engine.video.HeldFrames
import app.memix.engine.video.HoldKey

/** A plan with its reversed clips swapped for what plays now (see [withReversedClips]), the files that go with the swap, and how far the copies are. */
internal class ReversedPlan(
    val plan: CompositionPlan,
    /** The plan's files, plus one for every copy or still the swap brought in. */
    val uriByMedia: Map<MediaRef, Uri>,
    /** The slowest running copy, 0 to 100; null when none is being made. */
    val percent: Int?,
    /** The clips whose copy could not be made, with whether the phone is full. */
    val failures: List<ClipFailure>,
    /** Where each reversed range stood when the swap was made, so a later change of one can be told from "still going". */
    val states: Map<ReversalKey, CopyState?> = emptyMap(),
) {
    class ClipFailure(val itemId: String, val noSpace: Boolean, val tooLong: Boolean)
}

/**
 * Makes a plan with reversed clips playable (P4-10): asks [copies] for each reversed range and swaps in the finished copy, or, until it is ready, the clip's
 * first frame as a silent still ([HeldFrames]). The preview calls [forPreview] on every build and again when a copy finishes; the export calls [forExport], which
 * waits. Reads files: call off the main thread.
 */
internal class ReversedClips(private val copies: ReversedCopies, private val heldFrames: HeldFrames) {

    /** The plan for the preview of [owner]; starts the copies it lacks and cancels the ones [owner] no longer needs. */
    fun forPreview(owner: Any, plan: CompositionPlan, uriByMedia: Map<MediaRef, Uri>): ReversedPlan {
        val items = plan.reversedItems()
        val sources = sourcesOf(items.map { it.second }, uriByMedia)
        val states = copies.request(owner, sources)
        return swapped(plan, uriByMedia, states, items)
    }

    /** The plan for the export: waits for every copy ([onProgress] 0 to 1) and throws [ReverseException] when one can't be made. */
    suspend fun forExport(owner: Any, plan: CompositionPlan, uriByMedia: Map<MediaRef, Uri>, onProgress: (Float) -> Unit): ReversedPlan {
        val items = plan.reversedItems()
        if (items.isEmpty()) return ReversedPlan(plan, uriByMedia, null, emptyList())
        val ready = copies.awaitAll(owner, sourcesOf(items.map { it.second }, uriByMedia), onProgress)
        return swapped(plan, uriByMedia, ready, items)
    }

    /** Emits when any copy's progress moved or one finished or failed. */
    val changes get() = copies.changes

    /** Where each of [keys] stands now; null for a range with no copy and no work started. */
    fun statesOf(keys: Set<ReversalKey>): Map<ReversalKey, CopyState?> = keys.associateWith(copies::stateOf)

    /** [owner] is gone (the preview closed): its copies in the making stop. */
    fun release(owner: Any) = copies.release(owner)

    // A range whose source file is missing is left out: its clip plays as black like any missing clip.
    private fun sourcesOf(keys: List<ReversalKey>, uriByMedia: Map<MediaRef, Uri>): Map<ReversalKey, Uri> =
        keys.distinct().mapNotNull { key -> uriByMedia[key.source]?.let { key to it } }.toMap()

    private fun swapped(plan: CompositionPlan, uriByMedia: Map<MediaRef, Uri>, states: Map<ReversalKey, CopyState>, items: List<Pair<String, ReversalKey>>): ReversedPlan {
        if (items.isEmpty()) return ReversedPlan(plan, uriByMedia, null, emptyList())
        val uris = uriByMedia.toMutableMap()
        val pieces = states.mapValues { (key, state) -> pieceOf(key, state, uriByMedia, uris) }
        val swappedPlan = plan.withReversedClips { key -> pieces[key] }
        val percents = states.values.filterIsInstance<CopyState.Preparing>().map { it.percent }
        val failures = items.mapNotNull { (itemId, key) -> (states[key] as? CopyState.Failed)?.let { ReversedPlan.ClipFailure(itemId, it.noSpace, it.tooLong) } }
        return ReversedPlan(swappedPlan, uris, percents.minOrNull(), failures, states)
    }

    private fun pieceOf(key: ReversalKey, state: CopyState, uriByMedia: Map<MediaRef, Uri>, uris: MutableMap<MediaRef, Uri>): ReversedPiece? = when (state) {
        is CopyState.Ready -> {
            val ref = MediaRef(
                origin = MediaOrigin.CatalogItem("reversed/${state.file.name}"),
                kind = MediaKind.VIDEO,
                durationUs = state.range.spanUs,
                pixelSize = key.source.pixelSize,
                // A source whose sound this reverser can't carry (more than two channels) gives a copy with no sound track.
                hasAudio = key.source.hasAudio?.let { it && hasSoundTrack(state.file) },
            )
            uris[ref] = Uri.fromFile(state.file)
            ReversedPiece.Copy(ref, state.range.endUs)
        }
        // Not ready: the clip's first frame, which is the last frame of the range, held and silent.
        is CopyState.Preparing, is CopyState.Failed -> firstFrame(key, uriByMedia, uris)
    }

    private fun hasSoundTrack(file: java.io.File): Boolean {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME).orEmpty().startsWith("audio/") }
        } catch (e: java.io.IOException) {
            true
        } finally {
            extractor.release()
        }
    }

    private fun firstFrame(key: ReversalKey, uriByMedia: Map<MediaRef, Uri>, uris: MutableMap<MediaRef, Uri>): ReversedPiece? {
        val sourceUri = uriByMedia[key.source] ?: return null
        val frameUs = maxOf(key.startUs, key.endUs - FRAME_US)
        val file = heldFrames.still(HoldKey(key.source, frameUs), sourceUri) ?: return null
        val ref = MediaRef(
            origin = MediaOrigin.CatalogItem("reversed-first-frame/${file.name}"),
            kind = MediaKind.IMAGE,
            pixelSize = key.source.pixelSize,
            hasAudio = false,
        )
        uris[ref] = Uri.fromFile(file)
        return ReversedPiece.Still(ref)
    }

    private companion object {
        /** The last frame of a range starts this long before its end. */
        const val FRAME_US = 33_333L
    }
}
