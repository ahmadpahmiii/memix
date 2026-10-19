package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.memix.core.model.project.TrackKind
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.timeline_add_media
import org.jetbrains.compose.resources.stringResource

/**
 * The Add media tile (spec P1-05 → Trailing area, P1-16): a `track-height-video` square in `surface-raised` with
 * `radius-sm` and the plus in `text`, inside a 48 dp target, `space-2` after the end of the video in the main row. It
 * moves with time and with the lanes' vertical scroll, clipped below the ruler; in an empty project it sits just right
 * of the playhead at 0:00. Pressed: `hairline`. Hidden in reorder mode and while a trim or move is held. Screen readers
 * reach it after the "Add a meme sound" pill; [focusRequester] brings focus back to it after its pick.
 */
@Composable
internal fun BoxScope.AddMediaTile(
    controller: TimelineController,
    lanes: LaneLayout,
    lengthUs: Long,
    laneScroll: ScrollState,
    rulerHeight: Dp,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val state = controller.state
    if (state.reorder != null || state.trim != null || state.move != null) return
    val mainRow = lanes.rows.firstOrNull { it.track.kind == TrackKind.MAIN_VIDEO } ?: return
    val label = stringResource(Res.string.timeline_add_media)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val targetPx = with(density) { MemixSize.touchTarget.toPx() }
    val tilePx = with(density) { MemixSize.trackHeightVideo.toPx() }
    val gapPx = with(density) { MemixSpacing.space2.toPx() }
    Box(Modifier.matchParentSize().padding(top = rulerHeight).clipToBounds()) {
        Box(
            Modifier
                // Read at placement: playback, scrolling and zooming move the tile without recomposing it.
                .offset {
                    val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
                    val tileLeft = xOf(lengthUs, state.playheadUs, state.widthPx / 2f, pxPerUs) + gapPx
                    val top = mainRow.topPx + (mainRow.heightPx - targetPx) / 2 - laneScroll.value
                    IntOffset((tileLeft - (targetPx - tilePx) / 2).roundToInt(), top.roundToInt())
                }
                .size(MemixSize.touchTarget)
                .focusRequester(focusRequester)
                .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    traversalIndex = AFTER_ADD_SOUND_PILL
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(MemixSize.trackHeightVideo)
                    .focusRing(interactionSource, MemixShapes.radiusSm)
                    .clip(MemixShapes.radiusSm)
                    .background(if (pressed) MemixColors.hairline else MemixColors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.text)
            }
        }
    }
}

/** After the tracks, the handles and the "Add a meme sound" pill (spec P1-05 → Accessibility 6). */
private const val AFTER_ADD_SOUND_PILL = 2f
