package app.memix.feature.videoeditor.speed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedPreset
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.Readout
import app.memix.core.ui.panels.RepeatingButton
import app.memix.core.ui.panels.StepperRow
import app.memix.core.ui.panels.bleedPastPanelPadding
import app.memix.feature.videoeditor.SpeedCurveAction
import app.memix.feature.videoeditor.SpeedPanelUi
import app.memix.feature.videoeditor.SpeedTab
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import app.memix.feature.videoeditor.lengthSecondsText
import app.memix.feature.videoeditor.speedText
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.fx_reset
import memix.feature.video_editor.generated.resources.fx_speed_value
import memix.feature.video_editor.generated.resources.speed_audio_rough
import memix.feature.video_editor.generated.resources.speed_curve_bullet
import memix.feature.video_editor.generated.resources.speed_curve_flash
import memix.feature.video_editor.generated.resources.speed_curve_pulse
import memix.feature.video_editor.generated.resources.speed_curve_ramp_down
import memix.feature.video_editor.generated.resources.speed_curve_ramp_up
import memix.feature.video_editor.generated.resources.speed_curve_slam
import memix.feature.video_editor.generated.resources.speed_mode_curve
import memix.feature.video_editor.generated.resources.speed_mode_normal
import memix.feature.video_editor.generated.resources.speed_point_add
import memix.feature.video_editor.generated.resources.speed_point_earlier
import memix.feature.video_editor.generated.resources.speed_point_faster
import memix.feature.video_editor.generated.resources.speed_point_later
import memix.feature.video_editor.generated.resources.speed_point_next
import memix.feature.video_editor.generated.resources.speed_point_of
import memix.feature.video_editor.generated.resources.speed_point_previous
import memix.feature.video_editor.generated.resources.speed_point_remove
import memix.feature.video_editor.generated.resources.speed_point_slower
import memix.feature.video_editor.generated.resources.speed_point_time
import memix.feature.video_editor.generated.resources.speed_point_time_value
import memix.feature.video_editor.generated.resources.speed_length
import memix.feature.video_editor.generated.resources.speed_readout
import memix.feature.video_editor.generated.resources.speed_sound
import memix.feature.video_editor.generated.resources.speed_sound_keep
import memix.feature.video_editor.generated.resources.speed_sound_mute
import memix.feature.video_editor.generated.resources.tool_speed
import org.jetbrains.compose.resources.stringResource

/**
 * The Speed panel of a video clip (spec docs/ux/specs/P4-09-speed.md → The Speed panel, Normal view): a row of tiles, a logarithmic slider with the
 * clip's resulting length, and the Sound choice (Keep or Mute), with Reset in the header. A tile and a release are one undo step each; the slider
 * only moves the readout until it is let go. It takes the place of the timeline and the tool bar ([modifier] sizes it to them) and is shown while
 * [panel] isn't null. P4-09b: Tabs "Normal · Curve" sit pinned under the title; the Curve tab holds the preset tiles, the graph (`SpeedCurveGraph`), the
 * controls of the selected point (previous and next, Time and Speed steppers, Remove and Add) and the same Sound choice. Setting anything in the other tab
 * switches the clip to that mode, in the same step (the domain edits do it).
 */
@Composable
internal fun SpeedPanel(panel: SpeedPanelUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val held = remember { arrayOfNulls<SpeedPanelUi>(1) }
    if (panel != null) held[0] = panel
    val shown = panel ?: held[0]
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.tool_speed),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseSpeed) },
        modifier = modifier,
        headerActions = { Button(stringResource(Res.string.fx_reset), { onIntent(VideoEditorIntent.ResetSpeed) }, variant = ButtonVariant.Quiet) },
        pinned = { SpeedTabs(shown?.tab ?: SpeedTab.NORMAL, onIntent) },
    ) {
        if (shown == null) return@ToolPanel
        when (shown.tab) {
            SpeedTab.NORMAL -> {
                SpeedTiles(shown, onIntent)
                SpeedSlider(shown, onIntent)
            }
            SpeedTab.CURVE -> CurveTab(shown, onIntent)
        }
        SoundChoice(shown, onIntent)
    }
}

@Composable
private fun SpeedTabs(selected: SpeedTab, onIntent: (VideoEditorIntent) -> Unit) {
    val normal = stringResource(Res.string.speed_mode_normal)
    val curve = stringResource(Res.string.speed_mode_curve)
    val labels = remember(normal, curve) { listOf(normal, curve) }
    Tabs(labels, selected.ordinal, { onIntent(VideoEditorIntent.SpeedTabSelected(SpeedTab.entries[it])) }, Modifier.fillMaxWidth().bleedPastPanelPadding(), contentPadding = MemixSpacing.space4)
}

// Preset tiles, the graph, then the controls of the selected point: the steppers are the way to do it all without dragging (WCAG 2.5.7).
@Composable
private fun CurveTab(panel: SpeedPanelUi, onIntent: (VideoEditorIntent) -> Unit) {
    val onAction = { action: SpeedCurveAction -> onIntent(VideoEditorIntent.SpeedCurveChanged(action)) }
    PresetTiles(panel.preset, onIntent)
    SpeedCurveGraph(panel, onAction)
    if (panel.selected != null) PointControls(panel, onAction)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(MemixSpacing.space2, Alignment.End)) {
        if (panel.selected != null) Button(stringResource(Res.string.speed_point_remove), { onAction(SpeedCurveAction.RemovePoint) }, variant = ButtonVariant.Quiet)
        Button(stringResource(Res.string.speed_point_add), { onAction(SpeedCurveAction.AddPoint) })
    }
}

// The tile of the current preset is white; an edited curve, or none, has no white tile.
@Composable
private fun PresetTiles(current: SpeedPreset?, onIntent: (VideoEditorIntent) -> Unit) {
    val groupName = stringResource(Res.string.speed_mode_curve)
    ChoiceRow(groupName, showHeading = false) {
        SpeedPreset.entries.forEachIndexed { index, preset ->
            val name = stringResource(presetName(preset))
            OptionTile(
                label = name,
                selected = preset == current,
                onClick = { onIntent(VideoEditorIntent.SpeedPresetPicked(preset)) },
                modifier = Modifier.height(MemixSize.touchTarget),
                spokenName = "$name, ${index + 1} / ${SpeedPreset.entries.size}",
            )
        }
    }
}

private fun presetName(preset: SpeedPreset): StringResource = when (preset) {
    SpeedPreset.RAMP_UP -> Res.string.speed_curve_ramp_up
    SpeedPreset.RAMP_DOWN -> Res.string.speed_curve_ramp_down
    SpeedPreset.BULLET -> Res.string.speed_curve_bullet
    SpeedPreset.FLASH -> Res.string.speed_curve_flash
    SpeedPreset.PULSE -> Res.string.speed_curve_pulse
    SpeedPreset.SLAM -> Res.string.speed_curve_slam
}

@Composable
private fun PointControls(panel: SpeedPanelUi, onAction: (SpeedCurveAction) -> Unit) {
    val point = panel.selected ?: return
    Column(verticalArrangement = spacedBy(MemixSpacing.space2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(MemixIcons.Arrow, stringResource(Res.string.speed_point_previous), { onAction(SpeedCurveAction.PreviousPoint) }, enabled = panel.selectedPoint > 0, turnDegrees = ARROW_LEFT)
            Text(stringResource(Res.string.speed_point_of, panel.selectedPoint + 1, panel.curve.size), MemixTheme.type.body, color = MemixColors.text)
            IconButton(MemixIcons.Arrow, stringResource(Res.string.speed_point_next), { onAction(SpeedCurveAction.NextPoint) }, enabled = panel.selectedPoint < panel.curve.lastIndex, turnDegrees = ARROW_RIGHT)
        }
        val percent = (point.positionFraction * PERCENT).roundToInt()
        val percentText = stringResource(Res.string.speed_point_time_value, percent)
        StepperRow(stringResource(Res.string.speed_point_time), spokenState = percentText) {
            RepeatingButton(MemixIcons.Minus, stringResource(Res.string.speed_point_earlier), { onAction(SpeedCurveAction.StepTime(-it, held = true)) }, { onAction(SpeedCurveAction.StepTime(-it, held = false)) })
            Readout(percentText)
            RepeatingButton(MemixIcons.Plus, stringResource(Res.string.speed_point_later), { onAction(SpeedCurveAction.StepTime(it, held = true)) }, { onAction(SpeedCurveAction.StepTime(it, held = false)) })
        }
        val speedText = stringResource(Res.string.fx_speed_value, speedText(point.speed))
        StepperRow(stringResource(Res.string.tool_speed), spokenState = speedText) {
            RepeatingButton(MemixIcons.Minus, stringResource(Res.string.speed_point_slower), { onAction(SpeedCurveAction.StepSpeed(-it, held = true)) }, { onAction(SpeedCurveAction.StepSpeed(-it, held = false)) })
            Readout(speedText)
            RepeatingButton(MemixIcons.Plus, stringResource(Res.string.speed_point_faster), { onAction(SpeedCurveAction.StepSpeed(it, held = true)) }, { onAction(SpeedCurveAction.StepSpeed(it, held = false)) })
        }
    }
}

// The tile equal to the current speed is white; with another value none is. A tap applies at once.
@Composable
private fun SpeedTiles(panel: SpeedPanelUi, onIntent: (VideoEditorIntent) -> Unit) {
    val groupName = stringResource(Res.string.tool_speed)
    ChoiceRow(groupName, showHeading = false) {
        SpeedCatalog.TILES.forEachIndexed { index, tile ->
            val name = stringResource(Res.string.fx_speed_value, speedText(tile))
            OptionTile(
                label = name,
                // A clip with a curve has no constant speed, so no tile is white.
                selected = !panel.hasCurve && panel.speed == tile,
                onClick = { onIntent(VideoEditorIntent.SpeedTilePicked(tile)) },
                modifier = Modifier.height(MemixSize.touchTarget),
                // "2×, 4 of 6": the single-choice group says which one is selected (spec P4-09 → Accessibility).
                spokenName = "$name, ${index + 1} / ${SpeedCatalog.TILES.size}",
            )
        }
    }
}

// The thumb's place is the log scale's position p (0.1x at the left, 1x in the middle, 10x at the right); keys and screen readers step on the speed grid.
@Composable
private fun SpeedSlider(panel: SpeedPanelUi, onIntent: (VideoEditorIntent) -> Unit) {
    val readout = stringResource(Res.string.speed_readout, speedText(panel.speed), stringResource(Res.string.speed_length, lengthSecondsText(panel.lengthUs)))
    Slider(
        label = stringResource(Res.string.tool_speed),
        value = SpeedCatalog.positionOfSpeed(panel.speed),
        onValueChange = { onIntent(VideoEditorIntent.SpeedChanged(SpeedCatalog.speedAtPosition(it))) },
        valueText = readout,
        defaultValue = SpeedCatalog.positionOfSpeed(SpeedCatalog.NORMAL),
        onValueChangeFinished = { onIntent(VideoEditorIntent.SpeedReleased) },
        snapValue = SpeedCatalog.positionOfSpeed(SpeedCatalog.NORMAL),
        snapDistance = SNAP_DISTANCE_POSITION,
        nextValue = { value, up -> SpeedCatalog.positionOfSpeed(SpeedCatalog.gridStep(SpeedCatalog.speedAtPosition(value), if (up) 1 else -1)) },
    )
}

@Composable
private fun SoundChoice(panel: SpeedPanelUi, onIntent: (VideoEditorIntent) -> Unit) {
    val groupName = stringResource(Res.string.speed_sound)
    val keep = stringResource(Res.string.speed_sound_keep)
    val mute = stringResource(Res.string.speed_sound_mute)
    val segments = remember(keep, mute) { listOf(Segment(keep), Segment(mute)) }
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Text(groupName, MemixTheme.type.bodyStrong, Modifier.semantics { heading() }, color = MemixColors.text)
        SegmentedChoice(
            segments,
            if (panel.keepSound) KEEP_INDEX else MUTE_INDEX,
            { onIntent(VideoEditorIntent.SpeedSoundSelected(keep = it == KEEP_INDEX)) },
            groupName,
            Modifier.fillMaxWidth(),
        )
        // Only with Keep, outside 0.5x to 2x (spec P4-09 → The Sound row); nothing is changed for the user.
        if (panel.soundIsRough) Text(stringResource(Res.string.speed_audio_rough), MemixTheme.type.label, Modifier.fillMaxWidth(), color = MemixColors.textSecondary)
    }
}

private const val ARROW_LEFT = 180f
private const val ARROW_RIGHT = 0f
private const val PERCENT = 100f
private const val KEEP_INDEX = 0
private const val MUTE_INDEX = 1

/** How close to 1x, as a share of the slider's whole width, the thumb snaps to it: about 4 dp on a phone, with one tick (spec P4-09 → Speed slider). */
private const val SNAP_DISTANCE_POSITION = 0.012f
