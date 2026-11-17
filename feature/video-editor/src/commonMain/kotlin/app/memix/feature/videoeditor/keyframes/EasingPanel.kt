package app.memix.feature.videoeditor.keyframes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.Easing
import app.memix.core.model.project.KeyframeCatalog
import app.memix.feature.videoeditor.KeyframeBarUi
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.easing_hint
import memix.feature.video_editor.generated.resources.easing_hold
import memix.feature.video_editor.generated.resources.easing_in
import memix.feature.video_editor.generated.resources.easing_linear
import memix.feature.video_editor.generated.resources.easing_out
import memix.feature.video_editor.generated.resources.easing_smooth
import memix.feature.video_editor.generated.resources.keyframe_easing
import memix.feature.video_editor.generated.resources.keyframe_note
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Easing panel (spec P4-02b → The Easing panel): five tiles, each a curve drawn from the same control points the engine evaluates,
 * for the keyframe the bar has selected. A tile that changes the easing is one undo step, and every tap plays the segment once. The
 * tiles keep the last choice while the panel slides away.
 */
@Composable
internal fun EasingPanel(bar: KeyframeBarUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    ToolPanel(
        visible = bar?.easingOpen == true,
        title = stringResource(Res.string.keyframe_easing),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseEasingPanel) },
        modifier = modifier,
    ) {
        val current = bar?.selected?.easing
        Row(Modifier.selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            EASINGS.forEach { (easing, label) ->
                OptionTile(stringResource(label), current == easing, { onIntent(VideoEditorIntent.KeyframeEasingChosen(easing)) }, OptionTileLayout.STACKED) { tint ->
                    EasingCurve(easing, tint)
                }
            }
        }
        Spacer(Modifier.height(MemixSpacing.space4))
        Text(stringResource(Res.string.easing_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
    }
}

/** The note at the top of the Position, Opacity and Volume panels of an item that has keyframes (spec → Value panels while the item has keyframes). */
@Composable
internal fun KeyframeNote() {
    // The panel's content is already spaced `space-4` apart, so the note adds no gap of its own.
    Text(stringResource(Res.string.keyframe_note), MemixTheme.type.label, color = MemixColors.textSecondary)
}

/**
 * [easing] as a curve in a 20 × 20 dp box, 2 dp stroke and flat caps, rising from the lower left to the upper right: a Bézier through the
 * catalog's control points (Smooth, Ease in, Ease out), a straight line (Linear), or a step (Hold). Layout math, not an icon file.
 */
@Composable
private fun EasingCurve(easing: Easing, color: Color) {
    val stroke = MemixStroke.strokeSelection
    val path = remember(easing) { Path() }
    Canvas(Modifier.size(CURVE_SIZE)) {
        val width = size.width
        val height = size.height
        path.reset()
        path.moveTo(0f, height)
        val points = KeyframeCatalog.controlPoints(easing)
        when {
            points != null -> path.cubicTo(points[0] * width, height - points[1] * height, points[2] * width, height - points[3] * height, width, 0f)
            easing == Easing.LINEAR -> path.lineTo(width, 0f)
            else -> {
                path.lineTo(width * HOLD_JUMP, height)
                path.lineTo(width * HOLD_JUMP, 0f)
                path.lineTo(width, 0f)
            }
        }
        drawPath(path, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    }
}

/** The tile's and the screen reader's name of [easing]. */
internal fun easingNameOf(easing: Easing): StringResource = when (easing) {
    Easing.SMOOTH -> Res.string.easing_smooth
    Easing.LINEAR -> Res.string.easing_linear
    Easing.EASE_IN -> Res.string.easing_in
    Easing.EASE_OUT -> Res.string.easing_out
    Easing.HOLD -> Res.string.easing_hold
}

private val CURVE_SIZE = 20.dp

/** Where a Hold's step stands in its picture: late, since the item stays until the next keyframe. */
private const val HOLD_JUMP = 0.75f

private val EASINGS: List<Pair<Easing, StringResource>> = Easing.entries.map { it to easingNameOf(it) }
