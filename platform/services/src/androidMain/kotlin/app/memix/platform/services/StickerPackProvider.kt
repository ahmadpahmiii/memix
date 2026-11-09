package app.memix.platform.services

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.memix.core.domain.stickerpack.StickerPackStore
import app.memix.core.model.stickerpack.StickerPack
import java.io.File
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The provider WhatsApp reads the sticker packs from (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md; WhatsApp's Android README,
 * "ContentProvider"). Exported, but guarded by the read permission only WhatsApp holds, and read-only.
 *
 * It serves two things and nothing else: the metadata of the packs the user made, and the files such a pack lists (its stickers and its
 * tray icon). A file is opened only when its name is in that pack's `pack.json`, so no path a caller makes up (`..`, another pack's
 * folder, a project's media) reaches a file outside the pack. It reads the pack files directly instead of asking the app's dependency
 * graph, because Android creates a provider before the app's own start-up runs.
 *
 * The URIs, columns and their names are WhatsApp's contract: they must not change.
 */
class StickerPackProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var packsFolder: File

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val authority = StickerPackAuthority.of(context)
        packsFolder = File(context.filesDir, StickerPackStore.ROOT)
        matcher.addURI(authority, METADATA, ALL_PACKS)
        matcher.addURI(authority, "$METADATA/*", ONE_PACK)
        matcher.addURI(authority, "$STICKERS/*", PACK_STICKERS)
        matcher.addURI(authority, "$STICKERS_ASSET/*/*", PACK_FILE)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        when (matcher.match(uri)) {
            ALL_PACKS -> packCursor(readPacks())
            ONE_PACK -> packCursor(listOfNotNull(uri.lastPathSegment?.let(::readPack)))
            PACK_STICKERS -> stickerCursor(uri.lastPathSegment?.let(::readPack))
            else -> null
        }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = if (mode == READ_ONLY) openPackFile(uri) else null

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
        openFile(uri, mode)?.let { AssetFileDescriptor(it, 0, AssetFileDescriptor.UNKNOWN_LENGTH) }

    override fun getType(uri: Uri): String? {
        val authority = context?.let { StickerPackAuthority.of(it) } ?: return null
        return when (matcher.match(uri)) {
            ALL_PACKS -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            ONE_PACK -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            PACK_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            PACK_FILE -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
            else -> null
        }
    }

    // Read-only: WhatsApp never writes here.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only")

    private fun packCursor(packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(PACK_COLUMNS)
        for (pack in packs) {
            cursor.newRow()
                .add(pack.id).add(pack.name).add(pack.publisher).add(pack.trayFile)
                // Store links, publisher contacts and the other optional fields are empty.
                .add(null).add(null).add(null).add(null).add(null).add(null)
                .add(pack.imageDataVersion.toString())
                // 0: WhatsApp may cache the files (the flag is deprecated). 0: not an animated pack.
                .add(0).add(0)
        }
        return cursor
    }

    private fun stickerCursor(pack: StickerPack?): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        pack?.stickers?.forEach { cursor.addRow(arrayOf(it.file, it.emojis.joinToString(","), null)) }
        return cursor
    }

    private fun openPackFile(uri: Uri): ParcelFileDescriptor? {
        if (matcher.match(uri) != PACK_FILE) return null
        val segments = uri.pathSegments
        val pack = readPack(segments[1]) ?: return null
        val name = segments[2]
        val listed = name == pack.trayFile || pack.stickers.any { it.file == name }
        if (!listed) return null
        val file = File(File(packsFolder, pack.id), name)
        return try {
            // Belt and braces next to the listed-name check: the file must really sit inside the pack's folder.
            if (!file.canonicalPath.startsWith(File(packsFolder, pack.id).canonicalPath + File.separator)) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: IOException) {
            null
        }
    }

    private fun readPacks(): List<StickerPack> =
        packsFolder.listFiles()?.filter { it.isDirectory }?.mapNotNull { readPack(it.name) }.orEmpty().sortedBy { it.createdAtEpochUs }

    // The pack in folder [id], or null for an id that isn't a UUID-shaped folder name, a missing or unreadable file, or a pack waiting out its delete.
    private fun readPack(id: String): StickerPack? {
        if (!PACK_ID.matches(id)) return null
        val file = File(File(packsFolder, id), StickerPackStore.PACK_FILE)
        return try {
            json.decodeFromString(StickerPack.serializer(), file.readText()).takeIf { it.id == id && !it.removed }
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val STICKERS_ASSET = "stickers_asset"
        const val ALL_PACKS = 1
        const val ONE_PACK = 2
        const val PACK_STICKERS = 3
        const val PACK_FILE = 4
        const val READ_ONLY = "r"

        // Pack ids are UUIDs: anything else (a dot, a slash) is refused before a path is built. File names are never checked by shape, only by
        // being listed in the pack's own pack.json.
        val PACK_ID = Regex("[A-Za-z0-9_-]+")

        // WhatsApp's column names, in its order (README "ContentProvider"; the sample app's StickerContentProvider).
        val PACK_COLUMNS = arrayOf(
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
            "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
        )
        val STICKER_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")
    }
}

/** The provider's authority: the app's id plus a fixed name (the manifest declares `${applicationId}.stickercontentprovider`). */
internal object StickerPackAuthority {
    fun of(context: Context): String = "${context.packageName}.stickercontentprovider"
}
