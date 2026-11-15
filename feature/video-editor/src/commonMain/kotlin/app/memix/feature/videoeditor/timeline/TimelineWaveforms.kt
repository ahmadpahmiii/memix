package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.memix.core.domain.AppError
import app.memix.core.domain.Outcome
import app.memix.core.domain.video.AudioPeaks
import app.memix.core.domain.video.ReadWaveformUseCase
import app.memix.core.model.project.MediaRef
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The peaks behind the waveforms on the timeline's sound clips (spec P3-09 → Waveforms on the timeline). The editor says which
 * sounds the project has ([want]); one worker reads each file's peaks once through [ReadWaveformUseCase] (decoded off the main
 * thread and kept on disk by the engine) and keeps them for the visit. Draw code reads [peaksOf], [arrivedAt] and [version]
 * (bumped when peaks arrive, so only the lanes redraw). A sound that has no peaks yet draws no bars and no placeholder. Main thread
 * only. The editor's ViewModel owns it and closes it.
 */
@Stable
class TimelineWaveforms internal constructor(
    private val read: ReadWaveformUseCase,
    scope: CoroutineScope,
) : AutoCloseable {
    /** Goes up by one with every set of peaks that arrives. */
    internal var version by mutableIntStateOf(0)
        private set

    // Keyed by the file's path: a string hashes once, where a MediaRef hashes its origin on every bar of every frame.
    private val loaded = HashMap<String, Loaded>()
    private val missing = HashSet<String>()
    private val asked = HashSet<String>()

    /** The fades of one sound as its slider shows them while it is dragged (P3-09), so the bars follow before the release; null otherwise. */
    internal var liveFades: LiveFadesLook? by mutableStateOf(null)
        private set
    private val queue = Channel<MediaRef>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for (source in queue) {
            val key = keyOf(source) ?: continue
            when (val outcome = read(source)) {
                is Outcome.Success -> {
                    loaded[key] = Loaded(outcome.value, TimeSource.Monotonic.markNow())
                    version++
                }
                // Missing is what the Audio effects tool explains; a file that won't decode just has no bars.
                is Outcome.Failure -> if (outcome.error == AppError.NotFound) missing += key
            }
        }
    }

    /** Starts reading the peaks of every source in [sources] not asked for yet. */
    fun want(sources: Collection<MediaRef>) {
        for (source in sources) {
            val key = keyOf(source) ?: continue
            if (asked.add(key)) queue.trySend(source)
        }
    }

    /** The peaks of [source] once they have arrived; null before, and for a file that can't be read. */
    internal fun peaksOf(source: MediaRef): AudioPeaks? = keyOf(source)?.let { loaded[it]?.peaks }

    /** When the peaks of [source] arrived, so its bars can fade in over 120 ms. */
    internal fun arrivedAt(source: MediaRef): TimeMark? = keyOf(source)?.let { loaded[it]?.arrivedAt }

    /** True once reading [source] found its file gone (the same check the clip's missing-file look uses); a file that fails to decode isn't missing. */
    fun isMissing(source: MediaRef): Boolean = keyOf(source) in missing

    /** Shows the dragged fades of sound [itemId] on its bars ([fadeInUs] and [fadeOutUs] in µs of timeline time); [clearLiveFades] ends it. */
    fun showLiveFades(itemId: String, fadeInUs: Long, fadeOutUs: Long) {
        liveFades = LiveFadesLook(itemId, fadeInUs, fadeOutUs)
    }

    fun clearLiveFades() {
        if (liveFades != null) liveFades = null
    }

    private fun keyOf(source: MediaRef): String? = source.cachedCopyPath

    override fun close() {
        worker.cancel()
        queue.close()
        loaded.clear()
    }

    /** The dragged fades of one sound. */
    internal class LiveFadesLook(val itemId: String, val fadeInUs: Long, val fadeOutUs: Long)

    private class Loaded(val peaks: AudioPeaks, val arrivedAt: TimeMark)
}
