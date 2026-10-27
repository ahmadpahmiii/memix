package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize

/**
 * The big play button over a video that isn't playing (the Exported screen, P1-12): a `primary` disc with the play icon
 * in `on-primary`, `play-disc-size` (56 dp) as on the ExportDone board. Only draws: the tap belongs to the video it sits on, so the whole
 * picture is one target.
 */
@Composable
fun PlayDisc(modifier: Modifier = Modifier) {
    Box(modifier.size(MemixSize.playDiscSize).background(MemixColors.primary, MemixShapes.radiusFull), contentAlignment = Alignment.Center) {
        Icon(MemixIcons.Play, contentDescription = null, tint = MemixColors.onPrimary)
    }
}
