package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The four mask shapes (P4-03b). The serial names are saved in drafts and sent as the `value` of `tool_use` `mask`: never rename. */
@Serializable
enum class MaskShape(val id: String) {
    @SerialName("rectangle") RECTANGLE("rectangle"),
    @SerialName("circle") CIRCLE("circle"),
    @SerialName("linear") LINEAR("linear"),
    @SerialName("mirror") MIRROR("mirror"),
}

/**
 * A clip's shape mask (P4-03b): the part of the clip that shows. A declarative spec that every engine renders the same way
 * ([MaskCatalog] holds the rules, `MaskMath` in :core:domain the CPU twin of the shader); it never changes a file. Null on a
 * [MediaClip] means no mask. Every number is a fraction of the clip's own box (its picture after Fit or Fill, before framing), so the
 * mask moves, scales and turns with the clip. Not keyframed in v1.
 */
@Serializable
data class ClipMask(
    val shape: MaskShape,
    /** The mask's center, 0 (left, top) to 1 (right, bottom) of the box. Linear: a point on the line. Mirror: the band's middle. */
    val centerX: Float = MaskCatalog.CENTER,
    val centerY: Float = MaskCatalog.CENTER,
    /**
     * Rectangle: share of the box's width. Circle and Mirror: share of the box's shorter side (Mirror: the band's width). Unused by Linear.
     */
    val width: Float = MaskCatalog.defaultWidth(shape),
    /** Rectangle: share of the box's height. Circle: share of the box's shorter side. Unused by Linear and Mirror. */
    val height: Float = MaskCatalog.defaultHeight(shape),
    /** Clockwise on screen. Linear: the arrow points up at 0 and the side it points to stays. */
    val rotationDegrees: Float = 0f,
    /** 0 is a sharp edge, 1 a soft one [MaskCatalog.FEATHER_SHARE] of the box's shorter side wide, centered on the edge. */
    val softness: Float = 0f,
    /** Shows the outside instead of the inside. */
    val invert: Boolean = false,
    /** Rectangle only: 0 sharp corners, 1 the corner radius is half the rectangle's shorter side (a pill). */
    val cornerRoundness: Float = 0f,
)
