package app.memix.feature.photoeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.Canvas as ProjectCanvas
import app.memix.core.model.project.CensorCatalog
import app.memix.core.model.project.CensorMode
import app.memix.core.model.project.CensorShape
import app.memix.core.model.project.Transform
import app.memix.core.ui.layers.LayerBox
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.Readout
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import app.memix.core.ui.scene.CensorLive
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size_value
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.censor_bar
import memix.feature.photo_editor.generated.resources.censor_blur
import memix.feature.photo_editor.generated.resources.censor_ellipse
import memix.feature.photo_editor.generated.resources.censor_mode
import memix.feature.photo_editor.generated.resources.censor_pixelate
import memix.feature.photo_editor.generated.resources.censor_rect
import memix.feature.photo_editor.generated.resources.censor_shape
import memix.feature.photo_editor.generated.resources.censor_strength
import memix.feature.photo_editor.generated.resources.censor_title
import memix.feature.photo_editor.generated.resources.position_height
import memix.feature.photo_editor.generated.resources.position_height_less
import memix.feature.photo_editor.generated.resources.position_height_more
import memix.feature.photo_editor.generated.resources.position_width
import memix.feature.photo_editor.generated.resources.position_width_less
import memix.feature.photo_editor.generated.resources.position_width_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * A censor's editor pieces (spec docs/ux/specs/P2-05-draw-and-censor.md → The censor layer): its panel (Mode, Shape, Strength), the
 * Position panel's Width and Height rows, and the four edge handles on the stage.
 */

/** The Censor panel: Mode and Shape tiles drawn from data, and a Strength slider (not for Black bar). Every tap is one undo step. */
@Composable
internal fun CensorPanel(state: PhotoEditorUiState, live: PhotoLive, maxHeight: androidx.compose.ui.unit.Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val layer = state.selectedLayer?.takeIf { it.censor != null }
    val censor = layer?.censor
    ToolPanel(
        state.panel == PhotoPanel.CENSOR && layer != null,
        stringResource(Res.string.censor_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
    ) {
        if (layer == null || censor == null) return@ToolPanel
        ChoiceRow(stringResource(Res.string.censor_mode)) {
            CensorMode.entries.forEach { mode ->
                OptionTile(stringResource(mode.label()), selected = mode == censor.mode, onClick = { onIntent(PhotoEditorIntent.SetCensorMode(mode)) }, layout = OptionTileLayout.STACKED) { tint ->
                    ModePicture(mode, tint)
                }
            }
        }
        if (censor.mode != CensorMode.BAR) {
            val strength = live.censorOf(layer.id)?.strength ?: censor.strength
            Slider(
                label = stringResource(Res.string.censor_strength),
                value = strength,
                onValueChange = { live.setCensorStrength(layer.id, it) },
                valueText = stringResource(UiRes.string.position_size_value, (strength * PERCENT).roundToInt()),
                defaultValue = CensorCatalog.DEFAULT_STRENGTH,
                onValueChangeFinished = { onIntent(PhotoEditorIntent.SetCensorStrength(layer.id, live.censorOf(layer.id)?.strength ?: censor.strength)) },
            )
        }
        ChoiceRow(stringResource(Res.string.censor_shape)) {
            CensorShape.entries.forEach { shape ->
                OptionTile(stringResource(shape.label()), selected = shape == censor.shape, onClick = { onIntent(PhotoEditorIntent.SetCensorShape(shape)) }, layout = OptionTileLayout.STACKED) { tint ->
                    ShapePicture(shape, tint)
                }
            }
        }
    }
}

private fun CensorMode.label(): StringResource = when (this) {
    CensorMode.BLUR -> Res.string.censor_blur
    CensorMode.PIXELATE -> Res.string.censor_pixelate
    CensorMode.BAR -> Res.string.censor_bar
}

private fun CensorShape.label(): StringResource = when (this) {
    CensorShape.RECT -> Res.string.censor_rect
    CensorShape.ELLIPSE -> Res.string.censor_ellipse
}

// Mode and shape pictures are drawn from data in the 24 dp picture box (20 dp long side, as the ratio shapes), not icon files.
@Composable
private fun ModePicture(mode: CensorMode, tint: Color) {
    if (mode == CensorMode.BLUR) {
        Icon(MemixIcons.Blur, contentDescription = null, tint = tint)
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * PICTURE_SHARE
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        if (mode == CensorMode.BAR) {
            drawRoundRect(tint, origin, Size(side, side), CornerRadius(1f))
            return@Canvas
        }
        // Pixelate: a 3 x 3 checker of squares.
        val cell = side / 3f
        for (row in 0 until 3) for (column in 0 until 3) if ((row + column) % 2 == 0) drawRect(tint, origin + Offset(column * cell, row * cell), Size(cell, cell))
    }
}

@Composable
private fun ShapePicture(shape: CensorShape, tint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val long = size.minDimension * PICTURE_SHARE
        val box = Size(long, long * 0.7f)
        val origin = Offset((size.width - box.width) / 2f, (size.height - box.height) / 2f)
        if (shape == CensorShape.ELLIPSE) drawOval(tint, origin, box) else drawRoundRect(tint, origin, box, CornerRadius(1f))
    }
}

/** The Position panel's Width and Height rows (a minus and a plus, 2% of S a tap, a hold repeats): the single-pointer route for the edge handles. */
@Composable
internal fun CensorSizeRows(layer: SceneLayerUi, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    val censor = layer.censor ?: return
    val shown = live.censorOf(layer.id)
    val width = shown?.widthS ?: censor.widthS
    val height = shown?.heightS ?: censor.heightS
    fun held(widthSteps: Int, heightSteps: Int) = live.setCensorSize(
        layer.id,
        (censor.widthS + widthSteps * CensorCatalog.SIZE_STEP_S).coerceIn(CensorCatalog.SIZE_RANGE),
        (censor.heightS + heightSteps * CensorCatalog.SIZE_STEP_S).coerceIn(CensorCatalog.SIZE_RANGE),
    )
    SizeRow(Res.string.position_width, Res.string.position_width_less, Res.string.position_width_more, width, { held(-it, 0) }, { held(it, 0) },
        { onIntent(PhotoEditorIntent.CensorSizeStepped(layer.id, -it, 0)) }, { onIntent(PhotoEditorIntent.CensorSizeStepped(layer.id, it, 0)) })
    SizeRow(Res.string.position_height, Res.string.position_height_less, Res.string.position_height_more, height, { held(0, -it) }, { held(0, it) },
        { onIntent(PhotoEditorIntent.CensorSizeStepped(layer.id, 0, -it)) }, { onIntent(PhotoEditorIntent.CensorSizeStepped(layer.id, 0, it)) })
}

@Composable
private fun SizeRow(
    label: StringResource,
    less: StringResource,
    more: StringResource,
    valueS: Float,
    onLessHeld: (Int) -> Unit,
    onMoreHeld: (Int) -> Unit,
    onLess: (Int) -> Unit,
    onMore: (Int) -> Unit,
) {
    val percent = (valueS * PERCENT).roundToInt()
    StepperRow(stringResource(label), spokenState = stringResource(UiRes.string.position_size_value, percent)) {
        RepeatingButton(MemixIcons.Minus, stringResource(less), onHeld = onLessHeld, onStep = onLess)
        Readout(stringResource(UiRes.string.position_size_value, percent))
        RepeatingButton(MemixIcons.Plus, stringResource(more), onHeld = onMoreHeld, onStep = onMore)
    }
}

/** Which edge a handle sits on, and the direction (along the layer's own axes) a drag outward grows the censor. */
private enum class Edge(val dx: Int, val dy: Int) { RIGHT(1, 0), LEFT(-1, 0), BOTTOM(0, 1), TOP(0, -1) }

/**
 * The four edge handles of a selected, unlocked censor (spec → Handles and gestures): a `selection` bar in the middle of each edge,
 * a 48 dp target; a drag resizes that side along the layer's own axis and the opposite side stays. Live through [live] (the scene
 * and the frame follow without recomposing), one step on release. Drag-only, so hidden from accessibility: the Position panel's Width
 * and Height rows are the route.
 */
@Composable
internal fun BoxScope.CensorHandles(layer: SceneLayerUi, canvas: ProjectCanvas, stage: LayerStageState, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    if (layer.censor == null) return
    Edge.entries.forEach { edge -> EdgeHandle(edge, layer, canvas, stage, live, onIntent) }
}

@Composable
private fun BoxScope.EdgeHandle(edge: Edge, layer: SceneLayerUi, canvas: ProjectCanvas, stage: LayerStageState, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    val censor = layer.censor ?: return
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val outsetPx = with(density) { app.memix.core.designsystem.MemixSpacing.space1.toPx() }
    val currentLayer by rememberUpdatedState(layer)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val shortSide = min(canvas.widthPx, canvas.heightPx).toFloat()
    fun shownBox(): LayerBox {
        val shown = live.censorOf(layer.id)
        return LayerBox.of(
            (shown?.widthS ?: censor.widthS) * shortSide, (shown?.heightS ?: censor.heightS) * shortSide,
            live.transformOf(layer.id) ?: layer.transform, stage.frame, canvas.widthPx,
        )
    }
    // Each target is capped at a third of the box's side on screen (24 dp at least), so the middle of the box stays a move area and
    // neighbouring handles never overlap; the Width and Height rows are the route for boxes too small to grab.
    val minTargetPx = with(density) { (MemixSize.touchTarget / 2).toPx() }
    fun targetOf(box: LayerBox): Size = Size(
        (box.halfWidth * 2f / 3f).coerceIn(minTargetPx, targetPx),
        (box.halfHeight * 2f / 3f).coerceIn(minTargetPx, targetPx),
    )
    Box(
        Modifier
            .zIndex(1f)
            .offset {
                val box = shownBox()
                val at = box.corner(edge.dx, edge.dy, outsetPx)
                val target = targetOf(box)
                IntOffset((at.x - target.width / 2).roundToInt(), (at.y - target.height / 2).roundToInt())
            }
            .layout { measurable, _ ->
                val target = targetOf(shownBox())
                val placeable = measurable.measure(Constraints.fixed(target.width.roundToInt(), target.height.roundToInt()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer { rotationZ = (live.transformOf(layer.id) ?: currentLayer.transform).rotationDegrees }
            .clearAndSetSemantics { }
            .pointerInput(layer.id, edge) {
                var total = Offset.Zero
                var start = currentLayer
                detectDragGestures(
                    onDragStart = { total = Offset.Zero; start = currentLayer },
                    onDragEnd = {
                        val result = live.censorOf(start.id)
                        val transform = live.transformOf(start.id)
                        if (result?.widthS != null && result.heightS != null && transform != null) {
                            currentOnIntent(PhotoEditorIntent.CensorResized(start.id, result.widthS!!, result.heightS!!, transform))
                        } else {
                            live.cancelCensor()
                        }
                    },
                    onDragCancel = { live.cancelCensor() },
                ) { change, drag ->
                    change.consume()
                    total += drag
                    resize(edge, start, canvas, stage, total, live)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val length = MemixSize.edgeHandleLength
        val thickness = MemixSize.edgeHandleThickness
        val horizontal = edge.dy != 0
        Box(
            Modifier
                .size(if (horizontal) length else thickness, if (horizontal) thickness else length)
                .drawBehind {
                    val hairline = MemixStroke.strokeHairline.toPx()
                    val radius = CornerRadius(1f)
                    drawRoundRect(MemixColors.onPrimary, Offset(-hairline, -hairline), Size(size.width + 2 * hairline, size.height + 2 * hairline), radius)
                    drawRoundRect(MemixColors.selection, Offset.Zero, size, radius)
                },
        )
    }
}

// The size and place the edge's drag [total] (stage pixels) makes from [start]: the dragged side follows the finger along the layer's own
// axis, the opposite side stays, and the size stops at its limits.
private fun resize(edge: Edge, start: SceneLayerUi, canvas: ProjectCanvas, stage: LayerStageState, total: Offset, live: PhotoLive) {
    val censor = start.censor ?: return
    val pixelScale = stage.frame.width / canvas.widthPx
    if (pixelScale <= 0f) return
    val shortSide = min(canvas.widthPx, canvas.heightPx).toFloat()
    val transform = start.transform
    val radians = transform.rotationDegrees * (kotlin.math.PI.toFloat() / 180f)
    val localX = (total.x * cos(-radians) - total.y * sin(-radians)) / pixelScale
    val localY = (total.x * sin(-radians) + total.y * cos(-radians)) / pixelScale
    val unit = shortSide * transform.scale
    var widthS = censor.widthS
    var heightS = censor.heightS
    var shiftX = 0f
    var shiftY = 0f
    if (edge.dx != 0) {
        widthS = ((censor.widthS * unit + edge.dx * localX) / unit).coerceIn(CensorCatalog.SIZE_RANGE)
        shiftX = edge.dx * (widthS - censor.widthS) * unit / 2f
    } else {
        heightS = ((censor.heightS * unit + edge.dy * localY) / unit).coerceIn(CensorCatalog.SIZE_RANGE)
        shiftY = edge.dy * (heightS - censor.heightS) * unit / 2f
    }
    val centerX = transform.centerX * canvas.widthPx + shiftX * cos(radians) - shiftY * sin(radians)
    val centerY = transform.centerY * canvas.heightPx + shiftX * sin(radians) + shiftY * cos(radians)
    live.setCensorSize(start.id, widthS, heightS)
    live.settle(start.id, Transform(centerX / canvas.widthPx, centerY / canvas.heightPx, transform.scale, transform.rotationDegrees))
}

/** [layer] with the size and place the handles have now, for the frame and the other handles (read while composing the selection overlay). */
internal fun SceneLayerUi.shownWith(live: PhotoLive, canvas: ProjectCanvas): SceneLayerUi {
    val censor = censor ?: return this
    val shortSide = min(canvas.widthPx, canvas.heightPx).toFloat()
    val shown = live.censorOf(id)
    return copy(
        boxWidthPx = (shown?.widthS ?: censor.widthS) * shortSide,
        boxHeightPx = (shown?.heightS ?: censor.heightS) * shortSide,
        transform = live.transformOf(id) ?: transform,
    )
}

private const val PERCENT = 100f
private const val PICTURE_SHARE = 20f / 24f
