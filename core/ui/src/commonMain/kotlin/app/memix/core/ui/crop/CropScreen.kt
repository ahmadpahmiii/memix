package app.memix.core.ui.crop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.RatioShape
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.CropFrames
import app.memix.core.model.project.ImageEdit
import app.memix.core.model.project.ImageToolCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.ui.panels.Readout
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import kotlin.math.abs
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.canvas_title
import memix.core.ui.generated.resources.crop_cancel_a11y
import memix.core.ui.generated.resources.crop_edge_bottom
import memix.core.ui.generated.resources.crop_edge_left
import memix.core.ui.generated.resources.crop_edge_less_a11y
import memix.core.ui.generated.resources.crop_edge_more_a11y
import memix.core.ui.generated.resources.crop_edge_right
import memix.core.ui.generated.resources.crop_edge_top
import memix.core.ui.generated.resources.crop_edges
import memix.core.ui.generated.resources.crop_flip_h
import memix.core.ui.generated.resources.crop_flip_v
import memix.core.ui.generated.resources.crop_free
import memix.core.ui.generated.resources.crop_original
import memix.core.ui.generated.resources.crop_straighten
import memix.core.ui.generated.resources.crop_title
import memix.core.ui.generated.resources.edit_photo_reset
import memix.core.ui.generated.resources.explain_crop_min
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_rotate_left
import memix.core.ui.generated.resources.position_rotate_right
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Crop screen (spec docs/ux/specs/P2-04-image-tools.md → The Crop screen), a mode of its own over the editor: the whole
 * [picture] (null while it decodes) with its turn, mirrors and straighten and a crop frame, aspect tiles, rotate and flip, an
 * Edges panel as the single-pointer route, and Straighten. It knows no layer: it takes the picture, an [initial] edit (its geometry
 * parts; color edits pass through) and the [canvasAspect] for the Canvas tile, and returns the edit through [onDone]. Done is the
 * one blue action; Close calls [onClose] and drops everything (a mode's changes were never applied). System back and Escape are the caller's: it calls the same close (the Edges panel takes back itself while it is open).
 * P4-16 reuses it for a video frame.
 */
@Composable
fun CropScreen(
    picture: ImageBitmap?,
    pictureSize: PixelSize,
    initial: ImageEdit,
    canvasAspect: Float,
    onDone: (ImageEdit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(pictureSize, initial) { CropState(initial, pictureSize, canvasAspect) }
    var edgesOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(MemixColors.stage).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onClose = onClose, onReset = state::reset, onDone = { onDone(state.result) })
            CropStage(state, picture, Modifier.weight(1f).fillMaxWidth())
            BottomArea(state, onEdges = { edgesOpen = true })
        }
        EdgesPanel(state, visible = edgesOpen, onDone = { edgesOpen = false }, Modifier.align(Alignment.BottomCenter))
    }
}

// Close and the title, then Reset and Done; at 200% font the actions wrap to a second line instead of squeezing the words.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(onClose: () -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MemixColors.canvas).heightIn(min = TOP_BAR_HEIGHT).padding(horizontal = MemixSpacing.space2), contentAlignment = Alignment.CenterStart) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            IconButton(MemixIcons.Close, stringResource(Res.string.crop_cancel_a11y), onClose)
            Text(stringResource(Res.string.crop_title), MemixTheme.type.title, Modifier.semantics { heading() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Button(stringResource(Res.string.edit_photo_reset), onReset, variant = ButtonVariant.Quiet)
            Button(stringResource(Res.string.panel_done), onDone, variant = ButtonVariant.Primary)
        }
    }
    }
}

@Composable
private fun BottomArea(state: CropState, onEdges: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = BOTTOM_AREA_MAX_HEIGHT)
            .background(MemixColors.surface)
            .verticalScroll(rememberScrollState())
            .padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        AspectRow(state)
        ToolRow(state, onEdges)
        val degrees = state.base.straighten
        Slider(
            label = stringResource(Res.string.crop_straighten),
            value = degrees,
            onValueChange = state::straighten,
            valueText = degreesText(degrees),
            valueRange = ImageToolCatalog.STRAIGHTEN_RANGE,
            defaultValue = 0f,
            step = STRAIGHTEN_KEY_STEP,
            snapValue = 0f,
            snapDistance = STRAIGHTEN_SNAP_DEGREES,
            origin = 0f,
        )
    }
}

// "-3.5°" with a real minus sign; a whole number has no decimals.
private fun degreesText(degrees: Float): String {
    val tenths = (abs(degrees) * 10f).roundToInt()
    val body = if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    return (if (degrees < 0f && tenths != 0) "−" else "") + body + "°"
}

@Composable
private fun AspectRow(state: CropState) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        for (choice in CropAspect.entries) {
            val label = aspectLabel(choice)
            OptionTile(label, state.aspect == choice, { state.select(choice) }, OptionTileLayout.STACKED) { tint ->
                when (choice) {
                    CropAspect.FREE -> Icon(MemixIcons.RatioCustom, null, tint = tint)
                    else -> RatioShape(aspectOf(state, choice), tint)
                }
            }
        }
    }
}

// The shape a tile draws: Original and Canvas are the picture's and the canvas's own; the rest are their ratio.
private fun aspectOf(state: CropState, choice: CropAspect): Float = when (choice) {
    CropAspect.ORIGINAL -> state.turned.widthPx.toFloat() / state.turned.heightPx
    CropAspect.CANVAS -> state.canvasShape
    else -> choice.ratio ?: 1f
}

// Ratios are drawn from data and never translated (spec).
@Composable
private fun aspectLabel(choice: CropAspect): String = when (choice) {
    CropAspect.FREE -> stringResource(Res.string.crop_free)
    CropAspect.ORIGINAL -> stringResource(Res.string.crop_original)
    CropAspect.CANVAS -> stringResource(Res.string.canvas_title)
    CropAspect.SQUARE -> "1:1"
    CropAspect.FOUR_FIVE -> "4:5"
    CropAspect.THREE_FOUR -> "3:4"
    CropAspect.NINE_SIXTEEN -> "9:16"
    CropAspect.SIXTEEN_NINE -> "16:9"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolRow(state: CropState, onEdges: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1), itemVerticalAlignment = Alignment.CenterVertically) {
        IconButton(MemixIcons.RotateLeft, stringResource(Res.string.position_rotate_left), state::turnLeft)
        IconButton(MemixIcons.RotateRight, stringResource(Res.string.position_rotate_right), state::turnRight)
        IconButton(MemixIcons.Flip, stringResource(Res.string.crop_flip_h), state::flipAcross)
        IconButton(MemixIcons.Flip, stringResource(Res.string.crop_flip_v), state::flipDown, turnDegrees = QUARTER_TURN)
        Button(stringResource(Res.string.crop_edges), onEdges, Modifier.padding(start = MemixSpacing.space2))
    }
}

private val TOP_BAR_HEIGHT = 56.dp
private val BOTTOM_AREA_MAX_HEIGHT = 300.dp
private const val STRAIGHTEN_KEY_STEP = 1f
private const val STRAIGHTEN_SNAP_DEGREES = 1f
private const val QUARTER_TURN = 90f

/** Left, Right, Top and Bottom, each trimmed by 1% a tap (hold repeats), the route for moving and resizing the frame without a drag. */
@Composable
private fun EdgesPanel(state: CropState, visible: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var atLimit by remember { mutableStateOf(false) }
    ToolPanel(visible, stringResource(Res.string.crop_edges), stringResource(Res.string.panel_done), onDone, modifier) {
        val sides = listOf(
            CropFrames.Handle.LEFT to stringResource(Res.string.crop_edge_left),
            CropFrames.Handle.RIGHT to stringResource(Res.string.crop_edge_right),
            CropFrames.Handle.TOP to stringResource(Res.string.crop_edge_top),
            CropFrames.Handle.BOTTOM to stringResource(Res.string.crop_edge_bottom),
        )
        for ((side, name) in sides) {
            val percent = state.trimmedPercent(side)
            val moreLabel = stringResource(Res.string.crop_edge_more_a11y, name)
            val lessLabel = stringResource(Res.string.crop_edge_less_a11y, name)
            StepperRow(name, spokenState = "$percent%") {
                EdgeButton(MemixIcons.Minus, lessLabel, state, side, direction = -1) { atLimit = it }
                Readout("$percent%")
                EdgeButton(MemixIcons.Plus, moreLabel, state, side, direction = 1) { atLimit = it }
            }
        }
        if (atLimit) {
            Text(
                stringResource(Res.string.explain_crop_min),
                MemixTheme.type.label,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MemixColors.textSecondary,
            )
        }
    }
}

// A tap trims one step; held, the frame follows each repeat and the release applies what is left (the repeats so far are never applied twice).
@Composable
private fun EdgeButton(icon: DrawableResource, label: String, state: CropState, side: CropFrames.Handle, direction: Int, onLimit: (Boolean) -> Unit) {
    var applied by remember { mutableIntStateOf(0) }
    RepeatingButton(
        icon,
        label,
        onHeld = { count ->
            onLimit(!state.stepEdge(side, direction * (count - applied)))
            applied = count
        },
        onStep = { count ->
            val rest = count - applied
            applied = 0
            if (rest > 0) onLimit(!state.stepEdge(side, direction * rest))
        },
    )
}
