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
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.VideoTimeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
 *
 * An edit never blanks the picture: from the second composition on, CompositionPlayer keeps its video graph and
 * output surface and only rebuilds the sequence players, so the surface keeps its last frame until the edited
 * project's first frame replaces it (Media3 1.11.1; TECHNICAL_DESIGN → Preview → Edits).
 *
 * While a trim handle is dragged ([showTrimFrame]) the player shows a one-clip composition of that clip's whole
 * source instead, built once per drag; each drag frame is a scrubbing seek in it. [positionUs] keeps the project's
 * time throughout, and seeks and play meant for the project wait until its composition is back on the player.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal class Media3PreviewSession(
    private val context: Context,
    private val sourceResolver: MediaSourceResolver,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    project: Project,
    /** Hand-check builds: log where each sound is heard in the mix ([SoundOnsetLog]). */
    private val measureSoundOnsets: Boolean = false,
) : PreviewSession {
    // CompositionPlayer must be used from the thread that created it: the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val projects = MutableStateFlow(project)

    // The clip whose source a trim drag shows instead of the project: null when no drag is on. Normalized to its
    // whole source, so it stays equal for the whole drag and the composition is built once.
    private val trimClip = MutableStateFlow<MediaClip?>(null)
    private var trimSourceUs = 0L
    private var playerShowsTrim = false

    // isCatchingUp clears only on the first frame of the newest update, not of an older one still on its way.
    private var updateCount = 0
    private var shownUpdate = 0

    private val mutablePlayback = MutableStateFlow(PreviewPlayback.Preparing)
    override val playback: StateFlow<PreviewPlayback> = mutablePlayback.asStateFlow()

    private val mutablePosition = MutableStateFlow(0L)
    override val positionUs: StateFlow<Long> = mutablePosition.asStateFlow()

    // Created only once a composition is ready: a CompositionPlayer released before it ever got one keeps its
    // playback thread running (Media3 1.11.1).
    private val mutablePlayer = MutableStateFlow<CompositionPlayer?>(null)

    /** What the preview surface shows; null until the first composition is ready, and again after [close]. */
    val player: StateFlow<CompositionPlayer?> = mutablePlayer.asStateFlow()

    // The newest project's length, so a seek before its composition is ready already lands inside it.
    private var lengthUs = project.videoLengthUs()
    private var scrubbing = false
    private var closed = false

    // The live gains of the composition on the player, with the volume it already applies per sound.
    private var liveVolumes = LiveVolumes(emptyMap(), emptyMap())
    private val positionTicker = FrameTicker(::publishPlayerPosition)
    private val headphonesUnplugged = HeadphonesUnpluggedReceiver(context, onUnplugged = ::pause)
    private val playSpan = PlaySpanLog(logger)
    private val showTiming = ShowTimingLog(logger)
    private var firstFrameWatch: Job? = null

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

        override fun onRenderedFirstFrame() {
            if (playerShowsTrim) return
            firstFrameWatch?.cancel()
            showTiming.firstFrameShown()
            val caughtUp = shownUpdate == updateCount
            publish { it.copy(firstFrameShown = true, isCatchingUp = it.isCatchingUp && !caughtUp) }
        }

        override fun onPlayerError(error: PlaybackException) = fail("playback error ${error.errorCodeName}", error)
    }

    init {
        showTiming.start(ShowTimingLog.Change.OPEN)
        scope.launch {
            combine(projects, trimClip, ::Pair).collectLatest { (next, clip) ->
                if (clip != null) {
                    showTrimComposition(withContext(ioDispatcher) { prepare(trimProjectOf(next, clip)) })
                } else {
                    val update = updateCount
                    show(withContext(ioDispatcher) { prepare(next) }, update)
                }
            }
        }
    }

    override fun play() {
        if (closed) return
        publish { it.copy(isPlaying = true) }
        // During a trim drag the player holds the trim composition; the project plays once it's back.
        if (!playerShowsTrim) mutablePlayer.value?.play()
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
        // Before the first composition (or while the trim composition is on) there is nothing of the project to seek
        // in; its composition starts at this position instead.
        if (!playerShowsTrim) mutablePlayer.value?.seekTo(clampedUs / MICROS_PER_MILLI)
    }

    // Media3's scrubbing mode keeps decoders warm and may show the nearest fast frame while many seeks arrive.
    override fun setScrubbing(scrubbing: Boolean) {
        if (closed || this.scrubbing == scrubbing) return
        this.scrubbing = scrubbing
        mutablePlayer.value?.setScrubbingModeEnabled(scrubbing)
    }

    override fun update(project: Project) {
        // An equal project renders nothing new, so there is no edit to time.
        if (closed || project == projects.value) return
        showTiming.start(ShowTimingLog.Change.EDIT)
        lengthUs = project.videoLengthUs()
        updateCount++
        publish { it.copy(isCatchingUp = true) }
        projects.value = project
    }

    override fun showTrimFrame(clip: MediaClip, sourceTimeUs: Long) {
        if (closed) return
        val wholeSource = wholeSourceOf(clip)
        trimSourceUs = sourceTimeUs.coerceIn(0, lastFrameUs(wholeSource))
        if (trimClip.value != wholeSource) {
            // The first frame of a drag: build the clip's composition; it starts at the newest time asked for.
            playerShowsTrim = false
            trimClip.value = wholeSource
            return
        }
        if (playerShowsTrim) mutablePlayer.value?.seekTo(trimSourceUs / MICROS_PER_MILLI)
    }

    override fun previewVolume(itemId: String, volume: Float) {
        val committed = liveVolumes.committed[itemId] ?: return
        // The live gain multiplies the committed level; at 0% there's no fixed step, so it's the level itself.
        liveVolumes.gains[itemId]?.factor = if (committed > 0f) volume / committed else volume
    }

    override fun endTrimFrames() {
        if (closed || trimClip.value == null) return
        // The project's composition is rebuilt at positionUs, the trim's commit included when it came first (the
        // editor commits, then ends the trim frames); otherwise the commit right after replaces that build.
        trimClip.value = null
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
        // Silent items stay in, so raising one from 0% is heard while the slider moves.
        val plan = when (val planned = CompositionPlanner.plan(project, keepSilentItems = true)) {
            is PlanResult.Unplayable -> return PreparedPreview.Failed("the project can't be played: ${planned.problem}", cause = null)
            is PlanResult.Ready -> planned.plan
        }
        val resolved = sourceResolver.resolve(plan)
        // Each missing file, and each file whose length isn't known, plays as black and silence.
        val unplayable = resolved.missingItemIds.toSet() + plan.itemsWithoutLength()
        val playablePlan = plan.withGapsFor(unplayable)
        return try {
            val onsetLog = if (measureSoundOnsets) listOf(SoundOnsetLog(playablePlan.soundStartsUs.toLongArray(), logger)) else emptyList()
            val volumes = itemsWithSoundOf(playablePlan)
            // A 0% item is silenced by its live gain (no fixed step), so that gain starts at 0.
            val liveGains = volumes.mapValues { (_, volume) -> LiveGainProcessor().apply { if (volume == 0f) factor = 0f } }
            val composition = Media3CompositionBuilder(playablePlan, resolved.uriByMedia, ExportSettings.DEFAULT_FRAME_RATE, onsetLog, liveGains).build()
            PreparedPreview.Ready(composition, plan.durationUs, missingMedia = unplayable.isNotEmpty(), LiveVolumes(liveGains, volumes))
        } catch (e: RuntimeException) {
            // Media3's own argument checks.
            PreparedPreview.Failed("the composition couldn't be built", e)
        }
    }

    private fun show(prepared: PreparedPreview, update: Int) {
        if (closed) return
        when (prepared) {
            is PreparedPreview.Failed -> fail(prepared.reason, prepared.cause)
            is PreparedPreview.Ready -> showComposition(prepared, update)
        }
    }

    private fun showComposition(prepared: PreparedPreview.Ready, update: Int) {
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
        if (playerShowsTrim) {
            playerShowsTrim = false
            player.setScrubbingModeEnabled(scrubbing)
            // A Play pressed during the drag starts now.
            player.playWhenReady = mutablePlayback.value.isPlaying
        }
        shownUpdate = update
        liveVolumes = prepared.liveVolumes
        mutablePosition.value = startUs
        publish { it.copy(missingMedia = prepared.missingMedia) }
        timeFirstFrame()
    }

    // A trim preview that can't be built leaves the last frame on screen: the drag and the project still work.
    private fun showTrimComposition(prepared: PreparedPreview) {
        if (closed) return
        val ready = when (prepared) {
            is PreparedPreview.Failed -> return logger.error(TAG, "Trim preview unavailable: ${prepared.reason}", prepared.cause)
            is PreparedPreview.Ready -> prepared
        }
        val player = mutablePlayer.value ?: return
        try {
            player.setScrubbingModeEnabled(true)
            player.setComposition(ready.composition, trimSourceUs / MICROS_PER_MILLI)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        } catch (e: RuntimeException) {
            return logger.error(TAG, "Trim preview refused by the player", e)
        }
        playerShowsTrim = true
    }

    // P1-04 review R1: how long each edit takes to show. A player that never reports a frame is logged too, so a
    // missing line never hides a slow edit.
    private fun timeFirstFrame() {
        showTiming.compositionSet()
        firstFrameWatch?.cancel()
        firstFrameWatch = scope.launch {
            delay(FIRST_FRAME_TIMEOUT_MS)
            showTiming.firstFrameMissing()
            // Never leave the editor saying the preview is updating when the player simply didn't report.
            publish { it.copy(isCatchingUp = false) }
        }
    }

    private fun newPlayer(): CompositionPlayer {
        val player = CompositionPlayer.Builder(context)
            // Pauses for calls and other apps' sound (handled by Media3), like every media player.
            .setAudioAttributes(MEDIA_AUDIO, /* handleAudioFocus= */ true)
            .build()
        player.addListener(playerListener)
        player.addAnalyticsListener(playSpan)
        player.playWhenReady = mutablePlayback.value.isPlaying
        player.setScrubbingModeEnabled(scrubbing)
        return player
    }

    private fun fail(reason: String, cause: Throwable?) {
        logger.error(TAG, "Preview stopped: $reason", cause)
        mutablePlayer.value?.pause()
        publish { it.copy(status = PreviewStatus.FAILED, isPlaying = false, isCatchingUp = false) }
    }

    // The end isn't a loop: playback stops with the last frame on screen and the time showing the full length.
    private fun stopAtEnd() {
        mutablePlayer.value?.pause()
        publishPlayerPosition()
        publish { it.copy(isPlaying = false) }
    }

    private fun publishPlayerPosition() {
        // The trim composition's time is a time in the clip's source, not in the project.
        if (playerShowsTrim) return
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
        class Ready(val composition: Composition, val lengthUs: Long, val missingMedia: Boolean, val liveVolumes: LiveVolumes) : PreparedPreview

        class Failed(val reason: String, val cause: Throwable?) : PreparedPreview
    }

    /** The volume of every item [plan] plays sound from: sounds on the audio lanes and main clips with their own sound. */
    private fun itemsWithSoundOf(plan: CompositionPlan): Map<String, Float> {
        val sounds = plan.audioLanes.flatMap { it.segments }.filterIsInstance<SoundClip>().map { it.itemId to it.volume }
        val clips = plan.mainVideo.filterIsInstance<VisualClip>().filter { it.playsOwnAudio }.map { it.itemId to it.volume }
        return (sounds + clips).toMap()
    }

    /** Each item's [LiveGainProcessor] in one composition, and the volume that composition applies to it. */
    private class LiveVolumes(val gains: Map<String, LiveGainProcessor>, val committed: Map<String, Float>)

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
        const val FIRST_FRAME_TIMEOUT_MS = 5_000L
        val MEDIA_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}

private const val TAG = "MemixPreview"

private const val TRIM_TRACK_ID = "trim-preview"

/** A photo shows the same picture at every time; one second is enough to hold it. */
private const val PHOTO_TRIM_PREVIEW_US = 1_000_000L

/** One frame at the export frame rate: the last frame of a source starts this long before its end. */
private const val FRAME_US = 1_000_000L / ExportSettings.DEFAULT_FRAME_RATE

/**
 * [clip] over its whole source, from 0:00, silent: the composition a trim drag seeks in. A video of unknown length
 * can only get shorter, so its current end is the limit.
 */
private fun wholeSourceOf(clip: MediaClip): MediaClip {
    val lengthUs = when (clip.source.kind) {
        MediaKind.IMAGE -> PHOTO_TRIM_PREVIEW_US
        MediaKind.VIDEO, MediaKind.AUDIO -> clip.source.durationUs ?: clip.trimOutUs
    }
    return clip.copy(startUs = 0, trimInUs = 0, trimOutUs = lengthUs, audioDetached = true)
}

private fun lastFrameUs(wholeSource: MediaClip): Long = (wholeSource.trimOutUs - FRAME_US).coerceAtLeast(0)

/** The project with only [wholeSource] on its main track: same canvas and frame, nothing else to decode. */
private fun trimProjectOf(project: Project, wholeSource: MediaClip): Project =
    project.copy(video = VideoTimeline(listOf(Track(TRIM_TRACK_ID, TrackKind.MAIN_VIDEO, listOf(wholeSource)))))

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
