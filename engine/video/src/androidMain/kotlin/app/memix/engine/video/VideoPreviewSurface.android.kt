package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import app.memix.core.domain.video.PreviewSession

// PlayerSurface is unstable API; the player it shows is a CompositionPlayer, which is experimental.
@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
actual fun VideoPreviewSurface(session: PreviewSession, modifier: Modifier) {
    // Every session this engine hands out is a Media3PreviewSession.
    val media3Session = session as? Media3PreviewSession ?: return
    val player by media3Session.player.collectAsState()
    // A SurfaceView, which CompositionPlayer requires; it draws below the window, so the editor's covers and
    // messages drawn on top of it hide it.
    PlayerSurface(player, modifier)
}
