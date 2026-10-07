package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The photo editor's content. Meme formats, drawing, censor and cut-out layers arrive with their Phase 2 tickets. */
@Serializable
data class PhotoScene(
    /** Bottom to top: later layers draw over earlier ones. */
    val layers: List<Layer>,
)

@Serializable
sealed interface Layer {
    val id: String
    val transform: Transform
}

@Serializable
@SerialName("image_layer")
data class ImageLayer(
    override val id: String,
    val source: MediaRef,
    override val transform: Transform = Transform(),
) : Layer

@Serializable
@SerialName("text_layer")
data class TextLayer(
    override val id: String,
    val text: String,
    val style: CaptionStyle,
    override val transform: Transform = Transform(),
) : Layer

@Serializable
@SerialName("sticker_layer")
data class StickerLayer(
    override val id: String,
    val source: MediaRef,
    override val transform: Transform = Transform(),
) : Layer
