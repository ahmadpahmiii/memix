package app.memix.core.domain.sticker

import kotlin.uuid.Uuid

/**
 * The device-wide sticker store (spec docs/ux/specs/P2-07-background-removal.md → Save as sticker): transparent PNGs in the app's own
 * `stickers/` folder as `<uuid>.png`, shared by every project. My stickers (P2-14) and the WhatsApp pack (P2-09) list it with
 * `MediaFiles.listFiles(ROOT)` (keep the `.png` names: an unfinished write has another suffix) and read the files; a placed sticker
 * is copied into its project (P2-14). Never uploaded, never part of a draft, never touched by the media cleaner (which only looks
 * inside `media/`).
 */
object StickerStore {
    const val ROOT = "stickers"

    internal fun newPath(): String = "$ROOT/${Uuid.random()}.png"
}
