package app.memix.feature.videoeditor.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.domain.text.graphemeCount
import app.memix.core.domain.text.takeGraphemes
import app.memix.core.model.project.CaptionCatalog
import app.memix.feature.videoeditor.TextEntryUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.text_entry_count
import memix.feature.video_editor.generated.resources.text_entry_count_a11y
import memix.feature.video_editor.generated.resources.text_entry_hint
import memix.feature.video_editor.generated.resources.text_entry_label
import memix.feature.video_editor.generated.resources.text_entry_limit_a11y
import org.jetbrains.compose.resources.stringResource

/**
 * The entry bar on the keyboard while a caption is typed (spec P1-10 → Typing): the field (up to 4 lines, then it
 * scrolls; Enter is a new line), Done, and from 200 characters a counter. The field keeps at most 250 user-perceived
 * characters. Every way out commits: Done here, back and Escape through the editor, and the keyboard going away.
 */
@Composable
internal fun TextEntryBar(entry: TextEntryUi, field: TextFieldState, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(entry.serial) {
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { currentOnIntent(VideoEditorIntent.TextEntryChanged(it)) }
    }
    CommitWhenKeyboardGoes { currentOnIntent(VideoEditorIntent.TextEntryDone) }
    val label = stringResource(Res.string.text_entry_label)
    val count = field.text.toString().graphemeCount()
    Column(
        modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .drawBehind { drawRect(MemixColors.hairline, Offset.Zero, Size(size.width, MemixStroke.strokeHairline.toPx())) }
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                state = field,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = MemixSize.touchTarget)
                    .clip(MemixShapes.radiusMd)
                    .background(MemixColors.surfaceRaised)
                    .focusRequester(focus)
                    .semantics { contentDescription = label },
                inputTransformation = CaptionLimit,
                textStyle = MemixTheme.type.body,
                cursorBrush = SolidColor(MemixColors.text),
                // Classic is the all-caps meme look; the keyboard starts in caps, and the text is kept as typed.
                keyboardOptions = KeyboardOptions(
                    capitalization = if (entry.style.fontId == CaptionCatalog.classic.id) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                ),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_FIELD_LINES),
                decorator = { innerField ->
                    Box(Modifier.padding(MemixSpacing.space3), contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) Text(stringResource(Res.string.text_entry_hint), MemixTheme.type.body, color = MemixColors.textMuted)
                        innerField()
                    }
                },
            )
            Button(stringResource(Res.string.panel_done), { onIntent(VideoEditorIntent.TextEntryDone) }, variant = ButtonVariant.Ghost)
        }
        if (count >= COUNTER_FROM) Counter(count)
    }
}

@Composable
private fun Counter(count: Int) {
    val spoken = if (count >= CaptionCatalog.MAX_CHARACTERS) {
        stringResource(Res.string.text_entry_limit_a11y)
    } else {
        stringResource(Res.string.text_entry_count_a11y, count, CaptionCatalog.MAX_CHARACTERS)
    }
    Text(
        stringResource(Res.string.text_entry_count, count, CaptionCatalog.MAX_CHARACTERS),
        MemixTheme.type.caption,
        Modifier
            .fillMaxWidth()
            // Polite: the limit is heard once, when it's reached, without moving focus off the field.
            .semantics {
                contentDescription = spoken
                if (count >= CaptionCatalog.MAX_CHARACTERS) liveRegion = LiveRegionMode.Polite
            },
        color = MemixColors.textMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

/** Commits once the keyboard has shown and then gone away (spec → Typing: the keyboard going away keeps the words). */
@Composable
private fun CommitWhenKeyboardGoes(onGone: () -> Unit) {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val currentOnGone by rememberUpdatedState(onGone)
    LaunchedEffect(ime, density) {
        var shown = false
        snapshotFlow { ime.getBottom(density) > 0 }.distinctUntilChanged().collect { visible ->
            if (visible) shown = true else if (shown) currentOnGone()
        }
    }
}

// At most 250 user-perceived characters; a paste that would pass it keeps what fits.
private val CaptionLimit = InputTransformation {
    val text = asCharSequence().toString()
    if (text.graphemeCount() > CaptionCatalog.MAX_CHARACTERS) replace(0, length, text.takeGraphemes(CaptionCatalog.MAX_CHARACTERS))
}

private const val MAX_FIELD_LINES = 4
private const val COUNTER_FROM = 200
