package app.memix.core.model.project

import kotlinx.serialization.Serializable

/** How meme text looks, shared by the video editor's [TextItem] and the photo editor's [TextLayer]. */
@Serializable
data class CaptionStyle(
    /** One of the bundled font ids, for example "anton". */
    val fontId: String,
    val color: ArgbColor,
    /** Null when the text has no outline. */
    val outlineColor: ArgbColor?,
)
