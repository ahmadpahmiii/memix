package app.memix.core.domain

import app.memix.core.model.project.ProjectType
import kotlin.math.roundToLong

/**
 * Product analytics (Firebase Analytics on Android). Implemented in :platform:services.
 *
 * Events carry no personal data: no file names, URIs, text the user typed or ids of their media. Collection
 * follows the user's consent choice, which the platform side enforces (off until P5-01), so callers log every
 * event and never check consent themselves. Safe to call from any thread; it never blocks.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)
}

/** The events and parameters listed in PRD → Metrics and analytics; names and values are the PRD's, exactly. */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, String>

    /** A project starts: logged when a new project is first saved with something in it. */
    data class ProjectCreate(val editor: ProjectType, val source: ProjectSource) : AnalyticsEvent {
        override val name = "project_create"
        override val params = mapOf("editor" to editor.eventValue, "source" to source.eventValue)
    }

    /**
     * A tool changed the project: one event per committed step, never during a drag (PM, 8 Oct 2026). [tool] must be
     * one with a [EditorTool.eventValue]. [font] is the caption font id, sent only with `add_text` and `text_font`
     * (owner, 9 Oct 2026: PRD → Analytics). [value] is the choice a canvas tool made, sent only with `canvas_ratio`
     * (`9:16` … `16:9` or `custom`, never the custom numbers), `canvas_fit` (`fit`, `fill`) and `canvas_background`
     * (`blur`, `color`, never the color itself) (owner, 21 Oct 2026). The photo editor sends `editor: photo` (P2-01).
     */
    data class ToolUse(val editor: ProjectType, val tool: EditorTool, val font: String? = null, val value: String? = null) : AnalyticsEvent {
        override val name = "tool_use"
        override val params = buildMap {
            put("editor", editor.eventValue)
            put("tool", requireNotNull(tool.eventValue))
            if (font != null) put("font", font)
            if (value != null) put("value", value)
        }
    }

    /** A meme sound landed on the timeline from the sounds sheet; not for duplicates (spec P1-06 → Analytics). */
    data class SoundAdd(val soundId: String, val tab: SoundTab, val editor: ProjectType) : AnalyticsEvent {
        override val name = "sound_add"
        override val params = mapOf("sound_id" to soundId, "tab" to tab.eventValue, "editor" to editor.eventValue)
    }

    /** A sound started playing in the sounds sheet. */
    data class SoundPreview(val soundId: String, val tab: SoundTab) : AnalyticsEvent {
        override val name = "sound_preview"
        override val params = mapOf("sound_id" to soundId, "tab" to tab.eventValue)
    }

    /** Export video was tapped and the export started (PM, 20 Oct 2026). [resolution] as named ("1080p"). */
    data class ExportStart(val editor: ProjectType, val resolution: String, val fps: Int, val lengthSeconds: Long) : AnalyticsEvent {
        override val name = "export_start"
        override val params = mapOf(
            "editor" to editor.eventValue,
            "format" to MP4,
            "resolution" to resolution,
            "fps" to fps.toString(),
            "length_s" to lengthSeconds.toString(),
        )
    }

    /** The file is in the gallery: [timeMs] from encode start to saved, [sizeMb] its size. */
    data class ExportComplete(val editor: ProjectType, val timeMs: Long, val sizeMb: Double) : AnalyticsEvent {
        override val name = "export_complete"
        override val params = mapOf(
            "editor" to editor.eventValue,
            "format" to MP4,
            "time_ms" to timeMs.toString(),
            // One decimal, as the sheet shows sizes.
            "size_mb" to ((sizeMb * TENTHS).roundToLong() / TENTHS).toString(),
        )
    }

    /** The export failed; [errorCode] is one of `ExportError`'s values (spec P1-12 → Analytics). */
    data class ExportFail(val editor: ProjectType, val errorCode: String) : AnalyticsEvent {
        override val name = "export_fail"
        override val params = mapOf("editor" to editor.eventValue, "error_code" to errorCode)
    }

    /** The user stopped an export at [progress] whole percent (owner, 21 Oct 2026). */
    data class ExportCancel(val editor: ProjectType, val progress: Int) : AnalyticsEvent {
        override val name = "export_cancel"
        override val params = mapOf("editor" to editor.eventValue, "progress" to progress.toString())
    }

    /** A share target was opened; P1-12's one Share button is `more` (owner, 21 Oct 2026; P5-05 adds the others). */
    data class ShareTarget(val target: String) : AnalyticsEvent {
        override val name = "share_target"
        override val params = mapOf("target" to target)
    }
}

private const val MP4 = "mp4"
private const val TENTHS = 10.0

/**
 * `tool_use.tool`: lowercase ids, the same id for the same action in both editors (PM acceptance log, 8 Oct
 * 2026). Each editor ticket adds the tools it builds (P1-08: move, P1-09: volume, mute_original, ...).
 */
enum class EditorTool(val eventValue: String?) {
    UNDO("undo"),
    REDO("redo"),
    SPLIT("split"),
    TRIM("trim"),
    DELETE("delete"),
    DUPLICATE("duplicate"),
    REORDER("reorder"),
    MOVE("move"),
    VOLUME("volume"),

    /** An undo step with no `tool_use`: a landed meme sound is counted by `sound_add` instead (spec P1-06 → Analytics). */
    ADD_SOUND(null),

    /** One pick added from the editor, however many items went in (P1-16). */
    ADD_MEDIA("add_media"),
    REPLACE_CLIP("replace_clip"),

    /** P1-09: one id per direction of the original-audio toggle (PM, 8 Oct), and Detach audio. */
    MUTE_ORIGINAL("mute_original"),
    UNMUTE_ORIGINAL("unmute_original"),
    DETACH_AUDIO("detach_audio"),

    /** P1-10: captions (PM, 9 Oct; `move_text` became `position_text`). `add_text` and `text_font` also send `font`. */
    ADD_TEXT("add_text"),
    EDIT_TEXT("edit_text"),
    TEXT_FONT("text_font"),
    TEXT_COLOR("text_color"),
    TEXT_OUTLINE("text_outline"),
    POSITION_TEXT("position_text"),
    TRANSFORM_TEXT("transform_text"),

    /**
     * P1-11: the Canvas panel (PM, 20 Oct), each with a `value`. Fit and Fill are one event, `canvas_fit` with `value`
     * `fit` or `fill`, but two entries, so the undo history can say "Undo: Fill".
     */
    CANVAS_RATIO("canvas_ratio"),
    CANVAS_FIT("canvas_fit"),
    CANVAS_FILL("canvas_fit"),
    CANVAS_BACKGROUND("canvas_background"),

    /**
     * P2-01: the photo editor's layer tools (PM, 27 Oct 2026). `transform_layer` is a gesture's release, `position_layer`
     * one Position-panel step, as video has `transform_text` and `position_text`. `move` and `reorder` already mean time
     * order in the video editor, so layers have their own ids; delete, duplicate, undo, redo and the canvas tools are shared.
     */
    TRANSFORM_LAYER("transform_layer"),
    POSITION_LAYER("position_layer"),
    LAYER_OPACITY("layer_opacity"),
    LOCK_LAYER("lock_layer"),
    UNLOCK_LAYER("unlock_layer"),
    HIDE_LAYER("hide_layer"),
    SHOW_LAYER("show_layer"),
    REORDER_LAYER("reorder_layer"),

    /** P2-13: a photo landed as a new top layer, once per pick. */
    ADD_PHOTO("add_photo"),
}

/** `sound_add.tab` and `sound_preview.tab`: where in the sounds UI it happened. "starter" until P3-05 (PM, 8 Oct 2026). */
enum class SoundTab(val eventValue: String) {
    STARTER("starter"),
}

/** Where a new project came from (`project_create.source`). */
enum class ProjectSource(val eventValue: String) {
    /** The photo editor's blank layout. */
    BLANK("blank"),
    TEMPLATE("template"),

    /** Media picked from the gallery. */
    GALLERY("gallery"),
}

private val ProjectType.eventValue: String
    get() = when (this) {
        ProjectType.VIDEO -> "video"
        ProjectType.PHOTO -> "photo"
    }
