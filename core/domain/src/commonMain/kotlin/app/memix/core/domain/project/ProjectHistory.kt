package app.memix.core.domain.project

import app.memix.core.model.project.Project

/**
 * Undo and redo for one editing session: the [current] project plus up to [MAX_UNDO_STEPS] earlier and
 * later versions. Immutable like [Project], so every change returns a new history.
 *
 * Versions share every part an edit didn't touch, so 100 steps cost roughly what the edits changed, not
 * 100 copies of the project (TECHNICAL_DESIGN.md → Decisions).
 */
internal class ProjectHistory private constructor(
    val current: Project,
    /** Oldest first; the last entry is where one undo goes back to. */
    private val undoSteps: List<Project>,
    /** Oldest first; the last entry is where one redo goes forward to. */
    private val redoSteps: List<Project>,
) {
    constructor(initial: Project) : this(initial, undoSteps = emptyList(), redoSteps = emptyList())

    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /**
     * Makes [next] the current project as one new undo step and clears redo. Past [MAX_UNDO_STEPS] the
     * oldest step is forgotten. A project equal to the current one adds no step and returns this history.
     */
    fun record(next: Project): ProjectHistory {
        if (next == current) return this
        return ProjectHistory(next, (undoSteps + current).takeLast(MAX_UNDO_STEPS), redoSteps = emptyList())
    }

    /** Goes back one step, or returns this history when there is nothing to undo. */
    fun undo(): ProjectHistory {
        val previous = undoSteps.lastOrNull() ?: return this
        return ProjectHistory(previous, undoSteps.dropLast(1), redoSteps + current)
    }

    /** Goes forward one undone step, or returns this history when there is nothing to redo. */
    fun redo(): ProjectHistory {
        val next = redoSteps.lastOrNull() ?: return this
        // Steps only move between the two lists here, so together they never pass MAX_UNDO_STEPS.
        return ProjectHistory(next, undoSteps + current, redoSteps.dropLast(1))
    }

    companion object {
        /** CLAUDE.md rule 6. */
        const val MAX_UNDO_STEPS = 100
    }
}
