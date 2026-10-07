package app.memix.core.model.project

import kotlinx.serialization.Serializable

/**
 * Where a text, sticker or image sits on the canvas. Positions are fractions of the canvas size,
 * so a layer stays in the same place when the canvas ratio or the export resolution changes.
 */
@Serializable
data class Transform(
    /** 0 is the left edge of the canvas, 1 the right edge. */
    val centerX: Float = 0.5f,
    /** 0 is the top edge of the canvas, 1 the bottom edge. */
    val centerY: Float = 0.5f,
    val scale: Float = 1f,
    /** Clockwise. */
    val rotationDegrees: Float = 0f,
)
