package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** How a censor hides what is under it. */
@Serializable
enum class CensorMode {
    @SerialName("blur") BLUR,
    @SerialName("pixelate") PIXELATE,
    @SerialName("bar") BAR,
}

@Serializable
enum class CensorShape {
    @SerialName("rect") RECT,
    @SerialName("ellipse") ELLIPSE,
}

/**
 * An area that hides what is drawn under it (spec docs/ux/specs/P2-05-draw-and-censor.md → The censor layer): a backdrop effect,
 * so layers above it stay sharp. [widthS] and [heightS] are its own size in S (the canvas's shorter side) at scale 1; [strength]
 * is the slider, 0 to 1, where 0 is the safe minimum of [CensorCatalog], never off. A censor has no opacity: [opacity] is always 1.
 */
@Serializable
@SerialName("censor_layer")
data class CensorLayer(
    override val id: String,
    val mode: CensorMode = CensorCatalog.DEFAULT_MODE,
    val shape: CensorShape = CensorShape.RECT,
    val strength: Float = CensorCatalog.DEFAULT_STRENGTH,
    val widthS: Float = CensorCatalog.DEFAULT_WIDTH_S,
    val heightS: Float = CensorCatalog.DEFAULT_HEIGHT_S,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    override fun withId(id: String): CensorLayer = copy(id = id)

    // Opacity stays 1 whatever is asked: a see-through censor shows what it should hide.
    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): CensorLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = 1f)
}

/**
 * The censor's fixed rules (spec P2-05; design/tokens.json → photo.censor). They are what makes a censor safe, so they are part of
 * every draft's look: changing one after the closed test needs a schema bump. S is the canvas's shorter side; a is the area's
 * shorter side (its width or height times the layer's scale, whichever is smaller).
 */
object CensorCatalog {
    val DEFAULT_MODE = CensorMode.PIXELATE
    const val DEFAULT_STRENGTH = 0.5f
    const val DEFAULT_WIDTH_S = 0.40f
    const val DEFAULT_HEIGHT_S = 0.25f

    /** Width and height, each, in S at scale 1. */
    val SIZE_RANGE: ClosedFloatingPointRange<Float> = 0.06f..3f

    /** The layer's own scale, as a sticker's. */
    val SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..5f

    /** A resize or Width/Height step moves a side by this much, in S. */
    const val SIZE_STEP_S = 0.02f

    /** Pixelate blocks are a × 1/5 at strength 0 to a × 1/2 at strength 1: at most five across the shorter side. */
    private const val BLOCK_OF_AREA_MIN = 0.2f
    private const val BLOCK_OF_AREA_MAX = 0.5f
    private const val MIN_BLOCK_S = 0.012f

    /** Blur sigma is a × 0.22 at strength 0 to a × 0.5 at strength 1. */
    private const val SIGMA_OF_AREA_MIN = 0.22f
    private const val SIGMA_OF_AREA_MAX = 0.5f
    private const val MIN_SIGMA_S = 0.016f

    /** The area's shorter side, in S, for a censor [widthS] by [heightS] at [scale]. */
    fun shorterSideS(widthS: Float, heightS: Float, scale: Float): Float = min(widthS, heightS) * scale

    /** A pixelate block's side in S. Strength 0 is this minimum, never off. */
    fun blockS(areaShorterSideS: Float, strength: Float): Float =
        max(MIN_BLOCK_S, areaShorterSideS * (BLOCK_OF_AREA_MIN + (BLOCK_OF_AREA_MAX - BLOCK_OF_AREA_MIN) * strength.coerceIn(0f, 1f)))

    /** A blur's Gaussian sigma in S. Strength 0 is this minimum, never off. */
    fun sigmaS(areaShorterSideS: Float, strength: Float): Float =
        max(MIN_SIGMA_S, areaShorterSideS * (SIGMA_OF_AREA_MIN + (SIGMA_OF_AREA_MAX - SIGMA_OF_AREA_MIN) * strength.coerceIn(0f, 1f)))
}
