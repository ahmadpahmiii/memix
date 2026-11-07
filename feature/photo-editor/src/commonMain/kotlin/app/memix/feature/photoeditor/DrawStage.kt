package app.memix.feature.photoeditor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.DrawCatalog
import app.memix.core.model.project.StrokeMode
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.scene.LiveStroke
import kotlin.math.min
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_draw_mode
import org.jetbrains.compose.resources.stringResource

/*
 * Draw mode on the stage (spec docs/ux/specs/P2-05-draw-and-censor.md → Draw). A one-finger drag draws and never moves a layer;
 * other fingers are ignored. The stroke is a [LiveStroke] that the scene draws while the finger is down, and it is recorded once,
 * on release, as one undo step.
 */

/** The stroke the stage is drawing, or the one just released that the project doesn't show yet. */
internal class LiveStrokeHolder {
    var stroke by mutableStateOf<LiveStroke?>(null)
    var fingerDown by mutableStateOf(false)
}

// The canvas as one node while drawing (spec → Accessibility): the layers can't be reached from here.
@Composable
internal fun BoxScope.DrawCanvasNode() {
    val label = stringResource(Res.string.a11y_draw_mode)
    Box(Modifier.matchParentSize().semantics { contentDescription = label })
}

@Composable
internal fun BoxScope.DrawGestureLayer(state: PhotoEditorUiState, stage: LayerStageState, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    val currentState by rememberUpdatedState(state)
    val currentOnIntent by rememberUpdatedState(onIntent)
    // System back during a stroke cancels it; the next back leaves the mode (spec → Back order).
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = live.strokes.fingerDown,
        onBackCompleted = {
            live.cancelStroke()
            onIntent(PhotoEditorIntent.StrokeCancelled)
        },
    )
    Box(
        Modifier.matchParentSize().pointerInput(stage, live) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val started = currentState.startStroke(stage.frame) ?: return@awaitEachGesture
                val stroke = started.stroke
                live.startStroke(stroke)
                currentOnIntent(PhotoEditorIntent.StrokeStarted)
                stroke.addAt(down.position, started)
                down.consume()
                var lifted = false
                while (!lifted && live.strokes.stroke === stroke) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    // Only the first finger counts: a second one or a palm never moves, scales or ends the stroke.
                    change.historical.forEach { stroke.addAt(it.position, started) }
                    stroke.addAt(change.position, started)
                    change.consume()
                    lifted = !change.pressed
                }
                if (lifted && live.strokes.stroke === stroke) {
                    live.releaseStroke()
                    currentOnIntent(
                        PhotoEditorIntent.StrokeFinished(stroke.layerId, started.erasing, started.colorIndex, stroke.widthS, stroke.xsCopy(), stroke.ysCopy(), stroke.count),
                    )
                } else {
                    live.cancelStroke()
                    currentOnIntent(PhotoEditorIntent.StrokeCancelled)
                }
            }
        },
    )
}

/** What a stroke needs from the editor's state when the finger goes down: the brush, the target layer and the canvas it maps to. */
private class StrokeStart(
    val stroke: LiveStroke,
    val erasing: Boolean,
    val colorIndex: Int,
    val frame: CanvasFrameRect,
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
)

private fun PhotoEditorUiState.startStroke(frame: CanvasFrameRect): StrokeStart? {
    val canvas = canvas ?: return null
    val draw = draw ?: return null
    if (frame.isEmpty) return null
    val layerId = selectedLayer?.takeIf { it.kind == LayerKind.DRAWING }?.id ?: draw.newLayerId
    val mode = if (brush.erasing) StrokeMode.ERASE else StrokeMode.DRAW
    val color = if (brush.erasing) null else CaptionCatalog.palette[brush.colorIndex]
    val width = DrawCatalog.BRUSH_SIZES[brush.sizeIndex]
    return StrokeStart(LiveStroke(layerId, mode, color, width), brush.erasing, brush.colorIndex, frame, canvas.widthPx, canvas.heightPx)
}

// The finger at [position] (stage pixels) as a point in S from the canvas center, held inside the canvas.
private fun LiveStroke.addAt(position: Offset, start: StrokeStart) {
    val shortSide = min(start.canvasWidthPx, start.canvasHeightPx).toFloat()
    val x = ((position.x - start.frame.left) / start.frame.width).coerceIn(0f, 1f) * start.canvasWidthPx
    val y = ((position.y - start.frame.top) / start.frame.height).coerceIn(0f, 1f) * start.canvasHeightPx
    add((x - start.canvasWidthPx / 2f) / shortSide, (y - start.canvasHeightPx / 2f) / shortSide)
}
