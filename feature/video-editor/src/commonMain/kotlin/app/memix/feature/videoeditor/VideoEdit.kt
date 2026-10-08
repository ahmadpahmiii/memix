package app.memix.feature.videoeditor

import app.memix.core.model.project.Project

/**
 * The video editor's edits, each one undo step. [id] is the name the edit session keeps for the step (and the
 * `tool_use` id), turned back into words for "Undo: Trim". P1-06 adds split, delete, duplicate, reorder and the rest.
 */
enum class VideoEdit(val id: String) {
    TRIM("trim"),
    ;

    companion object {
        fun fromId(id: String?): VideoEdit? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A test edit the composition root gives debug and benchmark builds, so undo, redo, their toasts and the save banner
 * can be checked by hand before P1-06 brings real edits. Release builds pass none.
 */
fun interface EditorDebugEdit {
    fun apply(project: Project): Project
}
