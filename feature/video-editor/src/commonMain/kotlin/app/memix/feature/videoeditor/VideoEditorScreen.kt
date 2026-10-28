package app.memix.feature.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextRange
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
import app.memix.core.designsystem.component.SettingPill
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.coveredByModal
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.model.project.Transform
import app.memix.core.domain.video.PreviewSession
import app.memix.core.ui.editor.CanvasFrame
import app.memix.core.ui.editor.EXPLANATION_TOAST_MILLIS
import app.memix.core.ui.editor.FrameEdge
import app.memix.core.ui.editor.SaveBanner
import app.memix.core.ui.editor.SavedAgainAnnouncement
import app.memix.core.ui.editor.SpokenOnly
import app.memix.core.ui.editor.UnsavedChangesSheet
import app.memix.core.ui.editor.WHAT_HAPPENED_TOAST_MILLIS
import app.memix.core.ui.editor.recommendedTimeoutMillis
import app.memix.core.ui.formatTimecode
import app.memix.core.ui.layers.CanvasFrameRect
import app.memix.core.ui.layers.LayerGestureEvents
import app.memix.core.ui.layers.LayerStageState
import app.memix.core.ui.layers.LayerTouchSizes
import app.memix.core.ui.layers.detectLayerGestures
import app.memix.core.ui.spokenTime
import app.memix.core.ui.spokenTimeOf
import app.memix.feature.videoeditor.canvas.CanvasPanel
import app.memix.feature.videoeditor.captions.CaptionLayer
import app.memix.feature.videoeditor.captions.CaptionStandInSlot
import app.memix.feature.videoeditor.captions.TextEntryBar
import app.memix.feature.videoeditor.captions.TextPanels
import app.memix.feature.videoeditor.export.ExportSheet
import app.memix.feature.videoeditor.importmedia.ImportSheet
import app.memix.feature.videoeditor.importmedia.showsSheet
import app.memix.feature.videoeditor.timeline.Timeline
import app.memix.feature.videoeditor.timeline.spokenName
import app.memix.feature.videoeditor.timeline.timelineMinHeight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import memix.feature.video_editor.generated.resources.Res
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.a11y_degrees
import memix.core.ui.generated.resources.a11y_edit_on_item
import memix.feature.video_editor.generated.resources.a11y_media_added
import memix.feature.video_editor.generated.resources.a11y_reorder_position
import memix.feature.video_editor.generated.resources.a11y_sound_added
import memix.feature.video_editor.generated.resources.a11y_text_added
import memix.core.ui.generated.resources.a11y_text_position
import memix.core.ui.generated.resources.a11y_text_transformed
import memix.feature.video_editor.generated.resources.canvas_layers_moved
import memix.core.ui.generated.resources.canvas_pill_a11y
import memix.feature.video_editor.generated.resources.edit_add_media
import memix.feature.video_editor.generated.resources.edit_add_sound
import memix.feature.video_editor.generated.resources.edit_add_text
import memix.core.ui.generated.resources.edit_canvas_background
import memix.feature.video_editor.generated.resources.edit_canvas_fill
import memix.feature.video_editor.generated.resources.edit_canvas_fit
import memix.core.ui.generated.resources.edit_canvas_ratio
import memix.core.ui.generated.resources.edit_delete
import memix.feature.video_editor.generated.resources.edit_detach_audio
import memix.core.ui.generated.resources.edit_duplicate
import memix.feature.video_editor.generated.resources.edit_edit_text
import memix.feature.video_editor.generated.resources.edit_move
import memix.feature.video_editor.generated.resources.edit_move_text
import memix.feature.video_editor.generated.resources.edit_mute_original
import memix.core.ui.generated.resources.edit_reorder
import memix.feature.video_editor.generated.resources.edit_replace
import memix.feature.video_editor.generated.resources.edit_split
import memix.feature.video_editor.generated.resources.edit_text_color
import memix.feature.video_editor.generated.resources.edit_text_font
import memix.feature.video_editor.generated.resources.edit_text_outline
import memix.feature.video_editor.generated.resources.edit_transform_text
import memix.feature.video_editor.generated.resources.edit_trim
import memix.feature.video_editor.generated.resources.edit_unmute_original
import memix.feature.video_editor.generated.resources.edit_volume
import memix.core.ui.generated.resources.editor_close
import memix.feature.video_editor.generated.resources.editor_empty_body_add
import memix.feature.video_editor.generated.resources.editor_empty_title
import memix.feature.video_editor.generated.resources.editor_export
import memix.feature.video_editor.generated.resources.editor_missing_media
import memix.feature.video_editor.generated.resources.editor_pause
import memix.feature.video_editor.generated.resources.editor_play
import memix.feature.video_editor.generated.resources.editor_preview_a11y
import memix.feature.video_editor.generated.resources.editor_preview_error_body
import memix.feature.video_editor.generated.resources.editor_preview_retry
import memix.feature.video_editor.generated.resources.editor_preview_updating
import memix.core.ui.generated.resources.editor_redo
import memix.core.ui.generated.resources.editor_redo_toast
import memix.core.ui.generated.resources.editor_undo
import memix.core.ui.generated.resources.editor_undo_toast
import memix.core.ui.generated.resources.explain_canvas_max
import memix.core.ui.generated.resources.explain_canvas_min
import memix.feature.video_editor.generated.resources.explain_canvas_reorder
import memix.core.ui.generated.resources.explain_canvas_tallest
import memix.core.ui.generated.resources.explain_canvas_widest
import memix.feature.video_editor.generated.resources.explain_detached
import memix.feature.video_editor.generated.resources.explain_detached_muted
import memix.feature.video_editor.generated.resources.explain_export_empty
import memix.feature.video_editor.generated.resources.explain_export_no_res
import memix.feature.video_editor.generated.resources.explain_export_no_shape
import memix.feature.video_editor.generated.resources.explain_export_no_space
import memix.feature.video_editor.generated.resources.explain_export_shape
import memix.feature.video_editor.generated.resources.explain_last_clip
import memix.feature.video_editor.generated.resources.explain_no_sound
import memix.feature.video_editor.generated.resources.explain_reorder_first
import memix.feature.video_editor.generated.resources.explain_reorder_last
import memix.feature.video_editor.generated.resources.explain_reorder_one
import memix.feature.video_editor.generated.resources.explain_replace_detached
import memix.feature.video_editor.generated.resources.explain_replace_too_short
import memix.feature.video_editor.generated.resources.explain_sound_unreadable
import memix.feature.video_editor.generated.resources.explain_split
import memix.core.ui.generated.resources.explain_text_biggest
import memix.feature.video_editor.generated.resources.explain_text_edge
import memix.core.ui.generated.resources.explain_text_smallest
import memix.feature.video_editor.generated.resources.export_fps
import memix.feature.video_editor.generated.resources.export_stopped_toast
import memix.core.ui.generated.resources.position_size_value
import memix.feature.video_editor.generated.resources.toast_export_fps_lowered
import memix.feature.video_editor.generated.resources.toast_export_res_lowered
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The video editor frame (spec P1-04 → Layout): top bar, preview stage, transport row, the timeline (P1-05) and the
 * tool bar (P1-06). The stage and timeline split their shared space 60/40 by window size only, so the preview never
 * jumps while editing.
 *
 * [previewSurface] draws a preview session's picture and [captionStandIn] one caption with the engine's renderer: the
 * composition root passes the engine's, which a feature can't depend on. [onFreeUpSpace] opens the phone's storage manager, asking for the given number of bytes.
 * The picker for Add media and Replace is the app's too: it opens the one in [VideoEditorUiState.mediaPicker].
 */
@Composable
fun VideoEditorScreen(
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
    previewSurface: @Composable (PreviewSession, Modifier) -> Unit,
    captionStandIn: CaptionStandInSlot,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // System back closes the editor through the app's own navigation, which saves on the way out. The editor takes
    // back itself while something inside it comes first (a selection clears before the editor closes, spec P1-04 →
    // System back order) or while saving fails, to ask first; an open sheet handles its own back.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.unsavedSheetOpen &&
            (state.textEntry != null || state.reorder != null || state.selectedItemId != null || state.isSaveFailing),
        onBackCompleted = { onIntent(VideoEditorIntent.Back) },
    )
    // The sounds sheet, the import sheet and the Volume panel take back and Escape themselves while they're open.
    val ownsEscape = !state.unsavedSheetOpen && !state.sounds.isOpen && !state.isImporting && state.exportSheet == null
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(VideoEditorIntent.AppStarted) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(VideoEditorIntent.AppStopped) }
    // A save needs little room; ask for the import's headroom, so the next import fits too (spec P1-04 → Saving).
    val freeUpSpaceForSave = remember(onFreeUpSpace) { { onFreeUpSpace(ImportMediaUseCase.HEADROOM_BYTES) } }
    val focusTargets = remember { EditorFocusTargets() }
    val panelOpen = state.volumePanel != null || state.textPanel != null || state.canvasPanel != null
    val sheetOpen = state.sounds.isOpen || state.unsavedSheetOpen || state.exportSheet != null || importSheetShown(state.mediaImport)
    ReturnFocus(state.returnFocus, focusTargets)
    // Typing (spec P1-10 → Typing): the field's words live here, so only the field and the stand-in redraw per key.
    val entry = state.textEntry
    val entryField = remember(entry?.serial) { TextFieldState(entry?.initialText.orEmpty(), TextRange(entry?.initialText.orEmpty().length)) }
    val captionLayer = remember { LayerStageState() }
    val typingFrame = remember { TypingFrame() }
    CancelCaptionGestureOnBack(captionLayer, onIntent)

    Box(
        modifier
            .fillMaxSize()
            .background(MemixColors.canvas)
            // Escape does what system back does (spec P1-04 → Interactions); the open sheet handles its own.
            .onPreviewKeyEvent { event ->
                val goesBack = event.key == Key.Escape && ownsEscape
                if (goesBack && event.type == KeyEventType.KeyUp) onIntent(VideoEditorIntent.Back)
                goesBack
            },
    ) {
        // An open sheet hides the whole editor behind it from TalkBack and the keyboard (P1-08 review, F1).
        Column(
            Modifier
                .fillMaxSize()
                .coveredByModal(sheetOpen)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            TopBar(state.canvas, state.canvasPanel, focusTargets, onIntent)
            EditorLayout(
                stage = { PreviewStage(state, onIntent, previewSurface, captionStandIn, captionLayer, typingFrame, { entryField.text.toString() }, focusTargets, freeUpSpaceForSave) },
                transport = { TransportRow(state, onIntent) },
                timeline = {
                    Box(Modifier.coveredByModal(panelOpen), propagateMinConstraints = true) {
                        Timeline(
                            state.timeline,
                            state.selectedItemId,
                            state.trimRanges,
                            state.reorder,
                            state.landed,
                            state.preview,
                            state.thumbnails,
                            state.originalAudio,
                            focusTargets.addSoundPill,
                            focusTargets.addMediaTile,
                            onIntent,
                        )
                    }
                },
                tools = {
                    Box(Modifier.coveredByModal(panelOpen), propagateMinConstraints = true) {
                        ToolBarRegion(state.clipTools, state.reorder, state.timeline.isEmpty, selectedIsMissing(state), focusTargets, onIntent)
                    }
                },
                panel = {
                    VolumePanel(state.volumePanel, state.originalAudio?.muted == true, onIntent, Modifier.fillMaxSize())
                    val panelCaption = selectedCaption(state)
                    TextPanels(state.textPanel, panelCaption?.style, panelCaption?.transform, onIntent, Modifier.fillMaxSize())
                    CanvasPanel(state.canvasPanel, state.canvas, onIntent, Modifier.fillMaxSize())
                },
                entryBar = entry?.let { { TextEntryBar(it, entryField, onIntent) } },
                timelineMinHeight = timelineMinHeight(),
                // While typing, the layout ends at the keyboard: the entry bar rides on it.
                modifier = Modifier.weight(1f).then(if (entry != null) Modifier.windowInsetsPadding(WindowInsets.ime) else Modifier),
            )
        }
        MemeSoundsSheet(state.sounds, onIntent)
        state.mediaImport?.let { EditorImportSheet(it, onIntent, onFreeUpSpace) }
        UnsavedChangesSheet(
            state.unsavedSheetOpen,
            onStay = { onIntent(VideoEditorIntent.StayInEditor) },
            onLeave = { onIntent(VideoEditorIntent.LeaveAnyway) },
            onFreeUpSpace = freeUpSpaceForSave.takeIf { state.canFreeUpSpace },
        )
        ExportSheet(state.exportSheet, state.missingMedia, onIntent, onFreeUpSpace.takeIf { state.canFreeUpSpace })
    }
}

/**
 * The import sheet of Add media and Replace (spec P1-06 → Add media: P1-02's sheet over the editor, with its own scrim).
 * It reads the import's progress itself, so only it redraws while copying.
 */
@Composable
private fun EditorImportSheet(mediaImport: EditorMediaImportUi, onIntent: (VideoEditorIntent) -> Unit, onFreeUpSpace: (Long) -> Unit) {
    val importState by mediaImport.state.collectAsState()
    ImportSheet(
        state = importState,
        onIntent = { onIntent(VideoEditorIntent.Import(it)) },
        onPickAgain = { onIntent(VideoEditorIntent.PickAgain) },
        onFreeUpSpace = onFreeUpSpace,
        drawScrim = true,
    )
}

/**
 * Whether the import sheet of Add media and Replace is up. Only that answer is read here, so the screen doesn't
 * recompose with each progress step.
 */
@Composable
private fun importSheetShown(mediaImport: EditorMediaImportUi?): Boolean {
    val importState = mediaImport?.state?.collectAsState() ?: return false
    val shown by remember(importState) { derivedStateOf { importState.value.showsSheet } }
    return shown
}

/**
 * Moves focus where [returnFocus] says once a surface has closed: back to the opener of a pick, the sheet or the panel,
 * or to a landed sound's Volume tool (spec P1-06 → Accessibility).
 */
@Composable
private fun ReturnFocus(returnFocus: ReturnFocus?, focusTargets: EditorFocusTargets) {
    LaunchedEffect(returnFocus) {
        val target = returnFocus ?: return@LaunchedEffect
        // One frame, so the control the surface covered, or a tool bar that just swapped in, is laid out. A closing
        // sheet lets focus leave during its exit.
        withFrameNanos { }
        focusTargets.moveTo(target.target)
    }
}

/** The selected main clip's file is missing (its thumbnails couldn't find it), so Replace comes first. */
@Composable
private fun selectedIsMissing(state: VideoEditorUiState): Boolean {
    val media = state.selectedItemId?.let { state.timeline.item(it)?.media } ?: return false
    return state.thumbnails?.missingSources?.contains(media) == true
}

/**
 * Close at the start, the ratio pill in the center of the screen (spec P1-11 → The ratio pill) and Export at the end
 * (spec P1-12, the screen's one primary action). The pill shows a custom ratio in lowest terms, also while its stepper
 * is held. A custom [Layout], not overlapping aligns: the pill stays at the true center until a long Export label (es
 * "Exportar" at 200% font) would cover it, then it moves left to clear its neighbors by `space-2`; Close and Export
 * never move or shrink (spec P1-12 → Export button, at large font).
 */
@Composable
private fun TopBar(canvas: CanvasUi?, canvasPanel: CanvasPanelUi?, focusTargets: EditorFocusTargets, onIntent: (VideoEditorIntent) -> Unit) {
    val gap = MemixSpacing.space2
    Layout(
        content = {
            IconButton(MemixIcons.Close, stringResource(UiRes.string.editor_close), { onIntent(VideoEditorIntent.Close) })
            Button(stringResource(Res.string.editor_export), { onIntent(VideoEditorIntent.ExportTool) }, variant = ButtonVariant.Primary)
            if (canvas != null) {
                val label = canvasPanel?.shownTerms?.lowest()?.toString() ?: canvas.ratioLabel
                SettingPill(
                    label,
                    stringResource(UiRes.string.canvas_pill_a11y, label),
                    expanded = canvasPanel != null,
                    onClick = { onIntent(VideoEditorIntent.RatioPill) },
                    modifier = Modifier.focusRequester(focusTargets.ratioPill),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val close = measurables[0].measure(loose)
        val export = measurables[1].measure(loose)
        val pill = measurables.getOrNull(2)?.measure(loose)
        val width = constraints.maxWidth
        val height = maxOf(close.height, export.height, pill?.height ?: 0)
        layout(width, height) {
            close.place(0, (height - close.height) / 2)
            export.place(width - export.width, (height - export.height) / 2)
            if (pill != null) {
                val centered = (width - pill.width) / 2
                val rightmost = width - export.width - gapPx - pill.width
                val leftmost = close.width + gapPx
                pill.place(centered.coerceAtMost(rightmost).coerceAtLeast(leftmost), (height - pill.height) / 2)
            }
        }
    }
}

/**
 * Stage on top, transport row, timeline, tool bar at the bottom. The transport row and the tool bar take what they
 * need (both grow at large font sizes); the timeline takes 40% of the rest but never less than [timelineMinHeight],
 * and the stage everything else. A tool [panel] covers exactly the timeline and the tool bar (ToolPanel), so the
 * preview never moves or resizes while it's open.
 */
/**
 * The editor's regions. While typing ([entryBar] not null) only the stage and the entry bar are placed: the stage takes
 * the space above the bar, and the transport row, timeline and tool bar are under the keyboard (spec P1-10 → Typing).
 */
@Composable
private fun EditorLayout(
    stage: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    timeline: @Composable () -> Unit,
    tools: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    entryBar: (@Composable () -> Unit)?,
    timelineMinHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(stage, transport, timeline, tools, panel, entryBar ?: {}), modifier) { parts, constraints ->
        val (stageParts, transportParts, timelineParts, toolParts, panelParts) = parts
        val entryParts = parts[ENTRY_BAR_SLOT]
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(minWidth = width, maxWidth = width)
        val entry = entryParts.firstOrNull()?.measure(loose)
        if (entry != null) {
            val stageHeight = (height - entry.height).coerceAtLeast(0)
            val stageBox = stageParts.first().measure(Constraints.fixed(width, stageHeight))
            return@Layout layout(width, height) {
                stageBox.place(0, 0)
                entry.place(0, stageHeight)
            }
        }
        val transportRow = transportParts.first().measure(loose)
        val toolBar = toolParts.first().measure(loose)
        val sharedHeight = (height - transportRow.height - toolBar.height).coerceAtLeast(0)
        val timelineHeight = max((sharedHeight * TIMELINE_SHARE).roundToInt(), timelineMinHeight.roundToPx()).coerceAtMost(sharedHeight)
        val stageHeight = sharedHeight - timelineHeight
        val stageBox = stageParts.first().measure(Constraints.fixed(width, stageHeight))
        val timelineBox = timelineParts.first().measure(Constraints.fixed(width, timelineHeight))
        val timelineTop = stageHeight + transportRow.height
        // Nothing to measure while the panel is closed: its slot emits no layout then.
        val panelBoxes = panelParts.map { it.measure(Constraints.fixed(width, (height - timelineTop).coerceAtLeast(0))) }
        layout(width, height) {
            stageBox.place(0, 0)
            transportRow.place(0, stageHeight)
            timelineBox.place(0, timelineTop)
            toolBar.place(0, timelineTop + timelineHeight)
            panelBoxes.forEach { it.place(0, timelineTop) }
        }
    }
}

@Composable
private fun PreviewStage(
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
    previewSurface: @Composable (PreviewSession, Modifier) -> Unit,
    captionStandIn: CaptionStandInSlot,
    captionLayer: LayerStageState,
    typingFrame: TypingFrame,
    entryText: () -> String,
    focusTargets: EditorFocusTargets,
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
    val visibleCaptions = rememberVisibleCaptions(state.captions, state.preview)
    // A traversal group, so screen readers reach the save banner before the preview (spec P1-04 → Accessibility).
    // Clipped, so a caption's frame and its TalkBack bounds never draw over the top bar, transport row or entry bar
    // (P1-10 review M1).
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(MemixColors.stage).semantics { isTraversalGroup = true }) {
        val frame = rememberCanvasFrame(canvas, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), typing = state.textEntry != null, captionLayer, typingFrame)
        // With the Canvas panel open, a tap anywhere on the stage plays or pauses, even over a caption (spec P1-11).
        val gesturesOn = canvas != null && state.textEntry == null && state.stage == StageContent.VIDEO && state.canvasPanel == null
        Box(
            Modifier
                .matchParentSize()
                .then(playOnTap)
                // Inside the stage's tap, so a touch on a caption is the caption's (spec P1-10 → On the preview).
                .captionGestures(gesturesOn, captionLayer, visibleCaptions, state, onIntent)
                .clearAndSetSemantics { contentDescription = previewLabel },
        ) {
            if (canvas != null) {
                CanvasFrame(frame) {
                    state.preview?.let { previewSurface(it.session, Modifier.fillMaxSize()) }
                    // Takes taps off the player's own view, so they reach the stage.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                    when {
                        // The empty frame until the first picture: static, no spinner (spec P1-04 → Preview stage).
                        state.stage == StageContent.OPENING -> Box(Modifier.fillMaxSize().background(MemixColors.surfaceRaised))
                        // No picture of the wrong shape after a ratio change, and an empty project: the canvas background
                        // (black for blur) fills the frame (spec P1-11 → States).
                        state.pictureOutOfShape || state.stage == StageContent.EMPTY -> Box(Modifier.fillMaxSize().background(backgroundColorOf(canvas)))
                    }
                }
            }
        }
        if (canvas != null) {
            FrameEdge({ frame }, visible = state.canvasPanel != null || captionLayer.gesture != null)
            CaptionLayer(
                captionLayer,
                canvas,
                visibleCaptions.value,
                state.selectedItemId,
                state.textEntry,
                entryText,
                state.captionStandIn,
                state.captionMeasure,
                captionStandIn,
                focusTargets.selectedCaption,
                interactive = state.canvasPanel == null,
                onIntent = onIntent,
            )
            FrameMessage(state.stage, frame, onRetry = { onIntent(VideoEditorIntent.RetryPreview) })
        }
        StageMessages(
            state,
            onIntent,
            onFreeUpSpace,
            Modifier.align(Alignment.TopCenter).semantics { traversalIndex = -1f },
        )
    }
}

/** The captions shown at the preview's time; the stage recomposes only when that set changes, not every frame. */
@Composable
private fun rememberVisibleCaptions(captions: ImmutableList<CaptionUi>, preview: EditorPreview?): State<List<CaptionUi>> {
    val position = preview?.positionUs?.collectAsState()
    return remember(captions, position) {
        derivedStateOf {
            val timeUs = position?.value ?: 0L
            captions.filter { it.isShownAt(timeUs) }
        }
    }
}

/**
 * The canvas frame on a stage [stageWidth] × [stageHeight]: the largest rectangle of the canvas ratio, `space-2` in from
 * every side. While typing it never gets bigger, and doesn't move, than it was before (spec P1-10 → Typing).
 */
@Composable
private fun rememberCanvasFrame(
    canvas: CanvasUi?,
    stageWidth: Float,
    stageHeight: Float,
    typing: Boolean,
    layer: LayerStageState,
    typingFrame: TypingFrame,
): CanvasFrameRect {
    val inset = with(LocalDensity.current) { MemixSpacing.space2.toPx() }
    val normalHeight = typingFrame.normalStageHeight
    val areaHeight = if (typing && normalHeight > 0f) min(stageHeight, normalHeight) else stageHeight
    val frame = canvas?.let { CanvasFrameRect.fit(stageWidth, areaHeight, it.aspectRatio, inset) } ?: CanvasFrameRect.Empty
    SideEffect {
        if (!typing) typingFrame.normalStageHeight = stageHeight
        layer.frame = frame
        layer.stageWidth = stageWidth
        layer.stageHeight = stageHeight
    }
    return frame
}

private fun Modifier.captionGestures(
    enabled: Boolean,
    layer: LayerStageState,
    visible: State<List<CaptionUi>>,
    state: VideoEditorUiState,
    onIntent: (VideoEditorIntent) -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val sizes = remember(density) {
        with(density) { LayerTouchSizes(MemixSize.touchTarget.toPx(), MemixSpacing.space1.toPx(), MemixSpacing.space2.toPx()) }
    }
    val selected by rememberUpdatedState(state.selectedItemId)
    val canvasWidth by rememberUpdatedState(state.canvas?.widthPx ?: 0)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentHaptics by rememberUpdatedState(haptics)
    val events = remember { CaptionGestureEvents { currentOnIntent(it) } }
    pointerInput(layer, sizes) {
        detectLayerGestures(layer, { visible.value }, { selected }, { canvasWidth }, sizes, { currentHaptics }, events)
    }
}

/** The stage's gestures as the video editor's caption intents. */
private class CaptionGestureEvents(private val onIntent: (VideoEditorIntent) -> Unit) : LayerGestureEvents {
    override fun onTap(layerId: String) = onIntent(VideoEditorIntent.CaptionTapped(layerId))

    override fun onStarted(layerId: String) = onIntent(VideoEditorIntent.CaptionGestureStarted(layerId))

    override fun onEnded(layerId: String, transform: Transform) = onIntent(VideoEditorIntent.CaptionGestureEnded(layerId, transform))

    // Captions in a video are never locked.
    override fun onLockedTouch(layerId: String) = Unit
}

/** The stage height before typing began; typing keeps the frame inside it (spec P1-10 → Typing). */
private class TypingFrame {
    var normalStageHeight = 0f
}

/** System back during a gesture on a caption cancels it: the caption goes back to where it was (spec P1-04 back order). */
@Composable
private fun CancelCaptionGestureOnBack(layer: LayerStageState, onIntent: (VideoEditorIntent) -> Unit) {
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = layer.gesture != null,
        onBackCompleted = {
            layer.gesture?.cancelled = true
            layer.gesture = null
            onIntent(VideoEditorIntent.CaptionGestureCancelled)
        },
    )
}

/** The selected caption as its panels show it: the stand-in's values while a hold or a commit is on its way. */
private fun selectedCaption(state: VideoEditorUiState): CaptionUi? {
    val itemId = state.selectedItemId ?: return null
    return state.captionStandIn?.takeIf { it.itemId == itemId } ?: state.captions.firstOrNull { it.itemId == itemId }
}

/** The canvas background as the frame shows it without a picture: the color, or black for blur. User content, not chrome. */
private fun backgroundColorOf(canvas: CanvasUi): Color = canvas.backgroundArgb?.let { Color(it.toInt()) } ?: MemixColors.stage

/** What the frame says instead of a picture; drawn over the stage's tap area so its button can be pressed. */
@Composable
private fun FrameMessage(stage: StageContent, frame: CanvasFrameRect, onRetry: () -> Unit) {
    when (stage) {
        StageContent.ERROR -> CanvasFrame(frame) { PreviewError(onRetry) }
        // The frame has no picture and the canvas is black like the stage, so the message takes the stage's whole width
        // (P1-16 review, P6): Hindi at 200% isn't cut.
        StageContent.EMPTY -> Box(Modifier.fillMaxSize().padding(MemixSpacing.space2)) { EmptyDraft() }
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

// The canvas background shows behind it (spec P1-11 → States, "Empty project").
@Composable
private fun EmptyDraft() {
    CenteredMessage {
        Text(stringResource(Res.string.editor_empty_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        // The Add media tool and the tile below are the way on (spec P1-04 → States, with P1-16).
        Text(stringResource(Res.string.editor_empty_body_add), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
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
    // The quietest message: only when nothing else is in the slot, and never announced (spec P1-06 → Preview catching up).
    val updatingShown = state.previewUpdating && banner == null && shownToast == null
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
            FadingToast(if (updatingShown) PreviewUpdating else null) { Toast(stringResource(Res.string.editor_preview_updating), announce = false) }
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
            is ToastMessage.Undone, is ToastMessage.Redone, is ToastMessage.Edited, is ToastMessage.ReorderPosition, is ToastMessage.SoundAdded,
            is ToastMessage.MediaAdded, is ToastMessage.TextAdded, is ToastMessage.TextTransformed, is ToastMessage.TextPositioned,
            -> WHAT_HAPPENED_TOAST_MILLIS
            is ToastMessage.RatioStepped, ToastMessage.ExportStopped -> WHAT_HAPPENED_TOAST_MILLIS
            is ToastMessage.ExportFrameRateLowered, is ToastMessage.ExportResolutionLowered, is ToastMessage.ExportCantDo,
            is ToastMessage.ExportShapeCantDo,
            -> EXPLANATION_TOAST_MILLIS
            // An unexpected move, so it stays as long as an explanation.
            ToastMessage.MissingMedia, is ToastMessage.Explain, is ToastMessage.ReplaceTooShort, ToastMessage.LayersMoved -> EXPLANATION_TOAST_MILLIS
        }
        delay(recommendedTimeoutMillis(accessibilityManager, baseMillis))
        onTimedOut(toast.id)
    }
}

@Composable
private fun toastText(message: ToastMessage): String = when (message) {
    is ToastMessage.Undone -> stringResource(UiRes.string.editor_undo_toast, editName(message.edit))
    is ToastMessage.Redone -> stringResource(UiRes.string.editor_redo_toast, editName(message.edit))
    ToastMessage.MissingMedia -> stringResource(Res.string.editor_missing_media)
    is ToastMessage.Explain -> explanationText(message.explanation)
    is ToastMessage.Edited -> stringResource(UiRes.string.a11y_edit_on_item, editName(message.edit), spokenName(message.item))
    is ToastMessage.ReorderPosition -> stringResource(Res.string.a11y_reorder_position, message.position, message.count)
    is ToastMessage.SoundAdded ->
        stringResource(Res.string.a11y_sound_added, message.title.inLanguage(LocalLocale.current.language), spokenTime(message.atUs))
    is ToastMessage.MediaAdded -> pluralStringResource(Res.plurals.a11y_media_added, message.count, message.count)
    is ToastMessage.TextAdded -> stringResource(Res.string.a11y_text_added, spokenTime(message.atUs))
    is ToastMessage.TextPositioned -> stringResource(UiRes.string.a11y_text_position, message.acrossPercent, message.downPercent)
    is ToastMessage.TextTransformed -> stringResource(
        UiRes.string.a11y_text_transformed,
        stringResource(UiRes.string.position_size_value, message.scalePercent),
        pluralStringResource(UiRes.plurals.a11y_degrees, message.degrees, message.degrees),
    )
    // The timecode is never translated (spec P1-06 → Copy).
    is ToastMessage.ReplaceTooShort -> stringResource(Res.string.explain_replace_too_short, formatTimecode(message.clipLengthUs))
    ToastMessage.LayersMoved -> stringResource(Res.string.canvas_layers_moved)
    // Ratios are never translated (spec P1-11 → Copy).
    is ToastMessage.RatioStepped -> message.label
    is ToastMessage.ExportFrameRateLowered ->
        stringResource(Res.string.toast_export_fps_lowered, message.resolutionLabel, stringResource(Res.string.export_fps, message.frameRate))
    is ToastMessage.ExportResolutionLowered ->
        stringResource(Res.string.toast_export_res_lowered, stringResource(Res.string.export_fps, message.frameRate), message.resolutionLabel)
    is ToastMessage.ExportCantDo ->
        stringResource(Res.string.explain_export_no_res, message.resolutionLabel ?: stringResource(Res.string.export_fps, message.frameRate ?: 0))
    is ToastMessage.ExportShapeCantDo -> stringResource(Res.string.explain_export_shape, message.resolutionLabel, message.highestLabel)
    ToastMessage.ExportStopped -> stringResource(Res.string.export_stopped_toast)
}

@Composable
private fun editName(edit: VideoEdit): String = stringResource(
    when (edit) {
        VideoEdit.SPLIT -> Res.string.edit_split
        VideoEdit.TRIM -> Res.string.edit_trim
        VideoEdit.DELETE -> UiRes.string.edit_delete
        VideoEdit.DUPLICATE -> UiRes.string.edit_duplicate
        VideoEdit.REORDER -> UiRes.string.edit_reorder
        VideoEdit.MOVE -> Res.string.edit_move
        VideoEdit.VOLUME -> Res.string.edit_volume
        VideoEdit.ADD_SOUND -> Res.string.edit_add_sound
        VideoEdit.ADD_MEDIA -> Res.string.edit_add_media
        VideoEdit.REPLACE -> Res.string.edit_replace
        VideoEdit.MUTE_ORIGINAL -> Res.string.edit_mute_original
        VideoEdit.UNMUTE_ORIGINAL -> Res.string.edit_unmute_original
        VideoEdit.DETACH_AUDIO -> Res.string.edit_detach_audio
        VideoEdit.ADD_TEXT -> Res.string.edit_add_text
        VideoEdit.EDIT_TEXT -> Res.string.edit_edit_text
        VideoEdit.TEXT_FONT -> Res.string.edit_text_font
        VideoEdit.TEXT_COLOR -> Res.string.edit_text_color
        VideoEdit.TEXT_OUTLINE -> Res.string.edit_text_outline
        VideoEdit.POSITION_TEXT -> Res.string.edit_move_text
        VideoEdit.TRANSFORM_TEXT -> Res.string.edit_transform_text
        VideoEdit.CANVAS_RATIO -> UiRes.string.edit_canvas_ratio
        VideoEdit.CANVAS_FIT -> Res.string.edit_canvas_fit
        VideoEdit.CANVAS_FILL -> Res.string.edit_canvas_fill
        VideoEdit.CANVAS_BACKGROUND -> UiRes.string.edit_canvas_background
    },
)

@Composable
private fun explanationText(explanation: Explanation): String = stringResource(
    when (explanation) {
        Explanation.SPLIT_OUTSIDE -> Res.string.explain_split
        Explanation.LAST_CLIP -> Res.string.explain_last_clip
        Explanation.REORDER_ONE_CLIP -> Res.string.explain_reorder_one
        Explanation.ALREADY_FIRST -> Res.string.explain_reorder_first
        Explanation.ALREADY_LAST -> Res.string.explain_reorder_last
        Explanation.SOUND_UNREADABLE -> Res.string.explain_sound_unreadable
        Explanation.REPLACE_DETACHED -> Res.string.explain_replace_detached
        Explanation.NO_SOUND -> Res.string.explain_no_sound
        Explanation.DETACHED -> Res.string.explain_detached
        Explanation.DETACHED_MUTED -> Res.string.explain_detached_muted
        Explanation.TEXT_SMALLEST -> UiRes.string.explain_text_smallest
        Explanation.TEXT_BIGGEST -> UiRes.string.explain_text_biggest
        Explanation.TEXT_EDGE -> Res.string.explain_text_edge
        Explanation.CANVAS_WIDEST -> UiRes.string.explain_canvas_widest
        Explanation.CANVAS_TALLEST -> UiRes.string.explain_canvas_tallest
        Explanation.CANVAS_MIN -> UiRes.string.explain_canvas_min
        Explanation.CANVAS_MAX -> UiRes.string.explain_canvas_max
        Explanation.CANVAS_REORDER -> Res.string.explain_canvas_reorder
        Explanation.EXPORT_EMPTY -> Res.string.explain_export_empty
        Explanation.EXPORT_NO_SHAPE -> Res.string.explain_export_no_shape
        Explanation.EXPORT_NO_SPACE -> Res.string.explain_export_no_space
    },
)

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
            IconButton(MemixIcons.Undo, stringResource(UiRes.string.editor_undo), { onIntent(VideoEditorIntent.Undo) }, enabled = state.canUndo)
            IconButton(MemixIcons.Redo, stringResource(UiRes.string.editor_redo), { onIntent(VideoEditorIntent.Redo) }, enabled = state.canRedo)
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

private val NoPosition: StateFlow<Long> = MutableStateFlow(0L)

/** The key FadingToast keeps while "Updating the preview" fades out. */
private object PreviewUpdating

/** The timeline's share of the space it splits with the stage (spec P1-04 → Layout). */
private const val TIMELINE_SHARE = 0.4f

/** Between the time and the length on one line, with real spaces; a timecode, so it is never translated. */
private const val TIMECODE_SEPARATOR = " / "

private const val MICROS_PER_TENTH = 100_000L

// EditorLayout's slots, in order: stage, transport, timeline, tools, panel, entry bar.
private const val ENTRY_BAR_SLOT = 5
