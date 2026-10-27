package app.memix.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.video.ResultPlayer

/**
 * Shows the picture of a [ResultPlayer] this engine opened, scaled to [modifier]'s size (the Exported screen). The
 * composition root hands it to the screen, which can't depend on the engine (CLAUDE.md rule 2). Android: Media3's
 * PlayerSurface on a TextureView, which rounds its corners and fades with the screen. iOS: nothing until P7-05.
 */
@Composable
expect fun VideoResultSurface(player: ResultPlayer, modifier: Modifier)
