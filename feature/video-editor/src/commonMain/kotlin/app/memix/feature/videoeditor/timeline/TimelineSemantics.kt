package app.memix.feature.videoeditor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalLocale
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
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.MemixSize
import app.memix.core.domain.project.TrimEdge
import app.memix.core.domain.project.TrimRange
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.spokenTime
import app.memix.core.ui.spokenTimeOf
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.roundToInt
import memix.feature.video_editor.generated.resources.Res
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.import_item_photo
import memix.core.ui.generated.resources.import_item_video
import memix.feature.video_editor.generated.resources.a11y_back_1s
import memix.feature.video_editor.generated.resources.a11y_clip_media
import memix.feature.video_editor.generated.resources.a11y_forward_1s
import memix.feature.video_editor.generated.resources.a11y_go_end
import memix.feature.video_editor.generated.resources.a11y_go_start
import memix.feature.video_editor.generated.resources.a11y_item
import memix.feature.video_editor.generated.resources.a11y_item_unnamed
import memix.feature.video_editor.generated.resources.a11y_playhead
import memix.feature.video_editor.generated.resources.a11y_reorder_tile
import memix.feature.video_editor.generated.resources.a11y_state_after_end
import memix.feature.video_editor.generated.resources.a11y_state_original_muted
import memix.feature.video_editor.generated.resources.a11y_state_past_end
import memix.feature.video_editor.generated.resources.a11y_state_sound_detached
import memix.feature.video_editor.generated.resources.a11y_trim_end
import memix.feature.video_editor.generated.resources.a11y_trim_start
import memix.feature.video_editor.generated.resources.a11y_trim_to_playhead
import memix.feature.video_editor.generated.resources.clip_missing_file
import memix.feature.video_editor.generated.resources.reorder_move_earlier
import memix.feature.video_editor.generated.resources.reorder_move_later
import memix.feature.video_editor.generated.resources.timeline_original_audio
import memix.feature.video_editor.generated.resources.timeline_zoom_fit
import memix.feature.video_editor.generated.resources.timeline_zoom_in
import memix.feature.video_editor.generated.resources.timeline_zoom_out
import memix.core.ui.generated.resources.tool_delete
import memix.core.ui.generated.resources.tool_duplicate
import memix.feature.video_editor.generated.resources.tool_move_here_a11y
import memix.feature.video_editor.generated.resources.tool_split
import org.jetbrains.compose.resources.stringResource

/*
 * What screen readers get from the timeline (spec P1-05 → Accessibility): the container with its zoom actions, the
 * playhead as an adjustable control, then each track's items in time order. It works like a lazy list: Compose
 * leaves nodes that sit off screen out of the tree, so each track has nodes for its items on screen, placed where
 * they're drawn, and the screen reader scrolls the track to reach the rest.
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
                traversalIndex = PLAYHEAD_ORDER
            },
    )
}

/**
 * The timeline's reading order (spec P1-05 → Accessibility): the playhead, the original-audio toggle, the tracks (each
 * row's items at 0 and up, in time order), then the "Add a meme sound" pill and the Add media tile.
 */
internal const val PLAYHEAD_ORDER = -2f
internal const val ORIGINAL_AUDIO_TOGGLE_ORDER = -1f

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

/**
 * One node per track row, each holding nodes for its items on screen, in time order. [missingSources] are the files
 * the thumbnail reader couldn't find: their clips say "File missing", as they show it.
 */
@Composable
internal fun TrackSemantics(
    controller: TimelineController,
    painter: TimelinePainter,
    timeline: TimelineUi,
    lanes: LaneLayout,
    selectedItemId: String?,
    trimRanges: TrimRanges?,
    missingSources: Set<MediaRef>,
) {
    val clipCount = timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }?.items?.size ?: 0
    val facts = ItemFacts(timeline.lengthUs, clipCount, selectedItemId, trimRanges, missingSources)
    lanes.rows.forEach { row ->
        key(row.track.id) {
            TrackRowSemantics(controller, painter, row, facts)
        }
    }
}

/** What every item node of a timeline needs to describe itself. */
private class ItemFacts(
    val lengthUs: Long,
    val clipCount: Int,
    val selectedItemId: String?,
    val trimRanges: TrimRanges?,
    val missingSources: Set<MediaRef>,
)

/**
 * A track's row: scrollable for screen readers (forward and back move the playhead a screen), with a node for each
 * item on screen, placed where it's drawn, and the selected item's two handles right after it. Which items those are
 * changes as time scrolls; where they sit is read at placement, so a scroll frame moves the nodes without
 * recomposing them.
 */
@Composable
private fun TrackRowSemantics(controller: TimelineController, painter: TimelinePainter, row: LaneRow, facts: ItemFacts) {
    val state = controller.state
    val density = LocalDensity.current
    val itemsOnScreen by remember(row, painter, density) {
        derivedStateOf { itemsOnScreen(row, painter, state.playheadUs, pxPerUs(state.dpPerSecond, density.density), state.widthPx) }
    }
    val shownItems = itemsOnScreen
    val selectedIndex = shownItems.indexOfFirst { it.id == facts.selectedItemId }
    val selectedItem = shownItems.getOrNull(selectedIndex)
    val trimRanges = facts.trimRanges.takeIf { selectedItem != null }
    val handleTargetPx = with(density) { MemixSize.touchTarget.toPx() }
    Layout(
        content = {
            shownItems.forEachIndexed { index, item -> key(item.id) { ItemSemantics(controller, row.track, item, facts, index) } }
            if (selectedItem != null && trimRanges != null) {
                // Read right after their item: traversal follows these indices, not the handles' places left and right of it.
                key(TrimEdge.START) { HandleSemantics(controller, row.track, selectedItem, TrimEdge.START, trimRanges.start, selectedIndex + START_HANDLE_ORDER) }
                key(TrimEdge.END) { HandleSemantics(controller, row.track, selectedItem, TrimEdge.END, trimRanges.end, selectedIndex + END_HANDLE_ORDER) }
            }
        },
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
        val itemHeightPx = row.itemHeightPx.roundToInt()
        val placed = measurables.mapIndexed { index, measurable ->
            val item = shownItems.getOrNull(index)
            if (item == null) {
                // A handle: its touch target, outward from the selected item's edge, moved inward at the screen's edge.
                val selected = selectedItem ?: return@mapIndexed measurable.measure(Constraints.fixed(1, 1)) to 0
                val left = xOf(selected.startUs, state.playheadUs, centerPx, pxPerUs)
                val right = left + painter.itemWidthPx(selected, pxPerUs)
                val isStart = index == shownItems.size
                val targetLeft = if (isStart) (left - handleTargetPx).coerceAtLeast(0f) else right.coerceAtMost(widthPx - handleTargetPx)
                return@mapIndexed measurable.measure(Constraints.fixed(handleTargetPx.roundToInt(), itemHeightPx)) to targetLeft.roundToInt()
            }
            val left = xOf(item.startUs, state.playheadUs, centerPx, pxPerUs)
            val shownLeft = left.coerceIn(0f, widthPx)
            val shownRight = (left + painter.itemWidthPx(item, pxPerUs)).coerceIn(0f, widthPx)
            val width = max(1, (shownRight - shownLeft).roundToInt())
            measurable.measure(Constraints.fixed(width, itemHeightPx)) to shownLeft.roundToInt()
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
 * starts at 3.2 seconds", plus its states. Double-tap selects it and moves the playhead to its start. Once selected
 * it carries its edits as actions, the single-pointer route that replaces the tool bar's taps and drags (spec
 * P1-06 → Accessibility).
 */
@Composable
private fun ItemSemantics(controller: TimelineController, track: TrackUi, item: ItemUi, facts: ItemFacts, order: Int) {
    val description = itemDescription(track, item, facts.clipCount)
    val states = itemStates(track, item, facts)
    val isSelected = item.id == facts.selectedItemId
    val actions = if (isSelected) rememberItemActions(controller, isMainClip = track.kind == TrackKind.MAIN_VIDEO) else emptyList()
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
            if (actions.isNotEmpty()) customActions = actions
            traversalIndex = order.toFloat()
        },
    )
}

/**
 * A trim handle of the selected item (spec P1-05 → Accessibility 5): "Start of Boom", value "3.2 seconds". Swiping up
 * or down moves the edge one frame (one Trim step each); the action Trim to playhead moves it to the playhead. These
 * are the single-pointer route that replaces dragging (WCAG 2.2 SC 2.5.7).
 */
@Composable
private fun HandleSemantics(controller: TimelineController, track: TrackUi, item: ItemUi, edge: TrimEdge, range: TrimRange, order: Float) {
    val itemName = spokenName(nameOf(track, item))
    val name = stringResource(if (edge == TrimEdge.START) Res.string.a11y_trim_start else Res.string.a11y_trim_end, itemName)
    val edgeUs = if (edge == TrimEdge.START) item.startUs else item.endUs
    val value = spokenTime(edgeUs)
    val trimToPlayhead = stringResource(Res.string.a11y_trim_to_playhead)
    val actions = remember(controller, item.id, edge, trimToPlayhead) {
        listOf(
            CustomAccessibilityAction(trimToPlayhead) {
                controller.onIntent(VideoEditorIntent.TrimTo(item.id, edge, controller.state.playheadUs))
                true
            },
        )
    }
    // Sources without a limit report a range around the edge; screen readers speak the value, not a percentage.
    val shownMinUs = max(range.minUs, 0L)
    val shownMaxUs = max(range.maxUs.coerceAtMost(edgeUs + UNLIMITED_RANGE_SHOWN_US), edgeUs)
    Box(
        Modifier.semantics {
            contentDescription = name
            stateDescription = value
            val edgeSeconds = edgeUs.toFloat() / MICROS_PER_SECOND
            progressBarRangeInfo = ProgressBarRangeInfo(edgeSeconds, shownMinUs.toFloat() / MICROS_PER_SECOND..shownMaxUs.toFloat() / MICROS_PER_SECOND)
            setProgress { targetSeconds ->
                when {
                    targetSeconds > edgeSeconds -> controller.onIntent(VideoEditorIntent.TrimTo(item.id, edge, edgeUs + FRAME_US))
                    targetSeconds < edgeSeconds -> controller.onIntent(VideoEditorIntent.TrimTo(item.id, edge, edgeUs - FRAME_US))
                }
                true
            }
            customActions = actions
            traversalIndex = order
        },
    )
}

/** Split, Delete, Duplicate, and for main clips Move earlier and Move later. P1-08 adds Move to playhead. */
@Composable
private fun rememberItemActions(controller: TimelineController, isMainClip: Boolean): List<CustomAccessibilityAction> {
    val split = stringResource(Res.string.tool_split)
    val delete = stringResource(UiRes.string.tool_delete)
    val duplicate = stringResource(UiRes.string.tool_duplicate)
    val earlier = stringResource(Res.string.reorder_move_earlier)
    val later = stringResource(Res.string.reorder_move_later)
    val moveToPlayhead = stringResource(Res.string.tool_move_here_a11y)
    return remember(controller, isMainClip, split, delete, duplicate, earlier, later, moveToPlayhead) {
        fun action(label: String, intent: VideoEditorIntent) = CustomAccessibilityAction(label) {
            controller.onIntent(intent)
            true
        }
        val edits = listOf(
            action(split, VideoEditorIntent.SplitSelected),
            action(delete, VideoEditorIntent.DeleteSelected),
            action(duplicate, VideoEditorIntent.DuplicateSelected),
        )
        // Main clips reorder; every other item moves to the playhead, the single-pointer route for Move (spec P1-06).
        if (!isMainClip) return@remember edits + action(moveToPlayhead, VideoEditorIntent.MoveHereSelected)
        edits + action(earlier, VideoEditorIntent.MoveSelectedClip(steps = -1)) + action(later, VideoEditorIntent.MoveSelectedClip(steps = 1))
    }
}

@Composable
private fun itemDescription(track: TrackUi, item: ItemUi, clipCount: Int): String {
    val duration = spokenTime(item.durationUs)
    val start = spokenTime(item.startUs)
    if (track.kind == TrackKind.MAIN_VIDEO) {
        val kind = stringResource(if (item.isPhoto) UiRes.string.import_item_photo else UiRes.string.import_item_video)
        return stringResource(Res.string.a11y_clip_media, kind, item.clipNumber, clipCount, duration, start)
    }
    val trackName = stringResource(trackNameOf(track.kind))
    return when (val label = item.label) {
        is ItemLabel.Written -> stringResource(Res.string.a11y_item, trackName, label.text, duration, start)
        ItemLabel.OriginalAudio -> {
            val originalAudio = stringResource(Res.string.timeline_original_audio)
            stringResource(Res.string.a11y_item, trackName, originalAudio, duration, start)
        }
        is ItemLabel.SoundTitle -> stringResource(Res.string.a11y_item, trackName, label.title.inLanguage(LocalLocale.current.language), duration, start)
        // A sound the catalog doesn't know, or a sticker the user cut out (P2-07), has no name to say; saying the
        // track's name twice would sound like a glitch.
        ItemLabel.None -> stringResource(Res.string.a11y_item_unnamed, trackName, duration, start)
    }
}

/** File missing, muted, sound detached and past the end, in that order, joined for the screen reader. */
@Composable
private fun itemStates(track: TrackUi, item: ItemUi, facts: ItemFacts): String {
    val isMainClip = track.kind == TrackKind.MAIN_VIDEO
    val media = item.media
    // First, because it explains the rest: the clip plays black, and the editor's message says to delete it.
    val isMissing = isMainClip && media != null && media in facts.missingSources
    val missing = if (isMissing) stringResource(Res.string.clip_missing_file) else null
    val hasOwnSound = isMainClip && !item.isPhoto && media?.hasAudio != false
    val muted = if (hasOwnSound && (track.muted || item.silenced)) stringResource(Res.string.a11y_state_original_muted) else null
    val detached = if (item.soundDetached) stringResource(Res.string.a11y_state_sound_detached) else null
    val pastEnd = when {
        item.startUs >= facts.lengthUs -> stringResource(Res.string.a11y_state_after_end)
        item.endUs > facts.lengthUs -> stringResource(Res.string.a11y_state_past_end)
        else -> null
    }
    return listOfNotNull(missing, muted, detached, pastEnd).joinToString(STATE_SEPARATOR)
}

private const val MICROS_PER_TENTH = 100_000L

/** The selected item's handles are read right after it, start first. */
private const val START_HANDLE_ORDER = 0.25f
private const val END_HANDLE_ORDER = 0.5f

/** How far past the edge a handle with no source limit reports its range: an hour. */
private const val UNLIMITED_RANGE_SHOWN_US = 3_600_000_000L

/** Between states, as Android joins a node's states. */
private const val STATE_SEPARATOR = ", "

/**
 * Reorder mode for screen readers (spec P1-06 → Accessibility → Reorder mode): the tiles as a list, "Video, clip 2 of
 * 3", the moving one selected. Double-tap on another tile moves the clip to its place; Move earlier, Move later and
 * Done are the tool bar's buttons. Each node sits on its tile, read at placement as the tiles slide.
 */
@Composable
internal fun ReorderSemantics(controller: TimelineController, strip: ReorderStrip, mainRow: LaneRow, rulerHeightPx: Float, laneScroll: ScrollState) {
    val order = strip.order
    Layout(
        content = {
            order.forEachIndexed { index, id ->
                key(id) { TileSemantics(controller, strip.clips[id], isMoving = id == strip.movingId, position = index + 1, count = order.size) }
            }
        },
        modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true },
    ) { measurables, constraints ->
        val tilePx = strip.tileSizePx.roundToInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(tilePx, tilePx)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = (rulerHeightPx + mainRow.itemTopPx - laneScroll.value).roundToInt()
            placeables.forEachIndexed { index, placeable -> placeable.place(strip.tileLeftPx(order[index]).roundToInt(), top) }
        }
    }
}

@Composable
private fun TileSemantics(controller: TimelineController, clip: ItemUi?, isMoving: Boolean, position: Int, count: Int) {
    val kind = stringResource(if (clip?.isPhoto == true) UiRes.string.import_item_photo else UiRes.string.import_item_video)
    val description = stringResource(Res.string.a11y_reorder_tile, kind, position, count)
    Box(
        Modifier.semantics {
            contentDescription = description
            selected = isMoving
            if (!isMoving) {
                onClick {
                    controller.onIntent(VideoEditorIntent.ReorderMoveTo(position - 1))
                    true
                }
            }
            traversalIndex = position.toFloat()
        },
    )
}
