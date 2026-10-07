package app.memix.core.model.project

/** What the drafts list shows for one project, read without loading the whole project. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val type: ProjectType,
    val updatedAtEpochUs: Long,
    /** Relative to the app's files directory; null until a thumbnail has been rendered. */
    val thumbnailPath: String?,
)
