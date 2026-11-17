package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How an item moves from one keyframe to the next (spec docs/ux/specs/P4-02b-keyframes.md → How it draws). The easing belongs to
 * the keyframe the motion leaves from; the curves are in [KeyframeCatalog]. Saved by `@SerialName`, which must never change.
 */
@Serializable
enum class Easing {
    /** Starts and ends gently (CSS ease-in-out). The default of every new keyframe. */
    @SerialName("smooth") SMOOTH,
    @SerialName("linear") LINEAR,
    @SerialName("ease_in") EASE_IN,
    @SerialName("ease_out") EASE_OUT,

    /** Stays on this keyframe's look until the next keyframe's time, then jumps. */
    @SerialName("hold") HOLD,
}

/**
 * Everything about an item that can change over time, at one moment (spec D1: one keyframe holds the whole look). Position is the
 * canvas fraction of the item's center (it may leave the frame while keyed, [KeyframeCatalog.CENTER_RANGE]), [rotationDegrees] is
 * clockwise and never wrapped (0 to 360 is one full spin). A property the item doesn't have is kept at its neutral value and ignored:
 * a caption has no opacity or volume, a sound has no position, a main clip no opacity.
 */
@Serializable
data class ItemLook(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    /** 1 draws fully, 0 not at all. */
    val opacity: Float = 1f,
    /** 1 is the item's original loudness. */
    val volume: Float = 1f,
) {
    /** The place, size and turn of this look. */
    val transform: Transform get() = Transform(centerX, centerY, scale, rotationDegrees)
}

/**
 * One saved moment of an item's motion. [timeUs] counts from the item's start on the timeline (not from its source, and not from
 * the project), so the keyframes move with the item and a later speed change can scale them with it (P4-09). It is negative or past
 * the item's end when a trim cut the item shorter: such a keyframe stays so the motion across the new edge is unchanged, and
 * is simply not drawn by the editor. Keyframes of one item are sorted by [timeUs] and at least [KeyframeCatalog.MIN_SPACING_US] apart.
 */
@Serializable
data class Keyframe(
    val timeUs: Long,
    val look: ItemLook,
    /** The curve of the motion from this keyframe to the next; no effect on the last one. */
    val easing: Easing = KeyframeCatalog.DEFAULT_EASING,
)
