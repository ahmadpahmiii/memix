package app.memix.platform.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// TODO(P7-05): AVAudioApplication.requestRecordPermission and the Settings URL; until then recording is never allowed.
@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess = remember {
    object : MicrophoneAccess {
        override fun isGranted() = false

        override fun request(onAnswer: (Boolean) -> Unit) = onAnswer(false)

        override fun openSettings() = Unit
    }
}

// TODO(P7-02): UIApplication.idleTimerDisabled while a take records.
@Composable
actual fun KeepScreenOn(keepOn: Boolean) = Unit
