package app.memix.feature.videoeditor

import app.memix.core.domain.voiceover.DiscardVoiceTakeUseCase
import app.memix.core.domain.voiceover.MicrophoneAskedUseCase
import app.memix.core.domain.voiceover.PlaceVoiceTakeUseCase
import app.memix.core.domain.voiceover.StartVoiceTakeUseCase
import app.memix.core.domain.voiceover.VoiceRouteUseCase

/** The voiceover use cases the video editor needs (P3-08), handed over together so its constructor stays readable. */
class EditorVoiceover(
    val startTake: StartVoiceTakeUseCase,
    val discardTake: DiscardVoiceTakeUseCase,
    val route: VoiceRouteUseCase,
    val microphoneAsked: MicrophoneAskedUseCase,
    val placeTake: PlaceVoiceTakeUseCase,
)

/**
 * What the screen can ask of the phone's microphone permission (spec P3-08 → Permission). The composition root fills it
 * from the platform; the editor never sees the platform class.
 */
class MicrophoneHooks(
    val isGranted: () -> Boolean,
    val request: (onAnswer: (granted: Boolean) -> Unit) -> Unit,
    val openSettings: () -> Unit,
)
