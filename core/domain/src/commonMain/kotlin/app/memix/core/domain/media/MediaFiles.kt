package app.memix.core.domain.media

import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin

/**
 * The phone's side of a media import: reads what the user picked and keeps the app's own copies. Implemented in
 * :platform:services.
 *
 * Every path is relative to the app's files directory (like `MediaRef.cachedCopyPath`); [MediaPaths] decides
 * where copies go. Every function runs its disk work on an I/O dispatcher, so it is safe to call from the main
 * thread. None of them throws for a missing or unreadable file; they report it in their result instead.
 */
interface MediaFiles {
    /** What the platform can tell about a picked item without reading it, or null when it can't be opened at all. */
    suspend fun describe(origin: MediaOrigin): PickedFile?

    /**
     * Copies a picked item to [destination]. The bytes go to a temporary file that is renamed to [destination] only
     * once complete, so a copy cut short (cancelled, app stopped) never sits under a real name. [onBytesCopied] gets
     * the bytes copied so far, many times a second, on the copying thread. Cancelling deletes the unfinished file.
     */
    suspend fun copy(origin: MediaOrigin, destination: String, onBytesCopied: (Long) -> Unit): CopyResult

    /**
     * Writes [bytes] to [destination] like [copy] does: under a temporary name, renamed only once complete, replacing
     * a file already there. Returns [CopyResult.COPIED], [CopyResult.STORAGE_FULL], or [CopyResult.UNREADABLE] when
     * it can't be written for another reason.
     */
    suspend fun write(bytes: ByteArray, destination: String): CopyResult

    /**
     * Makes [destination] a hard link to [source], both app files: instant and free, since the two names share one
     * file and deleting either leaves the other whole (a duplicated draft, P1-14). Returns false when the platform or
     * the file system refuses, or [source] is missing; then copy with [copyFile]. Never replaces an existing file.
     * Because a linked file belongs to two drafts, nothing may ever change a media copy in place: write a new file.
     */
    suspend fun link(source: String, destination: String): Boolean

    /**
     * Copies the app file [source] to [destination] the way [copy] copies a picked item: under a temporary name,
     * renamed only once complete, [onBytesCopied] many times a second on the copying thread, and the unfinished
     * file deleted on cancel. Returns [CopyResult.UNREADABLE] when [source] is missing or can't be read.
     */
    suspend fun copyFile(source: String, destination: String, onBytesCopied: (Long) -> Unit): CopyResult

    /** The size of the app file at [path] in bytes, or null when it isn't there. */
    suspend fun sizeOf(path: String): Long?

    /** The whole app file at [path] (a thumbnail), or null when it's missing or can't be read. For small files only. */
    suspend fun read(path: String): ByteArray?

    /** Deletes these files, then each folder they leave empty. Missing files are skipped. */
    suspend fun delete(paths: List<String>)

    /** Names of the folders directly inside [parent]; empty when it doesn't exist. */
    suspend fun listFolders(parent: String): List<String>

    /** Paths of the files directly inside [folder], in the same relative form; empty when it doesn't exist. */
    suspend fun listFiles(folder: String): List<String>

    /** Deletes a folder and everything in it. */
    suspend fun deleteFolder(path: String)

    /** Deletes every unfinished copy (see [copy]) anywhere inside [parent]. Only safe while nothing is copying. */
    suspend fun deleteUnfinishedCopies(parent: String)

    /** Bytes the app could still write, counting other apps' cache files the system would delete to make room. */
    suspend fun allocatableBytes(): Long

    /** Asks the system to make [bytes] free for the app, deleting other apps' cache files if needed. False if it can't. */
    suspend fun reserveBytes(bytes: Long): Boolean
}

/** What [MediaFiles.describe] found out about a picked item. Each value is null when the source doesn't say. */
data class PickedFile(
    /** The name the user sees in their gallery, for example "IMG_2041.MOV". Shown only on this phone, never logged. */
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    /** A file extension that matches the content, without the dot ("mp4", "heic"); the engine detects photos by it. */
    val extension: String?,
) {
    /** Video or photo by MIME type; null for anything else, which Memix can't use. */
    val kind: MediaKind?
        get() = when {
            mimeType == null -> null
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            else -> null
        }
}

/** How [MediaFiles.copy] ended, when it wasn't cancelled. */
enum class CopyResult {
    COPIED,

    /** The source couldn't be opened or read to the end: deleted, access lost, or a cloud item that didn't download. */
    UNREADABLE,

    /** The phone ran out of space; nothing was kept. */
    STORAGE_FULL,
}
