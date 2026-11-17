package app.memix.core.domain.project

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.Transition
import app.memix.core.model.project.TransitionCatalog

/*
 * Transition edits (spec docs/ux/specs/P4-08-transitions.md → What a transition is). A transition belongs to the main clip before the cut and
 * is a picture effect around it: nothing moves, so these edits only change the one field and never touch a start time, a length or a sound.
 *
 * What fits: a clip gives half of each transition at its two ends, so clip i must be at least d(i-1) / 2 + d(i) / 2 long, and a new
 * transition can be no longer than twice the room left at either neighbour. Every edit that changes a length (trim, split, reorder, speed)
 * is settled by [settleTransitions] where it commits, in the same undo step.
 */

/** Why a cut can't get a transition. */
enum class TransitionBlock {
    /** The clip is the last one (or the only one): nothing follows it. */
    NO_NEXT_CLIP,

    /** The two neighbours can't give [TransitionCatalog.MIN_DURATION_US] between them. */
    TOO_SHORT,
}

/** The main clip [clipId] leads into, or null when it is the last one or not a main clip. */
fun Project.nextMainClip(clipId: String): MediaClip? {
    val ordered = mainTrack()?.let(::mainClipsInOrder).orEmpty()
    val index = ordered.indexOfFirst { it.id == clipId }
    return ordered.getOrNull(index + 1) as? MediaClip
}

/** The main clip [clipId], or null when it isn't one. */
fun Project.mainMediaClip(clipId: String): MediaClip? = mainTrack()?.items?.firstOrNull { it.id == clipId } as? MediaClip

/** The cut after main clip [clipId] in project µs (where the next clip starts), or null when it has no next clip. */
fun Project.cutAfter(clipId: String): Long? = nextMainClip(clipId)?.startUs

/**
 * The longest transition the cut after [clipId] can have, µs, a whole number of 0.1 s up to 2 s; 0 when it can't have one.
 * Counts the half the clip before gives at its own start and the half the clip after gives at its end ([Transition]'s neighbours).
 */
fun Project.maxTransitionDurationUs(clipId: String): Long {
    val ordered = mainTrack()?.let(::mainClipsInOrder).orEmpty().filterIsInstance<MediaClip>()
    val index = ordered.indexOfFirst { it.id == clipId }
    val next = ordered.getOrNull(index + 1) ?: return 0L
    val clip = ordered[index]
    val before = ordered.getOrNull(index - 1)?.transitionOut?.durationUs ?: 0L
    val after = next.transitionOut?.takeIf { ordered.getOrNull(index + 2) != null }?.durationUs ?: 0L
    val roomUs = minOf(2 * clip.durationUs - before, 2 * next.durationUs - after)
    return TransitionCatalog.roundedDown(roomUs.coerceAtLeast(0L)).takeIf { it >= TransitionCatalog.MIN_DURATION_US } ?: 0L
}

/** Why the cut after [clipId] can't get a transition, or null when it can. */
fun Project.transitionBlock(clipId: String): TransitionBlock? = when {
    nextMainClip(clipId) == null -> TransitionBlock.NO_NEXT_CLIP
    maxTransitionDurationUs(clipId) < TransitionCatalog.MIN_DURATION_US -> TransitionBlock.TOO_SHORT
    else -> null
}

/**
 * Gives the cut after [clipId] the transition [transitionId]. The duration is the cut's current one, else the transition's own default,
 * both stopped at [maxTransitionDurationUs]. An id this build doesn't draw, or a cut that can't have one, changes nothing.
 */
fun Project.setTransition(clipId: String, transitionId: String): Project {
    val def = TransitionCatalog.of(transitionId) ?: return this
    val clip = mainMediaClip(clipId) ?: return this
    val maxUs = maxTransitionDurationUs(clipId).takeIf { it > 0 } ?: return this
    val wantedUs = clip.transitionOut?.durationUs ?: def.defaultDurationUs
    return withTransition(clip, Transition(def.id, minOf(wantedUs, maxUs))).unlessTooFlashy(this)
}

/** The duration of the transition after [clipId] as close to [durationUs] as the cut allows (0.1 s steps, up to [maxTransitionDurationUs]). */
fun Project.setTransitionDuration(clipId: String, durationUs: Long): Project {
    val clip = mainMediaClip(clipId) ?: return this
    val current = clip.transitionOut ?: return this
    val maxUs = maxTransitionDurationUs(clipId).takeIf { it > 0 } ?: return this
    val steppedUs = ((durationUs + TransitionCatalog.DURATION_STEP_US / 2) / TransitionCatalog.DURATION_STEP_US) * TransitionCatalog.DURATION_STEP_US
    val clampedUs = steppedUs.coerceIn(TransitionCatalog.MIN_DURATION_US, maxUs)
    return withTransition(clip, current.copy(durationUs = clampedUs)).unlessTooFlashy(this)
}

// An edit that makes more flash events than allowed in one second is refused (WCAG 2.3.1): the project stays as it was. One that was already over changes nothing here.
private fun Project.unlessTooFlashy(before: Project): Project = if (exceedsFlashRate() && !before.exceedsFlashRate()) before else this

/**
 * When the flashes of the transitions happen, µs: one at the cut of each Dip to white, three for each Glitch cut at its bursts (spec P4-08 → The list).
 * Only transitions this build draws count.
 */
fun Project.transitionFlashTimesUs(): List<Long> = drawnTransitions().flatMap { (clip, transition) ->
    val cutUs = clip.startUs + clip.durationUs
    when (transition.id) {
        "dip_white" -> listOf(cutUs)
        "glitch_cut" -> {
            val startUs = cutUs - transition.durationUs / 2
            TransitionCatalog.GLITCH_BURST_CENTERS.map { startUs + (it * transition.durationUs).toLong() }
        }
        else -> emptyList()
    }
}

/** The cut after [clipId] as a plain cut. */
fun Project.removeTransition(clipId: String): Project {
    val clip = mainMediaClip(clipId) ?: return this
    return if (clip.transitionOut == null) this else withTransition(clip, null)
}

private fun Project.withTransition(clip: MediaClip, transition: Transition?): Project {
    if (clip.transitionOut == transition) return this
    val track = mainTrack() ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == clip.id) clip.copy(transitionOut = transition) else it }))
}

/** What [settleTransitions] had to do to make every transition fit. */
enum class TransitionSettling { NOTHING, SHORTENED, REMOVED }

/**
 * [Project] with every main-track transition made to fit again after an edit changed the clips' lengths or order (spec → What fits): one on
 * the last clip goes silently, a longer one than the cut allows is shortened to what fits, and one that can't keep even 0.1 s goes. Earlier
 * cuts are settled first, so the later of two competing transitions is the one that gives way. [TransitionSettling] says the worst thing done,
 * for the toast; removing one from the last clip isn't counted (nothing was too short).
 */
fun Project.settleTransitions(): Pair<Project, TransitionSettling> {
    val track = mainTrack() ?: return this to TransitionSettling.NOTHING
    val ordered = mainClipsInOrder(track)
    if (ordered.none { it is MediaClip && it.transitionOut != null }) return this to TransitionSettling.NOTHING
    var worst = TransitionSettling.NOTHING
    var previousUs = 0L
    val settled = ordered.mapIndexed { index, item ->
        val clip = item as? MediaClip ?: return@mapIndexed item
        val transition = clip.transitionOut ?: run { previousUs = 0L; return@mapIndexed item }
        val next = ordered.getOrNull(index + 1) as? MediaClip
        if (next == null) {
            previousUs = 0L
            return@mapIndexed clip.copy(transitionOut = null)
        }
        val roomUs = TransitionCatalog.roundedDown(minOf(2 * clip.durationUs - previousUs, 2 * next.durationUs).coerceAtLeast(0L))
        val keptUs = minOf(transition.durationUs, roomUs)
        when {
            roomUs < TransitionCatalog.MIN_DURATION_US -> {
                worst = TransitionSettling.REMOVED
                previousUs = 0L
                clip.copy(transitionOut = null)
            }
            keptUs < transition.durationUs -> {
                if (worst == TransitionSettling.NOTHING) worst = TransitionSettling.SHORTENED
                previousUs = keptUs
                clip.copy(transitionOut = transition.copy(durationUs = keptUs))
            }
            else -> {
                previousUs = keptUs
                clip
            }
        }
    }
    val changed = settled.zip(ordered).any { (a, b) -> a !== b }
    return if (changed) replaceTrack(track.copy(items = settled)) to worst else this to worst
}

/** The main clips that lead into the next with a transition this build draws, in play order, each with it. */
fun Project.drawnTransitions(): List<Pair<MediaClip, Transition>> =
    mainTrack()?.let(::mainClipsInOrder).orEmpty().filterIsInstance<MediaClip>()
        .mapNotNull { clip -> clip.transitionOut?.takeIf { TransitionCatalog.isBuilt(it.id) }?.let { clip to it } }
