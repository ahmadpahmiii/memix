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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays single sounds with one ExoPlayer (the sounds sheet's previews, P1-08; pause, resume and the position for the
 * Sounds screen's waveform, P3-03). The player is made on the first [warmUp] or [play] and released by [close]; it
 * takes media audio focus, so a call or another app pauses it, and it stops instead of resuming later. Only the
 * caller's own [pause] can be resumed. Main thread only, as ExoPlayer is.
 */
internal class Media3SoundPlayer(
    private val context: Context,
    private val logger: Logger,
) : SoundPlayer {
    private val mutablePlaying = MutableStateFlow<MediaRef?>(null)
    override val playing: StateFlow<MediaRef?> = mutablePlaying.asStateFlow()
    private val mutablePaused = MutableStateFlow<MediaRef?>(null)
    override val paused: StateFlow<MediaRef?> = mutablePaused.asStateFlow()
    private val mutableFailures = MutableSharedFlow<MediaRef>(extraBufferCapacity = 1)
    override val failures: Flow<MediaRef> = mutableFailures.asSharedFlow()

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) clearState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Audio focus lost (a call, another app): the row goes back to idle rather than waiting to resume.
            // Our own pause() is a user request and keeps its sound in `paused`.
            if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) clearState()
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(TAG, "Sound preview failed: ${error.errorCodeName}")
            (mutablePlaying.value ?: mutablePaused.value)?.let { mutableFailures.tryEmit(it) }
            clearState()
        }
    }

    override fun warmUp() {
        readyPlayer()
    }

    override fun play(sound: MediaRef) {
        val path = sound.cachedCopyPath ?: return
        val exoPlayer = readyPlayer()
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(context.filesDir, path))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        mutablePaused.value = null
        mutablePlaying.value = sound
    }

    override fun pause() {
        val sound = mutablePlaying.value ?: return
        player?.pause()
        // Paused first, so a reader never sees the sound in neither.
        mutablePaused.value = sound
        mutablePlaying.value = null
    }

    override fun resume() {
        val sound = mutablePaused.value ?: return
        player?.play()
        mutablePaused.value = null
        mutablePlaying.value = sound
    }

    override fun stop() {
        player?.stop()
        clearState()
    }

    override fun positionUs(): Long {
        if (mutablePlaying.value == null && mutablePaused.value == null) return 0L
        return (player?.currentPosition ?: 0L) * MICROS_PER_MILLI
    }

    override fun close() {
        player?.removeListener(listener)
        player?.release()
        player = null
        clearState()
    }

    private fun clearState() {
        mutablePlaying.value = null
        mutablePaused.value = null
    }

    private fun readyPlayer(): ExoPlayer = player ?: newPlayer().also { player = it }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { it.addListener(listener) }

    private companion object {
        const val TAG = "MemixSounds"
        const val MICROS_PER_MILLI = 1_000L
    }
}
