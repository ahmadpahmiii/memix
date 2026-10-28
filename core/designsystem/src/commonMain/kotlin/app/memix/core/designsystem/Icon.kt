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
import memix.core.designsystem.generated.resources.ic_arrow
import memix.core.designsystem.generated.resources.ic_arrow_back
import memix.core.designsystem.generated.resources.ic_blur
import memix.core.designsystem.generated.resources.ic_check
import memix.core.designsystem.generated.resources.ic_chevron_right
import memix.core.designsystem.generated.resources.ic_close
import memix.core.designsystem.generated.resources.ic_create
import memix.core.designsystem.generated.resources.ic_delete
import memix.core.designsystem.generated.resources.ic_detach_audio
import memix.core.designsystem.generated.resources.ic_drafts
import memix.core.designsystem.generated.resources.ic_duplicate
import memix.core.designsystem.generated.resources.ic_edit
import memix.core.designsystem.generated.resources.ic_edit_text
import memix.core.designsystem.generated.resources.ic_fill
import memix.core.designsystem.generated.resources.ic_fit
import memix.core.designsystem.generated.resources.ic_globe
import memix.core.designsystem.generated.resources.ic_eye
import memix.core.designsystem.generated.resources.ic_eye_off
import memix.core.designsystem.generated.resources.ic_layers
import memix.core.designsystem.generated.resources.ic_lock
import memix.core.designsystem.generated.resources.ic_opacity
import memix.core.designsystem.generated.resources.ic_unlock
import memix.core.designsystem.generated.resources.ic_heart
import memix.core.designsystem.generated.resources.ic_heart_filled
import memix.core.designsystem.generated.resources.ic_home
import memix.core.designsystem.generated.resources.ic_minus
import memix.core.designsystem.generated.resources.ic_more
import memix.core.designsystem.generated.resources.ic_move_here
import memix.core.designsystem.generated.resources.ic_pause
import memix.core.designsystem.generated.resources.ic_photo
import memix.core.designsystem.generated.resources.ic_play
import memix.core.designsystem.generated.resources.ic_plus
import memix.core.designsystem.generated.resources.ic_position
import memix.core.designsystem.generated.resources.ic_ratio_custom
import memix.core.designsystem.generated.resources.ic_redo
import memix.core.designsystem.generated.resources.ic_reorder
import memix.core.designsystem.generated.resources.ic_replace
import memix.core.designsystem.generated.resources.ic_rotate_left
import memix.core.designsystem.generated.resources.ic_rotate_right
import memix.core.designsystem.generated.resources.ic_scale_rotate
import memix.core.designsystem.generated.resources.ic_share
import memix.core.designsystem.generated.resources.ic_sounds
import memix.core.designsystem.generated.resources.ic_split
import memix.core.designsystem.generated.resources.ic_style
import memix.core.designsystem.generated.resources.ic_templates
import memix.core.designsystem.generated.resources.ic_text
import memix.core.designsystem.generated.resources.ic_undo
import memix.core.designsystem.generated.resources.ic_video
import memix.core.designsystem.generated.resources.ic_volume
import memix.core.designsystem.generated.resources.ic_volume_off
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The Memix-drawn icon set (docs/DESIGN_SYSTEM.md → Iconography). */
object MemixIcons {
    val Arrow = Res.drawable.ic_arrow
    val ArrowBack = Res.drawable.ic_arrow_back
    val Blur = Res.drawable.ic_blur
    val Check = Res.drawable.ic_check
    val ChevronRight = Res.drawable.ic_chevron_right
    val Close = Res.drawable.ic_close
    val Create = Res.drawable.ic_create
    val Delete = Res.drawable.ic_delete
    val DetachAudio = Res.drawable.ic_detach_audio
    val Drafts = Res.drawable.ic_drafts
    val Duplicate = Res.drawable.ic_duplicate
    val Edit = Res.drawable.ic_edit
    val Fill = Res.drawable.ic_fill
    val Fit = Res.drawable.ic_fit
    val EditText = Res.drawable.ic_edit_text
    val Eye = Res.drawable.ic_eye
    val EyeOff = Res.drawable.ic_eye_off
    val Globe = Res.drawable.ic_globe
    val Heart = Res.drawable.ic_heart
    val HeartFilled = Res.drawable.ic_heart_filled
    val Home = Res.drawable.ic_home
    val Layers = Res.drawable.ic_layers
    val Lock = Res.drawable.ic_lock
    val Minus = Res.drawable.ic_minus
    val More = Res.drawable.ic_more
    val MoveHere = Res.drawable.ic_move_here
    val Opacity = Res.drawable.ic_opacity
    val Pause = Res.drawable.ic_pause
    val Photo = Res.drawable.ic_photo
    val Play = Res.drawable.ic_play
    val Plus = Res.drawable.ic_plus
    val Position = Res.drawable.ic_position
    val RatioCustom = Res.drawable.ic_ratio_custom
    val Redo = Res.drawable.ic_redo
    val Reorder = Res.drawable.ic_reorder
    val Replace = Res.drawable.ic_replace
    val RotateLeft = Res.drawable.ic_rotate_left
    val RotateRight = Res.drawable.ic_rotate_right
    val ScaleRotate = Res.drawable.ic_scale_rotate
    val Share = Res.drawable.ic_share
    val Sounds = Res.drawable.ic_sounds
    val Split = Res.drawable.ic_split
    val Style = Res.drawable.ic_style
    val Templates = Res.drawable.ic_templates
    val Text = Res.drawable.ic_text
    val Unlock = Res.drawable.ic_unlock
    val Undo = Res.drawable.ic_undo
    val Video = Res.drawable.ic_video
    val Volume = Res.drawable.ic_volume
    val VolumeOff = Res.drawable.ic_volume_off
}

/** [contentDescription] is null only when a visible label next to the icon already says the same thing. */
@Composable
fun Icon(
    icon: DrawableResource,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = ICON_SIZE,
) {
    Image(painterResource(icon), contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** The icon grammar's canvas: every icon is drawn on 24 × 24, its shapes in the central 20 (DESIGN_SYSTEM → Iconography). */
internal val ICON_SIZE = 24.dp

/** The icon grammar's corner radius for rectangles. */
internal val ICON_CORNER = 1.dp

/** The icon grammar's share of the canvas that shapes live in: the central 20 of 24. */
internal const val ICON_SHAPE_SHARE = 20f / 24f
