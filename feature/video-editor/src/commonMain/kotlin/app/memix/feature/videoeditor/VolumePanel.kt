package app.memix.feature.videoeditor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.Toggle
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.domain.project.MAX_VOLUME
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.volume_mute_all
import memix.feature.video_editor.generated.resources.volume_percent
import memix.feature.video_editor.generated.resources.volume_this_clip
import memix.feature.video_editor.generated.resources.volume_this_sound
import memix.feature.video_editor.generated.resources.volume_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Volume panel of a meme sound or a video clip (spec P1-06 → Volume panel): 0–200%, snapping to 100% within 3% with
 * one tick, 10% steps for the keyboard and TalkBack, a double-tap on the thumb back to 100%. Playback keeps going, so
 * each move is heard; each release is one Volume step. A video clip's panel reads "This clip" and adds the "Mute
 * original audio of every clip" row, the same state as the timeline's toggle ([originalMuted]). Shown when [panel] isn't
 * null; it covers the timeline and the tool bar ([modifier] sizes it to them).
 */
@Composable
internal fun VolumePanel(
    panel: VolumePanelUi?,
    originalMuted: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.volume_title),
        doneLabel = stringResource(Res.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseVolumePanel) },
        modifier = modifier,
    ) {
        // The panel's last value stays on screen while it slides away.
        val volume = panel?.volume ?: DEFAULT_VOLUME
        val isVideoClip = panel?.isVideoClip == true
        Slider(
            label = stringResource(if (isVideoClip) Res.string.volume_this_clip else Res.string.volume_this_sound),
            value = volume,
            onValueChange = { onIntent(VideoEditorIntent.VolumeChanged(it)) },
            valueText = stringResource(Res.string.volume_percent, (volume * PERCENT).roundToInt()),
            valueRange = 0f..MAX_VOLUME,
            defaultValue = DEFAULT_VOLUME,
            step = STEP,
            onValueChangeFinished = { onIntent(VideoEditorIntent.VolumeReleased) },
            snapValue = DEFAULT_VOLUME,
            snapDistance = SNAP_DISTANCE,
        )
        if (isVideoClip) {
            // While every clip is muted the slider still sets this clip's level; the row right under it says why
            // nothing is heard (spec P1-06 → Clip volume).
            Spacer(Modifier.height(MemixSpacing.space4))
            Toggle(
                label = stringResource(Res.string.volume_mute_all),
                checked = originalMuted,
                onCheckedChange = { onIntent(VideoEditorIntent.ToggleOriginalAudio) },
            )
        }
    }
}

/** 100%: the sound's own level, and where the slider snaps and resets to. */
private const val DEFAULT_VOLUME = 1f

/** Keyboard and TalkBack steps: 10%. */
private const val STEP = 0.1f

/** The slider snaps to 100% from within ±3%. */
private const val SNAP_DISTANCE = 0.03f

private const val PERCENT = 100
