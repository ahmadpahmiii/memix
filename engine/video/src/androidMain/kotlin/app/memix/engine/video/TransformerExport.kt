package app.memix.engine.video

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one Media3 [Transformer] export. A Transformer must be used from a single looper thread; this
 * gives each export its own, so the main thread stays free and the thread ends with the export.
 */
@OptIn(UnstableApi::class)
internal class TransformerExport(private val context: Context) {

    /**
     * Writes [composition] to [outputPath] as MP4 with H.264 video and AAC audio, and returns Media3's
     * result. Throws [ExportException] when Media3 fails. Cancelling the caller stops the export; the
     * partial file stays for the caller to delete.
     */
    suspend fun run(composition: Composition, outputPath: String, onProgress: (Float) -> Unit): ExportResult {
        val thread = HandlerThread(THREAD_NAME).apply { start() }
        try {
            return withContext(Handler(thread.looper).asCoroutineDispatcher()) {
                runOnLooper(thread.looper, composition, outputPath, onProgress)
            }
        } finally {
            thread.quitSafely()
        }
    }

    private suspend fun runOnLooper(
        looper: Looper,
        composition: Composition,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ): ExportResult = coroutineScope {
        val completion = CompletableDeferred<ExportResult>()
        val transformer = Transformer.Builder(context)
            .setLooper(looper)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(exportedComposition: Composition, exportResult: ExportResult) {
                    completion.complete(exportResult)
                }

                override fun onError(
                    exportedComposition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    completion.completeExceptionally(exportException)
                }
            })
            .build()
        transformer.start(composition, outputPath)
        val progressReports = launch { reportProgress(transformer, onProgress) }
        try {
            completion.await()
        } finally {
            progressReports.cancel()
            // Does nothing once the export has ended; stops it when the caller was cancelled.
            transformer.cancel()
        }
    }

    private suspend fun reportProgress(transformer: Transformer, onProgress: (Float) -> Unit) {
        val progress = ProgressHolder()
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(progress.progress / PERCENT)
            }
        }
    }

    private companion object {
        const val THREAD_NAME = "MemixExport"
        const val PROGRESS_INTERVAL_MS = 100L
        const val PERCENT = 100f
    }
}
