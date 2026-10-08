package app.memix.feature.videoeditor.importmedia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.Sheet
import app.memix.core.domain.media.ImportFailure
import app.memix.core.ui.SizeRounding
import app.memix.core.ui.fileSizeText
import kotlinx.collections.immutable.ImmutableList
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.close
import memix.feature.video_editor.generated.resources.import_cancel
import memix.feature.video_editor.generated.resources.import_cap_note
import memix.feature.video_editor.generated.resources.import_continue
import memix.feature.video_editor.generated.resources.import_copied
import memix.feature.video_editor.generated.resources.import_count
import memix.feature.video_editor.generated.resources.import_free_up_space
import memix.feature.video_editor.generated.resources.import_item_photo
import memix.feature.video_editor.generated.resources.import_item_video
import memix.feature.video_editor.generated.resources.import_no_picker_body
import memix.feature.video_editor.generated.resources.import_no_picker_title
import memix.feature.video_editor.generated.resources.import_none_body
import memix.feature.video_editor.generated.resources.import_none_title
import memix.feature.video_editor.generated.resources.import_partial_body
import memix.feature.video_editor.generated.resources.import_partial_title
import memix.feature.video_editor.generated.resources.import_percent
import memix.feature.video_editor.generated.resources.import_pick_again
import memix.feature.video_editor.generated.resources.import_reason_unreadable
import memix.feature.video_editor.generated.resources.import_reason_unsupported
import memix.feature.video_editor.generated.resources.import_row_a11y
import memix.feature.video_editor.generated.resources.import_row_a11y_no_name
import memix.feature.video_editor.generated.resources.import_storage_body
import memix.feature.video_editor.generated.resources.import_storage_title
import memix.feature.video_editor.generated.resources.import_time_left_minutes
import memix.feature.video_editor.generated.resources.import_time_left_seconds
import memix.feature.video_editor.generated.resources.import_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The import sheet over whatever screen the pick started from (spec P1-02 → Import sheet). It draws no scrim of its
 * own: the app draws one shared scrim, so it doesn't blink when the Create sheet hands over to this one.
 * [onPickAgain] closes the sheet and reopens the picker; [onFreeUpSpace] opens the phone's storage screen asking
 * for the given number of bytes.
 */
@Composable
fun ImportSheet(
    state: ImportUiState,
    onIntent: (ImportIntent) -> Unit,
    onPickAgain: () -> Unit,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While the sheet slides away, or holds still under the editor's push, it keeps showing its last content
    // instead of going blank.
    var lastShown by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }
    SideEffect { if (state.hasSheetContent) lastShown = state }
    val shown = if (state.hasSheetContent) state else lastShown

    // Before the sheet comes up (the first 300 ms of copying), system back still cancels the import.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state is ImportUiState.Copying && !state.sheetShown,
        onBackCompleted = { onIntent(ImportIntent.Cancel) },
    )
    // Coming back from the phone's storage screen, or from anywhere else, checks the space again.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(ImportIntent.AppResumed) }

    Sheet(
        visible = state.showsSheet,
        onDismiss = { onIntent(dismissIntentFor(shown)) },
        title = titleFor(shown),
        modifier = modifier,
        blocking = shown.isBlockingSheet,
        drawScrim = false,
    ) {
        val reduceMotion = MemixTheme.reduceMotion
        AnimatedContent(
            targetState = shown,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                val sizeSpec: FiniteAnimationSpec<IntSize> = if (reduceMotion) snap() else tween(MemixMotion.durationSheet)
                (fadeIn(tween(MemixMotion.durationPress)) togetherWith fadeOut(tween(MemixMotion.durationPress)))
                    .using(SizeTransform(clip = false) { _, _ -> sizeSpec })
            },
            // A new progress value updates the content in place; only a new kind of state cross-fades.
            contentKey = { it::class },
            label = "import sheet",
        ) { target ->
            SheetContent(target, onIntent, onPickAgain, onFreeUpSpace)
        }
    }
}

@Composable
private fun SheetContent(
    state: ImportUiState,
    onIntent: (ImportIntent) -> Unit,
    onPickAgain: () -> Unit,
    onFreeUpSpace: (Long) -> Unit,
) {
    when (state) {
        is ImportUiState.Copying -> CopyingContent(state, onCancel = { onIntent(ImportIntent.Cancel) })
        is ImportUiState.SomeNotAdded -> SomeNotAddedContent(state, onContinue = { onIntent(ImportIntent.Continue) })
        is ImportUiState.NoneAdded -> ResultContent(
            body = stringResource(Res.string.import_none_body),
            notAdded = state.notAdded,
            note = null,
            buttonLabel = stringResource(Res.string.import_pick_again),
            onButtonClick = onPickAgain,
        )
        is ImportUiState.NotEnoughSpace -> NotEnoughSpaceContent(state, onFreeUpSpace)
        ImportUiState.NoPicker -> NoPickerContent(onClose = { onIntent(ImportIntent.Close) })
        // Never shown: in these states the sheet is hidden or held under the editor, keeping its last content.
        ImportUiState.Idle, is ImportUiState.OpenEditor -> Unit
    }
}

@Composable
private fun CopyingContent(state: ImportUiState.Copying, onCancel: () -> Unit) {
    val progress = state.progress
    val percentText = stringResource(Res.string.import_percent, progress.percent)
    Column {
        // The percent wraps under the count when both don't fit (large font sizes).
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(Res.string.import_count, progress.itemNumber, progress.itemCount),
                MemixTheme.type.label,
                // Read out when a new item starts, not on every percent (WCAG 2.2 SC 4.1.3).
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MemixColors.textSecondary,
            )
            Text(percentText, MemixTheme.type.timecode, color = MemixColors.textSecondary)
        }
        Spacer(Modifier.height(MemixSpacing.space2))
        ProgressBar(progress.fraction, stateDescription = percentText)
        val detail = copyingDetail(progress)
        if (detail != null) {
            Spacer(Modifier.height(MemixSpacing.space2))
            Text(detail, MemixTheme.type.label, color = MemixColors.textMuted)
        }
        if (state.limit != null) {
            Spacer(Modifier.height(MemixSpacing.space2))
            Text(stringResource(Res.string.import_cap_note, state.limit), MemixTheme.type.label, color = MemixColors.textSecondary)
        }
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(Res.string.import_cancel), onCancel, Modifier.fillMaxWidth())
    }
}

/** The optional line under the bar: time left when it's long, or how much of a file of unknown size is copied. */
@Composable
private fun copyingDetail(progress: CopyProgressUi): String? {
    val copiedBytes = progress.currentFileCopiedBytes
    return when (val timeLeft = progress.timeLeft) {
        is TimeLeft.Seconds -> pluralStringResource(Res.plurals.import_time_left_seconds, timeLeft.value, timeLeft.value)
        is TimeLeft.Minutes -> pluralStringResource(Res.plurals.import_time_left_minutes, timeLeft.value, timeLeft.value)
        null -> if (copiedBytes != null) stringResource(Res.string.import_copied, fileSizeText(copiedBytes)) else null
    }
}

@Composable
private fun SomeNotAddedContent(state: ImportUiState.SomeNotAdded, onContinue: () -> Unit) {
    val limitNote = state.limit?.let { stringResource(Res.string.import_cap_note, it) }
    // When only the limit left items out, its note is the whole message.
    val body = if (state.notAdded.isEmpty() && limitNote != null) limitNote else stringResource(Res.string.import_partial_body)
    ResultContent(
        body = body,
        notAdded = state.notAdded,
        note = limitNote.takeIf { state.notAdded.isNotEmpty() },
        buttonLabel = stringResource(Res.string.import_continue),
        onButtonClick = onContinue,
    )
}

@Composable
private fun ResultContent(
    body: String,
    notAdded: ImmutableList<NotAddedItemUi>,
    note: String?,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    Column {
        Text(body, MemixTheme.type.body, color = MemixColors.textSecondary)
        if (notAdded.isNotEmpty()) {
            Spacer(Modifier.height(MemixSpacing.space4))
            // A long list scrolls here, between the body and the button, which both stay in place.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
            ) {
                notAdded.forEach { item -> NotAddedRow(item) }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(MemixSpacing.space4))
            Text(note, MemixTheme.type.label, color = MemixColors.textSecondary)
        }
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(buttonLabel, onButtonClick, Modifier.fillMaxWidth(), ButtonVariant.Primary)
    }
}

@Composable
private fun NotAddedRow(item: NotAddedItemUi) {
    val kindLabel = stringResource(if (item.isVideo) Res.string.import_item_video else Res.string.import_item_photo)
    val reason = stringResource(
        when (item.reason) {
            ImportFailure.UNREADABLE -> Res.string.import_reason_unreadable
            ImportFailure.UNSUPPORTED -> Res.string.import_reason_unsupported
        },
    )
    // "IMG_2041.MOV, Video. Damaged, ..." with the language's own punctuation (Hindi ends a sentence with "।").
    val spokenRow = if (item.displayName != null) {
        stringResource(Res.string.import_row_a11y, item.displayName, kindLabel, reason)
    } else {
        stringResource(Res.string.import_row_a11y_no_name, kindLabel, reason)
    }
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spokenRow },
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Icon(if (item.isVideo) MemixIcons.Video else MemixIcons.Photo, contentDescription = null, tint = MemixColors.textSecondary)
        Column(Modifier.weight(1f)) {
            // Cut in the middle, so the extension stays visible.
            Text(item.displayName ?: kindLabel, MemixTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(reason, MemixTheme.type.label, color = MemixColors.textSecondary)
        }
    }
}

@Composable
private fun NotEnoughSpaceContent(state: ImportUiState.NotEnoughSpace, onFreeUpSpace: (Long) -> Unit) {
    // Needed rounds up and free rounds down, so the two never look equal when there isn't enough.
    val needed = fileSizeText(state.neededBytes, SizeRounding.UP)
    val free = fileSizeText(state.freeBytes, SizeRounding.DOWN)
    Column {
        Text(stringResource(Res.string.import_storage_body, needed, free), MemixTheme.type.body, color = MemixColors.textSecondary)
        if (state.canFreeUpSpace) {
            Spacer(Modifier.height(MemixSpacing.space6))
            Button(
                stringResource(Res.string.import_free_up_space),
                { onFreeUpSpace(state.neededBytes) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Primary,
            )
        }
    }
}

@Composable
private fun NoPickerContent(onClose: () -> Unit) {
    Column {
        Text(stringResource(Res.string.import_no_picker_body), MemixTheme.type.body, color = MemixColors.textSecondary)
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(Res.string.close), onClose, Modifier.fillMaxWidth())
    }
}

@Composable
private fun titleFor(state: ImportUiState): String = when (state) {
    is ImportUiState.Copying -> stringResource(Res.string.import_title)
    is ImportUiState.SomeNotAdded -> stringResource(Res.string.import_partial_title, state.addedCount, state.pickedCount)
    is ImportUiState.NoneAdded -> stringResource(Res.string.import_none_title)
    is ImportUiState.NotEnoughSpace -> stringResource(Res.string.import_storage_title)
    ImportUiState.NoPicker -> stringResource(Res.string.import_no_picker_title)
    ImportUiState.Idle, is ImportUiState.OpenEditor -> ""
}

// System back and Escape run the screen's own way out: cancel while copying, continue after a partial import.
private fun dismissIntentFor(state: ImportUiState): ImportIntent = when (state) {
    is ImportUiState.Copying -> ImportIntent.Cancel
    is ImportUiState.SomeNotAdded -> ImportIntent.Continue
    is ImportUiState.NoneAdded, is ImportUiState.NotEnoughSpace, ImportUiState.NoPicker -> ImportIntent.Close
    ImportUiState.Idle, is ImportUiState.OpenEditor -> ImportIntent.Close
}
