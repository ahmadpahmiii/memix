package app.memix.feature.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.component.ToastDismiss
import app.memix.core.domain.video.PreviewSession
import app.memix.core.ui.formatTimecode
import app.memix.core.ui.spokenTimeOf
import app.memix.feature.videoeditor.timeline.Timeline
import app.memix.feature.videoeditor.timeline.timelineMinHeight
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.edit_trim
import memix.feature.video_editor.generated.resources.editor_close
import memix.feature.video_editor.generated.resources.editor_dismiss
import memix.feature.video_editor.generated.resources.editor_empty_body
import memix.feature.video_editor.generated.resources.editor_empty_title
import memix.feature.video_editor.generated.resources.editor_leave_anyway
import memix.feature.video_editor.generated.resources.editor_missing_media
import memix.feature.video_editor.generated.resources.editor_pause
import memix.feature.video_editor.generated.resources.editor_play
import memix.feature.video_editor.generated.resources.editor_preview_a11y
import memix.feature.video_editor.generated.resources.editor_preview_error_body
import memix.feature.video_editor.generated.resources.editor_preview_retry
import memix.feature.video_editor.generated.resources.editor_redo
import memix.feature.video_editor.generated.resources.editor_redo_toast
import memix.feature.video_editor.generated.resources.editor_save_failed
import memix.feature.video_editor.generated.resources.editor_save_storage_full
import memix.feature.video_editor.generated.resources.editor_saved_a11y
import memix.feature.video_editor.generated.resources.editor_undo
import memix.feature.video_editor.generated.resources.editor_undo_toast
import memix.feature.video_editor.generated.resources.editor_unsaved_body
import memix.feature.video_editor.generated.resources.editor_unsaved_title
import memix.feature.video_editor.generated.resources.import_free_up_space
import org.jetbrains.compose.resources.stringResource

/**
 * The video editor frame (spec P1-04 → Layout): top bar, preview stage, transport row, and the regions the timeline
 * (P1-05) and the tool bar (P1-06) fill. The stage and timeline split their shared space 60/40 by window size only,
 * so the preview never jumps while editing.
 *
 * [previewSurface] draws a preview session's picture: the composition root passes the engine's surface, which a
 * feature can't depend on. [onFreeUpSpace] opens the phone's storage manager.
 */
@Composable
fun VideoEditorScreen(
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
    previewSurface: @Composable (PreviewSession, Modifier) -> Unit,
    onFreeUpSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System back closes the editor through the app's own navigation, which saves on the way out. Only while
    // saving fails does the editor take back itself, to ask first; the open sheet then handles back.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.isSaveFailing && !state.unsavedSheetOpen,
        onBackCompleted = { onIntent(VideoEditorIntent.Close) },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(VideoEditorIntent.AppStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(VideoEditorIntent.AppStopped) }

    Box(
        modifier
            .fillMaxSize()
            .background(MemixColors.canvas)
            // Escape does what system back does (spec P1-04 → Interactions); the open sheet handles its own.
            .onPreviewKeyEvent { event ->
                val closes = event.key == Key.Escape && !state.unsavedSheetOpen
                if (closes && event.type == KeyEventType.KeyUp) onIntent(VideoEditorIntent.Close)
                closes
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(onClose = { onIntent(VideoEditorIntent.Close) })
            StageTransportAndTimeline(
                stage = { PreviewStage(state, onIntent, previewSurface, onFreeUpSpace) },
                transport = { TransportRow(state, onIntent) },
                timeline = { Timeline(state.timeline, state.selectedItemId, state.preview, state.thumbnails, onIntent) },
                timelineMinHeight = timelineMinHeight(),
                modifier = Modifier.weight(1f),
            )
            ToolBarRegion()
        }
        UnsavedChangesSheet(state.unsavedSheetOpen, onIntent, onFreeUpSpace.takeIf { state.canFreeUpSpace })
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    // P1-11's ratio chip, P1-12's quality chip and Export join this bar with their tickets.
    Row(Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1)) {
        IconButton(MemixIcons.Close, stringResource(Res.string.editor_close), onClose)
    }
}

/**
 * Stage on top, transport row, timeline below. The transport row takes what it needs (it grows at large font
 * sizes); the timeline takes 40% of the rest but never less than [timelineMinHeight], and the stage everything else.
 */
@Composable
private fun StageTransportAndTimeline(
    stage: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    timeline: @Composable () -> Unit,
    timelineMinHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(stage, transport, timeline), modifier) { (stageParts, transportParts, timelineParts), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val transportRow = transportParts.first().measure(Constraints(minWidth = width, maxWidth = width))
        val sharedHeight = (height - transportRow.height).coerceAtLeast(0)
        val timelineHeight = max((sharedHeight * TIMELINE_SHARE).roundToInt(), timelineMinHeight.roundToPx()).coerceAtMost(sharedHeight)
        val stageHeight = sharedHeight - timelineHeight
        val stageBox = stageParts.first().measure(Constraints.fixed(width, stageHeight))
        val timelineBox = timelineParts.first().measure(Constraints.fixed(width, timelineHeight))
        layout(width, height) {
            stageBox.place(0, 0)
            transportRow.place(0, stageHeight)
            timelineBox.place(0, stageHeight + transportRow.height)
        }
    }
}

@Composable
private fun PreviewStage(
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
    previewSurface: @Composable (PreviewSession, Modifier) -> Unit,
    onFreeUpSpace: () -> Unit,
) {
    val canvas = state.canvas
    val playLabel = stringResource(if (state.isPlaying) Res.string.editor_pause else Res.string.editor_play)
    val previewLabel = canvas?.let { stringResource(Res.string.editor_preview_a11y, it.ratioLabel) }.orEmpty()
    // A tap anywhere on the stage plays or pauses; screen readers hear one node, "Preview, 9:16", whose action is
    // "Play" or "Pause". With nothing to play (empty draft, preview error) the stage takes no taps and has no action.
    val playOnTap = if (state.canPlay) {
        Modifier.clickable(interactionSource = null, indication = null, onClickLabel = playLabel) { onIntent(VideoEditorIntent.PlayPause) }
    } else {
        Modifier
    }
    // A traversal group, so screen readers reach the save banner before the preview (spec P1-04 → Accessibility).
    Box(Modifier.fillMaxSize().background(MemixColors.stage).semantics { isTraversalGroup = true }) {
        Box(Modifier.matchParentSize().then(playOnTap).clearAndSetSemantics { contentDescription = previewLabel }) {
            if (canvas != null) {
                CanvasFrame(canvas.aspectRatio) {
                    state.preview?.let { previewSurface(it.session, Modifier.fillMaxSize()) }
                    // Takes taps off the player's own view, so they reach the stage.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                    // The empty frame until the first picture: static, no spinner (spec P1-04 → Preview stage).
                    if (state.stage == StageContent.OPENING) Box(Modifier.fillMaxSize().background(MemixColors.surfaceRaised))
                }
            }
        }
        if (canvas != null) FrameMessage(state.stage, canvas.aspectRatio, onRetry = { onIntent(VideoEditorIntent.RetryPreview) })
        StageMessages(
            state,
            onIntent,
            onFreeUpSpace,
            Modifier.align(Alignment.TopCenter).semantics { traversalIndex = -1f },
        )
    }
}

/** The largest rectangle of the canvas ratio that fits the stage, `space-2` in from every side, centered. */
@Composable
private fun CanvasFrame(aspectRatio: Float, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(MemixSpacing.space2), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(aspectRatio), content = content)
    }
}

/** What the frame says instead of a picture; drawn over the stage's tap area so its button can be pressed. */
@Composable
private fun FrameMessage(stage: StageContent, aspectRatio: Float, onRetry: () -> Unit) {
    when (stage) {
        StageContent.ERROR -> CanvasFrame(aspectRatio) { PreviewError(onRetry) }
        StageContent.EMPTY -> CanvasFrame(aspectRatio) { EmptyDraft() }
        StageContent.OPENING, StageContent.VIDEO -> Unit
    }
}

// `surface`, not `surface-raised`: the Secondary button's own fill is `surface-raised` and would lose its shape.
@Composable
private fun PreviewError(onRetry: () -> Unit) {
    CenteredMessage(Modifier.background(MemixColors.surface)) {
        Text(stringResource(Res.string.editor_preview_error_body), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        Button(stringResource(Res.string.editor_preview_retry), onRetry)
    }
}

// The canvas background shows behind it: black until P1-11 lets the user pick one.
@Composable
private fun EmptyDraft() {
    CenteredMessage {
        Text(stringResource(Res.string.editor_empty_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.editor_empty_body), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
    }
}

// Scrolls rather than cutting text off in a small frame at large font sizes.
@Composable
private fun CenteredMessage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MemixSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * The save banner, else the latest toast, `space-2` below the top of the stage. They never stack: a toast under the
 * banner is spoken to screen readers only (spec P1-04 → Saving).
 */
@Composable
private fun StageMessages(
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
    onFreeUpSpace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = state.toast
    if (toast != null) ToastTimer(toast, onTimedOut = { onIntent(VideoEditorIntent.ToastTimedOut(it)) })
    val banner = state.saveBanner
    val shownToast = toast?.takeIf { banner == null && !it.isSpokenOnly }
    Box(modifier.padding(top = MemixSpacing.space2, start = MemixSpacing.space4, end = MemixSpacing.space4)) {
        // Scrolls rather than cutting a message off when a large font makes it taller than a small stage.
        Box(Modifier.verticalScroll(rememberScrollState())) {
            FadingToast(banner) { problem ->
                SaveBanner(
                    problem,
                    onFreeUpSpace = onFreeUpSpace.takeIf { state.canFreeUpSpace },
                    onDismiss = { onIntent(VideoEditorIntent.DismissSaveBanner) },
                )
            }
            FadingToast(shownToast) { shown -> Toast(toastText(shown.message)) }
        }
        // A new node per toast, so the same text twice ("Undo: Trim") is announced twice.
        if (toast != null && toast.isSpokenOnly) key(toast.id) { SpokenOnly(toastText(toast.message)) }
        SavedAgainAnnouncement(state.savedAgainCount)
    }
}

/**
 * Hides [toast] after its time: 2 s for what happened, 4 s for an explanation, or longer when the user's "Time to
 * take action" setting asks for it. It runs even while the save banner hides the toast.
 */
@Composable
private fun ToastTimer(toast: EditorToast, onTimedOut: (id: Long) -> Unit) {
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(toast.id) {
        val baseMillis = when (toast.message) {
            is ToastMessage.Undone, is ToastMessage.Redone -> WHAT_HAPPENED_TOAST_MILLIS
            ToastMessage.MissingMedia -> EXPLANATION_TOAST_MILLIS
        }
        delay(recommendedTimeoutMillis(accessibilityManager, baseMillis))
        onTimedOut(toast.id)
    }
}

@Composable
private fun toastText(message: ToastMessage): String = when (message) {
    is ToastMessage.Undone -> stringResource(Res.string.editor_undo_toast, editName(message.edit))
    is ToastMessage.Redone -> stringResource(Res.string.editor_redo_toast, editName(message.edit))
    ToastMessage.MissingMedia -> stringResource(Res.string.editor_missing_media)
}

@Composable
private fun editName(edit: VideoEdit): String = stringResource(
    when (edit) {
        VideoEdit.TRIM -> Res.string.edit_trim
    },
)

/** [onFreeUpSpace] is null when the phone's storage screen can't open; the banner then has no action. */
@Composable
private fun SaveBanner(problem: SaveProblem, onFreeUpSpace: (() -> Unit)?, onDismiss: () -> Unit) {
    val message = when (problem) {
        SaveProblem.STORAGE_FULL -> stringResource(Res.string.editor_save_storage_full)
        SaveProblem.OTHER -> stringResource(Res.string.editor_save_failed)
    }
    val action = when (problem) {
        SaveProblem.STORAGE_FULL -> onFreeUpSpace?.let { ToastAction(stringResource(Res.string.import_free_up_space), it) }
        SaveProblem.OTHER -> null
    }
    Toast(message, action = action, dismiss = ToastDismiss(stringResource(Res.string.editor_dismiss), onDismiss))
}

/**
 * "Changes saved", spoken only, when saving works again after failing (spec P1-04 → Saving): an empty node that
 * screen readers announce politely as it appears, removed again after the toast time.
 */
@Composable
private fun SavedAgainAnnouncement(savedAgainCount: Int) {
    if (savedAgainCount == 0) return
    val accessibilityManager = LocalAccessibilityManager.current
    var speaking by remember { mutableStateOf(false) }
    LaunchedEffect(savedAgainCount) {
        speaking = true
        delay(recommendedTimeoutMillis(accessibilityManager, WHAT_HAPPENED_TOAST_MILLIS))
        speaking = false
    }
    if (speaking) SpokenOnly(stringResource(Res.string.editor_saved_a11y))
}

/** Nothing on screen: an empty node that screen readers announce politely as it appears. */
@Composable
private fun SpokenOnly(text: String) {
    Box(
        Modifier.size(MemixSize.touchTarget).clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    )
}

private fun recommendedTimeoutMillis(accessibilityManager: AccessibilityManager?, baseMillis: Long): Long =
    accessibilityManager?.calculateRecommendedTimeoutMillis(baseMillis, containsText = true) ?: baseMillis

@Composable
private fun TransportRow(state: VideoEditorUiState, onIntent: (VideoEditorIntent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.touchTarget)
            .drawBehind { drawBottomHairline() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onDebugEdit = if (state.canMakeDebugEdit) ({ onIntent(VideoEditorIntent.DebugEdit) }) else null
        // Equal side slots keep the play button on the screen's center line, over the timeline's playhead.
        Timecode(state.preview, state.lengthUs, onDebugEdit, Modifier.weight(1f).padding(start = MemixSpacing.space4))
        IconButton(
            if (state.isPlaying) MemixIcons.Pause else MemixIcons.Play,
            stringResource(if (state.isPlaying) Res.string.editor_pause else Res.string.editor_play),
            { onIntent(VideoEditorIntent.PlayPause) },
            enabled = state.canPlay,
        )
        Row(
            Modifier.weight(1f).padding(end = MemixSpacing.space2),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.End),
        ) {
            IconButton(MemixIcons.Undo, stringResource(Res.string.editor_undo), { onIntent(VideoEditorIntent.Undo) }, enabled = state.canUndo)
            IconButton(MemixIcons.Redo, stringResource(Res.string.editor_redo), { onIntent(VideoEditorIntent.Redo) }, enabled = state.canRedo)
        }
    }
}

private fun DrawScope.drawBottomHairline() {
    val strokePx = MemixStroke.strokeHairline.toPx()
    drawRect(MemixColors.hairline, topLeft = Offset(0f, size.height - strokePx), size = Size(size.width, strokePx))
}

/**
 * "00:03.20 / 00:08.00" on one line: the time on screen in `text`, then " / " and the length in `text-muted`. When
 * that doesn't fit (large fonts, hour-long projects), the length goes under the time, without the slash (spec P1-04
 * → Transport row). Screen readers hear "3.2 seconds of 8 seconds" instead of the digits; it isn't a live region,
 * because it changes every frame.
 */
@Composable
private fun Timecode(preview: EditorPreview?, lengthUs: Long, onDebugEdit: (() -> Unit)?, modifier: Modifier = Modifier) {
    val position = (preview?.positionUs ?: NoPosition).collectAsState()
    // Only the spoken text depends on this, and it changes ten times a second at most.
    val spokenPositionUs by remember(position) { derivedStateOf { position.value / MICROS_PER_TENTH * MICROS_PER_TENTH } }
    val spoken = spokenTimeOf(spokenPositionUs, lengthUs)
    val debugEditGesture = if (onDebugEdit == null) {
        Modifier
    } else {
        Modifier.pointerInput(onDebugEdit) { detectTapGestures(onLongPress = { onDebugEdit() }) }
    }
    val length = formatTimecode(lengthUs)
    val style = MemixTheme.type.timecode
    Layout(
        content = {
            CurrentTime(position)
            Text(TIMECODE_SEPARATOR + length, style, color = MemixColors.textMuted)
            Text(length, style, color = MemixColors.textMuted)
            // Measured, never drawn: the widest the one-line timecode gets (see TimecodeMeasurePolicy).
            Text(length + TIMECODE_SEPARATOR + length, style)
        },
        modifier = modifier.then(debugEditGesture).clearAndSetSemantics { contentDescription = spoken },
        measurePolicy = TimecodeMeasurePolicy,
    )
}

/**
 * Lays out [Timecode]: the time and " / length" side by side when they fit, else the time over the length alone.
 * The fit is decided by the fourth text, the length twice as one line: the time is never wider than the length
 * (Space Mono gives every digit the same width, and the time never passes the length), so the row doesn't jump when
 * the time gains a digit, and one text is measured the way the spec counts it (two texts can round 1 px wider, and
 * at 360 dp the line fits by less than 1 dp).
 */
private val TimecodeMeasurePolicy = MeasurePolicy { measurables, constraints ->
    val (time, separatorAndLength, lengthAlone, widestOneLine) = measurables
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val fitsOnOneLine = widestOneLine.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth
    val timeText = time.measure(loose)
    if (fitsOnOneLine) {
        val rest = separatorAndLength.measure(loose)
        layout(constraints.constrainWidth(timeText.width + rest.width), constraints.constrainHeight(max(timeText.height, rest.height))) {
            timeText.placeRelative(0, 0)
            rest.placeRelative(timeText.width, 0)
        }
    } else {
        val below = lengthAlone.measure(loose)
        layout(constraints.constrainWidth(max(timeText.width, below.width)), constraints.constrainHeight(timeText.height + below.height)) {
            timeText.placeRelative(0, 0)
            below.placeRelative(0, timeText.height)
        }
    }
}

// Reads the time on its own, so a playing frame redraws this text and nothing else.
@Composable
private fun CurrentTime(position: State<Long>) {
    Text(formatTimecode(position.value), MemixTheme.type.timecode)
}

/** P1-06 puts the tools here; until then the bar is empty, in `surface`, over the navigation bar's space. */
@Composable
private fun ToolBarRegion() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .height(MemixSize.toolbarHeight),
    )
}

/** [onFreeUpSpace] is null when the phone's storage screen can't open; Leave anyway is then the only button. */
@Composable
private fun UnsavedChangesSheet(visible: Boolean, onIntent: (VideoEditorIntent) -> Unit, onFreeUpSpace: (() -> Unit)?) {
    // Its close button, the scrim and back keep the user in the editor; only "Leave anyway" leaves.
    Sheet(visible, onDismiss = { onIntent(VideoEditorIntent.StayInEditor) }, title = stringResource(Res.string.editor_unsaved_title)) {
        Text(stringResource(Res.string.editor_unsaved_body), MemixTheme.type.body, color = MemixColors.textSecondary)
        if (onFreeUpSpace != null) {
            Button(stringResource(Res.string.import_free_up_space), onFreeUpSpace, Modifier.fillMaxWidth(), ButtonVariant.Primary)
        }
        Button(stringResource(Res.string.editor_leave_anyway), { onIntent(VideoEditorIntent.LeaveAnyway) }, Modifier.fillMaxWidth())
    }
}

private val NoPosition: StateFlow<Long> = MutableStateFlow(0L)

/** The timeline's share of the space it splits with the stage (spec P1-04 → Layout). */
private const val TIMELINE_SHARE = 0.4f

/** Between the time and the length on one line, with real spaces; a timecode, so it is never translated. */
private const val TIMECODE_SEPARATOR = " / "

private const val MICROS_PER_TENTH = 100_000L
private const val WHAT_HAPPENED_TOAST_MILLIS = 2_000L
private const val EXPLANATION_TOAST_MILLIS = 4_000L
