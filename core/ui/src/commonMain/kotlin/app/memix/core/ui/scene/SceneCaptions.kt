package app.memix.core.ui.scene

import androidx.compose.ui.graphics.drawscope.DrawScope
import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.CaptionLook
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
     * about it) at [opacity], its words laid out as [look] says (a caption's, or a bubble's): the whole caption, outline under fill, is drawn at full strength into a group and the group is
     * composited at [opacity], so a translucent caption never shows its outline through the fill.
     */
    fun draw(scope: DrawScope, text: String, style: CaptionStyle, transform: Transform, opacity: Float, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook = CaptionLook.Text)

    /**
     * The laid-out block of [text] in [style] as [look] says, at scale 1 in canvas pixels (the widest line by all the lines'
     * height): a speech bubble's body grows around it (spec P2-02 → Bubbles). Cached with the layout [draw] uses.
     */
    fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook): BoxSize
}

/** Draws nothing: previews, harnesses and iOS until its renderer exists. */
object NoSceneCaptions : SceneCaptions {
    override fun draw(scope: DrawScope, text: String, style: CaptionStyle, transform: Transform, opacity: Float, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook) = Unit

    override fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook): BoxSize = BoxSize(0f, 0f)
}
