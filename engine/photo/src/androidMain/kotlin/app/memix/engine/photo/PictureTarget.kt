package app.memix.engine.photo

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where one picture's bytes are written, and what becomes of the file when the export ends. */
internal interface PictureTarget {
    /** A stream to write the encoded picture to; the caller closes it. Throws [IOException] when it can't be opened. */
    fun openStream(): OutputStream

    /** The write finished: the picture is made visible where it belongs. Disk work. */
    fun finish(): FinishedPicture

    /** The export failed or was cancelled: the partial file goes. Disk work. */
    fun discard()
}

/** A finished picture: its content URI (gallery) or absolute path (cache), and its size in bytes. */
internal class FinishedPicture(val location: String, val sizeBytes: Long)

/**
 * A picture in the gallery's "Memix" album (spec P2-08 → What the file is): a MediaStore item in `Pictures/Memix` inserted
 * pending, so no other app sees it until [finish]. No storage permission is needed for media the app adds itself.
 */
internal class GalleryPictureTarget private constructor(private val resolver: ContentResolver, private val uri: Uri) : PictureTarget {
    override fun openStream(): OutputStream = resolver.openOutputStream(uri, "w") ?: throw IOException("The gallery item can't be written")

    override fun finish(): FinishedPicture {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        return FinishedPicture(uri.toString(), size)
    }

    override fun discard() {
        resolver.delete(uri, null, null)
    }

    companion object {
        /** A new pending item named `Memix_<yyyyMMdd_HHmmss>.<jpg|png>`. */
        fun create(context: Context, format: PhotoExportFormat): GalleryPictureTarget {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileNameFor(format))
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, PhotoExportCatalog.ALBUM_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection(), values) ?: throw IOException("The gallery refused a new picture")
            return GalleryPictureTarget(resolver, uri)
        }

        /** Deletes this app's pending items in the album: pictures a stopped run never finished. */
        fun discardUnfinished(context: Context) {
            val resolver = context.contentResolver
            val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1 AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("${PhotoExportCatalog.ALBUM_PATH}%")
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val query = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(collection(), projection, query, null)
            } else {
                @Suppress("DEPRECATION")
                resolver.query(MediaStore.setIncludePending(collection()), projection, selection, args, null)
            }
            val ids = cursor?.use { rows -> buildList { while (rows.moveToNext()) add(rows.getLong(0)) } }.orEmpty()
            ids.forEach { id -> resolver.delete(Uri.withAppendedPath(collection(), id.toString()), null, null) }
        }

        private fun collection(): Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
}

/** A picture in one of the app's cache folders: the clipboard's copy, or the debug hook's file. */
internal class CacheFileTarget private constructor(private val file: File) : PictureTarget {
    override fun openStream(): OutputStream = file.outputStream()

    override fun finish(): FinishedPicture = FinishedPicture(file.path, file.length())

    override fun discard() {
        file.delete()
    }

    companion object {
        /** A new file in `cache/[folder]/`, named like the gallery's. */
        fun create(cacheDir: File, folder: String, format: PhotoExportFormat): CacheFileTarget {
            val directory = File(cacheDir, folder).apply { mkdirs() }
            return CacheFileTarget(File(directory, fileNameFor(format)))
        }

        /** Deletes every file in `cache/[folder]/`. */
        fun deleteAll(cacheDir: File, folder: String) {
            File(cacheDir, folder).listFiles()?.forEach { it.delete() }
        }
    }
}

private fun fileNameFor(format: PhotoExportFormat): String =
    "Memix_${SimpleDateFormat(NAME_TIME_FORMAT, Locale.US).format(Date())}.${format.fileExtension}"

private const val NAME_TIME_FORMAT = "yyyyMMdd_HHmmss"
