package app.memix.platform.services

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import app.memix.core.domain.Logger
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.PickedFile
import app.memix.core.model.project.MediaOrigin
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Picked media through the ContentResolver (photo picker and file chooser URIs, readable for as long as the app
 * runs), copies in the app's files directory (not the cache, which Android may clear when storage runs low).
 */
internal class AndroidMediaFiles(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : MediaFiles {
    private val resolver: ContentResolver get() = context.contentResolver
    private val filesDir: File get() = context.filesDir
    private val storageManager: StorageManager get() = context.getSystemService(StorageManager::class.java)

    override suspend fun describe(origin: MediaOrigin): PickedFile? = withContext(ioDispatcher) {
        val uri = origin.contentUri() ?: return@withContext null
        try {
            val mimeType = resolver.getType(uri)
            val (displayName, sizeBytes) = queryNameAndSize(uri)
            PickedFile(displayName, mimeType, sizeBytes, extensionFor(mimeType, displayName))
        } catch (e: RuntimeException) {
            // The provider is another app (the picker, or any file chooser source): a lost grant, an unknown URI or
            // a provider bug all mean the same here, so the item can't be read. Nothing in here suspends, so no
            // cancellation is caught by mistake.
            null
        }
    }

    override suspend fun copy(origin: MediaOrigin, destination: String, onBytesCopied: (Long) -> Unit): CopyResult =
        withContext(ioDispatcher) {
            val uri = origin.contentUri() ?: return@withContext CopyResult.UNREADABLE
            val target = File(filesDir, destination)
            val unfinished = File(target.path + UNFINISHED_SUFFIX)
            var complete = false
            try {
                target.parentFile?.mkdirs()
                val input = openOrNull(uri) ?: return@withContext CopyResult.UNREADABLE
                input.use { source -> FileOutputStream(unfinished).use { sink -> copyAndSync(source, sink, onBytesCopied) } }
                if (!unfinished.renameTo(target)) throw IOException("Couldn't rename the finished copy")
                complete = true
                CopyResult.COPIED
            } catch (e: IOException) {
                // A failed write with less than one buffer left on the disk counts as full too, in case a stream
                // wraps the error differently.
                if (e.isOutOfSpace() || filesDir.usableSpace < COPY_BUFFER_BYTES) {
                    CopyResult.STORAGE_FULL
                } else {
                    // Logged without the message, which can quote a path or URI.
                    logger.error(TAG, "Media copy failed: ${e::class.simpleName}")
                    CopyResult.UNREADABLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // A provider's stream failing in its own way; the same as a failed read to the user.
                logger.error(TAG, "Media copy failed: ${e::class.simpleName}")
                CopyResult.UNREADABLE
            } finally {
                if (!complete) unfinished.delete()
            }
        }

    override suspend fun delete(paths: List<String>) = withContext(ioDispatcher) {
        for (path in paths) {
            val file = File(filesDir, path)
            file.delete()
            // Only the project folder it sat in, never the media root above it.
            file.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
        }
    }

    override suspend fun listFolders(parent: String): List<String> = withContext(ioDispatcher) {
        File(filesDir, parent).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
    }

    override suspend fun deleteFolder(path: String) {
        withContext(ioDispatcher) { File(filesDir, path).deleteRecursively() }
    }

    override suspend fun deleteUnfinishedCopies(parent: String) = withContext(ioDispatcher) {
        File(filesDir, parent).walkTopDown()
            .filter { it.isFile && it.name.endsWith(UNFINISHED_SUFFIX) }
            .forEach { it.delete() }
    }

    override suspend fun allocatableBytes(): Long = withContext(ioDispatcher) {
        try {
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(filesDir))
        } catch (e: IOException) {
            // The volume can't report allocatable space; plain free space is the closest stand-in.
            filesDir.usableSpace
        }
    }

    override suspend fun reserveBytes(bytes: Long): Boolean = withContext(ioDispatcher) {
        try {
            storageManager.allocateBytes(storageManager.getUuidForPath(filesDir), bytes)
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return resolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            cursor.stringOrNull(OpenableColumns.DISPLAY_NAME) to cursor.longOrNull(OpenableColumns.SIZE)?.takeIf { it >= 0 }
        } ?: (null to null)
    }

    // Not suspending, so catching RuntimeException can't swallow a cancellation.
    private fun openOrNull(uri: Uri): InputStream? = try {
        resolver.openInputStream(uri)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    // Writes the whole stream, then flushes it to the disk before the rename, so a file under its final name is
    // complete even after a power cut. Checks for cancellation between reads.
    private suspend fun copyAndSync(source: InputStream, sink: FileOutputStream, onBytesCopied: (Long) -> Unit) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            copied += read
            onBytesCopied(copied)
        }
        sink.fd.sync()
    }

    private companion object {
        const val TAG = "MediaImport"
        const val UNFINISHED_SUFFIX = ".part"
        // Large enough that a 1 GB video takes about 4,000 reads, small enough to check for Cancel often.
        const val COPY_BUFFER_BYTES = 256 * 1024
    }
}

private fun MediaOrigin.contentUri(): Uri? = (this as? MediaOrigin.GalleryUri)?.let { Uri.parse(it.uri) }

private fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getLong(index)
}

// The extension the engine needs to recognise the file: from the MIME type, else from the name the source gave.
private fun extensionFor(mimeType: String?, displayName: String?): String? =
    mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?: displayName?.substringAfterLast('.', missingDelimiterValue = "")?.ifEmpty { null }

// Android reports a full disk as an IOException caused by ErrnoException(ENOSPC) (EDQUOT where quotas apply).
private fun IOException.isOutOfSpace(): Boolean {
    val errno = (cause as? ErrnoException)?.errno
    return errno == OsConstants.ENOSPC || errno == OsConstants.EDQUOT || message?.contains("ENOSPC") == true
}
