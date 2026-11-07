package app.memix.core.domain.sticker

import kotlin.uuid.Uuid

/**
 * The device-wide sticker store (spec docs/ux/specs/P2-07-background-removal.md → Save as sticker): transparent PNGs in the app's own
 * `stickers/` folder, shared by every project. My stickers (P2-14) and the WhatsApp pack (P2-09) list it with
 * `MediaFiles.listFiles(ROOT)` (keep the `.png` names: an unfinished write has another suffix) and read the files; a placed sticker
 * is copied into its project (P2-14). Never uploaded, never part of a draft, never touched by the media cleaner (which only looks
 * inside `media/`).
 *
 * A file is named `s<time in ms>_<random id>.png`, so the list puts the newest first without an index file; a name without the time
 * (`<uuid>.png`, saved before the time was added) counts as the oldest.
 */
object StickerStore {
    const val ROOT = "stickers"

    internal fun newPath(nowEpochMillis: Long): String = "$ROOT/s${nowEpochMillis}_${Uuid.random()}.png"

    /** When the sticker at [path] was saved, in epoch milliseconds; 0 for a name that doesn't say. */
    fun savedAt(path: String): Long =
        path.substringAfterLast('/').removePrefix("s").substringBefore('_', missingDelimiterValue = "").toLongOrNull() ?: 0L
}
