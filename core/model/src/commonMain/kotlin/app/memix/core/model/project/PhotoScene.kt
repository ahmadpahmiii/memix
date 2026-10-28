package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The photo editor's content. Meme formats, drawing, censor and cut-out layers arrive with their Phase 2 tickets. */
@Serializable
data class PhotoScene(
    /** Bottom to top: later layers draw over earlier ones. */
    val layers: List<Layer>,
)

/**
 * One thing stacked on the photo canvas. The state flags are the same for every kind of layer (spec
 * docs/ux/specs/P2-01-photo-editor-shell.md → Layers on the canvas); their defaults mean a layer saved before schema 3
 * is unchanged.
 */
@Serializable
sealed interface Layer {
    val id: String
    val transform: Transform

    /** Locked means "don't change this by accident": it can be selected, unlocked, hidden and duplicated, nothing else. */
    val locked: Boolean

    /** A hidden layer isn't drawn, in the editor or in export, and can't be hit on the canvas. */
    val hidden: Boolean

    /** 1 draws it fully, [PhotoCatalog.OPACITY_RANGE]'s start is the most see-through it gets. */
    val opacity: Float

    /** This layer under another id (a duplicate). */
    fun withId(id: String): Layer

    /** This layer with the given state; each kind of layer copies itself. */
    fun withState(
        transform: Transform = this.transform,
        locked: Boolean = this.locked,
        hidden: Boolean = this.hidden,
        opacity: Float = this.opacity,
    ): Layer
}

@Serializable
@SerialName("image_layer")
data class ImageLayer(
    override val id: String,
    val source: MediaRef,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    override fun withId(id: String): ImageLayer = copy(id = id)

    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): ImageLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = opacity)
}

@Serializable
@SerialName("text_layer")
data class TextLayer(
    override val id: String,
    val text: String,
    val style: CaptionStyle,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    override fun withId(id: String): TextLayer = copy(id = id)

    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): TextLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = opacity)
}

@Serializable
@SerialName("sticker_layer")
data class StickerLayer(
    override val id: String,
    val source: MediaRef,
    override val transform: Transform = Transform(),
    override val locked: Boolean = false,
    override val hidden: Boolean = false,
    override val opacity: Float = 1f,
) : Layer {
    override fun withId(id: String): StickerLayer = copy(id = id)

    override fun withState(transform: Transform, locked: Boolean, hidden: Boolean, opacity: Float): StickerLayer =
        copy(transform = transform, locked = locked, hidden = hidden, opacity = opacity)
}
