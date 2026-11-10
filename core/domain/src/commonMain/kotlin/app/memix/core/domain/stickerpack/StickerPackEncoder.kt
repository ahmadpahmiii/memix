package app.memix.core.domain.stickerpack

/**
 * Turns a My stickers file into what WhatsApp accepts (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md -> Rules). Implemented in
 * `:engine:photo`. Both functions run their work off the main thread, can be cancelled, and report a file that can't be read in their
 * result instead of throwing.
 */
interface StickerPackEncoder {
    /**
     * [sourcePath] (relative to the files directory) fitted inside 512 x 512 on a transparent background and encoded as a static
     * WebP of 100 KB or less, at the best quality that fits.
     */
    suspend fun encodeSticker(sourcePath: String): StickerEncoding

    /** [sourcePath] fitted inside 96 x 96 as a PNG of 50 KB or less, or null when the file can't be read. */
    suspend fun encodeTray(sourcePath: String): ByteArray?
}

sealed interface StickerEncoding {
    /** The finished file; [webp] is never changed by the encoder afterwards. */
    class Encoded(val webp: ByteArray) : StickerEncoding

    /** Even at the lowest quality the picture still looks right at, the file stays over 100 KB. */
    data object TooDetailed : StickerEncoding

    /** The source file is missing or can't be decoded. */
    data object Unreadable : StickerEncoding
}
