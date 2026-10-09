package app.memix.core.domain.video

/** Opens the reader behind the timeline's thumbnail strips (see [ThumbnailReader]). The caller closes it. */
class StartThumbnailsUseCase(private val videoEngine: VideoEngine) {
    operator fun invoke(): ThumbnailReader = videoEngine.openThumbnails()
}
