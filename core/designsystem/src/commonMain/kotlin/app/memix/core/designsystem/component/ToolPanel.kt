package app.memix.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text

/**
 * An editor tool's settings (design/system/components/ToolPanel): it takes the place of the timeline and the tool bar,
 * so the preview and the transport row above stay visible and working while the user adjusts. Give it exactly that
 * region, and put [coveredByModal] on what it covers. `surface` with a `hairline` top border; no scrim, shadow or
 * grabber.
 *
 * The top row holds [title] and [doneLabel] (a Ghost button); [onDone] also runs on system back and Escape. There's
 * no Cancel: each committed change is an undo step. [content] (Slider, Toggle and similar rows) scrolls under the top
 * row when it doesn't fit. Opening moves focus to the title; it slides up and down in 200 ms, or fades in 120 ms with
 * reduce motion.
 *
 * With [contentMaxHeight] the panel is as tall as its content up to that height (the photo editor, where no timeline
 * reserves the space; the caller anchors it at the bottom and refits the stage above it), and then scrolls inside.
 * [headerActions] sit between the title and Done and wrap under the title when the row doesn't fit (the Layers panel's
 * Move buttons); with [scrolls] false the content scrolls itself (a lazy list) instead of the panel.
 */
@Composable
fun ToolPanel(
    visible: Boolean,
    title: String,
    doneLabel: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentMaxHeight: Dp? = null,
    headerActions: (@Composable () -> Unit)? = null,
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = MemixTheme.reduceMotion
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = visible, onBackCompleted = onDone)
    AnimatedVisibility(
        visible,
        modifier,
        enter = if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideInVertically(tween(MemixMotion.durationSheet)) { it },
        exit = if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutVertically(tween(MemixMotion.durationSheet)) { it },
    ) {
        val titleFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { titleFocus.requestFocus() }
        Column(
            Modifier
                // The video editor's panel fills the region it covers; the photo editor's is as tall as its content, up to a cap.
                .then(if (contentMaxHeight == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(max = contentMaxHeight))
                // Takes every touch in its region, so nothing reaches the timeline underneath.
                .pointerInput(Unit) {}
                .background(MemixColors.surface)
                .drawBehind { drawRect(MemixColors.hairline, Offset.Zero, Size(size.width, MemixStroke.strokeHairline.toPx())) }
                .semantics { paneTitle = title }
                // Its region reaches the screen's bottom edge; the content stays above the navigation bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .onPreviewKeyEvent { event ->
                    val closes = event.key == Key.Escape
                    if (closes && event.type == KeyEventType.KeyUp) onDone()
                    closes
                }
                .padding(MemixSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
        ) {
            val titleModifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() }
            if (headerActions == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, MemixTheme.type.title, titleModifier.weight(1f))
                    Button(doneLabel, onDone, variant = ButtonVariant.Ghost)
                }
            } else {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, MemixTheme.type.title, titleModifier)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        headerActions()
                        Button(doneLabel, onDone, variant = ButtonVariant.Ghost)
                    }
                }
            }
            Column(
                Modifier.weight(1f, fill = false).then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
                content = content,
            )
        }
    }
}
