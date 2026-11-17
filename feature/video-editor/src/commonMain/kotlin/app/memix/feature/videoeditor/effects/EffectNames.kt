package app.memix.feature.videoeditor.effects

import app.memix.core.model.project.EffectTab
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.fx_tab_blur
import memix.feature.video_editor.generated.resources.fx_tab_color
import memix.feature.video_editor.generated.resources.fx_tab_glitch
import memix.feature.video_editor.generated.resources.fx_tab_light
import memix.feature.video_editor.generated.resources.fx_tab_motion
import memix.feature.video_editor.generated.resources.fx_tab_retro
import memix.feature.video_editor.generated.resources.fx_name_zoom_punch
import memix.feature.video_editor.generated.resources.fx_desc_zoom_punch
import memix.feature.video_editor.generated.resources.fx_name_slow_push
import memix.feature.video_editor.generated.resources.fx_desc_slow_push
import memix.feature.video_editor.generated.resources.fx_name_shake
import memix.feature.video_editor.generated.resources.fx_desc_shake
import memix.feature.video_editor.generated.resources.fx_name_wobble
import memix.feature.video_editor.generated.resources.fx_desc_wobble
import memix.feature.video_editor.generated.resources.fx_name_bounce
import memix.feature.video_editor.generated.resources.fx_desc_bounce
import memix.feature.video_editor.generated.resources.fx_name_flash
import memix.feature.video_editor.generated.resources.fx_desc_flash
import memix.feature.video_editor.generated.resources.fx_name_light_pulse
import memix.feature.video_editor.generated.resources.fx_desc_light_pulse
import memix.feature.video_editor.generated.resources.fx_name_glow
import memix.feature.video_editor.generated.resources.fx_desc_glow
import memix.feature.video_editor.generated.resources.fx_name_spotlight
import memix.feature.video_editor.generated.resources.fx_desc_spotlight
import memix.feature.video_editor.generated.resources.fx_name_sun_leak
import memix.feature.video_editor.generated.resources.fx_desc_sun_leak
import memix.feature.video_editor.generated.resources.fx_name_deep_fry
import memix.feature.video_editor.generated.resources.fx_desc_deep_fry
import memix.feature.video_editor.generated.resources.fx_name_mono
import memix.feature.video_editor.generated.resources.fx_desc_mono
import memix.feature.video_editor.generated.resources.fx_name_negative
import memix.feature.video_editor.generated.resources.fx_desc_negative
import memix.feature.video_editor.generated.resources.fx_name_duotone
import memix.feature.video_editor.generated.resources.fx_desc_duotone
import memix.feature.video_editor.generated.resources.fx_name_poster
import memix.feature.video_editor.generated.resources.fx_desc_poster
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The words of the effects the catalog builds (P4-06a: Motion, Light and Color), by effect id; P4-06b adds its 15 with their strings. */
private val EFFECT_NAMES: Map<String, StringResource> = mapOf(
    "zoom_punch" to Res.string.fx_name_zoom_punch,
    "slow_push" to Res.string.fx_name_slow_push,
    "shake" to Res.string.fx_name_shake,
    "wobble" to Res.string.fx_name_wobble,
    "bounce" to Res.string.fx_name_bounce,
    "flash" to Res.string.fx_name_flash,
    "light_pulse" to Res.string.fx_name_light_pulse,
    "glow" to Res.string.fx_name_glow,
    "spotlight" to Res.string.fx_name_spotlight,
    "sun_leak" to Res.string.fx_name_sun_leak,
    "deep_fry" to Res.string.fx_name_deep_fry,
    "mono" to Res.string.fx_name_mono,
    "negative" to Res.string.fx_name_negative,
    "duotone" to Res.string.fx_name_duotone,
    "poster" to Res.string.fx_name_poster,
)

private val EFFECT_DESCRIPTIONS: Map<String, StringResource> = mapOf(
    "zoom_punch" to Res.string.fx_desc_zoom_punch,
    "slow_push" to Res.string.fx_desc_slow_push,
    "shake" to Res.string.fx_desc_shake,
    "wobble" to Res.string.fx_desc_wobble,
    "bounce" to Res.string.fx_desc_bounce,
    "flash" to Res.string.fx_desc_flash,
    "light_pulse" to Res.string.fx_desc_light_pulse,
    "glow" to Res.string.fx_desc_glow,
    "spotlight" to Res.string.fx_desc_spotlight,
    "sun_leak" to Res.string.fx_desc_sun_leak,
    "deep_fry" to Res.string.fx_desc_deep_fry,
    "mono" to Res.string.fx_desc_mono,
    "negative" to Res.string.fx_desc_negative,
    "duotone" to Res.string.fx_desc_duotone,
    "poster" to Res.string.fx_desc_poster,
)

/** The name under a tile, on the timeline item and in the panel's title; null for an id this build has no words for. */
internal fun effectNameOf(effectId: String): StringResource? = EFFECT_NAMES[effectId]

/** The panel's one-line description of the effect. */
internal fun effectDescriptionOf(effectId: String): StringResource? = EFFECT_DESCRIPTIONS[effectId]

internal fun tabNameOf(tab: EffectTab): StringResource = when (tab) {
    EffectTab.MOTION -> Res.string.fx_tab_motion
    EffectTab.GLITCH -> Res.string.fx_tab_glitch
    EffectTab.LIGHT -> Res.string.fx_tab_light
    EffectTab.COLOR -> Res.string.fx_tab_color
    EffectTab.BLUR -> Res.string.fx_tab_blur
    EffectTab.RETRO -> Res.string.fx_tab_retro
}

/** The names of every effect this build draws in the app's language, for the timeline's painter (it measures text itself, so it needs plain strings). */
@Composable
internal fun effectNameTexts(): Map<String, String> = EFFECT_NAMES.mapValues { (_, name) -> stringResource(name) }
