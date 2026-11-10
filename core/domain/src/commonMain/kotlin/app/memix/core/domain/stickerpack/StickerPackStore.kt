package app.memix.core.domain.stickerpack

import kotlin.uuid.Uuid

/**
 * Where the WhatsApp packs live (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md): one folder per pack inside the app's files
 * directory, holding `pack.json`, the prepared stickers and the tray icon. The ContentProvider WhatsApp reads serves only these
 * folders and only the files a pack lists; nothing from a project, and nothing else in the files directory, is ever reachable.
 * Paths are relative to the files directory, like every media path.
 */
object StickerPackStore {
    const val ROOT = "stickerpacks"
    const val PACK_FILE = "pack.json"

    fun newId(): String = Uuid.random().toString()

    fun folderOf(packId: String): String = "$ROOT/$packId"

    fun pathOf(packId: String, file: String): String = "$ROOT/$packId/$file"

    /** Sticker [index] (from 0) of the pack's version [version]. The version is in the name, so a new version never overwrites a file WhatsApp may be reading. */
    fun stickerFileName(index: Int, version: Long): String = "s${index + 1}_v$version.webp"

    fun trayFileName(version: Long): String = "tray_v$version.png"
}
