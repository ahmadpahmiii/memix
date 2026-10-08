package app.memix.engine.video

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import app.memix.core.domain.Logger
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Android [PreviewSession]: the project's [CompositionPlan], built into the same Media3 composition as the
 * export, played by Media3's CompositionPlayer on the main thread. The Compose surface ([VideoPreviewSurface])
 * shows [player].
 *
 * Planning, file checks and building run on [ioDispatcher]; only `setComposition` runs on the main thread. A newer
 * [update] cancels a build that is still running, so quick edits only render the last project.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal class Media3PreviewSession(
    private val context: Context,
    private val sourceResolver: MediaSourceResolver,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    project: Project,
) : PreviewSession {
    // CompositionPlayer must be used from the thread that created it: the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val projects = MutableStateFlow(project)

    private val mutablePlayback = MutableStateFlow(PreviewPlayback.Preparing)
    override val playback: StateFlow<PreviewPlayback> = mutablePlayback.asStateFlow()

    private val mutablePosition = MutableStateFlow(0L)
    override val positionUs: StateFlow<Long> = mutablePosition.asStateFlow()

    // Created only once a composition is ready: a CompositionPlayer released before it ever got one keeps its
    // playback thread running (Media3 1.11.1).
    private val mutablePlayer = MutableStateFlow<CompositionPlayer?>(null)

    /** What the preview surface shows; null until the first composition is ready, and again after [close]. */
    val player: StateFlow<CompositionPlayer?> = mutablePlayer.asStateFlow()

    private var lengthUs = 0L
    private var closed = false
    private val positionTicker = FrameTicker(::publishPlayerPosition)
    private val headphonesUnplugged = HeadphonesUnpluggedReceiver(context, onUnplugged = ::pause)
    private val playSpan = PlaySpanLog(logger)

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> publish { it.copy(status = PreviewStatus.READY) }
                Player.STATE_ENDED -> stopAtEnd()
                Player.STATE_IDLE, Player.STATE_BUFFERING -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = onPlayingChanged(isPlaying)

        // Audio focus loss, the end, or our own play and pause.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publish { it.copy(isPlaying = playWhenReady) }

        // Media3 would resume by itself after a call; the editor stays paused instead (spec P1-04 → Playback).
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) pause()
        }

        override fun onRenderedFirstFrame() = publish { it.copy(firstFrameShown = true) }

        override fun onPlayerError(error: PlaybackException) = fail("playback error ${error.errorCodeName}", error)
    }

    init {
        scope.launch {
            projects.collectLatest { next -> show(withContext(ioDispatcher) { prepare(next) }) }
        }
    }

    override fun play() {
        if (closed) return
        publish { it.copy(isPlaying = true) }
        mutablePlayer.value?.play()
    }

    override fun pause() {
        if (closed) return
        publish { it.copy(isPlaying = false) }
        mutablePlayer.value?.pause()
    }

    override fun seekTo(positionUs: Long) {
        if (closed) return
        val clampedUs = positionUs.coerceIn(0, lengthUs)
        mutablePosition.value = clampedUs
        // Before the first composition there is nothing to seek in; it starts at this position instead.
        mutablePlayer.value?.seekTo(clampedUs / MICROS_PER_MILLI)
    }

    override fun update(project: Project) {
        if (!closed) projects.value = project
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        positionTicker.stop()
        headphonesUnplugged.unregister()
        mutablePlayer.value?.release()
        mutablePlayer.value = null
    }

    // Runs on ioDispatcher.
    private fun prepare(project: Project): PreparedPreview {
        val plan = when (val planned = CompositionPlanner.plan(project)) {
            is PlanResult.Unplayable -> return PreparedPreview.Failed("the project can't be played: ${planned.problem}", cause = null)
            is PlanResult.Ready -> planned.plan
        }
        val resolved = sourceResolver.resolve(plan)
        // Each missing file, and each file whose length isn't known, plays as black and silence.
        val unplayable = resolved.missingItemIds.toSet() + plan.itemsWithoutLength()
        val playablePlan = plan.withGapsFor(unplayable)
        return try {
            val composition = Media3CompositionBuilder(playablePlan, resolved.uriByMedia, ExportSettings.DEFAULT_FRAME_RATE).build()
            PreparedPreview.Ready(composition, plan.durationUs, missingMedia = unplayable.isNotEmpty())
        } catch (e: RuntimeException) {
            // Media3's own argument checks.
            PreparedPreview.Failed("the composition couldn't be built", e)
        }
    }

    private fun show(prepared: PreparedPreview) {
        if (closed) return
        when (prepared) {
            is PreparedPreview.Failed -> fail(prepared.reason, prepared.cause)
            is PreparedPreview.Ready -> showComposition(prepared)
        }
    }

    private fun showComposition(prepared: PreparedPreview.Ready) {
        lengthUs = prepared.lengthUs
        val startUs = mutablePosition.value.coerceIn(0, lengthUs)
        val player = mutablePlayer.value ?: newPlayer().also { mutablePlayer.value = it }
        try {
            // Rebuilds every decoder (Media3 1.11.1), so it costs a re-prepare; the position and play state carry on.
            player.setComposition(prepared.composition, startUs / MICROS_PER_MILLI)
            // Needed the first time and after an error; later compositions prepare by themselves.
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        } catch (e: RuntimeException) {
            return fail("the player refused the composition", e)
        }
        mutablePosition.value = startUs
        publish { it.copy(missingMedia = prepared.missingMedia) }
    }

    private fun newPlayer(): CompositionPlayer {
        val player = CompositionPlayer.Builder(context)
            // Pauses for calls and other apps' sound (handled by Media3), like every media player.
            .setAudioAttributes(MEDIA_AUDIO, /* handleAudioFocus= */ true)
            .build()
        player.addListener(playerListener)
        player.addAnalyticsListener(playSpan)
        player.playWhenReady = mutablePlayback.value.isPlaying
        return player
    }

    private fun fail(reason: String, cause: Throwable?) {
        logger.error(TAG, "Preview stopped: $reason", cause)
        mutablePlayer.value?.pause()
        publish { it.copy(status = PreviewStatus.FAILED, isPlaying = false) }
    }

    // The end isn't a loop: playback stops with the last frame on screen and the time showing the full length.
    private fun stopAtEnd() {
        mutablePlayer.value?.pause()
        publishPlayerPosition()
        publish { it.copy(isPlaying = false) }
    }

    private fun publishPlayerPosition() {
        val player = mutablePlayer.value ?: return
        // At the end Media3 reports whole milliseconds, which can fall short of the project's length.
        val playerUs = if (player.playbackState == Player.STATE_ENDED) lengthUs else player.currentPosition * MICROS_PER_MILLI
        mutablePosition.value = playerUs.coerceIn(0, lengthUs)
    }

    private fun onPlayingChanged(isPlaying: Boolean) {
        val player = mutablePlayer.value ?: return
        if (isPlaying) {
            positionTicker.start()
            headphonesUnplugged.register()
            playSpan.start(player.currentPosition)
        } else {
            positionTicker.stop()
            headphonesUnplugged.unregister()
            publishPlayerPosition()
            playSpan.stop(player.currentPosition)
        }
    }

    private fun publish(change: (PreviewPlayback) -> PreviewPlayback) = mutablePlayback.update(change)

    private sealed interface PreparedPreview {
        class Ready(val composition: Composition, val lengthUs: Long, val missingMedia: Boolean) : PreparedPreview

        class Failed(val reason: String, val cause: Throwable?) : PreparedPreview
    }

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
        val MEDIA_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}

private const val TAG = "MemixPreview"

/** CompositionPlayer needs each file's length up front; P1-02 measures it at import and the editor fills older drafts. */
private fun CompositionPlan.itemsWithoutLength(): Set<String> =
    mediaItems.filter { (_, media) -> media.kind != MediaKind.IMAGE && media.durationUs == null }.map { it.first }.toSet()

/**
 * Logs one line per stretch of playback (tag MemixPreview): how much video played, how long it took, how many frames
 * the video decoder put on screen and how many it dropped. The P1-04 frame-rate check reads it on the phone.
 */
@OptIn(UnstableApi::class)
private class PlaySpanLog(private val logger: Logger) : AnalyticsListener {
    // Media3 updates these counters on its playback thread; the newest player's video decoder reports here.
    private var counters: DecoderCounters? = null
    private var startedAtMs = 0L
    private var startPositionMs = 0L
    private var renderedAtStart = 0
    private var droppedAtStart = 0

    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
        counters = decoderCounters
    }

    fun start(positionMs: Long) {
        startedAtMs = SystemClock.elapsedRealtime()
        startPositionMs = positionMs
        val current = counters?.apply { ensureUpdated() }
        renderedAtStart = current?.renderedOutputBufferCount ?: 0
        droppedAtStart = current?.droppedBufferCount ?: 0
    }

    fun stop(positionMs: Long) {
        val wallMs = SystemClock.elapsedRealtime() - startedAtMs
        val current = counters?.apply { ensureUpdated() } ?: return
        val shown = current.renderedOutputBufferCount - renderedAtStart
        val dropped = current.droppedBufferCount - droppedAtStart
        if (wallMs <= 0 || shown < 0) return
        // Tenths of a frame per second, so 29.97 doesn't print as 29.
        val tenthsPerSecond = shown * TENTHS_PER_SECOND_IN_MILLIS / wallMs
        val framesPerSecond = "${tenthsPerSecond / TENTHS}.${tenthsPerSecond % TENTHS}"
        logger.debug(
            TAG,
            "Played ${positionMs - startPositionMs} ms of video in $wallMs ms: $shown frames shown ($framesPerSecond fps), $dropped dropped",
        )
    }

    private companion object {
        const val TENTHS = 10L
        const val TENTHS_PER_SECOND_IN_MILLIS = 10_000L
    }
}
