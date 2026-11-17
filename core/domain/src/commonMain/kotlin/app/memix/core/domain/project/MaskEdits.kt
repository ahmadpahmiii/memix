package app.memix.core.domain.project

import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MaskCatalog
import app.memix.core.model.project.MaskShape
import app.memix.core.model.project.Project
import app.memix.core.model.project.nextRotation

/**
 * Shape mask edits (P4-03b). Pure; the editor records each returned project as one undo step. Masks apply to main clips and overlays,
 * video and photo alike, the same clips green screen keys ([keyableClip]); everything else has no picture to mask.
 */

/** The mask of clip [itemId], or null when it has none or isn't a maskable clip. */
fun Project.maskOf(itemId: String): ClipMask? = keyableClip(itemId)?.mask

/**
 * Sets clip [itemId]'s mask to [mask] (null removes it), every number held to its range. Returns this project unchanged when nothing
 * changes, so no undo step is recorded.
 */
fun Project.setClipMask(itemId: String, mask: ClipMask?): Project {
    val clip = keyableClip(itemId) ?: return this
    val clean = mask?.let(MaskCatalog::clean)
    if (clip.mask == clean) return this
    val (track, _) = findItem(itemId) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) clip.copy(mask = clean) else it }))
}

/**
 * Picks [shape] for clip [itemId] (null is None): the new shape starts at its defaults in the middle of the box, keeping the old mask's
 * softness and Invert (spec → Interactions: replacing a mask). The same shape again changes nothing.
 */
fun Project.setMaskShape(itemId: String, shape: MaskShape?): Project {
    val old = maskOf(itemId)
    if (shape == null) return setClipMask(itemId, null)
    if (old?.shape == shape) return this
    return setClipMask(itemId, MaskCatalog.starting(shape, old?.softness ?: 0f, old?.invert ?: false))
}

/** One tap in the Position tab (spec → Position tab). */
enum class MaskStep { NARROWER, WIDER, SHORTER, TALLER, ROTATE_LEFT, ROTATE_RIGHT, MOVE_LEFT, MOVE_UP, MOVE_DOWN, MOVE_RIGHT, CENTER }

/** Why a [MaskStep] couldn't apply; the editor explains it ("This is the smallest the mask gets."). */
enum class MaskLimit { SMALLEST, BIGGEST, EDGE }

/** The limit a first tap of [this] step runs into; null for a step that never stops (a turn, Center). */
val MaskStep.limit: MaskLimit?
    get() = when (this) {
        MaskStep.NARROWER, MaskStep.SHORTER -> MaskLimit.SMALLEST
        MaskStep.WIDER, MaskStep.TALLER -> MaskLimit.BIGGEST
        MaskStep.MOVE_LEFT, MaskStep.MOVE_UP, MaskStep.MOVE_DOWN, MaskStep.MOVE_RIGHT -> MaskLimit.EDGE
        MaskStep.ROTATE_LEFT, MaskStep.ROTATE_RIGHT, MaskStep.CENTER -> null
    }

/**
 * [this] after [repeats] taps of [step] (a hold repeats); null when the first tap changes nothing (a limit, or already centered). A hold
 * that reaches a limit midway stops there. Sizes and moves are [MaskCatalog.STEP] of the clip's box, a turn the Position panel's step.
 */
fun ClipMask.steppedBy(step: MaskStep, repeats: Int): ClipMask? {
    var current = this
    repeat(repeats) { index ->
        val next = current.steppedOnce(step)
        if (next == current) return if (index == 0) null else current
        current = next
    }
    return current
}

private fun ClipMask.steppedOnce(step: MaskStep): ClipMask = when (step) {
    MaskStep.NARROWER -> if (MaskCatalog.hasWidth(shape)) MaskCatalog.clean(copy(width = width - MaskCatalog.STEP)) else this
    MaskStep.WIDER -> if (MaskCatalog.hasWidth(shape)) MaskCatalog.clean(copy(width = width + MaskCatalog.STEP)) else this
    MaskStep.SHORTER -> if (MaskCatalog.hasHeight(shape)) MaskCatalog.clean(copy(height = height - MaskCatalog.STEP)) else this
    MaskStep.TALLER -> if (MaskCatalog.hasHeight(shape)) MaskCatalog.clean(copy(height = height + MaskCatalog.STEP)) else this
    MaskStep.ROTATE_LEFT -> MaskCatalog.clean(copy(rotationDegrees = nextRotation(rotationDegrees, clockwise = false)))
    MaskStep.ROTATE_RIGHT -> MaskCatalog.clean(copy(rotationDegrees = nextRotation(rotationDegrees, clockwise = true)))
    MaskStep.MOVE_LEFT -> MaskCatalog.clean(copy(centerX = centerX - MaskCatalog.STEP))
    MaskStep.MOVE_RIGHT -> MaskCatalog.clean(copy(centerX = centerX + MaskCatalog.STEP))
    MaskStep.MOVE_UP -> MaskCatalog.clean(copy(centerY = centerY - MaskCatalog.STEP))
    MaskStep.MOVE_DOWN -> MaskCatalog.clean(copy(centerY = centerY + MaskCatalog.STEP))
    MaskStep.CENTER -> copy(centerX = MaskCatalog.CENTER, centerY = MaskCatalog.CENTER)
}

/** The mask [itemId] has after [change], or this project when it has none or [change] changes nothing. */
fun Project.adjustMask(itemId: String, change: (ClipMask) -> ClipMask): Project {
    val mask = maskOf(itemId) ?: return this
    return setClipMask(itemId, change(mask))
}
