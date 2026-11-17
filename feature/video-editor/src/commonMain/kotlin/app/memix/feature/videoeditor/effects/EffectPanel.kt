package app.memix.feature.videoeditor.effects

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.EffectCatalog
import app.memix.feature.videoeditor.EffectPanelUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.core.ui.generated.resources.position_size_value
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.tool_intensity
import org.jetbrains.compose.resources.stringResource

/**
 * The Intensity panel of the selected effect item (spec docs/ux/specs/P4-06-video-effects.md → The Intensity panel): the effect's name as the title,
 * one slider 0 to 100% in steps of 10% (a double-tap on the thumb resets to the effect's own default, 0% is allowed and does nothing), and the effect's
 * one-line description under it. The picture follows the slider live; one undo step on release. A ToolPanel over the timeline and tool bar, shown while
 * [panel] isn't null.
 */
@Composable
internal fun EffectPanel(panel: EffectPanelUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val effectId = panel?.effectId
    val name = effectId?.let(::effectNameOf)?.let { stringResource(it) }.orEmpty()
    ToolPanel(
        visible = panel != null,
        title = name,
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseIntensity) },
        modifier = modifier,
    ) {
        val intensity = panel?.intensity ?: 0f
        Slider(
            label = stringResource(Res.string.tool_intensity),
            value = intensity,
            onValueChange = { onIntent(VideoEditorIntent.EffectIntensityChanged(it)) },
            valueText = stringResource(UiRes.string.position_size_value, (intensity * PERCENT).roundToInt()),
            valueRange = 0f..1f,
            defaultValue = effectId?.let { EffectCatalog.of(it)?.defaultIntensity } ?: 1f,
            step = EffectCatalog.INTENSITY_STEP,
            onValueChangeFinished = { onIntent(VideoEditorIntent.EffectIntensityReleased) },
        )
        Spacer(Modifier.height(MemixSpacing.space2))
        effectId?.let(::effectDescriptionOf)?.let { Text(stringResource(it), MemixTheme.type.label, color = MemixColors.textSecondary) }
    }
}

private const val PERCENT = 100f
