package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How meme text looks, shared by the video editor's [TextItem] and the photo editor's [TextLayer]. */
@Serializable
data class CaptionStyle(
    /** One of the bundled font ids, for example "anton". */
    val fontId: String,
    val color: ArgbColor,
    /** Null when the text has no outline. */
    val outlineColor: ArgbColor?,
    /** Null when the text casts no shadow (P2-11; drafts saved before it have none). */
    val shadow: CaptionShadow? = null,
    /** Null when the text has no background box (P2-11; drafts saved before it have none). */
    val box: CaptionBox? = null,
)

/** A soft copy of the text behind it, in [color] (a caption-palette color) and [softness] (spec P2-11 → How a shadow and a box draw). */
@Serializable
data class CaptionShadow(val color: ArgbColor, val softness: ShadowSoftness = ShadowSoftness.SOFT)

/** A colored rectangle around the whole laid-out block, in [color] (a caption-palette color) with [corners] (spec P2-11). */
@Serializable
data class CaptionBox(val color: ArgbColor, val corners: BoxCorners = BoxCorners.ROUNDED)

/** The three shadow edges; the blur of each is in [CaptionCatalog.shadowBlurEm]. Serial names are saved in drafts and never renamed. */
@Serializable
enum class ShadowSoftness {
    @SerialName("sharp") SHARP,
    @SerialName("soft") SOFT,
    @SerialName("blurry") BLURRY,
}

/** The three box corner steps; the radius of each is in [CaptionCatalog.boxCornerEm]. Serial names are saved in drafts and never renamed. */
@Serializable
enum class BoxCorners {
    @SerialName("square") SQUARE,
    @SerialName("rounded") ROUNDED,
    @SerialName("pill") PILL,
}
