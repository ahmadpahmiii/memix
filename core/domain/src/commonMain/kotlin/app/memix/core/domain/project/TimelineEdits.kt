package app.memix.core.domain.project

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.FreezeReverseCatalog
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.pictureChangeAtEnd
import app.memix.core.model.project.pictureTakenFromStart
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.withKeyframes

/*
 * The video editor's clip edits (spec docs/ux/specs/P1-06-clip-edits-and-audio.md → Edits). Each one returns a new
 * Project and leaves this one alone; one that can't apply returns this project unchanged, which the edit session
 * records as no step. The editor checks the "can" functions first, so it can explain instead.
 *
 * Two rules hold after every edit:
 * - The main video track is gap-free from 0:00, in its clips' order (ripple). Trims, deletes, duplicates and
 *   reorders of main clips re-pack it.
 * - Every other track keeps absolute times: its items never follow the main track (owner, 8 Oct 2026). An emptied
 *   non-main track is removed in the same step.
 */

/** The shortest any item can be after a split or a trim: 0.1 s. */
const val MIN_ITEM_DURATION_US = 100_000L

/** Which end of an item a trim moves. */
enum class TrimEdge { START, END }

/** Where a trimmed edge may go, in project µs: [minUs]..[maxUs], both included. */
data class TrimRange(val minUs: Long, val maxUs: Long)

/** True when the playhead at [atUs] is inside the item, at least [MIN_ITEM_DURATION_US] from both of its edges. */
fun Project.canSplit(itemId: String, atUs: Long): Boolean {
    val item = findItem(itemId)?.item ?: return false
    return atUs >= item.startUs + MIN_ITEM_DURATION_US && atUs <= item.startUs + item.durationUs - MIN_ITEM_DURATION_US
}

/**
 * Cuts the item in two at [atUs]: the left piece keeps [itemId], the right piece from [atUs] on gets [newItemId].
 * Media and sounds continue in their source where the left piece stops; every other property is copied.
 */
fun Project.splitItem(itemId: String, atUs: Long, newItemId: String): Project {
    if (!canSplit(itemId, atUs)) return this
    val (track, item) = findItem(itemId) ?: return this
    val leftLengthUs = atUs - item.startUs
    val (leftKeyframes, rightKeyframes) = item.keyframesAfterSplit(leftLengthUs)
    // The transition leads from the clip's end into the next clip, so it goes with the right piece; the cut between the pieces is plain (P4-08).
    val left = item.withLength(leftLengthUs).withoutFade(FadeEdge.OUT).withFadesFitted().withKeyframesIfAny(leftKeyframes).withoutTransition()
    // A curved clip's left piece can come out a little longer than asked (the cut re-eases the curve): the right piece starts where it really ends, so the two never overlap.
    val rightStartUs = maxOf(atUs, left.startUs + left.durationUs)
    val right = item.withId(newItemId).withStart(rightStartUs).trimmedFromStart(leftLengthUs).withoutFade(FadeEdge.IN).withFadesFitted().withKeyframesIfAny(rightKeyframes)
    val items = track.items.flatMap { if (it.id == itemId) listOf(left, right) else listOf(it) }
    // A sped clip's two pieces can be a few µs shorter than the whole; the main track closes that up (a no-op at 1x).
    return replaceTrack(track.copy(items = if (track.kind == TrackKind.MAIN_VIDEO) repacked(items.sortedBy { it.startUs }) else items))
}

/**
 * How far [edge] of the item can be dragged: the item stays at least [MIN_ITEM_DURATION_US] long; a video or sound
 * stops at its source file's start or end (a source of unknown length can only get shorter); photos, text, stickers
 * and effects have no source limit. On other tracks a neighbor on the same track is a hard stop and nothing starts
 * before 0:00. Main clips may overlap or leave a gap while dragged; the release re-packs the track.
 */
fun Project.trimRange(itemId: String, edge: TrimEdge): TrimRange? {
    val (track, item) = findItem(itemId) ?: return null
    val startUs = item.startUs
    val endUs = item.startUs + item.durationUs
    val isMain = track.kind == TrackKind.MAIN_VIDEO
    // The edge's current place is always allowed, so an item already shorter than the minimum (or one placed
    // against the rules by an older build) can still be dragged out, and the range is never empty.
    return when (edge) {
        TrimEdge.START -> {
            val sourceLimitUs = item.footageBeforeUs()?.let { startUs - it } ?: UNLIMITED_EARLIER_US
            val limitedUs = item.maxLengthUs()?.let { maxOf(sourceLimitUs, endUs - it) } ?: sourceLimitUs
            val minUs = if (isMain) limitedUs else maxOf(limitedUs, 0L, track.previousEndUs(item))
            TrimRange(minOf(minUs, startUs), maxOf(endUs - MIN_ITEM_DURATION_US, startUs))
        }
        TrimEdge.END -> {
            val sourceLimitUs = item.footageAfterUs()?.let { endUs + it } ?: UNLIMITED_LATER_US
            val limitedUs = item.maxLengthUs()?.let { minOf(sourceLimitUs, startUs + it) } ?: sourceLimitUs
            val maxUs = if (isMain) limitedUs else minOf(limitedUs, track.nextStartUs(item))
            TrimRange(minOf(startUs + MIN_ITEM_DURATION_US, endUs), maxOf(maxUs, endUs))
        }
    }
}

/** Moves [edge] of the item to [edgeUs] (clamped to [trimRange]); on the main track the clips then close up. */
fun Project.trimItem(itemId: String, edge: TrimEdge, edgeUs: Long): Project {
    val range = trimRange(itemId, edge) ?: return this
    val (track, item) = findItem(itemId) ?: return this
    val clampedUs = edgeUs.coerceIn(range.minUs, range.maxUs)
    val trimmed = when (edge) {
        // The keyframes stay where they were on the timeline, so trimming never changes how the rest moves (spec P4-02b → How other edits treat keyframes).
        TrimEdge.START -> item.withStart(clampedUs).trimmedFromStart(clampedUs - item.startUs).withKeyframesShifted(clampedUs - item.startUs)
        TrimEdge.END -> item.withLength(clampedUs - item.startUs)
    }.withFadesFitted()
    if (track.kind != TrackKind.MAIN_VIDEO) return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) trimmed else it }))
    // The order from before the trim: a start dragged past the previous clip's start doesn't swap the two.
    return replaceTrack(track.copy(items = repacked(mainClipsInOrder(track).map { if (it.id == itemId) trimmed else it })))
}

/** False only for the last clip of the main video track: a video project needs at least one. */
fun Project.canDelete(itemId: String): Boolean {
    val (track, _) = findItem(itemId) ?: return false
    return track.kind != TrackKind.MAIN_VIDEO || track.items.size > 1
}

/** Removes the item. Later main clips close up; an emptied non-main track goes too. */
fun Project.deleteItem(itemId: String): Project {
    if (!canDelete(itemId)) return this
    val (track, _) = findItem(itemId) ?: return this
    if (track.kind == TrackKind.MAIN_VIDEO) {
        // The clip before it now leads into a different clip, so its transition (made for the old pair) goes (P4-08).
        val ordered = mainClipsInOrder(track)
        val index = ordered.indexOfFirst { it.id == itemId }
        val remaining = ordered.filterNot { it.id == itemId }.mapIndexed { place, item -> if (place == index - 1) item.withoutTransition() else item }
        return replaceTrack(track.copy(items = repacked(remaining)))
    }
    val items = track.items.filterNot { it.id == itemId }
    if (items.isEmpty()) return withTracks(tracks().filterNot { it.id == track.id })
    return replaceTrack(track.copy(items = items))
}

/**
 * Copies the item as [newItemId], right after the original. On the main track later clips move along. Elsewhere
 * the copy stays on the original's track if that time is free, else goes to the first track of the same kind
 * that's free there, else to a new track ([newTrackId]) after the last one of that kind. A copy of a clip whose sound
 * is detached gets its own sound back at 0%: no detached clip lies under the copy, and nothing gets louder.
 */
fun Project.duplicateItem(itemId: String, newItemId: String, newTrackId: String): Project {
    val (track, item) = findItem(itemId) ?: return this
    val copyStartUs = item.startUs + item.durationUs
    val copy = item.withId(newItemId).withStart(copyStartUs).withOwnSoundAtZero()
    if (track.kind == TrackKind.MAIN_VIDEO) {
        val ordered = mainClipsInOrder(track)
        val index = ordered.indexOfFirst { it.id == itemId }
        val items = ordered.subList(0, index + 1) + copy + ordered.subList(index + 1, ordered.size)
        return replaceTrack(track.copy(items = repacked(items)))
    }
    val copyEndUs = copyStartUs + copy.durationUs
    val otherTracksOfKind = tracks().filter { it.kind == track.kind && it.id != track.id }
    val target = (listOf(track) + otherTracksOfKind).firstOrNull { it.isFree(copyStartUs, copyEndUs) }
    if (target != null) return replaceTrack(target.copy(items = target.items + copy))
    val newTrack = Track(newTrackId, track.kind, listOf(copy))
    val insertAfter = tracks().indexOfLast { it.kind == track.kind }
    return withTracks(tracks().toMutableList().apply { add(insertAfter + 1, newTrack) })
}

/** The main video clips' ids in play order. */
fun Project.mainClipIds(): List<String> = mainTrack()?.let { mainClipsInOrder(it).map(TimelineItem::id) }.orEmpty()

/** Plays the main clips in [orderedIds] order (the same ids, rearranged), back to back from 0:00. */
fun Project.reorderMainClips(orderedIds: List<String>): Project {
    val track = mainTrack() ?: return this
    val byId = track.items.associateBy { it.id }
    if (orderedIds.size != byId.size || orderedIds.toSet() != byId.keys) return this
    return replaceTrack(track.copy(items = repacked(orderedIds.map { byId.getValue(it) })))
}

/** Moves a main clip one place earlier ([steps] -1) or later (+1), or more; clamped to the ends. */
fun Project.moveMainClip(itemId: String, steps: Int): Project {
    val ids = mainClipIds()
    val from = ids.indexOf(itemId)
    if (from < 0) return this
    val to = (from + steps).coerceIn(0, ids.lastIndex)
    if (to == from) return this
    return reorderMainClips(ids.toMutableList().apply { add(to, removeAt(from)) })
}

// ---- Helpers (the internal ones are shared with SoundEdits.kt, MoveEdits.kt and MediaEdits.kt) ----------------------

/** No source limit: far enough that no drag reaches it, near enough that adding a screen of time can't overflow. */
private const val UNLIMITED_EARLIER_US = Long.MIN_VALUE / 4
private const val UNLIMITED_LATER_US = Long.MAX_VALUE / 4

internal data class Found(val track: Track, val item: TimelineItem)

internal fun Project.tracks(): List<Track> = video?.tracks.orEmpty()

internal fun Project.mainTrack(): Track? = tracks().firstOrNull { it.kind == TrackKind.MAIN_VIDEO }

internal fun Project.findItem(itemId: String): Found? {
    for (track in tracks()) {
        val item = track.items.firstOrNull { it.id == itemId } ?: continue
        return Found(track, item)
    }
    return null
}

internal fun Project.withTracks(tracks: List<Track>): Project {
    val timeline = video ?: return this
    return copy(video = timeline.copy(tracks = tracks))
}

internal fun Project.replaceTrack(track: Track): Project = withTracks(tracks().map { if (it.id == track.id) track else it })

// Main clips by start, the model's order breaking ties, so re-packing never swaps two clips.
internal fun mainClipsInOrder(track: Track): List<TimelineItem> = track.items.sortedBy { it.startUs }

/** [items] back to back from 0:00, in the order given. */
internal fun repacked(items: List<TimelineItem>): List<TimelineItem> {
    var nextStartUs = 0L
    return items.map { item ->
        item.withStart(nextStartUs).also { nextStartUs += item.durationUs }
    }
}

private fun Track.previousEndUs(item: TimelineItem): Long =
    items.filter { it.id != item.id && it.startUs + it.durationUs <= item.startUs }.maxOfOrNull { it.startUs + it.durationUs } ?: 0L

private fun Track.nextStartUs(item: TimelineItem): Long {
    val endUs = item.startUs + item.durationUs
    return items.filter { it.id != item.id && it.startUs >= endUs }.minOfOrNull { it.startUs } ?: UNLIMITED_LATER_US
}

internal fun Track.isFree(startUs: Long, endUs: Long): Boolean = items.none { it.startUs < endUs && it.startUs + it.durationUs > startUs }

/**
 * How much of the source plays before the item's start: null when there's no limit or it isn't known. A reversed clip's start is the end of its trimmed source
 * (P4-10), so what lies before it on the timeline is the footage after its trim-out.
 */
private fun TimelineItem.footageBeforeUs(): Long? = when (this) {
    is MediaClip -> if (source.kind == MediaKind.IMAGE) null else SpeedCatalog.timelineLengthOf(if (reversed) source.remainingAfter(trimOutUs) else trimInUs, edgeSpeed(atEnd = false))
    is AudioClip -> effects.timelineLengthFloorOf(trimInUs)
    is TextItem, is StickerItem, is EffectItem -> null
}

/** How much of the source is left after the item's end; 0 when the source's length isn't known (can't extend). */
private fun TimelineItem.footageAfterUs(): Long? = when (this) {
    is MediaClip -> if (source.kind == MediaKind.IMAGE) null else SpeedCatalog.timelineLengthOf(if (reversed) trimInUs else source.remainingAfter(trimOutUs), edgeSpeed(atEnd = true))
    is AudioClip -> effects.timelineLengthOf(source.remainingAfter(trimOutUs))
    is TextItem, is StickerItem, is EffectItem -> null
}

// The longest an item may be made by a trim (P4-10): a frozen frame is held up to 10 s, and a reversed clip keeps its trimmed source within the 60 s the
// engine can reverse (at its speed), so the cap holds after Reverse is on as well as when it is switched on. Null: no cap beyond the source.
private fun TimelineItem.maxLengthUs(): Long? = when {
    this !is MediaClip -> null
    freeze != null -> FreezeReverseCatalog.FREEZE_MAX_US
    reversed && source.kind == MediaKind.VIDEO -> SpeedCatalog.timelineLengthOf(FreezeReverseCatalog.MAX_REVERSE_SOURCE_US, slowestSpeed())
    else -> null
}

// A curved clip held outward by a trim plays the speed at the end it grows from (the picture's first or last point in playing order, see rebasedCurve).
private fun MediaClip.edgeSpeed(atEnd: Boolean): Float = SpeedCatalog.edgeSpeedOf(SpeedCatalog.speedOf(this), SpeedCatalog.curveOf(this), atEnd)

// The reverse cap on a curved clip is the longest it can be: its slowest speed. A trim that goes past 60 s of source anyway makes the engine drop Reverse (P4-10).
private fun MediaClip.slowestSpeed(): Float = SpeedCatalog.curveOf(this)?.minOf { it.speed } ?: SpeedCatalog.speedOf(this)

private fun MediaRef.remainingAfter(trimOutUs: Long): Long = durationUs?.let { (it - trimOutUs).coerceAtLeast(0) } ?: 0L

internal fun TimelineItem.withStart(startUs: Long): TimelineItem = when (this) {
    is MediaClip -> copy(startUs = startUs)
    is AudioClip -> copy(startUs = startUs)
    is TextItem -> copy(startUs = startUs)
    is StickerItem -> copy(startUs = startUs)
    is EffectItem -> copy(startUs = startUs)
}

// A detached clip's copy (P1-09 review, P1): the same rule as Replace on a detached clip (MediaEdits).
private fun TimelineItem.withoutTransition(): TimelineItem = if (this is MediaClip && transitionOut != null) copy(transitionOut = null) else this

private fun TimelineItem.withOwnSoundAtZero(): TimelineItem =
    if (this is MediaClip && audioDetached) copy(audioDetached = false, volume = 0f) else this

private fun TimelineItem.withId(id: String): TimelineItem = when (this) {
    is MediaClip -> copy(id = id)
    is AudioClip -> copy(id = id)
    is TextItem -> copy(id = id)
    is StickerItem -> copy(id = id)
    is EffectItem -> copy(id = id)
}

/**
 * The item cut (or extended) to [lengthUs] of timeline time, keeping its start and where it starts in its source. A sound
 * with a speed other than 1 plays more or less of its source in that time.
 */
private fun TimelineItem.withLength(lengthUs: Long): TimelineItem = when (this) {
    is MediaClip -> cutToLength(lengthUs)
    is AudioClip -> copy(trimOutUs = trimInUs + effects.sourceSpanOf(lengthUs))
    is TextItem -> copy(durationUs = lengthUs)
    is StickerItem -> copy(durationUs = lengthUs)
    is EffectItem -> copy(durationUs = lengthUs)
}

/**
 * The item with [deltaUs] taken off its start (negative: added back), keeping its end in the source. A photo's
 * trim is only how long it shows, so it stays 0-based and never goes negative.
 */
private fun TimelineItem.trimmedFromStart(deltaUs: Long): TimelineItem = when (this) {
    is MediaClip -> if (source.kind == MediaKind.IMAGE) copy(trimInUs = 0, trimOutUs = durationUs - deltaUs) else trimmedFromStartBy(deltaUs)
    is AudioClip -> copy(trimInUs = (trimInUs + effects.sourceSpanOf(deltaUs)).coerceAtLeast(0))
    is TextItem -> copy(durationUs = durationUs - deltaUs)
    is StickerItem -> copy(durationUs = durationUs - deltaUs)
    is EffectItem -> copy(durationUs = durationUs - deltaUs)
}

// A video clip cut (or extended) to [lengthUs] of timeline time, keeping its start. A curve decides how much of the picture that is (an extension holds the speed of
// the end it grows from) and is laid over the new picture so every kept place keeps its speed.
private fun MediaClip.cutToLength(lengthUs: Long): MediaClip {
    val curve = SpeedCatalog.curveOf(this) ?: return withSourceSpan(SpeedCatalog.sourceSpanOf(lengthUs, SpeedCatalog.speedOf(this)), keepStartOfSpan = !reversed)
    val keptUs = sourceSpanUs + retime.pictureChangeAtEnd(lengthUs - durationUs, edgeSpeed(atEnd = true))
    return withSourceSpan(keptUs, keepStartOfSpan = !reversed).copy(speedCurve = SpeedCatalog.rebasedCurve(curve, sourceSpanUs, 0, keptUs.coerceAtLeast(0)))
}

// A video clip with [deltaUs] of timeline time taken off its start (negative: put back), keeping its end in the source.
private fun MediaClip.trimmedFromStartBy(deltaUs: Long): MediaClip {
    val curve = SpeedCatalog.curveOf(this) ?: return withSourceSpan(sourceSpanUs - SpeedCatalog.sourceSpanOf(deltaUs, SpeedCatalog.speedOf(this)), keepStartOfSpan = reversed)
    val removedUs = retime.pictureTakenFromStart(deltaUs, edgeSpeed(atEnd = false))
    val keptUs = (sourceSpanUs - removedUs).coerceAtLeast(0)
    return withSourceSpan(keptUs, keepStartOfSpan = reversed).copy(speedCurve = SpeedCatalog.rebasedCurve(curve, sourceSpanUs, removedUs, keptUs))
}

// The clip with its trimmed source [spanUs] long, one end of the trim kept: the trim-in while it plays forward, the trim-out when reversed, because the end
// of the clip that a cut or a drag moves is the other end of the source then (P4-10). A negative span (a start dragged past the end) is not made.
private fun MediaClip.withSourceSpan(spanUs: Long, keepStartOfSpan: Boolean): MediaClip {
    val span = spanUs.coerceAtLeast(0)
    return if (keepStartOfSpan) copy(trimOutUs = trimInUs + span) else copy(trimInUs = (trimOutUs - span).coerceAtLeast(0))
}

// A piece of a split gets its keyframes; an item that had none stays without.
private fun TimelineItem.withKeyframesIfAny(keyframes: List<Keyframe>): TimelineItem = if (keyframes.isEmpty()) this else withKeyframes(keyframes)

private enum class FadeEdge { IN, OUT }

// A split puts Fade in on the left piece and Fade out on the right one, so it doesn't leave a dip in the middle (spec P3-09 rule 5).
private fun TimelineItem.withoutFade(edge: FadeEdge): TimelineItem = when {
    this !is AudioClip -> this
    edge == FadeEdge.IN -> copy(effects = effects.copy(fadeInUs = 0))
    else -> copy(effects = effects.copy(fadeOutUs = 0))
}

// A trim or a split that made a sound shorter keeps its fades inside it, in the same step.
private fun TimelineItem.withFadesFitted(): TimelineItem =
    if (this is AudioClip) copy(effects = effects.withFadesFitted(durationUs)) else this
