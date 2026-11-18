package app.memix.core.domain.video

import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.model.project.FreezeReverseCatalog
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlin.uuid.Uuid

/**
 * Reads the frame of [video] at [sourceTimeUs] into a picture in project [projectId]'s own media folder (P4-10, Freeze frame): a photo [MediaRef] the freeze clip
 * plays like any photo, so it needs no video decoder and survives the video being replaced. The picture is the frame in its own shape, at most
 * [FreezeReverseCatalog.FREEZE_PICTURE_LONG_SIDE_PX] on its long side; the clip's crop, filters and framing are applied on top by the same code as for a photo.
 * Reads and writes files: off the main thread. Fails with [AppError.NotFound] when the video isn't on the phone, [AppError.StorageFull] when the phone is full,
 * [AppError.Unexpected] when the frame can't be read.
 */
class ReadFreezePictureUseCase(private val videoEngine: VideoEngine, private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(projectId: String, video: MediaRef, sourceTimeUs: Long): Outcome<MediaRef> {
        // Never bigger than the video itself: a 720p clip freezes at 720p, not stretched.
        val longSidePx = video.pixelSize?.let { maxOf(it.widthPx, it.heightPx) }?.takeIf { it > 0 }?.coerceAtMost(FreezeReverseCatalog.FREEZE_PICTURE_LONG_SIDE_PX)
            ?: FreezeReverseCatalog.FREEZE_PICTURE_LONG_SIDE_PX
        val frame = videoEngine.openThumbnails().use { reader -> reader.frame(video, sourceTimeUs, longSidePx) }
        val picture = when (frame) {
            is Outcome.Success -> frame.value
            is Outcome.Failure -> return frame
        }
        val path = MediaPaths.newCopy(projectId, MediaKind.IMAGE, PICTURE_EXTENSION)
        return when (mediaFiles.write(picture.jpegBytes, path)) {
            CopyResult.COPIED -> Outcome.Success(
                MediaRef(
                    origin = MediaOrigin.CatalogItem("$FREEZE_ORIGIN/${Uuid.random()}"),
                    kind = MediaKind.IMAGE,
                    cachedCopyPath = path,
                    pixelSize = video.pixelSize,
                    hasAudio = false,
                ),
            )
            CopyResult.STORAGE_FULL -> Outcome.Failure(AppError.StorageFull)
            CopyResult.UNREADABLE -> Outcome.Failure(AppError.Unexpected(IllegalStateException("The freeze picture couldn't be written")))
        }
    }

    private companion object {
        const val PICTURE_EXTENSION = "jpg"
        const val FREEZE_ORIGIN = "freeze-frame"
    }
}
