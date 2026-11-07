package app.memix.engine.segmentation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions

/**
 * Owns the one ML Kit client. Creating it loads the model into memory, so it is kept between cut-outs (a second photo
 * must not pay for the load) and closed when the app's screens are hidden or memory runs low, unless a call is using it.
 */
internal class SubjectSegmenterClients(context: Context) {
    // Only the confidence mask: the foreground bitmap and the per-subject results cost time and memory we don't use.
    private val options = SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
    private var client: SubjectSegmenter? = null
    private var callsRunning = 0

    init {
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) closeWhenIdle()
            }

            override fun onLowMemory() = closeWhenIdle()

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        })
    }

    suspend fun <T> use(block: suspend (SubjectSegmenter) -> T): T {
        val segmenter = acquire()
        try {
            return block(segmenter)
        } finally {
            release()
        }
    }

    @Synchronized
    private fun acquire(): SubjectSegmenter {
        callsRunning++
        return client ?: SubjectSegmentation.getClient(options).also { client = it }
    }

    @Synchronized
    private fun release() {
        callsRunning--
    }

    @Synchronized
    private fun closeWhenIdle() {
        if (callsRunning > 0) return
        client?.close()
        client = null
    }
}
