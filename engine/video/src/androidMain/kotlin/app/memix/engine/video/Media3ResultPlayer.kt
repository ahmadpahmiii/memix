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
import app.memix.core.domain.video.ResultPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays the exported file once with sound (the Exported screen, P1-12). The file is prepared paused, so its first frame
 * shows behind the play disc; at the end it goes back to the start, paused. Takes media audio focus, so a call pauses it.
 * Main thread only, as ExoPlayer is.
 */
internal class Media3ResultPlayer(context: Context, location: String, private val logger: Logger) : ResultPlayer {
    private val mutablePlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    /** The player the surface draws; null once closed. */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutablePlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            player.pause()
            player.seekTo(0)
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(TAG, "The exported video couldn't play: ${error.errorCodeName}")
            mutablePlaying.value = false
        }
    }

    init {
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(location)))
        player.prepare()
    }

    override fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun pause() = player.pause()

    override fun close() {
        player.removeListener(listener)
        player.release()
        mutablePlaying.value = false
    }

    private companion object {
        const val TAG = "MemixExport"
    }
}
