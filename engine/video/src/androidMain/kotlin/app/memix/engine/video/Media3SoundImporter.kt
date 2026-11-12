package app.memix.engine.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import app.memix.core.domain.Logger
import app.memix.core.domain.mysound.MySoundFailure
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.domain.mysound.SoundConversion
import app.memix.core.domain.mysound.SoundImporter
import app.memix.core.domain.mysound.SoundProbe
import app.memix.core.model.project.MediaOrigin
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My sounds' import with Media3 (P3-07). [probe] reads the pick's metadata with MediaMetadataRetriever through a file
 * descriptor the provider hands over (so a refused grant, a missing file and a format the phone can't read are told apart);
 * [convert] runs an audio-only Transformer export to AAC at 128 kbps in an MP4 container (`.m4a`), reading the picked item
 * straight from its provider. A source that is already AAC may be copied as it is by the Transformer, keeping its bitrate;
 * every other codec is decoded and encoded. Nothing is loudness-normalized (the catalog is, −16 LUFS).
 *
 * Logs the class of an error, never a message, a name or a URI.
 */
@OptIn(UnstableApi::class)
internal class Media3SoundImporter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : SoundImporter {
    private val filesDir: File get() = context.filesDir

    override suspend fun probe(origin: MediaOrigin, source: MySoundSource): SoundProbe = withContext(ioDispatcher) {
        val uri = (origin as? MediaOrigin.GalleryUri)?.let { Uri.parse(it.uri) } ?: return@withContext unreadable(null, null)
        val (displayName, sizeBytes) = nameAndSize(uri)
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
                // A pipe or stream from a provider can't be seeked, which the retriever and the Transformer need.
            if (descriptor == null || descriptor.statSize < 0) return@withContext unreadable(displayName, sizeBytes)
                retriever.setDataSource(descriptor.fileDescriptor)
            }
            val facts = retriever.facts()
            val failure = when {
                !facts.hasAudio -> if (source == MySoundSource.VIDEO && facts.hasVideo) MySoundFailure.NO_AUDIO else MySoundFailure.UNSUPPORTED
                source == MySoundSource.VIDEO && !facts.hasVideo -> MySoundFailure.UNSUPPORTED
                else -> null
            }
            SoundProbe(displayName, sizeBytes, facts.durationUs, failure)
        } catch (e: SecurityException) {
            SoundProbe(displayName, sizeBytes, null, MySoundFailure.DENIED)
        } catch (e: IOException) {
            unreadable(displayName, sizeBytes)
        } catch (e: RuntimeException) {
            // The retriever's own way to say it can't read the file: not a sound, or a format this phone doesn't know.
            logger.debug(TAG, "Probe failed: ${e::class.simpleName}")
            SoundProbe(displayName, sizeBytes, null, MySoundFailure.UNSUPPORTED)
        } finally {
            retriever.release()
        }
    }

    override suspend fun convert(origin: MediaOrigin, destination: String, onProgress: (Float) -> Unit): SoundConversion {
        val uri = (origin as? MediaOrigin.GalleryUri)?.let { Uri.parse(it.uri) } ?: return SoundConversion.Failed(MySoundFailure.UNREADABLE)
        val target = File(filesDir, destination)
        val unfinished = File(target.path + UNFINISHED_SUFFIX)
        var complete = false
        val thread = HandlerThread(THREAD_NAME).apply { start() }
        try {
            // The empty .part file makes the folder non-empty at once, so a delete that removes an empty My sounds folder can't
            // take it away between here and the Transformer's first write.
            withContext(ioDispatcher) {
                target.parentFile?.mkdirs()
                unfinished.createNewFile()
            }
            withContext(Handler(thread.looper).asCoroutineDispatcher()) { run(uri, unfinished, thread, onProgress) }
            complete = withContext(ioDispatcher) { unfinished.renameTo(target) }
            return if (complete) SoundConversion.Converted else SoundConversion.Failed(MySoundFailure.UNREADABLE)
        } catch (e: ExportException) {
            logger.debug(TAG, "Conversion failed: ${e.errorCodeName}")
            return SoundConversion.Failed(failureOf(e))
        } finally {
            thread.quitSafely()
            if (!complete) unfinished.delete()
        }
    }

    // A Transformer must be used from one looper thread; this one is ours, so the main thread stays free.
    private suspend fun run(uri: Uri, output: File, thread: HandlerThread, onProgress: (Float) -> Unit) = coroutineScope {
        val completion = CompletableDeferred<Unit>()
        val audioEncoder = AudioEncoderSettings.Builder().setBitrate(AAC_BITRATE_BPS).build()
        val transformer = Transformer.Builder(context)
            .setLooper(thread.looper)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            // No data leaves the phone: the platform's media diagnostics service would see the file's metadata.
            .setUsePlatformDiagnostics(false)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedAudioEncoderSettings(audioEncoder).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    completion.complete(Unit)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: ExportException,
                ) {
                    completion.completeExceptionally(exportException)
                }
            })
            .build()
        // Audio only: a video's picture is dropped, so the output is the sound alone.
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
        transformer.start(item, output.path)
        val reports = launch { reportProgress(transformer, onProgress) }
        try {
            completion.await()
        } finally {
            reports.cancel()
            // Does nothing once the export has ended; stops it when the caller was cancelled.
            transformer.cancel()
        }
    }

    private suspend fun reportProgress(transformer: Transformer, onProgress: (Float) -> Unit) {
        val progress = ProgressHolder()
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress / PERCENT)
        }
    }

    private fun failureOf(error: ExportException): MySoundFailure = when {
        error.errorCode == ExportException.ERROR_CODE_IO_NO_PERMISSION -> MySoundFailure.DENIED
        generateSequence<Throwable>(error) { it.cause }.any { it is SecurityException } -> MySoundFailure.DENIED
        isOutOfSpace(error) -> MySoundFailure.STORAGE
        error.errorCode in UNREADABLE_CODES -> MySoundFailure.UNREADABLE
        else -> MySoundFailure.UNSUPPORTED
    }

    // A full disk surfaces as a muxing failure caused by ENOSPC; a phone with almost nothing left counts too.
    private fun isOutOfSpace(error: ExportException): Boolean {
        if (error.errorCode != ExportException.ERROR_CODE_MUXING_FAILED && error.errorCode != ExportException.ERROR_CODE_IO_UNSPECIFIED) return false
        val causes = generateSequence<Throwable>(error) { it.cause }
        val noSpace = causes.any { (it as? ErrnoException)?.errno == OsConstants.ENOSPC || it.message?.contains("ENOSPC") == true }
        return noSpace || filesDir.usableSpace < LOW_SPACE_BYTES
    }

    private fun nameAndSize(uri: Uri): Pair<String?, Long?> = try {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex < 0 || cursor.isNull(nameIndex)) null else cursor.getString(nameIndex)
            val size = if (sizeIndex < 0 || cursor.isNull(sizeIndex)) null else cursor.getLong(sizeIndex).takeIf { it >= 0 }
            name to size
        } ?: (null to null)
    } catch (e: RuntimeException) {
        // The name is a nicety; a provider that refuses the query is told apart when the file is opened.
        null to null
    }

    private fun unreadable(displayName: String?, sizeBytes: Long?) = SoundProbe(displayName, sizeBytes, null, MySoundFailure.UNREADABLE)

    private class Facts(val hasAudio: Boolean, val hasVideo: Boolean, val durationUs: Long?)

    private fun MediaMetadataRetriever.facts(): Facts {
        val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        return Facts(
            hasAudio = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == YES,
            hasVideo = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == YES,
            durationUs = durationMs?.takeIf { it > 0 }?.times(MICROS_PER_MILLI),
        )
    }

    private companion object {
        const val TAG = "MySounds"
        const val THREAD_NAME = "MemixSoundImport"
        const val UNFINISHED_SUFFIX = ".part"
        const val YES = "yes"
        const val AAC_BITRATE_BPS = 128_000
        const val PROGRESS_INTERVAL_MS = 100L
        const val PERCENT = 100f
        const val MICROS_PER_MILLI = 1_000L
        const val LOW_SPACE_BYTES = 1_000_000L
        val UNREADABLE_CODES = setOf(
            ExportException.ERROR_CODE_IO_UNSPECIFIED,
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ExportException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        )
    }
}
