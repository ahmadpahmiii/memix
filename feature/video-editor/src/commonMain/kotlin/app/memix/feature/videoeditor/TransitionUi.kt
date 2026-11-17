package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.ui.scene.SceneBitmap

/**
 * The Transition panel of the cut after main clip [clipId] (P4-08; spec docs/ux/specs/P4-08-transitions.md → The Transition panel): the cut's
 * transition ([transitionId], null is None) and its [durationUs], which follows the slider while it moves. [maxDurationUs] is the longest the two
 * neighbours allow (the thumb stops there). [frames] are the two pictures the tiles are made from, once they have arrived.
 */
@Immutable
data class TransitionPanelUi(
    val clipId: String,
    val transitionId: String?,
    val durationUs: Long,
    val maxDurationUs: Long,
    val frames: TransitionFrames? = null,
)

/** The last frame of the clip before the cut and the first of the clip after it, for the tiles; [key] names both, so the tiles are made again only for other pictures. */
@Immutable
class TransitionFrames(val key: String, val outgoing: SceneBitmap, val incoming: SceneBitmap)
