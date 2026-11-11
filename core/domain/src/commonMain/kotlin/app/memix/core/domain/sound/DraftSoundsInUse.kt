package app.memix.core.domain.sound

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.mediaCopyPaths
import app.memix.core.domain.project.ProjectRepository
import kotlinx.coroutines.flow.first

/**
 * The sounds every saved draft uses, or null when that can't be known (a draft saved by a newer app). A draft keeps `CatalogItem(id)` and the shared path `sounds/<id>.<extension>`,
 * not a copy of its own (TECHNICAL_DESIGN → Meme sounds), so the cache cleanup asks here before it deletes a file.
 */
class DraftSoundsInUse(private val projects: ProjectRepository, private val logger: Logger) : SoundsInUse {
    override suspend fun ids(): Set<String>? {
        val used = HashSet<String>()
        for (summary in projects.observeSummaries().first()) {
            val project = (projects.get(summary.id) as? Outcome.Success)?.value
            if (project == null) {
                // A draft from a newer app might use any sound, so nothing may be deleted until it can be read. A
                // damaged one is skipped (its sounds are not protected) so it can't stop the cleanup for good.
                if (summary.needsNewerApp) return null
                logger.error("SoundDownloads", "A damaged draft is skipped when listing the sounds drafts use")
                continue
            }
            project.mediaCopyPaths().forEach { path -> SoundPaths.soundIdOf(path)?.let(used::add) }
        }
        return used
    }
}
