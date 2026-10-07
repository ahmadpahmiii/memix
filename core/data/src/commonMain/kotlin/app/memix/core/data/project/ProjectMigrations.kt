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
internal val projectMigrations: List<ProjectMigration> = emptyList()
