package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.model.project.Project
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The P1-16 Replace hand check (debug and benchmark builds): every editor save logs which values of the project changed
 * since the stored draft, one line per changed value under `MemixEditorCheck`, such as
 * `video.tracks[main].items[clip-2].source.cachedCopyPath: "media/…/a.mp4" -> "media/…/b.mp4"`. Items are named by
 * their id, so an edit to one clip reads as that clip. The save itself is unchanged
 * (docs/qa/phase-1/engineer-hand-checks.md → P1-16).
 */
internal object EditorSaveDiffLog {
    /** [repository], logging each save's changes through [logger]. */
    fun around(repository: ProjectRepository, logger: Logger): ProjectRepository = object : ProjectRepository by repository {
        override suspend fun save(project: Project): Outcome<Unit> {
            val stored = (repository.get(project.id) as? Outcome.Success)?.value
            val result = repository.save(project)
            if (stored != null && result is Outcome.Success) logChanges(stored, project, logger)
            return result
        }
    }

    private fun logChanges(before: Project, after: Project, logger: Logger) {
        val changes = ArrayList<String>()
        collectChanges("", json.encodeToJsonElement(Project.serializer(), before), json.encodeToJsonElement(Project.serializer(), after), changes)
        // Every save stamps a new time; that isn't an edit.
        changes.removeAll { it.startsWith("$UPDATED_AT:") }
        logger.debug(TAG, "Saved ${after.id}: ${changes.size} values changed")
        changes.take(MAX_LINES).forEach { logger.debug(TAG, "  $it") }
        if (changes.size > MAX_LINES) logger.debug(TAG, "  … and ${changes.size - MAX_LINES} more")
    }

    private fun collectChanges(path: String, before: JsonElement?, after: JsonElement?, changes: MutableList<String>) {
        when {
            before is JsonObject && after is JsonObject ->
                (before.keys + after.keys).forEach { key -> collectChanges(childPath(path, key), before[key], after[key], changes) }
            before is JsonArray && after is JsonArray -> collectArrayChanges(path, before, after, changes)
            before != after -> changes += "$path: ${before ?: NONE} -> ${after ?: NONE}"
        }
    }

    // Lists of things with ids (tracks, items) are matched by id, so an insert doesn't read as every later item changing.
    private fun collectArrayChanges(path: String, before: JsonArray, after: JsonArray, changes: MutableList<String>) {
        val beforeById = before.associateByIdOrNull()
        val afterById = after.associateByIdOrNull()
        if (beforeById == null || afterById == null) {
            for (index in 0 until max(before.size, after.size)) collectChanges("$path[$index]", before.getOrNull(index), after.getOrNull(index), changes)
            return
        }
        (beforeById.keys + afterById.keys).forEach { id -> collectChanges("$path[$id]", beforeById[id], afterById[id], changes) }
        if (before.idsInOrder() != after.idsInOrder()) changes += "$path order: ${before.idsInOrder()} -> ${after.idsInOrder()}"
    }

    private fun JsonArray.associateByIdOrNull(): Map<String, JsonElement>? {
        val ids = map { ((it as? JsonObject)?.get(ID) as? JsonPrimitive)?.content ?: return null }
        return ids.zip(this).toMap()
    }

    private fun JsonArray.idsInOrder(): List<String> = mapNotNull { ((it as? JsonObject)?.get(ID) as? JsonPrimitive)?.content }

    private fun childPath(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    private const val TAG = "MemixEditorCheck"
    private const val ID = "id"
    private const val UPDATED_AT = "updatedAtEpochUs"
    private const val NONE = "(none)"
    // An Add media of 35 photos changes a few hundred values; the first ones say enough.
    private const val MAX_LINES = 60

    // Every value written, defaults included, so a change to or from a default shows.
    private val json = Json { encodeDefaults = true }
}
