package app.memix.engine.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * Calls [onUnplugged] when the sound is about to move to the phone's speaker (wired headphones unplugged, a
 * Bluetooth headset gone), so the preview pauses instead of playing out loud. CompositionPlayer doesn't do this
 * itself (Media3 1.11.1). Register only while playing; both calls are main-thread only.
 */
internal class HeadphonesUnpluggedReceiver(private val context: Context, private val onUnplugged: () -> Unit) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onUnplugged()
        }
    }

    fun register() {
        if (registered) return
        // A protected system broadcast, so no exported or not-exported flag (Media3's own guidance).
        context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
