package app.memix.feature.photoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.FormatRect
import app.memix.core.model.project.PanelCell
import app.memix.core.model.project.PanelFamily
import app.memix.core.model.project.PanelLayouts
import app.memix.core.model.project.PanelsFormat
import app.memix.core.model.project.PanelsGeometry
import app.memix.core.model.project.PhotoFormat
import app.memix.core.model.project.PhotoFormatCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.clampedToCover
import app.memix.core.model.project.dividerPositions
import app.memix.core.model.project.geometry
import app.memix.core.model.project.pictureRect
import app.memix.core.model.project.spec
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.PositionStep
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import app.memix.core.ui.panels.Readout
import app.memix.core.ui.panels.keptInViewWhen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_bigger
import memix.core.ui.generated.resources.position_center
import memix.core.ui.generated.resources.position_move
import memix.core.ui.generated.resources.position_title
import memix.core.ui.generated.resources.position_move_down
import memix.core.ui.generated.resources.position_move_left
import memix.core.ui.generated.resources.position_move_right
import memix.core.ui.generated.resources.position_move_up
import memix.core.ui.generated.resources.position_size
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.position_smaller
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_divider
import memix.feature.photo_editor.generated.resources.a11y_panel_cell
import memix.feature.photo_editor.generated.resources.a11y_panel_empty
import memix.feature.photo_editor.generated.resources.divider_even
import memix.feature.photo_editor.generated.resources.divider_title
import memix.feature.photo_editor.generated.resources.layer_meta_missing
import memix.feature.photo_editor.generated.resources.panels_columns
import memix.feature.photo_editor.generated.resources.panels_count
import memix.feature.photo_editor.generated.resources.panels_even
import memix.feature.photo_editor.generated.resources.panels_grid
import memix.feature.photo_editor.generated.resources.panels_layout_label
import memix.feature.photo_editor.generated.resources.panels_rows
import memix.feature.photo_editor.generated.resources.tool_add_photo
import memix.feature.photo_editor.generated.resources.tool_panels_layout
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The Panels format's editor pieces (spec docs/ux/specs/P2-02-meme-formats.md → Panels): the pick tiles of empty cells, the
 * selection ring and the selected divider's gutter, the divider handles, the cell gestures, and the Layout, Divider and cell
 * Position panels. Cells and dividers are derived from the format ([geometry]); a gesture keeps its in-between view in
 * [PhotoLive], read while drawing, and commits once on release.
 */

/** The Panels format of [scene], or null. */
internal fun SceneUi.panels(): PanelsFormat? = format as? PanelsFormat

// A rectangle in canvas pixels as stage pixels.
private fun stageRect(rect: FormatRect, frame: CanvasFrameRect, canvasWidthPx: Int): FormatRect {
    val scale = if (canvasWidthPx > 0) frame.width / canvasWidthPx else 0f
    return FormatRect(frame.left + rect.left * scale, frame.top + rect.top * scale, rect.width * scale, rect.height * scale)
}

private fun geometryOf(format: PhotoFormat?, scene: SceneUi): PanelsGeometry? = (format as? PanelsFormat)?.geometry(PixelSize(scene.canvas.widthPx, scene.canvas.heightPx))

/**
 * Over the scene while the format is Panels: a pick tile in each empty (or missing) cell, the selected cell's white ring and the
 * selected divider's gutter in the selection line, and each divider's handle. Nothing here is saved or rendered.
 */
@Composable
internal fun BoxScope.PanelsOverlay(state: PhotoEditorUiState, scene: SceneUi, stage: LayerStageState, live: PhotoLive, onIntent: (PhotoEditorIntent) -> Unit) {
    val panels = scene.panels() ?: return
    val canvasWidth = scene.canvas.widthPx
    val density = LocalDensity.current
    val ringPx = with(density) { MemixStroke.strokeSelection.toPx() }
    val hairlinePx = with(density) { MemixStroke.strokeHairline.toPx() }
    // The selected cell's ring and divider gutter: two-tone, read while drawing so a drag keeps them in place.
    Spacer(
        Modifier.matchParentSize().drawBehind {
            val frame = stage.frame
            if (frame.isEmpty) return@drawBehind
            val geometry = geometryOf(live.formatOf() ?: panels, scene) ?: return@drawBehind
            state.selectedCell?.let { index ->
                geometry.cells.getOrNull(index)?.let { cell ->
                    val rect = stageRect(cell, frame, canvasWidth)
                    // A 2 dp white ring inside the cell's edge with a near-black 1 dp edge on both sides, so it reads over the white
                    // gutter as well as over any picture (design review P2-02b M1).
                    fun line(inset: Float, width: Float, color: androidx.compose.ui.graphics.Color) =
                        drawRect(color, Offset(rect.left + inset + width / 2, rect.top + inset + width / 2), Size(rect.width - 2 * inset - width, rect.height - 2 * inset - width), style = Stroke(width))
                    line(0f, hairlinePx, MemixColors.onPrimary)
                    line(hairlinePx, ringPx, MemixColors.selection)
                    line(hairlinePx + ringPx, hairlinePx, MemixColors.onPrimary)
                }
            }
            state.selectedDivider?.let { index ->
                geometry.dividers.firstOrNull { it.index == index }?.let { divider ->
                    val rect = stageRect(divider.gutter, frame, canvasWidth)
                    drawRect(MemixColors.selection, Offset(rect.left, rect.top), Size(rect.width, rect.height))
                    drawRect(MemixColors.onPrimary, Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(hairlinePx))
                }
            }
        },
    )
    PickTiles(state, scene, panels, stage, onIntent)
    val handlesShown = state.selectedLayerId == null && state.panel == null && state.textEntry == null
    if (handlesShown) panels.dividerPositions().indices.forEach { index -> DividerHandle(index, scene, stage, live, canvasWidth, onIntent) }
}

// An empty cell invites a photo: a surface-raised tile with the create icon over "Add photo" (or "File missing"), centered in the
// cell and never larger than it. A tap opens the picker for this cell.
@Composable
private fun BoxScope.PickTiles(state: PhotoEditorUiState, scene: SceneUi, panels: PanelsFormat, stage: LayerStageState, onIntent: (PhotoEditorIntent) -> Unit) {
    val open = panels.cells.withIndex().filter { it.value.source == null || it.index in state.missingCells }
    if (open.isEmpty() || state.textEntry != null) return
    val canvasWidth = scene.canvas.widthPx
    val insetPx = with(LocalDensity.current) { MemixSpacing.space3.roundToPx() }
    val minPx = with(LocalDensity.current) { MemixSize.touchTarget.roundToPx() }
    // A tile with the icon over two lines of label needs about 96 dp at 100% font; bigger with the font.
    val needPx = with(LocalDensity.current) { (COMPACT_BELOW * fontScale.coerceAtLeast(1f)).toPx() }
    val geometryNow = geometryOf(panels, scene)
    val isCompact = { index: Int -> geometryNow?.cells?.getOrNull(index)?.let { stageRect(it, stage.frame, canvasWidth).let { r -> min(r.width, r.height) < needPx } } == true }
    Layout(
        content = { open.forEach { (index, cell) -> PickTile(index, panels, missing = cell.source != null, compact = isCompact(index), onIntent) } },
        modifier = Modifier.matchParentSize(),
    ) { measurables, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {
            val frame = stage.frame
            val geometry = geometryOf(panels, scene) ?: return@layout
            measurables.forEachIndexed { slot, measurable ->
                val rect = geometry.cells.getOrNull(open[slot].index)?.let { stageRect(it, frame, canvasWidth) } ?: return@forEachIndexed
                val availableWidth = (rect.width - 2 * insetPx).roundToInt()
                val availableHeight = (rect.height - 2 * insetPx).roundToInt()
                // At least a touch target, but never larger than the cell.
                val widest = max(min(availableWidth, rect.width.roundToInt()), min(minPx, rect.width.roundToInt())).coerceAtLeast(1)
                val tallest = max(min(availableHeight, rect.height.roundToInt()), min(minPx, rect.height.roundToInt())).coerceAtLeast(1)
                val placeable = measurable.measure(Constraints(minWidth = min(minPx, widest), minHeight = min(minPx, tallest), maxWidth = widest, maxHeight = tallest))
                placeable.place((rect.left + rect.width / 2 - placeable.width / 2).roundToInt(), (rect.top + rect.height / 2 - placeable.height / 2).roundToInt())
            }
        }
    }
}

@Composable
private fun PickTile(index: Int, panels: PanelsFormat, missing: Boolean, compact: Boolean, onIntent: (PhotoEditorIntent) -> Unit) {
    val label = stringResource(if (missing) Res.string.layer_meta_missing else Res.string.tool_add_photo)
    val spoken = stringResource(Res.string.a11y_panel_cell, index + 1, panels.cells.size) + " " + stringResource(Res.string.a11y_panel_empty) + " " + label
    Column(
        Modifier
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .clickable(onClick = { onIntent(PhotoEditorIntent.TapCell(index)) })
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.textSecondary)
        // In a cell too small for two lines the plus stands alone; its full label is the spoken name (design review M2).
        if (!compact) Text(label, MemixTheme.type.label, color = MemixColors.text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * A divider's handle: a white pill (5 dp by 32 dp, turned along the gutter) with an `on-primary` hairline edge in a 48 dp target
 * centered on the gutter, above every layer. A drag moves it along its axis (snapping to even within 3% with one tick, stopping
 * at the limits) and commits once on release; a tap selects it and opens the Divider panel. A screen reader gets a slider.
 */
@Composable
private fun BoxScope.DividerHandle(index: Int, scene: SceneUi, stage: LayerStageState, live: PhotoLive, canvasWidthPx: Int, onIntent: (PhotoEditorIntent) -> Unit) {
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val pillLong = with(density) { PILL_LONG.toPx() }
    val pillShort = with(density) { PILL_SHORT.toPx() }
    val hairline = with(density) { MemixStroke.strokeHairline.toPx() }
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentScene by rememberUpdatedState(scene)
    val panels = scene.panels() ?: return
    val divider = geometryOf(panels, scene)?.dividers?.firstOrNull { it.index == index } ?: return
    val percent = (divider.position * 100).roundToInt()
    val spoken = stringResource(Res.string.a11y_divider, index + 1, panels.dividerPositions().size, percent)
    Box(
        Modifier
            .zIndex(1f)
            .offset {
                val shown = geometryOf(live.formatOf() ?: currentScene.format, currentScene)?.dividers?.firstOrNull { it.index == index } ?: divider
                val rect = stageRect(shown.gutter, stage.frame, canvasWidthPx)
                IntOffset((rect.left + rect.width / 2 - targetPx / 2).roundToInt(), (rect.top + rect.height / 2 - targetPx / 2).roundToInt())
            }
            .size(MemixSize.touchTarget)
            .sliderSemantics(spoken, divider.position, divider.min, divider.max) { currentOnIntent(PhotoEditorIntent.DividerMoved(index, it)) }
            .pointerInput(index) { detectTapGestures(onTap = { currentOnIntent(PhotoEditorIntent.SelectDivider(index)) }) }
            .pointerInput(index) {
                // The finger's own position, kept apart from the shown one: the snap holds the divider at even while the finger
                // is within reach of it, and lets go once the finger has moved on (accumulating on the snapped value would stick).
                var finger = divider.position
                var position = divider.position
                var snapped = false
                detectDragGestures(
                    onDragStart = {
                        finger = (live.formatOf() ?: currentScene.format)?.let { geometryOf(it, currentScene)?.dividers?.firstOrNull { d -> d.index == index }?.position } ?: divider.position
                        position = finger
                        snapped = false
                    },
                    onDragEnd = { currentOnIntent(PhotoEditorIntent.DividerMoved(index, position)) },
                    onDragCancel = { live.releasePanels() },
                ) { change, drag ->
                    change.consume()
                    val now = geometryOf(currentScene.format, currentScene)?.dividers?.firstOrNull { it.index == index } ?: return@detectDragGestures
                    val pixelScale = if (canvasWidthPx > 0) stage.frame.width / canvasWidthPx else return@detectDragGestures
                    val delta = (if (now.vertical) drag.x else drag.y) / pixelScale / now.extentLength
                    finger = (finger + delta).coerceIn(now.min, now.max)
                    val near = abs(finger - now.even) <= PhotoFormatCatalog.DIVIDER_SNAP_TO_EVEN_WITHIN
                    position = if (near) now.even else finger
                    if (near && !snapped) currentHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    snapped = near
                    live.setDivider(index, position)
                }
            }
            .drawBehind {
                val vertical = divider.vertical
                val width = if (vertical) pillShort else pillLong
                val height = if (vertical) pillLong else pillShort
                val topLeft = Offset((size.width - width) / 2, (size.height - height) / 2)
                drawRoundRect(MemixColors.onPrimary, topLeft - Offset(hairline, hairline), Size(width + 2 * hairline, height + 2 * hairline), CornerRadius(width))
                drawRoundRect(MemixColors.selection, topLeft, Size(width, height), CornerRadius(width / 2))
            },
    )
}

private val COMPACT_BELOW = MemixSize.touchTarget * 2f
private val PILL_LONG = MemixSize.touchTarget * (32f / 48f)
private val PILL_SHORT = MemixSize.iconSmall * (5f / 16f)

// A divider is an adjustable node for a screen reader: its percent is the value, a swipe or key press moves it by 2% of its extent.
private fun Modifier.sliderSemantics(description: String, position: Float, min: Float, max: Float, onSet: (Float) -> Unit): Modifier =
    this.clearAndSetSemantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(position, min..max)
        setProgress { target -> onSet(target.coerceIn(min, max)); true }
    }

// ---- Cell gestures --------------------------------------------------------------------------------------------------------

/** What the cell gestures need from the editor. */
internal class CellGestureHost(
    val scene: () -> SceneUi?,
    val selectedCell: () -> Int?,
    val missingCells: () -> Set<Int>,
    val photoSizeOf: (Int) -> PixelSize?,
    val live: PhotoLive,
    val onIntent: (PhotoEditorIntent) -> Unit,
)

/**
 * Taps, one-finger pans and two-finger zooms on the cells (spec → Canvas touches in Panels), after the layers and the dividers'
 * handles have had the touch: a tap on an empty cell opens the picker, a tap on a filled one selects it, a drag in a filled cell
 * selects it and pans its photo 1:1, two fingers anywhere with a cell selected zoom it. The in-between view lives in [PhotoLive]
 * and is committed on release.
 */
internal suspend fun PointerInputScope.detectCellGestures(stage: LayerStageState, host: CellGestureHost, haptics: () -> HapticFeedback?) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = true)
    val scene = host.scene() ?: return@awaitEachGesture
    val panels = scene.panels() ?: return@awaitEachGesture
    val frame = stage.frame
    if (frame.isEmpty || scene.canvas.widthPx <= 0) return@awaitEachGesture
    val geometry = geometryOf(panels, scene) ?: return@awaitEachGesture
    val pixelScale = frame.width / scene.canvas.widthPx
    val at = Offset((down.position.x - frame.left) / pixelScale, (down.position.y - frame.top) / pixelScale)
    val hit = geometry.cells.indexOfFirst { at.x in it.left..it.right && at.y in it.top..it.bottom }
    val hitCell = panels.cells.getOrNull(hit)
    val hitFilled = hitCell?.source != null && hit !in host.missingCells()
    val selected = host.selectedCell()
    // A touch on an empty cell is a tap for the picker; nothing else.
    if (hit >= 0 && !hitFilled) {
        down.consume()
        if (!awaitUpWithinSlop(down)) return@awaitEachGesture
        host.onIntent(PhotoEditorIntent.TapCell(hit))
        return@awaitEachGesture
    }
    if (hit < 0 && selected == null) return@awaitEachGesture
    val target = if (hitFilled) hit else selected ?: return@awaitEachGesture
    val cell0 = panels.cells.getOrNull(target) ?: return@awaitEachGesture
    val photo = host.photoSizeOf(target)
    val rect = geometry.cells.getOrNull(target) ?: return@awaitEachGesture
    if (hit >= 0) down.consume()
    // Wait for the slop, a second finger or the lift.
    var moved = Offset.Zero
    var panning = false
    var view = cell0
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) {
            if (!panning && hitFilled) host.onIntent(PhotoEditorIntent.TapCell(hit))
            break
        }
        if (pressed.size >= 2 || panning) {
            if (photo == null) break
            if (!panning) {
                panning = true
                if (selected != target) host.onIntent(PhotoEditorIntent.TapCell(target))
            }
            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            val picture = view.pictureRect(rect, photo)
            view = view.copy(
                zoom = (view.zoom * zoom).coerceIn(PhotoFormatCatalog.CELL_ZOOM_RANGE),
                centerX = view.centerX - pan.x / pixelScale / picture.width,
                centerY = view.centerY - pan.y / pixelScale / picture.height,
            ).clampedToCover(rect, photo)
            host.live.setCell(target, view)
            event.changes.forEach { if (it.pressed || it.positionChanged()) it.consume() }
            continue
        }
        val change = pressed.first()
        moved += change.position - change.previousPosition
        if (moved.getDistance() > viewConfiguration.touchSlop && hitFilled) {
            panning = true
            if (selected != target) host.onIntent(PhotoEditorIntent.TapCell(target))
        }
        // A touch in a gutter or outside the cells stays unconsumed, so a tap there still clears the selection.
        if (hit >= 0) change.consume()
    }
    if (panning && photo != null) {
        host.live.setCell(target, view)
        host.onIntent(PhotoEditorIntent.CellViewReleased(target, view.zoom, view.centerX, view.centerY))
    }
}

private suspend fun AwaitPointerEventScope.awaitUpWithinSlop(down: PointerInputChange): Boolean {
    var moved = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        moved += change.position - change.previousPosition
        change.consume()
        if (moved.getDistance() > viewConfiguration.touchSlop) return false
    }
}

private fun PointerInputChange.positionChanged(): Boolean = position != previousPosition

// ---- Panels ---------------------------------------------------------------------------------------------------------------

/**
 * The Layout panel (spec → Layout panel): the number of panels (2 to 6, tiles drawn as the count's cells), the layout family
 * (Grid, Side by side, Stacked: the ones the count offers) and Make panels even. Each tap is one "Layout" step.
 */
@Composable
internal fun LayoutPanel(state: PhotoEditorUiState, maxHeight: androidx.compose.ui.unit.Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val panels = state.format as? PanelsFormat
    val spec = panels?.spec()
    ToolPanel(
        state.panel == PhotoPanel.LAYOUT && spec != null,
        stringResource(Res.string.tool_panels_layout),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
    ) {
        if (spec == null) return@ToolPanel
        ChoiceRow(stringResource(Res.string.panels_count)) {
            for (count in PhotoFormatCatalog.PANELS_MIN_CELLS..PhotoFormatCatalog.PANELS_MAX_CELLS) {
                val selected = count == spec.cellCount
                OptionTile(count.toString(), selected, { onIntent(PhotoEditorIntent.SetPanelCount(count)) }, OptionTileLayout.STACKED, Modifier.keptInViewWhen(selected)) { tint ->
                    LayoutShape(PanelLayouts.of(count, spec.family), tint)
                }
            }
        }
        ChoiceRow(stringResource(Res.string.panels_layout_label)) {
            PanelLayouts.forCount(spec.cellCount).forEach { layout ->
                val selected = layout.id == spec.id
                OptionTile(stringResource(familyLabel(layout.family)), selected, { onIntent(PhotoEditorIntent.SetPanelFamily(layout.family)) }, OptionTileLayout.STACKED, Modifier.keptInViewWhen(selected)) { tint ->
                    LayoutShape(layout, tint)
                }
            }
        }
        Button(stringResource(Res.string.panels_even), { onIntent(PhotoEditorIntent.MakePanelsEven) })
    }
}

private fun familyLabel(family: PanelFamily): StringResource = when (family) {
    PanelFamily.GRID -> Res.string.panels_grid
    PanelFamily.COLUMNS -> Res.string.panels_columns
    PanelFamily.ROWS -> Res.string.panels_rows
}

// A layout drawn as its cells at even dividers: solid rectangles with 2 units between them, in the 24-unit icon box.
@Composable
private fun LayoutShape(layout: app.memix.core.model.project.PanelLayoutSpec, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(MemixSize.iconSmall * 1.5f).drawBehind {
            val unit = size.minDimension / 24f
            val geometry = PanelsFormat(layout.id, layout.evenDividers(), List(layout.cellCount) { PanelCell() }).geometry(PixelSize(1000, 1000)) ?: return@drawBehind
            val scale = 20f * unit / 1000f
            geometry.cells.forEach { cell ->
                // The cells fill 1000 units; the gutter is shown a little wider than the format's, so it reads at 20 dp.
                val gap = unit * 0.75f
                drawRoundRect(
                    color,
                    Offset(2f * unit + cell.left * scale + gap, 2f * unit + cell.top * scale + gap),
                    Size(max(cell.width * scale - 2 * gap, unit), max(cell.height * scale - 2 * gap, unit)),
                    CornerRadius(unit),
                )
            }
        },
    )
}

/** The Divider panel: the two arrows along the divider's axis (2% of its extent, held repeats) and Even (spec → Selected-divider panel). */
@Composable
internal fun DividerPanel(state: PhotoEditorUiState, maxHeight: androidx.compose.ui.unit.Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val index = state.selectedDivider
    val panels = state.format as? PanelsFormat
    val divider = if (index != null && panels != null) panels.geometry(PixelSize(state.canvas?.widthPx ?: 0, state.canvas?.heightPx ?: 0))?.dividers?.firstOrNull { it.index == index } else null
    ToolPanel(
        state.panel == PhotoPanel.DIVIDER && divider != null,
        stringResource(Res.string.divider_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
    ) {
        if (divider == null || index == null) return@ToolPanel
        StepperRow(stringResource(UiRes.string.position_move), spokenState = null) {
            val back = if (divider.vertical) UiRes.string.position_move_left else UiRes.string.position_move_up
            val forward = if (divider.vertical) UiRes.string.position_move_right else UiRes.string.position_move_down
            RepeatingButton(
                MemixIcons.Arrow, stringResource(back),
                onHeld = { onIntent(PhotoEditorIntent.DividerStepHeld(index, -1, it)) },
                onStep = { onIntent(PhotoEditorIntent.DividerStepped(index, -1, it)) },
                turnDegrees = if (divider.vertical) 180f else 270f,
            )
            RepeatingButton(
                MemixIcons.Arrow, stringResource(forward),
                onHeld = { onIntent(PhotoEditorIntent.DividerStepHeld(index, 1, it)) },
                onStep = { onIntent(PhotoEditorIntent.DividerStepped(index, 1, it)) },
                turnDegrees = if (divider.vertical) 0f else 90f,
            )
        }
        Button(stringResource(Res.string.divider_even), { onIntent(PhotoEditorIntent.DividerEven(index)) })
    }
}

/** The cell's Position panel: Size (the zoom ladder), Move and Center; no Rotation (spec → Cell Position panel). */
@Composable
internal fun CellPositionPanel(state: PhotoEditorUiState, maxHeight: androidx.compose.ui.unit.Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val index = state.selectedCell
    val cell = (state.format as? PanelsFormat)?.cells?.getOrNull(index ?: -1)
    ToolPanel(
        state.panel == PhotoPanel.CELL_POSITION && cell != null,
        stringResource(UiRes.string.position_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
    ) {
        if (cell == null || index == null) return@ToolPanel
        val percent = (cell.zoom * 100).roundToInt()
        StepperRow(stringResource(UiRes.string.position_size), spokenState = stringResource(UiRes.string.position_size_value, percent)) {
            IconButton(MemixIcons.Minus, stringResource(UiRes.string.position_smaller), { onIntent(PhotoEditorIntent.CellStepped(index, PositionStep.SMALLER, 1)) })
            Readout(stringResource(UiRes.string.position_size_value, percent))
            IconButton(MemixIcons.Plus, stringResource(UiRes.string.position_bigger), { onIntent(PhotoEditorIntent.CellStepped(index, PositionStep.BIGGER, 1)) })
        }
        StepperRow(stringResource(UiRes.string.position_move), spokenState = null) {
            CellArrow(index, PositionStep.MOVE_LEFT, UiRes.string.position_move_left, 180f, onIntent)
            CellArrow(index, PositionStep.MOVE_UP, UiRes.string.position_move_up, 270f, onIntent)
            CellArrow(index, PositionStep.MOVE_DOWN, UiRes.string.position_move_down, 90f, onIntent)
            CellArrow(index, PositionStep.MOVE_RIGHT, UiRes.string.position_move_right, 0f, onIntent)
        }
        Button(stringResource(UiRes.string.position_center), { onIntent(PhotoEditorIntent.CellStepped(index, PositionStep.CENTER, 1)) })
    }
}

@Composable
private fun CellArrow(index: Int, step: PositionStep, label: StringResource, turn: Float, onIntent: (PhotoEditorIntent) -> Unit) {
    RepeatingButton(
        MemixIcons.Arrow, stringResource(label),
        onHeld = { onIntent(PhotoEditorIntent.CellStepHeld(index, step, it)) },
        onStep = { onIntent(PhotoEditorIntent.CellStepped(index, step, it)) },
        turnDegrees = turn,
    )
}

/** Where a cell's photo is, as the selection's accessibility name: "Panel 2 of 4". */
@Composable
internal fun cellSpokenName(index: Int, count: Int): String = stringResource(Res.string.a11y_panel_cell, index + 1, count)


/**
 * One screen-reader node per filled cell over its rectangle ("Panel 2 of 4", selected when it is); double-tap selects it. The
 * empty cells' pick tiles and the dividers speak for themselves.
 */
@Composable
internal fun BoxScope.CellNodes(state: PhotoEditorUiState, scene: SceneUi, stage: LayerStageState, onIntent: (PhotoEditorIntent) -> Unit) {
    val panels = scene.panels() ?: return
    val geometry = geometryOf(panels, scene) ?: return
    val density = LocalDensity.current
    val interactive = state.panel != PhotoPanel.FORMATS && state.panel != PhotoPanel.CANVAS
    geometry.cells.forEachIndexed { index, cell ->
        if (panels.cells.getOrNull(index)?.source == null || index in state.missingCells) return@forEachIndexed
        val name = cellSpokenName(index, panels.cells.size)
        val isSelected = state.selectedCell == index
        Box(
            Modifier
                .offset { stageRect(cell, stage.frame, scene.canvas.widthPx).let { IntOffset(it.left.roundToInt(), it.top.roundToInt()) } }
                .size(with(density) { (stageRect(cell, stage.frame, scene.canvas.widthPx).width).toDp() }, with(density) { (stageRect(cell, stage.frame, scene.canvas.widthPx).height).toDp() })
                .clearAndSetSemantics {
                    contentDescription = name
                    selected = isSelected
                    if (interactive) onClick { onIntent(PhotoEditorIntent.TapCell(index)); true }
                },
        )
    }
}
