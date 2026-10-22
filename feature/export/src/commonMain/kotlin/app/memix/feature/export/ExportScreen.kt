package app.memix.feature.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.focusable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.PlayDisc
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.coveredByModal
import app.memix.core.domain.video.ExportError
import app.memix.core.domain.video.ResultPlayer
import app.memix.core.domain.video.lengthLabelOf
import app.memix.core.ui.shortDurationText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import memix.feature.export.generated.resources.Res
import memix.feature.export.generated.resources.export_back_to_editor
import memix.feature.export.generated.resources.export_cancel
import memix.feature.export.generated.resources.export_done
import memix.feature.export.generated.resources.export_done_title
import memix.feature.export.generated.resources.export_fail_encoder
import memix.feature.export.generated.resources.export_fail_media
import memix.feature.export.generated.resources.export_fail_other
import memix.feature.export.generated.resources.export_fail_space
import memix.feature.export.generated.resources.export_fail_system
import memix.feature.export.generated.resources.export_fail_title
import memix.feature.export.generated.resources.export_free_up_space
import memix.feature.export.generated.resources.export_keep
import memix.feature.export.generated.resources.export_leave_ok
import memix.feature.export.generated.resources.export_make_another
import memix.feature.export.generated.resources.export_percent
import memix.feature.export.generated.resources.export_pause_a11y
import memix.feature.export.generated.resources.export_percent_a11y
import memix.feature.export.generated.resources.export_play_a11y
import memix.feature.export.generated.resources.export_progress_title
import memix.feature.export.generated.resources.export_saved
import memix.feature.export.generated.resources.export_share
import memix.feature.export.generated.resources.export_share_sheet_title
import memix.feature.export.generated.resources.export_stop
import memix.feature.export.generated.resources.export_stop_title
import memix.feature.export.generated.resources.export_time_left
import memix.feature.export.generated.resources.export_try_again
import memix.feature.export.generated.resources.export_try_lower
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/**
 * The Exporting screen and, in its place when the file is saved, the Exported screen (spec P1-12). [resultSurface]
 * draws the saved file's player (the engine's, from the composition root); [requestNotificationPermission] shows the phone's
 * permission dialog and calls back when it's gone; [onFreeUpSpace] opens the storage screen, null when the phone has
 * none; [onExit] leaves the screen.
 */
@Composable
fun ExportScreen(
    state: ExportUiState,
    onIntent: (ExportIntent) -> Unit,
    resultSurface: @Composable (player: ResultPlayer, modifier: Modifier) -> Unit,
    requestNotificationPermission: (onClosed: () -> Unit) -> Unit,
    onFreeUpSpace: (() -> Unit)?,
    onExit: (ExportExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = state.phase
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.exit == null) {
        onIntent(ExportIntent.Back)
    }
    // One per screen state: the outgoing and the incoming title are both composed during the cross-fade.
    val exportingFocus = remember { FocusRequester() }
    val exportedFocus = remember { FocusRequester() }
    val failedFocus = remember { FocusRequester() }
    LaunchedEffect(state.askNotificationPermission) {
        if (!state.askNotificationPermission) return@LaunchedEffect
        requestNotificationPermission {
            onIntent(ExportIntent.NotificationPermissionAsked)
            // Back to the title after the dialog (spec → Accessibility).
            exportingFocus.requestFocus()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onIntent(ExportIntent.LeftScreen) }
    FinishedHaptic(phase)
    Box(modifier.fillMaxSize().background(MemixColors.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .coveredByModal(state.stopSheetOpen)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(MemixSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
        ) {
            // Exporting → Exported cross-fades the content; the frame keeps its place (spec → Motion).
            AnimatedContent(
                targetState = phase.screen(),
                transitionSpec = { fadeIn(tween(MemixMotion.durationSheet)) togetherWith fadeOut(tween(MemixMotion.durationSheet)) },
                modifier = Modifier.weight(1f),
                label = "export phase",
            ) { screen ->
                Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
                    when (screen) {
                        PhaseScreen.EXPORTING -> Exporting(state, exportingFocus, onIntent)
                        PhaseScreen.EXPORTED -> Exported(state, exportedFocus, resultSurface, onIntent)
                        PhaseScreen.FAILED -> Failed(state, failedFocus, onIntent, onFreeUpSpace)
                    }
                }
            }
        }
        StopSheet(state.stopSheetOpen, onIntent)
    }
}

private enum class PhaseScreen { EXPORTING, EXPORTED, FAILED }

private fun ExportPhase.screen(): PhaseScreen = when (this) {
    is ExportPhase.Exporting -> PhaseScreen.EXPORTING
    is ExportPhase.Exported -> PhaseScreen.EXPORTED
    is ExportPhase.Failed -> PhaseScreen.FAILED
}

// One Confirm when the file lands, if the screen is showing (spec → Haptics).
@Composable
private fun FinishedHaptic(phase: ExportPhase) {
    val haptics = LocalHapticFeedback.current
    val finished = phase is ExportPhase.Exported
    LaunchedEffect(finished) { if (finished) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
}

@Composable
private fun ColumnScope.Exporting(state: ExportUiState, titleFocus: FocusRequester, onIntent: (ExportIntent) -> Unit) {
    val exporting = state.phase as? ExportPhase.Exporting ?: ExportPhase.Exporting(0, null)
    Title(stringResource(Res.string.export_progress_title), titleFocus)
    MemeFrame(state.frame, Modifier.weight(1f).fillMaxWidth())
    Progress(exporting)
    Text(stringResource(Res.string.export_leave_ok), MemixTheme.type.body, color = MemixColors.textSecondary)
    Button(stringResource(Res.string.export_cancel), { onIntent(ExportIntent.Cancel) }, Modifier.fillMaxWidth())
}

@Composable
private fun Title(text: String, focus: FocusRequester, modifier: Modifier = Modifier) {
    // Screen readers start on the title (spec → Accessibility).
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisplayText(text, modifier.focusRequester(focus).focusable().semantics { heading() })
}

// "42%" and "About 12 s left" beside the bar; the time shows after 2 s and only when it's over 10 s (P1-02's rule).
@Composable
private fun Progress(exporting: ExportPhase.Exporting) {
    val percentText = stringResource(Res.string.export_percent, exporting.percent)
    ProgressBar(exporting.percent / PERCENT, stringResource(Res.string.export_percent_a11y, exporting.percent))
    // Screen readers hear milestones only (25, 50, 75%), never every percent (spec → Accessibility): the row's spoken
    // text changes only when a milestone is passed, as a polite live region.
    val milestone = (exporting.percent / MILESTONE) * MILESTONE
    val milestoneText = stringResource(Res.string.export_percent_a11y, milestone)
    Row(
        Modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = milestoneText
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Text(percentText, MemixTheme.type.title)
        val secondsLeft = exporting.secondsLeft?.takeIf { it > TIME_LEFT_FROM_SECONDS }
        if (secondsLeft != null) {
            Text(stringResource(Res.string.export_time_left, shortDurationText(secondsLeft)), MemixTheme.type.body, color = MemixColors.textSecondary)
        }
    }
}

@Composable
private fun ColumnScope.Exported(
    state: ExportUiState,
    titleFocus: FocusRequester,
    resultSurface: @Composable (ResultPlayer, Modifier) -> Unit,
    onIntent: (ExportIntent) -> Unit,
) {
    val exported = state.phase as? ExportPhase.Exported ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Title(stringResource(Res.string.export_done_title), titleFocus, Modifier.weight(1f))
        IconButton(MemixIcons.Close, stringResource(Res.string.export_back_to_editor), { onIntent(ExportIntent.BackToEditor) })
    }
    // The result shrinks before anything else (spec → 200% font), down to 40% of the screen, then the rest scrolls.
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = state.frame?.aspectRatio ?: 1f
        val player = state.resultPlayer
        if (player != null) Result(player, resultSurface, { onIntent(ExportIntent.ToggleResultPlayback) }, Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f))
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
        StatusPill(stringResource(Res.string.export_saved, lengthLabelOf(exported.durationUs), exported.resolutionLabel))
        val chooserTitle = stringResource(Res.string.export_share_sheet_title)
        Button(
            stringResource(Res.string.export_share),
            { onIntent(ExportIntent.Share(chooserTitle)) },
            Modifier.fillMaxWidth(),
            leadingIcon = MemixIcons.Share,
        )
        Footer(
            first = { Button(stringResource(Res.string.export_make_another), { onIntent(ExportIntent.MakeAnother) }, it) },
            last = { Button(stringResource(Res.string.export_done), { onIntent(ExportIntent.Done) }, it, ButtonVariant.Primary) },
        )
    }
}

// The saved file in the canvas shape with the play disc on it. One tap target: play once, or pause. No autoplay.
@Composable
private fun Result(
    player: ResultPlayer,
    surface: @Composable (ResultPlayer, Modifier) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val label = stringResource(if (isPlaying) Res.string.export_pause_a11y else Res.string.export_play_a11y)
    Box(
        modifier
            .clip(MemixShapes.radiusLg)
            .background(MemixColors.stage)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = label, onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        surface(player, Modifier.fillMaxSize())
        AnimatedVisibility(!isPlaying, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
            PlayDisc()
        }
    }
}

// "Saved to your gallery · 0:08 · 1080p": a success pill with the check icon, announced as it appears.
@Composable
private fun StatusPill(text: String) {
    Row(
        Modifier
            .clip(MemixShapes.radiusFull)
            .background(MemixColors.success)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = MemixSpacing.space3, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MemixIcons.Check, contentDescription = null, tint = MemixColors.onTrack, size = MemixSize.iconSmall)
        Text(text, MemixTheme.type.label, color = MemixColors.onTrack)
    }
}

/**
 * Two buttons side by side at equal width, [first] then [last], `space-3` apart; stacked, [first] above [last], when
 * either label doesn't fit its half (200% font), so [last] stays last and lowest.
 */
@Composable
private fun Footer(first: @Composable (Modifier) -> Unit, last: @Composable (Modifier) -> Unit) {
    val gap = MemixSpacing.space3
    Layout(content = { first(Modifier); last(Modifier) }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val half = (width - gapPx) / 2
        val sideBySide = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }
        val itemWidth = if (sideBySide) half else width
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(itemWidth)) }
        val height = if (sideBySide) placeables.maxOf { it.height } else placeables.sumOf { it.height } + gapPx
        layout(width, height) {
            if (sideBySide) {
                placeables[0].place(0, 0)
                placeables[1].place(half + gapPx, 0)
            } else {
                placeables[0].place(0, 0)
                placeables[1].place(0, placeables[0].height + gapPx)
            }
        }
    }
}

@Composable
private fun ColumnScope.Failed(state: ExportUiState, titleFocus: FocusRequester, onIntent: (ExportIntent) -> Unit, onFreeUpSpace: (() -> Unit)?) {
    val failed = state.phase as? ExportPhase.Failed ?: return
    Title(stringResource(Res.string.export_progress_title), titleFocus)
    MemeFrame(state.frame, Modifier.weight(1f).fillMaxWidth())
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Text(stringResource(Res.string.export_fail_title), MemixTheme.type.title)
        Text(stringResource(failed.error.message()), MemixTheme.type.body, color = MemixColors.textSecondary)
    }
    val back = Action(stringResource(Res.string.export_back_to_editor)) { onIntent(ExportIntent.BackToEditor) }
    val tryAgain = Action(stringResource(Res.string.export_try_again)) { onIntent(ExportIntent.TryAgain) }
    val (primary, secondary) = when (failed.error) {
        ExportError.STORAGE_FULL -> (onFreeUpSpace?.let { Action(stringResource(Res.string.export_free_up_space), it) } ?: tryAgain) to back
        ExportError.ENCODER -> {
            val lower = failed.lowerResolution?.let { Action(stringResource(Res.string.export_try_lower, it)) { onIntent(ExportIntent.TryLower) } }
            (lower ?: tryAgain) to back
        }
        ExportError.MISSING_MEDIA, ExportError.DECODER -> back to tryAgain
        ExportError.SYSTEM_STOPPED, ExportError.PROCESS_DEATH, ExportError.UNKNOWN -> tryAgain to back
    }
    Button(primary.label, primary.onClick, Modifier.fillMaxWidth(), ButtonVariant.Primary)
    Button(secondary.label, secondary.onClick, Modifier.fillMaxWidth())
}

private class Action(val label: String, val onClick: () -> Unit)

private fun ExportError.message() = when (this) {
    ExportError.STORAGE_FULL -> Res.string.export_fail_space
    ExportError.ENCODER -> Res.string.export_fail_encoder
    ExportError.MISSING_MEDIA, ExportError.DECODER -> Res.string.export_fail_media
    ExportError.SYSTEM_STOPPED -> Res.string.export_fail_system
    ExportError.PROCESS_DEATH, ExportError.UNKNOWN -> Res.string.export_fail_other
}

/**
 * The meme's frame: the canvas shape on `stage` black, the largest that fits, with the background color and the first
 * clip's first frame placed as it sits on the canvas. Static.
 */
@Composable
private fun MemeFrame(frame: ExportFrameUi?, modifier: Modifier) {
    val picture = rememberDecoded(frame?.picture)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (frame == null) return@BoxWithConstraints
        Box(
            Modifier
                .aspectRatio(frame.aspectRatio, matchHeightConstraintsFirst = maxWidth / maxHeight > frame.aspectRatio)
                .background(frame.backgroundArgb?.let { Color(it.toInt()) } ?: MemixColors.stage)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            if (picture != null) {
                Image(
                    picture,
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(frame.pictureAspectRatio, matchHeightConstraintsFirst = frame.pictureAspectRatio < frame.aspectRatio),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

// JPEG decoding off the main thread.
@Composable
private fun rememberDecoded(bytes: ByteArray?): ImageBitmap? {
    val decoded by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let { withContext(Dispatchers.Default) { it.decodeToImageBitmap() } }
    }
    return decoded
}

@Composable
private fun StopSheet(open: Boolean, onIntent: (ExportIntent) -> Unit) {
    // Close, scrim and back keep exporting (spec → States).
    Sheet(open, onDismiss = { onIntent(ExportIntent.KeepExporting) }, title = stringResource(Res.string.export_stop_title)) {
        Button(stringResource(Res.string.export_keep), { onIntent(ExportIntent.KeepExporting) }, Modifier.fillMaxWidth())
        Button(stringResource(Res.string.export_stop), { onIntent(ExportIntent.Stop) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
    }
}

private const val PERCENT = 100f
private const val TIME_LEFT_FROM_SECONDS = 10L
private const val MILESTONE = 25
