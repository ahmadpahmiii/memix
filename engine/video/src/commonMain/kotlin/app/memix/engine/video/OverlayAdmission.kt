package app.memix.engine.video

/**
 * The decoder budget of overlays (P4-01a). Every video that plays holds one hardware decoder, and a phone has only so many
 * (and so much bandwidth) at once: the main clip and every overlay video showing at the same moment each need one. Photos
 * decode to a bitmap and need none.
 *
 * The rule, the same for the preview and the export so that what the user sees is what they get: overlay videos are admitted
 * one by one, the top layer first (then the earlier start), and a video is admitted when at every moment of its span the
 * admitted videos plus the main clip plus itself fit in the budget. One that doesn't fit is left out whole and reported in
 * [CompositionPlan.skippedOverlayIds]; a half-drawn overlay would be harder to understand than a missing one. Nothing else is
 * dropped, so a project with at most `budget - 1` overlay videos at once always renders in full.
 */
internal object OverlayAdmission {
    /** Main video and overlays together: the main clip and five overlay videos at once, the ticket's target. */
    const val DEFAULT_VIDEO_DECODER_BUDGET = 6

    /** A video showing from [startUs] to [endUs] (µs, [endUs] excluded), with the layer it is on: a higher layer wins. */
    class Candidate(val itemId: String, val layer: Int, val startUs: Long, val endUs: Long)

    /**
     * The ids of the [candidates] that fit next to the [main] clips' videos (their time spans) in [budget] decoders, bottom
     * layers losing first.
     */
    fun admit(candidates: List<Candidate>, main: List<LongRange>, budget: Int): Set<String> {
        val admitted = mutableListOf<Candidate>()
        val inPriorityOrder = candidates.sortedWith(compareByDescending<Candidate> { it.layer }.thenBy { it.startUs })
        for (candidate in inPriorityOrder) {
            if (fits(candidate, admitted, main, budget)) admitted += candidate
        }
        return admitted.mapTo(HashSet()) { it.itemId }
    }

    // The count only rises where something starts, so it's enough to look at the candidate's start and at each start inside it.
    private fun fits(candidate: Candidate, admitted: List<Candidate>, main: List<LongRange>, budget: Int): Boolean {
        val checkTimes = buildList {
            add(candidate.startUs)
            admitted.filter { it.startUs > candidate.startUs && it.startUs < candidate.endUs }.mapTo(this) { it.startUs }
            main.filter { it.first > candidate.startUs && it.first < candidate.endUs }.mapTo(this) { it.first }
        }
        return checkTimes.all { timeUs ->
            val mainDecoders = main.count { timeUs in it }
            val overlayDecoders = admitted.count { timeUs >= it.startUs && timeUs < it.endUs }
            mainDecoders + overlayDecoders + 1 <= budget
        }
    }
}
