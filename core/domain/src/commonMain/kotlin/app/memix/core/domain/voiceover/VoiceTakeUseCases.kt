package app.memix.core.domain.voiceover

import app.memix.core.domain.Logger
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.MediaOrigin

/** What [StartVoiceTakeUseCase] came to. */
sealed interface StartTakeResult {
    class Started(val take: VoiceTake) : StartTakeResult

    /** Less than the import headroom (100 MB, spec P3-08 → Phone storage low) is free: "Not enough space to record." */
    data object NoSpace : StartTakeResult

    data object Busy : StartTakeResult

    data object PermissionMissing : StartTakeResult

    data object Failed : StartTakeResult
}

/**
 * Opens a take that writes straight into [projectId]'s own media folder (spec P3-08 → Capture): the file is a new one
 * (media copies are never changed in place, P1-14), so it is already where a landed clip belongs and nothing is copied.
 * A take that doesn't land is deleted by [DiscardVoiceTakeUseCase]; one lost with the process is a half file the next
 * start's [LeftoverMediaCleaner] removes.
 */
class StartVoiceTakeUseCase(
    private val recorder: VoiceRecorder,
    private val mediaFiles: MediaFiles,
    private val leftoverCleaner: LeftoverMediaCleaner,
) {
    suspend operator fun invoke(projectId: String): StartTakeResult {
        if (mediaFiles.allocatableBytes() < ImportMediaUseCase.HEADROOM_BYTES) return StartTakeResult.NoSpace
        // The launch cleanup deletes folders and copies no saved draft uses, which a new project's take is until its first save.
        leftoverCleaner.awaitDone()
        val destination = MediaPaths.newCopy(projectId, MediaKind.AUDIO, "m4a")
        return when (val opening = recorder.open(destination)) {
            is VoiceOpening.Opened -> StartTakeResult.Started(opening.take)
            VoiceOpening.Busy -> StartTakeResult.Busy
            VoiceOpening.PermissionMissing -> StartTakeResult.PermissionMissing
            VoiceOpening.Failed -> StartTakeResult.Failed
        }
    }
}

/** The take's file as a [MediaRef] for the project: the app's own recording, marked as a voiceover. */
fun RecordedTake.toMediaRef(): MediaRef? {
    val file = path ?: return null
    return MediaRef(MediaOrigin.Recording, MediaKind.AUDIO, cachedCopyPath = file, durationUs = durationUs, hasAudio = true)
}

/** Deletes the file of a take that didn't land (too short, silent, cancelled, or the edit changed nothing). */
class DiscardVoiceTakeUseCase(private val mediaFiles: MediaFiles) {
    suspend operator fun invoke(take: RecordedTake) {
        take.path?.let { mediaFiles.delete(listOf(it)) }
    }
}

/** Where the project's sound goes now ([VoiceRoute]), for the panel's note and for muting the preview. */
class VoiceRouteUseCase(private val recorder: VoiceRecorder) {
    operator fun invoke(): VoiceRoute = recorder.route()
}

/** The microphone question the panel asks (spec P3-08 → Permission): whether Android's dialog was already answered "No". */
class MicrophoneAskedUseCase(private val preferences: VoiceoverPreferences) {
    suspend fun wasDenied(): Boolean = preferences.microphoneWasDenied()

    suspend fun markDenied() = preferences.markMicrophoneDenied()
}

/**
 * Places a finished take on the timeline from the preview's clock samples ([placeTake]) with the hand-check correction of
 * [VoiceoverTuning] applied, and in hand-check builds writes the line the clap test reads (tag `MemixVoiceover`).
 */
class PlaceVoiceTakeUseCase(private val tuning: VoiceoverTuning, private val logger: Logger) {
    operator fun invoke(take: RecordedTake, clocks: List<ClockSample>, playheadUs: Long): TakePlacement {
        val extraUs = tuning.extraOffsetUs
        val placement = placeTake(take.timing, clocks, playheadUs, extraUs, take.primingUs)
        if (tuning.logOffsets) logger.debug(LOG_TAG, syncReportLine(take.timing, clocks, playheadUs, extraUs, take.primingUs, placement))
        return placement
    }

    private companion object {
        const val LOG_TAG = "MemixVoiceover"
    }
}
