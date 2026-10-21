package app.memix.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform

// TODO(P7-04): draw with the Core Text caption renderer. Until then the editor shows only the caption's frame.
@Composable
actual fun CaptionStandIn(
    measurer: CaptionMeasurer,
    text: String,
    style: CaptionStyle,
    transform: () -> Transform,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    modifier: Modifier,
) = Unit
