package app.memix.engine.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.memix.core.domain.video.ResultPlayer

// PlayerSurface is unstable API. Every player this engine opens is a Media3ResultPlayer.
@OptIn(UnstableApi::class)
@Composable
actual fun VideoResultSurface(player: ResultPlayer, modifier: Modifier) {
    val media3Player = player as? Media3ResultPlayer ?: return
    PlayerSurface(media3Player.player, modifier, SURFACE_TYPE_TEXTURE_VIEW)
}
