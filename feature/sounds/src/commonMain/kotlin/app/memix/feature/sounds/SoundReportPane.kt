package app.memix.feature.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.domain.sound.MAX_REPORT_NOTE_CHARACTERS
import app.memix.core.domain.sound.SoundReportReason
import app.memix.core.domain.sound.limitReportNoteLength
import app.memix.core.model.text.graphemeCount
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sound_detail_report
import memix.feature.sounds.generated.resources.sound_detail_reported
import memix.feature.sounds.generated.resources.sound_report_count
import memix.feature.sounds.generated.resources.sound_report_count_a11y
import memix.feature.sounds.generated.resources.sound_report_failed
import memix.feature.sounds.generated.resources.sound_report_info
import memix.feature.sounds.generated.resources.sound_report_note_label
import memix.feature.sounds.generated.resources.sound_report_offline
import memix.feature.sounds.generated.resources.sound_report_pick_reason
import memix.feature.sounds.generated.resources.sound_report_prompt
import memix.feature.sounds.generated.resources.sound_report_reason_broken
import memix.feature.sounds.generated.resources.sound_report_reason_offensive
import memix.feature.sounds.generated.resources.sound_report_reason_other
import memix.feature.sounds.generated.resources.sound_report_reason_rights
import memix.feature.sounds.generated.resources.sound_report_send
import memix.feature.sounds.generated.resources.sound_report_sending
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Report pane (spec P3-10): the second pane of the details sheet, in the Sounds tab and in the editor's picker. Four
 * reasons (none chosen at first), an optional note of at most 480 characters, and Send report pinned under the scrolling
 * part. [form] is the ViewModel's, so going back to the details and returning keeps what was typed; a failed send keeps
 * it too, with a line above Send and Send still enabled.
 */
@Composable
internal fun ColumnScope.ReportPane(soundName: String, form: SoundReportUi, onIntent: (SoundsIntent) -> Unit) {
    val firstReason = remember { FocusRequester() }
    val send = remember { FocusRequester() }
    val field = rememberTextFieldState(form.note)
    NoteSync(field, onIntent)
    // A message names the control to act on, and the focus goes there (spec P3-10 → Accessibility).
    LaunchedEffect(form.messageCount) {
        when (form.message) {
            SoundReportMessage.PICK_REASON -> firstReason.requestFocus()
            SoundReportMessage.OFFLINE, SoundReportMessage.FAILED -> send.requestFocus()
            null -> Unit
        }
    }
    val editable = !form.sending
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Text(soundName, MemixTheme.type.bodyStrong, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(Res.string.sound_report_prompt), MemixTheme.type.body, color = MemixColors.textSecondary)
        Reasons(form.reason, editable, firstReason, onIntent)
        if (form.message == SoundReportMessage.PICK_REASON) StatusLine(Res.string.sound_report_pick_reason)
        Column(Modifier.padding(top = MemixSpacing.space1), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Text(stringResource(Res.string.sound_report_note_label), MemixTheme.type.label, color = MemixColors.textSecondary)
            NoteField(field, editable)
            NoteCounter(field)
        }
        Text(stringResource(Res.string.sound_report_info), MemixTheme.type.label, color = MemixColors.textSecondary)
    }
    if (form.message == SoundReportMessage.OFFLINE || form.message == SoundReportMessage.FAILED) {
        StatusLine(if (form.message == SoundReportMessage.OFFLINE) Res.string.sound_report_offline else Res.string.sound_report_failed)
    }
    Button(
        stringResource(if (form.sending) Res.string.sound_report_sending else Res.string.sound_report_send),
        { onIntent(SoundsIntent.SendReport) },
        Modifier.fillMaxWidth().focusRequester(send).semantics { liveRegion = LiveRegionMode.Polite },
        variant = ButtonVariant.Primary,
        busy = form.sending,
    )
}

// The field is the form's own TextFieldState so a keystroke redraws the box, not the sheet; the ViewModel keeps the text for the visit.
@Composable
private fun NoteSync(field: TextFieldState, onIntent: (SoundsIntent) -> Unit) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { currentOnIntent(SoundsIntent.ReportNoteChanged(it)) }
    }
}

@Composable
private fun Reasons(chosen: SoundReportReason?, enabled: Boolean, firstReason: FocusRequester, onIntent: (SoundsIntent) -> Unit) {
    val prompt = stringResource(Res.string.sound_report_prompt)
    Column(
        Modifier.selectableGroup().semantics { contentDescription = prompt },
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        SoundReportReason.entries.forEachIndexed { index, reason ->
            ReasonRow(
                label = stringResource(reason.label()),
                selected = reason == chosen,
                enabled = enabled,
                onClick = { onIntent(SoundsIntent.ReportReasonChosen(reason)) },
                modifier = if (index == 0) Modifier.focusRequester(firstReason) else Modifier,
            )
        }
    }
}

private fun SoundReportReason.label(): StringResource = when (this) {
    SoundReportReason.RIGHTS -> Res.string.sound_report_reason_rights
    SoundReportReason.OFFENSIVE -> Res.string.sound_report_reason_offensive
    SoundReportReason.BROKEN -> Res.string.sound_report_reason_broken
    SoundReportReason.OTHER -> Res.string.sound_report_reason_other
}

/** One single-choice row: a `surface-raised` row at least a touch target tall, pressed `hairline`, the radio at the start. */
@Composable
private fun ReasonRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val background = pressedColor(interactionSource, MemixColors.surfaceRaised, MemixColors.hairline)
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .heightIn(min = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(background.value) }
            .selectable(selected, interactionSource, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MemixSpacing.space3, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioMark(selected)
        Text(label, MemixTheme.type.body, color = if (enabled) MemixColors.text else MemixColors.textSecondary)
    }
}

/** Selected: a filled `selection` disc with an `on-primary` dot. Unselected: a `text-secondary` ring. */
@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        Modifier.size(MemixSize.iconMedium).drawBehind {
            val ring = MemixStroke.strokeSelection.toPx()
            if (selected) {
                drawCircle(MemixColors.selection)
                drawCircle(MemixColors.onPrimary, radius = MemixSize.radioDotSize.toPx() / 2)
            } else {
                drawCircle(MemixColors.textSecondary, radius = (size.minDimension - ring) / 2, style = Stroke(ring))
            }
        },
    )
}

/** P1-10's field look: `surface-raised`, `radius-md`, `space-3` inside, `body` text; at least three lines tall. */
@Composable
private fun NoteField(field: TextFieldState, enabled: Boolean) {
    val label = stringResource(Res.string.sound_report_note_label)
    BasicTextField(
        state = field,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .semantics { contentDescription = label },
        enabled = enabled,
        inputTransformation = NoteLimit,
        textStyle = MemixTheme.type.body.copy(color = if (enabled) MemixColors.text else MemixColors.textSecondary),
        cursorBrush = SolidColor(MemixColors.text),
        // The action key is "New line": the note never sends the report.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = NOTE_MIN_LINES),
        decorator = { innerField -> Box(Modifier.padding(MemixSpacing.space3)) { innerField() } },
    )
}

// The counter appears from 400 characters; reading the text here keeps a keystroke from recomposing the pane.
@Composable
private fun NoteCounter(field: TextFieldState) {
    val count by remember(field) { derivedStateOf { field.text.toString().graphemeCount() } }
    if (count < COUNTER_FROM) return
    val spoken = stringResource(Res.string.sound_report_count_a11y, count, MAX_REPORT_NOTE_CHARACTERS)
    Text(
        stringResource(Res.string.sound_report_count, count, MAX_REPORT_NOTE_CHARACTERS),
        MemixTheme.type.caption,
        Modifier.fillMaxWidth().semantics { contentDescription = spoken },
        color = MemixColors.textMuted,
        textAlign = TextAlign.End,
    )
}

// A paste past the limit keeps what fits, never cutting a cluster.
private val NoteLimit = InputTransformation {
    val text = asCharSequence().toString()
    val limited = limitReportNoteLength(text)
    if (limited != text) replace(0, length, limited)
}

/** A polite status message in `text` on `surface` (spec P3-10 → States); the words carry the meaning, not a color. */
@Composable
private fun StatusLine(message: StringResource) {
    Text(
        stringResource(message),
        MemixTheme.type.body,
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The end of the details (spec P3-03 → Layout 5): the Report row, or once this phone has reported the sound the plain line
 * "Reported. Thank you." in its place. The row is a Quiet-looking button in `text-secondary`, not the blue of a main action.
 */
@Composable
internal fun ReportEntry(reported: Boolean, onOpen: () -> Unit, focus: FocusRequester, modifier: Modifier = Modifier) {
    if (reported) {
        Box(modifier.fillMaxWidth().heightIn(min = MemixSize.touchTarget).focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.sound_detail_reported), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        }
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val content by pressedColor(interactionSource, MemixColors.textSecondary, MemixColors.text)
    Row(
        modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .heightIn(min = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onOpen)
            .padding(horizontal = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MemixIcons.Flag, contentDescription = null, tint = content, size = MemixSize.iconMedium)
        Text(stringResource(Res.string.sound_detail_report), MemixTheme.type.bodyStrong, color = content, textAlign = TextAlign.Center)
    }
}

/**
 * From [LARGE_FONT_SCALE] up, the Report row scrolls with the facts instead of sitting under the Primary, so the Primary keeps
 * the room (a proxy for the spec's 40% `pinned-bar-max-fraction` rule, which would need the bars measured; P3-03 → Layout).
 */
@Composable
internal fun reportScrollsWithContent(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/**
 * Back on the details from the Report pane, focus returns to the Report row that opened it; after a report, to the "Reported.
 * Thank you." line that replaced it (spec P3-10 → Accessibility). [reported] may arrive a moment after the pane closes, so the
 * request is made again then.
 */
@Composable
internal fun ReportFocusReturn(reportOpen: Boolean, reported: Boolean, reportRow: FocusRequester) {
    var wasOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(reportOpen) {
        if (wasOpen && !reportOpen) {
            pending = true
            runCatching { reportRow.requestFocus() }
        }
        wasOpen = reportOpen
    }
    LaunchedEffect(reported) {
        if (pending && reported) {
            runCatching { reportRow.requestFocus() }
            pending = false
        }
    }
}

/** On entering the Report pane, focus goes to the sheet's title, which is the pane title (spec P3-10 → Accessibility). */
@Composable
internal fun ReportTitleFocus(reportOpen: Boolean, title: FocusRequester) {
    LaunchedEffect(reportOpen) { if (reportOpen) runCatching { title.requestFocus() } }
}

private const val NOTE_MIN_LINES = 3
private const val COUNTER_FROM = 400
