package app.memix.feature.photoeditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * The cut-out screen's stage while a finger is on it (spec docs/ux/specs/P2-07-background-removal.md → Gestures): the picture's place
 * under zoom (1x to 5x) and pan, and the stroke being painted. Points are fractions of the drawn picture's longer side from its center,
 * the unit a stored stroke uses, so the finger's path and the stored stroke agree. Read in draw and gesture code only.
 */
@Stable
internal class CutOutView {
    var stage by mutableStateOf(IntSize.Zero)
    var zoom by mutableFloatStateOf(1f)
        private set
    private var pan by mutableStateOf(Offset.Zero)

    /** The stroke in progress; null when no finger is painting. It grows in place, so [version] says when to redraw. */
    var live by mutableStateOf<Live?>(null)
        private set

    /**
     * Strokes that were let go and are stored, but whose cut picture is still being made: they stay drawn as they were painted, so a
     * stroke never blinks out for the moment the picture takes. [settle] drops them once the new picture is there.
     */
    var held by mutableStateOf<List<Live>>(emptyList())
        private set
    var version by mutableIntStateOf(0)
        private set

    class Live(val erasing: Boolean, val sizeIndex: Int, var xs: FloatArray = FloatArray(INITIAL_POINTS), var ys: FloatArray = FloatArray(INITIAL_POINTS), var count: Int = 0) {
        fun add(x: Float, y: Float) {
            if (count == xs.size) {
                xs = xs.copyOf(count * 2)
                ys = ys.copyOf(count * 2)
            }
            xs[count] = x
            ys[count] = y
            count++
        }
    }

    /** Where the picture of [width] x [height] pixels is drawn, in the stage's pixels; null before the stage has a size. */
    fun pictureRect(width: Int, height: Int): Rect? {
        if (stage.width == 0 || stage.height == 0 || width == 0 || height == 0) return null
        val fit = min(stage.width.toFloat() / width, stage.height.toFloat() / height) * zoom
        val size = Size(width * fit, height * fit)
        val center = Offset(stage.width / 2f, stage.height / 2f) + pan
        return Rect(center - Offset(size.width / 2f, size.height / 2f), size)
    }

    fun begin(position: Offset, width: Int, height: Int, erasing: Boolean, sizeIndex: Int) {
        val rect = pictureRect(width, height) ?: return
        val start = fraction(position, rect)
        live = Live(erasing, sizeIndex).also { it.add(start.x, start.y) }
        version++
    }

    fun extend(position: Offset, width: Int, height: Int) {
        val rect = pictureRect(width, height) ?: return
        val current = live ?: return
        val point = fraction(position, rect)
        if (current.count > 0 && current.xs[current.count - 1] == point.x && current.ys[current.count - 1] == point.y) return
        current.add(point.x, point.y)
        version++
    }

    /** The finished path, or null when there is none. It is no longer [live]; [hold] keeps it drawn until the picture catches up. */
    fun finish(): Live? {
        val current = live ?: return null
        live = null
        version++
        return current.takeIf { it.count > 0 }
    }

    fun hold(path: Live) {
        held = held + path
    }

    fun settle() {
        if (held.isEmpty()) return
        held = emptyList()
        version++
    }

    fun cancelStroke() {
        live = null
        version++
    }

    /** Pinch and pan about [centroid] (stage pixels), clamped so the picture always covers the stage's middle. */
    fun transform(zoomChange: Float, panChange: Offset, centroid: Offset, width: Int, height: Int) {
        val before = pictureRect(width, height) ?: return
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val factor = newZoom / zoom
        val center = before.center
        val newCenter = centroid + (center - centroid) * factor + panChange
        zoom = newZoom
        pan = newCenter - Offset(stage.width / 2f, stage.height / 2f)
        clampPan(width, height)
    }

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }

    private fun clampPan(width: Int, height: Int) {
        val rect = pictureRect(width, height) ?: return
        val limitX = max(0f, (rect.width - stage.width) / 2f)
        val limitY = max(0f, (rect.height - stage.height) / 2f)
        pan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
    }

    private fun fraction(position: Offset, rect: Rect): Offset {
        val longSide = max(rect.width, rect.height)
        return Offset((position.x - rect.center.x) / longSide, (position.y - rect.center.y) / longSide)
    }

    private companion object {
        const val MAX_ZOOM = 5f
        const val INITIAL_POINTS = 64
    }
}
