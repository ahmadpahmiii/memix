package app.memix.feature.photoeditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.memix.core.model.project.CutOutCatalog
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.Stroke
import app.memix.core.model.project.StrokeMode

/**
 * What the cut-out screen has changed so far (spec docs/ux/specs/P2-07-background-removal.md): refine strokes and softness, with the
 * screen's own undo and redo. Nothing here is in the project until Apply.
 */
@Stable
internal class CutOutEdits(initialStrokes: List<Stroke>, initialSoftness: Float) {
    private data class Snapshot(val strokes: List<Stroke>, val softness: Float)

    var strokes by mutableStateOf(initialStrokes)
        private set
    var softness by mutableStateOf(initialSoftness)
        private set

    private val undone = ArrayList<Snapshot>()
    private val redone = ArrayList<Snapshot>()

    // Counted so Undo and Redo buttons recompose when a step is taken.
    private var steps by mutableIntStateOf(0)

    val canUndo: Boolean get() = steps.let { undone.isNotEmpty() }
    val canRedo: Boolean get() = steps.let { redone.isNotEmpty() }
    val pointCount: Int get() = strokes.sumOf { it.pointCount }

    /**
     * One undo step: a finished finger path ([count] points of [xs] and [ys], fractions of the picture's longer side) as a stroke of the
     * chosen brush, simplified and cut off at the point cap ([CutOutCatalog.MAX_POINTS]) for the points already stored.
     */
    fun addPath(erasing: Boolean, sizeIndex: Int, xs: FloatArray, ys: FloatArray, count: Int): PathAdd {
        val room = (CutOutCatalog.MAX_POINTS - pointCount).coerceAtLeast(0)
        val points = DrawCatalog.simplify(xs, ys, count)
        val stroke = DrawCatalog.strokeOf(
            mode = if (erasing) StrokeMode.ERASE else StrokeMode.DRAW,
            color = null,
            widthS = CutOutCatalog.BRUSH_SIZES[sizeIndex],
            points = points,
            maxPoints = room,
        )
        if (stroke.pointCount == 0) return PathAdd.NOTHING
        record()
        strokes = strokes + stroke
        return if (points.xs.size > room && stroke.pointCount == room) PathAdd.CUT_OFF else PathAdd.WHOLE
    }

    fun changeSoftness(value: Float) {
        if (value == softness) return
        record()
        softness = value
    }

    fun undo() {
        val last = undone.removeLastOrNull() ?: return
        redone.add(Snapshot(strokes, softness))
        strokes = last.strokes
        softness = last.softness
        steps++
    }

    fun redo() {
        val next = redone.removeLastOrNull() ?: return
        undone.add(Snapshot(strokes, softness))
        strokes = next.strokes
        softness = next.softness
        steps++
    }

    private fun record() {
        undone.add(Snapshot(strokes, softness))
        redone.clear()
        steps++
    }
}

/** What became of a finished path: all of it kept, kept up to the point cap, or (the cap was reached already) dropped. */
internal enum class PathAdd { WHOLE, CUT_OFF, NOTHING }
