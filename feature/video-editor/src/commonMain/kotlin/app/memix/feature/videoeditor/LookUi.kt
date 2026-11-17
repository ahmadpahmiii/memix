package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.project.EditPhotoTab
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.ImageEdit
import app.memix.core.ui.scene.SceneBitmap

/**
 * The Filters panel (P4-07). [itemId] is the one clip it acts on (opened from a clip's own bar), or null for every main clip (opened from
 * the main bar). [adjust] and [filter] follow the sliders while they move and show the look the clips share; in the [mixed] state (the
 * main clips have different looks) [filter] is null, no tile is selected, and [adjust] is the look of the clip under the playhead
 * (spec → The tool and its scope). [frame] is the picture the 21 tiles are made from, once it has arrived.
 */
@Immutable
data class LookUi(
    val itemId: String?,
    val tab: EditPhotoTab = EditPhotoTab.FILTERS,
    val adjust: ImageAdjust = ImageAdjust(),
    val filter: FilterRef? = null,
    val mixed: Boolean = false,
    val frame: LookFrame? = null,
) {
    /** True when the panel acts on every main clip. */
    val isAllClips: Boolean get() = itemId == null
}

/**
 * One frame of a clip for the tiles: [key] names the clip and the time, so the tiles are made again only for another picture;
 * [geometry] is the clip's crop and turn (P4-16), which the tiles show, never its colors.
 */
@Immutable
class LookFrame(val key: String, val picture: SceneBitmap, val geometry: ImageEdit?)
