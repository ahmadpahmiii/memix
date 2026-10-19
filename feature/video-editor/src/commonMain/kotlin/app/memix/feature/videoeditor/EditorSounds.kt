package app.memix.feature.videoeditor

import app.memix.core.domain.sound.LoadStarterSoundsUseCase
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.OpenSoundPlayerUseCase
import app.memix.core.domain.sound.PrepareSoundUseCase

/** The meme sound use cases the video editor needs (P1-08), handed over together so its constructor stays readable. */
class EditorSounds(
    val loadStarterSounds: LoadStarterSoundsUseCase,
    val prepareSound: PrepareSoundUseCase,
    val openSoundPlayer: OpenSoundPlayerUseCase,
    val logEvents: LogSoundEventsUseCase,
)
