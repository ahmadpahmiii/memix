package app.memix.engine.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.memix.core.domain.Logger
import app.memix.core.domain.sound.SoundPlayer
import app.memix.core.model.project.MediaRef
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays single sounds with one ExoPlayer (the sounds sheet's previews, P1-08). The player is made on the first
 * [play] and released by [close]; it takes media audio focus, so a call or another app pauses it, and it stops
 * instead of resuming later. Main thread only, as ExoPlayer is.
 */
internal class Media3SoundPlayer(
    private val context: Context,
    private val logger: Logger,
) : SoundPlayer {
    private val mutablePlaying = MutableStateFlow<MediaRef?>(null)
    override val playing: StateFlow<MediaRef?> = mutablePlaying.asStateFlow()

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) mutablePlaying.value = null
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Audio focus lost (a call, another app): the row goes back to idle rather than waiting to resume.
            if (!playWhenReady) mutablePlaying.value = null
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(TAG, "Sound preview failed: ${error.errorCodeName}")
            mutablePlaying.value = null
        }
    }

    override fun play(sound: MediaRef) {
        val path = sound.cachedCopyPath ?: return
        val exoPlayer = player ?: newPlayer().also { player = it }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(context.filesDir, path))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        mutablePlaying.value = sound
    }

    override fun stop() {
        player?.stop()
        mutablePlaying.value = null
    }

    override fun close() {
        player?.removeListener(listener)
        player?.release()
        player = null
        mutablePlaying.value = null
    }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { it.addListener(listener) }

    private companion object {
        const val TAG = "MemixSounds"
    }
}
