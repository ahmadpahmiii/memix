package app.memix.core.model.stickerpack

import kotlinx.serialization.Serializable

/**
 * A WhatsApp sticker pack the user made (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md). Stored as `pack.json` next to its
 * prepared files; the provider WhatsApp reads is built from the same file.
 *
 * [id] never changes (letters, digits and `-` only). [imageDataVersion] goes up on every save, which tells WhatsApp the pack has new
 * content. Files are named after their version, so a new version never overwrites a file WhatsApp may still be reading.
 */
@Serializable
data class StickerPack(
    val id: String,
    val name: String,
    val imageDataVersion: Long,
    /** The tray icon: a 96 x 96 PNG, the pack's first sticker. */
    val trayFile: String,
    val stickers: List<PackSticker>,
    val createdAtEpochUs: Long,
    /** When the app first saw this pack in WhatsApp, or null if it never did. A pack added before reads "Update in WhatsApp". */
    val addedToWhatsAppAtEpochUs: Long? = null,
    /** Waiting out the Undo window of a delete: hidden from the list and from WhatsApp, erased when the window ends. */
    val removed: Boolean = false,
    val publisher: String = StickerPackLimits.PUBLISHER,
)

/** One prepared sticker: a 512 x 512 WebP in the pack's folder, made from the My stickers file [sourcePath] (so editing can pre-select it). */
@Serializable
data class PackSticker(
    val file: String,
    val sourcePath: String,
    /** 1 to 3 emojis, in the order shown. */
    val emojis: List<String>,
)
