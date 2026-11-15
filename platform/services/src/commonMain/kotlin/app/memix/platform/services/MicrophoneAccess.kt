package app.memix.platform.services

import androidx.compose.runtime.Composable

/**
 * The phone's microphone permission as the voiceover panel uses it (spec P3-08 → Permission). Remember it in the
 * composition root and hand it to the editor; the editor decides when to ask (once, at the first Record tap).
 */
interface MicrophoneAccess {
    /** Whether Memix may record right now; read again whenever the app comes back to the screen. */
    fun isGranted(): Boolean

    /** Shows Android's own dialog; [onAnswer] gets whether the microphone is allowed once it closes. Does nothing when it already is. */
    fun request(onAnswer: (granted: Boolean) -> Unit)

    /** Opens Memix's page in the phone's settings, where the microphone can be turned on. */
    fun openSettings()
}

@Composable
expect fun rememberMicrophoneAccess(): MicrophoneAccess

/** Keeps the screen on while [keepOn] is true and this is in the composition (a take records without touches). */
@Composable
expect fun KeepScreenOn(keepOn: Boolean)
