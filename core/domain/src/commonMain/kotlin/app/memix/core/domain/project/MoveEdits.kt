package app.memix.core.domain.project

import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind

/*
 * Move (spec docs/ux/specs/P1-06-clip-edits-and-audio.md → Edits → Move): items on every lane except the main video
 * track change their start and, by drag, their lane. Items never overlap within a lane, a lane left empty is removed in
 * the same step, and the main video track never moves this way (its clips reorder instead).
 */

/** The lane a moved item is dropped on. */
sealed interface LaneTarget {
    /** An existing lane, by track id. A lane of another kind counts as the item's own lane. */
    data class Lane(val trackId: String) : LaneTarget

    /** A new lane right after the last lane of the item's kind: a meme sound dropped on the "Add a meme sound" row. */
    data object NewLane : LaneTarget
}

/** True for items that move with Move and Move here: everything except main video clips. */
fun Project.canMove(itemId: String): Boolean {
    val (track, _) = findItem(itemId) ?: return false
    return track.kind != TrackKind.MAIN_VIDEO
}

/**
 * Moves item [itemId] to start at [startUs] (never before 0:00; it may reach past the end of the video) on [lane].
 * Where it would overlap another item there, it goes to the next lane of its kind that's free, else to a new lane
 * [newTrackId] after the last one of its kind. Dropped where it already is, nothing changes.
 */
fun Project.moveItem(itemId: String, startUs: Long, lane: LaneTarget, newTrackId: String): Project {
    if (!canMove(itemId)) return this
    val (fromTrack, item) = findItem(itemId) ?: return this
    val moved = item.withStart(startUs.coerceAtLeast(0))
    val withoutItem = removeItem(fromTrack.id, itemId)
    val lanesOfKind = withoutItem.tracks().filter { it.kind == fromTrack.kind }
    val targetIndex = when (lane) {
        is LaneTarget.Lane -> lanesOfKind.indexOfFirst { it.id == lane.trackId }.takeIf { it >= 0 } ?: lanesOfKind.indexOfFirst { it.id == fromTrack.id }
        LaneTarget.NewLane -> lanesOfKind.size
    }
    // The lane it was dropped on first, then the ones below it; a new lane when none is free.
    val candidates = if (targetIndex < 0) emptyList() else lanesOfKind.drop(targetIndex)
    val result = withoutItem.withItemOnFreeLane(moved, fromTrack.kind, candidates, newTrackId)
    return if (result.sameLanesAs(this)) this else result.withoutEmptyLanes(fromTrack.id)
}

/**
 * Move here (spec P1-06 → Move): the item starts at the playhead [atUs], or ends at the end of the video when the
 * playhead is there ([startAtPlayhead]), on its own lane if that's free, else the next free lane of its kind.
 */
fun Project.moveItemToPlayhead(itemId: String, atUs: Long, newTrackId: String): Project {
    val (track, item) = findItem(itemId) ?: return this
    return moveItem(itemId, startAtPlayhead(atUs, item.durationUs, videoLengthUs()), LaneTarget.Lane(track.id), newTrackId)
}

// The item taken off its lane; the lane stays (possibly empty) so lane order is kept while it's placed again.
private fun Project.removeItem(trackId: String, itemId: String): Project {
    val track = tracks().firstOrNull { it.id == trackId } ?: return this
    return replaceTrack(track.copy(items = track.items.filterNot { it.id == itemId }))
}

// The lane the item left, if it's empty now, goes in the same step (spec P1-05 request 7).
private fun Project.withoutEmptyLanes(leftTrackId: String): Project =
    withTracks(tracks().filterNot { it.id == leftTrackId && it.items.isEmpty() })

// Same items on the same lanes at the same times, whatever their order in each list.
private fun Project.sameLanesAs(other: Project): Boolean {
    val mine = tracks().map { track -> track.id to track.items.associateBy { it.id } }
    val theirs = other.tracks().map { track -> track.id to track.items.associateBy { it.id } }
    return mine == theirs
}
