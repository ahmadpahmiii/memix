package app.memix.core.data.project

import app.memix.core.model.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Turns a [Project] into the JSON kept in the project table and back, migrating old JSON on the way in. */
internal class ProjectJsonCodec(private val migrator: ProjectJsonMigrator) {
    fun encode(project: Project): String = json.encodeToString(Project.serializer(), project)

    /**
     * Throws when the JSON can't be read or migrated: SerializationException for damaged JSON, an unknown key
     * or an unsupported schemaVersion, or whatever a faulty migration step throws.
     */
    fun decode(savedJson: String): Project {
        val saved = json.parseToJsonElement(savedJson).jsonObject
        return json.decodeFromJsonElement(Project.serializer(), migrator.migrate(saved))
    }

    private companion object {
        val json = Json {
            // Write every field, so changing a default value in code never changes what an old draft means.
            encodeDefaults = true
            // ignoreUnknownKeys stays off: an unknown key means a missing migration step, and ignoring it would
            // drop that data for good on the next save. Failing to open keeps the saved draft intact.
        }
    }
}
