package app.memix.core.ui.scene

import androidx.compose.ui.graphics.drawscope.DrawScope
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform

/**
 * Draws one caption into the photo scene (spec docs/ux/specs/P2-03-photo-text-and-stickers.md → How a caption draws): the
 * same renderer that draws video captions in the preview and the export, so a style looks the same in both editors. The
 * scene function ([drawPhotoScene]) hands it a [DrawScope] whose units are canvas pixels of a canvas [canvasWidthPx] ×
 * [canvasHeightPx] and clipped at its edge; the implementation lays the caption out once in those pixels (cached by words,
 * style and canvas) and draws it, never again per frame of a drag. Implemented in `:engine:video` (Android: android.graphics
 * text layout; iOS: nothing until P7-04) and bound in the composition root, so no engine depends on another.
 */
interface SceneCaptions {
    /**
     * Draws [text] in [style] with [transform] (its block centered at the transform's center, scaled and turned clockwise
     * about it) at [opacity]: the whole caption, outline under fill, is drawn at full strength into a group and the group is
     * composited at [opacity], so a translucent caption never shows its outline through the fill.
     */
    fun draw(scope: DrawScope, text: String, style: CaptionStyle, transform: Transform, opacity: Float, canvasWidthPx: Int, canvasHeightPx: Int)
}

/** Draws nothing: previews, harnesses and iOS until its renderer exists. */
object NoSceneCaptions : SceneCaptions {
    override fun draw(scope: DrawScope, text: String, style: CaptionStyle, transform: Transform, opacity: Float, canvasWidthPx: Int, canvasHeightPx: Int) = Unit
}
