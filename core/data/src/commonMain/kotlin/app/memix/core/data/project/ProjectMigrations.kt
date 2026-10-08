package app.memix.core.data.project

import kotlinx.serialization.json.JsonObject

/**
 * One step of the migration chain: takes project JSON saved at [fromVersion] and returns it in the
 * shape of version [fromVersion] + 1. [ProjectJsonMigrator] writes the new schemaVersion itself.
 */
internal class ProjectMigration(val fromVersion: Int, val migrate: (JsonObject) -> JsonObject)

/**
 * Every change to the saved shape of Project adds one step here and bumps Project.CURRENT_SCHEMA_VERSION
 * (CLAUDE.md rule 6); then open a draft saved before the change to check it. For example, renaming
 * Project.name to title in version 2 would add:
 *
 *     ProjectMigration(fromVersion = 1) { json -> JsonObject(json - "name" + ("title" to json.getValue("name"))) }
 */
internal val projectMigrations: List<ProjectMigration> = listOf(
    // 1 → 2 (P1-02): MediaRef gained durationUs, pixelSize and hasAudio, measured at import. All three are optional
    // with null defaults, so version-1 JSON decodes as it is and the step only moves the version on. Media in a
    // version-1 draft keeps null for them; the editor reads them from the copy when it opens such a draft.
    ProjectMigration(fromVersion = 1) { json -> json },
)
