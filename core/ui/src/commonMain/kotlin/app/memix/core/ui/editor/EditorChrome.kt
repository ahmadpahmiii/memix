package app.memix.core.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.IntOffset
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.component.ToastDismiss
import app.memix.core.ui.layers.CanvasFrameRect
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.editor_dismiss
import memix.core.ui.generated.resources.editor_leave_anyway
import memix.core.ui.generated.resources.editor_save_failed
import memix.core.ui.generated.resources.editor_save_storage_full
import memix.core.ui.generated.resources.editor_saved_a11y
import memix.core.ui.generated.resources.editor_unsaved_body
import memix.core.ui.generated.resources.editor_unsaved_title
import memix.core.ui.generated.resources.import_free_up_space
import org.jetbrains.compose.resources.stringResource

/*
 * The pieces of the editor frame both editors share (spec docs/ux/specs/P1-04-editor-and-preview.md → Saving, Layout): the
 * save banner and the sheet that asks before leaving unsaved changes, the spoken-only announcements, and the canvas frame
 * with its edge.
 */

/** Why the banner says the latest changes aren't saved. */
enum class SaveProblem { STORAGE_FULL, OTHER }

/** How long a toast about what happened stays, before the user's own "time to take action" setting stretches it. */
const val WHAT_HAPPENED_TOAST_MILLIS = 2_000L

/** How long a toast that explains stays: longer, there is something to read. */
const val EXPLANATION_TOAST_MILLIS = 4_000L

/** [baseMillis], or longer when the user's accessibility setting asks for more time to read or act. */
fun recommendedTimeoutMillis(accessibilityManager: AccessibilityManager?, baseMillis: Long): Long =
    accessibilityManager?.calculateRecommendedTimeoutMillis(baseMillis, containsText = true) ?: baseMillis

/** [onFreeUpSpace] is null when the phone's storage screen can't open; the banner then has no action. */
@Composable
fun SaveBanner(problem: SaveProblem, onFreeUpSpace: (() -> Unit)?, onDismiss: () -> Unit) {
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
fun SavedAgainAnnouncement(savedAgainCount: Int) {
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
fun SpokenOnly(text: String) {
    Box(
        Modifier.size(MemixSize.touchTarget).clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    )
}

/**
 * "Your latest changes aren't saved": leaving while saving fails. Its close button, the scrim and back keep the user in
 * the editor ([onStay]); only "Leave anyway" leaves ([onLeave]). [onFreeUpSpace] is null when the phone's storage screen
 * can't open; Leave anyway is then the only button.
 */
@Composable
fun UnsavedChangesSheet(visible: Boolean, onStay: () -> Unit, onLeave: () -> Unit, onFreeUpSpace: (() -> Unit)?) {
    Sheet(visible, onDismiss = onStay, title = stringResource(Res.string.editor_unsaved_title)) {
        Text(stringResource(Res.string.editor_unsaved_body), MemixTheme.type.body, color = MemixColors.textSecondary)
        if (onFreeUpSpace != null) {
            Button(stringResource(Res.string.import_free_up_space), onFreeUpSpace, Modifier.fillMaxWidth(), ButtonVariant.Primary)
        }
        Button(stringResource(Res.string.editor_leave_anyway), onLeave, Modifier.fillMaxWidth())
    }
}

/**
 * The frame's edge (spec P1-11 → The frame edge): a `stroke-hairline` line in `text-muted` just outside the canvas
 * frame, on the stage, so it never covers the meme and is never exported. It fades in and out in 120 ms. [frame] is read
 * while drawing, so a frame that animates (the photo editor's refit above a panel) redraws only this.
 */
@Composable
fun FrameEdge(frame: () -> CanvasFrameRect, visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(MemixMotion.durationPress), label = "frame edge")
    Box(
        Modifier.fillMaxSize().drawBehind {
            val rect = frame()
            if (alpha == 0f || rect.isEmpty) return@drawBehind
            val stroke = MemixStroke.strokeHairline.toPx()
            drawRect(
                MemixColors.textMuted,
                topLeft = Offset(rect.left - stroke / 2, rect.top - stroke / 2),
                size = Size(rect.width + stroke, rect.height + stroke),
                alpha = alpha,
                style = Stroke(stroke),
            )
        },
    )
}

/** The canvas frame on the stage, at [frame]; nothing while the frame is empty. */
@Composable
fun CanvasFrame(frame: CanvasFrameRect, content: @Composable BoxScope.() -> Unit) {
    if (frame.isEmpty) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
            .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() }),
        content = content,
    )
}
