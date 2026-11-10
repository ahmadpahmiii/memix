package app.memix.core.domain.sticker

import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import kotlin.time.Clock

/** Writes a finished sticker's PNG into the [StickerStore] under a new name (the file is complete before it has its name). */
class SaveStickerUseCase(private val mediaFiles: MediaFiles, private val clock: Clock) {
    suspend operator fun invoke(png: ByteArray): StickerSave {
        val path = StickerStore.newPath(clock.now().toEpochMilliseconds())
        return when (mediaFiles.write(png, path)) {
            CopyResult.COPIED -> StickerSave.Saved(path)
            CopyResult.STORAGE_FULL, CopyResult.UNREADABLE -> StickerSave.Failed
        }
    }
}

sealed interface StickerSave {
    data class Saved(val path: String) : StickerSave

    data object Failed : StickerSave
}
