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
    FADE_IN(EditorTool.AUDIO_FADE_IN),
    FADE_OUT(EditorTool.AUDIO_FADE_OUT),
    PITCH(EditorTool.AUDIO_PITCH),
    SPEED(EditorTool.AUDIO_SPEED),
    BASS(EditorTool.AUDIO_BASS),
    DISTORTION(EditorTool.AUDIO_DISTORTION),
    ECHO(EditorTool.AUDIO_ECHO),
    ECHO_LENGTH(EditorTool.AUDIO_ECHO_LENGTH),
    VOICE(EditorTool.AUDIO_VOICE),
    RESET_EFFECTS(EditorTool.AUDIO_RESET),
    ADD_SOUND(EditorTool.ADD_SOUND),
    ADD_MEDIA(EditorTool.ADD_MEDIA),
    REPLACE(EditorTool.REPLACE_CLIP),
    MUTE_ORIGINAL(EditorTool.MUTE_ORIGINAL),
    UNMUTE_ORIGINAL(EditorTool.UNMUTE_ORIGINAL),
    DETACH_AUDIO(EditorTool.DETACH_AUDIO),
    VOICEOVER(EditorTool.VOICEOVER),
    ADD_TEXT(EditorTool.ADD_TEXT),
    EDIT_TEXT(EditorTool.EDIT_TEXT),
    TEXT_FONT(EditorTool.TEXT_FONT),
    TEXT_COLOR(EditorTool.TEXT_COLOR),
    TEXT_OUTLINE(EditorTool.TEXT_OUTLINE),
    TEXT_SHADOW(EditorTool.TEXT_SHADOW),
    TEXT_BOX(EditorTool.TEXT_BOX),
    POSITION_TEXT(EditorTool.POSITION_TEXT),
    TRANSFORM_TEXT(EditorTool.TRANSFORM_TEXT),
    ADD_OVERLAY(EditorTool.ADD_OVERLAY),
    TRANSFORM_OVERLAY(EditorTool.TRANSFORM_OVERLAY),
    POSITION_OVERLAY(EditorTool.POSITION_OVERLAY),
    OVERLAY_OPACITY(EditorTool.OVERLAY_OPACITY),
    OVERLAY_ORDER(EditorTool.OVERLAY_ORDER),
    GREEN_SCREEN(EditorTool.GREEN_SCREEN),
    GREEN_SCREEN_OFF(EditorTool.GREEN_SCREEN_OFF),
    MASK(EditorTool.MASK),
    MASK_ADJUST(EditorTool.MASK_ADJUST),
    MASK_POSITION(EditorTool.MASK_POSITION),
    FILTER(EditorTool.FILTER),
    FILTER_ALL(EditorTool.FILTER_ALL),
    ADJUST_BRIGHTNESS(EditorTool.ADJUST_BRIGHTNESS),
    ADJUST_CONTRAST(EditorTool.ADJUST_CONTRAST),
    ADJUST_SATURATION(EditorTool.ADJUST_SATURATION),
    ADJUST_WARMTH(EditorTool.ADJUST_WARMTH),
    ADJUST_SHARPEN(EditorTool.ADJUST_SHARPEN),
    ADJUST_VIGNETTE(EditorTool.ADJUST_VIGNETTE),
    RESET_LOOK(EditorTool.EDIT_PHOTO_RESET),
    EFFECT_ADD(EditorTool.EFFECT_ADD),
    EFFECT_CHANGE(EditorTool.EFFECT_CHANGE),
    EFFECT_INTENSITY(EditorTool.EFFECT_INTENSITY),
    EFFECT_MOVE(EditorTool.EFFECT_MOVE),
    EFFECT_DELETE(EditorTool.EFFECT_DELETE),
    KEYFRAME_ADD(EditorTool.KEYFRAME_ADD),
    KEYFRAME_REMOVE(EditorTool.KEYFRAME_REMOVE),
    KEYFRAME_MOVE(EditorTool.KEYFRAME_MOVE),
    KEYFRAME_EASING(EditorTool.KEYFRAME_EASING),
    KEYFRAME_CLEAR(EditorTool.KEYFRAME_CLEAR),
    CANVAS_RATIO(EditorTool.CANVAS_RATIO),
    CANVAS_FIT(EditorTool.CANVAS_FIT),
    CANVAS_FILL(EditorTool.CANVAS_FILL),
    CANVAS_BACKGROUND(EditorTool.CANVAS_BACKGROUND),
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
