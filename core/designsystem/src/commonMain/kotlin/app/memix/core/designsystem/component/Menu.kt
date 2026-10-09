package app.memix.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixElevation
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.pressedColor
import kotlin.math.roundToInt

/**
 * One action in a [Menu]. A disabled item shows in `text-muted` and does nothing (Zoom in when fully zoomed in).
 * [closesMenu] is false for actions people repeat, such as zoom steps.
 */
@Immutable
class MenuItem(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val closesMenu: Boolean = true,
)

/**
 * A small floating list of actions anchored where the user pressed (`design/system/components/Menu/README.md`):
 * `surface-raised`, `radius-md`, `shadow-float`, items `touch-target` tall. It sits above [pressPoint] when there's
 * room, otherwise below, `space-2` inside the window edges. [pressPoint] is in pixels from the top-left of the
 * composable the menu is placed in. [items] is called only while the menu shows, so state it reads (whether an item
 * can act) recomposes the menu alone.
 *
 * Closes on a tap outside, system back and Escape, each calling [onDismiss]; no scrim. Fades in and out over 120 ms,
 * with or without reduce motion. Screen readers hear [title] as the pane title; focus moves to the first item.
 * Never the only way to an action: every item needs another route.
 */
@Composable
fun Menu(
    expanded: Boolean,
    pressPoint: IntOffset,
    title: String,
    items: () -> List<MenuItem>,
    onDismiss: () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    // Kept in the tree until the fade-out ends.
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    val positionProvider = remember(pressPoint, density) { MenuPositionProvider(pressPoint, density) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        // Not clipped to the window, so the transparent room for the shadow may hang off its edge; the menu itself
        // stays inside (MenuPositionProvider).
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        AnimatedVisibility(
            visibility,
            enter = fadeIn(tween(MemixMotion.durationPress)),
            exit = fadeOut(tween(MemixMotion.durationPress)),
        ) {
            MenuSurface(title, items, onDismiss)
        }
    }
}

@Composable
private fun MenuSurface(title: String, items: () -> List<MenuItem>, onDismiss: () -> Unit) {
    val firstItem = remember { FocusRequester() }
    val surface = remember { FocusRequester() }
    // Touch mode can refuse focus to a clickable item; the menu itself then takes it, so Escape still reaches it.
    LaunchedEffect(Unit) { if (!firstItem.requestFocus()) surface.requestFocus() }
    Column(
        Modifier
            // Room for the shadow: the popup's window clips whatever is drawn outside it.
            .padding(ShadowRoom)
            .dropShadow(MemixShapes.radiusMd, MemixElevation.shadowFloat)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(MemixColors.surfaceRaised) }
            .semantics { paneTitle = title }
            .focusRequester(surface)
            .focusable()
            .onPreviewKeyEvent { event ->
                val closes = event.key == Key.Escape
                if (closes && event.type == KeyEventType.KeyUp) onDismiss()
                closes
            }
            // As wide as the longest item, never under three touch targets.
            .width(IntrinsicSize.Max)
            .widthIn(min = MemixSize.touchTarget * MIN_WIDTH_IN_TARGETS),
    ) {
        items().forEachIndexed { index, item ->
            MenuRow(item, onDismiss, if (index == 0) Modifier.focusRequester(firstItem) else Modifier)
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val fill = pressedColor(interactionSource, MemixColors.surfaceRaised, MemixColors.hairline)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.touchTarget)
            .drawBehind { drawRect(fill.value) }
            .clickable(interactionSource, indication = null, enabled = item.enabled, role = Role.Button) {
                item.onClick()
                if (item.closesMenu) onDismiss()
            }
            .padding(horizontal = MemixSpacing.space4),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(item.label, MemixTheme.type.body, color = if (item.enabled) MemixColors.text else MemixColors.textMuted, maxLines = 1)
    }
}

/**
 * Centers the menu on the press point, above it when it fits under the window's top margin, else below it; always
 * `space-2` inside the window. The popup includes [ShadowRoom] on every side, which is taken back out here.
 */
private class MenuPositionProvider(private val pressPoint: IntOffset, density: Density) : PopupPositionProvider {
    private val marginPx = with(density) { MemixSpacing.space2.roundToPx() }
    private val shadowRoomPx = with(density) { ShadowRoom.roundToPx() }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val menuWidth = popupContentSize.width - 2 * shadowRoomPx
        val menuHeight = popupContentSize.height - 2 * shadowRoomPx
        val pressX = anchorBounds.left + pressPoint.x
        val pressY = anchorBounds.top + pressPoint.y
        val maxLeft = (windowSize.width - marginPx - menuWidth).coerceAtLeast(marginPx)
        val left = (pressX - menuWidth / 2f).roundToInt().coerceIn(marginPx, maxLeft)
        val above = pressY - menuHeight
        val top = if (above >= marginPx) above else (pressY).coerceAtMost(windowSize.height - marginPx - menuHeight)
        return IntOffset(left - shadowRoomPx, top - shadowRoomPx)
    }
}

/** The shadow-float blur plus its downward offset, so the popup's window doesn't cut the shadow off. */
private val ShadowRoom = MemixElevation.shadowFloat.radius + MemixElevation.shadowFloat.offset.y

private const val MIN_WIDTH_IN_TARGETS = 3
