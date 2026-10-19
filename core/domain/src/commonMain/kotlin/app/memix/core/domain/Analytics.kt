package app.memix.core.domain

import app.memix.core.model.project.ProjectType

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
     * one with a [EditorTool.eventValue].
     */
    data class ToolUse(val editor: ProjectType, val tool: EditorTool) : AnalyticsEvent {
        override val name = "tool_use"
        override val params = mapOf("editor" to editor.eventValue, "tool" to requireNotNull(tool.eventValue))
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
}

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
