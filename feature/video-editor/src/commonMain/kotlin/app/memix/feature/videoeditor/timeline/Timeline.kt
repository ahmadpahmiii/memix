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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.formatTimecode
import app.memix.feature.videoeditor.EditorPreview
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.clip_missing_file
import memix.feature.video_editor.generated.resources.timeline_a11y
import memix.feature.video_editor.generated.resources.timeline_original_audio
import memix.feature.video_editor.generated.resources.timeline_zoom_fit
import memix.feature.video_editor.generated.resources.timeline_zoom_in
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
 * The original-audio toggle (P1-06/P1-09), the "Add a meme sound" row (P1-08), Add media (P1-16) and dragging the
 * handles (P1-06) arrive with their tickets; until then they aren't drawn, never shown disabled.
 */
@Composable
internal fun Timeline(
    timeline: TimelineUi,
    selectedItemId: String?,
    preview: EditorPreview?,
    thumbnails: TimelineThumbnails?,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Time always runs left to right, whatever the language (spec → Structure).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        TimelineContent(timeline, selectedItemId, preview, thumbnails, onIntent, modifier)
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
    preview: EditorPreview?,
    thumbnails: TimelineThumbnails?,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val controller = remember { TimelineController(TimelineState(), scope) }
    val state = controller.state
    val reduceMotion = MemixTheme.reduceMotion
    SideEffect {
        controller.timeline = timeline
        controller.onIntent = onIntent
        controller.reduceMotion = reduceMotion
        controller.density = density.density
    }

    val textMeasurer = rememberTextMeasurer()
    val painter = rememberTimelinePainter(textMeasurer)
    val labelLineHeightPx = with(density) { MemixTheme.type.caption.lineHeight.toPx() }
    val lanes = remember(timeline, density, labelLineHeightPx) { laneLayoutOf(timeline, density, labelLineHeightPx) }
    val rulerHeight = rulerHeight()
    val rulerHeightPx = with(density) { rulerHeight.toPx() }
    val labelSpacingPx = rememberRulerLabelSpacing(textMeasurer, timeline.lengthUs)
    val selection = rememberSelection(textMeasurer, timeline, lanes, selectedItemId)
    val currentSelectedId by rememberUpdatedState(selection.selected?.item?.id)
    val laneScroll = rememberScrollState()
    val scrollable = rememberScrollableState(controller::scrollBy)
    var fadeFrame by remember { mutableLongStateOf(0L) }
    var zoomMenuOpen by remember { mutableStateOf(false) }
    // Kept after closing, so the menu fades out where it was.
    var zoomMenuAt by remember { mutableStateOf(IntOffset.Zero) }

    FollowPreview(controller, preview, scrollable::isScrollInProgress)
    ReportScrubbing(controller, scrollable::isScrollInProgress)
    KeepInRange(controller, timeline.lengthUs)
    if (thumbnails != null) LoadThumbnails(controller, timeline, thumbnails, painter.tileSizePx, onFade = { fadeFrame = it })

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.widthPx = it.width }
            .timelineSemantics(stringResource(Res.string.timeline_a11y), rememberZoomActions(controller))
            .pointerInput(controller) { detectTouchAndPinch(controller::onTouchDown, controller::zoomBy, controller::onTouchUp) }
            // No overscroll stretch: time stops exactly at 0:00 and at the end.
            .scrollable(scrollable, Orientation.Horizontal, overscrollEffect = null)
            .pointerInput(controller, painter, lanes, rulerHeightPx) {
                detectTapGestures(
                    onTap = { offset ->
                        val hit = TimelineHit(rulerHeightPx, laneScroll.value, offset)
                        onTap(controller, painter, lanes, hit, currentSelectedId)
                    },
                    onLongPress = { offset ->
                        if (offset.y < rulerHeightPx) {
                            zoomMenuAt = offset.round()
                            zoomMenuOpen = true
                        }
                    },
                )
            }
            .drawWithContent {
                drawContent()
                val lanesBottomPx = rulerHeightPx + lanes.heightPx - laneScroll.value
                with(painter) { drawPlayhead(bottomPx = max(rulerHeightPx, min(size.height, lanesBottomPx))) }
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
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(laneScroll)) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { lanes.heightPx.toDp() })
                        .drawBehind {
                            // Read so the strip redraws when a tile arrives and while it fades in.
                            fadeFrame
                            thumbnails?.version
                            val playheadUs = state.playheadUs
                            val pxPerUs = pxPerUs(state.dpPerSecond, density.density)
                            with(painter) {
                                drawLanes(timeline, lanes, playheadUs, pxPerUs, thumbnails, thumbnails?.missingSources.orEmpty())
                                selection.selected?.let { drawSelection(it.item, it.row, playheadUs, pxPerUs, it.badge, selection.alpha.value) }
                            }
                        },
                )
                TrackSemantics(controller, painter, timeline, lanes, selection.selected?.item?.id)
            }
        }
        PlayheadSemantics(controller, timeline.lengthUs, rulerHeight)
        ZoomMenu(controller, zoomMenuOpen, zoomMenuAt, onDismiss = { zoomMenuOpen = false })
    }
}

@Composable
private fun rememberTimelinePainter(textMeasurer: TextMeasurer): TimelinePainter {
    val density = LocalDensity.current
    val labelStyle = MemixTheme.type.caption
    val rulerStyle = MemixTheme.type.timecode
    val originalAudio = stringResource(Res.string.timeline_original_audio)
    val missingFile = stringResource(Res.string.clip_missing_file)
    return remember(textMeasurer, labelStyle, rulerStyle, originalAudio, missingFile, density) {
        TimelinePainter(textMeasurer, labelStyle, rulerStyle, originalAudio, missingFile, density)
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
 * Tells [thumbnails] which tiles the strip needs whenever the playhead crosses a tile or the zoom changes, and
 * ticks [onFade] every frame for 120 ms after a tile arrives so it can fade in.
 */
@Composable
private fun LoadThumbnails(
    controller: TimelineController,
    timeline: TimelineUi,
    thumbnails: TimelineThumbnails,
    tileSizePx: Int,
    onFade: (frameTimeNanos: Long) -> Unit,
) {
    LaunchedEffect(controller, timeline, thumbnails, tileSizePx) {
        val state = controller.state
        snapshotFlow { tileWindowOf(state.playheadUs, pxPerUs(state.dpPerSecond, controller.density), state.widthPx, tileSizePx) }
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
    val row = lanes.rowAt(y - hit.rulerHeightPx + hit.laneScrollPx)
    val item = row?.let { painter.itemAt(it, x, state.playheadUs, centerPx, pxPerUs) }
    when {
        item == null -> controller.onIntent(VideoEditorIntent.ClearSelection)
        item.id != selectedItemId -> controller.onIntent(VideoEditorIntent.SelectItem(item.id))
    }
}

/** The zoom menu, opened by a long-press on the ruler; zoom steps keep it open for repeated taps (spec → Zoom). */
@Composable
private fun ZoomMenu(controller: TimelineController, open: Boolean, pressPoint: IntOffset, onDismiss: () -> Unit) {
    val title = stringResource(Res.string.timeline_a11y)
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
