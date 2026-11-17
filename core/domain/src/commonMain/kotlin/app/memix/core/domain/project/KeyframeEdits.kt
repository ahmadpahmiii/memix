package app.memix.core.domain.project

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Easing
import app.memix.core.model.project.ItemLook
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.KeyframeCatalog
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform
import app.memix.core.model.project.canHaveKeyframes
import app.memix.core.model.project.keyframeList
import app.memix.core.model.project.staticLook
import app.memix.core.model.project.withKeyframes
import kotlin.math.abs

/*
 * Keyframe edits (P4-02a; spec docs/ux/specs/P4-02b-keyframes.md → Interactions). Like every edit, each returns a new Project, or this
 * one unchanged when it can't apply, so the edit session records one undo step or none; the "problem" functions say why, so the editor
 * can explain. Times given to these functions are on the project timeline (the playhead); the keyframes themselves are stored relative
 * to their item's start ([Keyframe.timeUs]), so they move with the item. A keyframe is addressed by a time within
 * [KeyframeCatalog.ON_KEYFRAME_US] of it, which is how the editor knows "the playhead is on a keyframe".
 */

/** Why a keyframe can't be added at a time. */
enum class KeyframeProblem {
    /** No such item, or one that can't move (an effect). */
    NOT_KEYFRAMEABLE,

    /** The time is not within the item: "Move the playhead onto the item first." */
    OUTSIDE_ITEM,

    /** Another keyframe is less than [KeyframeCatalog.MIN_SPACING_US] away: the editor's Add is Remove here. */
    TOO_CLOSE,

    /** The item has [KeyframeCatalog.MAX_PER_ITEM] keyframes. */
    LIMIT,
}

/** The keyframe item [itemId] has at [atUs], within half a frame; null when none. */
fun Project.keyframeAt(itemId: String, atUs: Long): Keyframe? {
    val item = findItem(itemId)?.item ?: return null
    val relativeUs = atUs - item.startUs
    return item.keyframeList().firstOrNull { abs(it.timeUs - relativeUs) <= KeyframeCatalog.ON_KEYFRAME_US }
}

/** Why [addKeyframe] would do nothing at [atUs]; null when it can add. */
fun Project.keyframeAddProblem(itemId: String, atUs: Long): KeyframeProblem? {
    val item = findItem(itemId)?.item?.takeIf { it.canHaveKeyframes() } ?: return KeyframeProblem.NOT_KEYFRAMEABLE
    val relativeUs = atUs - item.startUs
    if (relativeUs < 0 || relativeUs > item.durationUs) return KeyframeProblem.OUTSIDE_ITEM
    val keyframes = item.keyframeList()
    if (keyframes.any { abs(it.timeUs - relativeUs) < KeyframeCatalog.MIN_SPACING_US }) return KeyframeProblem.TOO_CLOSE
    if (keyframes.size >= KeyframeCatalog.MAX_PER_ITEM) return KeyframeProblem.LIMIT
    return null
}

/** Adds a keyframe at [atUs] holding the item's look there (so nothing moves), with [easing]. One undo step. */
fun Project.addKeyframe(itemId: String, atUs: Long, easing: Easing = KeyframeCatalog.DEFAULT_EASING): Project {
    if (keyframeAddProblem(itemId, atUs) != null) return this
    val (track, item) = findItem(itemId) ?: return this
    val look = item.lookAt(atUs, track.isOverlay()).restrictedTo(item, track)
    return withKeyframesOf(track, item, (item.keyframeList() + Keyframe(atUs - item.startUs, look, easing)).sortedBy { it.timeUs })
}

/** Removes the keyframe at [atUs]. The item keeps moving through the others; removing the last one leaves it still, at its static look. */
fun Project.removeKeyframe(itemId: String, atUs: Long): Project {
    val (track, item) = findItem(itemId) ?: return this
    val target = keyframeAt(itemId, atUs) ?: return this
    return withKeyframesOf(track, item, item.keyframeList() - target)
}

/**
 * The project times keyframe [atUs] of item [itemId] can be dragged to: the item's span, and no closer than one frame to a
 * neighbor, so it never crosses one. Null when there is no keyframe there.
 */
fun Project.keyframeMoveRange(itemId: String, atUs: Long): LongRange? {
    val item = findItem(itemId)?.item ?: return null
    val target = keyframeAt(itemId, atUs) ?: return null
    val others = item.keyframeList() - target
    val earliestUs = maxOf(0L, (others.lastOrNull { it.timeUs < target.timeUs }?.timeUs ?: Long.MIN_VALUE / 2) + KeyframeCatalog.MIN_SPACING_US)
    val latestUs = minOf(item.durationUs, (others.firstOrNull { it.timeUs > target.timeUs }?.timeUs ?: Long.MAX_VALUE / 2) - KeyframeCatalog.MIN_SPACING_US)
    return if (earliestUs > latestUs) null else (item.startUs + earliestUs)..(item.startUs + latestUs)
}

/** Moves the keyframe at [fromUs] to [toUs], keeping its look and easing. Unchanged when [toUs] is outside [keyframeMoveRange]. One undo step. */
fun Project.moveKeyframe(itemId: String, fromUs: Long, toUs: Long): Project {
    val (track, item) = findItem(itemId) ?: return this
    val target = keyframeAt(itemId, fromUs) ?: return this
    val range = keyframeMoveRange(itemId, fromUs) ?: return this
    if (toUs !in range) return this
    val moved = target.copy(timeUs = toUs - item.startUs)
    return withKeyframesOf(track, item, (item.keyframeList() - target + moved).sortedBy { it.timeUs })
}

/** Sets the easing of the motion that leaves the keyframe at [atUs]; stored on the last keyframe too, where it has no effect. One undo step. */
fun Project.setKeyframeEasing(itemId: String, atUs: Long, easing: Easing): Project {
    val (track, item) = findItem(itemId) ?: return this
    val target = keyframeAt(itemId, atUs)?.takeIf { it.easing != easing } ?: return this
    return withKeyframesOf(track, item, item.keyframeList().map { if (it.timeUs == target.timeUs) it.copy(easing = easing) else it })
}

/** Removes every keyframe; the item keeps the look it has at [keepLookAtUs] as its static look (spec → Clear all). One undo step. */
fun Project.clearKeyframes(itemId: String, keepLookAtUs: Long): Project {
    val (track, item) = findItem(itemId) ?: return this
    if (item.keyframeList().isEmpty()) return this
    val look = item.lookAt(keepLookAtUs, track.isOverlay()).restrictedTo(item, track)
    val still = item.withKeyframes(emptyList()).withStaticLook(look, track)
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) still else it }))
}

/**
 * The auto-keying edit (spec → Auto-keying, D4): the item should look like [look] at [atUs]; call it for every edit of a keyed item, and
 * for the first edit made while the keyframe bar is open. A keyframe within a frame of [atUs] takes the look; else one is added
 * there (unchanged at [KeyframeCatalog.MAX_PER_ITEM]). An item with no keyframes gets one at [atUs], or, later than
 * [KeyframeCatalog.AUTO_START_WITHIN_US] into the item, two: one at its start holding the look it had, so it glides to the new look.
 * The look is brought inside the item's limits. All of it is one undo step.
 */
fun Project.setLookAt(itemId: String, atUs: Long, look: ItemLook): Project {
    val (track, item) = findItem(itemId) ?: return this
    if (!item.canHaveKeyframes()) return this
    val relativeUs = atUs - item.startUs
    if (relativeUs < 0 || relativeUs > item.durationUs) return this
    val wanted = look.restrictedTo(item, track)
    val keyframes = item.keyframeList()
    if (keyframes.isEmpty()) return withKeyframesOf(track, item, firstKeyframes(item, track, relativeUs, wanted))
    val near = keyframes.minByOrNull { abs(it.timeUs - relativeUs) }?.takeIf { abs(it.timeUs - relativeUs) < KeyframeCatalog.MIN_SPACING_US }
    if (near != null) return withKeyframesOf(track, item, keyframes.map { if (it == near) it.copy(look = wanted) else it })
    if (keyframes.size >= KeyframeCatalog.MAX_PER_ITEM) return this
    return withKeyframesOf(track, item, (keyframes + Keyframe(relativeUs, wanted)).sortedBy { it.timeUs })
}

private fun firstKeyframes(item: TimelineItem, track: Track, relativeUs: Long, wanted: ItemLook): List<Keyframe> {
    if (relativeUs <= KeyframeCatalog.AUTO_START_WITHIN_US) return listOf(Keyframe(relativeUs, wanted))
    val before = item.staticLook(track.isOverlay()).restrictedTo(item, track)
    return listOf(Keyframe(0, before), Keyframe(relativeUs, wanted))
}

/**
 * [this] brought inside what [item] can do: the center within [KeyframeCatalog.CENTER_RANGE], the scale within the item's own range,
 * the turn within [KeyframeCatalog.ROTATION_LIMIT_DEGREES], opacity and volume within theirs. A property the item doesn't have is put
 * back to neutral, so a keyframe never carries a value nothing uses: no place for a sound, no opacity on a main clip or a caption,
 * no volume on a picture.
 */
fun ItemLook.restrictedTo(item: TimelineItem, track: Track): ItemLook {
    val moves = item !is AudioClip
    val fades = track.isOverlay() && item is MediaClip
    val sounds = item is AudioClip || (item is MediaClip && item.source.kind == MediaKind.VIDEO)
    return ItemLook(
        centerX = if (moves) centerX.coerceIn(KeyframeCatalog.CENTER_RANGE) else NEUTRAL.centerX,
        centerY = if (moves) centerY.coerceIn(KeyframeCatalog.CENTER_RANGE) else NEUTRAL.centerY,
        scale = if (moves) scale.coerceIn(KeyframeCatalog.scaleRangeOf(item)) else NEUTRAL.scale,
        rotationDegrees = if (moves) rotationDegrees.coerceIn(-KeyframeCatalog.ROTATION_LIMIT_DEGREES, KeyframeCatalog.ROTATION_LIMIT_DEGREES) else NEUTRAL.rotationDegrees,
        opacity = if (fades) opacity.coerceIn(KeyframeCatalog.OPACITY_RANGE) else NEUTRAL.opacity,
        volume = if (sounds) volume.coerceIn(KeyframeCatalog.VOLUME_RANGE) else NEUTRAL.volume,
    )
}

private val NEUTRAL = ItemLook()

private fun Track.isOverlay(): Boolean = kind == TrackKind.OVERLAY

private fun Project.withKeyframesOf(track: Track, item: TimelineItem, keyframes: List<Keyframe>): Project {
    val updated = item.withKeyframes(keyframes)
    return replaceTrack(track.copy(items = track.items.map { if (it.id == item.id) updated else it }))
}

// The look as the item's own transform, opacity and volume, for an item that stops being keyed.
private fun TimelineItem.withStaticLook(look: ItemLook, track: Track): TimelineItem = when (this) {
    is MediaClip -> copy(
        transform = look.transform,
        opacity = if (track.isOverlay()) look.opacity else opacity,
        volume = look.volume,
    )
    is AudioClip -> copy(volume = look.volume)
    is TextItem -> copy(transform = look.transform)
    is StickerItem -> copy(transform = look.transform)
    else -> this
}

/*
 * How the clip edits treat keyframes (spec → How other edits treat keyframes). They live here so TimelineEdits stays about clips.
 */

/**
 * The keyframes of the two pieces of an item cut at [cutRelativeUs] after its start, as (left, right) with the right one's times
 * counted from its own new start. Each piece keeps the keyframes inside its range and gets one at the cut holding the look there, with the
 * easing of the motion that was crossing the cut, so the motion carries across with no jump. Empty lists for an item with none.
 */
internal fun TimelineItem.keyframesAfterSplit(cutRelativeUs: Long): Pair<List<Keyframe>, List<Keyframe>> {
    val keyframes = keyframeList()
    val look = interpolatedLook(keyframes, cutRelativeUs) ?: return emptyList<Keyframe>() to emptyList()
    val crossing = keyframes.lastOrNull { it.timeUs <= cutRelativeUs }
    val spacing = KeyframeCatalog.MIN_SPACING_US
    val left = keyframes.filter { it.timeUs <= cutRelativeUs - spacing } + Keyframe(cutRelativeUs, look)
    val right = listOf(Keyframe(0, look, crossing?.easing ?: KeyframeCatalog.DEFAULT_EASING)) +
        keyframes.filter { it.timeUs >= cutRelativeUs + spacing }.map { it.copy(timeUs = it.timeUs - cutRelativeUs) }
    return left to right
}

/** This item's keyframes counted from a start moved [deltaUs] later (negative: earlier), so the motion stays where it was on the timeline. */
internal fun TimelineItem.withKeyframesShifted(deltaUs: Long): TimelineItem {
    val keyframes = keyframeList()
    if (keyframes.isEmpty() || deltaUs == 0L) return this
    return withKeyframes(keyframes.map { it.copy(timeUs = it.timeUs - deltaUs) })
}

/** [keyframes] with the look kept in the item's range after a ratio change: each look's transform goes through [keep]. */
internal fun List<Keyframe>.withTransformsKept(keep: (Transform) -> Transform): List<Keyframe> =
    map { frame ->
        val kept = keep(frame.look.transform)
        if (kept == frame.look.transform) frame else frame.copy(look = frame.look.copy(centerX = kept.centerX, centerY = kept.centerY, scale = kept.scale, rotationDegrees = kept.rotationDegrees))
    }
