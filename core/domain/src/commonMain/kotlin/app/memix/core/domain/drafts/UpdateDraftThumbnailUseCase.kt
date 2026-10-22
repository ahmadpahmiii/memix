package app.memix.core.domain.drafts

import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.coverClip
import app.memix.core.domain.video.VideoEngine
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a draft's thumbnail, the drafts list's picture (spec P1-14 → Draft row), in step with its cover: the first
 * main clip's frame at its trim-in, a [THUMBNAIL_SIZE_PX] square JPEG from the engine's thumbnail reader, in the
 * draft's folder. Called after the first save (P1-02 import) and when the editor goes to the background or closes.
 *
 * The file is named after the cover ([MediaPaths.thumbnail]), so a draft whose saved path already matches is left
 * alone: nothing is read or written unless the first clip or its trim-in changed. A draft saved before P1-14 gets its
 * thumbnail the first time it's left. Never changes the edit time. Runs in [scope], the app's, one draft at a time,
 * so it finishes after the editor has gone; failures only leave the placeholder.
 */
class UpdateDraftThumbnailUseCase(
    private val repository: ProjectRepository,
    private val videoEngine: VideoEngine,
    private val mediaFiles: MediaFiles,
    private val leftoverCleaner: LeftoverMediaCleaner,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    /** Returns at once; the work runs in the app's scope. */
    operator fun invoke(project: Project) {
        scope.launch { lock.withLock { update(project) } }
    }

    private suspend fun update(project: Project) {
        val cover = project.coverClip() ?: return
        val coverFile = cover.source.cachedCopyPath ?: return
        val path = MediaPaths.thumbnail(project.id, coverFile, cover.trimInUs)
        // The cleanup at start would take a new thumbnail for an unused file while the row still names the old one.
        leftoverCleaner.awaitDone()
        // NotFound: the draft was deleted meanwhile (an empty draft leaving the editor).
        val saved = repository.thumbnailPath(project.id) as? Outcome.Success ?: return
        if (saved.value == path) return
        val picture = videoEngine.openThumbnails().use { reader -> reader.thumbnail(cover.source, cover.trimInUs, THUMBNAIL_SIZE_PX) }
        if (picture !is Outcome.Success) return
        if (mediaFiles.write(picture.value.jpegBytes, path) != CopyResult.COPIED) return
        if (repository.setThumbnailPath(project.id, path) !is Outcome.Success) {
            mediaFiles.delete(listOf(path))
            return
        }
        saved.value?.let { old -> mediaFiles.delete(listOf(old)) }
    }

    companion object {
        /** Sharp at the row's 56 dp on a 3× screen (168 px) with a little to spare. */
        const val THUMBNAIL_SIZE_PX = 192
    }
}
