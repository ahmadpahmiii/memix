package app.memix.core.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.RatioShape
import app.memix.core.designsystem.component.Swatch
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasCatalog
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.RatioTerms
import app.memix.core.model.project.size
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.canvas_custom_height
import memix.core.ui.generated.resources.canvas_custom_height_down
import memix.core.ui.generated.resources.canvas_custom_height_up
import memix.core.ui.generated.resources.canvas_custom_width
import memix.core.ui.generated.resources.canvas_custom_width_down
import memix.core.ui.generated.resources.canvas_custom_width_up
import memix.core.ui.generated.resources.canvas_ratio
import memix.core.ui.generated.resources.canvas_ratio_a11y
import memix.core.ui.generated.resources.canvas_ratio_custom
import memix.core.ui.generated.resources.canvas_shape_square
import memix.core.ui.generated.resources.canvas_shape_tall
import memix.core.ui.generated.resources.canvas_shape_wide
import memix.core.ui.generated.resources.color_black
import memix.core.ui.generated.resources.color_blue
import memix.core.ui.generated.resources.color_green
import memix.core.ui.generated.resources.color_light_blue
import memix.core.ui.generated.resources.color_orange
import memix.core.ui.generated.resources.color_pink
import memix.core.ui.generated.resources.color_purple
import memix.core.ui.generated.resources.color_red
import memix.core.ui.generated.resources.color_white
import memix.core.ui.generated.resources.color_yellow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The Canvas panel's rows both editors share (spec docs/ux/specs/P1-11-canvas.md → Canvas panel, P2-01 → Canvas panel): the
 * ratio tiles, a custom ratio's steppers and the caption palette as background swatches. What else the panel holds (Fit and
 * Fill and Blur for video, the hint line for photo) is each editor's own.
 */

/** One step of a custom ratio's Width or Height stepper. */
enum class RatioStep { WIDTH_DOWN, WIDTH_UP, HEIGHT_DOWN, HEIGHT_UP }

/** Which limit a custom ratio stepper ran into; the editor explains it ("The widest shape is 2.39 to 1."). */
enum class CanvasLimit { WIDEST, TALLEST, MIN, MAX }

/** The custom ratio after stepping, and the limit that stopped it, if one did. */
class SteppedTerms(val terms: RatioTerms, val limit: CanvasLimit?)

/** [terms] after up to [repeats] steps of [step]; the first step that would pass a limit stops it, and names the limit. */
fun steppedTerms(terms: RatioTerms, step: RatioStep, repeats: Int): SteppedTerms {
    var current = terms
    repeat(repeats) {
        val next = when (step) {
            RatioStep.WIDTH_DOWN -> current.copy(width = current.width - 1)
            RatioStep.WIDTH_UP -> current.copy(width = current.width + 1)
            RatioStep.HEIGHT_DOWN -> current.copy(height = current.height - 1)
            RatioStep.HEIGHT_UP -> current.copy(height = current.height + 1)
        }
        limitOf(next)?.let { return SteppedTerms(current, it) }
        current = next
    }
    return SteppedTerms(current, null)
}

private fun limitOf(terms: RatioTerms): CanvasLimit? = when {
    terms.width < CanvasCatalog.CUSTOM_TERMS.first || terms.height < CanvasCatalog.CUSTOM_TERMS.first -> CanvasLimit.MIN
    terms.width > CanvasCatalog.CUSTOM_TERMS.last || terms.height > CanvasCatalog.CUSTOM_TERMS.last -> CanvasLimit.MAX
    CanvasCatalog.allowsCustom(terms) -> null
    terms.width > terms.height -> CanvasLimit.WIDEST
    else -> CanvasLimit.TALLEST
}

/**
 * The steppers' numbers [terms] after the canvas changed (an edit, undo or redo): they go (null) when it's no longer custom
 * (spec P1-11 → Interactions); they show its terms when an undo or redo brought another custom ratio back. They keep the
 * numbers as stepped while the canvas has their ratio (10 and 16 for 5:8).
 */
fun customTermsAfterChange(canvas: Canvas, terms: RatioTerms): RatioTerms? = when {
    canvas.ratio == CanvasRatio.CUSTOM -> terms.takeIf { it.lowest() == RatioTerms.of(canvas.size()) } ?: RatioTerms.of(canvas.size())
    // Custom picked but not stepped yet: the canvas is still the preset the steppers were filled from.
    terms.lowest() == RatioTerms.of(canvas.size()) -> terms
    else -> null
}

/** The spoken names of the caption palette's ten colors, in palette order (P1-10); the background swatches use them too (P1-11). */
val PaletteColorNames: List<StringResource> = listOf(
    Res.string.color_white, Res.string.color_black, Res.string.color_yellow, Res.string.color_orange, Res.string.color_red,
    Res.string.color_pink, Res.string.color_purple, Res.string.color_blue, Res.string.color_light_blue, Res.string.color_green,
)

/** Six tiles: the presets drawn as their own shape, then Custom. While Custom is picked, no preset is selected. */
@Composable
fun CanvasRatioRow(ratio: CanvasRatio, customPicked: Boolean, onPick: (CanvasRatio) -> Unit) {
    ChoiceRow(stringResource(Res.string.canvas_ratio)) {
        CanvasCatalog.presets.forEach { (preset, size) ->
            val label = RatioTerms.of(size).toString()
            val aspect = size.widthPx.toFloat() / size.heightPx
            val selected = !customPicked && ratio == preset
            OptionTile(
                label,
                selected = selected,
                onClick = { onPick(preset) },
                layout = OptionTileLayout.STACKED,
                modifier = Modifier.keptInViewWhen(selected),
                spokenName = stringResource(Res.string.canvas_ratio_a11y, label, stringResource(shapeWordOf(aspect))),
            ) { tint -> RatioShape(aspect, tint) }
        }
        val customSelected = customPicked || ratio == CanvasRatio.CUSTOM
        OptionTile(
            stringResource(Res.string.canvas_ratio_custom),
            selected = customSelected,
            onClick = { onPick(CanvasRatio.CUSTOM) },
            layout = OptionTileLayout.STACKED,
            modifier = Modifier.keptInViewWhen(customSelected),
        ) { tint -> Icon(MemixIcons.RatioCustom, contentDescription = null, tint = tint) }
    }
}

/**
 * Scrolls the row so this tile shows whole when it becomes selected, or opens selected (Custom at the row's end at 200%
 * or in a long language; P1-11 review P1).
 */
@Composable
private fun Modifier.keptInViewWhen(selected: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val firstRun = remember { FirstRun() }
    LaunchedEffect(selected) {
        // The scroll ignores the request while the panel is still sliding in, so a tile selected as the panel opens
        // waits for the panel; a later selection waits one frame for the row's layout.
        if (firstRun.pending) awaitFrameTime(MemixMotion.durationSheet.toLong()) else withFrameMillis { }
        firstRun.pending = false
        if (selected) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
}

// Waits [millis] of frame time, the clock the panel's slide runs on.
private suspend fun awaitFrameTime(millis: Long) {
    val start = withFrameMillis { it }
    do {
        val now = withFrameMillis { it }
    } while (now - start < millis)
}

/** Whether an effect hasn't run yet in this composition; plain, as no drawing reads it. */
private class FirstRun(var pending: Boolean = true)

private fun shapeWordOf(aspect: Float): StringResource = when {
    aspect < 1f -> Res.string.canvas_shape_tall
    aspect > 1f -> Res.string.canvas_shape_wide
    else -> Res.string.canvas_shape_square
}

/**
 * Width and Height steppers, only while Custom is picked ([terms] not null); they fade in and out in 120 ms (spec →
 * Motion). A held stepper reports its steps so far to [onHeld]; the release reports them all to [onStepped].
 */
@Composable
fun CustomRatioRows(terms: RatioTerms?, onHeld: (RatioStep, repeats: Int) -> Unit, onStepped: (RatioStep, repeats: Int) -> Unit) {
    AnimatedVisibility(terms != null, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
        // Kept for the fade-out once the steppers are gone.
        val shown = terms ?: return@AnimatedVisibility
        Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
            TermStepper(
                Res.string.canvas_custom_width, shown.width, RatioStep.WIDTH_DOWN, RatioStep.WIDTH_UP,
                Res.string.canvas_custom_width_down, Res.string.canvas_custom_width_up, onHeld, onStepped,
            )
            TermStepper(
                Res.string.canvas_custom_height, shown.height, RatioStep.HEIGHT_DOWN, RatioStep.HEIGHT_UP,
                Res.string.canvas_custom_height_down, Res.string.canvas_custom_height_up, onHeld, onStepped,
            )
        }
    }
}

@Composable
private fun TermStepper(
    label: StringResource,
    value: Int,
    down: RatioStep,
    up: RatioStep,
    downLabel: StringResource,
    upLabel: StringResource,
    onHeld: (RatioStep, Int) -> Unit,
    onStepped: (RatioStep, Int) -> Unit,
) {
    StepperRow(stringResource(label), spokenState = value.toString()) {
        StepButton(MemixIcons.Minus, stringResource(downLabel), down, onHeld, onStepped)
        Readout(value.toString())
        StepButton(MemixIcons.Plus, stringResource(upLabel), up, onHeld, onStepped)
    }
}

@Composable
private fun StepButton(icon: DrawableResource, label: String, step: RatioStep, onHeld: (RatioStep, Int) -> Unit, onStepped: (RatioStep, Int) -> Unit) {
    RepeatingButton(icon, label, onHeld = { count -> onHeld(step, count) }, onStep = { count -> onStepped(step, count) })
}

/** The caption palette as background swatches, in palette order; [selectedArgb] is the one with the ring. */
@Composable
fun CanvasColorSwatches(selectedArgb: Long?, onPick: (argb: Long) -> Unit) {
    CaptionCatalog.palette.forEachIndexed { index, color ->
        Swatch(Color(color.argb.toInt()), stringResource(PaletteColorNames[index]), selected = selectedArgb == color.argb, onClick = { onPick(color.argb) })
    }
}
