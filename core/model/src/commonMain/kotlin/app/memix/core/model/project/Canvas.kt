package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The frame everything is placed in. Export scales [widthPx] x [heightPx] to the resolution the user picks. */
@Serializable
data class Canvas(
    /** The ratio chip the user picked; [CanvasRatio.CUSTOM] when the size was typed in. */
    val ratio: CanvasRatio,
    val widthPx: Int,
    val heightPx: Int,
    val background: CanvasBackground,
)

/** The canvas's width and height in pixels. */
fun Canvas.size(): PixelSize = PixelSize(widthPx, heightPx)

@Serializable
enum class CanvasRatio {
    @SerialName("9:16") RATIO_9_16,
    @SerialName("1:1") RATIO_1_1,
    @SerialName("4:5") RATIO_4_5,
    @SerialName("3:4") RATIO_3_4,
    @SerialName("16:9") RATIO_16_9,
    @SerialName("custom") CUSTOM,
}

/** What shows where no clip or layer covers the canvas. */
@Serializable
sealed interface CanvasBackground {
    @Serializable
    @SerialName("color")
    data class Solid(val color: ArgbColor) : CanvasBackground

    /** A blurred, filled copy of the clip that is playing. */
    @Serializable
    @SerialName("blur")
    data object Blur : CanvasBackground

    /**
     * A picture the user picked, drawn to cover the whole canvas (centered, the overflow cropped) under every clip and
     * layer (spec P2-12 → How it draws). [source] is the app's own copy in the project's media folder; its
     * [MediaRef.pixelSize] is the size as shown.
     */
    @Serializable
    @SerialName("image")
    data class Image(val source: MediaRef) : CanvasBackground
}
