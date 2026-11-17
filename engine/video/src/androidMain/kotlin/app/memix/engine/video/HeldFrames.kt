package app.memix.engine.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.memix.core.model.project.MediaRef
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.io.FileOutputStream
import kotlin.math.max

/** One frame to hold in a transition: [source]'s picture at [atUs] (µs in the source). */
internal data class HoldKey(val source: MediaRef, val atUs: Long)

/**
 * The held frames of the transitions (P4-08): where a clip has no footage beyond its trim, its edge frame is held for the stretch of the window
 * the missing footage would have filled. Each frame is read once from the clip's own file into a JPEG in the app's cache and played like a photo,
 * so a hold is the same picture in the preview and the export. Reads and writes files: call it off the main thread.
 */
internal class HeldFrames(private val context: Context, private val cacheDir: File) {
    @Volatile private var pruned = false

    /** The still picture for every hold in [plan] whose source file is in [uriByMedia] and could be read; the others are left out (that side of the cut has nothing beneath it). */
    fun resolve(plan: CompositionPlan, uriByMedia: Map<MediaRef, Uri>): Map<HoldKey, Uri> {
        // Once per process, at the first resolve (already off the main thread): the folder of old holds is trimmed before it can grow.
        if (!pruned) {
            pruned = true
            prune()
        }
        val holds = plan.transitionLayer?.segments.orEmpty().filterIsInstance<PartnerHold>()
        if (holds.isEmpty()) return emptyMap()
        val found = mutableMapOf<HoldKey, Uri>()
        for (hold in holds) {
            val key = HoldKey(hold.clip.source, hold.atUs)
            if (key in found) continue
            val sourceUri = uriByMedia[key.source] ?: continue
            fileFor(key, sourceUri)?.let { found[key] = Uri.fromFile(it) }
        }
        return found
    }

    private fun fileFor(key: HoldKey, sourceUri: Uri): File? {
        val folder = File(cacheDir, FOLDER).apply { mkdirs() }
        val file = File(folder, nameOf(key))
        if (file.isFile && file.length() > 0) return file.also { it.setLastModified(System.currentTimeMillis()) }
        val temp = File(folder, "${file.name}.tmp")
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            retriever.setDataSource(context, sourceUri)
            frame = retriever.getFrameAtTime(key.atUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            scaled = scaledDown(frame)
            FileOutputStream(temp).use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            // Written whole, then renamed: a half-written file never looks like a finished hold.
            if (temp.renameTo(file)) file else null
        } catch (e: RuntimeException) {
            // An unreadable or unsupported file: that side of the cut has nothing beneath it.
            null
        } catch (e: IOException) {
            null
        } finally {
            if (scaled != null && scaled !== frame) scaled.recycle()
            frame?.recycle()
            temp.delete()
            retriever.release()
        }
    }

    /** Deletes held frames not used for [MAX_AGE_MS] (they are made again when needed). Runs once, at the first resolve. */
    fun prune(nowMs: Long = System.currentTimeMillis()) {
        File(cacheDir, FOLDER).listFiles()?.forEach { if (nowMs - it.lastModified() > MAX_AGE_MS) it.delete() }
    }

    // A name that is the same on every launch: a hash of the file and the time, never an identity hash.
    private fun nameOf(key: HoldKey): String {
        val text = "${key.source.cachedCopyPath ?: key.source.origin}@${key.atUs}"
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) } + ".jpg"
    }

    // A frame bigger than any canvas is cut down to MAX_SIDE_PX on its long side; Media3 scales it to the frame like any picture.
    private fun scaledDown(frame: Bitmap): Bitmap {
        val longSide = max(frame.width, frame.height)
        if (longSide <= MAX_SIDE_PX) return frame
        val scale = MAX_SIDE_PX.toFloat() / longSide
        return Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        const val FOLDER = "transition-holds"
        const val MAX_SIDE_PX = 1920
        const val JPEG_QUALITY = 95
        const val HASH_BYTES = 10
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
