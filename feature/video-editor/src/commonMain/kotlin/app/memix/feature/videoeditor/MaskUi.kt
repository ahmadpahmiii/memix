package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.project.MaskStageBox
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.Transform

/** The two views of the Mask panel (spec P4-03b → The Mask panel). */
enum class MaskTab { SHAPE, POSITION }

/**
 * The Mask panel of clip [itemId] (P4-03b). [mask] follows the sliders and the stage's fingers while they move and is null when the clip
 * has none (the None tile). [boxWidthPx] and [boxHeightPx] are the clip's own box at scale 1 in canvas pixels; [overlay] is the overlay's
 * look (its keyframes follow the playhead), null for a main clip, whose box sits in the middle of the canvas.
 */
@Immutable
data class MaskUi(
    val itemId: String,
    val mask: ClipMask?,
    val boxWidthPx: Float,
    val boxHeightPx: Float,
    val overlay: OverlayUi? = null,
    val tab: MaskTab = MaskTab.SHAPE,
) {
    /** The clip's box on the canvas at [timeUs]: an overlay's from its transform (keyed, under the playhead), a main clip's in the middle. */
    fun boxAt(timeUs: Long, canvasWidthPx: Int, canvasHeightPx: Int): MaskStageBox {
        val transform = overlay?.atTime(timeUs)?.transform ?: Transform()
        return MaskStageBox(
            transform.centerX * canvasWidthPx,
            transform.centerY * canvasHeightPx,
            boxWidthPx * transform.scale,
            boxHeightPx * transform.scale,
            transform.rotationDegrees,
        )
    }
}
