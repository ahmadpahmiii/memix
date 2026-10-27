package app.memix.core.designsystem.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixTheme

/**
 * Determinate progress: a `primary` fill on a `hairline` rail, full width. Always next to a count or percent in
 * text, because the bar is never the only signal. [progress] runs from 0 to 1; [stateDescription] is what a screen
 * reader says for it, for example "40%". There's no indeterminate version: nothing in Memix moves on its own.
 *
 * The fill moves to each new value over `duration-press` (it jumps with reduce motion), so update it at most
 * 10 times a second.
 */
@Composable
fun ProgressBar(progress: Float, stateDescription: String, modifier: Modifier = Modifier) {
    val target = progress.coerceIn(0f, 1f)
    val animationSpec: AnimationSpec<Float> = if (MemixTheme.reduceMotion) snap() else tween(MemixMotion.durationPress, easing = LinearEasing)
    val shownProgress = animateFloatAsState(target, animationSpec, label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(MemixSize.railHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                this.stateDescription = stateDescription
            }
            // Reads the animated value while drawing, so the fill's movement redraws without recomposing.
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(MemixColors.hairline, cornerRadius = radius)
                val fillWidth = size.width * shownProgress.value
                if (fillWidth <= 0f) return@drawBehind
                val fillStart = if (layoutDirection == LayoutDirection.Rtl) size.width - fillWidth else 0f
                drawRoundRect(MemixColors.primary, Offset(fillStart, 0f), Size(fillWidth, size.height), radius)
            },
    )
}
