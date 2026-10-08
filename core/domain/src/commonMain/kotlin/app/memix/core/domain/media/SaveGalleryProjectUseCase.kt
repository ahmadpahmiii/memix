package app.memix.core.domain.media

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Outcome
import app.memix.core.domain.ProjectSource
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType

/**
 * The first save of a new video project made from picked media, once something has landed: [media] goes on the
 * main video track from 0 µs in pick order. Nothing is saved before this, so backing out or cancelling leaves no
 * empty draft. Logs `project_create` when the save works. The editor then opens the saved draft.
 */
class SaveGalleryProjectUseCase(private val saveProject: SaveProjectUseCase, private val analytics: Analytics) {
    suspend operator fun invoke(newProject: Project, media: List<MediaRef>): Outcome<Unit> {
        require(media.isNotEmpty()) { "A gallery project is saved only once something went in" }
        val outcome = saveProject(newProject.withMediaAppended(media))
        if (outcome is Outcome.Success) {
            analytics.log(AnalyticsEvent.ProjectCreate(editor = ProjectType.VIDEO, source = ProjectSource.GALLERY))
        }
        return outcome
    }
}
