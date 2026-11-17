package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.project.KeyframeProperties
import app.memix.core.domain.project.keyframedItem
import app.memix.core.model.project.Easing
import app.memix.core.model.project.KeyframeCatalog
import app.memix.core.model.project.Project
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One diamond of the marker row at [timeUs] on the project timeline; [minUs]..[maxUs] is where a drag may take it. */
@Immutable
data class KeyframeMarkerUi(val timeUs: Long, val easing: Easing, val minUs: Long, val maxUs: Long)

/**
 * The keyframe bar of item [itemId] (spec P4-02b → Layout): the item's span, its [markers] (project times, only those inside the span),
 * the [selectedUs] keyframe a tap, Previous or Next picked, and whether the Easing panel [easingOpen]. Whether the playhead is on a
 * keyframe is not here: it changes every frame, so the bar and the marker row read it themselves.
 */
@Immutable
data class KeyframeBarUi(
    val itemId: String,
    val startUs: Long,
    val endUs: Long,
    val markers: ImmutableList<KeyframeMarkerUi>,
    val properties: KeyframeProperties,
    val selectedUs: Long? = null,
    val easingOpen: Boolean = false,
) {
    /** The marker within half a frame of [timeUs], or null. */
    fun markerAt(timeUs: Long): KeyframeMarkerUi? = markers.firstOrNull { abs(it.timeUs - timeUs) <= KeyframeCatalog.ON_KEYFRAME_US }

    /** The selected marker, or null (it may have gone with an undo). */
    val selected: KeyframeMarkerUi? get() = selectedUs?.let(::markerAt)
}

/** The bar for [itemId] as [project] has it now, keeping what [previous] (the same item's bar) had selected and open while it still exists; null when the item is gone. */
internal fun Project.keyframeBarOf(itemId: String, previous: KeyframeBarUi?): KeyframeBarUi? {
    val item = keyframedItem(itemId) ?: return null
    val markers = item.points.map { KeyframeMarkerUi(it.timeUs, it.easing, it.moveRange.first, it.moveRange.last) }.toImmutableList()
    val kept = previous?.takeIf { it.itemId == itemId }
    val bar = KeyframeBarUi(itemId, item.startUs, item.endUs, markers, item.properties, kept?.selectedUs, kept?.easingOpen == true)
    return bar.copy(selectedUs = bar.selected?.timeUs, easingOpen = bar.easingOpen && bar.selected != null)
}
