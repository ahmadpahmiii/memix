package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.domain.text.MeasureCaptionUseCase
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Transform

/**
 * A caption as the preview shows it (spec P1-10): its words, look and place, when it's on screen, and its laid-out
 * block at scale 1 in canvas pixels ([block]), which sizes its selection frame and its touch area.
 */
@Immutable
data class CaptionUi(
    val itemId: String,
    val text: String,
    val style: CaptionStyle,
    val transform: Transform,
    val startUs: Long,
    val endUs: Long,
    val block: CaptionBlockSize,
) {
    /** Whether it shows at [timeUs]. */
    fun isShownAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs
}

/** [this] caption as the preview shows it, measured with [measure] on a canvas [canvasWidthPx] × [canvasHeightPx]. */
internal fun TextItem.toCaptionUi(measure: MeasureCaptionUseCase, canvasWidthPx: Int, canvasHeightPx: Int) = CaptionUi(
    itemId = id,
    text = text,
    style = style,
    transform = transform,
    startUs = startUs,
    endUs = startUs + durationUs,
    block = measure(text, style, canvasWidthPx, canvasHeightPx),
)

/**
 * Measures a caption with the engine's renderer while it's typed, so its frame fits the words on every keystroke.
 * Compared by identity: one per editor.
 */
@Stable
class EditorCaptionMeasure internal constructor(private val measure: MeasureCaptionUseCase) {
    /** [text] in [style] at scale 1, in canvas pixels on a canvas [canvasWidthPx] × [canvasHeightPx]. */
    fun blockOf(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize =
        measure(text, style, canvasWidthPx, canvasHeightPx)
}

/**
 * Typing a caption (spec P1-10 → Typing): a new one ([itemId] null) or caption [itemId]'s words. The field starts with
 * [initialText]; [style] and [transform] are how it shows on the preview while typed. A new [serial] is a new entry.
 */
@Immutable
data class TextEntryUi(
    val serial: Long,
    val itemId: String?,
    val initialText: String,
    val style: CaptionStyle,
    val transform: Transform,
)

/** The selected caption's panel over the timeline and tool bar. */
enum class TextPanel { STYLE, POSITION }

/** One tap in the Position panel (spec P1-10 → Position panel). */
enum class PositionStep { SMALLER, BIGGER, ROTATE_LEFT, ROTATE_RIGHT, MOVE_LEFT, MOVE_UP, MOVE_DOWN, MOVE_RIGHT, CENTER }
