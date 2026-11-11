package app.memix.core.domain.sound

/** Reads the phone's media volume. The platform answers; Android asks `AudioManager`. */
interface MediaVolume {
    /** True when the media volume is at zero, so a preview would play in silence. */
    fun isSilent(): Boolean
}

/**
 * Asks whether to say "Turn up your volume to hear it" before a preview starts: once per app session, and only when
 * the media volume is zero (spec P3-02 → States). A single, so "once" holds across screens.
 */
class LowVolumeWarningUseCase(private val volume: MediaVolume) {
    private var warned = false

    /** True once, the first time it is asked while the volume is zero. Main thread only. */
    fun isDue(): Boolean {
        if (warned || !volume.isSilent()) return false
        warned = true
        return true
    }
}
