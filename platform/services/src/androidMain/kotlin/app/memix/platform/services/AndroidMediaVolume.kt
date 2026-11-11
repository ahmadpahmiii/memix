package app.memix.platform.services

import android.content.Context
import android.media.AudioManager
import app.memix.core.domain.sound.MediaVolume

/** The music stream is the one previews play on, and the one the phone's volume keys control while media plays. */
internal class AndroidMediaVolume(context: Context) : MediaVolume {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)

    // No audio service (never on a phone) means nothing to warn about.
    override fun isSilent(): Boolean = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
}
