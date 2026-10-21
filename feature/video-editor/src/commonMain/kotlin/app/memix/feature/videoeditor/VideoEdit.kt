package app.memix.feature.videoeditor

import app.memix.core.domain.EditorTool
import app.memix.core.model.project.Project

/**
 * The video editor's edits, each one undo step named by its [tool] (the `tool_use` id), turned back into words for
 * "Undo: Split" and "Delete: clip 2".
 */
enum class VideoEdit(val tool: EditorTool) {
    SPLIT(EditorTool.SPLIT),
    TRIM(EditorTool.TRIM),
    DELETE(EditorTool.DELETE),
    DUPLICATE(EditorTool.DUPLICATE),
    REORDER(EditorTool.REORDER),
    MOVE(EditorTool.MOVE),
    VOLUME(EditorTool.VOLUME),
    ADD_SOUND(EditorTool.ADD_SOUND),
    ADD_MEDIA(EditorTool.ADD_MEDIA),
    REPLACE(EditorTool.REPLACE_CLIP),
    MUTE_ORIGINAL(EditorTool.MUTE_ORIGINAL),
    UNMUTE_ORIGINAL(EditorTool.UNMUTE_ORIGINAL),
    DETACH_AUDIO(EditorTool.DETACH_AUDIO),
    ADD_TEXT(EditorTool.ADD_TEXT),
    EDIT_TEXT(EditorTool.EDIT_TEXT),
    TEXT_FONT(EditorTool.TEXT_FONT),
    TEXT_COLOR(EditorTool.TEXT_COLOR),
    TEXT_OUTLINE(EditorTool.TEXT_OUTLINE),
    POSITION_TEXT(EditorTool.POSITION_TEXT),
    TRANSFORM_TEXT(EditorTool.TRANSFORM_TEXT),
    ;

    companion object {
        /** The edit [tool] names, or null for tools that aren't edits of this editor (undo, redo). */
        fun of(tool: EditorTool?): VideoEdit? = entries.firstOrNull { it.tool == tool }
    }
}

/**
 * A test edit the composition root gives debug and benchmark builds, so undo, redo, their toasts and the save banner
 * can be checked by hand with a long-press on the timecode. Release builds pass none.
 */
fun interface EditorDebugEdit {
    fun apply(project: Project): Project
}
