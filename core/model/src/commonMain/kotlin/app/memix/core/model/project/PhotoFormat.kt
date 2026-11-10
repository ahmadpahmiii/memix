package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A meme format of the photo editor (spec docs/ux/specs/P2-02-meme-formats.md): a ready-made layout around the user's own
 * layers. It is declarative and slot-based: the parts it draws (a bar, a poster and frame, panels' cells) and the places
 * it invites words or a bubble (its slots) are all derived from the format and the canvas ([PhotoFormats.kt]), never
 * saved, so Phase 4's photo-format templates can carry a format as it is. `PhotoScene.format` is null for **plain**: no
 * parts, no hints, no cells (every new photo meme, and every draft saved before P2-02).
 *
 * Every size is a share of S, the canvas's shorter side ([PhotoFormatCatalog]), so a format scales exactly with the export
 * size. Stable `@SerialName`s: they are in saved drafts.
 */
@Serializable
sealed interface PhotoFormat

/** Two slot hints, top and bottom, over the plain canvas. No parts. */
@Serializable
@SerialName("classic")
data object ClassicFormat : PhotoFormat

/** A white bar across the top that holds one caption; the photo is placed in the canvas below it. */
@Serializable
@SerialName("caption_bar")
data object CaptionBarFormat : PhotoFormat

/** A black poster with a thin white frame holding the photo, a title and a subtitle below it. */
@Serializable
@SerialName("demotivational")
data object DemotivationalFormat : PhotoFormat

/** One bubble slot; bubbles themselves are layers ([BubbleLayer]). */
@Serializable
@SerialName("bubbles")
data object BubblesFormat : PhotoFormat

/**
 * 2 to 6 photo cells that fill the canvas except a gutter ([PhotoFormatCatalog.PANELS_GUTTER_OF_SHORT_SIDE]).
 * [layout] is one of the ids of `PanelLayouts` (`cols-2`, `grid-4`, ...); [dividers] are the splits' positions in that
 * layout's fixed order (outer splits first, then each group's own), each 0 to 1 of its split's extent; [cells] are the
 * photos, in reading order, [PanelLayoutSpec.cellCount] of them.
 */
@Serializable
@SerialName("panels")
data class PanelsFormat(
    val layout: String,
    val dividers: List<Float>,
    val cells: List<PanelCell>,
) : PhotoFormat

/**
 * One cell of [PanelsFormat]: one photo or nothing, drawn to cover the cell at [zoom] (1 to 5). ([centerX], [centerY]) is
 * the point of the picture, as fractions of it, that sits at the cell's center; it is kept where the cell stays covered.
 */
@Serializable
data class PanelCell(
    val source: MediaRef? = null,
    val zoom: Float = 1f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
)

/** The three shapes of a bubble (spec P2-02 → Bubbles). */
@Serializable
enum class BubbleShape {
    @SerialName("speech") SPEECH,
    @SerialName("round") ROUND,
    @SerialName("thought") THOUGHT,
}

/**
 * A speech bubble: a layer like any other (it moves, scales, turns, fades, locks, hides, reorders, duplicates and deletes),
 * its body growing around its [text] ([BubbleGeometry.kt]). The words use a caption [style] (the color is the words'; the
 * outline is unused). Body and tail are one shape filled with [fill] and stroked black. The tail's tip is stored as an offset
 * from the body's center in body widths and heights ([tailX], [tailY]) and stays within [PhotoFormatCatalog.TAIL_TIP_LIMIT_OF_BODY].
 */
@Serializable
@SerialName("bubble_layer")
data class BubbleLayer(
    override val id: String,
    val text: String,
    val style: CaptionStyle,
    val shape: BubbleShape,
    val fill: ArgbColor,
    val tailX: Float,
    val tailY: Float,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    override fun withId(id: String): BubbleLayer = copy(id = id)

    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): BubbleLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = opacity)
}
