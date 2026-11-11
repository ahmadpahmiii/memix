package app.memix.feature.videoeditor

import app.memix.core.domain.catalog.LoadSoundTitlesUseCase
import app.memix.core.domain.sound.LoadStarterSoundsUseCase
import app.memix.core.domain.sound.LogSoundEventsUseCase
import app.memix.core.domain.sound.PlaceSoundInProjectUseCase
import app.memix.core.domain.sound.RecordRecentSoundUseCase

/**
 * The meme sound use cases the video editor needs (P1-08, P3-05), handed over together so its constructor stays readable.
 * The picker's own playing and downloading live in `:feature:sounds`; the editor only lands what it hands over.
 */
class EditorSounds(
    val loadStarterSounds: LoadStarterSoundsUseCase,
    val loadTitles: LoadSoundTitlesUseCase,
    val placeInProject: PlaceSoundInProjectUseCase,
    val logEvents: LogSoundEventsUseCase,
    val recordRecent: RecordRecentSoundUseCase,
)
