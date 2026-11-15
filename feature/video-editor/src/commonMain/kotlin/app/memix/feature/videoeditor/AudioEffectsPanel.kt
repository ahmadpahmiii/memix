package app.memix.feature.videoeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.AudioEffects
import app.memix.core.model.project.AudioEffectsCatalog
import app.memix.core.ui.appLanguageCode
import kotlin.math.abs
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.a11y_fx_tab_on
import memix.feature.video_editor.generated.resources.fx_fade_in
import memix.feature.video_editor.generated.resources.fx_fade_out
import memix.feature.video_editor.generated.resources.fx_pitch
import memix.feature.video_editor.generated.resources.fx_reset
import memix.feature.video_editor.generated.resources.fx_reset_a11y
import memix.feature.video_editor.generated.resources.fx_seconds
import memix.feature.video_editor.generated.resources.fx_speed
import memix.feature.video_editor.generated.resources.fx_speed_value
import memix.feature.video_editor.generated.resources.fx_tab_fades
import memix.feature.video_editor.generated.resources.fx_tab_tone
import memix.feature.video_editor.generated.resources.fx_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Audio effects panel of a meme sound or an audio clip (spec docs/ux/specs/P3-09-audio-effects.md): Tone (pitch, speed) and
 * Fades, with Reset in the header and a dot on a tab that has anything set. It takes the place of the timeline and the tool bar
 * ([modifier] sizes it to them) and is shown while [panel] isn't null. The tabs stay under the title while the content scrolls,
 * unless they would take more than 40% of the panel at a large font. P3-09b adds the Voice, Bass and Echo tabs in their places.
 */
@Composable
internal fun AudioEffectsPanel(panel: AudioEffectsPanelUi?, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The panel's last values stay on screen while it slides away.
    val effects = panel?.effects ?: AudioEffects()
    val tab = panel?.tab ?: AudioEffectsTab.TONE
    val resetLabel = stringResource(Res.string.fx_reset)
    val resetSpoken = stringResource(Res.string.fx_reset_a11y)
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.fx_title),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseAudioEffects) },
        modifier = modifier,
        headerActions = {
            Button(
                resetLabel,
                { onIntent(VideoEditorIntent.ResetAudioEffects) },
                Modifier.semantics { contentDescription = resetSpoken },
                variant = ButtonVariant.Quiet,
            )
        },
        pinned = { AudioEffectsTabs(tab, effects, onIntent) },
    ) {
        when (tab) {
            AudioEffectsTab.TONE -> ToneTab(effects, onIntent)
            AudioEffectsTab.FADES -> FadesTab(effects, panel?.canFade ?: true, onIntent)
        }
    }
}

@Composable
private fun AudioEffectsTabs(selected: AudioEffectsTab, effects: AudioEffects, onIntent: (VideoEditorIntent) -> Unit) {
    val tone = stringResource(Res.string.fx_tab_tone)
    val fades = stringResource(Res.string.fx_tab_fades)
    val labels = remember(tone, fades) { listOf(tone, fades) }
    // The spoken name of a tab with a dot: its visible label first, then "on" (WCAG 2.5.3).
    // Kept here, so the tabs' scroll position survives the panel moving them between pinned and scrolling at a large font.
    val scroll = rememberScrollState()
    val spoken = labels.associateWith { stringResource(Res.string.a11y_fx_tab_on, it) }
    val marked = buildSet {
        if (effects.hasTone) add(AudioEffectsTab.TONE.ordinal)
        if (effects.hasFades) add(AudioEffectsTab.FADES.ordinal)
    }
    Tabs(
        labels,
        selected.ordinal,
        { onIntent(VideoEditorIntent.AudioEffectsTabSelected(AudioEffectsTab.entries[it])) },
        Modifier.fillMaxWidth(),
        markedTabs = marked,
        markedName = { spoken.getValue(it) },
        scroll = scroll,
    )
}

// Pitch: a bipolar slider from -12 to +12, one step per semitone, snapping to 0. Speed: 0.5x to 2x in 0.1 steps, snapping to 1x.
@Composable
private fun ToneTab(effects: AudioEffects, onIntent: (VideoEditorIntent) -> Unit) {
    val range = AudioEffectsCatalog.PITCH_RANGE_SEMITONES
    Slider(
        label = stringResource(Res.string.fx_pitch),
        value = effects.pitchSemitones.toFloat(),
        onValueChange = { onIntent(VideoEditorIntent.AudioEffectChanged(AudioEffectPart.PITCH, it)) },
        valueText = signedSemitones(effects.pitchSemitones),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        defaultValue = 0f,
        step = 1f,
        onValueChangeFinished = { onIntent(VideoEditorIntent.AudioEffectReleased(AudioEffectPart.PITCH)) },
        snapValue = 0f,
        snapDistance = SNAP_SEMITONES,
        origin = 0f,
    )
    val language = appLanguageCode()
    Slider(
        label = stringResource(Res.string.fx_speed),
        value = effects.speed,
        onValueChange = { onIntent(VideoEditorIntent.AudioEffectChanged(AudioEffectPart.SPEED, it)) },
        valueText = stringResource(Res.string.fx_speed_value, formatTenths((effects.speed * TENTHS).roundToInt(), language)),
        valueRange = AudioEffectsCatalog.MIN_SPEED..AudioEffectsCatalog.MAX_SPEED,
        defaultValue = 1f,
        step = AudioEffectsCatalog.SPEED_STEP,
        onValueChangeFinished = { onIntent(VideoEditorIntent.AudioEffectReleased(AudioEffectPart.SPEED)) },
        snapValue = 1f,
        snapDistance = SNAP_SPEED,
    )
}

// Fade in and out: 0 to 5 s in 0.1 s steps. A clip under 0.3 s is too short: both stay at 0 and a release explains.
@Composable
private fun FadesTab(effects: AudioEffects, canFade: Boolean, onIntent: (VideoEditorIntent) -> Unit) {
    val language = appLanguageCode()
    val maxSeconds = AudioEffectsCatalog.MAX_FADE_US / MICROS_PER_SECOND
    val stepSeconds = AudioEffectsCatalog.FADE_STEP_US / MICROS_PER_SECOND
    for ((part, label, fadeUs) in listOf(
        Triple(AudioEffectPart.FADE_IN, Res.string.fx_fade_in, effects.fadeInUs),
        Triple(AudioEffectPart.FADE_OUT, Res.string.fx_fade_out, effects.fadeOutUs),
    )) {
        Slider(
            label = stringResource(label),
            value = if (canFade) fadeUs / MICROS_PER_SECOND else 0f,
            onValueChange = { onIntent(VideoEditorIntent.AudioEffectChanged(part, it)) },
            valueText = stringResource(Res.string.fx_seconds, formatTenths((fadeUs / TENTH_US).toInt(), language)),
            valueRange = 0f..maxSeconds,
            defaultValue = 0f,
            step = stepSeconds,
            onValueChangeFinished = { onIntent(VideoEditorIntent.AudioEffectReleased(part)) },
        )
    }
}

/** "+3", "−5", "0": the readout of the Pitch slider, with a real minus sign. */
internal fun signedSemitones(semitones: Int): String = when {
    semitones > 0 -> "+$semitones"
    semitones < 0 -> "−${abs(semitones)}"
    else -> "0"
}

/** [tenths] (15 is 1.5) with the decimal mark of [languageCode]: a comma in Indonesian, Spanish and Portuguese, a point in English and Hindi. */
internal fun formatTenths(tenths: Int, languageCode: String): String {
    val mark = if (languageCode in COMMA_LANGUAGES) ',' else '.'
    return "${tenths / TENTHS_PER_UNIT}$mark${tenths % TENTHS_PER_UNIT}"
}

private val COMMA_LANGUAGES = setOf("id", "es", "pt")
private const val TENTHS_PER_UNIT = 10
private const val TENTHS = 10f
private const val TENTH_US = 100_000L
private const val MICROS_PER_SECOND = 1_000_000f

/** Pitch snaps to 0 from half a semitone, so every step but 0 stays reachable. */
private const val SNAP_SEMITONES = 0.5f

/** Speed snaps to 1x from within 0.05. */
private const val SNAP_SPEED = 0.05f
