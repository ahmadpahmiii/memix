package app.memix.engine.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.video.PreviewSession

// TODO(P7-03): an AVPlayerLayer hosted through UIKitView.
@Composable
actual fun VideoPreviewSurface(session: PreviewSession, modifier: Modifier) {
    Box(modifier)
}
