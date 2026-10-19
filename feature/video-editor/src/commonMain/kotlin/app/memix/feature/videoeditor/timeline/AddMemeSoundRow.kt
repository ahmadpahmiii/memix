package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.feature.videoeditor.LandedItems
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.timeline_add_meme_sound
import org.jetbrains.compose.resources.stringResource

/**
 * The "Add a meme sound" pill (spec P1-05 → "Add a meme sound" row): `primary-subtle` and `radius-full`, the plus and
 * the label in `primary` (it's meme-sound UI), in [addRow] under the sound lanes, centered on the playhead and drawn
 * over it. It stays put while time scrolls, because it marks where a sound will land, and moves with the lanes'
 * vertical scroll, clipped below the ruler. Hidden in reorder mode and while an item is carried; its row stays, so a
 * carried meme sound can be dropped there for a new lane. Screen readers reach it after the tracks.
 */
@Composable
internal fun BoxScope.AddMemeSoundPill(
    controller: TimelineController,
    addRow: AddSoundRow,
    motion: EditMotion,
    laneScroll: ScrollState,
    rulerHeight: Dp,
    onClick: () -> Unit,
) {
    val state = controller.state
    if (state.reorder != null || state.move != null) return
    val label = stringResource(Res.string.timeline_add_meme_sound)
    val interactionSource = remember { MutableInteractionSource() }
    val pillHeight = with(LocalDensity.current) { addRow.heightPx.toDp() } - MemixSpacing.space1 * 2
    Box(Modifier.matchParentSize().padding(top = rulerHeight).clipToBounds()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                // Read at placement: scrolling the lanes moves the pill without recomposing it.
                .offset { IntOffset(0, (addRow.topPx + motion.laneShiftPx(EditMotion.ADD_SOUND_ROW_KEY) - laneScroll.value).roundToInt()) }
                .padding(vertical = MemixSpacing.space1)
                .height(pillHeight)
                .focusRing(interactionSource, MemixShapes.radiusFull)
                .clip(MemixShapes.radiusFull)
                // primary-subtle is a 16% wash: on its own `canvas` base it covers the playhead line, as the spec draws it.
                .background(MemixColors.canvas)
                .background(MemixColors.primarySubtle)
                .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .semantics { traversalIndex = AFTER_TRACKS }
                .padding(horizontal = MemixSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MemixIcons.Create, contentDescription = null, tint = MemixColors.primary)
            Text(label, MemixTheme.type.label, color = MemixColors.primary, maxLines = 1)
        }
    }
}

/**
 * When an item lands on a lane scrolled out of view, the lanes scroll to show it (spec P1-05 → Meme sounds), over
 * 200 ms (at once with reduce motion). [viewportHeightPx] is how tall the lanes' window is.
 */
@Composable
internal fun ShowLandedLane(landed: LandedItems?, lanes: LaneLayout, laneScroll: ScrollState, viewportHeightPx: () -> Int) {
    val reduceMotion = MemixTheme.reduceMotion
    LaunchedEffect(landed) {
        val row = landed?.itemIds?.firstNotNullOfOrNull(lanes::rowOf) ?: return@LaunchedEffect
        // One frame, so a new lane is laid out and the lanes can scroll that far.
        withFrameNanos { }
        val viewport = viewportHeightPx()
        if (viewport <= 0) return@LaunchedEffect
        val top = row.topPx.roundToInt()
        val bottom = (row.topPx + row.heightPx).roundToInt()
        val target = when {
            top < laneScroll.value -> top
            bottom > laneScroll.value + viewport -> bottom - viewport
            else -> return@LaunchedEffect
        }
        if (reduceMotion) laneScroll.scrollTo(target) else laneScroll.animateScrollTo(target, tween(MemixMotion.durationSheet))
    }
}

/** After every track and handle in the timeline's reading order (spec P1-05 → Accessibility 6). */
private const val AFTER_TRACKS = 1f
