package app.memix.core.domain.photo

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.AppError
import app.memix.core.domain.EditorTool
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.domain.project.layers
import app.memix.core.domain.project.nowEpochUs
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** How [MakeVideoMemeUseCase] ended, when it wasn't cancelled. */
sealed interface MakeVideoMemeOutcome {
    /** The video draft [projectId] is saved; the video editor opens it. */
    data class Made(val projectId: String) : MakeVideoMemeOutcome

    /** The phone ran out of room; nothing was kept. */
    data object NoSpace : MakeVideoMemeOutcome

    /** The picture or the draft couldn't be made (memory, a picture that can't be read, a failed save); nothing was kept. */
    data object Failed : MakeVideoMemeOutcome
}

/**
 * Make it a video meme (spec docs/ux/specs/P2-10-make-it-a-video-meme.md): turns the photo project [invoke] is given into a new
 * video project and saves it. The photo draft is never touched.
 *
 * - The layers under the lifted text ([splitForVideoMeme]) are drawn into one JPEG by [ScenePictureBaker], the scene drawing the
 *   export uses, at the canvas's shape with the shorter side [BAKE_SHORT_SIDE_PX] pixels. The file goes into the new project's own
 *   media folder.
 * - The video project ([videoMemeProject]) has that picture as its 3 s main clip and the lifted texts as captions.
 * - A background picture is linked (copied where the file system refuses) into the new folder, because media is never shared by
 *   path between drafts.
 * - All or nothing: any failure, and a cancel, delete the new folder, so no half-made draft is left. The first save of the draft is
 *   the last step. Logs `tool_use` `make_video` (editor `photo`) once the draft exists; no `project_create` (spec → Analytics).
 */
class MakeVideoMemeUseCase(
    private val baker: ScenePictureBaker,
    private val mediaFiles: MediaFiles,
    private val saveProject: SaveProjectUseCase,
    private val updateThumbnail: UpdateDraftThumbnailUseCase,
    private val leftoverCleaner: LeftoverMediaCleaner,
    private val analytics: Analytics,
    private val logger: Logger,
    private val clock: Clock,
) {
    suspend operator fun invoke(photo: Project): MakeVideoMemeOutcome {
        require(photo.type == ProjectType.PHOTO) { "Only a photo project becomes a video meme" }
        // The start-up cleanup deletes folders without a saved draft, which the new folder is until the save.
        leftoverCleaner.awaitDone()
        val newId = Uuid.random().toString()
        return try {
            make(photo, newId)
        } catch (e: CancellationException) {
            // Only a cancel before the save lands here: the save below can't be cancelled, so a saved draft keeps its folder.
            withContext(NonCancellable) { mediaFiles.deleteFolder(MediaPaths.projectFolder(newId)) }
            throw e
        } catch (e: Exception) {
            // A file or database error must not crash the caller's scope or leave the button busy.
            logger.error(TAG, "The video meme couldn't be made", e)
            withContext(NonCancellable) { failed(newId, MakeVideoMemeOutcome.Failed) }
        }
    }

    private suspend fun make(photo: Project, newId: String): MakeVideoMemeOutcome {
        val split = photo.layers().splitForVideoMeme()
        val scene = photo.copy(photo = photo.photo?.copy(layers = split.baked))
        val output = PhotoExportCatalog.outputSize(photo.canvas, BAKE_SHORT_SIDE_PX)
        val baked = try {
            baker.bake(scene, output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "The scene couldn't be drawn", e)
            null
        } ?: return failed(newId, MakeVideoMemeOutcome.Failed)
        val picturePath = MediaPaths.newCopy(newId, MediaKind.IMAGE, PICTURE_EXTENSION)
        when (mediaFiles.write(baked.jpegBytes, picturePath)) {
            CopyResult.COPIED -> Unit
            CopyResult.STORAGE_FULL -> return failed(newId, MakeVideoMemeOutcome.NoSpace)
            CopyResult.UNREADABLE -> return failed(newId, MakeVideoMemeOutcome.Failed)
        }
        val background = when (val brought = backgroundFor(photo.canvas, newId)) {
            is Outcome.Success -> brought.value
            is Outcome.Failure -> return failed(newId, outcomeOf(brought.error))
        }
        val video = videoMemeProject(
            id = newId,
            canvas = photo.canvas.copy(background = background),
            picture = bakedPictureRef(newId, picturePath, baked.size),
            lifted = split.lifted,
            nowEpochUs = clock.nowEpochUs(),
        )
        // The point of no return: once the row may be written, a cancel must not delete the folder under it.
        return when (val saved = withContext(NonCancellable) { saveProject(video) }) {
            is Outcome.Success -> {
                analytics.log(AnalyticsEvent.ToolUse(ProjectType.PHOTO, EditorTool.MAKE_VIDEO))
                updateThumbnail(video)
                MakeVideoMemeOutcome.Made(newId)
            }
            is Outcome.Failure -> failed(newId, outcomeOf(saved.error))
        }
    }

    // The canvas's background in the new draft: a color is kept as it is; a picture is brought into the new folder, and where its file
    // is gone the canvas is black, as the photo draws it (spec → States, "A picture's file is missing").
    private suspend fun backgroundFor(canvas: Canvas, newId: String): Outcome<CanvasBackground> {
        val image = canvas.background as? CanvasBackground.Image ?: return Outcome.Success(canvas.background)
        val source = image.source.cachedCopyPath ?: return Outcome.Success(MISSING_BACKGROUND)
        val destination = MediaPaths.inFolderOf(newId, source)
        if (mediaFiles.link(source, destination)) return Outcome.Success(image.copyingTo(destination))
        return when (mediaFiles.copyFile(source, destination) { }) {
            CopyResult.COPIED -> Outcome.Success(image.copyingTo(destination))
            CopyResult.UNREADABLE -> Outcome.Success(MISSING_BACKGROUND)
            CopyResult.STORAGE_FULL -> Outcome.Failure(AppError.StorageFull)
        }
    }

    private fun CanvasBackground.Image.copyingTo(path: String) = CanvasBackground.Image(source.copy(cachedCopyPath = path))

    private fun outcomeOf(error: AppError): MakeVideoMemeOutcome =
        if (error == AppError.StorageFull) MakeVideoMemeOutcome.NoSpace else MakeVideoMemeOutcome.Failed

    // Nothing of a failed attempt stays: the folder holds only what this attempt wrote.
    private suspend fun failed(newId: String, outcome: MakeVideoMemeOutcome): MakeVideoMemeOutcome {
        mediaFiles.deleteFolder(MediaPaths.projectFolder(newId))
        return outcome
    }

    companion object {
        /** The baked picture's shorter side: the 1x size of the photo export, so the video editor stays light (spec → Rules 1). */
        const val BAKE_SHORT_SIDE_PX = 1080

        private const val TAG = "MakeVideoMeme"
        private const val PICTURE_EXTENSION = "jpg"
        private val MISSING_BACKGROUND = CanvasBackground.Solid(ArgbColor(0xFF000000))
    }
}
