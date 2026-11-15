package app.memix.engine.video

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.os.Handler
import android.os.Looper
import android.os.Process
import app.memix.core.domain.Logger
import app.memix.core.domain.voiceover.RecordedTake
import app.memix.core.domain.voiceover.TakeInterruption
import app.memix.core.domain.voiceover.TakeTiming
import app.memix.core.domain.voiceover.TimingSource
import app.memix.core.domain.voiceover.VoiceTake
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One take on a dedicated capture thread: [AudioRecord] reads the microphone, [AacM4aWriter] encodes it into [partFile],
 * which becomes [finalFile] when the take finishes. The thread is the only one that touches the writer and releases the
 * recorder; [finish] and [discard] only ask the microphone to stop (which unblocks a read) and set a flag.
 *
 * Reading, encoding and muxing share this one thread. The buffer holds a second of audio, so a slow encoder step or
 * muxer write cannot overrun it; a second thread with a queue was not needed for that and was left out.
 *
 * Timing (spec P3-08 → Sync): the phone's `getTimestamp` gives when a frame was delivered at the earliest point of the
 * capture pipeline, so the first sample's time is that moment minus the frames before it. It is polled after every chunk
 * for the first [TIMESTAMP_POLL_NANOS]; a phone that has given none by [ESTIMATE_AFTER_DIVISOR]'s share of a second gets
 * the time the first read returned, minus the samples already read, minus [ESTIMATED_INPUT_LATENCY_US], until a real
 * timestamp arrives and replaces it. Either way it is on `System.nanoTime()`, the clock the preview's samples use.
 *
 * Interruptions: the system silencing the microphone for another app, the routed microphone changing, a full phone and an
 * I/O or codec failure each end the loop; the take written so far is still finished and kept.
 */
internal class VoiceCapture(
    private val record: AudioRecord,
    private val sampleRate: Int,
    private val writer: AacM4aWriter,
    private val partFile: File,
    private val finalFile: File,
    private val relativePath: String,
    private val audioManager: AudioManager,
    private val askedAtNanos: Long,
    private val logger: Logger,
    private val logDetails: Boolean,
) : VoiceTake {
    private val mutableLevel = MutableStateFlow(0f)
    override val level: StateFlow<Float> = mutableLevel.asStateFlow()

    private val mutableInterruption = MutableStateFlow<TakeInterruption?>(null)
    override val interruption: StateFlow<TakeInterruption?> = mutableInterruption.asStateFlow()

    private val firstSample = CompletableDeferred<Boolean>()
    private val finished = CompletableDeferred<RecordedTake>()

    @Volatile
    private var stopRequested = false

    @Volatile
    private var discardRequested = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val thread = Thread(::run, "memix-voice").apply { isDaemon = true }
    private var routedDeviceId: Int? = null

    // The listeners are armed once the first sample has arrived (the routing callback also fires for the initial route) and
    // are removed by the capture thread; this lock keeps a take that ends at that moment from leaving one registered.
    private val listenersLock = Any()
    private var listenersArmed = false
    private var ended = false

    private val routingListener = AudioRouting.OnRoutingChangedListener { router ->
        val id = router.routedDevice?.id ?: return@OnRoutingChangedListener
        val first = routedDeviceId
        if (first == null) routedDeviceId = id else if (first != id) interrupt(TakeInterruption.ROUTE_CHANGED)
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val ours = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId } ?: return
            if (ours.isClientSilenced) interrupt(TakeInterruption.SILENCED)
        }
    }

    /** Starts the thread; true once the first sample has arrived, false when the microphone never delivered one. Cancelling it leaves the take running: the caller discards it. */
    suspend fun start(): Boolean {
        thread.start()
        val heard = firstSample.await()
        if (heard) armListeners()
        return heard
    }

    private fun armListeners() = synchronized(listenersLock) {
        if (ended) return
        record.addOnRoutingChangedListener(routingListener, mainHandler)
        audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)
        listenersArmed = true
    }

    private fun disarmListeners() = synchronized(listenersLock) {
        ended = true
        if (!listenersArmed) return
        record.removeOnRoutingChangedListener(routingListener)
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        listenersArmed = false
    }

    override suspend fun finish(): RecordedTake {
        requestStop()
        return finished.await()
    }

    override fun discard() {
        discardRequested = true
        requestStop()
    }

    // Stopping the microphone makes a blocked read return, so the thread sees the flag at once; the thread releases the recorder.
    private fun requestStop() {
        stopRequested = true
        runCatching { record.stop() }
    }

    private fun interrupt(reason: TakeInterruption) {
        mutableInterruption.compareAndSet(null, reason)
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val progress = Progress()
        var failed = false
        try {
            capture(progress)
        } catch (e: IOException) {
            interrupt(if (isPhoneFull()) TakeInterruption.NO_SPACE else TakeInterruption.FAILED)
        } catch (e: RuntimeException) {
            // Codec and muxer errors: the class only, never the message. A full phone is not a failure: what was written is kept.
            val phoneIsFull = isPhoneFull()
            if (!phoneIsFull) logger.error(TAG, "Recording failed: ${e::class.simpleName}")
            interrupt(if (phoneIsFull) TakeInterruption.NO_SPACE else TakeInterruption.FAILED)
            failed = !phoneIsFull
        } finally {
            // Whatever happened above, the microphone is let go and both waiting callers are answered.
            val timing = progress.timing ?: TakeTiming(askedAtNanos, TimingSource.ESTIMATED, startDelayUs = 0, inputLatencyUs = 0)
            val result = try {
                wrapUp(progress, timing, failed)
            } catch (e: RuntimeException) {
                logger.error(TAG, "Finishing the take failed: ${e::class.simpleName}")
                RecordedTake(null, 0, timing, 0f)
            }
            finished.complete(result)
            firstSample.complete(false)
        }
    }

    // The tooling for one take: counters the loop keeps and wrapUp reads.
    private class Progress {
        var framesRead = 0L
        var peak = 0
        var timing: TakeTiming? = null
        val timestamp = AudioTimestamp()
    }

    private fun capture(progress: Progress) {
        val chunk = ShortArray(sampleRate * CHUNK_MILLIS / MILLIS_PER_SECOND)
        var meterPeak = 0
        var meterChunks = 0
        var firstReadNanos = 0L
        var lastSpaceCheckNanos = System.nanoTime()
        while (!stopRequested && mutableInterruption.value == null) {
            val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            val returnedAtNanos = System.nanoTime()
            if (read < 0) {
                // A read that fails because the microphone was stopped on purpose is the end, not an error.
                if (!stopRequested) interrupt(TakeInterruption.FAILED)
                return
            }
            if (progress.framesRead == 0L && read > 0) {
                firstReadNanos = returnedAtNanos
                firstSample.complete(true)
            }
            writer.write(chunk, read, progress.framesRead * MICROS_PER_SECOND / sampleRate, sampleRate)
            progress.framesRead += read
            val chunkPeak = peakOf(chunk, read)
            progress.peak = maxOf(progress.peak, chunkPeak)
            meterPeak = maxOf(meterPeak, chunkPeak)
            if (++meterChunks == CHUNKS_PER_METER_UPDATE) {
                mutableLevel.value = meterPeak / FULL_SCALE
                meterPeak = 0
                meterChunks = 0
            }
            if (progress.timing?.source != TimingSource.DEVICE_TIMESTAMP && returnedAtNanos - firstReadNanos <= TIMESTAMP_POLL_NANOS) {
                progress.timing = timingOf(progress, returnedAtNanos) ?: progress.timing
            }
            if (returnedAtNanos - lastSpaceCheckNanos > SPACE_CHECK_NANOS) {
                lastSpaceCheckNanos = returnedAtNanos
                if (isPhoneFull()) interrupt(TakeInterruption.NO_SPACE)
            }
        }
    }

    // The space check wants the bytes free right now, not what the system could clear: the take is being written this second.
    @SuppressLint("UsableSpace")
    private fun isPhoneFull(): Boolean = partFile.usableSpace < LOW_SPACE_BYTES

    // A phone's timestamp, when it has one: the delivery time minus the frames before it. Without one, an estimate once a tenth of a second has been read.
    private fun timingOf(progress: Progress, returnedAtNanos: Long): TakeTiming? {
        val delivered = record.getTimestamp(progress.timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
        if (delivered) {
            val firstFrameNanos = progress.timestamp.nanoTime - progress.timestamp.framePosition * NANOS_PER_SECOND / sampleRate
            val inputLatencyUs = (returnedAtNanos - firstFrameNanos) / NANOS_PER_MICRO - progress.framesRead * MICROS_PER_SECOND / sampleRate
            return TakeTiming(firstFrameNanos, TimingSource.DEVICE_TIMESTAMP, (firstFrameNanos - askedAtNanos) / NANOS_PER_MICRO, inputLatencyUs)
        }
        if (progress.timing != null || progress.framesRead < sampleRate / ESTIMATE_AFTER_DIVISOR) return null
        val firstFrameNanos = returnedAtNanos - progress.framesRead * NANOS_PER_SECOND / sampleRate - ESTIMATED_INPUT_LATENCY_US * NANOS_PER_MICRO
        return TakeTiming(firstFrameNanos, TimingSource.ESTIMATED, (firstFrameNanos - askedAtNanos) / NANOS_PER_MICRO, ESTIMATED_INPUT_LATENCY_US)
    }

    // Ends the microphone, the encoder and the file, on the capture thread, and says what came out.
    private fun wrapUp(progress: Progress, timing: TakeTiming, encoderFailed: Boolean): RecordedTake {
        disarmListeners()
        if (logDetails) logDroppedFrames(progress)
        runCatching { record.stop() }
        record.release()
        val durationUs = progress.framesRead * MICROS_PER_SECOND / sampleRate
        val empty = RecordedTake(null, 0, timing, 0f)
        if (discardRequested || progress.framesRead == 0L || encoderFailed) {
            writer.abort()
            return empty
        }
        return try {
            writer.finish(durationUs)
            if (!partFile.renameTo(finalFile)) throw IOException("Couldn't rename the finished take")
            RecordedTake(relativePath, durationUs, timing, progress.peak / FULL_SCALE, primingUs = AacM4aWriter.primingUs(sampleRate))
        } catch (e: IOException) {
            interrupt(TakeInterruption.FAILED)
            writer.abort()
            empty
        } catch (e: RuntimeException) {
            logger.error(TAG, "Finishing the take failed: ${e::class.simpleName}")
            interrupt(TakeInterruption.FAILED)
            writer.abort()
            empty
        }
    }

    // Overruns are not reported by AudioRecord: the phone's own frame count, moved to now, against the frames this thread read (hand-check builds only).
    private fun logDroppedFrames(progress: Progress) {
        if (record.getTimestamp(progress.timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) != AudioRecord.SUCCESS) return
        val deliveredByNow = progress.timestamp.framePosition + (System.nanoTime() - progress.timestamp.nanoTime) * sampleRate / NANOS_PER_SECOND
        val missingMs = (deliveredByNow - progress.framesRead) * MILLIS_PER_SECOND / sampleRate
        logger.debug(TAG, "frames read=${progress.framesRead} phoneDelivered~=$deliveredByNow gap~=${missingMs}ms (a gap over 100 ms may be dropped audio)")
    }

    private fun peakOf(samples: ShortArray, count: Int): Int {
        var peak = 0
        for (i in 0 until count) peak = maxOf(peak, abs(samples[i].toInt()))
        return peak
    }

    internal companion object {
        const val TAG = "MemixVoiceover"
        const val CHUNK_MILLIS = 20
        const val MILLIS_PER_SECOND = 1_000
        const val MICROS_PER_SECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MICRO = 1_000L
        const val FULL_SCALE = 32768f

        // 20 ms chunks, so every third one updates the meter: about 16 a second, under the 20 the spec allows.
        const val CHUNKS_PER_METER_UPDATE = 3

        /** A typical input latency for phones that give no timestamp; the clap test replaces it with a measured one. */
        const val ESTIMATED_INPUT_LATENCY_US = 40_000L

        // A tenth of a second without a timestamp is long enough to stop waiting for an estimate to be made.
        const val ESTIMATE_AFTER_DIVISOR = 10

        // A real timestamp is looked for during the first second after the first sample, and replaces an estimate.
        const val TIMESTAMP_POLL_NANOS = 1_000_000_000L
        const val SPACE_CHECK_NANOS = 1_000_000_000L
        const val LOW_SPACE_BYTES = 5L * 1024 * 1024
    }
}
