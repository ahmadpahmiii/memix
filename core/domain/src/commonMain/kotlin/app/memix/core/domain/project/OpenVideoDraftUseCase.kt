package app.memix.core.domain.project

import app.memix.core.domain.Outcome
import app.memix.core.domain.media.MediaInspector
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem

/**
 * Opens a saved video draft for the editor. Media added before schema version 2 has no measured length or
 * has-sound (P1-02); the preview needs every length up front, so those facts are read from the app's copy of
 * each such file now. The draft isn't saved for this: the facts go into the database with the next edit.
 *
 * Fails like [ProjectRepository.get]. A copy that is missing or can't be read keeps its facts null; the
 * preview then plays that clip as black and says so.
 */
class OpenVideoDraftUseCase(private val repository: ProjectRepository, private val inspector: MediaInspector) {
    suspend operator fun invoke(projectId: String): Outcome<Project> =
        when (val loaded = repository.get(projectId)) {
            is Outcome.Failure -> loaded
            is Outcome.Success -> Outcome.Success(withMeasuredMedia(loaded.value))
        }

    private suspend fun withMeasuredMedia(project: Project): Project {
        val timeline = project.video ?: return project
        val unmeasured = timeline.tracks
            .flatMap { it.items }
            .mapNotNull { it.mediaSource() }
            .filter { it.needsMeasuring() }
            .distinct()
        if (unmeasured.isEmpty()) return project
        val measured = unmeasured.associateWith { measure(it) }
        val tracks = timeline.tracks.map { track -> track.copy(items = track.items.map { it.withSource(measured) }) }
        return project.copy(video = timeline.copy(tracks = tracks))
    }

    private suspend fun measure(media: MediaRef): MediaRef {
        val path = media.cachedCopyPath ?: return media
        val facts = inspector.inspect(path, media.kind) ?: return media
        return media.copy(
            durationUs = media.durationUs ?: facts.durationUs,
            pixelSize = media.pixelSize ?: facts.pixelSize,
            hasAudio = media.hasAudio ?: facts.hasAudio,
        )
    }

    // Photos show for their clip's length and have no sound, so only video and sound files need measuring.
    private fun MediaRef.needsMeasuring(): Boolean =
        cachedCopyPath != null && kind != MediaKind.IMAGE && (durationUs == null || hasAudio == null)

    // Only what the engine renders today; stickers get their facts when they're rendered (P4-12).
    private fun TimelineItem.mediaSource(): MediaRef? = when (this) {
        is MediaClip -> source
        is AudioClip -> source
        is TextItem, is StickerItem, is EffectItem -> null
    }

    private fun TimelineItem.withSource(measured: Map<MediaRef, MediaRef>): TimelineItem = when (this) {
        is MediaClip -> measured[source]?.let { copy(source = it) } ?: this
        is AudioClip -> measured[source]?.let { copy(source = it) } ?: this
        is TextItem, is StickerItem, is EffectItem -> this
    }
}
