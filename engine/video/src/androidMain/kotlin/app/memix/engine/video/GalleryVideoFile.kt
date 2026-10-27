package app.memix.engine.video

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import androidx.media3.muxer.MuxerUtil
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.transformer.InAppMp4Muxer
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An export's file in the phone's gallery (spec P1-12 → Finished; research P1-12-encoder-facts.md → 4): a MediaStore
 * item in `Movies/Memix`, inserted pending, so no other app sees it until [publish]. No storage permission is needed
 * for media the app adds itself (Android 10+, our minSdk).
 *
 * [WRITES_STRAIGHT_TO_GALLERY] picks how the encoder's output gets there: straight into the item through its file
 * descriptor ([muxerFactory]), or into a temporary file that [publish] copies in (twice the space). The straight
 * write is the plan; the copy is the fallback if the hand check on Android 10 and 11+ finds the descriptor doesn't
 * seek (engineer hand checks → P1-12 F).
 */
@OptIn(UnstableApi::class)
internal class GalleryVideoFile private constructor(private val resolver: ContentResolver, val uri: Uri, private val tempFile: File?) {

    /** The muxer the export writes with: into the pending item, or the temporary file when copying. */
    fun muxerFactory(): Muxer.Factory =
        if (tempFile == null) PendingItemMuxerFactory(resolver, uri) else InAppMp4Muxer.Factory()

    /** The path Transformer is given: the temporary file, or a placeholder the gallery muxer ignores. */
    val transformerPath: String get() = tempFile?.path ?: PLACEHOLDER_PATH

    /** Makes the finished file visible in the gallery and returns its size in bytes. */
    fun publish(): Long {
        if (tempFile != null) {
            resolver.openOutputStream(uri, "w")?.use { output -> tempFile.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("The gallery item can't be written")
            tempFile.delete()
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }

    /** Deletes the unfinished item (a failed or cancelled export). */
    fun discard() {
        tempFile?.delete()
        resolver.delete(uri, null, null)
    }

    companion object {
        /**
         * True: the encoder writes straight into the gallery item (needs the estimate's space once). False: a temporary
         * file plus a copy (twice). The one switch for the P1-12 device check's result.
         */
        const val WRITES_STRAIGHT_TO_GALLERY = true

        private const val ALBUM = "Movies/Memix"
        private const val PLACEHOLDER_PATH = "gallery-item.mp4"

        /** A new pending item named `Memix_<yyyyMMdd_HHmmss>.mp4`, with a temporary file in [cacheDir] when copying. */
        fun create(context: Context, cacheDir: File): GalleryVideoFile {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Memix_${SimpleDateFormat(NAME_TIME_FORMAT, Locale.US).format(Date())}.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, MimeTypes.VIDEO_MP4)
                put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection(), values) ?: throw IOException("The gallery refused a new video")
            val tempFile = if (WRITES_STRAIGHT_TO_GALLERY) null else File.createTempFile("gallery-", ".mp4", cacheDir)
            return GalleryVideoFile(resolver, uri, tempFile)
        }

        /** Deletes this app's pending items in the album: exports a stopped run never finished. */
        fun discardUnfinished(context: Context) {
            val resolver = context.contentResolver
            val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1 AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("$ALBUM%")
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val query = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(collection(), projection, query, null)
            } else {
                @Suppress("DEPRECATION")
                resolver.query(MediaStore.setIncludePending(collection()), projection, selection, args, null)
            }
            val ids = cursor?.use { rows -> buildList { while (rows.moveToNext()) add(rows.getLong(0)) } }.orEmpty()
            ids.forEach { id -> resolver.delete(Uri.withAppendedPath(collection(), id.toString()), null, null) }
        }

        private fun collection(): Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        private const val NAME_TIME_FORMAT = "yyyyMMdd_HHmmss"
    }
}

/**
 * Media3's MP4 muxer writing into a gallery item's file descriptor instead of a path (Transformer only takes paths).
 * It must seek and truncate, never read. [create] ignores the path it's given.
 */
@OptIn(UnstableApi::class)
private class PendingItemMuxerFactory(private val resolver: ContentResolver, private val uri: Uri) : Muxer.Factory {
    private val mp4Factory = InAppMp4Muxer.Factory()

    override fun create(path: String): Muxer {
        val descriptor = try {
            resolver.openFileDescriptor(uri, "rw")
        } catch (e: IOException) {
            throw MuxerException("The gallery item can't be opened", e)
        } ?: throw MuxerException("The gallery item can't be opened", IOException(uri.toString()))
        // Closing the stream closes the descriptor too.
        val output = SeekableMuxerOutput.of(ParcelFileDescriptor.AutoCloseOutputStream(descriptor))
        return OrientedMp4Muxer(Mp4Muxer.Builder(output).setAttemptStreamableOutputEnabled(true).build())
    }

    override fun getSupportedSampleMimeTypes(trackType: @C.TrackType Int): ImmutableList<String> =
        mp4Factory.getSupportedSampleMimeTypes(trackType)

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean = mp4Factory.supportsWritingNegativeTimestampsInEditList()
}

/**
 * What InAppMp4Muxer adds around [Mp4Muxer] (Media3 1.11.1; its constructor is private): the video track's rotation as
 * orientation data, without which every portrait export plays sideways (Transformer encodes portrait frames turned),
 * and only the metadata MP4 supports.
 */
@OptIn(UnstableApi::class)
private class OrientedMp4Muxer(private val muxer: Mp4Muxer) : Muxer {
    private val metadata = LinkedHashSet<Metadata.Entry>()

    override fun addTrack(format: Format): Int {
        val trackId = muxer.addTrack(format)
        if (MimeTypes.isVideo(format.sampleMimeType)) muxer.addMetadataEntry(Mp4OrientationData(format.rotationDegrees))
        return trackId
    }

    override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) = muxer.writeSampleData(trackId, byteBuffer, bufferInfo)

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
        if (MuxerUtil.isMetadataSupported(metadataEntry)) metadata.add(metadataEntry)
    }

    override fun close() {
        metadata.forEach(muxer::addMetadataEntry)
        muxer.close()
    }
}
