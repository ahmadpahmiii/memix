package app.memix.core.domain.sticker

import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaInspector
import app.memix.core.domain.media.MediaPaths
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlin.uuid.Uuid

/** The stickers in the [StickerStore], newest first; the paths are relative to the files directory. Main-safe. */
class ListStickersUseCase(private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(): List<String> =
        mediaFiles.listFiles(StickerStore.ROOT)
            .filter { it.endsWith(".png") }
            .sortedWith(compareByDescending<String> { StickerStore.savedAt(it) }.thenByDescending { it })
}

/**
 * Removes a sticker from the store. A sticker already placed in a project keeps working: placing copies the file into the project's
 * folder (spec docs/ux/specs/P2-14-sticker-picker.md → Delete), so this never touches a draft.
 */
class DeleteStickerUseCase(private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(path: String) {
        // Only the store's own files: a path from anywhere else is never deleted here.
        if (path.startsWith("${StickerStore.ROOT}/")) mediaFiles.delete(listOf(path))
    }
}

/**
 * Copies a store sticker into [projectId]'s media folder and measures it, so the layer has its own file and a real shape (an
 * unmeasured sticker would draw as a square). Never changes the store's file.
 */
class PlaceStickerUseCase(private val mediaFiles: MediaFiles, private val inspector: MediaInspector) {
    suspend operator fun invoke(projectId: String, storePath: String): StickerPlacement {
        if (!storePath.startsWith("${StickerStore.ROOT}/")) return StickerPlacement.Missing
        val destination = "${MediaPaths.projectFolder(projectId)}/${Uuid.random()}.png"
        when (mediaFiles.copyFile(storePath, destination) {}) {
            CopyResult.COPIED -> Unit
            CopyResult.UNREADABLE -> return StickerPlacement.Missing
            CopyResult.STORAGE_FULL -> return StickerPlacement.NoSpace
        }
        val size = inspector.inspect(destination, MediaKind.IMAGE)?.pixelSize
        if (size == null) {
            mediaFiles.delete(listOf(destination))
            return StickerPlacement.Missing
        }
        val name = storePath.substringAfterLast('/')
        return StickerPlacement.Placed(MediaRef(MediaOrigin.CatalogItem("my-sticker/$name"), MediaKind.IMAGE, cachedCopyPath = destination, pixelSize = size))
    }
}

sealed interface StickerPlacement {
    data class Placed(val media: MediaRef) : StickerPlacement

    /** The store's file is gone or can't be read. */
    data object Missing : StickerPlacement

    data object NoSpace : StickerPlacement
}
