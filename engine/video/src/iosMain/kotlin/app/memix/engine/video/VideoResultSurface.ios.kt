package app.memix.engine.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.video.ResultPlayer

// TODO(P7-05): an AVPlayerLayer hosted through UIKitView.
@Composable
actual fun VideoResultSurface(player: ResultPlayer, modifier: Modifier) {
    Box(modifier)
}
