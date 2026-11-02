package app.memix.engine.video

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import app.memix.core.ui.scene.SceneCaptions
import kotlin.math.roundToInt

/**
 * Draws a photo text layer with the one caption renderer (spec P2-03 → How a caption draws), so the same style gives the same
 * picture as in the video preview and export. Each caption is laid out once in canvas pixels and kept (by words, style and
 * canvas size, the most recently used [MAX_LAYOUTS]); a drag only moves the already laid-out block. Drawn at an opacity below
 * 100%, the caption goes into its own layer first, so the outline never shows through a translucent fill.
 *
 * Draws on whichever thread the scene is drawn on (the draw phase, or the render hook's): each layout owns its paint, so a
 * layout is used by one thread at a time.
 */
internal class AndroidSceneCaptions(private val renderer: AndroidCaptionRenderer) : SceneCaptions {
    private data class Key(val text: String, val style: CaptionStyle, val canvasWidthPx: Int, val canvasHeightPx: Int)

    private val layouts = object : LinkedHashMap<Key, CaptionLayout>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, CaptionLayout>): Boolean = size > MAX_LAYOUTS
    }

    override fun draw(scope: DrawScope, text: String, style: CaptionStyle, transform: Transform, opacity: Float, canvasWidthPx: Int, canvasHeightPx: Int) {
        if (text.isBlank()) return
        val key = Key(text, style, canvasWidthPx, canvasHeightPx)
        val layout = synchronized(layouts) { layouts.getOrPut(key) { renderer.layout(text, style, canvasWidthPx, canvasHeightPx) } }
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            synchronized(layout) {
                if (opacity >= 1f) {
                    layout.draw(native, transform)
                } else {
                    val saved = native.saveLayerAlpha(null, (opacity * ALPHA_MAX).roundToInt().coerceIn(0, ALPHA_MAX))
                    layout.draw(native, transform)
                    native.restoreToCount(saved)
                }
            }
        }
    }

    private companion object {
        const val MAX_LAYOUTS = 64
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val ALPHA_MAX = 255
    }
}
