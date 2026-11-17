package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.memix.core.domain.project.interpolatedLook
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.domain.text.MeasureCaptionUseCase
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.KeyframeCatalog
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.Transform
import app.memix.core.ui.layers.GestureTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A caption as the preview shows it (spec P1-10): its words, look and place, when it's on screen, and its laid-out
 * block at scale 1 in canvas pixels ([block]), which sizes its selection frame and its touch area.
 */
@Immutable
data class CaptionUi(
    val itemId: String,
    val text: String,
    val style: CaptionStyle,
    override val transform: Transform,
    val startUs: Long,
    val endUs: Long,
    val block: CaptionBlockSize,
    /** The caption's keyframes (counted from its start); the stage's frame follows them under the playhead (P4-02b). */
    val keyframes: ImmutableList<Keyframe> = persistentListOf(),
) : GestureTarget {
    override val id: String get() = itemId
    override val boxWidthPx: Float get() = block.widthPx
    override val boxHeightPx: Float get() = block.heightPx
    override val scaleRange: ClosedFloatingPointRange<Float> get() = SCALE_RANGE

    // The video editor has no locked captions.
    override val locked: Boolean get() = false
    override val centerRange: ClosedFloatingPointRange<Float> get() = if (keyframes.isEmpty()) 0f..1f else KeyframeCatalog.CENTER_RANGE

    /** This caption as it looks at [timeUs]: its place from the keyframes when it has them. */
    fun atTime(timeUs: Long): CaptionUi {
        if (keyframes.isEmpty()) return this
        val look = interpolatedLook(keyframes, timeUs - startUs) ?: return this
        return copy(transform = look.transform)
    }

    /** Whether it shows at [timeUs]. */
    fun isShownAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    private companion object {
        val SCALE_RANGE = CaptionCatalog.MIN_SCALE..CaptionCatalog.MAX_SCALE
    }
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
    keyframes = keyframes.toImmutableList(),
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

/** The selected caption's panel over the timeline and tool bar. */
enum class TextPanel { STYLE, POSITION }

