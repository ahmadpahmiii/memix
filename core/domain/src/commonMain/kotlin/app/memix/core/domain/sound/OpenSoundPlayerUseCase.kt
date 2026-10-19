package app.memix.core.domain.sound

import app.memix.core.domain.video.VideoEngine

/** Opens the player behind the sheet's play buttons. The caller closes it. */
class OpenSoundPlayerUseCase(private val videoEngine: VideoEngine) {
    operator fun invoke(): SoundPlayer = videoEngine.openSoundPlayer()
}
