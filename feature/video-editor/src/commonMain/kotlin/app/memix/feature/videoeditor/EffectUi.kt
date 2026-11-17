package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.model.project.EffectTab
import app.memix.core.ui.scene.SceneBitmap

/**
 * The Effects sheet (P4-06a; spec docs/ux/specs/P4-06-video-effects.md → The Effects sheet): the open [tab], and in replace mode ([replaceItemId], the
 * item whose effect "Change effect" swaps) the effect that item has now, [currentEffectId], which its tile rings. [frame] is the picture the tiles are
 * made from, once it has arrived.
 */
@Immutable
data class EffectsSheetUi(
    val tab: EffectTab,
    val replaceItemId: String? = null,
    val currentEffectId: String? = null,
    val frame: EffectFrame? = null,
)

/** One frame of the clip under the playhead for the tiles; [key] names the clip and the time, so the tiles are made again only for another picture. */
@Immutable
class EffectFrame(val key: String, val picture: SceneBitmap)

/** The Intensity panel of effect item [itemId] (spec → The Intensity panel): [intensity] follows the slider while it moves (0 to 1). */
@Immutable
data class EffectPanelUi(val itemId: String, val effectId: String, val intensity: Float)
