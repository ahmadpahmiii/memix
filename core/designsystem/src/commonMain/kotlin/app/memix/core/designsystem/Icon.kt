package app.memix.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import memix.core.designsystem.generated.resources.Res
import memix.core.designsystem.generated.resources.ic_arrow_back
import memix.core.designsystem.generated.resources.ic_chevron_right
import memix.core.designsystem.generated.resources.ic_close
import memix.core.designsystem.generated.resources.ic_create
import memix.core.designsystem.generated.resources.ic_drafts
import memix.core.designsystem.generated.resources.ic_globe
import memix.core.designsystem.generated.resources.ic_heart
import memix.core.designsystem.generated.resources.ic_heart_filled
import memix.core.designsystem.generated.resources.ic_home
import memix.core.designsystem.generated.resources.ic_pause
import memix.core.designsystem.generated.resources.ic_photo
import memix.core.designsystem.generated.resources.ic_play
import memix.core.designsystem.generated.resources.ic_sounds
import memix.core.designsystem.generated.resources.ic_templates
import memix.core.designsystem.generated.resources.ic_video
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The Memix-drawn icon set (docs/DESIGN_SYSTEM.md → Iconography). */
object MemixIcons {
    val ArrowBack = Res.drawable.ic_arrow_back
    val ChevronRight = Res.drawable.ic_chevron_right
    val Close = Res.drawable.ic_close
    val Create = Res.drawable.ic_create
    val Drafts = Res.drawable.ic_drafts
    val Globe = Res.drawable.ic_globe
    val Heart = Res.drawable.ic_heart
    val HeartFilled = Res.drawable.ic_heart_filled
    val Home = Res.drawable.ic_home
    val Pause = Res.drawable.ic_pause
    val Photo = Res.drawable.ic_photo
    val Play = Res.drawable.ic_play
    val Sounds = Res.drawable.ic_sounds
    val Templates = Res.drawable.ic_templates
    val Video = Res.drawable.ic_video
}

/** [contentDescription] is null only when a visible label next to the icon already says the same thing. */
@Composable
fun Icon(
    icon: DrawableResource,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(painterResource(icon), contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
