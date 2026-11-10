package app.memix.core.ui.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Swatch
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.BoxCorners
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.ShadowSoftness
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.PaletteColorNames
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.box_none
import memix.core.ui.generated.resources.box_pill
import memix.core.ui.generated.resources.box_rounded
import memix.core.ui.generated.resources.box_square
import memix.core.ui.generated.resources.shadow_blurry
import memix.core.ui.generated.resources.shadow_none
import memix.core.ui.generated.resources.shadow_sharp
import memix.core.ui.generated.resources.shadow_soft
import memix.core.ui.generated.resources.style_box
import memix.core.ui.generated.resources.style_box_corners
import memix.core.ui.generated.resources.style_shadow
import memix.core.ui.generated.resources.style_shadow_softness
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The Style panel's Shadow and Box rows (spec docs/ux/specs/P2-11-text-shadow-and-box.md → Layout): a Swatch row with "none"
 * first, like Outline, and under it, only while the effect is on, a three-step SegmentedChoice. A color tap turns the effect on
 * in that color (keeping the step if it was already on, else the default step); "none" turns it off, and turning it on again
 * starts from the defaults. Every tap is one undo step; a tap that changes nothing records nothing (the editors compare styles).
 */

@Composable
internal fun ShadowRows(shadow: CaptionShadow?, onShadow: (CaptionShadow?) -> Unit) {
    Column {
        ChoiceRow(stringResource(Res.string.style_shadow)) {
            EffectSwatches(Res.string.shadow_none, shadow?.color) { color -> onShadow(color?.let { CaptionShadow(it, shadow?.softness ?: ShadowSoftness.SOFT) }) }
        }
        StepRow(
            shadow != null,
            stringResource(Res.string.style_shadow_softness),
            listOf(Res.string.shadow_sharp, Res.string.shadow_soft, Res.string.shadow_blurry).map { Segment(stringResource(it)) },
            shadow?.softness?.ordinal,
        ) { step -> shadow?.let { onShadow(it.copy(softness = ShadowSoftness.entries[step])) } }
    }
}

@Composable
internal fun BoxRows(box: CaptionBox?, onBox: (CaptionBox?) -> Unit) {
    Column {
        ChoiceRow(stringResource(Res.string.style_box)) {
            EffectSwatches(Res.string.box_none, box?.color) { color -> onBox(color?.let { CaptionBox(it, box?.corners ?: BoxCorners.ROUNDED) }) }
        }
        StepRow(
            box != null,
            stringResource(Res.string.style_box_corners),
            listOf(Res.string.box_square, Res.string.box_rounded, Res.string.box_pill).map { Segment(stringResource(it)) },
            box?.corners?.ordinal,
        ) { step -> box?.let { onBox(it.copy(corners = BoxCorners.entries[step])) } }
    }
}

// "None" first, then the ten palette colors; [onPick] gets the color or null for "none".
@Composable
private fun EffectSwatches(noneName: StringResource, selected: ArgbColor?, onPick: (ArgbColor?) -> Unit) {
    Swatch(null, stringResource(noneName), selected = selected == null, onClick = { onPick(null) })
    CaptionCatalog.palette.forEachIndexed { index, color ->
        Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = selected == color, onClick = { onPick(color) })
    }
}

// Fades in and out in 120 ms while its effect is on; the rows below move at once (spec → Interactions). The last step shown is
// kept for the fade-out.
@Composable
private fun StepRow(visible: Boolean, heading: String, segments: List<Segment>, selectedStep: Int?, onStep: (Int) -> Unit) {
    // The step stays selected while the row fades out (the effect is already off by then): the holder keeps the last step set, updated only when the step changes.
    val holder = remember { StepHolder() }
    val shownStep = remember(selectedStep) { selectedStep?.also { holder.step = it } ?: holder.step }
    AnimatedVisibility(visible, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
        Column(Modifier.padding(top = MemixSpacing.space4), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Text(heading, MemixTheme.type.label, Modifier.semantics { heading() }, color = MemixColors.textSecondary)
            SegmentedChoice(segments, shownStep, onStep, heading)
        }
    }
}

private class StepHolder(var step: Int = 0)
