package app.memix.core.domain.project

import app.memix.core.model.project.Project

/**
 * Undo and redo for one editing session: the [current] project plus up to [MAX_UNDO_STEPS] earlier and
 * later versions, each with the name of the edit that made it. Immutable like [Project], so every change
 * returns a new history.
 *
 * Versions share every part an edit didn't touch, so 100 steps cost roughly what the edits changed, not
 * 100 copies of the project (TECHNICAL_DESIGN.md → Decisions).
 */
internal class ProjectHistory private constructor(
    private val now: Version,
    /** Oldest first; the last entry is where one undo goes back to. */
    private val undoSteps: List<Version>,
    /** Oldest first; the last entry is where one redo goes forward to. */
    private val redoSteps: List<Version>,
) {
    constructor(initial: Project) : this(Version(initial, editName = null), undoSteps = emptyList(), redoSteps = emptyList())

    val current: Project get() = now.project
    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /** The edit one undo takes back: the one that made [current]. Null when there is nothing to undo. */
    val undoEditName: String? get() = if (canUndo) now.editName else null

    /** The edit one redo puts back. Null when there is nothing to redo. */
    val redoEditName: String? get() = redoSteps.lastOrNull()?.editName

    /**
     * Makes [next] the current project as one new undo step named [editName] and clears redo. Past
     * [MAX_UNDO_STEPS] the oldest step is forgotten. A project equal to the current one adds no step and
     * returns this history.
     */
    fun record(editName: String, next: Project): ProjectHistory {
        if (next == current) return this
        return ProjectHistory(Version(next, editName), (undoSteps + now).takeLast(MAX_UNDO_STEPS), redoSteps = emptyList())
    }

    /** Goes back one step, or returns this history when there is nothing to undo. */
    fun undo(): ProjectHistory {
        val previous = undoSteps.lastOrNull() ?: return this
        return ProjectHistory(previous, undoSteps.dropLast(1), redoSteps + now)
    }

    /** Goes forward one undone step, or returns this history when there is nothing to redo. */
    fun redo(): ProjectHistory {
        val next = redoSteps.lastOrNull() ?: return this
        // Steps only move between the two lists here, so together they never pass MAX_UNDO_STEPS.
        return ProjectHistory(next, undoSteps + now, redoSteps.dropLast(1))
    }

    /** One version of the project, and the edit that produced it (null for the version the session opened with). */
    private class Version(val project: Project, val editName: String?)

    companion object {
        /** CLAUDE.md rule 6. */
        const val MAX_UNDO_STEPS = 100
    }
}
