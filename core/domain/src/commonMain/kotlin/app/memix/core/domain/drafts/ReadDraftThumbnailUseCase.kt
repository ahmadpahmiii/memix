package app.memix.core.domain.drafts

import app.memix.core.domain.media.MediaFiles

/** A draft's thumbnail as JPEG bytes ([UpdateDraftThumbnailUseCase] writes it), or null when it's missing. Main-safe. */
class ReadDraftThumbnailUseCase(private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(thumbnailPath: String): ByteArray? = mediaFiles.read(thumbnailPath)
}
