package app.memix.core.model.project

import kotlinx.serialization.Serializable

/**
 * The change from one main clip into the next, drawn around the cut (P4-08, spec docs/ux/specs/P4-08-transitions.md → What a transition is).
 * [id] is one of the [TransitionCatalog] ids, saved forever; an id this build can't draw is kept and the cut stays plain. The window is
 * [cut - durationUs / 2, cut + durationUs / 2]. Nothing moves and no sound changes: it is a picture effect only.
 */
@Serializable
data class Transition(
    val id: String,
    val durationUs: Long,
)
