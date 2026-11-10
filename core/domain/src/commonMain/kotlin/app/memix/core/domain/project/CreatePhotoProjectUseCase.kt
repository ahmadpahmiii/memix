package app.memix.core.domain.project

import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PhotoScene
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Starts an empty photo project in memory: PhotoCatalog's blank canvas (1:1, 1080 × 1080, white) and no layer. It isn't
 * saved until something lands in it, so backing out right away leaves no empty draft: a gallery start saves it with the
 * picked photo ([SaveGalleryPhotoProjectUseCase]), a blank start saves it at its first committed step that leaves a
 * layer ([StartEditSessionUseCase] with a `newSource`).
 */
class CreatePhotoProjectUseCase(private val clock: Clock) {
    /** The name stays empty until the user gives one: the drafts list shows "Untitled photo meme" in their language. */
    operator fun invoke(): Project {
        val now = clock.nowEpochUs()
        return Project(
            id = Uuid.random().toString(),
            type = ProjectType.PHOTO,
            name = "",
            canvas = Canvas(
                ratio = PhotoCatalog.BLANK_RATIO,
                widthPx = PhotoCatalog.BLANK_SIZE.widthPx,
                heightPx = PhotoCatalog.BLANK_SIZE.heightPx,
                background = CanvasBackground.Solid(PhotoCatalog.BACKGROUND),
            ),
            photo = PhotoScene(layers = emptyList()),
            createdAtEpochUs = now,
            updatedAtEpochUs = now,
        )
    }
}
