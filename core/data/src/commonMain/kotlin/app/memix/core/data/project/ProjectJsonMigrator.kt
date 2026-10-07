package app.memix.core.data.project

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Brings saved project JSON from the version it was saved with up to [targetVersion], one step per version. */
internal class ProjectJsonMigrator(
    private val steps: List<ProjectMigration>,
    private val targetVersion: Int,
) {
    init {
        // A missing step would otherwise only show when someone opens an old draft; this fails on the first save or load.
        val expectedFromVersions = (FIRST_SCHEMA_VERSION until targetVersion).toList()
        require(steps.map { it.fromVersion } == expectedFromVersions) {
            "Migration steps must start at versions $expectedFromVersions, in order; found ${steps.map { it.fromVersion }}"
        }
    }

    /** @throws SerializationException when the JSON has no schemaVersion or was saved by a newer app. */
    fun migrate(saved: JsonObject): JsonObject {
        val savedVersion = saved.schemaVersion()
        if (savedVersion !in FIRST_SCHEMA_VERSION..targetVersion) {
            throw SerializationException("Can't migrate a project saved with schema $savedVersion to $targetVersion")
        }
        return steps.drop(savedVersion - FIRST_SCHEMA_VERSION).fold(saved) { json, step ->
            step.migrate(json).withSchemaVersion(step.fromVersion + 1)
        }
    }

    private fun JsonObject.schemaVersion(): Int =
        this[SCHEMA_VERSION_KEY]?.jsonPrimitive?.intOrNull
            ?: throw SerializationException("Saved project has no $SCHEMA_VERSION_KEY")

    private fun JsonObject.withSchemaVersion(version: Int) = JsonObject(this + (SCHEMA_VERSION_KEY to JsonPrimitive(version)))

    private companion object {
        const val FIRST_SCHEMA_VERSION = 1

        // The JSON name of Project.schemaVersion.
        const val SCHEMA_VERSION_KEY = "schemaVersion"
    }
}
