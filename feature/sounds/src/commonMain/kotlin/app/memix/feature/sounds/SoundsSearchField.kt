package app.memix.feature.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.IconButton
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sounds_search_a11y
import memix.feature.sounds.generated.resources.sounds_search_clear_a11y
import org.jetbrains.compose.resources.stringResource

/**
 * The search box (spec P3-02 → Layout 2): a magnifier, the typed text, and a Clear button once there is text. The text
 * lives in [field], so a keystroke redraws only this box; the screen forwards it to the ViewModel. Search is the IME
 * action and only hides the keyboard, because results already show as you type. Escape clears a non-empty box.
 */
@Composable
internal fun SoundsSearchField(
    field: TextFieldState,
    hint: String,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val name = stringResource(Res.string.sounds_search_a11y)
    BasicTextField(
        state = field,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MemixSpacing.space4)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .onPreviewKeyEvent { event ->
                val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (isEscape && field.text.isNotEmpty()) onClear()
                isEscape && field.text.isNotEmpty()
            }
            .semantics { contentDescription = name },
        textStyle = MemixTheme.type.body.copy(color = MemixColors.text),
        cursorBrush = SolidColor(MemixColors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = KeyboardActionHandler { focusManager.clearFocus() },
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { innerField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MemixIcons.Search, contentDescription = null, tint = MemixColors.textSecondary, modifier = Modifier.padding(start = MemixSpacing.space3), size = MemixSize.iconMedium)
                Box(Modifier.weight(1f).padding(horizontal = MemixSpacing.space2), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(hint, MemixTheme.type.body, color = MemixColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerField()
                }
                if (field.text.isNotEmpty()) {
                    IconButton(MemixIcons.Close, stringResource(Res.string.sounds_search_clear_a11y), onClear, onSurfaceRaised = true)
                }
            }
        },
    )
}

