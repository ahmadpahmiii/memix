package app.memix.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixElevation
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import memix.core.designsystem.generated.resources.Res
import memix.core.designsystem.generated.resources.close
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet over everything, nav included, so place it at the root of the screen tree.
 *
 * - **Standard:** closes on its close button, a scrim tap, a drag down, system back and Escape; each calls [onDismiss].
 * - **[blocking]:** for work that must finish or be cancelled, and results the user must acknowledge. No grabber,
 *   close button or drag, and scrim taps do nothing; system back and Escape call [onDismiss], which runs the
 *   screen's cancel or continue action.
 *
 * [heroTitle] sets the title in Anton for sheets that start a flow (Create). With [drawScrim] false the caller draws
 * one [Scrim] for several sheets, so it doesn't blink off and on when one sheet hands over to the next.
 * The sheet grows with its content until its top is `space-10` below the status bar; give the part that should
 * scroll then `Modifier.weight(1f, fill = false)` and a vertical scroll, so the title and bottom button stay put.
 */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    heroTitle: Boolean = false,
    blocking: Boolean = false,
    drawScrim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = MemixTheme.reduceMotion
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = visible, onBackCompleted = onDismiss)

    Box(modifier.fillMaxSize()) {
        if (drawScrim) Scrim(visible, onDismiss = if (blocking) null else onDismiss)
        AnimatedVisibility(
            visible,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = MemixSpacing.space10),
            enter = if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideInVertically(tween(MemixMotion.durationSheet)) { it },
            exit = if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutVertically(tween(MemixMotion.durationSheet)) { it },
        ) {
            SheetSurface(title, heroTitle, blocking, onDismiss, content)
        }
    }
}

/**
 * The dimmed layer behind a sheet, fading in and out with it. A tap calls [onDismiss], or does nothing when it's
 * null (blocking sheets); either way taps never reach the screen below.
 */
@Composable
fun Scrim(visible: Boolean, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val closeLabel = stringResource(Res.string.close)
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(tween(MemixMotion.durationSheet)),
        exit = fadeOut(tween(MemixMotion.durationSheet)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MemixColors.scrim)
                // A screen reader hears "Close" when a tap closes the sheet, and nothing at all when it doesn't.
                .clearAndSetSemantics {
                    if (onDismiss != null) {
                        contentDescription = closeLabel
                        onClick { onDismiss(); true }
                    }
                }
                .clickable(interactionSource = null, indication = null) { onDismiss?.invoke() },
        )
    }
}

@Composable
private fun SheetSurface(
    title: String,
    heroTitle: Boolean,
    blocking: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    // Focus moves to the sheet itself (no ring is drawn there), so Escape reaches it even after a touch open.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val dragToDismiss = if (blocking) {
        Modifier
    } else {
        Modifier.draggable(
            rememberDraggableState { delta -> scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) } },
            Orientation.Vertical,
            onDragStopped = { velocity ->
                if (dragOffset.value > DismissDistancePx || velocity > DismissVelocityPx) onDismiss()
                else dragOffset.animateTo(0f, tween(MemixMotion.durationSheet))
            },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .dropShadow(MemixShapes.radiusLgTop, MemixElevation.shadowSheet)
            .clip(MemixShapes.radiusLgTop)
            .background(MemixColors.surface)
            .then(dragToDismiss)
            // The title doubles as the pane title, so a screen reader announces the sheet and each change of its title.
            .semantics { paneTitle = title }
            .focusRequester(focusRequester)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusable()
            // The sheet holds focus, so a Back key would otherwise go to the focus system and never close it.
            .onPreviewKeyEvent { event ->
                val closes = event.key == Key.Escape || event.key == Key.Back
                if (closes && event.type == KeyEventType.KeyUp) onDismiss()
                closes
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = MemixSpacing.space4, end = MemixSpacing.space4, bottom = MemixSpacing.space6, top = if (blocking) MemixSpacing.space4 else 0.dp),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
    ) {
        if (!blocking) Grabber(Modifier.align(Alignment.CenterHorizontally))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (heroTitle) DisplayText(title, Modifier.weight(1f)) else Text(title, MemixTheme.type.title, Modifier.weight(1f))
            if (!blocking) CloseButton(onDismiss)
        }
        content()
    }
}

@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = MemixSpacing.space2)
            .size(40.dp, 4.dp)
            .clip(MemixShapes.radiusFull)
            .background(MemixColors.hairline),
    )
}

/** Close button labelled "Close" in the user's language. */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    CloseButton(stringResource(Res.string.close), onClick, modifier)

@Composable
fun CloseButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = pressedColor(interactionSource, MemixColors.surface.copy(alpha = 0f), MemixColors.surfaceRaised)
    Box(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusFull)
            .size(MemixSize.touchTarget)
            .clip(MemixShapes.radiusFull)
            .drawBehind { drawRect(container.value) }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MemixIcons.Close, contentDescription = null, tint = MemixColors.text)
    }
}

private const val DismissDistancePx = 160f
private const val DismissVelocityPx = 1200f
