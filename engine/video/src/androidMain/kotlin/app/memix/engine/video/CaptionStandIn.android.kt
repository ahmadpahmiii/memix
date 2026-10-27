package app.memix.engine.video

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform

// core-ktx's withScale isn't a declared dependency of this module; save and restore do the same.
@SuppressLint("UseKtx")
@Composable
actual fun CaptionStandIn(
    measurer: CaptionMeasurer,
    text: String,
    style: CaptionStyle,
    transform: () -> Transform,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    modifier: Modifier,
) {
    // Every measurer this engine binds is its renderer.
    val renderer = measurer as? AndroidCaptionRenderer ?: return
    // Laid out again only when the words, the style or the canvas change; a keystroke costs one layout.
    val layout = remember(renderer, text, style, canvasWidthPx, canvasHeightPx) {
        renderer.layout(text, style, canvasWidthPx, canvasHeightPx)
    }
    Spacer(
        modifier.clipToBounds().drawBehind {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(size.width / canvasWidthPx, size.height / canvasHeightPx)
                layout.draw(native, transform())
                native.restore()
            }
        },
    )
}
