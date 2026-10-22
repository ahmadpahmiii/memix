package app.memix.engine.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.ExportException
import app.memix.core.domain.video.ExportDestination
import app.memix.core.domain.video.ExportError
import java.io.File

/** Where one export's file is written, and what becomes of it when the export ends. */
@OptIn(UnstableApi::class)
internal sealed interface ExportOutput {
    /** The path Transformer is given (the gallery's muxer ignores it). */
    val transformerPath: String

    /** The muxer to write with; null for Media3's own, writing to [transformerPath]. */
    fun muxerFactory(): Muxer.Factory?

    /** The export finished: the file is made visible where it belongs. Disk work. */
    fun finish(): FinishedFile

    /** The export failed or was cancelled: the partial file goes. Disk work. */
    fun discard()

    /** The user's export: a pending item in the gallery. */
    class Gallery(private val file: GalleryVideoFile) : ExportOutput {
        override val transformerPath get() = file.transformerPath

        override fun muxerFactory(): Muxer.Factory = file.muxerFactory()

        override fun finish() = FinishedFile(file.uri.toString(), file.publish())

        override fun discard() = file.discard()
    }

    /** The debug export hook's file in `cache/exports/`, which its owner steps pull with adb. */
    class AppCache(private val file: File) : ExportOutput {
        override val transformerPath: String get() = file.path

        override fun muxerFactory(): Muxer.Factory? = null

        override fun finish() = FinishedFile(file.path, file.length())

        override fun discard() {
            file.delete()
        }
    }

    companion object {
        /** A new output at [destination]. Disk work; throws IOException when nothing can be created there. */
        fun create(context: Context, destination: ExportDestination): ExportOutput {
            val exportsDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
            return when (destination) {
                ExportDestination.GALLERY -> Gallery(GalleryVideoFile.create(context, exportsDir))
                ExportDestination.APP_CACHE -> AppCache(File.createTempFile(FILE_PREFIX, FILE_SUFFIX, exportsDir))
            }
        }

        private const val EXPORTS_DIR = "exports"
        private const val FILE_PREFIX = "export-"
        private const val FILE_SUFFIX = ".mp4"
    }
}

/** A finished export's file: its content URI or path, and its size. */
internal class FinishedFile(val location: String, val sizeBytes: Long)

/**
 * The `export_fail` code for a failure (spec P1-12 → Analytics): a full disk anywhere in the cause chain, Media3's
 * input I/O (a clip that went missing during the export), decoding, and encoding or muxing.
 */
@OptIn(UnstableApi::class)
internal fun exportErrorOf(e: Throwable): ExportError {
    if (e.isOutOfSpace()) return ExportError.STORAGE_FULL
    val exportException = e as? ExportException ?: return ExportError.UNKNOWN
    return when (exportException.errorCode / ERROR_GROUP) {
        IO_GROUP -> ExportError.MISSING_MEDIA
        DECODING_GROUP -> ExportError.DECODER
        ENCODING_GROUP, MUXING_GROUP -> ExportError.ENCODER
        else -> ExportError.UNKNOWN
    }
}

// A full disk surfaces as ENOSPC somewhere down the cause chain, usually an ErrnoException under an IOException.
private fun Throwable.isOutOfSpace(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it.message?.contains(NO_SPACE_ERRNO) == true }

// ExportException's codes come in thousands: 2xxx input I/O, 3xxx decoding, 4xxx encoding, 7xxx muxing.
private const val ERROR_GROUP = 1000
private const val IO_GROUP = 2
private const val DECODING_GROUP = 3
private const val ENCODING_GROUP = 4
private const val MUXING_GROUP = 7
private const val NO_SPACE_ERRNO = "ENOSPC"
private const val MAX_CAUSE_DEPTH = 10
