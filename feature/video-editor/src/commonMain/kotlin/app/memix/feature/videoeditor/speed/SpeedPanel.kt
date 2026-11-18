package app.memix.feature.videoeditor.speed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.ui.panels.ChoiceRow
import app.memix.feature.videoeditor.SpeedPanelUi
import app.memix.feature.videoeditor.VideoEditorIntent
import app.memix.feature.videoeditor.lengthSecondsText
import app.memix.feature.videoeditor.speedText
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.fx_reset
import memix.feature.video_editor.generated.resources.fx_speed_value
import memix.feature.video_editor.generated.resources.speed_audio_rough
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
 * [panel] isn't null. 09b adds the Normal and Curve tabs above this content.
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
    ) {
        if (shown == null) return@ToolPanel
        SpeedTiles(shown.speed, onIntent)
        SpeedSlider(shown, onIntent)
        SoundChoice(shown, onIntent)
    }
}

// The tile equal to the current speed is white; with another value none is. A tap applies at once.
@Composable
private fun SpeedTiles(speed: Float, onIntent: (VideoEditorIntent) -> Unit) {
    val groupName = stringResource(Res.string.tool_speed)
    ChoiceRow(groupName, showHeading = false) {
        SpeedCatalog.TILES.forEachIndexed { index, tile ->
            val name = stringResource(Res.string.fx_speed_value, speedText(tile))
            OptionTile(
                label = name,
                selected = speed == tile,
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

private const val KEEP_INDEX = 0
private const val MUTE_INDEX = 1

/** How close to 1x, as a share of the slider's whole width, the thumb snaps to it: about 4 dp on a phone, with one tick (spec P4-09 → Speed slider). */
private const val SNAP_DISTANCE_POSITION = 0.012f
