package app.memix.core.domain.video

import app.memix.core.domain.Outcome
import app.memix.core.model.project.MediaRef

/** Reads the peaks of a sound for the timeline's waveform (see [VideoEngine.waveform]); a failure says whether the file is missing. Main-safe. */
class ReadWaveformUseCase(private val videoEngine: VideoEngine) {
    suspend operator fun invoke(source: MediaRef): Outcome<AudioPeaks> = videoEngine.waveform(source)
}
