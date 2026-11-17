package app.memix.feature.videoeditor.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.round
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.Menu
import app.memix.core.designsystem.component.MenuItem
import app.memix.core.domain.project.TrimEdge
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.formatTimecode
import app.memix.core.ui.appLanguageCode
import app.memix.feature.videoeditor.EditorPreview
import app.memix.feature.videoeditor.KeyframeBarUi
import app.memix.feature.videoeditor.KeyframeMarkerUi
import app.memix.feature.videoeditor.LandedItems
import app.memix.feature.videoeditor.OriginalAudioUi
import app.memix.feature.videoeditor.ReorderUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.abs
import kotlinx.collections.immutable.persistentSetOf
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.clip_missing_file
import memix.feature.video_editor.generated.resources.timeline_a11y
import memix.feature.video_editor.generated.resources.timeline_my_sound
import memix.feature.video_editor.generated.resources.timeline_original_audio
import memix.feature.video_editor.generated.resources.timeline_voiceover
import memix.feature.video_editor.generated.resources.timeline_zoom_fit
import memix.feature.video_editor.generated.resources.timeline_zoom_in
import memix.feature.video_editor.generated.resources.timeline_zoom_menu
import memix.feature.video_editor.generated.resources.timeline_zoom_out
import org.jetbrains.compose.resources.stringResource

/**
 * The video editor's timeline (spec docs/ux/specs/P1-05-timeline.md): a pinned ruler over lanes that scroll
 * vertically, a playhead fixed in the center, and time that scrolls under it.
 *
 * The playhead time and the zoom change every frame, so they live in a [TimelineState] that only draw blocks,
 * placement and gesture code read: scrolling, zooming and playback redraw without recomposing (mobile-performance
 * skill). The playhead follows [preview]'s position, except while the timeline itself moves time (a finger down, a
 * fling, a seek animation); then it scrubs the preview through [onIntent].
 *
 * The selected item's handles trim it (spec P1-06 → Trim) within [trimRanges]: a drag on a handle wins over scrolling,
 * moves only that edge at frame rate and commits once, on release.
 *
 * Items on every lane but the main track move by a long-press drag (spec P1-06 → Move): the same finger carries the
 * item in time and across lanes of its kind, and the drop commits once. The "Add a meme sound" pill sits on its row at
 * the playhead (P1-08), and the Add media tile after the end of the video (P1-16). [addSoundPillFocus] and
 * [addMediaTileFocus] let the editor bring focus back to them after the sheet or picker they opened closes.
 *
 * The original-audio toggle sits before 0:00 in the main row when a clip has its own sound (P1-09, [originalAudio]).
 */
@Composable
internal fun Timeline(
    timeline: TimelineUi,
    selectedItemId: String?,
    keyframeBar: KeyframeBarUi?,
    trimRanges: TrimRanges?,
    reorder: ReorderUi?,
    landed: LandedItems?,
    revealed: LandedItems?,
    preview: EditorPreview?,
    thumbnails: TimelineThumbnails?,
    waveforms: TimelineWaveforms?,
    originalAudio: OriginalAudioUi?,
    addSoundPillFocus: FocusRequester,
    addMediaTileFocus: FocusRequester,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Time always runs left to right, whatever the language (spec → Structure).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        TimelineContent(timeline, selectedItemId, keyframeBar, trimRanges, reorder, landed, revealed, preview, thumbnails, waveforms, originalAudio, addSoundPillFocus, addMediaTileFocus, onIntent, modifier)
    }
}

/** The timeline's height at the current text size: ruler, main video row and the "Add a meme sound" row (P1-08). */
@Composable
internal fun timelineMinHeight(): Dp = rulerHeight() + MemixSize.touchTarget * 2

/** The `timecode` line height plus `space-1` above and below, so it grows with the text size. */
@Composable
private fun rulerHeight(): Dp = with(LocalDensity.current) { MemixTheme.type.timecode.lineHeight.toDp() } + MemixSpacing.space1 * 2

@Composable
private fun TimelineContent(
    timeline: TimelineUi,
    selectedItemId: String?,
    keyframeBar: KeyframeBarUi?,
    trimRanges: TrimRanges?,
    reorder: ReorderUi?,
    landed: LandedItems?,
    revealed: LandedItems?,
    preview: EditorPreview?,
    thumbnails: TimelineThumbnails?,
    waveforms: TimelineWaveforms?,
    originalAudio: OriginalAudioUi?,
    addSoundPillFocus: FocusRequester,
    addMediaTileFocus: FocusRequester,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val controller = remember { TimelineController(TimelineState(), scope) }
    val state = controller.state
    val reduceMotion = MemixTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    SideEffect {
        controller.timeline = timeline
        controller.onIntent = onIntent
        controller.keyframeBar = keyframeBar
        controller.reduceMotion = reduceMotion
        controller.density = density.density
        controller.haptics = haptics
        controller.snapDistancePx = with(density) { MemixSpacing.space2.toPx() }
        controller.edgeZonePx = with(density) { MemixSize.touchTarget.toPx() }
        controller.reorderTileSizePx = with(density) { MemixSize.trackHeightVideo.toPx() }
        controller.reorderPitchPx = with(density) { (MemixSize.trackHeightVideo + MemixSpacing.space1).toPx() }
    }

    val textMeasurer = rememberTextMeasurer()
    val painter = rememberTimelinePainter(textMeasurer)
    val labelLineHeightPx = with(density) { MemixTheme.type.caption.lineHeight.toPx() }
    // Read in composition: it changes only when the timeline is laid out at a new width.
    val addPillLabelHeightPx = rememberAddPillLabelHeightPx(textMeasurer, state.widthPx)
    val rulerHeight = rulerHeight()
    val rulerHeightPx = with(density) { rulerHeight.toPx() }
    // The whole timeline's height: when the lanes and the add row don't fit under the ruler, the row is pinned (P4-01b).
    var timelineHeightPx by remember { mutableIntStateOf(0) }
    val lanesAvailablePx = (timelineHeightPx - rulerHeightPx).coerceAtLeast(0f)
    // The selected item's marker row opens under its lane while its keyframe bar is open (P4-02b); the lanes below slide.
    val markerItemId = keyframeBar?.itemId
    val lanes = remember(timeline, density, labelLineHeightPx, addPillLabelHeightPx, lanesAvailablePx, markerItemId) {
        laneLayoutOf(timeline, density, labelLineHeightPx, addPillLabelHeightPx, lanesAvailablePx, markerItemId)
    }
    val currentBar by rememberUpdatedState(keyframeBar)
    val labelSpacingPx = rememberRulerLabelSpacing(textMeasurer, timeline.lengthUs)
    val selection = rememberSelection(textMeasurer, timeline, lanes, selectedItemId)
    val currentSelectedId by rememberUpdatedState(selection.selected?.item?.id)
    val currentSelected by rememberUpdatedState(selection.selected)
    val currentTrimRanges by rememberUpdatedState(trimRanges)
    val laneScroll = rememberScrollState()
    val scrollable = rememberScrollableState(controller::scrollBy)
    var fadeFrame by remember { mutableLongStateOf(0L) }
    // How tall the scrolling lanes' window is, for bringing a landed sound's lane into view.
    var lanesViewportPx by remember { mutableIntStateOf(0) }
    // Asked again once the lanes' window is measured (the serial differs), so a bar open from the first frame still scrolls.
    val markerReveal = remember(markerItemId, lanesViewportPx > 0) { markerItemId?.let { LandedItems(persistentSetOf(it), serial = if (lanesViewportPx > 0) 1 else 0) } }
    var zoomMenuOpen by remember { mutableStateOf(false) }
    // Kept after closing, so the menu fades out where it was.
    var zoomMenuAt by remember { mutableStateOf(IntOffset.Zero) }

    FollowPreview(controller, preview, scrollable::isScrollInProgress)
    ReportScrubbing(controller, scrollable::isScrollInProgress)
    KeepInRange(controller, timeline.lengthUs)
    FollowReorderMode(controller, reorder, timeline)
    val motion = remember { EditMotion() }
    AnimateEdits(motion, timeline, landed, controller)
    AnimateLanes(motion, lanes)
    val reordering = state.reorder != null
    if (thumbnails != null) LoadThumbnails(controller, timeline, thumbnails, painter.tileSizePx, painter.overlayTileSizePx, reordering, onFade = { fadeFrame = it })
    if (waveforms != null) FadeInWaveforms(waveforms, onFade = { fadeFrame = it })

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                state.widthPx = it.width
                timelineHeightPx = it.height
            }
            .timelineSemantics(stringResource(Res.string.timeline_a11y), rememberZoomActions(controller))
            .pointerInput(controller) { detectTouchAndPinch(controller::onTouchDown, controller::zoomBy, controller::onTouchUp) }
            // A diamond on the marker row is taken first: a tap selects it, a drag moves it (P4-02b).
            .pointerInput(controller, painter, lanes, rulerHeightPx) {
                detectKeyframeGestures(
                    markerAt = { offset -> currentBar?.let { diamondUnder(controller, lanes, it, TimelineHit(rulerHeightPx, laneScroll.value, offset)) } },
                    onTap = controller::tapKeyframe,
                    onDragStart = { marker, x -> currentBar?.let { bar -> lanes.rowOf(bar.itemId)?.let { controller.startKeyframeDrag(bar, marker, it, x) } } },
                    onDrag = controller::dragKeyframe,
                    onEnd = controller::endKeyframeDrag,
                    onCancel = controller::cancelKeyframeDrag,
                )
            }
            // Before the scroll and the taps, so a finger on a handle trims instead of scrubbing.
            .pointerInput(controller, painter, rulerHeightPx) {
                detectTrimDrag(
                    handleAt = { offset ->
                        // No handles in reorder mode, where the clips are tiles, or while an item is carried.
                        currentSelected?.takeIf { currentTrimRanges != null && controller.state.reorder == null && controller.state.move == null }?.let { selected ->
                            handleUnder(controller, painter, selected, TimelineHit(rulerHeightPx, laneScroll.value, offset))
                        }
                    },
                    onStart = { edge, x ->
                        val selected = currentSelected
                        val ranges = currentTrimRanges
                        if (selected != null && ranges != null) controller.startTrim(selected.item, selected.row, edge, ranges.of(edge), x)
                    },
                    onDrag = controller::dragTrim,
                    onEnd = controller::endTrim,
                    onCancel = controller::cancelTrim,
                )
            }
            // Also before the scroll and the taps: a long-press on a main clip lifts it, and reorder mode takes every touch.
            .pointerInput(controller, painter, lanes, rulerHeightPx) {
                detectReorderGestures(
                    isReordering = { controller.isReordering },
                    isOnStrip = { offset -> isOnMainRow(lanes, TimelineHit(rulerHeightPx, laneScroll.value, offset)) },
                    mainClipAt = { offset -> mainClipUnder(controller, painter, lanes, TimelineHit(rulerHeightPx, laneScroll.value, offset)) },
                    onLift = controller::liftForReorder,
                    onGrab = controller::grabMovingTile,
                    onDrag = controller::dragReorder,
                    onDrop = controller::dropReorder,
                    onTap = controller::tapReorderTile,
                )
            }
            // Also before the scroll and the taps: a long-press on any item but a main clip lifts it to move.
            .pointerInput(controller, painter, lanes, rulerHeightPx) {
                detectMoveGestures(
                    isMovableAt = { offset -> !controller.isReordering && movableItemUnder(controller, painter, lanes, TimelineHit(rulerHeightPx, laneScroll.value, offset)) != null },
                    onLift = { offset ->
                        val found = movableItemUnder(controller, painter, lanes, TimelineHit(rulerHeightPx, laneScroll.value, offset))
                        if (found != null) controller.liftForMove(found.first, found.second, lanes, offset.x)
                    },
                    onDrag = { offset -> controller.dragMove(offset.x, offset.y - rulerHeightPx + laneScroll.value) },
                    onDrop = controller::dropMove,
                )
            }
            // No overscroll stretch: time stops exactly at 0:00 and at the end.
            .scrollable(scrollable, Orientation.Horizontal, overscrollEffect = null)
            .pointerInput(controller, painter, lanes, rulerHeightPx, haptics) {
                detectTapGestures(
                    onTap = { offset ->
                        val hit = TimelineHit(rulerHeightPx, laneScroll.value, offset)
                        onTap(controller, painter, lanes, hit, currentSelectedId)
                    },
                    onLongPress = { offset ->
                        if (offset.y < rulerHeightPx) {
                            // The buzz says the long-press worked before the menu has faded in (spec → Zoom).
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            zoomMenuAt = offset.round()
                            zoomMenuOpen = true
                        } else {
                            // A long-press selects too (spec P1-06 → Entry and exit); on a main clip it also lifts it
                            // for reordering, which the reorder detector does. P1-08 makes it lift other items to move.
                            val hit = TimelineHit(rulerHeightPx, laneScroll.value, offset)
                            onTap(controller, painter, lanes, hit, currentSelectedId)
                        }
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(rulerHeight)
                    .drawBehind {
                        with(painter) { drawRuler(state.playheadUs, pxPerUs(state.dpPerSecond, density.density), labelSpacingPx) }
                    },
            )
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { lanesViewportPx = it.height }.verticalScroll(laneScroll)) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { lanes.heightPx.toDp() })
                        .drawBehind {
                            // Read so the strip redraws when a tile arrives and while it fades in.
                            fadeFrame
                            thumbnails?.version
                            waveforms?.version
                            val playheadUs = state.playheadUs
                            val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
                            val missingSources = thumbnails?.missingSources.orEmpty()
                            val trim = state.trim
                            val move = state.move
                            // In reorder mode the main clips are tiles, drawn over the wash in the layer above.
                            val strip = state.reorder
                            with(painter) {
                                drawLanes(timeline, lanes, playheadUs, pxPerUs, thumbnails, waveforms, missingSources, trim, move, motion, hideMainClips = strip != null)
                            }
                            keyframeBar?.let { bar ->
                                lanes.rowOf(bar.itemId)?.let { row ->
                                    translate(top = motion.laneShiftPx(row.track.id)) {
                                        with(painter) { drawKeyframeMarkers(row, bar, state.keyframeDrag, playheadUs, pxPerUs, timeline.lengthUs) }
                                    }
                                }
                            }
                            // A carried item draws its own outline where it goes.
                            if (strip == null && move == null) {
                                selection.selected?.let { selected ->
                                    translate(top = motion.laneShiftPx(selected.row.track.id)) {
                                        drawSelected(painter, selected, trim, motion, missingSources, playheadUs, pxPerUs, selection.alpha.value)
                                    }
                                }
                            }
                        },
                )
                if (!reordering) keyframeBar?.let { bar -> lanes.rowOf(bar.itemId)?.takeIf { it.markerHeightPx > 0f }?.let { KeyframeSemantics(controller, bar, it) } }
                if (!reordering) TrackSemantics(controller, painter, timeline, lanes, selection.selected?.item?.id, trimRanges, thumbnails?.missingSources.orEmpty())
                ShowLandedLane(landed, lanes, laneScroll, viewportHeightPx = { lanesViewportPx })
                // After Layer order the clip's lane may have moved: the lanes scroll to keep it in view.
                ShowLandedLane(revealed, lanes, laneScroll, viewportHeightPx = { lanesViewportPx })
                // Opening a keyframe bar brings its item's lane and marker row into view (spec P4-02b → Lane budget).
                ShowLandedLane(markerReveal, lanes, laneScroll, viewportHeightPx = { lanesViewportPx })
            }
            // The pinned "Add a meme sound" row takes its own height under the scrolling lanes (the pill itself is drawn over it).
            lanes.addSoundRow?.takeIf { it.pinned }?.let { Spacer(Modifier.fillMaxWidth().height(with(density) { it.heightPx.toDp() })) }
        }
        // Reorder mode: the wash over the ruler and lanes, and the tiles over it.
        Spacer(
            Modifier.matchParentSize().drawBehind {
                fadeFrame
                thumbnails?.version
                val strip = state.reorder ?: return@drawBehind
                val mainRow = lanes.rows.firstOrNull { it.track.kind == TrackKind.MAIN_VIDEO } ?: return@drawBehind
                val itemTopPx = rulerHeightPx + mainRow.itemTopPx - laneScroll.value
                with(painter) {
                    drawReorder(strip, itemTopPx, state.playheadUs, pxPerUs(state.dpPerSecond, density.density), thumbnails, thumbnails?.missingSources.orEmpty())
                }
            },
        )
        state.reorder?.let { strip -> lanes.rows.firstOrNull { it.track.kind == TrackKind.MAIN_VIDEO }?.let { ReorderSemantics(controller, strip, it, rulerHeightPx, laneScroll) } }
        // Over the items, under the value bubble and the menu, the floating parts (spec P1-05 → Playhead → Layering).
        Spacer(
            Modifier.matchParentSize().drawBehind {
                val lanesBottomPx = max(rulerHeightPx, min(size.height, rulerHeightPx + lanes.heightPx - laneScroll.value))
                with(painter) {
                    snapGuideX(state, density.density, size.width)?.let { drawSnapGuide(it, rulerHeightPx, lanesBottomPx) }
                    // Fades out with reorder mode's wash and back in as the tiles expand (spec P1-06 → Reorder).
                    drawPlayhead(bottomPx = lanesBottomPx, alpha = 1f - (state.reorder?.collapse?.value ?: 0f))
                }
            },
        )
        PlayheadSemantics(controller, timeline.lengthUs, rulerHeight)
        originalAudio?.let { audio ->
            val selectedStartUs = remember(timeline, selectedItemId) { timeline.mainClipStartUs(selectedItemId) }
            OriginalAudioToggle(controller, lanes, audio.muted, selectedStartUs, laneScroll, rulerHeight, onToggle = { onIntent(VideoEditorIntent.ToggleOriginalAudio) })
        }
        lanes.addSoundRow?.let { addRow ->
            AddMemeSoundPill(controller, addRow, motion, laneScroll, rulerHeight, addSoundPillFocus, onClick = { onIntent(VideoEditorIntent.AddMemeSoundRow) })
        }
        // A selected main clip's end handle reaches `touch-target` past its end, where the tile sits when the clip ends
        // near the end of the video (always for the last clip): the tile steps aside meanwhile. The zoom is read in
        // derivedStateOf, so a pinch recomposes only when the answer flips.
        val touchTargetPx = with(density) { MemixSize.touchTarget.toPx() }
        val selectedEndUs = remember(timeline, selectedItemId) { timeline.mainClipEndUs(selectedItemId) }
        val endHandleOnTile by remember(selectedEndUs, timeline.lengthUs, touchTargetPx) {
            derivedStateOf {
                selectedEndUs != null && (timeline.lengthUs - selectedEndUs) * pxPerUs(state.dpPerSecond, density.density) < touchTargetPx
            }
        }
        if (!endHandleOnTile) {
            AddMediaTile(controller, lanes, timeline.lengthUs, laneScroll, rulerHeight, addMediaTileFocus, onClick = { onIntent(VideoEditorIntent.AddMediaTile) })
        }
        EditValueBubble(controller, rulerHeightPx, laneScroll)
        ZoomMenu(controller, zoomMenuOpen, zoomMenuAt, onDismiss = { zoomMenuOpen = false })
    }
    CancelTrimOnBack(controller)
    CancelMoveOnBack(controller)
    CancelKeyframeDragOnBack(controller)
}

/** True when [hit] is in the main video row's band (48 dp: the reorder tiles' targets), below the ruler. */
private fun isOnMainRow(lanes: LaneLayout, hit: TimelineHit): Boolean {
    val mainRow = lanes.rows.firstOrNull { it.track.kind == TrackKind.MAIN_VIDEO } ?: return false
    val laneY = hit.offset.y - hit.rulerHeightPx + hit.laneScrollPx
    return hit.offset.y >= hit.rulerHeightPx && laneY >= mainRow.topPx && laneY < mainRow.topPx + mainRow.contentHeightPx
}

/** The diamond of [bar] under [hit], within `touch-target` wide of its center, in the marker row; null elsewhere. */
private fun diamondUnder(controller: TimelineController, lanes: LaneLayout, bar: KeyframeBarUi, hit: TimelineHit): KeyframeMarkerUi? {
    if (hit.offset.y < hit.rulerHeightPx) return null
    val row = lanes.rowOf(bar.itemId)?.takeIf { it.markerHeightPx > 0f } ?: return null
    val laneY = hit.offset.y - hit.rulerHeightPx + hit.laneScrollPx
    if (laneY < row.markerTopPx || laneY >= row.markerTopPx + row.markerHeightPx) return null
    val state = controller.state
    val pxPerUs = pxPerUs(state.dpPerSecond, controller.density)
    if (pxPerUs <= 0f) return null
    val halfTargetPx = controller.edgeZonePx / 2
    var nearest: KeyframeMarkerUi? = null
    var nearestDistance = Float.MAX_VALUE
    for (index in bar.markers.indices) {
        val marker = bar.markers[index]
        val distance = abs(xOf(marker.timeUs, state.playheadUs, state.widthPx / 2f, pxPerUs) - hit.offset.x)
        if (distance <= halfTargetPx && distance < nearestDistance) {
            nearest = marker
            nearestDistance = distance
        }
    }
    return nearest
}

/** The item under [hit] on any lane but the main track, with its row, for a long-press move; null elsewhere. */
private fun movableItemUnder(controller: TimelineController, painter: TimelinePainter, lanes: LaneLayout, hit: TimelineHit): Pair<ItemUi, LaneRow>? {
    if (hit.offset.y < hit.rulerHeightPx) return null
    val row = lanes.rowAt(hit.offset.y - hit.rulerHeightPx + hit.laneScrollPx)?.takeIf { it.track.kind != TrackKind.MAIN_VIDEO } ?: return null
    val state = controller.state
    val item = painter.itemAt(row, hit.offset.x, state.playheadUs, state.widthPx / 2f, pxPerUs(state.dpPerSecond, controller.density)) ?: return null
    return item to row
}

/** The main clip under [hit] (not on the ruler), for a long-press lift; null elsewhere. */
private fun mainClipUnder(controller: TimelineController, painter: TimelinePainter, lanes: LaneLayout, hit: TimelineHit): ItemUi? {
    if (hit.offset.y < hit.rulerHeightPx) return null
    val row = lanes.rowAt(hit.offset.y - hit.rulerHeightPx + hit.laneScrollPx)?.takeIf { it.track.kind == TrackKind.MAIN_VIDEO } ?: return null
    val state = controller.state
    return painter.itemAt(row, hit.offset.x, state.playheadUs, state.widthPx / 2f, pxPerUs(state.dpPerSecond, controller.density))
}

/**
 * The selection's outline, handles and badge: at the dragged size while its handle moves (no badge then; the value
 * bubble shows the new length), else at the item's own.
 */
private fun DrawScope.drawSelected(
    painter: TimelinePainter,
    selected: SelectedItem,
    trim: TrimDrag?,
    motion: EditMotion,
    missingSources: Set<MediaRef>,
    playheadUs: Long,
    pxPerUs: Float,
    alpha: Float,
) {
    if (trim != null && trim.item.id == selected.item.id) {
        with(painter) { drawSelection(trim.startUs, trim.endUs, selected.row, playheadUs, pxPerUs, badge = null, alpha) }
        return
    }
    // A missing clip's "File missing" label sits where the badge would (P1-05 review, polish 8). Checked here, so a
    // file found missing after selecting loses it at once.
    val media = selected.item.media
    val isMissing = media != null && media in missingSources
    val badge = if (isMissing) null else selected.badge
    // A main clip's outline slides with it while the clips ripple into place.
    val startUs = if (selected.row.track.kind == TrackKind.MAIN_VIDEO) motion.startUsOf(selected.item) else selected.item.startUs
    with(painter) { drawSelection(startUs, startUs + selected.item.durationUs, selected.row, playheadUs, pxPerUs, badge, alpha) }
}

/** The handle of the selected item under [hit], when the touch is in its row below the ruler; else null. */
private fun handleUnder(controller: TimelineController, painter: TimelinePainter, selected: SelectedItem, hit: TimelineHit): TrimEdge? {
    val (x, y) = hit.offset
    if (y < hit.rulerHeightPx) return null
    val laneY = y - hit.rulerHeightPx + hit.laneScrollPx
    val row = selected.row
    // The item's own row: a diamond under it is the marker row's, never a trim handle's.
    if (laneY < row.topPx || laneY >= row.topPx + row.contentHeightPx) return null
    val state = controller.state
    val pxPerUs = pxPerUs(state.dpPerSecond, controller.density)
    return painter.trimHandleAt(selected.item.startUs, selected.item.endUs, x, state.playheadUs, pxPerUs, state.widthPx.toFloat())
}

@Composable
private fun rememberTimelinePainter(textMeasurer: TextMeasurer): TimelinePainter {
    val density = LocalDensity.current
    val labelStyle = MemixTheme.type.caption
    val rulerStyle = MemixTheme.type.timecode
    val originalAudio = stringResource(Res.string.timeline_original_audio)
    val voiceover = stringResource(Res.string.timeline_voiceover)
    val unnamedMySound = stringResource(Res.string.timeline_my_sound)
    val missingFile = stringResource(Res.string.clip_missing_file)
    val languageCode = appLanguageCode()
    return remember(textMeasurer, labelStyle, rulerStyle, originalAudio, voiceover, unnamedMySound, missingFile, languageCode, density) {
        TimelinePainter(textMeasurer, labelStyle, rulerStyle, originalAudio, voiceover, unnamedMySound, missingFile, languageCode, density)
    }
}

/** The widest ruler label for this length plus `space-4`: labels never sit closer (spec → Ruler → Label step). */
@Composable
private fun rememberRulerLabelSpacing(textMeasurer: TextMeasurer, lengthUs: Long): Float {
    val density = LocalDensity.current
    val rulerStyle = MemixTheme.type.timecode
    val widest = widestRulerLabel(lengthUs)
    return remember(textMeasurer, widest, rulerStyle, density) {
        textMeasurer.measure(widest, rulerStyle, softWrap = false, maxLines = 1).size.width + with(density) { MemixSpacing.space4.toPx() }
    }
}

/** The selected item, if it still exists, with its 120 ms fade-in. */
private class TimelineSelection(val selected: SelectedItem?, val alpha: Animatable<Float, AnimationVector1D>)

/** Where the selected item is drawn, and its duration badge (main video clips only). */
private class SelectedItem(val item: ItemUi, val row: LaneRow, val badge: TextLayoutResult?)

@Composable
private fun rememberSelection(textMeasurer: TextMeasurer, timeline: TimelineUi, lanes: LaneLayout, selectedItemId: String?): TimelineSelection {
    val badgeStyle = MemixTheme.type.timecode
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(selectedItemId) {
        if (selectedItemId == null) return@LaunchedEffect
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(MemixMotion.durationPress))
    }
    return remember(textMeasurer, timeline, lanes, selectedItemId, badgeStyle) {
        val item = selectedItemId?.let(timeline::item)
        val row = selectedItemId?.let(lanes::rowOf)
        val selected = if (item != null && row != null) {
            val badge = if (row.track.kind == TrackKind.MAIN_VIDEO) {
                textMeasurer.measure(formatTimecode(item.durationUs), badgeStyle, softWrap = false, maxLines = 1)
            } else {
                null
            }
            SelectedItem(item, row, badge)
        } else {
            null
        }
        TimelineSelection(selected, alpha)
    }
}

/**
 * The playhead follows the preview's position (every frame while playing), except while the timeline moves time
 * itself: a report still on its way after a touch paused playback can't pull the playhead back.
 */
@Composable
private fun FollowPreview(controller: TimelineController, preview: EditorPreview?, isScrolling: () -> Boolean) {
    LaunchedEffect(controller, preview) {
        val state = controller.state
        preview?.positionUs?.collect { positionUs ->
            if (!state.isFingerDown && !isScrolling() && !state.isSeekAnimating) state.playheadUs = positionUs
        }
    }
}

/** A drag or fling of the timeline puts the preview into scrubbing mode until the timeline stops. */
@Composable
private fun ReportScrubbing(controller: TimelineController, isScrolling: () -> Boolean) {
    LaunchedEffect(controller) {
        var scrubbing = false
        snapshotFlow(isScrolling).collect { scrolling ->
            if (scrolling == scrubbing) return@collect
            scrubbing = scrolling
            controller.onIntent(if (scrolling) VideoEditorIntent.ScrubStarted else VideoEditorIntent.ScrubEnded(controller.state.playheadUs))
        }
    }
}

/** After an edit or a resize, the playhead stays inside the video and the zoom inside its range (spec → Zoom). */
@Composable
private fun KeepInRange(controller: TimelineController, lengthUs: Long) {
    LaunchedEffect(controller, lengthUs) {
        val state = controller.state
        snapshotFlow { state.widthPx }.collect { widthPx ->
            state.keepInRange(lengthUs, minDpPerSecond(lengthUs, widthPx / controller.density))
        }
    }
}

/**
 * Tells [thumbnails] which tiles the strip needs whenever the playhead crosses a tile or the zoom changes (in reorder
 * mode, every clip's first frame instead), and
 * ticks [onFade] every frame for 120 ms after a tile arrives so it can fade in.
 */
@Composable
private fun LoadThumbnails(
    controller: TimelineController,
    timeline: TimelineUi,
    thumbnails: TimelineThumbnails,
    tileSizePx: Int,
    overlayTileSizePx: Int,
    reordering: Boolean,
    onFade: (frameTimeNanos: Long) -> Unit,
) {
    LaunchedEffect(controller, timeline, thumbnails, tileSizePx, overlayTileSizePx, reordering) {
        // Reorder tiles show each clip's first frame (spec P1-06 → Reorder).
        if (reordering) return@LaunchedEffect thumbnails.want(firstFrameRequestsFor(timeline), tileSizePx)
        val state = controller.state
        snapshotFlow { tileWindowOf(state.playheadUs, pxPerUs(state.dpPerSecond, controller.density), state.widthPx, tileSizePx, overlayTileSizePx) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { window -> thumbnails.want(tileRequestsFor(timeline, window), tileSizePx) }
    }
    LaunchedEffect(thumbnails) {
        snapshotFlow { thumbnails.version }.collectLatest {
            val fadeEnd = TimeSource.Monotonic.markNow() + MemixMotion.durationPress.milliseconds
            while (fadeEnd.hasNotPassedNow()) withFrameNanos(onFade)
            // One more frame, drawn at full opacity.
            withFrameNanos(onFade)
        }
    }
}

/** Ticks [onFade] every frame for 120 ms after peaks arrive, so the new bars fade in (spec P3-09 → Motion); the sound's lanes redraw on each tick. */
@Composable
private fun FadeInWaveforms(waveforms: TimelineWaveforms, onFade: (frameTimeNanos: Long) -> Unit) {
    LaunchedEffect(waveforms) {
        snapshotFlow { waveforms.version }.collectLatest {
            val fadeEnd = TimeSource.Monotonic.markNow() + MemixMotion.durationPress.milliseconds
            while (fadeEnd.hasNotPassedNow()) withFrameNanos(onFade)
            // One more frame, drawn at full opacity.
            withFrameNanos(onFade)
        }
    }
}

/** Where a tap landed: [offset] in the timeline, the ruler's height and how far the lanes are scrolled. */
private class TimelineHit(val rulerHeightPx: Float, val laneScrollPx: Int, val offset: Offset)

/**
 * A tap on the ruler seeks there (the selection stays); a tap on an item selects it (the selected one stays
 * selected); a tap anywhere else in the lanes clears the selection (spec → Scroll, zoom and scrub).
 */
private fun onTap(controller: TimelineController, painter: TimelinePainter, lanes: LaneLayout, hit: TimelineHit, selectedItemId: String?) {
    val state = controller.state
    val pxPerUs = pxPerUs(state.dpPerSecond, controller.density)
    val centerPx = state.widthPx / 2f
    val (x, y) = hit.offset
    if (y < hit.rulerHeightPx) {
        if (pxPerUs > 0f) controller.seekAnimated(timeAt(x, state.playheadUs, centerPx, pxPerUs))
        return
    }
    val laneY = y - hit.rulerHeightPx + hit.laneScrollPx
    // The marker row answers only to its diamonds, which the keyframe detector took; an empty part of it does nothing.
    if (lanes.rows.any { it.markerHeightPx > 0f && laneY >= it.markerTopPx && laneY < it.markerTopPx + it.markerHeightPx }) return
    val row = lanes.rowAt(laneY)
    val item = row?.let { painter.itemAt(it, x, state.playheadUs, centerPx, pxPerUs) }
    when {
        item == null -> controller.onIntent(VideoEditorIntent.ClearSelection)
        item.id != selectedItemId -> controller.onIntent(VideoEditorIntent.SelectItem(item.id))
    }
}

/** The zoom menu, opened by a long-press on the ruler; zoom steps keep it open for repeated taps (spec → Zoom). */
@Composable
private fun ZoomMenu(controller: TimelineController, open: Boolean, pressPoint: IntOffset, onDismiss: () -> Unit) {
    val title = stringResource(Res.string.timeline_zoom_menu)
    val zoomIn = stringResource(Res.string.timeline_zoom_in)
    val zoomOut = stringResource(Res.string.timeline_zoom_out)
    val wholeVideo = stringResource(Res.string.timeline_zoom_fit)
    // Called by the menu only while it shows, so the zoom it reads never recomposes the timeline itself.
    val items = {
        listOf(
            MenuItem(zoomIn, controller::zoomIn, enabled = controller.canZoomIn, closesMenu = false),
            MenuItem(zoomOut, controller::zoomOut, enabled = controller.canZoomOut, closesMenu = false),
            MenuItem(wholeVideo, controller::showWholeVideo, enabled = controller.canZoomOut),
        )
    }
    Menu(open, pressPoint, title, items, onDismiss)
}
