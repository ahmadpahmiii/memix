package app.memix.core.model.project

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.Serializable

/**
 * Everything the image tools did to one [ImageLayer] (spec docs/ux/specs/P2-04-image-tools.md → Model). All of it is a
 * parameter, never a new file: the media copy is not changed, so every edit stays editable and undoable. Null on the layer
 * means "no edit". The geometry (turn, flip, straighten, crop) is defined by the functions below; the color tools by
 * [ImageToolCatalog]. The pipeline order is fixed whatever order the user made the edits in.
 */
@Serializable
data class ImageEdit(
    /** Clockwise quarter turns of the picture, 0 to 3. */
    val quarterTurns: Int = 0,
    /** A mirror of the turned picture, as seen: left to right. */
    val flipH: Boolean = false,
    /** A mirror of the turned picture, as seen: top to bottom. */
    val flipV: Boolean = false,
    /** Degrees clockwise, [ImageToolCatalog.STRAIGHTEN_RANGE]; the picture is scaled up so it still covers its frame. */
    val straighten: Float = 0f,
    /** The kept part, as fractions of the turned, flipped and straightened picture's frame. */
    val crop: CropRect = CropRect.Full,
    val adjust: ImageAdjust = ImageAdjust(),
    val filter: FilterRef? = null,
    /** 0 is off, 1 the full fry. */
    val deepFry: Float = 0f,
) {
    /** True when this edit changes nothing, so the layer can drop it (and not show "Edited"). */
    val isNeutral: Boolean get() = this == NONE

    /** True when the picture's geometry (shape or turn) differs from the file's. */
    val changesGeometry: Boolean
        get() = quarterTurns != 0 || flipH || flipV || straighten != 0f || crop != CropRect.Full

    /** The color tools only (adjustments, filter, deep-fry). */
    val hasColorEdits: Boolean get() = adjust != ImageAdjust() || filter != null || deepFry != 0f

    companion object {
        val NONE = ImageEdit()
    }
}

/** A crop as fractions (0 to 1) of the frame: [left] < [right] and [top] < [bottom]. */
@Serializable
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = CropRect()
    }
}

/**
 * The six adjustments, each a fraction: brightness, contrast, saturation and warmth from -1 to 1, sharpen and vignette from
 * 0 to 1. Zero is "off" for all six.
 */
@Serializable
data class ImageAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
)

/** One of [ImageToolCatalog.filters] at [intensity] (0 to 1). */
@Serializable
data class FilterRef(val id: String, val intensity: Float = 1f)

/** [this] layer's edit, or null when none. */
fun ImageLayer.editOrNull(): ImageEdit? = edit?.takeUnless { it.isNeutral }

/** A layer with a neutral edit is saved as one with none. */
fun ImageLayer.withEdit(edit: ImageEdit?): ImageLayer = copy(edit = edit?.takeUnless { it.isNeutral })

/**
 * The picture the layer shows, as a size: [picture] (the file's size as shown) turned and cropped. Everything that sizes an
 * image layer (its box, its place in a format, the ratio rule, the decode size) uses this one function.
 */
fun ImageLayer.shownSize(picture: PixelSize): PixelSize = edit.shownSize(picture)

fun ImageEdit?.shownSize(picture: PixelSize): PixelSize {
    if (this == null) return picture
    val turned = turnedSize(picture)
    return PixelSize(
        max(1, (turned.widthPx * crop.width).roundToInt()),
        max(1, (turned.heightPx * crop.height).roundToInt()),
    )
}

/** [picture] after the quarter turns: width and height swap on an odd turn. */
fun ImageEdit?.turnedSize(picture: PixelSize): PixelSize =
    if (this != null && quarterTurns % 2 != 0) PixelSize(picture.heightPx, picture.widthPx) else picture

/**
 * How much the picture is scaled up so a picture turned by [degrees] still covers its whole frame (width : height =
 * [aspect]): cos a + sin a * max(aspect, 1/aspect). About 1.2 at 10 degrees on 4:3.
 */
fun straightenScale(degrees: Float, aspect: Float): Float {
    val radians = abs(degrees) * PI_FLOAT / HALF_TURN
    return cos(radians) + sin(radians) * max(aspect, 1f / aspect)
}

private const val PI_FLOAT = 3.1415927f
private const val HALF_TURN = 180f

/** This crop after the picture turns a quarter clockwise: the frame keeps its place on the picture. */
fun CropRect.turnedRight(): CropRect = CropRect(1f - bottom, left, 1f - top, right)

/** This crop after the picture turns a quarter counter-clockwise. */
fun CropRect.turnedLeft(): CropRect = CropRect(top, 1f - right, bottom, 1f - left)

fun CropRect.mirroredAcross(): CropRect = CropRect(1f - right, top, 1f - left, bottom)

fun CropRect.mirroredDown(): CropRect = CropRect(left, 1f - bottom, right, 1f - top)

/** [this] turned a quarter clockwise, its crop going with the picture (spec P2-04 → The Crop screen). */
// A mirror applies after the turn, so turning what is seen swaps which axis a mirror is on.
fun ImageEdit.turnedRight(): ImageEdit = copy(quarterTurns = (quarterTurns + 1) % 4, crop = crop.turnedRight(), flipH = flipV, flipV = flipH)

fun ImageEdit.turnedLeft(): ImageEdit = copy(quarterTurns = (quarterTurns + 3) % 4, crop = crop.turnedLeft(), flipH = flipV, flipV = flipH)

fun ImageEdit.flippedAcross(): ImageEdit = copy(flipH = !flipH, crop = crop.mirroredAcross(), straighten = -straighten)

fun ImageEdit.flippedDown(): ImageEdit = copy(flipV = !flipV, crop = crop.mirroredDown(), straighten = -straighten)

/**
 * How many times bigger than the layer's box the whole picture is drawn (the crop shows a part of it, straighten scales it up):
 * the decoder uses it to decode enough pixels. 1 with no edit.
 */
fun ImageEdit?.pictureZoom(picture: PixelSize): Float {
    if (this == null) return 1f
    val turned = turnedSize(picture)
    val shown = shownSize(picture)
    val frameAspect = turned.widthPx.toFloat() / turned.heightPx
    val straightened = if (straighten != 0f) straightenScale(straighten, frameAspect) else 1f
    return max(turned.widthPx, turned.heightPx).toFloat() / max(shown.widthPx, shown.heightPx) * straightened
}
