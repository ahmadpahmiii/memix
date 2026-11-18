package app.memix.core.domain.project

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.FrameFit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point in a clip's own picture, each side 0..1 from the picture's top-left (before its geometry, key, filters and framing). */
data class PicturePoint(val u: Float, val v: Float)

/**
 * Where the stage point ([canvasFx], [canvasFy], fractions of the canvas) lands in the picture of keyable clip [itemId] at project
 * time [atUs], through the inverse of the clip's own transform (spec P4-04 → Pick mode: "what is sampled"); null when the point is
 * outside the clip's drawn box or the clip's picture size isn't known. An overlay follows its keyframes at [atUs]; a main clip is
 * drawn by its Fit or Fill.
 */
fun Project.picturePointAt(itemId: String, atUs: Long, canvasFx: Float, canvasFy: Float): PicturePoint? {
    val clip = keyableClip(itemId) ?: return null
    val picture = clip.source.pixelSize?.takeIf { it.widthPx > 0 && it.heightPx > 0 } ?: return null
    val canvasSize = PixelSize(canvas.widthPx, canvas.heightPx)
    val x = canvasFx * canvasSize.widthPx
    val y = canvasFy * canvasSize.heightPx
    val onOverlay = findItem(itemId)?.track?.kind == TrackKind.OVERLAY
    val point = if (onOverlay) overlayPicturePoint(clip, atUs, picture, canvasSize, x, y) else mainPicturePoint(clip, picture, canvasSize, x, y)
    return point?.takeIf { it.u in 0f..1f && it.v in 0f..1f }
}

/** The time in [clip]'s source file of the frame shown at project time [atUs] (at the clip's speed, P4-09); 0 for a photo. */
fun sourceTimeOf(clip: MediaClip, atUs: Long): Long =
    if (clip.source.kind == MediaKind.IMAGE) 0L
    else (clip.trimInUs + SpeedCatalog.sourceSpanOf(atUs - clip.startUs, SpeedCatalog.speedOf(clip))).coerceIn(clip.trimInUs, clip.trimOutUs)

private fun Project.overlayPicturePoint(clip: MediaClip, atUs: Long, picture: PixelSize, canvas: PixelSize, x: Float, y: Float): PicturePoint? {
    val transform = lookOf(clip.id, atUs)?.transform ?: clip.transform
    val box = overlayBoxAtScaleOne(clip, canvas)
    val width = box.width * transform.scale
    val height = box.height * transform.scale
    if (width <= 0f || height <= 0f) return null
    val dx = x - transform.centerX * canvas.widthPx
    val dy = y - transform.centerY * canvas.heightPx
    // The turn is clockwise on screen (y down): undo it to land in the picture's own axes.
    val radians = transform.rotationDegrees * DEGREES_TO_RADIANS
    val localX = dx * cos(radians) + dy * sin(radians)
    val localY = -dx * sin(radians) + dy * cos(radians)
    return PicturePoint(localX / width + HALF, localY / height + HALF)
}

private fun mainPicturePoint(clip: MediaClip, picture: PixelSize, canvas: PixelSize, x: Float, y: Float): PicturePoint {
    val across = canvas.widthPx.toFloat() / picture.widthPx
    val down = canvas.heightPx.toFloat() / picture.heightPx
    val scale = if (clip.fit == FrameFit.FILL) max(across, down) else min(across, down)
    val width = picture.widthPx * scale
    val height = picture.heightPx * scale
    return PicturePoint((x - (canvas.widthPx - width) / 2) / width, (y - (canvas.heightPx - height) / 2) / height)
}

private const val HALF = 0.5f
private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()
