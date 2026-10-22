package app.memix.core.domain.video

import app.memix.core.model.project.PixelSize

/** How [VideoEngine.export] renders, and where the file goes. P1-13 adds the watermark. */
data class ExportSettings(
    /** Frames per second. Photos render at this rate; sources with more frames drop down to it. */
    val frameRate: Int = DEFAULT_FRAME_RATE,
    /**
     * The output's shorter side in pixels (720 for 720p), when it's smaller than the canvas's; null exports at the
     * canvas size. Captions are laid out at the canvas size either way. Ignored when [frameSize] is set (the sheet's
     * exports); the debug export hook sets it.
     */
    val shortSidePx: Int? = null,
    /** The exact frame size from [ExportCapabilities] (aligned to the encoder), in the canvas's orientation. */
    val frameSize: PixelSize? = null,
    val codec: VideoCodec = VideoCodec.H264,
    /** The video bitrate, bits per second (VBR); null lets the encoder pick. */
    val videoBitrateBps: Int? = null,
    val destination: ExportDestination = ExportDestination.APP_CACHE,
) {
    init {
        require(frameRate > 0) { "Frame rate must be positive: $frameRate" }
        require(shortSidePx == null || shortSidePx > 0) { "Short side must be positive: $shortSidePx" }
    }

    companion object {
        const val DEFAULT_FRAME_RATE = 30
    }
}

/** Where an export's file goes. */
enum class ExportDestination {
    /** The phone's gallery ("Movies/Memix"), visible to every app: the user's exports. */
    GALLERY,

    /** The app's cache (`cache/exports/`), for the debug export hook's hand checks. */
    APP_CACHE,
}

/** A finished export. */
data class ExportedVideo(
    /** Where the file is: a content URI in the gallery, or an absolute path in the app's cache. */
    val location: String,
    val durationUs: Long,
    val sizeBytes: Long,
    /** False when nothing in the project is heard, so the file has no audio track. */
    val hasAudio: Boolean,
    /** The encoder's average video bitrate, bits per second, when it reported one (for the estimate log). */
    val averageVideoBitrateBps: Int? = null,
)

/** What [VideoEngine.export] ends with. */
sealed interface ExportOutcome {
    data class Done(val video: ExportedVideo) : ExportOutcome

    data class Failed(val error: ExportError) : ExportOutcome
}

/** Why an export failed; also `export_fail.error_code` (spec P1-12 → Analytics). */
enum class ExportError(val eventValue: String) {
    /** The phone ran out of space while writing. */
    STORAGE_FULL("storage_full"),

    /** An encoder or muxer error, including a size the encoder refused. */
    ENCODER("encoder"),

    /** A clip's file that was there at the start couldn't be opened or read during the export. */
    MISSING_MEDIA("missing_media"),

    /** A clip's video or audio couldn't be decoded. */
    DECODER("decoder"),

    /** Android stopped the export (its time limit, or it refused to start the service). */
    SYSTEM_STOPPED("system_stopped"),

    /** The app was killed during an export for a reason other than the user's stop; found at the next start. */
    PROCESS_DEATH("process_death"),

    UNKNOWN("unknown"),
}
