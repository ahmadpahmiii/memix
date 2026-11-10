package app.memix.core.ui.text

import androidx.compose.runtime.Immutable
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform

/**
 * Typing a caption (spec P1-10 → Typing, P2-03 → Typing): a new one ([itemId] null) or caption [itemId]'s words. The field starts
 * with [initialText]; [style] and [transform] are how it shows on the canvas while typed. A new [serial] is a new entry. Both
 * editors use it: the video editor's captions and the photo editor's text layers.
 */
@Immutable
data class TextEntryUi(
    val serial: Long,
    val itemId: String?,
    val initialText: String,
    val style: CaptionStyle,
    val transform: Transform,
)
