package app.memix.platform.services

import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFacts
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaInspector
import app.memix.core.domain.media.PickedFile
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin

// TODO(P7-08): PHPicker results copied into the app's files directory. Until then iOS has no picker, so nothing
// reaches these; they answer like an empty media folder.
internal class NoMediaFiles : MediaFiles {
    override suspend fun describe(origin: MediaOrigin): PickedFile? = null
    override suspend fun copy(origin: MediaOrigin, destination: String, onBytesCopied: (Long) -> Unit) = CopyResult.UNREADABLE
    override suspend fun delete(paths: List<String>) = Unit
    override suspend fun listFolders(parent: String): List<String> = emptyList()
    override suspend fun deleteFolder(path: String) = Unit
    override suspend fun deleteUnfinishedCopies(parent: String) = Unit
    override suspend fun allocatableBytes(): Long = 0
    override suspend fun reserveBytes(bytes: Long): Boolean = false
}

// TODO(P7-08): AVFoundation and ImageIO checks.
internal class NoMediaInspector : MediaInspector {
    override suspend fun inspect(path: String, kind: MediaKind): MediaFacts? = null
}
