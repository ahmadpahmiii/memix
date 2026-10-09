package app.memix.feature.videoeditor.timeline

import androidx.compose.runtime.Immutable
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.TrackKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The stretch of time whose thumbnail tiles the strip needs, in whole tiles around the playhead: the screen plus one
 * screen on each side (mobile-performance skill: virtualize to the visible window plus one screen). It only changes
 * when the playhead crosses a tile or the zoom changes, so a scroll frame usually asks for nothing new.
 */
@Immutable
internal data class TileWindow(val playheadTile: Long, val tileDurationUs: Long, val reachTiles: Int)

/** Null until the timeline is laid out. Tile lengths match [TimelinePainter.drawLanes], so requests hit its cache. */
internal fun tileWindowOf(playheadUs: Long, pxPerUs: Float, widthPx: Int, tileSizePx: Int): TileWindow? {
    if (pxPerUs <= 0f || widthPx <= 0 || tileSizePx <= 0) return null
    val tileDurationUs = (tileSizePx / pxPerUs).toLong()
    if (tileDurationUs <= 0) return null
    val reachTiles = ceil(widthPx * SCREENS_EACH_SIDE / tileSizePx).toInt()
    return TileWindow(playheadUs / tileDurationUs, tileDurationUs, reachTiles)
}

/** The main video's tiles inside [window], nearest the playhead first, each source frame asked for once. */
internal fun tileRequestsFor(timeline: TimelineUi, window: TileWindow): List<TileRequest> {
    val mainTrack = timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO } ?: return emptyList()
    val tileUs = window.tileDurationUs
    val centerUs = window.playheadTile * tileUs
    val startUs = centerUs - window.reachTiles * tileUs
    val endUs = centerUs + (window.reachTiles + 1) * tileUs
    val byDistance = ArrayList<Pair<Long, TileRequest>>()
    mainTrack.forEachItemBetween(startUs, endUs) { clip ->
        val media = clip.media ?: return@forEachItemBetween
        val lastTile = max(0L, (clip.durationUs - 1) / tileUs)
        val firstWanted = max(0L, (startUs - clip.startUs) / tileUs)
        val lastWanted = min(lastTile, (endUs - clip.startUs) / tileUs)
        for (tileIndex in firstWanted..lastWanted) {
            val tileStartUs = clip.startUs + tileIndex * tileUs
            val timeUs = if (clip.isPhoto) 0L else tileSourceTimeUs(clip.trimInUs, clip.trimOutUs, tileIndex.toInt(), tileUs)
            byDistance += abs(tileStartUs + tileUs / 2 - centerUs) to TileRequest(media, timeUs)
        }
    }
    return byDistance.sortedBy { it.first }.map { it.second }.distinct()
}

/** One tile the timeline wants: [source] at [timeUs] in the source file (0 for a photo). */
@Immutable
internal data class TileRequest(val source: MediaRef, val timeUs: Long)

/** Half the screen is on each side of the playhead; one more screen each side is read ahead. */
private const val SCREENS_EACH_SIDE = 1.5f
