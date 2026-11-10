package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaInspector
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project

/**
 * Opens a saved photo draft for the editor. An image whose picture size wasn't measured at import has none to size
 * its layer by (spec P2-01 → Size at scale 1); it is read from the app's copy now. The draft isn't saved for this: the
 * size goes into the database with the next edit. Fails like [ProjectRepository.get]. A copy that is missing or can't be
 * read keeps its size null; the editor then draws nothing for that layer and says so.
 */
class OpenPhotoDraftUseCase(private val repository: ProjectRepository, private val inspector: MediaInspector) {
    suspend operator fun invoke(projectId: String): Outcome<Project> =
        when (val loaded = repository.get(projectId)) {
            is Outcome.Failure -> loaded
            is Outcome.Success -> Outcome.Success(withMeasuredImages(loaded.value))
        }

    private suspend fun withMeasuredImages(project: Project): Project {
        val scene = project.photo ?: return project
        val unmeasured = scene.layers.filterIsInstance<ImageLayer>().filter { it.source.pixelSize == null && it.source.cachedCopyPath != null }
        if (unmeasured.isEmpty()) return project
        val sizes = unmeasured.mapNotNull { it.source.cachedCopyPath }.distinct().associateWith { path ->
            inspector.inspect(path, MediaKind.IMAGE)?.pixelSize
        }
        val layers = scene.layers.map { layer ->
            val size = (layer as? ImageLayer)?.source?.cachedCopyPath?.let(sizes::get)
            if (layer is ImageLayer && size != null) layer.copy(source = layer.source.copy(pixelSize = size)) else layer
        }
        return project.copy(photo = scene.copy(layers = layers))
    }
}
