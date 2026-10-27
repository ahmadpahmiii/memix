package app.memix.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.domain.video.PreviewSession

/**
 * Shows the picture of a [PreviewSession] this engine created, scaled to [modifier]'s size; size it to the
 * project's canvas ratio. The composition root hands it to the editor screen, which can't depend on the engine
 * (CLAUDE.md rule 2). Android: Media3's PlayerSurface (a SurfaceView). iOS: nothing until P7-03.
 */
@Composable
expect fun VideoPreviewSurface(session: PreviewSession, modifier: Modifier)
