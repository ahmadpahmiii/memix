package app.memix.core.model.project

import kotlinx.serialization.Serializable

/** A picture's width and height in pixels. */
@Serializable
data class PixelSize(val widthPx: Int, val heightPx: Int)
