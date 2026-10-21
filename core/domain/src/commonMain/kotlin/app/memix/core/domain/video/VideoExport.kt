package app.memix.core.domain.video

/** How [VideoEngine.export] renders. P1-12 adds the resolution choice, P1-13 the watermark. */
data class ExportSettings(
    /** Frames per second. Photos render at this rate; sources with more frames drop down to it. */
    val frameRate: Int = DEFAULT_FRAME_RATE,
    /**
     * The output's shorter side in pixels (720 for 720p), when it's smaller than the canvas's; null exports at the
     * canvas size. Captions are laid out at the canvas size either way. Only the debug export hook sets it until P1-12
     * adds the resolution choice.
     */
    val shortSidePx: Int? = null,
) {
    init {
        require(frameRate > 0) { "Frame rate must be positive: $frameRate" }
        require(shortSidePx == null || shortSidePx > 0) { "Short side must be positive: $shortSidePx" }
    }

    companion object {
        const val DEFAULT_FRAME_RATE = 30
    }
}

/** A finished export. */
data class ExportedVideo(
    /** Absolute path of the MP4 in the app's cache. The caller saves or shares it, then deletes it. */
    val path: String,
    val durationUs: Long,
    val sizeBytes: Long,
    /** False when nothing in the project is heard, so the file has no audio track. */
    val hasAudio: Boolean,
)
