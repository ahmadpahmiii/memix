package app.memix.feature.videoeditor.greenscreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.feature.videoeditor.VideoEditorIntent

/**
 * The stage in pick mode (spec P4-04 → Pick mode): a press anywhere on the preview shows the loupe, a color circle floating above the
 * finger; releasing picks. It sits over the clips and captions, so a touch can't move or select anything meanwhile. The finger is
 * tracked here and drawn in the draw phase; the color under it is read through [colorAt] into local state, so a finger move never recomposes the editor.
 */
@Composable
internal fun PickLayer(frame: CanvasFrameRect, colorAt: ((Float, Float) -> Int?)?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    var finger by remember { mutableStateOf<Offset?>(null) }
    var loupeColor by remember { mutableStateOf<Int?>(null) }
    val shown by animateFloatAsState(if (finger != null && loupeColor != null) 1f else 0f, tween(MemixMotion.durationPress), label = "loupe")
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(frame) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    try {
                        finger = down.position
                        loupeColor = colorAt?.let { read -> frame.read(read, down.position) }
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                onIntent(frame.releaseAt(change.position))
                                break
                            }
                            finger = change.position
                            loupeColor = colorAt?.let { read -> frame.read(read, change.position) }
                        }
                    } finally {
                        finger = null
                        loupeColor = null
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val at = finger
                val color = loupeColor
                if (at == null || color == null || shown <= 0f) return@drawWithContent
                val radius = MemixSize.swatchSize.toPx() / 2
                // Never under the finger: one touch target and one gap above it.
                val center = Offset(at.x, at.y - MemixSize.touchTarget.toPx() - MemixSpacing.space2.toPx())
                drawCircle(Color(color), radius, center, alpha = shown)
                drawCircle(MemixColors.onPrimary, radius + MemixStroke.strokeHairline.toPx() / 2, center, alpha = shown, style = Stroke(MemixStroke.strokeHairline.toPx()))
                drawCircle(MemixColors.selection, radius + MemixStroke.strokeSelection.toPx() / 2 + MemixStroke.strokeHairline.toPx(), center, alpha = shown, style = Stroke(MemixStroke.strokeSelection.toPx()))
            },
    )
}

private fun CanvasFrameRect.read(colorAt: (Float, Float) -> Int?, point: Offset): Int? = colorAt((point.x - left) / width, (point.y - top) / height)

private fun CanvasFrameRect.releaseAt(point: Offset): VideoEditorIntent.GreenScreenPickReleased =
    VideoEditorIntent.GreenScreenPickReleased((point.x - left) / width, (point.y - top) / height)
