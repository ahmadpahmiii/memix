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
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import app.memix.core.domain.Logger
import app.memix.core.domain.project.updateClipLook
import app.memix.core.domain.project.videoLengthUs
import app.memix.core.domain.video.ExportSettings
import app.memix.core.domain.video.PreviewPlayback
import app.memix.core.domain.video.PreviewSession
import app.memix.core.domain.video.PreviewStatus
import app.memix.core.domain.video.ReverseFailure
import app.memix.engine.video.reverse.CopyState
import app.memix.engine.video.reverse.ReversalKey
import app.memix.engine.video.reverse.ReversedClips
import app.memix.engine.video.reverse.ReversedPlan
import app.memix.core.domain.voiceover.ClockSample
import app.memix.core.model.project.ChromaKey
import app.memix.core.model.project.ClipLook
import app.memix.core.model.project.ClipMask
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val captionRenderer: AndroidCaptionRenderer,
    private val decoderBudget: VideoDecoderBudget,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
    private val lutTables: LutTables,
    /** Prepares the reversed copies of reversed clips (P4-10) and swaps them into each plan; its copies stop when this session closes. */
    private val reversedClips: ReversedClips,
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

    // Whether the player was built with the video graph that composites overlays (see playerFor).
    private var playerComposites = false

    // The canvas size of the composition on the player, which becomes the picture's at its first frame.
    private var playerCanvasSize: PixelSize? = null

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

    // A voiceover is recording without headphones (P3-08): the picture plays, the sound doesn't.
    private var silent = false

    // The live gains of the composition on the player, with the volume it already applies per sound.
    private var liveVolumes = LiveVolumes(emptyMap(), emptyMap())

    // The caption the editor draws itself (typing, a gesture), and the hide switch of the composition on the player.
    // Each composition has its own switch, so a rebuild in flight never shows a caption the old frame hides, or the
    // other way round.
    private var requestedHiddenCaption: String? = null
    private var shownCaptions: CaptionVisibility? = null

    // The same for the overlay a finger moves (P4-01b): the compositor of the composition on the player draws it at alpha 0.
    private var requestedHiddenOverlay: String? = null
    private var requestedLiveOpacity: LiveOpacity? = null
    private var shownOverlays: OverlayVisibility? = null

    // The green screen sliders' newest key (P4-04): kept for a composition still being built, and written to the shown one's shaders.
    private var requestedLiveKey: Pair<String, ChromaKey>? = null
    private var shownKeys: Map<String, LiveChromaKey> = emptyMap()

    // The same for the mask's live moves (P4-03b).
    private var requestedLiveMask: Pair<String, ClipMask>? = null
    private var shownMasks: Map<String, LiveMask> = emptyMap()
    // The look sliders' newest look per clip (P4-07), the shown composition's live looks, and a counter that asks for a new composition when a
    // look needs a shader pass or a filter the shown one wasn't built with (the first drag on a clip with no look).
    private val requestedLiveLooks = mutableMapOf<String, ClipLook>()
    private var shownLooks: Map<String, LiveLook> = emptyMap()
    private val lookRebuilds = MutableStateFlow(0)

    // The reversed ranges the shown composition was built for, and how each stood then (P4-10): a copy that finishes or fails asks for a new composition,
    // and one that only moves on publishes its percent.
    private var shownReversals: Map<ReversalKey, CopyState?> = emptyMap()
    private val lookRebuildGate = LookRebuildGate()

    // The shown composition's live effect items (P4-06), or null when it was built without the effects pass: edits of effect items then move the
    // list the player draws from its next frame, with no new composition.
    private var shownEffects: LiveEffects? = null
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
            publish { it.copy(firstFrameShown = true, isCatchingUp = it.isCatchingUp && !caughtUp, shownCanvasSize = playerCanvasSize) }
        }

        override fun onPlayerError(error: PlaybackException) = fail("playback error ${error.errorCodeName}", error)
    }

    init {
        showTiming.start(ShowTimingLog.Change.OPEN)
        scope.launch {
            // A change of effect items alone is not a new composition (see sameComposition), so the project is read here, not taken from the flow.
            combine(projects.distinctUntilChanged(::sameComposition), trimClip, lookRebuilds) { _, clip, _ -> clip }.collectLatest { clip ->
                val next = projects.value
                if (clip != null) {
                    showTrimComposition(withContext(ioDispatcher) { prepare(trimProjectOf(next, clip), trimPreview = true) })
                } else {
                    val update = updateCount
                    captionRenderer.prepare()
                    val withLooks = requestedLiveLooks.entries.fold(next) { project, (itemId, look) -> project.updateClipLook(itemId) { look } }
                    show(withContext(ioDispatcher) { prepare(withLooks) }, update)
                }
            }
        }
        scope.launch { reversedClips.changes.collect { onReversedCopiesChanged() } }
    }

    // A reversed copy moved on (P4-10): a new percent is published as it is; a copy that finished or failed needs a new composition, which plays it (or keeps
    // the clip's first frame and lets the editor switch Reverse off).
    private suspend fun onReversedCopiesChanged() {
        val shown = shownReversals
        if (shown.isEmpty()) return
        val now = withContext(ioDispatcher) { reversedClips.statesOf(shown.keys) }
        val settled = now.any { (key, state) -> shown[key].settledKind() != state.settledKind() }
        if (settled) return startLookRebuild()
        publish { it.copy(reversePercent = now.values.filterIsInstance<CopyState.Preparing>().minOfOrNull { state -> state.percent }) }
    }

    // What a copy's state means for the composition: only a change between these asks for a new one.
    private fun CopyState?.settledKind(): Int = when (this) {
        is CopyState.Ready -> 1
        is CopyState.Failed -> 2
        is CopyState.Preparing, null -> 0
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
        if (sameComposition(projects.value, project)) return showEffectEdit(project)
        // A committed caption edit ends its stand-in: the new composition draws the caption itself.
        requestedHiddenCaption = null
        requestedHiddenOverlay = null
        requestedLiveOpacity = null
        requestedLiveKey = null
        requestedLiveMask = null
        requestedLiveLooks.clear()
        showTiming.start(ShowTimingLog.Change.EDIT)
        lengthUs = project.videoLengthUs()
        updateCount++
        publish { it.copy(isCatchingUp = true) }
        projects.value = project
    }

    // An edit of effect items only (add, move, trim, Intensity, delete among others): the effects pass reads its items live, so the player draws the
    // new list from its next frame. The project is kept for the next composition; paused, a seek to where it is draws the frame again.
    // Skips the show-timing, catching-up and caption bookkeeping of update() on purpose: nothing is rebuilt, so there is nothing to wait for.
    private fun showEffectEdit(project: Project) {
        projects.value = project
        shownEffects?.spans = effectSpansOf(project)
        redrawWhenPaused()
    }

    override fun previewEffectIntensity(itemId: String, intensity: Float) {
        if (closed) return
        val live = shownEffects ?: return
        live.spans = live.spans.map { if (it.itemId == itemId) it.copy(intensity = intensity) else it }
        redrawWhenPaused()
    }

    private fun redrawWhenPaused() {
        if (!playerShowsTrim && !mutablePlayback.value.isPlaying) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
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

    override fun previewFades(itemId: String, fadeInUs: Long, fadeOutUs: Long) {
        liveVolumes.fades[itemId]?.let {
            it.fadeInUs = fadeInUs
            it.fadeOutUs = fadeOutUs
        }
    }

    override fun previewBass(itemId: String, bassBoost: Float, distortion: Float) {
        liveVolumes.bass[itemId]?.let {
            it.bassBoost = bassBoost
            it.distortion = distortion
        }
    }

    override fun setSilent(silent: Boolean) {
        if (closed) return
        this.silent = silent
        mutablePlayer.value?.volume = playerVolume()
    }

    override fun sampleClock(): ClockSample? {
        val player = mutablePlayer.value
        if (closed || player == null || playerShowsTrim) return null
        // The player's own clock follows what is heard; the reading and the clock's time are taken together.
        val positionUs = player.currentPosition * MICROS_PER_MILLI
        return ClockSample(positionUs, System.nanoTime())
    }

    private fun playerVolume(): Float = if (silent) 0f else 1f

    override fun hideCaption(itemId: String?) {
        if (closed) return
        requestedHiddenCaption = itemId
        val shown = shownCaptions ?: return
        if (shown.hiddenItemId == itemId) return
        shown.hiddenItemId = itemId
        // Showing it again on a frame an update is about to replace would flash the old caption: leave that frame.
        if (itemId == null && mutablePlayback.value.isCatchingUp) return
        // Edits pause first. A seek to where it is renders the frame again through the overlay, without a rebuild.
        if (!playerShowsTrim) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    override fun hideOverlay(itemId: String?) {
        if (closed) return
        requestedHiddenOverlay = itemId
        val shown = shownOverlays ?: return
        if (shown.hiddenItemId == itemId) return
        shown.hiddenItemId = itemId
        if (itemId == null && mutablePlayback.value.isCatchingUp) return
        // Paused, a seek to where it is draws the frame again through the compositor, without a rebuild.
        if (!playerShowsTrim) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    override fun previewOpacity(itemId: String, opacity: Float?) {
        if (closed) return
        // Kept for a composition still being built, which takes it when it is shown (as for a hidden overlay).
        requestedLiveOpacity = opacity?.let { LiveOpacity(itemId, it) }
        val shown = shownOverlays ?: return
        shown.liveOpacity = requestedLiveOpacity
        if (opacity == null) return
        // Playing, the next frame draws it; paused, a seek to where it is draws the frame again through the compositor.
        if (!playerShowsTrim && !mutablePlayback.value.isPlaying) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    override fun previewChromaKey(itemId: String, key: ChromaKey) {
        if (closed) return
        requestedLiveKey = itemId to key
        val live = shownKeys[itemId] ?: return
        live.key = key
        // Playing, the next frame draws it; paused, a seek to where it is draws the frame again through the shader.
        if (!playerShowsTrim && !mutablePlayback.value.isPlaying) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    override fun previewMask(itemId: String, mask: ClipMask) {
        if (closed) return
        requestedLiveMask = itemId to mask
        val live = shownMasks[itemId] ?: return
        live.mask = mask
        // Playing, the next frame draws it; paused, a seek to where it is draws the frame again through the shader.
        if (!playerShowsTrim && !mutablePlayback.value.isPlaying) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    override fun previewLook(itemId: String, look: ClipLook) {
        if (closed) return
        requestedLiveLooks[itemId] = look
        val live = shownLooks[itemId]
        if (live == null || !live.covers(look)) return if (look.isNeutral) Unit else rebuildForLooks()
        live.look = look
        // Playing, the next frame draws it; paused, a seek to where it is draws the frame again through the shaders.
        if (!playerShowsTrim && !mutablePlayback.value.isPlaying) mutablePlayer.value?.let { player -> player.seekTo(player.currentPosition) }
    }

    // A look the shown composition can't draw (a pass or a filter it was built without) is drawn by a new one, built with the look in the project.
    private fun rebuildForLooks() {
        if (lookRebuildGate.request()) startLookRebuild()
    }

    private fun startLookRebuild() {
        updateCount++
        publish { it.copy(isCatchingUp = true) }
        lookRebuilds.value++
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
        reversedClips.release(this)
    }

    // Runs on ioDispatcher.
    private fun prepare(project: Project, trimPreview: Boolean = false): PreparedPreview {
        // Silent items stay in, so raising one from 0% is heard while the slider moves.
        val planned = CompositionPlanner.plan(project, keepSilentItems = true, videoDecoderBudget = decoderBudget.videoDecoders)
        val plan = when (planned) {
            is PlanResult.Unplayable -> return PreparedPreview.Failed("the project can't be played: ${planned.problem}", cause = null)
            is PlanResult.Ready -> planned.plan
        }
        if (plan.skippedOverlayIds.isNotEmpty()) logger.debug(TAG, "Overlays left out of the preview: ${plan.skippedOverlayIds}")
        val resolved = sourceResolver.resolve(plan)
        // Each missing file, and each file whose length isn't known, plays as black and silence.
        val unplayable = resolved.missingItemIds.toSet() + plan.itemsWithoutLength()
        val overlayIds = plan.overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().map { it.itemId }.toSet()
        // Reversed clips play their prepared copy, or hold their first frame until it is ready (P4-10). A trim drag shows the whole source and has none.
        val reversed = if (trimPreview) ReversedPlan(plan, resolved.uriByMedia, null, emptyList()) else reversedClips.forPreview(this, plan, resolved.uriByMedia)
        val playablePlan = reversed.plan.withGapsFor(unplayable)
        return try {
            val onsetLog = if (measureSoundOnsets) listOf(SoundOnsetLog(playablePlan.soundStartsUs.toLongArray(), logger)) else emptyList()
            val volumes = itemsWithSoundOf(playablePlan)
            // A 0% item is silenced by its live gain (no fixed step), so that gain starts at 0.
            val liveGains = volumes.mapValues { (_, volume) -> LiveGainProcessor().apply { if (volume == 0f) factor = 0f } }
            val sounds = playablePlan.audioLanes.flatMap { it.segments }.filterIsInstance<SoundClip>()
            val liveFades = sounds.associate { it.itemId to LiveFades(it.effects.fadeInUs, it.effects.fadeOutUs) }
            val liveBass = sounds.associate { it.itemId to LiveBass(it.effects.bassBoost, it.effects.distortion) }
            val liveKeys = playablePlan.chromaKeys.mapValues { (_, key) -> LiveChromaKey(key) }
            val liveMasks = playablePlan.masks.mapValues { (_, mask) -> LiveMask(mask) }
            val liveLooks = playablePlan.looks.mapValues { (_, look) -> LiveLook(look) }
            val liveEffects = if (playablePlan.hasEffects) LiveEffects(playablePlan.effects) else null
            val visibility = CaptionVisibility()
            var overlayVisibility: OverlayVisibility? = null
            val captions = playablePlan.captions.takeIf { it.isNotEmpty() }?.let {
                CaptionOverlay(it, captionRenderer, playablePlan.canvasWidthPx, playablePlan.canvasHeightPx, visibility)
            }
            val composition = Media3CompositionBuilder(
                playablePlan,
                reversed.uriByMedia,
                ExportSettings.DEFAULT_FRAME_RATE,
                onsetLog,
                liveGains,
                liveFades,
                liveBass,
                liveKeys = liveKeys,
                liveMasks = liveMasks,
                liveLooks = liveLooks,
                lutTables = lutTables,
                liveEffects = liveEffects,
                heldFrames = resolved.heldFrames,
                captionOverlay = captions,
                frameShortSidePx = if (playablePlan.background == PlanBackground.Blur) BLUR_PREVIEW_SHORT_SIDE_PX else null,
                backgroundFile = resolved.backgroundFile,
                onOverlayVisibility = { overlayVisibility = it },
            ).build()
            PreparedPreview.Ready(
                composition,
                plan.durationUs,
                missingMedia = unplayable.isNotEmpty(),
                onlyOverlaysMissing = unplayable.isNotEmpty() && unplayable.all { it in overlayIds },
                LiveVolumes(liveGains, volumes, liveFades, liveBass),
                visibility,
                overlayVisibility,
                PixelSize(plan.canvasWidthPx, plan.canvasHeightPx),
                compositesOverlays = playablePlan.compositesSequences,
                skippedOverlayIds = plan.skippedOverlayIds,
                liveKeys = liveKeys,
                liveMasks = liveMasks,
                liveLooks = liveLooks,
                liveEffects = liveEffects,
                reversals = reversed.states,
                reversePercent = reversed.percent,
                reverseFailures = reversed.failures.map { ReverseFailure(it.itemId, it.noSpace, it.tooLong) },
            )
        } catch (e: RuntimeException) {
            // Media3's own argument checks.
            PreparedPreview.Failed("the composition couldn't be built", e)
        }
    }

    private fun show(prepared: PreparedPreview, update: Int) {
        if (closed) return
        when (prepared) {
            is PreparedPreview.Failed -> {
                lookRebuildGate.shown(stillUncovered = false)
                fail(prepared.reason, prepared.cause)
            }
            is PreparedPreview.Ready -> {
                showComposition(prepared, update)
                // Looks asked for while this one was built that it can't draw (a pass or filter it lacks) get exactly one more build.
                val uncovered = requestedLiveLooks.any { (id, look) -> !look.isNeutral && shownLooks[id]?.covers(look) != true }
                if (lookRebuildGate.shown(uncovered)) startLookRebuild()
            }
        }
    }

    private fun showComposition(prepared: PreparedPreview.Ready, update: Int) {
        lengthUs = prepared.lengthUs
        val startUs = mutablePosition.value.coerceIn(0, lengthUs)
        val player = playerFor(prepared.compositesOverlays)
        // A caption hidden while this composition was built (a gesture that began meanwhile) stays hidden on it.
        prepared.captions.hiddenItemId = requestedHiddenCaption
        shownCaptions = prepared.captions
        prepared.overlays?.hiddenItemId = requestedHiddenOverlay
        prepared.overlays?.liveOpacity = requestedLiveOpacity
        shownOverlays = prepared.overlays
        requestedLiveKey?.let { (itemId, key) -> prepared.liveKeys[itemId]?.key = key }
        shownKeys = prepared.liveKeys
        requestedLiveMask?.let { (itemId, mask) -> prepared.liveMasks[itemId]?.mask = mask }
        shownMasks = prepared.liveMasks
        requestedLiveLooks.forEach { (itemId, look) -> prepared.liveLooks[itemId]?.takeIf { it.covers(look) }?.look = look }
        shownLooks = prepared.liveLooks
        // Effect items edited while this one was built.
        prepared.liveEffects?.spans = effectSpansOf(projects.value)
        shownEffects = prepared.liveEffects
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
        playerCanvasSize = prepared.canvasSize
        liveVolumes = prepared.liveVolumes
        mutablePosition.value = startUs
        shownReversals = prepared.reversals
        // A copy that settled while this composition was being built is not in it: look once now, or it would never be asked for.
        if (prepared.reversals.isNotEmpty()) scope.launch { onReversedCopiesChanged() }
        publish {
            it.copy(
                missingMedia = prepared.missingMedia,
                onlyOverlaysMissing = prepared.onlyOverlaysMissing,
                skippedOverlayIds = prepared.skippedOverlayIds,
                reversePercent = prepared.reversePercent,
                reverseFailures = prepared.reverseFailures,
            )
        }
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

    // A player's video graph is fixed when it is built (Media3 1.11.1): CompositionPlayer composites several video sequences only
    // with the multiple-input graph, and plays a single one best with the default. So the first overlay added, or the last one
    // removed, swaps the player; the surface shows the new one's first frame in place of the old picture.
    private fun playerFor(compositesOverlays: Boolean): CompositionPlayer {
        val current = mutablePlayer.value
        if (current != null && compositesOverlays == playerComposites) return current
        current?.release()
        playerComposites = compositesOverlays
        return newPlayer(compositesOverlays).also { mutablePlayer.value = it }
    }

    private fun newPlayer(compositesOverlays: Boolean): CompositionPlayer {
        val builder = CompositionPlayer.Builder(context)
            // Pauses for calls and other apps' sound (handled by Media3), like every media player.
            .setAudioAttributes(MEDIA_AUDIO, /* handleAudioFocus= */ true)
        if (compositesOverlays) builder.setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        val player = builder.build()
        player.addListener(playerListener)
        player.addAnalyticsListener(playSpan)
        player.playWhenReady = mutablePlayback.value.isPlaying
        player.setScrubbingModeEnabled(scrubbing)
        player.volume = playerVolume()
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
        class Ready(
            val composition: Composition,
            val lengthUs: Long,
            val missingMedia: Boolean,
            /** Every missing file belongs to an overlay. */
            val onlyOverlaysMissing: Boolean,
            val liveVolumes: LiveVolumes,
            val captions: CaptionVisibility,
            /** The hide switch of the composition's overlay compositor; null when it draws no overlays. */
            val overlays: OverlayVisibility?,
            val canvasSize: PixelSize,
            /** True when the composition draws overlays, so its player needs the compositing video graph. */
            val compositesOverlays: Boolean,
            val skippedOverlayIds: List<String>,
            /** The green screen's live key of every keyed clip in this composition. */
            val liveKeys: Map<String, LiveChromaKey>,
            /** The mask's live value of every masked clip in this composition. */
            val liveMasks: Map<String, LiveMask>,
            val liveLooks: Map<String, LiveLook>,
            /** The effects pass's live items; null when the composition has none (see [effectsChangeComposition]). */
            val liveEffects: LiveEffects?,
            /** Where each reversed range stood when this composition was built (P4-10), so a later change of one asks for a new composition. */
            val reversals: Map<ReversalKey, CopyState?>,
            val reversePercent: Int?,
            val reverseFailures: List<ReverseFailure>,
        ) : PreparedPreview

        class Failed(val reason: String, val cause: Throwable?) : PreparedPreview
    }

    /** The volume of every item [plan] plays sound from: sounds on the audio lanes and main and overlay clips with their own sound. */
    private fun itemsWithSoundOf(plan: CompositionPlan): Map<String, Float> {
        val sounds = plan.audioLanes.flatMap { it.segments }.filterIsInstance<SoundClip>().map { it.itemId to it.volume }
        val clips = plan.mainVideo.filterIsInstance<VisualClip>().filter { it.playsOwnAudio }.map { it.itemId to it.volume }
        val overlayClips = plan.overlays.flatMap { it.segments }.filterIsInstance<OverlayClip>().filter { it.playsOwnAudio }.map { it.itemId to it.volume }
        return (sounds + clips + overlayClips).toMap()
    }

    /** Each item's [LiveGainProcessor] in one composition, and the volume that composition applies to it. */
    private class LiveVolumes(val gains: Map<String, LiveGainProcessor>, val committed: Map<String, Float>, val fades: Map<String, LiveFades> = emptyMap(), val bass: Map<String, LiveBass> = emptyMap())

    private companion object {
        const val MICROS_PER_MILLI = 1_000L
        const val FIRST_FRAME_TIMEOUT_MS = 5_000L

        /**
         * The preview draws at this shorter side while a blur background plays (spec P1-11 allows the blur at a reduced
         * size in the preview): the blur pass costs per drawn pixel, and the stage shows the frame smaller than this on a
         * phone anyway. Export draws at the chosen resolution.
         */
        const val BLUR_PREVIEW_SHORT_SIDE_PX = 720
        val MEDIA_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}

private const val TAG = "MemixPreview"

/**
 * True when the two projects need the same composition: they differ at most in their effect items, and either both have an effect this engine draws
 * or neither does. The effects pass reads its items live, so such an edit is not a rebuild; the first effect and the last one going are (they add
 * and remove the pass).
 */
private fun sameComposition(before: Project, after: Project): Boolean =
    before.withoutEffectItems() == after.withoutEffectItems() && effectSpansOf(before).isEmpty() == effectSpansOf(after).isEmpty()

private fun Project.withoutEffectItems(): Project {
    val timeline = video ?: return this
    return copy(video = timeline.copy(tracks = timeline.tracks.filter { it.kind != TrackKind.EFFECT }))
}

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
    // At 1x and forward: the trim drag seeks in source time.
    return clip.copy(startUs = 0, trimInUs = 0, trimOutUs = lengthUs, audioDetached = true, speed = 1f, speedCurve = null, reversed = false)
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
