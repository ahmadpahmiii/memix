package app.memix.core.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.IconButton
import app.memix.core.model.project.MOVE_STEP
import app.memix.core.model.project.SizeLadder
import app.memix.core.model.project.Transform
import app.memix.core.model.project.nextRotation
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.a11y_degrees
import memix.core.ui.generated.resources.position_bigger
import memix.core.ui.generated.resources.position_center
import memix.core.ui.generated.resources.position_move
import memix.core.ui.generated.resources.position_move_down
import memix.core.ui.generated.resources.position_move_left
import memix.core.ui.generated.resources.position_move_right
import memix.core.ui.generated.resources.position_move_up
import memix.core.ui.generated.resources.position_rotate_left
import memix.core.ui.generated.resources.position_rotate_right
import memix.core.ui.generated.resources.position_rotation
import memix.core.ui.generated.resources.position_rotation_value
import memix.core.ui.generated.resources.position_size
import memix.core.ui.generated.resources.position_size_value
import memix.core.ui.generated.resources.position_smaller
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The Position panel's rows, for captions and layers alike (spec P1-10 → Position panel, P2-01 → Position panel): the
 * single-pointer, no-drag route for moving, sizing and turning (WCAG 2.5.1 and 2.5.7), and for exact nudges.
 */

/** One tap in the Position panel. */
enum class PositionStep { SMALLER, BIGGER, ROTATE_LEFT, ROTATE_RIGHT, MOVE_LEFT, MOVE_UP, MOVE_DOWN, MOVE_RIGHT, CENTER }

/** Why a [PositionStep] couldn't apply; the editor explains it ("This is the smallest size."). */
enum class PositionLimit { SMALLEST, BIGGEST, EDGE }

/** Whether [this] step moves the center (a place) rather than sizing or turning (spec P1-10 → Analytics). */
val PositionStep.changesPlace: Boolean
    get() = this != PositionStep.SMALLER && this != PositionStep.BIGGER && this != PositionStep.ROTATE_LEFT && this != PositionStep.ROTATE_RIGHT

/** The limit a first tap of [this] step runs into. */
val PositionStep.limit: PositionLimit
    get() = when (this) {
        PositionStep.SMALLER -> PositionLimit.SMALLEST
        PositionStep.BIGGER -> PositionLimit.BIGGEST
        PositionStep.ROTATE_LEFT, PositionStep.ROTATE_RIGHT, PositionStep.MOVE_LEFT, PositionStep.MOVE_UP,
        PositionStep.MOVE_DOWN, PositionStep.MOVE_RIGHT, PositionStep.CENTER -> PositionLimit.EDGE
    }

/**
 * [this] transform after [repeats] taps of [step], sizes taken from [ladder]; null when the first tap already hits a
 * limit. A hold that reaches a limit midway stops there.
 */
fun Transform.steppedBy(step: PositionStep, repeats: Int, ladder: SizeLadder): Transform? {
    var current = this
    repeat(repeats) { index ->
        val next = current.steppedOnce(step, ladder)
        if (next == null) return if (index == 0) null else current
        current = next
    }
    return current
}

private fun Transform.steppedOnce(step: PositionStep, ladder: SizeLadder): Transform? = when (step) {
    PositionStep.SMALLER -> ladder.below(scale)?.let { copy(scale = it) }
    PositionStep.BIGGER -> ladder.above(scale)?.let { copy(scale = it) }
    PositionStep.ROTATE_LEFT -> copy(rotationDegrees = nextRotation(rotationDegrees, clockwise = false))
    PositionStep.ROTATE_RIGHT -> copy(rotationDegrees = nextRotation(rotationDegrees, clockwise = true))
    PositionStep.MOVE_LEFT -> movedBy(-MOVE_STEP, 0f)
    PositionStep.MOVE_RIGHT -> movedBy(MOVE_STEP, 0f)
    PositionStep.MOVE_UP -> movedBy(0f, -MOVE_STEP)
    PositionStep.MOVE_DOWN -> movedBy(0f, MOVE_STEP)
    PositionStep.CENTER -> copy(centerX = CENTER, centerY = CENTER)
}

// Null when the center is already at that edge: the layer can't go further.
private fun Transform.movedBy(dx: Float, dy: Float): Transform? {
    val moved = copy(centerX = (centerX + dx).coerceIn(0f, 1f), centerY = (centerY + dy).coerceIn(0f, 1f))
    return if (moved == this) null else moved
}

/**
 * Size, Rotation, Move and Center for a layer at [transform] (the panel's content, inside a ToolPanel). A tap on
 * Size, Rotation or Center is [onStep] with one repeat; a Move arrow held repeats and reports the steps so far to
 * [onHeld], and its release reports them all to [onStep] (one commit).
 */
@Composable
fun PositionRows(transform: Transform, onStep: (PositionStep, repeats: Int) -> Unit, onHeld: (PositionStep, repeats: Int) -> Unit) {
    val percent = (transform.scale * PERCENT).roundToInt()
    val degrees = transform.rotationDegrees.roundToInt()
    StepperRow(stringResource(Res.string.position_size), spokenState = stringResource(Res.string.position_size_value, percent)) {
        IconButton(MemixIcons.Minus, stringResource(Res.string.position_smaller), { onStep(PositionStep.SMALLER, 1) })
        Readout(stringResource(Res.string.position_size_value, percent))
        IconButton(MemixIcons.Plus, stringResource(Res.string.position_bigger), { onStep(PositionStep.BIGGER, 1) })
    }
    StepperRow(stringResource(Res.string.position_rotation), spokenState = pluralStringResource(Res.plurals.a11y_degrees, degrees, degrees)) {
        IconButton(MemixIcons.RotateLeft, stringResource(Res.string.position_rotate_left), { onStep(PositionStep.ROTATE_LEFT, 1) })
        Readout(stringResource(Res.string.position_rotation_value, degrees))
        IconButton(MemixIcons.RotateRight, stringResource(Res.string.position_rotate_right), { onStep(PositionStep.ROTATE_RIGHT, 1) })
    }
    StepperRow(stringResource(Res.string.position_move), spokenState = null) {
        // One drawing turned four ways (spec → Icons).
        MoveButton(PositionStep.MOVE_LEFT, Res.string.position_move_left, ARROW_LEFT, onStep, onHeld)
        MoveButton(PositionStep.MOVE_UP, Res.string.position_move_up, ARROW_UP, onStep, onHeld)
        MoveButton(PositionStep.MOVE_DOWN, Res.string.position_move_down, ARROW_DOWN, onStep, onHeld)
        MoveButton(PositionStep.MOVE_RIGHT, Res.string.position_move_right, ARROW_RIGHT, onStep, onHeld)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(stringResource(Res.string.position_center), { onStep(PositionStep.CENTER, 1) })
    }
}

// A Move arrow: one drawing turned four ways (spec → Icons); held, it repeats and commits once on release.
@Composable
private fun MoveButton(
    step: PositionStep,
    label: StringResource,
    turnDegrees: Float,
    onStep: (PositionStep, repeats: Int) -> Unit,
    onHeld: (PositionStep, repeats: Int) -> Unit,
) {
    RepeatingButton(
        MemixIcons.Arrow,
        stringResource(label),
        onHeld = { count -> onHeld(step, count) },
        onStep = { count -> onStep(step, count) },
        turnDegrees = turnDegrees,
    )
}

private const val PERCENT = 100f
private const val CENTER = 0.5f
private const val ARROW_RIGHT = 0f
private const val ARROW_DOWN = 90f
private const val ARROW_LEFT = 180f
private const val ARROW_UP = 270f
