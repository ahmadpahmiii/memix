package app.memix.core.domain.project

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.Easing
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.keyframeList

/*
 * What the editor's keyframe bar and marker row need to know about one item (P4-02b), asked of the project so features never reach into
 * its tracks. Times are on the project timeline (the playhead's), unlike the stored keyframes, which count from the item's start.
 */

/** Which properties a keyframe of an item can animate; a tool for one that is false is absent from the keyframe bar (spec D6). */
data class KeyframeProperties(val position: Boolean, val opacity: Boolean, val volume: Boolean) {
    /** Nothing to animate: the item gets no Keyframes tool. */
    val any: Boolean get() = position || opacity || volume
}

/** One keyframe at [timeUs] on the project timeline; [moveRange] is where a drag may take it (see [keyframeMoveRange]). */
data class KeyframePoint(val timeUs: Long, val easing: Easing, val moveRange: LongRange)

/** An item's span and the keyframes inside it, in time order; keyframes a trim cut off stay in the project and are left out here. */
data class KeyframedItem(
    val itemId: String,
    val startUs: Long,
    val endUs: Long,
    val points: List<KeyframePoint>,
    /** How many keyframes the item has in all, hidden ones included. */
    val count: Int,
    val properties: KeyframeProperties,
)

/** [itemId]'s keyframe facts, or null when there is no such item. */
fun Project.keyframedItem(itemId: String): KeyframedItem? {
    val (track, item) = findItem(itemId) ?: return null
    val startUs = item.startUs
    val endUs = item.startUs + item.durationUs
    val points = item.keyframeList()
        .map { startUs + it.timeUs to it }
        .filter { (timeUs, _) -> timeUs in startUs..endUs }
        .mapNotNull { (timeUs, keyframe) -> keyframeMoveRange(itemId, timeUs)?.let { KeyframePoint(timeUs, keyframe.easing, it) } }
    val isVideo = item is MediaClip && item.source.kind == MediaKind.VIDEO
    val properties = when (item) {
        is AudioClip -> KeyframeProperties(position = false, opacity = false, volume = true)
        // A main clip's own framing is drawn with P4-16, so until then only its volume moves.
        is MediaClip -> if (track.kind == TrackKind.OVERLAY) KeyframeProperties(true, true, isVideo) else KeyframeProperties(false, false, isVideo)
        is TextItem, is StickerItem -> KeyframeProperties(position = true, opacity = false, volume = false)
        else -> KeyframeProperties(false, false, false)
    }
    return KeyframedItem(itemId, startUs, endUs, points, item.keyframeList().size, properties)
}

/** Whether [itemId] has any keyframe: every edit of it is then a keyframe edit (spec D4). */
fun Project.hasKeyframes(itemId: String): Boolean = findItem(itemId)?.item?.keyframeList()?.isNotEmpty() == true
