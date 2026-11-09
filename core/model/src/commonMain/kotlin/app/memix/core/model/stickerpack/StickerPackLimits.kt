package app.memix.core.model.stickerpack

/**
 * What WhatsApp accepts in a sticker pack, and the choices Memix offers (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md; values
 * in `design/tokens.json` -> `photo.stickerPack`). The limits come from WhatsApp's official Android README
 * (github.com/WhatsApp/stickers, read again on 8 Nov 2026): change them there first, never here alone.
 */
object StickerPackLimits {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30

    /** Packs Memix keeps at once. WhatsApp allows 1 to 10 packs per sticker app. */
    const val MAX_PACKS = 10

    /** Every sticker is exactly this many pixels on each side. */
    const val STICKER_SIZE_PX = 512

    /** "100 KB or less", counted in 1,000 bytes so the file is under the limit whichever way WhatsApp counts. */
    const val STICKER_MAX_BYTES = 100_000

    const val TRAY_SIZE_PX = 96
    const val TRAY_MAX_BYTES = 50_000

    const val MIN_EMOJIS = 1
    const val MAX_EMOJIS = 3
    const val DEFAULT_EMOJI = "\uD83D\uDE42"

    /** WhatsApp allows 128 characters; the screen caps the name at 30 so it fits WhatsApp's list. */
    const val NAME_MAX_CHARS = 30

    /** The publisher of every pack (spec: fixed in v1, no field). */
    const val PUBLISHER = "Memix"

    /** The 48 emojis of the picker (no search), in display order. Data, not strings: emojis are not translated. */
    val EMOJI_CHOICES: List<String> = listOf(
        "😀", "😂", "🤣", "😍", "😎", "🥳", "😭", "😡",
        "🤔", "🙄", "😴", "🤯", "😱", "🥺", "😅", "😬",
        "🤪", "😏", "👍", "👎", "👏", "🙏", "💪", "🤝",
        "👀", "💀", "🔥", "❤️", "💯", "🎉", "✨", "💩",
        "🤡", "👻", "🐶", "🐱", "🐸", "🐵", "🍕", "🍔",
        "☕", "⚽", "🚗", "🌧️", "☀️", "🌈", "💸", "🎵",
    )
}
