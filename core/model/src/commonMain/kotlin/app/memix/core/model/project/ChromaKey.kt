package app.memix.core.model.project

import kotlinx.serialization.Serializable

/**
 * A clip's green screen (P4-04): the color to remove, how much of its range goes and how softly the edge blends. A declarative spec
 * ([ChromaKeyCatalog] holds its id and the formulas' constants) that every engine renders the same way; the picture is never
 * changed on disk. Null on a [MediaClip] means keying is off.
 */
@Serializable
data class ChromaKey(
    /** The color to remove, as 0xAARRGGBB (the alpha is ignored; always opaque when made by [ChromaKeyCatalog.keyOf]). */
    val colorArgb: Int = ChromaKeyCatalog.GREEN_ARGB,
    /** 0 removes only the exact color, 1 a wide range of similar colors ([ChromaKeyCatalog.toleranceOf]). */
    val strength: Float = ChromaKeyCatalog.DEFAULT_STRENGTH,
    /** 0 is a hard cut-out, 1 a wide soft edge ([ChromaKeyCatalog.featherOf]). */
    val softness: Float = ChromaKeyCatalog.DEFAULT_SOFTNESS,
)
