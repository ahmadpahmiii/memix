package app.memix.core.domain.media

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.Outcome
import app.memix.core.domain.ProjectSource
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.photoCanvasFor
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.PhotoScene
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import kotlin.uuid.Uuid

/**
 * The first save of a new photo project made from a picked photo, once it has landed (spec P2-01 → Starting a photo
 * meme): [photo] becomes the one image layer, at scale 1, and the canvas takes the photo's own shape
 * ([photoCanvasFor]). Nothing is saved before this, so backing out or cancelling leaves no empty draft. Logs
 * `project_create` (`photo`, `gallery`) when the save works, and starts the draft's thumbnail without waiting for it.
 * The editor then opens the saved draft.
 */
class SaveGalleryPhotoProjectUseCase(
    private val saveProject: SaveProjectUseCase,
    private val analytics: Analytics,
    private val updateThumbnail: UpdateDraftThumbnailUseCase,
) {
    suspend operator fun invoke(newProject: Project, photo: MediaRef): Outcome<Unit> {
        // A photo whose size wasn't measured keeps the blank canvas: the editor still shows it whole, fitted inside.
        val canvas = photo.pixelSize?.let(::photoCanvasFor) ?: newProject.canvas
        val project = newProject.copy(
            canvas = canvas,
            photo = PhotoScene(layers = listOf(ImageLayer(id = Uuid.random().toString(), source = photo))),
        )
        val outcome = saveProject(project)
        if (outcome is Outcome.Success) {
            analytics.log(AnalyticsEvent.ProjectCreate(editor = ProjectType.PHOTO, source = ProjectSource.GALLERY))
            updateThumbnail(project)
        }
        return outcome
    }
}
