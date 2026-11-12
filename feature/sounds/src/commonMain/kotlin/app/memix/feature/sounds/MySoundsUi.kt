package app.memix.feature.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.domain.mysound.MAX_MY_SOUND_DURATION_MINUTES
import app.memix.core.domain.mysound.MAX_MY_SOUND_NAME_CHARACTERS
import app.memix.core.domain.mysound.MySoundFailure
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.model.text.graphemeCount
import app.memix.core.model.text.takeGraphemes
import app.memix.core.ui.SizeRounding
import app.memix.core.ui.fileSizeText
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.close
import memix.core.ui.generated.resources.import_cancel
import memix.core.ui.generated.resources.import_free_up_space
import memix.core.ui.generated.resources.import_item_video
import memix.core.ui.generated.resources.import_percent
import memix.core.ui.generated.resources.import_pick_again
import memix.core.ui.generated.resources.import_reason_unreadable
import memix.core.ui.generated.resources.import_storage_title
import memix.core.ui.generated.resources.sound_meta
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.mysound_delete
import memix.feature.sounds.generated.resources.mysound_deleted_toast
import memix.feature.sounds.generated.resources.mysound_deleted_unnamed_toast
import memix.feature.sounds.generated.resources.mysound_import_title
import memix.feature.sounds.generated.resources.mysound_item_audio
import memix.feature.sounds.generated.resources.mysound_name_file
import memix.feature.sounds.generated.resources.mysound_name_video
import memix.feature.sounds.generated.resources.mysound_nav_a11y
import memix.feature.sounds.generated.resources.mysound_no_picker_body
import memix.feature.sounds.generated.resources.mysound_no_picker_title
import memix.feature.sounds.generated.resources.mysound_none_body
import memix.feature.sounds.generated.resources.mysound_none_title
import memix.feature.sounds.generated.resources.mysound_reason_denied
import memix.feature.sounds.generated.resources.mysound_reason_no_audio
import memix.feature.sounds.generated.resources.mysound_reason_too_long
import memix.feature.sounds.generated.resources.mysound_reason_unsupported
import memix.feature.sounds.generated.resources.mysound_rename
import memix.feature.sounds.generated.resources.mysound_rename_count
import memix.feature.sounds.generated.resources.mysound_rename_empty
import memix.feature.sounds.generated.resources.mysound_rename_label
import memix.feature.sounds.generated.resources.mysound_rename_save
import memix.feature.sounds.generated.resources.mysound_row_a11y
import memix.feature.sounds.generated.resources.mysound_row_a11y_no_name
import memix.feature.sounds.generated.resources.mysound_storage_body
import memix.feature.sounds.generated.resources.mysound_undo
import memix.feature.sounds.generated.resources.mysounds_import_body
import memix.feature.sounds.generated.resources.mysounds_import_title
import memix.feature.sounds.generated.resources.mysounds_privacy_note
import memix.feature.sounds.generated.resources.mysounds_rights_note
import memix.feature.sounds.generated.resources.mysounds_video_body
import memix.feature.sounds.generated.resources.mysounds_video_title
import memix.feature.sounds.generated.resources.sound_detail_mine_imported
import memix.feature.sounds.generated.resources.sound_detail_mine_video
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the system pickers for My sounds (spec P3-07 → Entry and exit). The app root builds one from the platform's launchers,
 * because platform code stays out of the features; each function returns false when the phone has no app for that picker.
 */
class MySoundPickers(val openFile: () -> Boolean, val openVideo: () -> Boolean) {
    fun open(source: MySoundSource): Boolean = if (source == MySoundSource.VIDEO) openVideo() else openFile()

    companion object {
        /** For previews and screens that never import. */
        val None = MySoundPickers(openFile = { false }, openVideo = { false })
    }
}

// ---- The tab: two navigation rows and the note ----

/** The two actions at the top of the My sounds list, then the note when the list has sounds (spec P3-07 → Layout 1 and 2). */
internal fun androidx.compose.foundation.lazy.LazyListScope.mySoundsHeader(hasSounds: Boolean, pickers: MySoundPickers, onIntent: (SoundsIntent) -> Unit) {
    item(key = "mine-import", contentType = "mine-action") {
        MySoundAction(MemixIcons.Folder, Res.string.mysounds_import_title, Res.string.mysounds_import_body) {
            if (!pickers.openFile()) onIntent(SoundsIntent.MySoundPickerUnavailable(MySoundSource.FILE))
        }
    }
    item(key = "mine-video", contentType = "mine-action") {
        MySoundAction(MemixIcons.Video, Res.string.mysounds_video_title, Res.string.mysounds_video_body, Modifier.padding(top = MemixSpacing.space2)) {
            if (!pickers.openVideo()) onIntent(SoundsIntent.MySoundPickerUnavailable(MySoundSource.VIDEO))
        }
    }
    // With no sounds the empty block's own body carries the privacy line, so the note would say it twice.
    if (hasSounds) item(key = "mine-note", contentType = "mine-note") { MySoundsNote() }
}

/** One navigation row: Secondary weight, no chevron (it opens a picker, it does not go deeper). */
@Composable
private fun MySoundAction(icon: org.jetbrains.compose.resources.DrawableResource, title: StringResource, body: StringResource, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val titleText = stringResource(title)
    val bodyText = stringResource(body)
    val spoken = stringResource(Res.string.mysound_nav_a11y, titleText, bodyText)
    val interactionSource = remember { MutableInteractionSource() }
    val container by pressedColor(interactionSource, MemixColors.surfaceRaised, MemixColors.hairline)
    Row(
        modifier
            .padding(horizontal = MemixSpacing.space4)
            .fillMaxWidth()
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.navRowHeight)
            .clip(MemixShapes.radiusMd)
            .background(container)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MemixColors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(titleText, MemixTheme.type.bodyStrong, color = MemixColors.text)
            Text(bodyText, MemixTheme.type.label, color = MemixColors.textSecondary)
        }
    }
}

@Composable
private fun MySoundsNote() {
    Column(Modifier.fillMaxWidth().padding(start = MemixSpacing.space4, end = MemixSpacing.space4, top = MemixSpacing.space3)) {
        Text(stringResource(Res.string.mysounds_privacy_note), MemixTheme.type.label, color = MemixColors.textSecondary)
        Text(stringResource(Res.string.mysounds_rights_note), MemixTheme.type.label, color = MemixColors.textSecondary)
    }
}

// ---- Names and meta ----

/** The row's name: the title, or "My sound" / "Sound from video" in the app's language for a file that had no name. */
@Composable
internal fun SoundItemUi.nameIn(language: String): String {
    val named = title.inLanguage(language)
    if (named.isNotBlank() || mine == null) return named
    return stringResource(if (mine == MySoundSource.VIDEO) Res.string.mysound_name_video else Res.string.mysound_name_file)
}

@Composable
internal fun SoundDetailsUi.nameIn(language: String): String {
    val named = title.inLanguage(language)
    if (named.isNotBlank() || mine == null) return named
    return stringResource(if (mine == MySoundSource.VIDEO) Res.string.mysound_name_video else Res.string.mysound_name_file)
}

/** "0:12 · Imported" or "0:12 · From a video" (spec P3-07 → Layout 3). */
@Composable
internal fun mineMetaText(length: String, source: MySoundSource): String =
    stringResource(UiRes.string.sound_meta, length, mineOrigin(source))

@Composable
internal fun mineOrigin(source: MySoundSource): String =
    stringResource(if (source == MySoundSource.VIDEO) Res.string.sound_detail_mine_video else Res.string.sound_detail_mine_imported)

// ---- The import sheet's content ----

/** The sheet's title for [state] (spec P3-07 → States). */
@Composable
internal fun myImportTitle(state: MyImportUi): String = when (state) {
    is MyImportUi.Copying -> stringResource(Res.string.mysound_import_title)
    is MyImportUi.Failed -> stringResource(Res.string.mysound_none_title)
    is MyImportUi.NotEnoughSpace -> stringResource(UiRes.string.import_storage_title)
    is MyImportUi.NoPicker -> stringResource(Res.string.mysound_no_picker_title)
}

/** What the import sheet, or the picker's import pane, holds for [state]. */
@Composable
internal fun MyImportContent(state: MyImportUi, onIntent: (SoundsIntent) -> Unit, onPickAgain: () -> Unit, onFreeUpSpace: (Long) -> Unit) {
    when (state) {
        is MyImportUi.Copying -> CopyingContent(state, onCancel = { onIntent(SoundsIntent.CancelMySoundImport) })
        is MyImportUi.Failed -> FailedContent(state, onPickAgain)
        is MyImportUi.NotEnoughSpace -> NotEnoughSpaceContent(state, onFreeUpSpace)
        is MyImportUi.NoPicker -> Column {
            Text(stringResource(Res.string.mysound_no_picker_body), MemixTheme.type.body, color = MemixColors.textSecondary)
            Spacer(Modifier.height(MemixSpacing.space6))
            Button(stringResource(UiRes.string.close), { onIntent(SoundsIntent.CloseMySoundImport) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CopyingContent(state: MyImportUi.Copying, onCancel: () -> Unit) {
    val percentText = stringResource(UiRes.string.import_percent, state.percent)
    Column {
        Text(percentText, MemixTheme.type.timecode, Modifier.align(Alignment.End), color = MemixColors.textSecondary)
        Spacer(Modifier.height(MemixSpacing.space2))
        ProgressBar(state.fraction, stateDescription = percentText)
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(UiRes.string.import_cancel), onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FailedContent(state: MyImportUi.Failed, onPickAgain: () -> Unit) {
    val isVideo = state.source == MySoundSource.VIDEO
    val kindLabel = stringResource(if (isVideo) UiRes.string.import_item_video else Res.string.mysound_item_audio)
    val reason = stringResource(reasonText(state.reason), *reasonArguments(state.reason))
    val name = state.displayName?.takeIf { it.isNotBlank() }
    // "song.mp3, Audio file. Memix can't play this file…" with the language's own punctuation.
    val spoken = if (name != null) stringResource(Res.string.mysound_row_a11y, name, kindLabel, reason) else stringResource(Res.string.mysound_row_a11y_no_name, kindLabel, reason)
    Column {
        Text(stringResource(Res.string.mysound_none_body), MemixTheme.type.body, color = MemixColors.textSecondary)
        Spacer(Modifier.height(MemixSpacing.space4))
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        ) {
            Icon(if (isVideo) MemixIcons.Video else MemixIcons.Folder, contentDescription = null, tint = MemixColors.textSecondary)
            Column(Modifier.weight(1f)) {
                // Cut in the middle, so the extension stays visible.
                Text(name ?: kindLabel, MemixTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(reason, MemixTheme.type.label, color = MemixColors.textSecondary)
            }
        }
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(UiRes.string.import_pick_again), onPickAgain, Modifier.fillMaxWidth(), ButtonVariant.Primary)
    }
}

private fun reasonText(failure: MySoundFailure): StringResource = when (failure) {
    MySoundFailure.UNSUPPORTED -> Res.string.mysound_reason_unsupported
    MySoundFailure.NO_AUDIO -> Res.string.mysound_reason_no_audio
    MySoundFailure.TOO_LONG -> Res.string.mysound_reason_too_long
    MySoundFailure.UNREADABLE -> UiRes.string.import_reason_unreadable
    MySoundFailure.DENIED -> Res.string.mysound_reason_denied
    // A full phone has its own sheet; this is only a fallback if a failure reaches the result with it.
    MySoundFailure.STORAGE -> Res.string.mysound_reason_unsupported
}

private fun reasonArguments(failure: MySoundFailure): Array<Any> =
    if (failure == MySoundFailure.TOO_LONG) arrayOf(MAX_MY_SOUND_DURATION_MINUTES) else emptyArray()

@Composable
private fun NotEnoughSpaceContent(state: MyImportUi.NotEnoughSpace, onFreeUpSpace: (Long) -> Unit) {
    // Needed rounds up and free rounds down, so the two never look equal when there isn't enough.
    val needed = fileSizeText(state.neededBytes, SizeRounding.UP)
    val free = fileSizeText(state.freeBytes, SizeRounding.DOWN)
    Column {
        Text(stringResource(Res.string.mysound_storage_body, needed, free), MemixTheme.type.body, color = MemixColors.textSecondary)
        if (state.canFreeUpSpace) {
            Spacer(Modifier.height(MemixSpacing.space6))
            Button(stringResource(UiRes.string.import_free_up_space), { onFreeUpSpace(state.neededBytes) }, Modifier.fillMaxWidth(), ButtonVariant.Primary)
        }
    }
}

// ---- Rename pane and the details actions ----

/** Rename and Delete under the Primary, where Report sits for a library sound (spec P3-07 → The details sheet). */
@Composable
internal fun MineActions(onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        MineActionButton(MemixIcons.EditText, Res.string.mysound_rename, MemixColors.textSecondary, MemixColors.text, Modifier.weight(1f)) { onIntent(SoundsIntent.OpenRename) }
        // `danger` is 5.2:1 on `surface` and is never used on `surface-raised` (spec P3-07 → Accessibility).
        MineActionButton(MemixIcons.Delete, Res.string.mysound_delete, MemixColors.danger, MemixColors.dangerPressed, Modifier.weight(1f)) { onIntent(SoundsIntent.DeleteMine) }
    }
}

@Composable
private fun MineActionButton(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: StringResource,
    color: Color,
    pressedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val content by pressedColor(interactionSource, color, pressedColor)
    Row(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .heightIn(min = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, size = MemixSize.iconMedium)
        Text(stringResource(label), MemixTheme.type.bodyStrong, color = content, textAlign = TextAlign.Center)
    }
}

/**
 * The Rename pane (spec P3-07 → The details sheet): a one-line field with the whole name selected so typing replaces it, a
 * counter from [COUNTER_FROM] characters, and Save. An empty name does not save: the field says so and takes focus. [emptyCount]
 * counts those taps, so a repeat moves focus again.
 */
@Composable
internal fun RenamePane(name: String, emptyCount: Int, onSave: (String) -> Unit) {
    val field = rememberTextFieldState(name, initialSelection = TextRange(0, name.length))
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(emptyCount) { if (emptyCount > 0) focus.requestFocus() }
    val save = { onSave(field.text.toString()) }
    Column {
        NameField(field, focus, onDone = save)
        val count = field.text.toString().graphemeCount()
        if (emptyCount > 0 && field.text.isBlank()) {
            Text(
                stringResource(Res.string.mysound_rename_empty),
                MemixTheme.type.label,
                Modifier.fillMaxWidth().padding(top = MemixSpacing.space1).semantics { liveRegion = LiveRegionMode.Polite },
                color = MemixColors.danger,
            )
        }
        if (count >= COUNTER_FROM) {
            Text(
                stringResource(Res.string.mysound_rename_count, count, MAX_MY_SOUND_NAME_CHARACTERS),
                MemixTheme.type.caption,
                Modifier.fillMaxWidth().padding(top = MemixSpacing.space1),
                color = MemixColors.textMuted,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(MemixSpacing.space6))
        Button(stringResource(Res.string.mysound_rename_save), save, Modifier.fillMaxWidth(), ButtonVariant.Primary)
    }
}

@Composable
private fun NameField(field: TextFieldState, focus: FocusRequester, onDone: () -> Unit) {
    val label = stringResource(Res.string.mysound_rename_label)
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
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { innerField -> Box(Modifier.padding(MemixSpacing.space3), contentAlignment = Alignment.CenterStart) { innerField() } },
    )
}

// At most 60 user-perceived characters, on one line; a paste that would pass it keeps what fits.
private val NameLimit = InputTransformation {
    val text = asCharSequence().toString()
    val oneLine = text.replace('\n', ' ').replace('\r', ' ')
    val limited = if (oneLine.graphemeCount() > MAX_MY_SOUND_NAME_CHARACTERS) oneLine.takeGraphemes(MAX_MY_SOUND_NAME_CHARACTERS) else oneLine
    if (limited != text) replace(0, length, limited)
}

/** The counter shows from this many characters (spec P3-07 → The details sheet). */
private const val COUNTER_FROM = 48

// ---- Toasts ----

/** The Undo toast for a deleted sound: "Deleted {name}" with a Quiet Undo, for the whole 8 s window (spec P3-07 → States). */
@Composable
internal fun DeletedMineToast(deleted: DeletedMineUi, onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    val named = deleted.name.takeIf { it.isNotBlank() }
    val message = if (named != null) stringResource(Res.string.mysound_deleted_toast, named) else stringResource(Res.string.mysound_deleted_unnamed_toast)
    Toast(message, modifier, action = ToastAction(stringResource(Res.string.mysound_undo)) { onIntent(SoundsIntent.UndoDeleteMine) })
}

/**
 * My sounds' import sheet over the Sounds tab (spec P3-07 → Flow A): P1-02's blocking sheet while converting (Cancel is the
 * only way out), its standard result sheets after. The app places it over the bottom nav; the editor's picker shows the same
 * content as a pane instead. [onFreeUpSpace] opens the phone's storage screen for the given bytes.
 */
@Composable
fun MySoundImportSheet(
    state: State<SoundsUiState>,
    onIntent: (SoundsIntent) -> Unit,
    pickers: MySoundPickers,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val importUi by remember(state) { derivedStateOf { state.value.myImport } }
    // While the sheet slides away it keeps showing its last content instead of going blank.
    var lastShown by remember { mutableStateOf(importUi) }
    SideEffect { if (importUi != null) lastShown = importUi }
    val shown = importUi ?: lastShown
    // Before the sheet comes up (the first 300 ms), system back still cancels the import.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = (importUi as? MyImportUi.Copying)?.sheetShown == false,
        onBackCompleted = { onIntent(SoundsIntent.CancelMySoundImport) },
    )
    // Coming back from the phone's storage screen, or from anywhere else, checks the space again.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { onIntent(SoundsIntent.MySoundImportResumed) }
    Sheet(
        visible = importUi.showsSheet,
        onDismiss = { onIntent(if (shown.isBlocking) SoundsIntent.CancelMySoundImport else SoundsIntent.CloseMySoundImport) },
        title = shown?.let { myImportTitle(it) }.orEmpty(),
        modifier = modifier,
        blocking = shown.isBlocking,
    ) {
        if (shown != null) {
            MyImportContent(
                shown,
                onIntent,
                onPickAgain = {
                    onIntent(SoundsIntent.CloseMySoundImport)
                    if (!pickers.open(shown.source)) onIntent(SoundsIntent.MySoundPickerUnavailable(shown.source))
                },
                onFreeUpSpace = onFreeUpSpace,
            )
        }
    }
}
