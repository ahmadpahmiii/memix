package app.memix.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform

/**
 * Draws one caption with the engine's own caption renderer ([measurer] must be this engine's), for the editor to show
 * while the caption is typed or moved instead of the preview's copy (spec P1-10 → Live editing). Size [modifier] to the
 * canvas frame on screen: the caption is laid out in canvas pixels ([canvasWidthPx] × [canvasHeightPx]), drawn scaled
 * to it and clipped at its edges, exactly as the export draws it. [transform] is read while drawing, so a gesture
 * redraws it without recomposing. The composition root hands it to the editor, like [VideoPreviewSurface].
 * iOS: nothing until P7-04.
 */
@Composable
expect fun CaptionStandIn(
    measurer: CaptionMeasurer,
    text: String,
    style: CaptionStyle,
    transform: () -> Transform,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    modifier: Modifier,
)
