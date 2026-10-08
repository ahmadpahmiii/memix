package app.memix.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixElevation
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text

/** The one action a persistent [Toast] may carry, shown as a Quiet button. */
class ToastAction(val label: String, val onClick: () -> Unit)

/** The × of a persistent [Toast]: hides it, with [label] for screen readers. */
class ToastDismiss(val label: String, val onClick: () -> Unit)

/**
 * A short message that doesn't interrupt (`design/system/components/Toast/README.md`): `surface-raised`, `radius-md`,
 * `shadow-float`, text in `label`, up to three lines. Screen readers hear it politely as a status message; it never
 * takes focus, but its buttons can be focused.
 *
 * The persistent variant (save failures) adds an [action] and a [dismiss] button and stays until the caller hides
 * it. Place it yourself: centered, `space-2` below the top of the editor's stage, `space-4` from the screen edges.
 */
@Composable
fun Toast(
    message: String,
    modifier: Modifier = Modifier,
    action: ToastAction? = null,
    dismiss: ToastDismiss? = null,
) {
    val hasButtons = action != null || dismiss != null
    Row(
        modifier
            .dropShadow(MemixShapes.radiusMd, MemixElevation.shadowFloat)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .semantics { liveRegion = LiveRegionMode.Polite }
            // The buttons bring their own 48 dp targets, so the text keeps the spec's padding and the row grows.
            .padding(start = MemixSpacing.space3, end = if (hasButtons) MemixSpacing.space1 else MemixSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            MemixTheme.type.label,
            Modifier.weight(1f, fill = false).padding(vertical = MemixSpacing.space2),
            maxLines = MAX_LINES,
        )
        if (action != null) Button(action.label, action.onClick, variant = ButtonVariant.Quiet)
        if (dismiss != null) IconButton(MemixIcons.Close, dismiss.label, dismiss.onClick)
    }
}

/**
 * Shows [toast] with a 120 ms fade in and out (the same with reduce motion), keeping the last one on screen while it
 * fades away. A new value replaces the shown one at once: toasts never stack.
 */
@Composable
fun <T : Any> FadingToast(toast: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var lastShown by remember { mutableStateOf<T?>(null) }
    SideEffect { if (toast != null) lastShown = toast }
    val shown = toast ?: lastShown
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = fadeIn(tween(MemixMotion.durationPress)),
        exit = fadeOut(tween(MemixMotion.durationPress)),
    ) {
        shown?.let { content(it) }
    }
}

private const val MAX_LINES = 3
