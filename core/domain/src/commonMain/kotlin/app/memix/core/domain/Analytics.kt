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
