package app.memix.feature.photoeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.DemotivationalFormat
import app.memix.core.model.project.FormatSlot
import app.memix.core.model.project.SlotId
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.keptInViewWhen
import kotlin.math.min
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_format_slot
import memix.feature.photo_editor.generated.resources.a11y_format_slot_bubble
import memix.feature.photo_editor.generated.resources.format_caption_bar
import memix.feature.photo_editor.generated.resources.format_classic
import memix.feature.photo_editor.generated.resources.format_demotivational
import memix.feature.photo_editor.generated.resources.format_plain
import memix.feature.photo_editor.generated.resources.format_slot_bar
import memix.feature.photo_editor.generated.resources.format_slot_bottom
import memix.feature.photo_editor.generated.resources.format_slot_bubble_hint
import memix.feature.photo_editor.generated.resources.format_slot_hint
import memix.feature.photo_editor.generated.resources.format_slot_subtitle
import memix.feature.photo_editor.generated.resources.format_slot_title
import memix.feature.photo_editor.generated.resources.format_slot_top
import memix.feature.photo_editor.generated.resources.format_bubbles
import memix.feature.photo_editor.generated.resources.format_panels
import memix.feature.photo_editor.generated.resources.formats_group
import memix.feature.photo_editor.generated.resources.formats_hint_room
import memix.feature.photo_editor.generated.resources.formats_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The meme formats' editor pieces (spec docs/ux/specs/P2-02-meme-formats.md → Formats panel, The slot hint): the Formats panel,
 * the tiles' pictures drawn from data, and the editor-only hints over a format's empty slots.
 */

/** The formats this build can make, in the spec's tile order. A tile never shows before its format works (no dead controls). */
internal val BuiltFormats: List<FormatChoice> = listOf(FormatChoice.PLAIN, FormatChoice.CLASSIC, FormatChoice.CAPTION_BAR, FormatChoice.PANELS, FormatChoice.DEMOTIVATIONAL, FormatChoice.BUBBLES)

private fun FormatChoice.label(): StringResource = when (this) {
    FormatChoice.PLAIN -> Res.string.format_plain
    FormatChoice.CLASSIC -> Res.string.format_classic
    FormatChoice.CAPTION_BAR -> Res.string.format_caption_bar
    FormatChoice.PANELS -> Res.string.format_panels
    FormatChoice.DEMOTIVATIONAL -> Res.string.format_demotivational
    FormatChoice.BUBBLES -> Res.string.format_bubbles
}

/** The spoken "Format: Caption bar" after a tile. */
@Composable
internal fun formatName(choice: FormatChoice): String = stringResource(choice.label())

/**
 * The Formats panel: a ToolPanel with one row of tiles, the active format selected (Plain is a tile like the others and the way
 * back), and under them, for a format that gives the photo less room than the canvas, one line that says so. A tap applies at
 * once and the panel stays open.
 */
@Composable
internal fun FormatsPanel(state: PhotoEditorUiState, maxHeight: Dp, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val active = state.format.toChoice()
    ToolPanel(
        state.panel == PhotoPanel.FORMATS,
        stringResource(Res.string.formats_title),
        stringResource(UiRes.string.panel_done),
        { onIntent(PhotoEditorIntent.ClosePanel) },
        modifier,
        contentMaxHeight = maxHeight,
    ) {
        ChoiceRow(stringResource(Res.string.formats_group)) {
            BuiltFormats.forEach { choice ->
                val selected = choice == active
                OptionTile(
                    stringResource(choice.label()),
                    selected = selected,
                    onClick = { onIntent(PhotoEditorIntent.SetFormat(choice)) },
                    layout = OptionTileLayout.STACKED,
                    modifier = Modifier.keptInViewWhen(selected),
                ) { tint -> FormatShape(choice, tint) }
            }
        }
        if (state.formatHintRoom) {
            Text(stringResource(Res.string.formats_hint_room), MemixTheme.type.caption, color = MemixColors.textSecondary)
        }
    }
}

/**
 * A format drawn as itself for an [OptionTile]'s picture: one color, solid shapes with 1 dp corners, the long side 20 dp of the
 * 24 dp box (the icon grammar, like a ratio's rectangle). Layout math, not an icon file.
 */
@Composable
internal fun FormatShape(choice: FormatChoice, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(FORMAT_SHAPE_BOX).drawBehind { drawFormatShape(choice, color) })
}

// Everything in the 24-unit box of the icon grammar, scaled to the box's pixels.
private fun DrawScope.drawFormatShape(choice: FormatChoice, color: Color) {
    val unit = size.minDimension / BOX_UNITS
    val stroke = Stroke(2f * unit)
    val corner = CornerRadius(unit)
    fun solid(left: Float, top: Float, right: Float, bottom: Float) =
        drawRoundRect(color, Offset(left * unit, top * unit), Size((right - left) * unit, (bottom - top) * unit), corner)
    // A stroked rectangle whose outside edge is the given box (the stroke is centered on its path, so it is inset by one stroke half).
    fun outline(left: Float, top: Float, right: Float, bottom: Float) =
        drawRoundRect(color, Offset((left + 1f) * unit, (top + 1f) * unit), Size((right - left - 2f) * unit, (bottom - top - 2f) * unit), corner, style = stroke)
    when (choice) {
        FormatChoice.PLAIN -> outline(2f, 4f, 22f, 20f)
        FormatChoice.CLASSIC -> {
            outline(2f, 4f, 22f, 20f)
            solid(6f, 7f, 18f, 9f)
            solid(6f, 15f, 18f, 17f)
        }
        FormatChoice.CAPTION_BAR -> {
            outline(2f, 4f, 22f, 20f)
            solid(2f, 4f, 22f, 10f)
        }
        FormatChoice.PANELS -> {
            solid(2f, 2f, 11f, 11f)
            solid(13f, 2f, 22f, 11f)
            solid(2f, 13f, 11f, 22f)
            solid(13f, 13f, 22f, 22f)
        }
        FormatChoice.DEMOTIVATIONAL -> {
            outline(2f, 2f, 22f, 22f)
            outline(5f, 5f, 19f, 14f)
            solid(7f, 16.5f, 17f, 18f)
            solid(9f, 19f, 15f, 20f)
        }
        FormatChoice.BUBBLES -> {
            solid(2f, 4f, 22f, 18f)
            val tail = Path().apply {
                moveTo(5f * unit, 17f * unit)
                lineTo(5f * unit, 22f * unit)
                lineTo(10f * unit, 17f * unit)
                close()
            }
            drawPath(tail, color)
        }
    }
}

private val FORMAT_SHAPE_BOX = MemixSize.iconSmall * 1.5f
private const val BOX_UNITS = 24f

/**
 * The hints over a format's empty slots (spec → The slot hint): each a `scrim` rounded rectangle with "Tap to add text" (or "Tap
 * to add a bubble") centered on its slot, at least `touch-target` tall, never wider than 90% of S. Editor only: nothing here is
 * saved, rendered or exported. They show while nothing is selected and no panel is open, and give way (a 120 ms fade) while
 * typing or a gesture is on.
 */
@Composable
internal fun BoxScope.SlotHints(state: PhotoEditorUiState, scene: SceneUi, stage: LayerStageState, onIntent: (PhotoEditorIntent) -> Unit) {
    // On the Demotivational poster's black a scrim chip is invisible: the fill is surface-raised there (design review P1).
    val onPoster = state.format is DemotivationalFormat
    val shown = state.selectedLayerId == null && state.panel == null && state.textEntry == null && state.emptySlots.isNotEmpty()
    AnimatedVisibility(
        shown,
        Modifier.matchParentSize(),
        enter = fadeIn(tween(MemixMotion.durationPress)),
        exit = fadeOut(tween(MemixMotion.durationPress)),
    ) {
        val hints = remember(state.emptySlots) { state.emptySlots }
        Layout(
            content = { hints.forEach { slot -> SlotHint(slot, onPoster, onIntent) } },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            layout(constraints.maxWidth, constraints.maxHeight) {
                val frame = stage.frame
                // Read while placing, so a frame that moves (a ratio change, a panel) moves the hints without recomposing.
                val widest = (SLOT_HINT_MAX_OF_SHORT_SIDE * min(frame.width, frame.height)).roundToInt().coerceAtLeast(0)
                measurables.forEachIndexed { index, measurable ->
                    val slot = hints[index]
                    val placeable = measurable.measure(Constraints(maxWidth = widest.coerceAtLeast(1)))
                    val centerX = frame.left + slot.centerX * frame.width
                    val centerY = frame.top + slot.centerY * frame.height
                    // Shifted in, never cropped, when the slot is near the frame's edge (a two-line hint at 200% font).
                    val left = (centerX - placeable.width / 2f).coerceIn(frame.left, (frame.left + frame.width - placeable.width).coerceAtLeast(frame.left))
                    val top = (centerY - placeable.height / 2f).coerceIn(frame.top, (frame.top + frame.height - placeable.height).coerceAtLeast(frame.top))
                    placeable.place(left.roundToInt(), top.roundToInt())
                }
            }
        }
    }
}

@Composable
private fun SlotHint(slot: FormatSlot, onPoster: Boolean, onIntent: (PhotoEditorIntent) -> Unit) {
    val position = stringResource(slotName(slot.id))
    val label = stringResource(if (slot.id == SlotId.BUBBLE) Res.string.format_slot_bubble_hint else Res.string.format_slot_hint)
    val spoken = stringResource(if (slot.id == SlotId.BUBBLE) Res.string.a11y_format_slot_bubble else Res.string.a11y_format_slot, position)
    Column(
        Modifier
            .clip(MemixShapes.radiusMd)
            .background(if (onPoster) MemixColors.surfaceRaised else MemixColors.scrim)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clickable(role = Role.Button, onClick = { onIntent(PhotoEditorIntent.TapSlot(slot)) })
            .padding(horizontal = MemixSpacing.space3)
            .semantics { contentDescription = spoken },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, MemixTheme.type.label, Modifier.padding(vertical = MemixSpacing.space3), color = MemixColors.text, textAlign = TextAlign.Center, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Visible)
    }
}

private fun slotName(id: SlotId): StringResource = when (id) {
    SlotId.TOP -> Res.string.format_slot_top
    SlotId.BOTTOM -> Res.string.format_slot_bottom
    SlotId.BAR -> Res.string.format_slot_bar
    SlotId.TITLE -> Res.string.format_slot_title
    SlotId.SUBTITLE -> Res.string.format_slot_subtitle
    SlotId.BUBBLE -> Res.string.format_slot_top
}

private const val SLOT_HINT_MAX_OF_SHORT_SIDE = 0.9f
