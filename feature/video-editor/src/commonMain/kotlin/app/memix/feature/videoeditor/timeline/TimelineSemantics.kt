package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.MemixSize
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.spokenTime
import app.memix.core.ui.spokenTimeOf
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_back_1s
import memix.feature.video_editor.generated.resources.a11y_clip_media
import memix.feature.video_editor.generated.resources.a11y_forward_1s
import memix.feature.video_editor.generated.resources.a11y_go_end
import memix.feature.video_editor.generated.resources.a11y_go_start
import memix.feature.video_editor.generated.resources.a11y_item
import memix.feature.video_editor.generated.resources.a11y_playhead
import memix.feature.video_editor.generated.resources.a11y_state_after_end
import memix.feature.video_editor.generated.resources.a11y_state_original_muted
import memix.feature.video_editor.generated.resources.a11y_state_past_end
import memix.feature.video_editor.generated.resources.a11y_state_sound_detached
import memix.feature.video_editor.generated.resources.import_item_photo
import memix.feature.video_editor.generated.resources.import_item_video
import memix.feature.video_editor.generated.resources.timeline_original_audio
import memix.feature.video_editor.generated.resources.timeline_zoom_fit
import memix.feature.video_editor.generated.resources.timeline_zoom_in
import memix.feature.video_editor.generated.resources.timeline_zoom_out
import memix.feature.video_editor.generated.resources.track_audio
import memix.feature.video_editor.generated.resources.track_effect
import memix.feature.video_editor.generated.resources.track_meme_sound
import memix.feature.video_editor.generated.resources.track_overlay
import memix.feature.video_editor.generated.resources.track_sticker
import memix.feature.video_editor.generated.resources.track_text
import memix.feature.video_editor.generated.resources.track_video
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * What screen readers get from the timeline (spec P1-05 → Accessibility): the container with its zoom actions, the
 * playhead as an adjustable control, then each track's items in time order. Items get their nodes from the model,
 * not from the drawing; the ones on screen are placed where they're drawn, and each track can be scrolled by the
 * screen reader to reach the rest.
 */

/** The zoom actions of the timeline container ("Timeline": Zoom in, Zoom out, Show whole video). */
@Composable
internal fun rememberZoomActions(controller: TimelineController): List<CustomAccessibilityAction> {
    val zoomIn = stringResource(Res.string.timeline_zoom_in)
    val zoomOut = stringResource(Res.string.timeline_zoom_out)
    val wholeVideo = stringResource(Res.string.timeline_zoom_fit)
    return remember(controller, zoomIn, zoomOut, wholeVideo) {
        listOf(
            CustomAccessibilityAction(zoomIn) { controller.canZoomIn.also { if (it) controller.zoomIn() } },
            CustomAccessibilityAction(zoomOut) { controller.canZoomOut.also { if (it) controller.zoomOut() } },
            CustomAccessibilityAction(wholeVideo) { controller.canZoomOut.also { if (it) controller.showWholeVideo() } },
        )
    }
}

internal fun Modifier.timelineSemantics(label: String, zoomActions: List<CustomAccessibilityAction>): Modifier = semantics {
    contentDescription = label
    isTraversalGroup = true
    customActions = zoomActions
}

/**
 * The playhead: "Playhead", "3.2 seconds of 8 seconds". Swiping up or down (or the volume keys) steps one frame;
 * its actions jump a second, or to the start or end. Its node sits over the playhead's head on the ruler.
 */
@Composable
internal fun BoxScope.PlayheadSemantics(controller: TimelineController, lengthUs: Long, rulerHeight: Dp) {
    val state = controller.state
    // The spoken time changes ten times a second at most, so only this node recomposes while playing.
    val tenthsUs by remember(state) { derivedStateOf { state.playheadUs / MICROS_PER_TENTH * MICROS_PER_TENTH } }
    val value = spokenTimeOf(tenthsUs, lengthUs)
    val name = stringResource(Res.string.a11y_playhead)
    val actions = rememberPlayheadActions(controller)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .size(MemixSize.touchTarget, rulerHeight)
            .semantics {
                contentDescription = name
                stateDescription = value
                val shownSeconds = tenthsUs.toFloat() / MICROS_PER_SECOND
                progressBarRangeInfo = ProgressBarRangeInfo(shownSeconds, 0f..lengthUs.toFloat() / MICROS_PER_SECOND)
                // The screen reader asks for a bigger jump; the playhead moves exactly one frame that way.
                setProgress { targetSeconds ->
                    when {
                        targetSeconds > shownSeconds -> controller.seekNow(state.playheadUs + FRAME_US)
                        targetSeconds < shownSeconds -> controller.seekNow(state.playheadUs - FRAME_US)
                    }
                    true
                }
                customActions = actions
            },
    )
}

@Composable
private fun rememberPlayheadActions(controller: TimelineController): List<CustomAccessibilityAction> {
    val forward = stringResource(Res.string.a11y_forward_1s)
    val back = stringResource(Res.string.a11y_back_1s)
    val start = stringResource(Res.string.a11y_go_start)
    val end = stringResource(Res.string.a11y_go_end)
    return remember(controller, forward, back, start, end) {
        val state = controller.state
        listOf(
            CustomAccessibilityAction(forward) {
                controller.seekNow(state.playheadUs + MICROS_PER_SECOND)
                true
            },
            CustomAccessibilityAction(back) {
                controller.seekNow(state.playheadUs - MICROS_PER_SECOND)
                true
            },
            CustomAccessibilityAction(start) {
                controller.seekNow(0)
                true
            },
            CustomAccessibilityAction(end) {
                controller.seekNow(controller.timeline.lengthUs)
                true
            },
        )
    }
}

/** One node per track row, each holding nodes for its items on screen, in time order. */
@Composable
internal fun TrackSemantics(controller: TimelineController, painter: TimelinePainter, timeline: TimelineUi, lanes: LaneLayout, selectedItemId: String?) {
    val clipCount = timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.size ?: 0
    lanes.rows.forEach { row ->
        key(row.track.id) {
            TrackRowSemantics(controller, painter, row, ItemFacts(timeline.lengthUs, clipCount, selectedItemId))
        }
    }
}

/** What every item node of a timeline needs to describe itself. */
private class ItemFacts(val lengthUs: Long, val clipCount: Int, val selectedItemId: String?)

/**
 * A track's row: scrollable for screen readers (forward and back move the playhead a screen), with a node for each
 * item on screen, placed where it's drawn. Which items those are changes as time scrolls; where they sit is read at
 * placement, so a scroll frame moves the nodes without recomposing them.
 */
@Composable
private fun TrackRowSemantics(controller: TimelineController, painter: TimelinePainter, row: LaneRow, facts: ItemFacts) {
    val state = controller.state
    val density = LocalDensity.current
    val itemsOnScreen by remember(row, painter, density) {
        derivedStateOf { itemsOnScreen(row, painter, state.playheadUs, pxPerUs(state.dpPerSecond, density.density), state.widthPx) }
    }
    val shownItems = itemsOnScreen
    Layout(
        content = { shownItems.forEach { item -> key(item.id) { ItemSemantics(controller, row.track, item, facts) } } },
        modifier = Modifier
            .offset { IntOffset(0, row.topPx.roundToInt()) }
            .fillMaxWidth()
            .height(with(density) { row.heightPx.toDp() })
            .semantics {
                isTraversalGroup = true
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.playheadUs * pxPerUs(state.dpPerSecond, density.density) },
                    maxValue = { facts.lengthUs * pxPerUs(state.dpPerSecond, density.density) },
                )
                scrollBy { x, _ ->
                    val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
                    if (pxPerUs > 0f) controller.seekNow(state.playheadUs + (x / pxPerUs).toLong())
                    true
                }
            },
    ) { measurables, constraints ->
        val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
        val widthPx = constraints.maxWidth.toFloat()
        val centerPx = widthPx / 2
        val itemTopPx = (row.itemTopPx - row.topPx).roundToInt()
        val placed = measurables.mapIndexed { index, measurable ->
            val item = shownItems[index]
            val left = xOf(item.startUs, state.playheadUs, centerPx, pxPerUs)
            val shownLeft = left.coerceIn(0f, widthPx)
            val shownRight = (left + painter.itemWidthPx(item, pxPerUs)).coerceIn(0f, widthPx)
            val width = max(1, (shownRight - shownLeft).roundToInt())
            measurable.measure(Constraints.fixed(width, row.itemHeightPx.roundToInt())) to shownLeft.roundToInt()
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, x) -> placeable.place(x, itemTopPx) }
        }
    }
}

private fun itemsOnScreen(row: LaneRow, painter: TimelinePainter, playheadUs: Long, pxPerUs: Float, widthPx: Int): List<ItemUi> {
    if (pxPerUs <= 0f || widthPx <= 0) return emptyList()
    val centerPx = widthPx / 2f
    val shown = ArrayList<ItemUi>()
    row.track.forEachItemBetween(timeAt(0f, playheadUs, centerPx, pxPerUs), timeAt(widthPx.toFloat(), playheadUs, centerPx, pxPerUs)) { item ->
        val left = xOf(item.startUs, playheadUs, centerPx, pxPerUs)
        if (left + painter.itemWidthPx(item, pxPerUs) > 0f && left < widthPx) shown += item
    }
    return shown
}

/**
 * One item: "Video, clip 2 of 3, 4.5 seconds long, starts at 3.2 seconds" or "Meme sound, Boom, 1.4 seconds long,
 * starts at 3.2 seconds", plus its states. Double-tap selects it and moves the playhead to its start.
 */
@Composable
private fun ItemSemantics(controller: TimelineController, track: TrackUi, item: ItemUi, facts: ItemFacts) {
    val description = itemDescription(track, item, facts.clipCount)
    val states = itemStates(track, item, facts.lengthUs)
    val isSelected = item.id == facts.selectedItemId
    Box(
        Modifier.semantics {
            contentDescription = description
            if (states.isNotEmpty()) stateDescription = states
            selected = isSelected
            onClick {
                controller.onIntent(VideoEditorIntent.SelectItem(item.id))
                controller.seekNow(item.startUs)
                true
            }
        },
    )
}

@Composable
private fun itemDescription(track: TrackUi, item: ItemUi, clipCount: Int): String {
    val duration = spokenTime(item.durationUs)
    val start = spokenTime(item.startUs)
    if (track.kind == TrackKind.MAIN_VIDEO) {
        val kind = stringResource(if (item.isPhoto) Res.string.import_item_photo else Res.string.import_item_video)
        return stringResource(Res.string.a11y_clip_media, kind, item.clipNumber, clipCount, duration, start)
    }
    val trackName = stringResource(trackNameOf(track.kind))
    // TODO(P1-08): a meme sound says its title. Until then, and for stickers (P4-12) and effects (P4-06), an item
    // without a name in the model says its track's name in that place.
    val label = when (val itemLabel = item.label) {
        is ItemLabel.Written -> itemLabel.text
        ItemLabel.OriginalAudio -> stringResource(Res.string.timeline_original_audio)
        ItemLabel.None -> trackName
    }
    return stringResource(Res.string.a11y_item, trackName, label, duration, start)
}

/** Muted, sound detached and past the end, in that order, joined for the screen reader. */
@Composable
private fun itemStates(track: TrackUi, item: ItemUi, lengthUs: Long): String {
    val hasOwnSound = track.kind == TrackKind.MAIN_VIDEO && !item.isPhoto && item.media?.hasAudio != false
    val muted = if (hasOwnSound && (track.muted || item.silenced)) stringResource(Res.string.a11y_state_original_muted) else null
    val detached = if (item.soundDetached) stringResource(Res.string.a11y_state_sound_detached) else null
    val pastEnd = when {
        item.startUs >= lengthUs -> stringResource(Res.string.a11y_state_after_end)
        item.endUs > lengthUs -> stringResource(Res.string.a11y_state_past_end)
        else -> null
    }
    return listOfNotNull(muted, detached, pastEnd).joinToString(STATE_SEPARATOR)
}

private fun trackNameOf(kind: TrackKind): StringResource = when (kind) {
    TrackKind.MAIN_VIDEO -> Res.string.track_video
    TrackKind.OVERLAY -> Res.string.track_overlay
    TrackKind.TEXT -> Res.string.track_text
    TrackKind.STICKER -> Res.string.track_sticker
    TrackKind.MEME_SOUND -> Res.string.track_meme_sound
    TrackKind.AUDIO -> Res.string.track_audio
    TrackKind.EFFECT -> Res.string.track_effect
}

private const val MICROS_PER_TENTH = 100_000L

/** Between states, as Android joins a node's states. */
private const val STATE_SEPARATOR = ", "
