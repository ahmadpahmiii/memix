package app.memix.feature.drafts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.Sheet
import app.memix.core.domain.drafts.MAX_DRAFT_NAME_CHARACTERS
import app.memix.core.domain.text.graphemeCount
import app.memix.core.domain.text.takeGraphemes
import app.memix.core.model.project.ProjectType
import app.memix.core.ui.SizeRounding
import app.memix.core.ui.fileSizeText
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.drafts_copy_cancel
import memix.feature.drafts.generated.resources.drafts_copy_percent
import memix.feature.drafts.generated.resources.drafts_duplicating_title
import memix.feature.drafts.generated.resources.drafts_free_up_space
import memix.feature.drafts.generated.resources.drafts_name_count
import memix.feature.drafts.generated.resources.drafts_rename_label
import memix.feature.drafts.generated.resources.drafts_rename_save
import memix.feature.drafts.generated.resources.drafts_rename_title
import memix.feature.drafts.generated.resources.drafts_storage_body
import memix.feature.drafts.generated.resources.drafts_storage_title
import memix.feature.drafts.generated.resources.drafts_untitled_photo
import memix.feature.drafts.generated.resources.drafts_untitled_video
import org.jetbrains.compose.resources.stringResource

/**
 * The Drafts tab's sheets: Rename, and a duplicate's "Copying your draft" or "Not enough space". They cover the bottom
 * navigation, so the app places this at the root of its main screen, with `coveredByModal` on what's behind while
 * [DraftsUiState.sheetUp]. [onFreeUpSpace] opens the phone's storage screen asking for the given number of bytes.
 */
@Composable
fun DraftsSheets(state: DraftsUiState, onIntent: (DraftsIntent) -> Unit, onFreeUpSpace: (requestedBytes: Long) -> Unit) {
    // Back from the storage screen (or anywhere else): a duplicate that didn't fit tries again.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(DraftsIntent.AppResumed) }
    RenameSheet(state.renaming, onIntent)
    CopySheet(state.copySheet, canFreeUpSpace = state.canFreeUpSpace, onIntent, onFreeUpSpace)
}

/**
 * Rename (spec → Rename sheet): one line, the whole name selected so typing replaces it, the keyboard up, Save above
 * it (the keyboard's Done saves too), a counter from 50 of 60 characters. Close, scrim, drag, back and Escape change
 * nothing.
 */
@Composable
private fun RenameSheet(renaming: RenameUi?, onIntent: (DraftsIntent) -> Unit) {
    // While the sheet slides away it keeps showing the name it had.
    var lastShown by remember { mutableStateOf(renaming) }
    SideEffect { if (renaming != null) lastShown = renaming }
    val shown = renaming ?: lastShown
    Sheet(
        visible = renaming != null,
        onDismiss = { onIntent(DraftsIntent.CloseRename) },
        title = stringResource(Res.string.drafts_rename_title),
    ) {
        if (shown != null) key(shown.serial) { RenameContent(shown, onIntent) }
    }
}

@Composable
private fun RenameContent(renaming: RenameUi, onIntent: (DraftsIntent) -> Unit) {
    val field = rememberTextFieldState(renaming.name, initialSelection = TextRange(0, renaming.name.length))
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // After the sheet has taken focus for itself, so the field ends up with it and the keyboard opens.
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }
    val save = { onIntent(DraftsIntent.SaveName(field.text.toString())) }
    Column {
        NameField(field, focus, renaming.type, onDone = save)
        val count = field.text.toString().graphemeCount()
        if (count >= COUNTER_FROM) {
            Text(
                stringResource(Res.string.drafts_name_count, count, MAX_DRAFT_NAME_CHARACTERS),
                MemixTheme.type.caption,
                Modifier.fillMaxWidth().padding(top = MemixSpacing.space1),
                color = MemixColors.textMuted,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(Res.string.drafts_rename_save), save, Modifier.fillMaxWidth(), ButtonVariant.Primary)
    }
}

/** P1-10's field look: `surface-raised`, `radius-md`, `space-3` inside, at least a touch target tall, `body` text. */
@Composable
private fun NameField(field: TextFieldState, focus: FocusRequester, type: ProjectType, onDone: () -> Unit) {
    val label = stringResource(Res.string.drafts_rename_label)
    val placeholder = stringResource(if (type == ProjectType.PHOTO) Res.string.drafts_untitled_photo else Res.string.drafts_untitled_video)
    BasicTextField(
        state = field,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .focusRequester(focus)
            .semantics { contentDescription = label },
        inputTransformation = NameLimit,
        textStyle = MemixTheme.type.body.copy(color = MemixColors.text),
        cursorBrush = SolidColor(MemixColors.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        // One line: line breaks can't be typed (spec → Interactions).
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { innerField ->
            Box(Modifier.padding(MemixSpacing.space3), contentAlignment = Alignment.CenterStart) {
                // An unnamed draft opens empty, with its stand-in as the hint.
                if (field.text.isEmpty()) Text(placeholder, MemixTheme.type.body, color = MemixColors.textMuted)
                innerField()
            }
        },
    )
}

/** The duplicate's blocking copy sheet (P1-02's import sheet look) or its "Not enough space" sheet. */
@Composable
private fun CopySheet(
    copySheet: CopySheetUi?,
    canFreeUpSpace: Boolean,
    onIntent: (DraftsIntent) -> Unit,
    onFreeUpSpace: (Long) -> Unit,
) {
    var lastShown by remember { mutableStateOf(copySheet) }
    SideEffect { if (copySheet != null) lastShown = copySheet }
    val shown = copySheet ?: lastShown
    val copying = shown is CopySheetUi.Copying
    Sheet(
        visible = copySheet != null,
        onDismiss = { onIntent(if (copying) DraftsIntent.CancelCopy else DraftsIntent.CloseSpaceSheet) },
        title = stringResource(if (copying) Res.string.drafts_duplicating_title else Res.string.drafts_storage_title),
        blocking = copying,
    ) {
        when (shown) {
            is CopySheetUi.Copying -> CopyingContent(shown.progress, onCancel = { onIntent(DraftsIntent.CancelCopy) })
            is CopySheetUi.NotEnoughSpace -> NotEnoughSpaceContent(shown, canFreeUpSpace, onFreeUpSpace)
            null -> Unit
        }
    }
}

@Composable
private fun CopyingContent(progress: CopyProgress, onCancel: () -> Unit) {
    // Read here only, so the bar's updates don't reach the list behind the sheet.
    val fraction by progress.fraction.collectAsStateWithLifecycle()
    val percent = (fraction * PERCENT).toInt().coerceAtMost(PERCENT - 1)
    val percentText = stringResource(Res.string.drafts_copy_percent, percent)
    Column {
        Text(percentText, MemixTheme.type.timecode, Modifier.align(Alignment.End), color = MemixColors.textSecondary)
        Spacer(Modifier.height(MemixSpacing.space2))
        ProgressBar(fraction, stateDescription = percentText)
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(Res.string.drafts_copy_cancel), onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NotEnoughSpaceContent(space: CopySheetUi.NotEnoughSpace, canFreeUpSpace: Boolean, onFreeUpSpace: (Long) -> Unit) {
    // Needed rounds up and free rounds down, so the two never look equal when there isn't enough.
    val needed = fileSizeText(space.neededBytes, SizeRounding.UP)
    val free = fileSizeText(space.freeBytes, SizeRounding.DOWN)
    Column {
        Text(stringResource(Res.string.drafts_storage_body, needed, free), MemixTheme.type.body, color = MemixColors.textSecondary)
        if (canFreeUpSpace) {
            Spacer(Modifier.height(MemixSpacing.space6))
            Button(
                stringResource(Res.string.drafts_free_up_space),
                { onFreeUpSpace(space.neededBytes) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Primary,
            )
        }
    }
}

// At most 60 user-perceived characters, on one line; a paste that would pass it keeps what fits.
private val NameLimit = InputTransformation {
    val text = asCharSequence().toString()
    val oneLine = text.replace('\n', ' ').replace('\r', ' ')
    val limited = if (oneLine.graphemeCount() > MAX_DRAFT_NAME_CHARACTERS) oneLine.takeGraphemes(MAX_DRAFT_NAME_CHARACTERS) else oneLine
    if (limited != text) replace(0, length, limited)
}

/** The counter shows from this many characters (spec → Rename sheet). */
private const val COUNTER_FROM = 50
private const val PERCENT = 100
