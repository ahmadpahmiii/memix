package app.memix.feature.videoeditor.transitions

import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.tr_dissolve
import memix.feature.video_editor.generated.resources.tr_dip_black
import memix.feature.video_editor.generated.resources.tr_dip_white
import memix.feature.video_editor.generated.resources.tr_blur_through
import memix.feature.video_editor.generated.resources.tr_push_left
import memix.feature.video_editor.generated.resources.tr_push_right
import memix.feature.video_editor.generated.resources.tr_push_up
import memix.feature.video_editor.generated.resources.tr_push_down
import memix.feature.video_editor.generated.resources.tr_cover
import memix.feature.video_editor.generated.resources.tr_uncover
import memix.feature.video_editor.generated.resources.tr_wipe_right
import memix.feature.video_editor.generated.resources.tr_wipe_down
import memix.feature.video_editor.generated.resources.tr_iris
import memix.feature.video_editor.generated.resources.tr_clock
import memix.feature.video_editor.generated.resources.tr_blinds
import memix.feature.video_editor.generated.resources.tr_zoom_through
import memix.feature.video_editor.generated.resources.tr_twirl
import memix.feature.video_editor.generated.resources.tr_flip
import memix.feature.video_editor.generated.resources.tr_glitch_cut
import memix.feature.video_editor.generated.resources.tr_pixel_swap
import org.jetbrains.compose.resources.StringResource

/** The name of transition [id] under its tile and in the spoken description, or null for an id this build doesn't draw. */
internal fun transitionNameOf(id: String): StringResource? = when (id) {
        "dissolve" -> Res.string.tr_dissolve
        "dip_black" -> Res.string.tr_dip_black
        "dip_white" -> Res.string.tr_dip_white
        "blur_through" -> Res.string.tr_blur_through
        "push_left" -> Res.string.tr_push_left
        "push_right" -> Res.string.tr_push_right
        "push_up" -> Res.string.tr_push_up
        "push_down" -> Res.string.tr_push_down
        "cover" -> Res.string.tr_cover
        "uncover" -> Res.string.tr_uncover
        "wipe_right" -> Res.string.tr_wipe_right
        "wipe_down" -> Res.string.tr_wipe_down
        "iris" -> Res.string.tr_iris
        "clock" -> Res.string.tr_clock
        "blinds" -> Res.string.tr_blinds
        "zoom_through" -> Res.string.tr_zoom_through
        "twirl" -> Res.string.tr_twirl
        "flip" -> Res.string.tr_flip
        "glitch_cut" -> Res.string.tr_glitch_cut
        "pixel_swap" -> Res.string.tr_pixel_swap
    else -> null
}
