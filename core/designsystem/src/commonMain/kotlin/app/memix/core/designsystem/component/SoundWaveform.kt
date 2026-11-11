package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import kotlin.math.max

/**
 * What a playing [SoundRow] draws under its name (spec P3-03 → The waveform): the sound's [peaks] (linear, 0 to 1, as the
 * catalog's waveform file holds them), or a plain rail when it has none. [played] is how much has played, 0 to 1. It
 * is read in the draw step only, so a playhead that moves 30 times a second redraws the bars and recomposes nothing.
 */
@Stable
class SoundProgress(val peaks: List<Float>?, val played: () -> Float)

/**
 * Bars on a midline: `primary` where the sound has played, `text-muted` for the rest, `waveform-bar-width` wide with
 * `waveform-bar-gap` between and rounded ends. A bar is never shorter than its width, so silence is a dotted line.
 * The size is `waveform-height` ([large] false: in a row) or `waveform-height-large` (the details sheet). Decorative:
 * a screen reader hears the play state from the disc, not from this.
 */
@Composable
fun SoundWaveform(peaks: List<Float>, played: () -> Float, modifier: Modifier = Modifier, large: Boolean = false) {
    val playedColor = MemixColors.primary
    val restColor = MemixColors.textMuted
    Box(
        modifier
            .fillMaxWidth()
            .height(if (large) MemixSize.waveformHeightLarge else MemixSize.waveformHeight)
            .clearAndSetSemantics { }
            .drawWithCache {
                val barWidth = MemixSize.waveformBarWidth.toPx()
                val gap = MemixSize.waveformBarGap.toPx()
                val heights = waveformBarHeights(peaks, waveformBarCount(size.width, barWidth, gap))
                val corner = CornerRadius(barWidth / 2f)
                onDrawBehind {
                    val playedBars = played().coerceIn(0f, 1f) * heights.size
                    heights.forEachIndexed { bar, peak ->
                        val barHeight = max(peak * size.height, barWidth)
                        drawRoundRect(
                            color = if (bar + 0.5f <= playedBars) playedColor else restColor,
                            topLeft = Offset(bar * (barWidth + gap), (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner,
                        )
                    }
                }
            },
    )
}

/** The plain rail a sound shows while it has no waveform: `rail-height` tall, `hairline` with a `primary` fill. Decorative. */
@Composable
fun SoundRail(played: () -> Float, modifier: Modifier = Modifier) {
    val fill = MemixColors.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(MemixSize.railHeight)
            .clearAndSetSemantics { }
            .clip(MemixShapes.radiusFull)
            .background(MemixColors.hairline)
            .drawBehind { drawRect(fill, size = Size(size.width * played().coerceIn(0f, 1f), size.height)) },
    )
}
