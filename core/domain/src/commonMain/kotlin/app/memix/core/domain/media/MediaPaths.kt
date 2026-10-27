package app.memix.core.domain.media

import app.memix.core.model.project.MediaKind
import kotlin.uuid.Uuid

/**
 * Where the app keeps its copies of picked media, relative to the files directory: one folder per project,
 * `media/<project id>/<random id>.<extension>`. One folder per project lets a draft's media go with it, and
 * lets the launch cleanup find copies whose project was never saved.
 */
object MediaPaths {
    /** Excluded from cloud backup (Android's 25 MB backup quota); see androidApp's data_extraction_rules.xml. */
    const val ROOT = "media"

    fun projectFolder(projectId: String): String = "$ROOT/$projectId"

    /**
     * A new, unused path for a copy in [projectId]'s folder. [extension] comes from the source's MIME type or name;
     * anything that isn't a short run of letters and digits is replaced by the kind's usual one, so a name from
     * another app can never steer the path.
     */
    internal fun newCopy(projectId: String, kind: MediaKind, extension: String?): String {
        val safeExtension = extension?.lowercase()?.takeIf { SAFE_EXTENSION.matches(it) } ?: defaultExtension(kind)
        return "${projectFolder(projectId)}/${Uuid.random()}.$safeExtension"
    }

    /**
     * Where a draft's thumbnail goes: named after the cover's file and trim-in, so a thumbnail path that still matches
     * says the cover hasn't changed (P1-14). A duplicate keeps its media's file names, so its copied thumbnail matches
     * too. [coverFile] is the cover's own path; anything outside letters, digits, `-` and `_` is dropped from its name.
     */
    internal fun thumbnail(projectId: String, coverFile: String, trimInUs: Long): String {
        val coverName = coverFile.substringAfterLast('/').substringBeforeLast('.').filter { it.isSafeNameChar() }.take(MAX_COVER_NAME)
        return "${projectFolder(projectId)}/$THUMBNAIL_PREFIX$coverName-$trimInUs.jpg"
    }

    /** The same file name in [projectId]'s folder: where a duplicate's copy of [path] goes. */
    internal fun inFolderOf(projectId: String, path: String): String = "${projectFolder(projectId)}/${path.substringAfterLast('/')}"

    /** Whether [path] is inside [projectId]'s own folder (a copy the draft owns, not a shared catalog file). */
    internal fun isInFolderOf(projectId: String, path: String): Boolean = path.startsWith("${projectFolder(projectId)}/")

    private fun Char.isSafeNameChar(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '_'

    private const val THUMBNAIL_PREFIX = "thumb-"
    private const val MAX_COVER_NAME = 40

    // The engine finds photos by the file extension, so every copy needs one that matches its kind.
    private fun defaultExtension(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> "mp4"
        MediaKind.IMAGE -> "jpg"
        MediaKind.AUDIO -> "m4a"
    }

    private val SAFE_EXTENSION = Regex("[a-z0-9]{1,8}")
}
