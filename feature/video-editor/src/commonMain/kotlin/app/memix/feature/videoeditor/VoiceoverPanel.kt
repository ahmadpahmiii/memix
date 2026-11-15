package app.memix.feature.videoeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.domain.voiceover.VoiceRoute
import app.memix.core.ui.formatTimecode
import kotlin.math.sqrt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.import_cancel
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.tool_voiceover
import memix.feature.video_editor.generated.resources.voiceover_bluetooth_note
import memix.feature.video_editor.generated.resources.voiceover_countdown_a11y
import memix.feature.video_editor.generated.resources.voiceover_denied
import memix.feature.video_editor.generated.resources.voiceover_hint
import memix.feature.video_editor.generated.resources.voiceover_muted_note
import memix.feature.video_editor.generated.resources.voiceover_open_settings
import memix.feature.video_editor.generated.resources.voiceover_quiet_hint
import memix.feature.video_editor.generated.resources.voiceover_record
import memix.feature.video_editor.generated.resources.voiceover_recording
import memix.feature.video_editor.generated.resources.voiceover_saved
import memix.feature.video_editor.generated.resources.voiceover_stop
import memix.feature.video_editor.generated.resources.voiceover_stop_a11y
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/** What the one phase button says and does; it changes from Record to Cancel to Stop. */
private class PhaseButton(
    val label: String,
    val onClick: () -> Unit,
    val variant: ButtonVariant,
    val icon: DrawableResource?,
    val spokenName: String? = null,
)

/**
 * The Voiceover panel (spec docs/ux/specs/P3-08-voiceover.md): Ready (one message, Record), the 3, 2, 1 countdown with
 * Cancel, and Recording (Stop first, status row, then the level meter or the quiet hint). It covers the timeline and the
 * tool bar like the Volume panel. Nothing here is blue: `danger` carries the recording state, always with a word or an
 * icon. Shown when [panel] isn't null.
 *
 * The Record, Cancel and Stop button is one movable composable, so it is the same node in every phase and screen-reader
 * focus stays on it while its label changes (spec → Accessibility → Focus).
 */
@Composable
internal fun VoiceoverPanel(
    panel: VoiceoverPanelUi?,
    microphone: MicrophoneHooks,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phaseButton = remember {
        movableContentOf { spec: PhaseButton ->
            val spoken = spec.spokenName
            Button(
                spec.label,
                spec.onClick,
                Modifier.fillMaxWidth().then(if (spoken == null) Modifier else Modifier.semantics { contentDescription = spoken }),
                variant = spec.variant,
                leadingIcon = spec.icon,
            )
        }
    }
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.tool_voiceover),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseVoiceoverPanel) },
        modifier = modifier,
    ) {
        // The panel's last state stays on screen while it slides away.
        val shown = panel ?: return@ToolPanel
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
            when {
                shown.phase == VoicePhase.COUNTING_DOWN -> Countdown(shown.countdown, phaseButton, onIntent)
                shown.phase == VoicePhase.RECORDING -> Recording(shown, phaseButton, onIntent)
                shown.microphoneDenied -> Denied(microphone)
                else -> Ready(shown, phaseButton, microphone, onIntent)
            }
        }
    }
}

// One message above Record, never two: the saved line right after a take, else the route note, else the hint (spec → Ready).
@Composable
private fun Ready(panel: VoiceoverPanelUi, phaseButton: @Composable (PhaseButton) -> Unit, microphone: MicrophoneHooks, onIntent: (VideoEditorIntent) -> Unit) {
    val saved = panel.savedAtUs
    when {
        saved != null -> Text(stringResource(Res.string.voiceover_saved, formatTimecode(saved)), MemixTheme.type.body, color = MemixColors.textSecondary)
        // A Bluetooth headset counts as headphones, so the video's sound plays in it; no headphones: the video plays silent.
        panel.route == VoiceRoute.PHONE_SPEAKER -> Text(stringResource(Res.string.voiceover_muted_note), MemixTheme.type.label, color = MemixColors.textSecondary)
        panel.route == VoiceRoute.BLUETOOTH -> Text(stringResource(Res.string.voiceover_bluetooth_note), MemixTheme.type.label, color = MemixColors.textSecondary)
        else -> Text(stringResource(Res.string.voiceover_hint), MemixTheme.type.body, color = MemixColors.textSecondary)
    }
    phaseButton(PhaseButton(stringResource(Res.string.voiceover_record), { onIntent(VideoEditorIntent.VoiceoverRecord(microphone.isGranted())) }, ButtonVariant.Danger, MemixIcons.Mic))
}

@Composable
private fun Denied(microphone: MicrophoneHooks) {
    // The explanation takes focus, so a screen reader reads why Record is gone (spec → States).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Text(stringResource(Res.string.voiceover_denied), MemixTheme.type.body, Modifier.focusRequester(focus).focusable(), color = MemixColors.textSecondary)
    Button(stringResource(Res.string.voiceover_open_settings), microphone.openSettings, Modifier.fillMaxWidth())
}

// One polite announcement for the whole countdown; the digits are only for the eyes (spec → Accessibility).
@Composable
private fun Countdown(second: Int, phaseButton: @Composable (PhaseButton) -> Unit, onIntent: (VideoEditorIntent) -> Unit) {
    val announcement = stringResource(Res.string.voiceover_countdown_a11y)
    Box(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement },
        contentAlignment = Alignment.Center,
    ) {
        Text(second.toString(), MemixTheme.type.displayXl, Modifier.clearAndSetSemantics {}, textAlign = TextAlign.Center, maxLines = 1)
    }
    phaseButton(PhaseButton(stringResource(UiRes.string.import_cancel), { onIntent(VideoEditorIntent.VoiceoverStop) }, ButtonVariant.Secondary, null))
}

// Stop first, so it never scrolls out at any font size; then the status row; then the meter, or the quiet hint in its place.
@Composable
private fun Recording(panel: VoiceoverPanelUi, phaseButton: @Composable (PhaseButton) -> Unit, onIntent: (VideoEditorIntent) -> Unit) {
    phaseButton(
        PhaseButton(
            stringResource(Res.string.voiceover_stop),
            { onIntent(VideoEditorIntent.VoiceoverStop) },
            ButtonVariant.Danger,
            MemixIcons.Stop,
            spokenName = stringResource(Res.string.voiceover_stop_a11y),
        ),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Static: nothing loops (MOTION 2). The word and the icon on Stop carry the state, never the color alone.
        Box(Modifier.clearAndSetSemantics {}.size(MemixSpacing.space3).background(MemixColors.danger, CircleShape))
        Text(
            stringResource(Res.string.voiceover_recording),
            MemixTheme.type.label,
            Modifier.padding(start = MemixSpacing.space2).weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        ElapsedTime(panel.live)
    }
    if (panel.quiet) {
        // A blind user can't see the meter, so this is their only sign that the microphone hears nothing: a live region.
        Text(stringResource(Res.string.voiceover_quiet_hint), MemixTheme.type.label, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    } else {
        LevelMeter(panel.live)
    }
}

// The take's length counts up with the picture; only this small view reads it, so the panel doesn't recompose per frame.
@Composable
private fun ElapsedTime(live: VoiceoverLive) {
    Text(formatTimecode(live.elapsedUs), MemixTheme.type.timecode, Modifier.clearAndSetSemantics {}, maxLines = 1)
}

// A reading, not an animation: the fill is drawn from the latest level, which the recorder updates at most 20 times a second.
@Composable
private fun LevelMeter(live: VoiceoverLive) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MemixSize.railHeight)
            .clearAndSetSemantics {}
            .drawBehind {
                drawRect(MemixColors.hairline)
                // Speech peaks at a tenth or so of full scale; the square root spreads the quiet end so a voice moves the bar.
                drawRect(MemixColors.text, Offset.Zero, Size(size.width * sqrt(live.level.coerceIn(0f, 1f)), size.height))
            },
    )
}
