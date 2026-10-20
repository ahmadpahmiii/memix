package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.model.project.TrackKind
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.timeline_original_audio
import org.jetbrains.compose.resources.stringResource

/**
 * The original-audio toggle (spec P1-05 → Leading area, P1-09): a `track-height-video` square in `surface-raised` with
 * `radius-sm`, inside a 48 dp target, its right edge `space-2` before 0:00 in the main row. Sound on: `sounds` in
 * `text-secondary`; muted: `volume_off` in `text`, the brighter state, since muted is the exception. A tap mutes or
 * unmutes every clip's own sound. It moves with time and the lanes' vertical scroll, clipped below the ruler, and is
 * read as the switch "Original audio" right after the playhead. Hidden in reorder mode, where only the tiles act.
 */
@Composable
internal fun BoxScope.OriginalAudioToggle(
    controller: TimelineController,
    lanes: LaneLayout,
    muted: Boolean,
    laneScroll: ScrollState,
    rulerHeight: Dp,
    onToggle: () -> Unit,
) {
    val state = controller.state
    if (state.reorder != null) return
    val mainRow = lanes.rows.firstOrNull { it.track.kind == TrackKind.MAIN_VIDEO } ?: return
    val label = stringResource(Res.string.timeline_original_audio)
    val interactionSource = remember { MutableInteractionSource() }
    val fill by pressedColor(interactionSource, MemixColors.surfaceRaised, MemixColors.hairline)
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val tilePx = with(density) { MemixSize.trackHeightVideo.toPx() }
    val gapPx = with(density) { MemixSpacing.space2.toPx() }
    Box(Modifier.matchParentSize().padding(top = rulerHeight).clipToBounds()) {
        Box(
            Modifier
                // Read at placement: playback, scrolling and zooming move it without recomposing it.
                .offset {
                    val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
                    val tileRight = xOf(0, state.playheadUs, state.widthPx / 2f, pxPerUs) - gapPx
                    val top = mainRow.topPx + (mainRow.heightPx - targetPx) / 2 - laneScroll.value
                    IntOffset((tileRight - tilePx - (targetPx - tilePx) / 2).roundToInt(), top.roundToInt())
                }
                .size(MemixSize.touchTarget)
                // A tap here toggles, not scrubs: the toggle takes the press before the timeline's tap and scroll.
                .toggleable(value = !muted, interactionSource = interactionSource, indication = null, role = Role.Switch, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = label
                    traversalIndex = ORIGINAL_AUDIO_TOGGLE_ORDER
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(MemixSize.trackHeightVideo)
                    .focusRing(interactionSource, MemixShapes.radiusSm)
                    .clip(MemixShapes.radiusSm)
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                if (muted) {
                    Icon(MemixIcons.VolumeOff, contentDescription = null, tint = MemixColors.text)
                } else {
                    Icon(MemixIcons.Sounds, contentDescription = null, tint = MemixColors.textSecondary)
                }
            }
        }
    }
}
