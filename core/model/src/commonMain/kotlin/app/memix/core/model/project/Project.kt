package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One draft in either editor. Immutable: every edit returns a new copy, which is what undo keeps.
 * Saved as JSON, so every class in this tree has a stable `@SerialName` that must never change.
 */
@Serializable
data class Project(
    val id: String,
    val type: ProjectType,
    val name: String,
    val canvas: Canvas,
    /** Set when [type] is [ProjectType.VIDEO]. */
    val video: VideoTimeline? = null,
    /** Set when [type] is [ProjectType.PHOTO]. */
    val photo: PhotoScene? = null,
    /** Wall-clock time in microseconds since 1970-01-01 UTC, like every time in the project model. */
    val createdAtEpochUs: Long,
    /** When the project was last saved, which the drafts list shows as "last edited". */
    val updatedAtEpochUs: Long,
    /** Older values only exist in saved JSON; loading migrates it to [CURRENT_SCHEMA_VERSION] first. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        /** Bump with a migration step in :core:data for any change to the saved shape (CLAUDE.md rule 6). */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
enum class ProjectType {
    @SerialName("video") VIDEO,
    @SerialName("photo") PHOTO,
}
