package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import app.memix.core.domain.project.overlayBoxAtScaleOne
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.OverlayCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform
import app.memix.core.ui.layers.GestureTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * An overlay as the stage knows it (spec P4-01b): where and how big it is, when it shows, and the size of its own box at
 * scale 1 in canvas pixels (the picture fitted inside the canvas), which sizes its selection frame and its touch area. The
 * stage's gestures treat it like a caption, and also snap its sides to the canvas edges and keep its handle whole on the stage.
 */
@Immutable
data class OverlayUi(
    val itemId: String,
    val startUs: Long,
    val endUs: Long,
    override val transform: Transform,
    val opacity: Float,
    override val boxWidthPx: Float,
    override val boxHeightPx: Float,
    val media: MediaRef,
    /** Where the clip starts inside its source file; a photo has no time. */
    val trimInUs: Long,
) : GestureTarget {
    override val id: String get() = itemId
    override val scaleRange: ClosedFloatingPointRange<Float> get() = OverlayCatalog.SCALE_RANGE

    // The video editor has no locked layers.
    override val locked: Boolean get() = false
    override val snapsToEdges: Boolean get() = true
    override val keepsHandleOnStage: Boolean get() = true

    val isPhoto: Boolean get() = media.kind == MediaKind.IMAGE

    /** Whether it shows at [timeUs]. */
    fun isShownAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    /** The source file's time of the frame shown at [timeUs]; 0 for a photo. */
    fun sourceTimeAt(timeUs: Long): Long = if (isPhoto) 0L else trimInUs + (timeUs - startUs).coerceAtLeast(0)
}

/** What a finger or a held Position arrow has put an overlay at, drawn by the editor's stand-in until the preview shows the commit. */
@Immutable
data class OverlayStandInUi(val itemId: String, val transform: Transform, val opacity: Float)

/** The selected overlay's panel over the timeline and tool bar. */
enum class OverlayPanel { OPACITY, POSITION, ORDER }

/** Every overlay of [this] project, back to front (track order, then start), for [canvas]; clips with no known picture size draw nothing and are left out. */
internal fun Project.overlayUis(): ImmutableList<OverlayUi> {
    val size = PixelSize(canvas.widthPx, canvas.heightPx)
    return video?.tracks.orEmpty()
        .filter { it.kind == TrackKind.OVERLAY }
        .flatMap { track -> track.items.filterIsInstance<MediaClip>().sortedBy { it.startUs } }
        .mapNotNull { clip ->
            val box = overlayBoxAtScaleOne(clip, size)
            if (box.width <= 0f || box.height <= 0f) return@mapNotNull null
            OverlayUi(clip.id, clip.startUs, clip.startUs + clip.durationUs, clip.transform, clip.opacity, box.width, box.height, clip.source, clip.trimInUs)
        }
        .toImmutableList()
}
