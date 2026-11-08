package app.memix.engine.photo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import app.memix.core.domain.Logger
import app.memix.core.domain.photo.PhotoDestination
import app.memix.core.domain.photo.PhotoRenderOutcome
import app.memix.core.domain.photo.PhotoRenderRequest
import app.memix.core.domain.photo.PhotoRenderer
import app.memix.core.domain.photo.RenderedPhoto
import app.memix.core.domain.video.ExportError
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.model.project.Project
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The Android photo export (spec docs/ux/specs/P2-08-photo-export.md): draws the scene at the output size with
 * [PhotoSceneRenderer], encodes it with `Bitmap.compress` (sRGB, no EXIF: the encoder writes none, so a source photo's location
 * and camera data never travel) and writes it to the gallery's "Memix" album, the clipboard's cache folder or the debug hook's
 * folder. Everything runs on [ioDispatcher]; cancellation deletes the partial file.
 */
internal class AndroidPhotoRenderer(
    private val context: Context,
    private val sceneRenderer: PhotoSceneRenderer,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : PhotoRenderer {
    override suspend fun render(project: Project, request: PhotoRenderRequest): PhotoRenderOutcome {
        var written: PictureTarget? = null
        try {
            return withContext(ioDispatcher) { renderAndWrite(project, request) { written = it } }
        } catch (e: CancellationException) {
            // withContext throws on its way back when a cancel came after the file was finished: that picture goes too.
            written?.let { withContext(NonCancellable + ioDispatcher) { it.discard() } }
            throw e
        }
    }

    private suspend fun renderAndWrite(project: Project, request: PhotoRenderRequest, onFinished: (PictureTarget) -> Unit): PhotoRenderOutcome {
        val settings = request.settings
        val output = PhotoExportCatalog.outputSize(project.canvas, settings.shortSidePx)
        val target = try {
            targetFor(request)
        } catch (e: IOException) {
            logger.error(TAG, "The picture's file couldn't be created", e)
            return PhotoRenderOutcome.Failed(errorOf(e))
        }
        return try {
            val bitmap = sceneRenderer.render(project, output, request.watermark).asAndroidBitmap()
            currentCoroutineContext().ensureActive()
            encode(bitmap, settings.format, settings.quality, target)
            currentCoroutineContext().ensureActive()
            val finished = target.finish()
            onFinished(target)
            PhotoRenderOutcome.Done(RenderedPhoto(finished.location, finished.sizeBytes, output, settings.format))
        } catch (e: CancellationException) {
            target.discard()
            throw e
        } catch (e: Throwable) {
            // Out-of-memory included: a size this phone can't make is a calm notice, not a crash.
            target.discard()
            logger.error(TAG, "The picture couldn't be made", e)
            PhotoRenderOutcome.Failed(errorOf(e))
        }
    }

    override suspend fun deleteOldCopies() = withContext(ioDispatcher) {
        CacheFileTarget.deleteAll(context.cacheDir, COPIES_FOLDER)
        GalleryPictureTarget.discardUnfinished(context)
    }

    private fun targetFor(request: PhotoRenderRequest): PictureTarget = when (request.destination) {
        PhotoDestination.GALLERY -> GalleryPictureTarget.create(context, request.settings.format)
        PhotoDestination.CLIPBOARD_FILE -> CacheFileTarget.create(context.cacheDir, COPIES_FOLDER, request.settings.format)
        PhotoDestination.APP_CACHE -> CacheFileTarget.create(context.cacheDir, EXPORTS_FOLDER, request.settings.format)
    }

    // Bitmap.compress reports a failed write only as false; the stream remembers why.
    private fun encode(bitmap: Bitmap, format: PhotoExportFormat, quality: Int, target: PictureTarget) {
        val failure = arrayOfNulls<IOException>(1)
        val written = RememberingStream(target.openStream()) { failure[0] = it }.use { stream ->
            when (format) {
                PhotoExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                PhotoExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY_IGNORED, stream)
            }
        }
        if (!written) throw failure[0] ?: IllegalStateException("The picture couldn't be encoded")
    }

    private companion object {
        const val TAG = "MemixPhotoExport"
        const val COPIES_FOLDER = "photo-copies"
        const val EXPORTS_FOLDER = "photo-exports"
        const val PNG_QUALITY_IGNORED = 100
    }
}

/** An output stream that tells [onFailure] about the IOException a write threw, which the native encoder would swallow. */
private class RememberingStream(output: OutputStream, private val onFailure: (IOException) -> Unit) : FilterOutputStream(output) {
    override fun write(b: ByteArray, off: Int, len: Int) {
        try {
            out.write(b, off, len)
        } catch (e: IOException) {
            onFailure(e)
            throw e
        }
    }
}

/**
 * The `export_fail` code for a failure (spec P2-08 → Analytics): a full disk anywhere in the cause chain, a picture that can't be
 * read, and everything else a phone can't do at this size (out-of-memory, the encoder) as `encoder`.
 */
internal fun errorOf(e: Throwable): ExportError = when {
    e.isOutOfSpace() -> ExportError.STORAGE_FULL
    e is UnreadablePictureException -> ExportError.MISSING_MEDIA
    e is OutOfMemoryError || e is IllegalStateException -> ExportError.ENCODER
    else -> ExportError.UNKNOWN
}

// A full disk surfaces as ENOSPC somewhere down the cause chain, usually an ErrnoException under an IOException.
private fun Throwable.isOutOfSpace(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it.message?.contains(NO_SPACE_ERRNO) == true }

private const val NO_SPACE_ERRNO = "ENOSPC"
private const val MAX_CAUSE_DEPTH = 10
