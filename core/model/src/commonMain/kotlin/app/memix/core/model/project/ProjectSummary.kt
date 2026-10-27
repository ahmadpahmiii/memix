package app.memix.core.model.project

/** What the drafts list shows for one project, read without loading the whole project. */
data class ProjectSummary(
    val id: String,
    /** Empty for an unnamed draft; the list shows a stand-in in the user's language. */
    val name: String,
    val type: ProjectType,
    /** Breaks ties in the list's order: the newer of two drafts with the same edit time goes first (P1-14). */
    val createdAtEpochUs: Long,
    val updatedAtEpochUs: Long,
    /** The version of the app's project format the draft was saved in; above [Project.CURRENT_SCHEMA_VERSION] it can't be opened. */
    val schemaVersion: Int,
    /** Relative to the app's files directory; null until a thumbnail has been rendered. */
    val thumbnailPath: String?,
) {
    /** Saved by a newer Memix, so this version can't open, rename or duplicate it; it can still be deleted. */
    val needsNewerApp: Boolean get() = schemaVersion > Project.CURRENT_SCHEMA_VERSION
}
