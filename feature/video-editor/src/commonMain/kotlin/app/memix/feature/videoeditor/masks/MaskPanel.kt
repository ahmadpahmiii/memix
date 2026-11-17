package app.memix.feature.videoeditor.masks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.SegmentedTabs
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.designsystem.component.Toggle
import app.memix.core.domain.project.MaskStep
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.Readout
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import app.memix.core.ui.panels.keptInViewWhen
import app.memix.feature.videoeditor.MaskTab
import app.memix.feature.videoeditor.MaskUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_center
import memix.core.ui.generated.resources.position_height
import memix.core.ui.generated.resources.position_height_less
import memix.core.ui.generated.resources.position_height_more
import memix.core.ui.generated.resources.position_move
import memix.core.ui.generated.resources.position_move_down
import memix.core.ui.generated.resources.position_move_left
import memix.core.ui.generated.resources.position_move_right
import memix.core.ui.generated.resources.position_move_up
import memix.core.ui.generated.resources.position_rotate_left
import memix.core.ui.generated.resources.position_rotate_right
import memix.core.ui.generated.resources.position_rotation
import memix.core.ui.generated.resources.position_rotation_value
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.position_width
import memix.core.ui.generated.resources.position_width_less
import memix.core.ui.generated.resources.position_width_more
import memix.core.ui.generated.resources.tool_position
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.explain_mask_none
import memix.feature.video_editor.generated.resources.mask_circle
import memix.feature.video_editor.generated.resources.mask_corners
import memix.feature.video_editor.generated.resources.mask_feather
import memix.feature.video_editor.generated.resources.mask_invert
import memix.feature.video_editor.generated.resources.mask_linear
import memix.feature.video_editor.generated.resources.mask_linear_hint
import memix.feature.video_editor.generated.resources.mask_mirror
import memix.feature.video_editor.generated.resources.mask_none
import memix.feature.video_editor.generated.resources.mask_rectangle
import memix.feature.video_editor.generated.resources.mask_shape_group
import memix.feature.video_editor.generated.resources.mask_tab_shape
import memix.feature.video_editor.generated.resources.tool_mask
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Mask panel of the selected clip (spec P4-03b → The Mask panel): the Shape tab (the five tiles, Edge softness, Invert, Rounded
 * corners for a Rectangle, a hint for Linear) and the Position tab (Width, Height, Rotation, Move, Center, the single-pointer route for the
 * stage's frame). A ToolPanel over the timeline and tool bar, shown while [panel] isn't null; the tabs stay under the title unless they would
 * take more than 40% of the panel (the 360 x 640 phone), where they scroll with the content. Every control is one undo step, or live
 * while it moves.
 */
@Composable
internal fun MaskPanel(panel: MaskUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val held = remember { arrayOfNulls<MaskUi>(1) }
    if (panel != null) held[0] = panel
    val shown = panel ?: held[0]
    val tab = shown?.tab ?: MaskTab.SHAPE
    val mask = shown?.mask
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.tool_mask),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseMask) },
        modifier = modifier,
        pinned = { MaskTabs(tab, onIntent) },
    ) {
        when (tab) {
            MaskTab.SHAPE -> ShapeTab(mask, onIntent)
            MaskTab.POSITION -> PositionTab(mask, onIntent)
        }
    }
}

@Composable
private fun MaskTabs(selected: MaskTab, onIntent: (VideoEditorIntent) -> Unit) {
    val shape = stringResource(Res.string.mask_tab_shape)
    val position = stringResource(UiRes.string.tool_position)
    val labels = remember(shape, position) { listOf(shape, position) }
    SegmentedTabs(labels, selected.ordinal, { onIntent(VideoEditorIntent.MaskTabSelected(MaskTab.entries[it])) }, Modifier.fillMaxWidth())
}

// None and the four shapes as tiles whose pictures are drawn from data; below them the controls of the shape that is picked.
@Composable
private fun ShapeTab(mask: ClipMask?, onIntent: (VideoEditorIntent) -> Unit) {
    val choices = listOf<MaskShape?>(null) + MaskShape.entries
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        ChoiceRow(stringResource(Res.string.mask_shape_group), showHeading = false) {
            choices.forEachIndexed { index, shape ->
                val selected = mask?.shape == shape
                OptionTile(
                    label = stringResource(shape.label()),
                    selected = selected,
                    onClick = { onIntent(VideoEditorIntent.MaskShapePicked(shape)) },
                    layout = OptionTileLayout.STACKED,
                    modifier = Modifier
                        .keptInViewWhen(selected, margin = MemixSpacing.space4)
                        .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
                ) { tint -> ShapePicture(shape, tint) }
            }
        }
        if (mask == null) return@Column
        if (mask.shape == MaskShape.LINEAR) Text(stringResource(Res.string.mask_linear_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
        PercentSlider(Res.string.mask_feather, mask.softness, onChange = { onIntent(VideoEditorIntent.MaskSoftnessChanged(it)) }, onReleased = { onIntent(VideoEditorIntent.MaskSliderReleased) })
        Toggle(stringResource(Res.string.mask_invert), mask.invert, { onIntent(VideoEditorIntent.MaskInvertChanged(it)) })
        if (mask.shape == MaskShape.RECTANGLE) {
            PercentSlider(Res.string.mask_corners, mask.cornerRoundness, onChange = { onIntent(VideoEditorIntent.MaskCornersChanged(it)) }, onReleased = { onIntent(VideoEditorIntent.MaskSliderReleased) })
        }
    }
}

private fun MaskShape?.label(): StringResource = when (this) {
    null -> Res.string.mask_none
    MaskShape.RECTANGLE -> Res.string.mask_rectangle
    MaskShape.CIRCLE -> Res.string.mask_circle
    MaskShape.LINEAR -> Res.string.mask_linear
    MaskShape.MIRROR -> Res.string.mask_mirror
}

@Composable
private fun PercentSlider(label: StringResource, value: Float, onChange: (Float) -> Unit, onReleased: () -> Unit) {
    Slider(
        label = stringResource(label),
        value = value,
        onValueChange = onChange,
        valueText = stringResource(UiRes.string.position_size_value, (value * PERCENT).roundToInt()),
        valueRange = 0f..1f,
        defaultValue = 0f,
        step = MaskCatalog.SLIDER_STEP,
        onValueChangeFinished = onReleased,
    )
}

// The shapes drawn from data in the 24 dp picture box, 20 dp long (spec → Tokens and icons): a window, a round window, a line with the
// side that stays, a band. None is a dashed square: nothing cut out.
@Composable
private fun ShapePicture(shape: MaskShape?, tint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * PICTURE_SHARE
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val line = size.minDimension * LINE_SHARE
        when (shape) {
            null -> drawRoundRect(tint, origin, Size(side, side), CornerRadius(1f), Stroke(line))
            MaskShape.RECTANGLE -> drawRoundRect(tint, origin + Offset(0f, side * 0.15f), Size(side, side * 0.7f), CornerRadius(1f))
            MaskShape.CIRCLE -> drawOval(tint, origin + Offset(side * 0.1f, side * 0.1f), Size(side * 0.8f, side * 0.8f))
            MaskShape.LINEAR -> {
                // The line, and the half that stays filled (the upper one).
                drawRect(tint, origin, Size(side, side / 2f - line / 2f), alpha = HALF_ALPHA)
                drawRect(tint, origin + Offset(0f, side / 2f - line / 2f), Size(side, line))
            }
            MaskShape.MIRROR -> drawRect(tint, origin + Offset(0f, side * 0.3f), Size(side, side * 0.4f))
        }
    }
}

// Width, Height, Rotation and Move steps and Center: the no-drag route for the frame on the stage (WCAG 2.5.1 and 2.5.7). A tap is one
// step, a hold repeats and commits once on release. Only the rows the shape has.
@Composable
private fun PositionTab(mask: ClipMask?, onIntent: (VideoEditorIntent) -> Unit) {
    if (mask == null) {
        Text(stringResource(Res.string.explain_mask_none), MemixTheme.type.body, color = MemixColors.textSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        if (MaskCatalog.hasWidth(mask.shape)) {
            SizeRow(UiRes.string.position_width, mask.width, MaskStep.NARROWER, MaskStep.WIDER, UiRes.string.position_width_less, UiRes.string.position_width_more, onIntent)
        }
        if (MaskCatalog.hasHeight(mask.shape)) {
            SizeRow(UiRes.string.position_height, mask.height, MaskStep.SHORTER, MaskStep.TALLER, UiRes.string.position_height_less, UiRes.string.position_height_more, onIntent)
        }
        val degrees = mask.rotationDegrees.roundToInt()
        StepperRow(stringResource(UiRes.string.position_rotation), spokenState = stringResource(UiRes.string.position_rotation_value, degrees)) {
            IconButton(MemixIcons.RotateLeft, stringResource(UiRes.string.position_rotate_left), { onIntent(VideoEditorIntent.MaskStepped(MaskStep.ROTATE_LEFT, 1)) })
            Readout(stringResource(UiRes.string.position_rotation_value, degrees))
            IconButton(MemixIcons.RotateRight, stringResource(UiRes.string.position_rotate_right), { onIntent(VideoEditorIntent.MaskStepped(MaskStep.ROTATE_RIGHT, 1)) })
        }
        StepperRow(stringResource(UiRes.string.position_move), spokenState = null) {
            MoveButton(MaskStep.MOVE_LEFT, UiRes.string.position_move_left, ARROW_LEFT, onIntent)
            MoveButton(MaskStep.MOVE_UP, UiRes.string.position_move_up, ARROW_UP, onIntent)
            MoveButton(MaskStep.MOVE_DOWN, UiRes.string.position_move_down, ARROW_DOWN, onIntent)
            MoveButton(MaskStep.MOVE_RIGHT, UiRes.string.position_move_right, ARROW_RIGHT, onIntent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(stringResource(UiRes.string.position_center), { onIntent(VideoEditorIntent.MaskStepped(MaskStep.CENTER, 1)) })
        }
    }
}

@Composable
private fun SizeRow(
    label: StringResource,
    value: Float,
    less: MaskStep,
    more: MaskStep,
    lessLabel: StringResource,
    moreLabel: StringResource,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val percent = (value * PERCENT).roundToInt()
    StepperRow(stringResource(label), spokenState = stringResource(UiRes.string.position_size_value, percent)) {
        RepeatingButton(MemixIcons.Minus, stringResource(lessLabel), onHeld = { onIntent(VideoEditorIntent.MaskStepHeld(less, it)) }, onStep = { onIntent(VideoEditorIntent.MaskStepped(less, it)) })
        Readout(stringResource(UiRes.string.position_size_value, percent))
        RepeatingButton(MemixIcons.Plus, stringResource(moreLabel), onHeld = { onIntent(VideoEditorIntent.MaskStepHeld(more, it)) }, onStep = { onIntent(VideoEditorIntent.MaskStepped(more, it)) })
    }
}

// One arrow drawing turned four ways; held, it repeats and commits once on release.
@Composable
private fun MoveButton(step: MaskStep, label: StringResource, turnDegrees: Float, onIntent: (VideoEditorIntent) -> Unit) {
    RepeatingButton(
        MemixIcons.Arrow,
        stringResource(label),
        onHeld = { onIntent(VideoEditorIntent.MaskStepHeld(step, it)) },
        onStep = { onIntent(VideoEditorIntent.MaskStepped(step, it)) },
        turnDegrees = turnDegrees,
    )
}

private const val PERCENT = 100f
private const val PICTURE_SHARE = 20f / 24f
private const val LINE_SHARE = 2f / 24f
private const val HALF_ALPHA = 0.45f
private const val ARROW_RIGHT = 0f
private const val ARROW_DOWN = 90f
private const val ARROW_LEFT = 180f
private const val ARROW_UP = 270f
