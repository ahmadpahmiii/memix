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
import androidx.compose.ui.semantics.contentDescription
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
 * Closes on the close button, a scrim tap, a drag down, system back and Escape.
 * [heroTitle] sets the title in Anton for sheets that start a flow (Create).
 */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    heroTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = MemixTheme.reduceMotion
    val closeLabel = stringResource(Res.string.close)
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = visible, onBackCompleted = onDismiss)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(MemixMotion.durationSheet)),
            exit = fadeOut(tween(MemixMotion.durationSheet)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MemixColors.scrim)
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                    .semantics { contentDescription = closeLabel },
            )
        }
        AnimatedVisibility(
            visible,
            Modifier.align(Alignment.BottomCenter),
            enter = if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideInVertically(tween(MemixMotion.durationSheet)) { it },
            exit = if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutVertically(tween(MemixMotion.durationSheet)) { it },
        ) {
            SheetSurface(title, heroTitle, closeLabel, onDismiss, content)
        }
    }
}

@Composable
private fun SheetSurface(
    title: String,
    heroTitle: Boolean,
    closeLabel: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    // Focus moves to the sheet itself (no ring is drawn there), so Escape reaches it even after a touch open.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .dropShadow(MemixShapes.radiusLgTop, MemixElevation.shadowSheet)
            .clip(MemixShapes.radiusLgTop)
            .background(MemixColors.surface)
            .draggable(
                rememberDraggableState { delta -> scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) } },
                Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffset.value > DismissDistancePx || velocity > DismissVelocityPx) onDismiss()
                    else dragOffset.animateTo(0f, tween(MemixMotion.durationSheet))
                },
            )
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
            .padding(start = MemixSpacing.space4, end = MemixSpacing.space4, bottom = MemixSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = MemixSpacing.space2)
                .size(40.dp, 4.dp)
                .clip(MemixShapes.radiusFull)
                .background(MemixColors.hairline),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (heroTitle) DisplayText(title, Modifier.weight(1f)) else Text(title, MemixTheme.type.title, Modifier.weight(1f))
            CloseButton(closeLabel, onDismiss)
        }
        content()
    }
}

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
