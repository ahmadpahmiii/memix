package app.memix.core.domain.project

import app.memix.core.model.project.BoxSize
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.OverlayCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.Transform
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.normalizedDegrees
import app.memix.core.model.project.turnedBounds
import kotlin.math.abs

/*
 * Overlay edits (P4-01: video and photo clips floating over the main video). An overlay is a [MediaClip] on an OVERLAY track,
 * and each such track is one layer: its clips never overlap in time, and a track later in the project's track list draws over an
 * earlier one, like text lanes. The main video is always below every overlay. Like every edit, each function returns a new
 * Project, or this one unchanged when it can't apply, so the edit session records one undo step or none.
 *
 * Moving an overlay in time (and to another layer), trimming it, splitting it, duplicating it and deleting it are the clip edits
 * every lane already has: [moveItem], [trimItem], [splitItem], [duplicateItem], [deleteItem].
 */

/**
 * Where a new overlay of [wantedUs] goes in time when the playhead is at [atUs] in a video [videoLengthUs] long: from the
 * playhead for its whole length or to the end of the video, whichever comes first; if that leaves under
 * [OverlayCatalog.MIN_LANDING_US], it ends at the end of the video instead, as the smaller of its length and [OverlayCatalog.END_LANDING_MAX_US]. Returns (start, length) in µs.
 */
fun overlayLandingSpan(atUs: Long, wantedUs: Long, videoLengthUs: Long): Pair<Long, Long> {
    if (videoLengthUs <= 0) return 0L to wantedUs
    val startUs = atUs.coerceIn(0, videoLengthUs)
    if (videoLengthUs - startUs >= OverlayCatalog.MIN_LANDING_US) return startUs to minOf(wantedUs, videoLengthUs - startUs)
    val lengthUs = minOf(wantedUs, OverlayCatalog.END_LANDING_MAX_US, videoLengthUs)
    return (videoLengthUs - lengthUs) to lengthUs
}

/**
 * Adds [media] as overlay [itemId] at the playhead [atUs] (spec P4-01b → Landing): a video for its whole length, a photo for
 * [OverlayCatalog.PHOTO_LANDING_US] (see [overlayLandingSpan]), at [OverlayCatalog.LANDING_SCALE], upright, opaque, in the first free
 * slot ([OverlayCatalog.LANDING_SLOTS]). It lands in front of every layer holding an overlapping clip: on the first later layer free
 * for its whole span, else on a new layer [newTrackId] after the last. A video whose length wasn't measured, and audio, add nothing.
 * Its sound is at 100% like any clip's, played by the engine (P4-01b).
 */
fun Project.addOverlay(media: MediaRef, atUs: Long, itemId: String, newTrackId: String): Project {
    val wantedUs = when (media.kind) {
        MediaKind.VIDEO -> media.durationUs ?: return this
        MediaKind.IMAGE -> OverlayCatalog.PHOTO_LANDING_US
        MediaKind.AUDIO -> return this
    }
    if (video == null || wantedUs <= 0) return this
    val (startUs, lengthUs) = overlayLandingSpan(atUs, wantedUs, videoLengthUs())
    val (centerX, centerY) = landingSlot(atUs)
    val overlay = MediaClip(
        id = itemId,
        startUs = startUs,
        source = media,
        trimInUs = 0,
        trimOutUs = lengthUs,
        transform = Transform(centerX, centerY, OverlayCatalog.LANDING_SCALE),
    )
    val layers = tracks().filter { it.kind == TrackKind.OVERLAY }
    val lastBusy = layers.indexOfLast { layer -> !layer.isFree(overlay.startUs, overlay.startUs + overlay.durationUs) }
    return withItemOnFreeLane(overlay, TrackKind.OVERLAY, layers.drop(lastBusy + 1), newTrackId)
}

// The first slot no overlay showing at the playhead sits on; the center when all are taken.
private fun Project.landingSlot(atUs: Long): Pair<Float, Float> {
    val shown = overlaysAt(atUs).map { it.transform }
    return OverlayCatalog.LANDING_SLOTS.firstOrNull { (x, y) ->
        shown.none { abs(it.centerX - x) < OverlayCatalog.SLOT_TAKEN_WITHIN && abs(it.centerY - y) < OverlayCatalog.SLOT_TAKEN_WITHIN }
    } ?: OverlayCatalog.LANDING_SLOTS.first()
}

/**
 * Overlay [itemId]'s place, size and turn, kept inside the limits: the center on the canvas (0…1), the scale within
 * [OverlayCatalog.SCALE_RANGE] and the turn within −180°…180°. On an item with keyframes use [setLookAt] instead: this sets the
 * fixed look, which a keyed item doesn't use.
 */
fun Project.setOverlayTransform(itemId: String, transform: Transform): Project {
    val overlay = overlayClip(itemId) ?: return this
    val clamped = transform.clampedForOverlay()
    return if (overlay.transform == clamped) this else replaceOverlay(overlay.copy(transform = clamped))
}

/** Overlay [itemId]'s opacity, within [OverlayCatalog.OPACITY_RANGE]. On an item with keyframes use [setLookAt] instead. */
fun Project.setOverlayOpacity(itemId: String, opacity: Float): Project {
    val overlay = overlayClip(itemId) ?: return this
    val clamped = opacity.coerceIn(OverlayCatalog.OPACITY_RANGE)
    return if (overlay.opacity == clamped) this else replaceOverlay(overlay.copy(opacity = clamped))
}

/** Overlay [itemId], or null when it isn't a clip on an overlay layer. */
fun Project.overlayClip(itemId: String): MediaClip? {
    val (track, item) = findItem(itemId) ?: return null
    return (item as? MediaClip)?.takeIf { track.kind == TrackKind.OVERLAY }
}

/** The overlays shown at [atUs], bottom to top: layer by layer from the first, so a later layer is on top. */
fun Project.overlaysAt(atUs: Long): List<MediaClip> =
    tracks().filter { it.kind == TrackKind.OVERLAY }.flatMap { track ->
        track.items.filterIsInstance<MediaClip>().filter { atUs >= it.startUs && atUs < it.startUs + it.durationUs }
    }

/** The center stays on the canvas, the scale within [OverlayCatalog.SCALE_RANGE] and the turn within −180°…180°. */
fun Transform.clampedForOverlay(): Transform = Transform(
    centerX = centerX.coerceIn(0f, 1f),
    centerY = centerY.coerceIn(0f, 1f),
    scale = scale.coerceIn(OverlayCatalog.SCALE_RANGE),
    rotationDegrees = normalizedDegrees(rotationDegrees),
)

private fun Project.replaceOverlay(overlay: MediaClip): Project {
    val (track, _) = findItem(overlay.id) ?: return this
    return replaceTrack(track.copy(items = track.items.map { if (it.id == overlay.id) overlay else it }))
}

/**
 * Bring forward ([forward]) or Send back overlay [itemId] (spec P4-01b → Layer order panel): it swaps places in the stack with the
 * nearest overlay in front of it (behind it) that shares a moment with it, so it ends up directly in front of (behind) that one.
 * It takes the lane right next to the one it passes if that lane is free for its whole span, else a new lane [newTrackId] is made
 * there; a lane left empty goes. Returns this project unchanged when no overlay in that direction shares a moment with it, so the
 * edit session records no step and the editor explains.
 */
fun Project.reorderOverlay(itemId: String, forward: Boolean, newTrackId: String): Project {
    val overlay = overlayClip(itemId) ?: return this
    val lanes = tracks().filter { it.kind == TrackKind.OVERLAY }
    val ownIndex = lanes.indexOfFirst { lane -> lane.items.any { it.id == itemId } }
    val endUs = overlay.startUs + overlay.durationUs
    val passedIndex = if (forward) {
        (ownIndex + 1..lanes.lastIndex).firstOrNull { !lanes[it].isFree(overlay.startUs, endUs) }
    } else {
        (ownIndex - 1 downTo 0).firstOrNull { !lanes[it].isFree(overlay.startUs, endUs) }
    } ?: return this
    val passed = lanes[passedIndex]
    val nextTo = lanes.getOrNull(if (forward) passedIndex + 1 else passedIndex - 1)?.takeIf { it.isFree(overlay.startUs, endUs) }
    val own = lanes[ownIndex]
    val withoutOverlay = tracks().map { if (it.id == own.id) it.copy(items = it.items.filterNot { item -> item.id == itemId }) else it }
    val placed = if (nextTo != null) {
        withoutOverlay.map { if (it.id == nextTo.id) it.copy(items = it.items + overlay) else it }
    } else {
        val at = withoutOverlay.indexOfFirst { it.id == passed.id } + if (forward) 1 else 0
        withoutOverlay.toMutableList().apply { add(at, Track(newTrackId, TrackKind.OVERLAY, listOf(overlay))) }
    }
    return withTracks(placed.filterNot { it.id == own.id && it.items.isEmpty() })
}

/**
 * Where overlay [itemId] sits in the stack among all overlay layers, 1 = the front-most, for "layer 2 of 3" (spec P4-01b →
 * Accessibility); null when it isn't an overlay.
 */
fun Project.overlayLayerOf(itemId: String): OverlayLayerPosition? {
    val lanes = tracks().filter { it.kind == TrackKind.OVERLAY }
    val index = lanes.indexOfFirst { lane -> lane.items.any { it.id == itemId } }
    return if (index < 0) null else OverlayLayerPosition(layer = lanes.size - index, count = lanes.size)
}

/** Layer [layer] of [count] overlay layers, 1 being the front-most. */
data class OverlayLayerPosition(val layer: Int, val count: Int)

/**
 * The overlay at [atUs]'s place in the stack among the overlays showing there, 1 = front-most, and how many show: the stage nodes'
 * "layer 1 of 2" (spec P4-01b → Accessibility).
 */
fun Project.overlayStackAt(itemId: String, atUs: Long): OverlayLayerPosition? {
    val shown = overlaysAt(atUs)
    val index = shown.indexOfFirst { it.id == itemId }
    return if (index < 0) null else OverlayLayerPosition(layer = shown.size - index, count = shown.size)
}

/** Overlay [clip]'s picture fitted inside a canvas [canvas] at scale 1, in canvas pixels; empty when its size isn't known. */
fun overlayBoxAtScaleOne(clip: MediaClip, canvas: PixelSize): BoxSize =
    clip.source.pixelSize?.let { fittedImageSize(it, canvas) } ?: BoxSize(0f, 0f)

/** The overlay's box on [canvas] at [scale]: its turned bounds, in canvas pixels. */
internal fun MediaClip.overlayBoundsOn(canvas: PixelSize, scale: Float): BoxSize {
    val box = overlayBoxAtScaleOne(this, canvas)
    return turnedBounds(BoxSize(box.width * scale, box.height * scale), transform.rotationDegrees)
}

/**
 * This overlay after a ratio change from [old] to [new] (spec P4-01b → Ratio change): the photo layers' rule, with margin 0 and the
 * smallest size [OverlayCatalog.RATIO_CHANGE_SHRINK_FLOOR]. An overlay whose picture size isn't known keeps its place.
 */
internal fun MediaClip.keptInside(old: PixelSize, new: PixelSize): MediaClip {
    if (source.pixelSize == null) return this
    val kept = keptTransform(old, new)
    // Every keyframe's look follows the same rule, with its own turn (spec P4-02b → Ratio change).
    val keptKeyframes = keyframes.withTransformsKept { at -> copy(transform = at).keptTransform(old, new) }
    return if (kept == transform && keptKeyframes == keyframes) this else copy(transform = kept, keyframes = keptKeyframes)
}

private fun MediaClip.keptTransform(old: PixelSize, new: PixelSize): Transform =
    transform.keptInside(old, new, OverlayCatalog.RATIO_CHANGE_SHRINK_FLOOR, marginOfShortSide = 0f) { canvas, scale -> overlayBoundsOn(canvas, scale) }
